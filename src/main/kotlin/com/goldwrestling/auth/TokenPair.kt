package com.goldwrestling.auth

import java.time.OffsetDateTime

/**
 * 발급된 access/refresh 토큰 한 쌍을 나르는 값 객체.
 *
 * `data class` 금지 규칙(conventions.md §3)은 JPA `@Entity`에 적용되는 규약이다 — 이 클래스는
 * 영속화되지 않고, 지연 로딩 프록시·양방향 연관과 충돌할 일이 없어 `data class`를 그대로 쓴다.
 *
 * [refreshToken]에는 원문을 담는다 — DB에는 해시만 저장되므로([TokenService]), 클라이언트에게
 * 돌려줄 수 있는 유일한 시점이 발급 직후인 이 객체다.
 */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: OffsetDateTime,
    val refreshTokenExpiresAt: OffsetDateTime,
)
