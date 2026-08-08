package com.goldwrestling.reservation

import com.goldwrestling.admin.Admin
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberNotFoundException
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.pass.InsufficientPassCountException
import com.goldwrestling.pass.PassNotFoundException
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassSessionService
import com.goldwrestling.schedule.ReservationWindow
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * 예약 생성의 차감 경로와 취소의 복구 경로를 [MemberReservationService](회원 셀프)·
 * [AdminReservationService](관리자 대리, RESV-08, 04-13)가 공유하는 내부 컴포넌트(D-090 확장).
 *
 * **차감/복구 로직이 두 서비스에 각자 복제되면 D-021(모든 잔여 변경이 이력을 남긴다) 보장이
 * 흩어진다** — 그래서 `PassRepository.adjustRemainingCount`·`ClassSessionRepository`의 조건부
 * UPDATE는 이 컴포넌트 하나만 호출한다. `@Transactional`은 붙이지 않는다 — 항상 호출부
 * (`MemberReservationService`·`AdminReservationService`)가 이미 열어 둔 트랜잭션 안에서
 * 실행되는 헬퍼이기 때문이다(`NotificationService`와 동일 관례).
 *
 * [PassTransaction]의 주체(`admin`/`member`, 정확히 하나만 non-null)는 호출부가 [admin]
 * 파라미터로 결정한다 — 예약이 누구 소유인지([Reservation.member], 항상 [memberId]로 조회한
 * 회원)와 그 변경을 누가 수행했는지(이력 주체)는 다른 축이다. [admin]이 `null`이면 회원 셀프
 * 경로([memberId] 본인이 주체), non-null이면 관리자 대리 경로([admin]이 주체)다.
 */
