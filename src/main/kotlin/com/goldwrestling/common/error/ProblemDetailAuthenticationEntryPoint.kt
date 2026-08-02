package com.goldwrestling.common.error

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/**
 * 인증되지 않은 요청이 보호된 경로에 접근했을 때 401 + `application/problem+json` +
 * `code=UNAUTHENTICATED`로 응답한다.
 *
 * `GlobalExceptionHandler.handleUnexpectedException`이 `AuthenticationException`을 의도적으로
 * 되던지는 짝이 이 클래스다 — 되던져진 예외는 서블릿 스택으로 전파되어 `ExceptionTranslationFilter`가
 * 이 지점으로 위임한다.
 *
 * [authException]의 메시지는 응답에 담지 않는다(conventions.md §8) — 내부 검증 실패 사유를 노출하지
 * 않고 고정된 사용자 대면 문구만 준다.
 */
@Component
class ProblemDetailAuthenticationEntryPoint(
    private val writer: ProblemDetailResponseWriter,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        writer.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "인증이 필요합니다.")
    }
}
