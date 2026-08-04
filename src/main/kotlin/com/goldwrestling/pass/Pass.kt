package com.goldwrestling.pass

import com.goldwrestling.admin.Admin
import com.goldwrestling.branch.Branch
import com.goldwrestling.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 이용권 (저녁반 회비 / 예약제 횟수권 / 1:1 레슨권의 단일 엔티티, D-060).
 *
 * JPA `@Inheritance` 없이 [type] 판별 컬럼으로 3종을 표현한다 — 저장 상태는 `ACTIVE`/`CANCELED`
 * 2종뿐이고 만료·소진은 조회 시점 계산이다(D-064). 타입별 컬럼 규칙(횟수제만 [remainingCount]
 * NOT NULL)은 이 엔티티가 아니라 DB `ck_pass_remaining_count_by_type` CHECK가 강제한다(V4).
 *
 * 도메인 판정 메서드는 03-03~03-05의 TDD 플랜이 테스트를 먼저 쓰고 채웠다(RESEARCH Pattern 1):
 * - [validateAdjustment] — 관리자 수동 가감 허용 여부 (policies §4.2a, D-056, 03-03)
 * - [register] — 등록 시 타입별 필수·금지 필드, 종료일 계산 (policies §1, D-055·D-063·D-066, 03-04)
 * - [displayStatus] — 취소/만료/소진/사용가능 표시 상태 계산 (D-064, 03-04)
 * - [changePeriod] — 기간·유효기간 수정 판정 (PASS-04·PASS-07, D-062, 03-05)
 * - [cancel] — 등록 취소 처리·상쇄 수량 산출 (PASS-08, D-059·D-065, 03-05)
 *
 * 취소된 이용권에 대한 후속 조작 거부([validateAdjustment]·[changePeriod]·[cancel] 공통)는
 * [requireNotCanceled]로 모아 둔다 — D-059 "취소된 이용권 재조작 금지"가 세 진입점 모두에서
 * 같은 예외로 일관되게 강제되게 하기 위해서다.
 */
