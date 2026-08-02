package com.goldwrestling.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * 관리자 (지점 담당자). 담당 지점은 [AdminBranch](다대다 매핑)로 연결한다.
 *
 * 로그인 자격(ID/PW)은 [loginId]·[passwordHash]로 존재한다 (D-045 — DelegatingPasswordEncoder,
 * `{bcrypt}` 접두 포함 길이로 `VARCHAR(100)`). 역할 컬럼은 두지 않는다 — 역할은 "어느 테이블
 * (member/admin) 행인가"로 이미 결정되고, 코드 쪽 구분은 `PrincipalType`이 담당한다(D-040).
 *
 * `created_at`은 서비스가 주입받은 `Clock`으로 명시적으로 채운다 — JPA Auditing은 도입하지 않는다
 * (D-039). DB의 `DEFAULT now()`는 방어선으로 남긴다.
 */
@Entity
@Table(name = "admin")
class Admin(
    @Column(nullable = false, length = 50)
    var name: String,
    @Column(name = "login_id", nullable = false, length = 50)
    val loginId: String,
    @Column(name = "password_hash", nullable = false, length = 100)
    var passwordHash: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
