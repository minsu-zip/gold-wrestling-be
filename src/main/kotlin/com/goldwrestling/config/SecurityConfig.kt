package com.goldwrestling.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * 뼈대 단계의 보안 설정.
 *
 * 카카오 OAuth + JWT 인증은 인증 phase 에서 이 클래스에 추가한다.
 * 그때까지는 **모든 요청을 permitAll** 로 열어 두므로, 인증이 필요한 엔드포인트를 만들기 전에
 * 반드시 authorizeHttpRequests 규칙을 먼저 채워야 한다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val corsProperties: CorsProperties,
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
            // TODO(인증 phase): 도메인 엔드포인트 추가 시 역할별 인가 규칙으로 대체
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()

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
