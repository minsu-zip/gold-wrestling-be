package com.goldwrestling.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/**
 * `/api/auth/refresh`·`/api/auth/logout` 요청 DTO.
 *
 * 두 요청 모양이 필드 하나로 동일하더라도 하나로 합치지 않는다 — openapi 스키마에서 두 엔드포인트의
 * 계약이 각각 독립적으로 보여야 FE가 헷갈리지 않는다.
 */
@Schema(description = "access 토큰 갱신 요청")
data class RefreshTokenRequest(
    @field:NotBlank
    @field:Schema(description = "발급받은 refresh 토큰 원문")
    val refreshToken: String,
)

@Schema(description = "로그아웃 요청")
data class LogoutRequest(
    @field:NotBlank
    @field:Schema(description = "폐기할 refresh 토큰 원문")
    val refreshToken: String,
)
