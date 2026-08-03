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
 * **도메인 판정 메서드(가감 허용 여부, 만료·소진 계산 등)는 이 태스크에 넣지 않는다** — 03-03~03-05의
 * TDD 플랜이 테스트를 먼저 쓰고 채운다(RESEARCH Pattern 1).
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
}
