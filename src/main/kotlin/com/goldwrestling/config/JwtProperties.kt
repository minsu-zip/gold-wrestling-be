package com.goldwrestling.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 자체 JWT(HS256 대칭키) 발급·검증 설정.
 *
 * `.env` 키 매핑:
 * - `secret` ← `JWT_SECRET` (서명용 시크릿, 최소 32바이트 이상)
 * - `accessTokenExpiryMinutes` ← `JWT_ACCESS_TOKEN_EXPIRY_MINUTES`
 * - `refreshTokenExpiryDays` ← `JWT_REFRESH_TOKEN_EXPIRY_DAYS`
 *
 * 기본값(D-033)은 시크릿 미주입 시 빈 문자열로 두어 `JwtConfig`의 길이 검증에서 기동을 막는다 —
 * 실값을 코드 기본값에 절대 넣지 않는다.
 */
@ConfigurationProperties(prefix = "goldwrestling.jwt")
data class JwtProperties(
    val secret: String = "",
    val accessTokenExpiryMinutes: Long = 30,
    val refreshTokenExpiryDays: Long = 14,
)
