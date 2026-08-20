package com.goldwrestling.config

import com.goldwrestling.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Instant
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

/**
 * `JwtConfig`가 발급·검증 왕복 및 세 가지 실패 경로(만료·다른 시크릿·alg none)를 실제로
 * 거부하는지 확인하는 통합테스트. `alg none` 케이스는 T-02-06(알고리즘 confusion 방어)의
 * 회귀 테스트다.
 */
@SpringBootTest(properties = ["goldwrestling.jwt.secret=test-only-jwt-secret-value-do-not-use-in-production"])
@Import(TestcontainersConfiguration::class)
class JwtConfigTest {
    @Autowired
    private lateinit var jwtEncoder: JwtEncoder

    @Autowired
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `발급한 토큰을 같은 디코더로 검증하면 subject와 커스텀 클레임이 그대로 돌아온다`() {
        // given
        val now = Instant.now()
        val claims =
            JwtClaimsSet
                .builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1_800))
                .subject("42")
                .claim("principalType", "MEMBER")
                .build()
        val token = issue(jwtEncoder, claims)

        // when
        val decoded = jwtDecoder.decode(token)

        // then
        assertThat(decoded.subject).isEqualTo("42")
        assertThat(decoded.getClaimAsString("principalType")).isEqualTo("MEMBER")
    }

    @Test
    fun `만료 시각이 과거인 토큰은 디코딩에서 예외가 난다`() {
        // given
        val now = Instant.now()
        val claims =
            JwtClaimsSet
                .builder()
                .issuedAt(now.minusSeconds(120))
                .expiresAt(now.minusSeconds(60))
                .subject("42")
                .build()
        val expiredToken = issue(jwtEncoder, claims)

        // when / then
        assertThatThrownBy { jwtDecoder.decode(expiredToken) }
            .isInstanceOf(JwtException::class.java)
    }

    @Test
    fun `다른 시크릿으로 서명한 토큰은 디코딩에서 예외가 난다`() {
        // given
        val otherSecretKey = SecretKeySpec("other-completely-different-jwt-secret-value".toByteArray(), "HmacSHA256")
        val otherEncoder =
            NimbusJwtEncoder
                .withSecretKey(otherSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build()
        val now = Instant.now()
        val claims =
            JwtClaimsSet
                .builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1_800))
                .subject("42")
                .build()
        val tokenFromOtherSecret = issue(otherEncoder, claims)

        // when / then
        assertThatThrownBy { jwtDecoder.decode(tokenFromOtherSecret) }
            .isInstanceOf(JwtException::class.java)
    }

    @Test
    fun `알고리즘을 none 으로 바꾼 토큰은 디코딩에서 예외가 난다`() {
        // given — macAlgorithm(HS256) 고정이 실제로 alg confusion을 막는지 확인하는 회귀 테스트(T-02-06)
        val header = base64UrlEncode("""{"alg":"none"}""")
        val payload = base64UrlEncode("""{"sub":"42","exp":9999999999}""")
        val noneAlgToken = "$header.$payload."

        // when / then
        assertThatThrownBy { jwtDecoder.decode(noneAlgToken) }
            .isInstanceOf(JwtException::class.java)
    }

    private fun issue(
        encoder: JwtEncoder,
        claims: JwtClaimsSet,
    ): String {
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    private fun base64UrlEncode(json: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
}
