package com.goldwrestling.auth

import com.goldwrestling.admin.Admin
import com.goldwrestling.member.Member
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    fun findAllByMemberAndRevokedAtIsNull(member: Member): List<RefreshToken>

    fun findAllByAdminAndRevokedAtIsNull(admin: Admin): List<RefreshToken>
}
