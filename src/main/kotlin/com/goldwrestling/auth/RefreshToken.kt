package com.goldwrestling.auth

import com.goldwrestling.admin.Admin
import com.goldwrestling.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * access 토큰 재발급용 자격증명.
 *
 * 원문 토큰을 저장하지 않는다 — DB에는 SHA-256 해시([tokenHash])만 저장한다 (D-036).
 * 주체는 정확히 하나([member] 또는 [admin] 중 하나만 non-null) — DB `ck_refresh_token_principal`
 * CHECK 제약과 짝을 이룬다 (D-037). 이 클래스의 [principalType]·[principalId]는 CHECK가 이미
 * 막는 상태를 코드에서도 방어한다.
 */
@Entity
@Table(name = "refresh_token")
class RefreshToken(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    val member: Member?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    val admin: Admin?,
    @Column(name = "token_hash", nullable = false, length = 64)
    val tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: OffsetDateTime,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "revoked_at")
    var revokedAt: OffsetDateTime? = null

    /** 이미 폐기됐으면 아무것도 하지 않는다 (멱등). */
    fun revoke(at: OffsetDateTime) {
        if (revokedAt == null) {
            revokedAt = at
        }
    }

    fun isRevoked(): Boolean = revokedAt != null

    fun isExpired(at: OffsetDateTime): Boolean = !expiresAt.isAfter(at)

    /** 폐기되지 않았고 만료되지 않았을 때만 사용 가능하다. */
    fun isUsable(at: OffsetDateTime): Boolean = !isRevoked() && !isExpired(at)

    fun principalType(): PrincipalType =
        when {
            member != null -> PrincipalType.MEMBER
            admin != null -> PrincipalType.ADMIN
            else -> throw IllegalStateException("refresh_token의 주체가 없습니다 (member/admin 모두 null) — DB CHECK 제약과 불일치")
        }

    fun principalId(): Long {
        // 엔티티 클래스가 JPA 프록시 대상이라 open 프로퍼티다 — null 체크만으로는 스마트캐스트가
        // 되지 않으므로 로컬 val에 담아 안전하게 접근한다.
        val memberRef = member
        val adminRef = admin
        return when {
            memberRef != null -> memberRef.id ?: throw IllegalStateException("member.id가 null입니다 — 영속화되지 않은 엔티티")
            adminRef != null -> adminRef.id ?: throw IllegalStateException("admin.id가 null입니다 — 영속화되지 않은 엔티티")
            else -> throw IllegalStateException("refresh_token의 주체가 없습니다 (member/admin 모두 null) — DB CHECK 제약과 불일치")
        }
    }
}