@Entity
@Table(name = "pass")
class Pass(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    val branch: Branch,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by_admin_id", nullable = false)
    val registeredBy: Admin,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: PassType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PassStatus,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
    @Column(name = "remaining_count", precision = 4, scale = 1)
    var remainingCount: BigDecimal?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canceled_by_admin_id")
    var canceledBy: Admin? = null,
    @Column(name = "canceled_at")
    var canceledAt: OffsetDateTime? = null,
    @Column(name = "cancel_reason", length = 500)
    var cancelReason: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /**
     * 관리자 수동 가감(`ADMIN_ADJUST`) 허용 여부 판정 (policies §4.2a, D-056).
     *
     * **이 메서드는 판정만 하며 잔여를 바꾸지 않는다.** 실제 반영은
     * `PassRepository.adjustRemainingCount`의 조건부 UPDATE가 한다(D-021) — 다음에 이 메서드를
     * 읽는 사람이 여기에 대입문을 추가하지 않는다.
     *
     * 검사 순서는 policies §4.2a의 우선순위를 그대로 따른다 — 순서를 바꾸면 "취소된 기간제"
     * 같은 복합 케이스의 실패 사유가 달라진다.
     */
    fun validateAdjustment(amount: BigDecimal) {
        // 1. 취소된 이용권은 무엇보다 먼저 거부한다 (D-059)
        requireNotCanceled()

        // 2. 기간제는 횟수 가감 대상이 아니다 — 기간 수정으로만 조정한다 (policies §4.2a)
        if (type == PassType.EVENING_MEMBERSHIP) throw PassTypeNotAdjustableException()

        // 3. 가감 단위는 0.5, 0은 허용하지 않는다 (policies §4.2a)
        if (amount.compareTo(BigDecimal.ZERO) == 0 ||
            amount.remainder(HALF_SESSION).compareTo(BigDecimal.ZERO) != 0
        ) {
            throw InvalidAdjustmentUnitException()
        }

        // 4. 결과 잔여가 음수가 되는 가감은 거부한다. 검사 2를 통과했으므로 횟수제이고
        //    remainingCount는 non-null이 보장된다 — CHECK 제약이 깨졌을 때 원인이 드러나도록
        //    `!!` 대신 requireNotNull을 쓴다.
        val remaining = requireNotNull(remainingCount) { "횟수제 이용권의 remainingCount는 null일 수 없습니다." }
        if ((remaining + amount).compareTo(BigDecimal.ZERO) < 0) {
            throw InsufficientPassCountException()
        }
    }

    /**
     * 이용권 표시 상태 계산 (D-064) — 저장하지 않고 조회 시점마다 다시 계산한다.
     * 우선순위: 취소 → 만료 → 소진 → 사용가능(policies §1). 잔여가 남아 있어도 취소가 우선이고,
     * 잔여 0이어도 만료가 소진보다 우선한다 — 순서를 바꾸면 이 두 복합 케이스의 표시가 달라진다.
     */
    fun displayStatus(today: LocalDate): PassDisplayStatus =
        when {
            status == PassStatus.CANCELED -> PassDisplayStatus.CANCELED
            isExpired(today) -> PassDisplayStatus.EXPIRED
            isExhausted() -> PassDisplayStatus.EXHAUSTED
            else -> PassDisplayStatus.USABLE
        }

    /**
     * 유효기간 만료 판정 (D-066): `endDate`는 종료일 포함이라 `!today.isAfter(endDate)`가 유효 —
     * 그 반대인 `today.isAfter(endDate)`가 만료다. Phase 5 만료 배치가 이 식을 재사용한다.
     */
    private fun isExpired(today: LocalDate): Boolean = today.isAfter(endDate)

    /**
     * 소진 판정: 횟수제이며 잔여가 0 이하. 기간제는 [remainingCount]가 항상 null이라 소진 개념이 없다.
     */
    private fun isExhausted(): Boolean = remainingCount?.let { it.compareTo(BigDecimal.ZERO) <= 0 } ?: false

    /**
     * 기간·유효기간 수정 판정 (PASS-04·PASS-07, D-062).
     *
     * **이 메서드는 판정·계산만 하며 상태를 바꾸지 않는다(D-072).** 실제 반영은
     * `PassRepository.changePeriodIfUnchanged`의 조건부 UPDATE(compare-and-swap)가 한다 — 다음에
     * 이 메서드를 읽는 사람이 여기에 `startDate =`/`endDate =` 대입문을 추가하지 않는다. 두
     * 관리자가 동시에 기간을 수정하면 이 판정은 둘 다 통과할 수 있지만, 반영은 조건부 UPDATE가
     * "조회 시점 전값과 DB의 현재 값이 같을 때만" 반영해 한쪽만 성공시킨다.
     *
     * [newStartDate]가 null이면 기존 [startDate]를 유지한다. 횟수제(`SESSION_PASS`/`LESSON_PASS`)는
     * 시작일이 고정이라 기존과 다른 값이 들어오면 거부한다 — 같은 값 재전송(변경 없음)은 허용한다.
     * 기간제(`EVENING_MEMBERSHIP`)만 시작일도 함께 수정할 수 있다.
     *
     * 반환값은 **적용될 (시작일, 종료일)** 쌍이다. 호출부가 이 값과 호출 전 보관해 둔 전값으로
     * 같은 트랜잭션에서 `PassPeriodChange` 이력을 남긴다(D-057).
     */
    fun resolvePeriodChange(
        newStartDate: LocalDate?,
        newEndDate: LocalDate,
    ): Pair<LocalDate, LocalDate> {
        requireNotCanceled()

        val resolvedStart = newStartDate ?: startDate
        if (type != PassType.EVENING_MEMBERSHIP && resolvedStart != startDate) {
            throw InvalidPassPeriodException("횟수권은 유효기간의 종료일만 수정할 수 있습니다.")
        }
        if (newEndDate.isBefore(resolvedStart)) {
            throw InvalidPassPeriodException("종료일은 시작일보다 앞설 수 없습니다.")
        }

        return resolvedStart to newEndDate
    }

    /**
     * 등록 취소 판정 + 원장에 남길 상쇄 수량 산출 (PASS-08, D-059).
     *
     * **이 메서드는 판정·계산만 하며 상태를 바꾸지 않는다(D-072).** [PassStatus.CANCELED] 전환과
     * 취소 시각·사유·주체 기록은 `PassRepository.cancelIfNotCanceled`의 조건부 UPDATE가 한다 —
     * 다음에 이 메서드를 읽는 사람이 여기에 `status =`/`canceledAt =` 같은 대입문을 추가하지
     * 않는다. 두 관리자가 동시에 취소하면 이 판정은 둘 다 통과할 수 있지만, 실제 전환은 조건부
     * UPDATE가 정확히 한 트랜잭션만 반영하고 나머지는 반환 행 수 0으로 알려 호출부가
     * [PassAlreadyCanceledException]으로 변환한다.
     *
     * 반환값은 **원장(`PassTransaction`)에 남길 상쇄 수량**이다 — 0이면 호출부가 상쇄 이력을 남기지
     * 않는다(D-065).
     */
    fun resolveCancellationOffset(): BigDecimal {
        requireNotCanceled()

        return remainingCount?.negate() ?: BigDecimal.ZERO
    }

    /**
     * 이미 취소된 이용권에 대한 후속 조작을 공통으로 거부한다 (D-059).
     * [validateAdjustment]·[changePeriod]·[cancel] 세 진입점이 함께 쓴다.
     */
    private fun requireNotCanceled() {
        if (status == PassStatus.CANCELED) throw PassAlreadyCanceledException()
    }

    companion object {
        private val HALF_SESSION = BigDecimal("0.5")

        /**
         * 이용권 등록 팩토리 (policies §1, D-055, D-063, D-066).
         *
         * `startDate` 기본값(오늘) 채우기는 서비스가 `Clock`으로 하고, 이 팩토리는 항상 확정된
         * 날짜를 받는다. 타입별 필수·금지 필드는 `when (type)` 한 곳에 모아 "이 타입에 무엇이
         * 필수이고 무엇이 금지인지"가 한 화면에 보이게 한다.
         */
        fun register(
            member: Member,
            branch: Branch,
            type: PassType,
            startDate: LocalDate,
            term: EveningMembershipTerm?,
            initialCount: BigDecimal?,
            registeredBy: Admin,
            now: OffsetDateTime,
        ): Pass {
            val (endDate, remainingCount) =
                when (type) {
                    // 기간제: 개월 수는 저장하지 않고 종료일 계산에만 쓴다 (D-063). 종료일은
                    // 종료일 포함 계산식을 쓴다 (D-066).
                    PassType.EVENING_MEMBERSHIP -> {
                        if (initialCount != null) throw PassTypeNotAdjustableException()
                        if (term == null) {
                            throw InvalidPassPeriodException("저녁반 회비는 기간(1/3/6개월)을 지정해야 합니다.")
                        }
                        startDate.plusMonths(term.months).minusDays(1) to null
                    }

                    // 횟수제: 초기 횟수는 0.5 단위 자유 입력이다 (D-055). 유효기간은 시작일 기준
                    // 1년, 종료일 포함 계산식을 쓴다 (D-066).
                    PassType.SESSION_PASS, PassType.LESSON_PASS -> {
                        if (term != null) {
                            throw InvalidPassPeriodException("횟수권·레슨권은 회비 기간 단위를 지정할 수 없습니다.")
                        }
                        // 누락과 단위 위반은 원인이 다르다 — 누락은 MissingInitialCountException(WR-02),
                        // 단위 위반은 validateInitialCount의 InvalidAdjustmentUnitException.
                        val count = initialCount ?: throw MissingInitialCountException()
                        validateInitialCount(count)
                        startDate.plusYears(1).minusDays(1) to count
                    }
                }

            return Pass(
                member = member,
                branch = branch,
                registeredBy = registeredBy,
                type = type,
                status = PassStatus.ACTIVE,
                startDate = startDate,
                endDate = endDate,
                remainingCount = remainingCount,
                createdAt = now,
            )
        }

        /** 초기 횟수는 양수이며 0.5 단위여야 한다 (policies §1, D-055). */
        private fun validateInitialCount(initialCount: BigDecimal) {
            if (initialCount.compareTo(BigDecimal.ZERO) <= 0 ||
                initialCount.remainder(HALF_SESSION).compareTo(BigDecimal.ZERO) != 0
            ) {
                throw InvalidAdjustmentUnitException()
            }
        }
    }
}
