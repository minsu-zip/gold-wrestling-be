package com.goldwrestling.auth

import com.goldwrestling.auth.dto.LogoutRequest
import com.goldwrestling.auth.dto.RefreshTokenRequest
import com.goldwrestling.auth.dto.TokenPairResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * refresh 토큰 자체가 자격증명이므로 이 두 엔드포인트는 access 토큰 없이 호출된다 —
 * `SecurityConfig`에서 `/api/auth` 하위 전체 경로가 permitAll인 이유(02-05).
 *
 * 도메인 예외를 그대로 던지고 `try-catch`로 응답을 만들지 않는다 — `GlobalExceptionHandler`가
 * `ProblemDetail`로 변환한다(D-017).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "auth", description = "인증·토큰")
class TokenController(
    private val tokenService: TokenService,
) {
    @PostMapping("/refresh")
    @Operation(summary = "access 토큰 갱신 (refresh 회전)")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest,
    ): TokenPairResponse = TokenPairResponse.from(tokenService.rotate(request.refreshToken))

    @PostMapping("/logout")
    @Operation(summary = "로그아웃 (refresh 토큰 폐기)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
    ) {
        tokenService.revoke(request.refreshToken)
    }
}
