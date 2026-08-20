package com.goldwrestling.auth.dto

import com.goldwrestling.auth.TokenPair
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "발급된 access/refresh 토큰 쌍")
data class TokenPairResponse(
    @field:Schema(description = "access 토큰(JWT)")
    val accessToken: String,
    @field:Schema(description = "refresh 토큰(다음 회전에 사용)")
    val refreshToken: String,
    @field:Schema(description = "access 토큰 만료 시각")
    val accessTokenExpiresAt: OffsetDateTime,
    @field:Schema(description = "refresh 토큰 만료 시각")
    val refreshTokenExpiresAt: OffsetDateTime,
) {
    companion object {
        fun from(pair: TokenPair): TokenPairResponse =
            TokenPairResponse(
                accessToken = pair.accessToken,
                refreshToken = pair.refreshToken,
                accessTokenExpiresAt = pair.accessTokenExpiresAt,
                refreshTokenExpiresAt = pair.refreshTokenExpiresAt,
            )
    }
}
