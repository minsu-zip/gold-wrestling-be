package com.goldwrestling.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Bearer access 토큰을 검증해 [SecurityContextHolder]에 인증 정보를 세팅하는 필터.
 *
 * **스프링 컴포넌트 스캔 대상 애노테이션을 붙이지 않는다.** 필터가 스프링 빈으로 등록되면 서블릿
 * 컨테이너가 자동으로 필터 체인에도 등록해, `SecurityConfig`가 `addFilterBefore`로 명시 배치한
 * 위치와 별개로 한 번 더 실행되는 문제가 생긴다. 그래서 `SecurityConfig`가 생성자 호출로 직접
 * 만들어 등록한다.
 *
 * **이 필터는 어떤 예외도 던지지 않는다.** `addFilterBefore(this, UsernamePasswordAuthenticationFilter::class.java)`
 * 위치는 `ExceptionTranslationFilter`보다 앞이라, 여기서 예외를 던지면 `GlobalExceptionHandler`도
 * Spring Security의 `AuthenticationEntryPoint`도 잡지 못하고 컨테이너 기본 HTML 에러 페이지가 나간다
 * (RESEARCH.md Pattern 1 / Pitfall 1). 검증 실패는 `SecurityContext`를 비운 채 다음 필터로 넘기기만
 * 하고, 실제 401/403 판단과 응답 작성은 뒤에 있는 `AuthorizationFilter`(→ `ExceptionTranslationFilter`가
 * 감쌈)에 맡긴다 — 임의 변형 금지.
 */
class JwtAuthenticationFilter(
    private val jwtDecoder: JwtDecoder,
    private val principalResolver: AuthenticationPrincipalResolver,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authentication = extractBearerToken(request)?.let { authenticate(it, request) }
        if (authentication != null) {
            SecurityContextHolder.getContext().authentication = authentication
        }
        // SecurityContextHolder를 명시적으로 clear하지 않는다 — 요청 종료 시
        // SecurityContextHolderFilter 계열이 정리한다.
        filterChain.doFilter(request, response)
    }

    private fun authenticate(
        token: String,
        request: HttpServletRequest,
    ): UsernamePasswordAuthenticationToken? {
        val jwt =
            try {
                jwtDecoder.decode(token)
            } catch (ex: JwtException) {
                // 서명 위조 · 만료 · 형식 오류 등. 토큰 값 자체는 로그에 남기지 않는다.
                // 401 응답은 여기서 만들지 않는다 — 뒤의 AuthorizationFilter가 미인증으로 판단해 만든다.
                filterLogger.debug("JWT 검증 실패 — 미인증으로 처리합니다: {}", ex.message)
                return null
            }

        val principalId = jwt.subject?.toLongOrNull() ?: return null
        val principalType =
            jwt.getClaimAsString("principalType")?.let { raw ->
                runCatching { PrincipalType.valueOf(raw) }.getOrNull()
            } ?: return null

        val principal = principalResolver.resolve(principalType, principalId) ?: return null

        return UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_${principalType.name}")),
        ).apply {
            details = WebAuthenticationDetailsSource().buildDetails(request)
        }
    }

    private fun extractBearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null
        return header.removePrefix(BEARER_PREFIX).trim().ifBlank { null }
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "

        // 필터가 스프링 빈이 아니라 SecurityConfig가 직접 생성하므로, 이름을 GenericFilterBean이
        // 이미 갖고 있는 protected `logger` 필드와 겹치지 않게 둔다.
        private val filterLogger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    }
}