@Component
class ReservationLedgerSupport(
    private val memberRepository: MemberRepository,
    private val classSessionRepository: ClassSessionRepository,
    private val classSessionService: ClassSessionService,
    private val reservationRepository: ReservationRepository,
    private val passRepository: PassRepository,
    private val passTransactionRepository: PassTransactionRepository,
    private val clock: Clock,
) {
    /**
     * 예약 생성 실행부 — 세션 확보(get-or-create) → 정원 조건부 UPDATE → 차감 후보 선정 →
     * 이용권 조건부 차감 → `Reservation` INSERT → `PassTransaction(RESERVE)` INSERT.
     *
     * [enforceWindow]가 `true`면 [ReservationWindow.assertBookable]로 이번 주·마감 전 여부를
     * 검사한다(회원 셀프 예약·변경). 관리자 대리 변경은 `false`를 넘긴다 — policies §3 "관리자는
     * 예약 창 제약이 없다"(RESV-08 behavior "당일·지난 날짜로도 변경할 수 있다").
     *
     * 정원·차감 조건부 UPDATE(`clearAutomatically = true`) 이후 이전에 로드한 [schedule]·세션·
     * 차감 후보는 준영속 상태가 된다 — INSERT에 쓸 영속 엔티티는 마지막 조건부 UPDATE 직후 다시
     * 조회한다(`AdminPassService` 관례, RESEARCH Pitfall 4).
     */
    fun createReservation(
        memberId: Long,
        schedule: ClassSchedule,
        classDate: LocalDate,
        enforceWindow: Boolean,
        admin: Admin?,
    ): Reservation {
        val scheduleStartTime = schedule.startTime
        val scheduleClassType = schedule.classType

        if (enforceWindow) {
            val now = LocalDateTime.now(clock)
            ReservationWindow.assertBookable(classDate, scheduleStartTime, now)
        }

        val passType = ReservationPassPolicy.requiredPassType(scheduleClassType)

        val session = classSessionService.getOrCreate(schedule, classDate)
        session.assertReservable()
        val sessionId = requireNotNull(session.id) { "getOrCreate가 반환한 세션은 항상 저장돼 있어야 합니다." }
        val sessionClassType = session.classType
        val sessionStartTime = session.startTime

        if (reservationRepository.existsByMemberIdAndClassDateAndStartTimeAndStatus(
                memberId,
                classDate,
                sessionStartTime,
                ReservationStatus.ACTIVE,
            )
        ) {
            throw DuplicateReservationException()
        }

        if (classSessionRepository.incrementReservedCountIfCapacityAvailable(sessionId) == 0) {
            throw ReservationCapacityExceededException()
        }

        val candidates =
            passRepository.findDeductionCandidates(
                memberId,
                passType,
                classDate,
                ReservationPassPolicy.DEDUCTION_AMOUNT,
            )
        val candidate = ReservationPassPolicy.selectCandidate(candidates)
        val passId = requireNotNull(candidate.id) { "차감 후보 조회는 항상 저장된 Pass만 반환합니다." }

        if (passRepository.adjustRemainingCount(passId, ReservationPassPolicy.DEDUCTION_AMOUNT.negate()) == 0) {
            throw InsufficientPassCountException()
        }

        val refreshedMember = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        val refreshedSession =
            classSessionRepository.findById(sessionId).orElseThrow {
                IllegalStateException("방금 정원을 갱신한 ClassSession(id=$sessionId)을 찾을 수 없습니다.")
            }
        val refreshedPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }

        val nowOffset = OffsetDateTime.now(clock)

        val reservation =
            try {
                reservationRepository.saveAndFlush(
                    Reservation(
                        member = refreshedMember,
                        classSession = refreshedSession,
                        pass = refreshedPass,
                        classType = sessionClassType,
                        classDate = classDate,
                        startTime = sessionStartTime,
                        status = ReservationStatus.ACTIVE,
                        reservedAt = nowOffset,
                        createdAt = nowOffset,
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                throw DuplicateReservationException()
            }

        passTransactionRepository.save(
            PassTransaction(
                pass = refreshedPass,
                amount = ReservationPassPolicy.DEDUCTION_AMOUNT.negate(),
                reason = TransactionReason.RESERVE,
                note = null,
                admin = admin,
                member = if (admin == null) refreshedMember else null,
                occurredAt = nowOffset,
            ),
        )

        return reservation
    }

    /**
     * 취소 반영 공통 경로 — 세션 정원 복구 + 복구 여부 판정(D-091, Pitfall 2) + 판정이 true일 때만
     * 잔여 복구·`PassTransaction(CANCEL_REFUND)` 저장. 호출 전에 예약 상태 자체는 호출부가
     * compare-and-swap(`cancelByMemberIfActive`/`cancelByAdminIfActive`)으로 이미 `CANCELED`로
     * 반영했다고 가정한다 — 그 조건부 UPDATE는 회원/관리자 경로가 갱신하는 취소 메타데이터 컬럼이
     * 달라 이 컴포넌트가 공유하지 않는다.
     *
     * [member]/[admin] 중 정확히 하나만 non-null — 이 취소를 수행한 주체([PassTransaction]의 이력
     * 주체)다. 두 파라미터 다 이미 벌크 UPDATE로 준영속화됐을 수 있는 엔티티를 그대로 받는다 —
     * FK 참조 용도로만 쓰이므로(cascade 없음) 안전하다(`AdminPassService.adjust`의 `admin` 관례).
     */
    fun restoreAfterCancellation(
        sessionId: Long,
        passId: Long,
        passStatus: PassStatus,
        refundRequested: Boolean,
        canceledAt: OffsetDateTime,
        member: Member?,
        admin: Admin?,
    ) {
        if (classSessionRepository.decrementReservedCount(sessionId) == 0) {
            throw IllegalStateException("활성 예약이 있었던 세션(id=$sessionId)의 reservedCount가 이미 0입니다.")
        }

        if (!ReservationRefundPolicy.shouldRestore(passStatus, refundRequested)) {
            return
        }

        if (passRepository.adjustRemainingCount(passId, ReservationPassPolicy.DEDUCTION_AMOUNT) == 0) {
            throw IllegalStateException(
                "복구 대상 이용권(id=$passId)이 판정 이후 상태가 바뀌어 복구를 반영하지 못했습니다.",
            )
        }
        val refreshedPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
        passTransactionRepository.save(
            PassTransaction(
                pass = refreshedPass,
                amount = ReservationPassPolicy.DEDUCTION_AMOUNT,
                reason = TransactionReason.CANCEL_REFUND,
                note = null,
                admin = admin,
                member = member,
                occurredAt = canceledAt,
            ),
        )
    }
}
