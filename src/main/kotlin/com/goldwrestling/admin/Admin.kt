package com.goldwrestling.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 관리자 (지점 담당자). 담당 지점은 [AdminBranch](다대다 매핑)로 연결한다.
 *
 * 로그인 자격(ID/PW)은 D-04 최소 스키마 원칙에 따라 이 엔티티에 아직 없다 —
 * Phase 2에서 V3 이후 마이그레이션과 함께 추가된다.
 *
 * `created_at`은 이 엔티티에 매핑하지 않는다 — 값은 DB `DEFAULT now()`가 소유하고,
 * 이 필드를 읽거나 쓰는 코드 경로가 Phase 1에는 없다. 감사 시각 매핑 전략(Clock 빈 + JPA auditing 등)은
 * 첫 INSERT 경로가 생기는 Phase 2에서 결정한다.
 */
@Entity
@Table(name = "admin")
class Admin(
    @Column(nullable = false, length = 50)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
