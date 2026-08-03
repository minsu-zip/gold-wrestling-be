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
 * 도메인 판정 메서드(가감 허용 여부, 만료·소진 계산 등)는 03-03~03-05의 TDD 플랜이 테스트를
 * 먼저 쓰고 채운다(RESEARCH Pattern 1) — [validateAdjustment]가 그 첫 번째 판정이다(03-03).
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
        if (status == PassStatus.CANCELED) throw PassAlreadyCanceledException()

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

    companion object {
        private val HALF_SESSION = BigDecimal("0.5")
    }
}
