package com.goldwrestling.attendance

import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.attendance.dto.AttendanceResponse
import com.goldwrestling.attendance.dto.AttendanceRosterEntryResponse
import com.goldwrestling.attendance.dto.CheckAttendanceRequest
import com.goldwrestling.attendance.dto.ClassSessionAttendanceRosterResponse
import com.goldwrestling.member.MemberNotFoundException
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.ClassScheduleNotFoundException
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSessionService
import com.goldwrestling.schedule.ClassType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 출석 체크의 **예약제/1:1 경로**(D-132, policies §6) — 예약자 명단 프리로드([getRoster])와 회원
 * 건별 출석/불참 upsert([check])만 담당한다. 저녁반 0.5회 차감 경로(§4.2)는 06-07이 이 클래스에
 * `addEveningAttendance`로 추가한다.
 *
 * **이 클래스는 이용권 원장을 조회하지도 수정하지도 않는다** — 출석은 차감과 무관한 참고용
 * 데이터이기 때문이다(policies §6 "차감은 예약 시점에 이미 확정"). 소급 출석 정정이 이미 실행된
 * 배치성 차감 이력(2주 미사용 자동 차감, §4.3)을 되돌리는 코드는 이 파일 어디에도 없다(D-127) —
 * 정정이 필요하면 관리자가 수동 가감(§4.2a)으로 처리한다. 이 금지가 이 클래스의 설계 경계이므로,
 * 이용권 원장 리포지토리를 생성자에 주입하지 않는다.
 */
