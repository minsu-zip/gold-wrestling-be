package com.goldwrestling.auth

import com.goldwrestling.auth.dto.AdminLoginRequest
import com.goldwrestling.auth.dto.AdminLoginResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `SecurityConfig`에서 `/api/auth` 하위 전체 경로가 permitAll이다(02-05) — 로그인 전이라 당연히
 * 인증 없이 호출돼야 한다.
 *
 * 도메인 예외([InvalidCredentialsException])를 그대로 던지고 `try-catch`로 응답을 조립하지
 * 않는다 — `GlobalExceptionHandler`가 `ProblemDetail`로 변환한다(D-017).
 */
@RestController
@RequestMapping("/api/auth/admin")
@Tag(name = "auth", description = "인증·토큰")
class AdminAuthController(
    private val adminAuthService: AdminAuthService,
) {
    @PostMapping("/login")
    @Operation(summary = "관리자 ID/PW 로그인 (카카오 연동 없음, 회원과 동일한 JWT 체계)")
    fun login(
        @Valid @RequestBody request: AdminLoginRequest,
    ): AdminLoginResponse = adminAuthService.login(request.loginId, request.password)
}
