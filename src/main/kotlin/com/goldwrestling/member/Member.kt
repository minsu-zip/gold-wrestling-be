package com.goldwrestling.member

import com.goldwrestling.branch.Branch
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

/**
 * 회원 (카카오 로그인 사용자).
 *
 * [name]·[phoneNumber]는 온보딩(Phase 2, policies §5.1) 전에는 값이 없다 — 온보딩 완료 판정은
 * 이 두 필드의 입력 여부로 한다(D-025). 컬럼 nullable과 Kotlin 타입 nullable을 함께 맞춘다 —
 * 불일치는 `ddl-auto=validate`가 잡지 못하는 종류의 버그다(conventions §3).
 *
 * `created_at`은 이 엔티티에 매핑하지 않는다 — 값은 DB `DEFAULT now()`가 소유하고,
 * 이 필드를 읽거나 쓰는 코드 경로가 Phase 1에는 없다. 감사 시각 매핑 전략(Clock 빈 + JPA auditing 등)은
 * 첫 INSERT 경로가 생기는 Phase 2에서 결정한다.
 */
@Entity
@Table(name = "member")
class Member(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    val branch: Branch,
    @Column(length = 50)
    var name: String?,
    @Column(name = "phone_number", length = 20)
    var phoneNumber: String?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MemberStatus = MemberStatus.PENDING,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
