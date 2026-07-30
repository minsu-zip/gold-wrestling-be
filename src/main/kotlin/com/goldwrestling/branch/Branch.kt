package com.goldwrestling.branch

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 골드레슬링 지점 (MVP: 송파점 1개, V2 시드로 존재).
 *
 * `created_at`은 이 엔티티에 매핑하지 않는다 — 값은 DB `DEFAULT now()`가 소유하고,
 * 이 필드를 읽거나 쓰는 코드 경로가 Phase 1에는 없다. 감사 시각 매핑 전략(Clock 빈 + JPA auditing 등)은
 * 첫 INSERT 경로가 생기는 Phase 2에서 결정한다.
 */
@Entity
@Table(name = "branch")
class Branch(
    @Column(nullable = false, length = 100)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
