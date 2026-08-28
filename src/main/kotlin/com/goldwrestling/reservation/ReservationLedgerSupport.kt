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
import org.slf4j.LoggerFactory
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

        restorePassAfterCancellation(
            passId = passId,
            passStatus = passStatus,
            refundRequested = refundRequested,
            canceledAt = canceledAt,
            member = member,
            admin = admin,
        )
    }

    /**
     * 취소 복구의 **잔여 판정 + 이력 저장만** 담당한다(세션 정원 반영은 포함하지 않는다) —
     * [restoreAfterCancellation](건당 1회 취소, 회원 셀프·관리자 대리)가 쓴다. **휴강 캐스케이드는
     * 이 메서드를 쓰지 않는다** — [restorePassAfterSuspension]을 쓴다(D-145, 이슈 #12/WR-02).
     *
     * 두 메서드로 분리한 이유: 이 메서드는 호출 시점에 호출부가 이미 들고 있는 [passStatus]로만
     * 판정한다 — 단일 취소는 요청 하나가 곧 그 이용권의 복구 여부를 결정하므로 스냅샷이 stale일
     * 창이 없다. 그래서 0행(`adjustRemainingCount`)은 "판정 이후 상태가 바뀐 것"이라는 명확한
     * 이상 상황이고 [IllegalStateException]으로 요청 자체를 실패시키는 게 맞다. 반면 휴강은 N건을
     * 한 트랜잭션에서 순회하는 동안 무관한 다른 관리자의 등록취소가 끼어들 수 있어(스냅샷 stale
     * read) 같은 0행이 "정상적인 경합 결과"일 수 있다 — 그 차이를 `cascade: Boolean` 플래그나
     * `Boolean` 반환으로 표현하면 호출부가 반환값을 보고 즉흥적으로 나누게 되어 그 분기 자체가
     * 버그의 원인이 된다([ReservationRefundPolicy] KDoc이 이미 금지한 패턴). 메서드를 분리하면
     * **시그니처 자체가 실패 의미를 말한다** — 여기는 `Unit` + 예외(복구 실패 = 요청 실패),
     * [restorePassAfterSuspension]은 `Unit` + WARN 로그(N건 중 1건의 복구 실패가 나머지 N-1건을
     * 롤백시킬 이유가 없다).
     *
     * 휴강 캐스케이드는 N건을 한 트랜잭션에서 처리하며 세션 정원을 건별 [decrementReservedCount]가
     * 아니라 `ClassSessionRepository.resetReservedCount` 단일 호출로 반영한다(N번의 UPDATE·clear를
     * 피한다, 04-14 action 참조) — 그래서 이 메서드는 세션 갱신을 하지 않고, 호출부가 각자의 방식으로
     * `reserved_count`를 갱신한다.
     *
     * 이력 사유는 항상 [TransactionReason.CANCEL_REFUND]다 — 비기본값(`CLASS_CANCELED_REFUND`)을
     * 넘기던 유일한 호출부(휴강)가 [restorePassAfterSuspension]으로 이동했다.
     */
    fun restorePassAfterCancellation(
        passId: Long,
        passStatus: PassStatus,
        refundRequested: Boolean,
        canceledAt: OffsetDateTime,
        member: Member?,
        admin: Admin?,
    ) {
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

    /**
     * 휴강 캐스케이드 전용 복구(D-145, 이슈 #12/WR-02) — [restorePassAfterCancellation](단일 취소
     * 경로)의 `IllegalStateException` 의미를 건드리지 않기 위해 별도 메서드로 분리했다. 두 메서드로
     * 나눈 이유는 [restorePassAfterCancellation] KDoc에 있다.
     *
     * `AdminScheduleService.suspend`는 ④단계에서 활성 예약을 배치 조회하며 스칼라 스냅샷을 뜬 뒤
     * ⑤단계 루프에서 예약별로 취소·복구를 반영한다 — 스냅샷을 뜬 시점과 이 메서드가 실행되는 시점
     * 사이에 다른 관리자가 같은 이용권을 등록취소(`AdminPassService.cancel`)하면, 스냅샷이 들고
     * 있던 이용권 상태는 **낡은(stale) 값**이 된다. 그래서 이 메서드는 스냅샷의 상태를 받지 않고
     * (a) 복구 직전 [passId]로 **현재** 상태를 다시 읽어 [ReservationRefundPolicy.shouldRestore]를
     * 재판정한다.
     *
     * 그럼에도 (b) `adjustRemainingCount`가 0행이면 **예외 대신 WARN 로그 후 스킵**한다 — "직전
     * 재조회"만으로는 경합 창을 닫지 못하기 때문이다. PostgreSQL READ COMMITTED에서 조건부
     * UPDATE는, 상대 트랜잭션이 같은 행을 잠그고 아직 커밋하지 않았다면 그 잠금이 풀릴 때까지
     * 기다렸다가 깨어난 뒤 **갱신된 값으로 `WHERE`를 재평가**한다 — 재조회 시점에는 아직 ACTIVE로
     * 보였더라도, `adjustRemainingCount` UPDATE 문이 실행되는 순간 등록취소 트랜잭션이 먼저
     * 커밋해 버리면 `WHERE status = ACTIVE` 조건이 재평가되어 0행이 나올 수 있다. 그래서 0행이
     * 이 지점에서 나오는 것은 "등록취소와 동시 발생"이라는 정상적인 경합 결과이고, 스킵이 최종
     * 방어다 — 이때 잔여도 이력도 남기지 않는다. 등록취소가 이미 `REGISTRATION_CANCELED`로
     * 잔여를 0으로 상쇄했으므로, 여기서 복구하면 D-059의 취소 의미가 깨진다.
     *
     * **409로 거부하지 않는 이유**: 휴강은 관리자가 "이 수업을 닫는다"고 결정한 운영 행위이고, 그
     * 결정의 성패가 무관한 다른 회원의 이용권 상태에 좌우되면 안 된다. 409를 주면 관리자는
     * 재시도하는 것 말고 할 수 있는 일이 없고(자기가 고칠 수 있는 입력 오류가 아니다), 재시도
     * 사이에 또 다른 이용권이 취소되면 같은 실패가 반복된다.
     *
     * 파라미터를 좁게 고정한다 — 휴강은 항상 복구 요청([ReservationRefundPolicy.shouldRestore]에
     * `refundRequested = true` 고정), 이력 주체는 항상 관리자([member]는 `null` 고정), 사유는 항상
     * [TransactionReason.CLASS_CANCELED_REFUND](D-097)다. 호출부가 다르게 넘길 여지를 남기지
     * 않는다. [reservationId]는 WARN 로그의 추적 정보로만 쓴다.
     *
     * `passRepository.findById`를 두 번 호출하는 이유는 [restorePassAfterCancellation]과 같은
     * 함정이다 — `adjustRemainingCount`가 `clearAutomatically = true`라 1번에서 읽은 엔티티가
     * 준영속이 되므로, 이력 저장에는 UPDATE 이후 다시 조회한 영속 엔티티를 쓴다.
     */
    fun restorePassAfterSuspension(
        passId: Long,
        reservationId: Long,
        canceledAt: OffsetDateTime,
        admin: Admin,
    ) {
        val currentPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
        if (!ReservationRefundPolicy.shouldRestore(currentPass.status, refundRequested = true)) {
            return
        }

        if (passRepository.adjustRemainingCount(passId, ReservationPassPolicy.DEDUCTION_AMOUNT) == 0) {
            logger.warn(
                "휴강 복구 스킵 — passId={}, reservationId={}: 등록취소와 동시 발생 — 정책상 복구하지 않음",
                passId,
                reservationId,
            )
            return
        }
        val refreshedPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
        passTransactionRepository.save(
            PassTransaction(
                pass = refreshedPass,
                amount = ReservationPassPolicy.DEDUCTION_AMOUNT,
                reason = TransactionReason.CLASS_CANCELED_REFUND,
                note = null,
                admin = admin,
                member = null,
                occurredAt = canceledAt,
            ),
        )
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(ReservationLedgerSupport::class.java)
    }
}
