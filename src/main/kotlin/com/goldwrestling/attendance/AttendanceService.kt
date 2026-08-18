package com.goldwrestling.attendance

import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.attendance.dto.AddEveningAttendanceRequest
import com.goldwrestling.attendance.dto.AttendanceResponse
import com.goldwrestling.attendance.dto.AttendanceRosterEntryResponse
import com.goldwrestling.attendance.dto.CheckAttendanceRequest
import com.goldwrestling.attendance.dto.ClassSessionAttendanceRosterResponse
import com.goldwrestling.member.MemberNotFoundException
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.pass.PassNotFoundException
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.ClassScheduleNotFoundException
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSessionService
import com.goldwrestling.schedule.ClassType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 출석 체크의 **예약제/1:1 경로**(D-132, policies §6)와 **저녁반 0.5회 차감 경로**(§4.2, D-128,
 * ATTEND-02)를 함께 담당한다 — 예약자 명단 프리로드([getRoster]), 회원 건별 출석/불참 upsert
 * ([check]), 저녁반 출석 추가·차감([addEveningAttendance]), 출석 삭제·복구([delete]).
 *
 * **예약제/1:1 경로는 이용권 원장을 건드리지 않는다** — 출석은 차감과 무관한 참고용 데이터이기
 * 때문이다(policies §6 "차감은 예약 시점에 이미 확정"). 소급 출석 정정이 이미 실행된 배치성 차감
 * 이력(2주 미사용 자동 차감, §4.3)을 되돌리는 코드는 이 파일 어디에도 없다(D-127) — 정정이
 * 필요하면 관리자가 수동 가감(§4.2a)으로 처리한다.
 *
 * **저녁반 경로만 예외적으로 [passRepository]·[passTransactionRepository]를 쓴다** — 이 phase에서
 * 잔여 횟수를 바꾸는 유일한 경로다. 그 실행 구조는 `ReservationLedgerSupport.createReservation`이
 * 확립한 "판정 → 조건부 UPDATE(0행이면 예외) → 재조회 → INSERT → 원장 기록"을 그대로 이식한다
 * (06-07). `ReservationLedgerSupport`를 직접 재사용하지 않는 이유는 [delete]의 KDoc에 있다.
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
    private val passRepository: PassRepository,
    private val passTransactionRepository: PassTransactionRepository,
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

    /**
     * 저녁반 출석 추가 실행부(ATTEND-02, policies §4.2, D-128) — 회비 우선 판정 → (필요 시)
     * `SESSION_PASS` 0.5회 조건부 차감 → 출석 INSERT → `EVENING_HALF` 원장 기록을 한 트랜잭션으로
     * 묶는다. `ReservationLedgerSupport.createReservation`이 확립한 "판정 → 조건부 UPDATE(0행이면
     * 예외) → 재조회 → INSERT → 원장 기록" 구조를 그대로 이식한다.
     *
     * `EVENING` 세션은 `capacity`가 null이라(저녁반은 예약 대상이 아니다)
     * `incrementReservedCountIfCapacityAvailable`을 호출하지 않는다 — `reservedCount`는 예약제/1:1
     * 정원 관리 전용이다.
     *
     * **회비·차감 후보 조회는 항상 [ClassSession.classDate](수업날) 기준이다** — clock으로 구한 오늘
     * 날짜를 넘기면 소급 입력 시 오늘 기준으로 잘못 판정한다(D-128 Pitfall 1).
     */
    @Transactional
    fun addEveningAttendance(
        adminId: Long,
        request: AddEveningAttendanceRequest,
    ): AttendanceResponse {
        val schedule =
            classScheduleRepository.findById(request.classScheduleId).orElseThrow {
                ClassScheduleNotFoundException(request.classScheduleId)
            }
        val session = classSessionService.getOrCreate(schedule, request.classDate)
        EveningHalfDeductionPolicy.requireEveningSession(session.classType)
        val sessionId = requireNotNull(session.id) { "getOrCreate가 반환한 세션은 항상 저장돼 있어야 합니다." }
        val sessionClassDate = session.classDate

        if (attendanceRepository.existsByClassSessionIdAndMemberId(sessionId, request.memberId)) {
            throw DuplicateAttendanceException()
        }

        // 회비 우선 판정 — 반드시 수업날(sessionClassDate) 기준. 오늘 기준으로 판정하지 않는다.
        val hasValidMembership = passRepository.existsActiveEveningMembership(request.memberId, sessionClassDate)

        val deductedPassId: Long? =
            if (hasValidMembership) {
                null
            } else {
                val candidates =
                    passRepository.findDeductionCandidates(
                        request.memberId,
                        PassType.SESSION_PASS,
                        sessionClassDate,
                        EveningHalfDeductionPolicy.HALF_SESSION,
                    )
                val candidate = EveningHalfDeductionPolicy.selectCandidate(candidates)
                val candidatePassId = requireNotNull(candidate.id) { "차감 후보 조회는 항상 저장된 Pass만 반환합니다." }
                if (passRepository.adjustRemainingCount(candidatePassId, EveningHalfDeductionPolicy.HALF_SESSION.negate()) == 0) {
                    throw EveningAttendanceDeductionUnavailableException()
                }
                candidatePassId
            }

        // 조건부 UPDATE가 실행됐다면(deductedPassId != null) 영속성 컨텍스트가 clear됐으므로
        // INSERT에 쓸 엔티티를 모두 재조회한다(ReservationLedgerSupport 관례).
        val refreshedMember = memberRepository.findById(request.memberId).orElseThrow { MemberNotFoundException(request.memberId) }
        val refreshedSession =
            classSessionService.findExisting(request.classScheduleId, request.classDate)
                ?: throw IllegalStateException("방금 확보한 ClassSession(id=$sessionId)을 찾을 수 없습니다.")
        val refreshedAdmin =
            adminRepository.findById(adminId).orElseThrow {
                IllegalStateException("저녁반 출석을 추가하려는 관리자(id=$adminId)를 찾을 수 없습니다.")
            }
        val now = OffsetDateTime.now(clock)

        val passTransaction =
            deductedPassId?.let { passId ->
                val refreshedPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
                passTransactionRepository.save(
                    PassTransaction(
                        pass = refreshedPass,
                        amount = EveningHalfDeductionPolicy.HALF_SESSION.negate(),
                        reason = TransactionReason.EVENING_HALF,
                        note = null,
                        admin = refreshedAdmin,
                        member = null,
                        occurredAt = now,
                    ),
                )
            }

        val saved =
            try {
                attendanceRepository.saveAndFlush(
                    Attendance(
                        member = refreshedMember,
                        classSession = refreshedSession,
                        status = AttendanceStatus.ATTENDED,
                        passTransaction = passTransaction,
                        checkedBy = refreshedAdmin,
                        checkedAt = now,
                        createdAt = now,
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                throw DuplicateAttendanceException()
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
