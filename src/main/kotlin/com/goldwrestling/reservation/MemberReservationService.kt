package com.goldwrestling.reservation

import com.goldwrestling.member.MemberNotFoundException
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.notification.NotificationService
import com.goldwrestling.pass.InsufficientPassCountException
import com.goldwrestling.pass.PassNotFoundException
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.reservation.dto.ReservationResponse
import com.goldwrestling.reservation.dto.ReserveRequest
import com.goldwrestling.schedule.ClassScheduleNotFoundException
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassSessionService
import com.goldwrestling.schedule.ReservationWindow
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * 회원 예약 생성(RESV-01·02) — 이 phase에서 가장 중요한 트랜잭션 조립.
 *
 * 예약 1건은 5개 테이블을 원자적으로 갱신한다 — `class_session`(정원 +1) · `pass`(잔여 −1) ·
 * `reservation`(INSERT) · `pass_transaction`(RESERVE) · `notification`(INSERT). 각 단계의 원자성은
 * 04-03의 조건부 UPDATE가 이미 보장하므로, 이 서비스의 일은 **순서와 실패 처리**를 정확히 짜는
 * 것이다 — 순서를 바꾸면 롤백 범위나 에러코드가 달라진다(각 단계는 [reserve] 내부에 번호 주석으로
 * 남겨 뒀다).
 *
 * **회원 상태 게이트(`MemberStateGate.requireActive`)는 이 서비스가 호출하지 않는다** — 04-08의
 * 컨트롤러가 호출한다(D-040 관례). 이 서비스는 [memberId]만 받아 이미 활성 회원이라고 가정한다.
 */
