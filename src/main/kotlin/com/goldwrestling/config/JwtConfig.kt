package com.goldwrestling.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.spec.SecretKeySpec

/**
 * 자체 JWT(HS256 대칭키) 발급·검증 빈.
 *
 * `jwtProperties.secret`가 비어있거나 [MIN_SECRET_BYTES]바이트 미만이면 이 빈을 생성하는 시점에
 * [IllegalStateException]을 던져 기동을 막는다(T-02-09) — 시크릿 미주입 상태로 서비스가 떠서
 * "서명은 되는데 아무나 위조 가능한" 상태가 되는 것을 방지한다. 예외 메시지에는 시크릿 값을
 * 절대 포함하지 않는다.
 */
@Configuration
class JwtConfig(
    private val jwtProperties: JwtProperties,
) {
    private val secretKey: SecretKeySpec

    init {
        val secretBytes = jwtProperties.secret.toByteArray()
        check(secretBytes.size >= MIN_SECRET_BYTES) {
            "goldwrestling.jwt.secret(.env의 JWT_SECRET)이 비어있거나 ${MIN_SECRET_BYTES}바이트 미만입니다. " +
                "HS256 서명에는 최소 ${MIN_SECRET_BYTES}바이트 이상의 시크릿이 필요합니다."
        }
        secretKey = SecretKeySpec(secretBytes, "HmacSHA256")
    }

    /** 자체 JWT 발급용 인코더. */
    @Bean
    fun jwtEncoder(): JwtEncoder =
        NimbusJwtEncoder
            .withSecretKey(secretKey)
            .algorithm(MacAlgorithm.HS256)
            .build()

    /**
     * 자체 JWT 검증용 디코더. `macAlgorithm(HS256)`을 반드시 명시해 토큰 헤더의 `alg` 값을
     * 신뢰하지 않는다 — 알고리즘 confusion 공격(예: `alg: none`으로 조립한 토큰) 방어다(T-02-06).
     */
    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder
            .withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()

    companion object {
        private const val MIN_SECRET_BYTES = 32
    }
}