@Service
@Transactional(readOnly = true)
class AttendanceService(
    private val attendanceRepository: AttendanceRepository,
    private val reservationRepository: ReservationRepository,
    private val classScheduleRepository: ClassScheduleRepository,
    private val classSessionService: ClassSessionService,
    private val memberRepository: MemberRepository,
    private val adminRepository: AdminRepository,
    private val clock: Clock,
) {
    /**
     * 특정 시간표·날짜의 출석 명단을 조회한다(D-132) — 관리자가 타임별 참여자 명단을 출석 상태와
     * 함께 한 번에 볼 수 있게 한다(policies §6).
     *
     * [ClassSessionService.findExisting]만 쓰고 `getOrCreate`를 쓰지 않는다 — 조회가 세션 행을
     * 만들면 관리자가 화면을 열기만 해도 빈 세션이 쌓인다(`ScheduleService`가 조회 경로에서
     * `findExisting`을 쓰는 것과 같은 이유). 세션이 없으면 [classScheduleId]가 가리키는 시간표에서
     * 복사한 표시 정보와 빈 명단을 반환한다.
     */
    fun getRoster(
        classScheduleId: Long,
        classDate: LocalDate,
    ): ClassSessionAttendanceRosterResponse {
        val schedule =
            classScheduleRepository.findById(classScheduleId).orElseThrow {
                ClassScheduleNotFoundException(classScheduleId)
            }
        val session = classSessionService.findExisting(classScheduleId, classDate)
        if (session == null) {
            return ClassSessionAttendanceRosterResponse(
                classScheduleId = classScheduleId,
                classDate = classDate,
                classSessionId = null,
                classType = schedule.classType,
                startTime = schedule.startTime,
                endTime = schedule.endTime,
                capacity = schedule.capacity,
                entries = emptyList(),
            )
        }

        val sessionId = requireNotNull(session.id) { "조회된 ClassSession은 항상 저장돼 있어야 합니다." }
        val entries =
            if (session.classType == ClassType.EVENING) {
                // 저녁반은 예약이 없으므로 출석 레코드가 있는 회원만 명단에 나온다.
                attendanceRepository.findAllByClassSessionIdWithMember(sessionId).map { attendance ->
                    AttendanceRosterEntryResponse(
                        memberId = requireMemberId(attendance),
                        memberName = requireMemberName(attendance.member.id, attendance.member.name),
                        reservationId = null,
                        attendanceId = attendance.id,
                        status = attendance.status,
                        deducted = attendance.passTransaction != null,
                    )
                }
            } else {
                // 예약제/1:1: 활성 예약자 전원 + 출석 레코드가 있는 회원만 status가 채워진다(미체크 = null).
                val activeReservations =
                    reservationRepository.findAllByClassSessionIdInAndStatusWithMember(listOf(sessionId), ReservationStatus.ACTIVE)
                val attendanceByMemberId =
                    attendanceRepository.findAllByClassSessionIdWithMember(sessionId).associateBy { it.member.id }
                activeReservations.map { reservation ->
                    val attendance = attendanceByMemberId[reservation.member.id]
                    AttendanceRosterEntryResponse(
                        memberId = requireNotNull(reservation.member.id) { "저장되지 않은 Member를 참조하는 Reservation은 명단에 담을 수 없습니다." },
                        memberName = requireMemberName(reservation.member.id, reservation.member.name),
                        reservationId = reservation.id,
                        attendanceId = attendance?.id,
                        status = attendance?.status,
                        deducted = attendance?.passTransaction != null,
                    )
                }
            }

        return ClassSessionAttendanceRosterResponse(
            classScheduleId = classScheduleId,
            classDate = classDate,
            classSessionId = sessionId,
            classType = session.classType,
            startTime = session.startTime,
            endTime = session.endTime,
            capacity = session.capacity,
            entries = entries,
        )
    }

    /**
     * 예약제/1:1 출석/불참을 체크한다(upsert, D-132) — 첫 호출에서 새 [Attendance] 행을 만들고,
     * 같은 회원을 다시 체크하면 기존 행의 [Attendance.status]/[Attendance.checkedBy]/
     * [Attendance.checkedAt]만 갱신한다(D-127 소급 수정 허용).
     *
     * 여기는 쓰기 경로라 [ClassSessionService.getOrCreate]로 세션을 확보한다 — 예약이 하나도
     * 없던 타임에 출석만 기록하는 경우가 있다(D-094). 세션이 저녁반(`EVENING`)이면
     * [AttendanceClassTypeMismatchException] — 저녁반은 06-07의 전용 경로가 담당한다(차감 판정이
     * 필요하기 때문). [request]의 회원이 그 세션의 활성 예약자가 아니면
     * [AttendanceMemberNotReservedException].
     *
     * `passTransaction = null`을 고정한다(policies §6, 예약제/1:1은 차감이 없다). 시각은
     * [OffsetDateTime.now]를 직접 부르지 않고 주입받은 [clock]으로만 얻는다.
     */
    @Transactional
    fun check(
        adminId: Long,
        request: CheckAttendanceRequest,
    ): AttendanceResponse {
        val schedule =
            classScheduleRepository.findById(request.classScheduleId).orElseThrow {
                ClassScheduleNotFoundException(request.classScheduleId)
            }
        val session = classSessionService.getOrCreate(schedule, request.classDate)
        if (session.classType == ClassType.EVENING) throw AttendanceClassTypeMismatchException()
        val sessionId = requireNotNull(session.id) { "getOrCreate가 반환한 세션은 항상 저장돼 있어야 합니다." }

        val activeReservations =
            reservationRepository.findAllByClassSessionIdInAndStatusWithMember(listOf(sessionId), ReservationStatus.ACTIVE)
        if (activeReservations.none { it.member.id == request.memberId }) {
            throw AttendanceMemberNotReservedException()
        }

        val member = memberRepository.findById(request.memberId).orElseThrow { MemberNotFoundException(request.memberId) }
        val admin =
            adminRepository.findById(adminId).orElseThrow {
                IllegalStateException("출석을 체크하려는 관리자(id=$adminId)를 찾을 수 없습니다.")
            }
        val now = OffsetDateTime.now(clock)

        val existing = attendanceRepository.findByClassSessionIdAndMemberId(sessionId, request.memberId)
        val saved =
            if (existing != null) {
                existing.status = request.status
                existing.checkedBy = admin
                existing.checkedAt = now
                attendanceRepository.save(existing)
            } else {
                attendanceRepository.save(
                    Attendance(
                        member = member,
                        classSession = session,
                        status = request.status,
                        passTransaction = null,
                        checkedBy = admin,
                        checkedAt = now,
                        createdAt = now,
                    ),
                )
            }

        return AttendanceResponse.from(saved)
    }

    private fun requireMemberId(attendance: Attendance): Long =
        requireNotNull(attendance.member.id) { "저장되지 않은 Member를 참조하는 Attendance는 명단에 담을 수 없습니다." }

    private fun requireMemberName(
        memberId: Long?,
        name: String?,
    ): String = requireNotNull(name) { "이름이 없는 회원(id=$memberId)은 출석 명단에 담을 수 없습니다." }
}
