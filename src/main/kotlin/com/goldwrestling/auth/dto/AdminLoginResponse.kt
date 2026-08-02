package com.goldwrestling.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "관리자 로그인 성공 응답 — 토큰과 관리자 요약을 함께 담는다 (D-026, 회원과 동일한 JWT 체계)")
data class AdminLoginResponse(
    @field:Schema(description = "발급된 access/refresh 토큰 쌍")
    val tokens: TokenPairResponse,
    @field:Schema(description = "관리자 요약")
    val admin: AdminSummary,
)

@Schema(description = "관리자 요약 — loginId·passwordHash는 담지 않는다")
data class AdminSummary(
    @field:Schema(description = "관리자 ID")
    val adminId: Long,
    @field:Schema(description = "관리자 이름")
    val name: String,
)
