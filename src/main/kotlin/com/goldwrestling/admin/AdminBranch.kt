package com.goldwrestling.admin

import com.goldwrestling.branch.Branch
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * Admin ↔ Branch 다대다 조인 엔티티. MVP는 매핑이 1:1이지만, 확장 전제(requirements §1)를 위해
 * 다대다로 설계한다. 양방향 연관을 만들지 않는다(conventions §3: 기본은 단방향).
 *
 * `created_at`은 이 엔티티에 매핑하지 않는다 — 값은 DB `DEFAULT now()`가 소유하고,
 * 이 필드를 읽거나 쓰는 코드 경로가 Phase 1에는 없다. 감사 시각 매핑 전략(Clock 빈 + JPA auditing 등)은
 * 첫 INSERT 경로가 생기는 Phase 2에서 결정한다.
 */
@Entity
@Table(name = "admin_branch")
class AdminBranch(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    val admin: Admin,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    val branch: Branch,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
