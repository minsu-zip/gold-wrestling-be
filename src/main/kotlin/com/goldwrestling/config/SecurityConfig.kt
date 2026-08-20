package com.goldwrestling.config

import com.goldwrestling.auth.AuthenticationPrincipalResolver
import com.goldwrestling.auth.JwtAuthenticationFilter
import com.goldwrestling.common.error.ProblemDetailAccessDeniedHandler
import com.goldwrestling.common.error.ProblemDetailAuthenticationEntryPoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * 인증·인가 필터체인 배선(02-05, ROADMAP Phase 2 Note가 예고한 permitAll 뼈대 교체).
 *
 * **역할(공개/`ROLE_MEMBER`/`ROLE_ADMIN`) 구분만 이 클래스가 담당한다.** "회원 상태가 `ACTIVE`여야
 * 한다" 같은 조건은 여기서 표현하지 않는다 — `PENDING` 회원도 접근 가능해야 하는 엔드포인트
 * (`GET /api/members/me`, 온보딩 제출)가 있어 URL 패턴으로 예외를 또 열어주면 규칙이 더 복잡해진다.
 * 그런 조건은 서비스 계층의 `MemberStateGate`가 DB 현재 상태로 검사한다(D-040). 이 분업 때문에
 * 메서드 시큐리티 애노테이션(`@PreAuthorize` 등)은 이 프로젝트에서 쓰지 않는다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val corsProperties: CorsProperties,
    private val jwtDecoder: JwtDecoder,
    private val principalResolver: AuthenticationPrincipalResolver,
    private val authenticationEntryPoint: ProblemDetailAuthenticationEntryPoint,
    private val accessDeniedHandler: ProblemDetailAccessDeniedHandler,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            // 토큰 기반(무상태) API 이므로 세션 CSRF 토큰이 필요 없다.
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .addFilterBefore(
                JwtAuthenticationFilter(jwtDecoder, principalResolver),
                UsernamePasswordAuthenticationFilter::class.java,
            ).exceptionHandling {
                it
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            }.authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/api/system/**", "/actuator/health", "/actuator/info")
                    .permitAll()
                    // 이 줄이 빠지면 ./gradlew generateApiDocs(D-029)가 401로 실패해 API 계약
                    // 재생성 파이프라인이 통째로 깨진다(T-02-18) — 지우지 않는다.
                    .requestMatchers(
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                    ).permitAll()
                    // 로그인·토큰 갱신·로그아웃은 access 토큰 없이 호출된다(refresh 토큰 자체가 자격증명).
                    .requestMatchers("/api/auth/**")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/members/**")
                    .hasRole("MEMBER")
                    // 기본값은 거부다(T-02-17) — 새 엔드포인트를 추가하면서 위 규칙에 포함하는 것을
                    // 깜빡해도 permitAll로 조용히 열리는 대신 401로 드러난다.
                    .anyRequest()
                    .authenticated()
            }.build()

    private fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = corsProperties.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
                maxAge = 3600
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