@Service
@Transactional(readOnly = true)
class MemberReservationService(
    private val memberRepository: MemberRepository,
    private val classScheduleRepository: ClassScheduleRepository,
    private val classSessionRepository: ClassSessionRepository,
    private val classSessionService: ClassSessionService,
    private val reservationRepository: ReservationRepository,
    private val passRepository: PassRepository,
    private val passTransactionRepository: PassTransactionRepository,
    private val notificationService: NotificationService,
    private val clock: Clock,
) {
    /**
     * 예약을 생성한다. ⑦·⑨의 조건부 UPDATE는 `clearAutomatically = true`라 그 시점 이후
     * 이전에 로드한 엔티티(`member`·`schedule`·`session`·`candidate`)는 준영속 상태가 된다 —
     * ⑩·⑪에서 쓸 영속 엔티티는 마지막 조건부 UPDATE(⑨) 직후 다시 조회한다(`AdminPassService`
     * 관례, RESEARCH Pitfall 4).
     */
    @Transactional
    fun reserve(
        memberId: Long,
        request: ReserveRequest,
    ): ReservationResponse {
        // ① 회원 조회 — branchId 확보. 회원 상태 게이트는 컨트롤러(04-08)가 호출한다.
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        val memberBranchId = member.branch.id

        // ② 시간표 조회 — 없거나 타 지점 시간표거나 요일이 classDate와 다르면 거부한다(T-04-27·28).
        // "요일이 어긋난 조합"까지 같은 예외로 거부하는 이유: 존재하지 않는 (시간표, 날짜) 조합을
        // 클라이언트가 임의로 만들어 요청하는 것을 막기 위해서다.
        val schedule =
            classScheduleRepository.findById(request.classScheduleId).orElseThrow {
                ClassScheduleNotFoundException(request.classScheduleId)
            }
        if (schedule.branch.id != memberBranchId || schedule.dayOfWeek != request.classDate.dayOfWeek) {
            throw ClassScheduleNotFoundException(request.classScheduleId)
        }
        val scheduleStartTime = schedule.startTime
        val scheduleClassType = schedule.classType

        // ③ 예약 창 검사 — 이번 주가 아니거나 이미 시작된 수업이면 거부한다(D-095).
        val now = LocalDateTime.now(clock)
        ReservationWindow.assertBookable(request.classDate, scheduleStartTime, now)

        // ④ 필요 이용권 종류 판정 — 저녁반이면 여기서 ClassSessionNotReservableException으로 거부한다.
        val passType = ReservationPassPolicy.requiredPassType(scheduleClassType)

        // ⑤ 세션 확보(get-or-create, D-094)·휴강 검사.
        val session = classSessionService.getOrCreate(schedule, request.classDate)
        session.assertReservable()
        val sessionId = requireNotNull(session.id) { "getOrCreate가 반환한 세션은 항상 저장돼 있어야 합니다." }
        val sessionClassType = session.classType
        val sessionStartTime = session.startTime
        val sessionEndTime = session.endTime

        // ⑥ 중복 예약 사전 검사 — 사용자에게 정확한 에러를 주기 위한 것일 뿐, 실제 방어는 부분
        // 유니크 인덱스(ux_reservation_member_timeslot_active, D-021)다.
        if (reservationRepository.existsByMemberIdAndClassDateAndStartTimeAndStatus(
                memberId,
                request.classDate,
                sessionStartTime,
                ReservationStatus.ACTIVE,
            )
        ) {
            throw DuplicateReservationException()
        }

        // ⑦ 정원 조건부 UPDATE — 반환 0이면 정원 초과 또는 그 사이 휴강 처리됨.
        if (classSessionRepository.incrementReservedCountIfCapacityAvailable(sessionId) == 0) {
            throw ReservationCapacityExceededException()
        }

        // ⑧ 차감 후보 선정(D-091, 만료 임박순·단일 이용권 기준) — 없으면 잔여 부족.
        val candidates =
            passRepository.findDeductionCandidates(
                memberId,
                passType,
                request.classDate,
                ReservationPassPolicy.DEDUCTION_AMOUNT,
            )
        val candidate = ReservationPassPolicy.selectCandidate(candidates)
        val passId = requireNotNull(candidate.id) { "차감 후보 조회는 항상 저장된 Pass만 반환합니다." }

        // ⑨ 이용권 차감(조건부 UPDATE) — 반환 0이면 경쟁에서 진 것이다(같은 회원이 자신의 이용권을
        // 동시에 두 곳에서 쓰는 경우뿐). CONTEXT.md Claude's Discretion 확정대로 재시도 없이 즉시
        // 실패시킨다 — 빈도가 극히 낮아 재시도 로직의 복잡성을 들일 실익이 없다.
        if (passRepository.adjustRemainingCount(passId, ReservationPassPolicy.DEDUCTION_AMOUNT.negate()) == 0) {
            throw InsufficientPassCountException()
        }

        // ⑦·⑨의 clearAutomatically로 이전에 로드한 member·schedule·session·candidate는 전부
        // 준영속 상태다 — ⑩·⑪에 쓸 영속 엔티티를 재조회한다(AdminPassService 관례).
        val refreshedMember = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        val refreshedSession =
            classSessionRepository.findById(sessionId).orElseThrow {
                IllegalStateException("방금 정원을 갱신한 ClassSession(id=$sessionId)을 찾을 수 없습니다.")
            }
        val refreshedPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }

        val nowOffset = OffsetDateTime.now(clock)

        // ⑩ Reservation INSERT — 부분 유니크 인덱스 위반 시 DataIntegrityViolationException을
        // DuplicateReservationException으로 변환한다. INSERT 실패이므로 트랜잭션이 여기서 끝나도
        // 안전하다(⑦·⑨의 UPDATE는 같은 트랜잭션이라 함께 롤백된다).
        val reservation =
            try {
                reservationRepository.saveAndFlush(
                    Reservation(
                        member = refreshedMember,
                        classSession = refreshedSession,
                        pass = refreshedPass,
                        classType = sessionClassType,
                        classDate = request.classDate,
                        startTime = sessionStartTime,
                        status = ReservationStatus.ACTIVE,
                        reservedAt = nowOffset,
                        createdAt = nowOffset,
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                throw DuplicateReservationException()
            }

        // ⑪ PassTransaction INSERT — 행위자 기준(회원 셀프 예약이므로 member 주체, admin은 null).
        passTransactionRepository.save(
            PassTransaction(
                pass = refreshedPass,
                amount = ReservationPassPolicy.DEDUCTION_AMOUNT.negate(),
                reason = TransactionReason.RESERVE,
                note = null,
                admin = null,
                member = refreshedMember,
                occurredAt = nowOffset,
            ),
        )

        // ⑫ 관리자 알림 생성 — 같은 트랜잭션에 편승한다(NotificationService는 자체 경계를 열지 않는다).
        notificationService.createReservationCreated(reservation)

        return ReservationResponse.from(reservation, sessionEndTime)
    }
}
