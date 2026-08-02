package com.goldwrestling.auth

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.config.JwtProperties
import com.goldwrestling.config.SecurityFilterChainTest
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

/**
 * `JwtAuthenticationFilter`의 실패 경로(만료·다른 서명키·alg none·Bearer 접두 누락)와
 * 성공 경로(유효한 토큰 → principal, D-033 DB 상태 재조회)를 실제 HTTP 요청으로 증명하는
 * 통합테스트(02-05).
 *
 * `SecurityFilterChainTest.TestEndpoints`를 재사용해 `@Import` 조합을 동일하게 맞춘다
 * (conventions §10.1 — 컨텍스트 캐시 재사용). `/api/members/__test-secured`는 principal의
 * `principalId`/`memberStatus`를 그대로 JSON으로 돌려주므로 이 검증에 그대로 쓸 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class, SecurityFilterChainTest.TestEndpoints::class)
@Transactional
class JwtAuthenticationFilterTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var jwtProperties: JwtProperties

    @Autowired
    private lateinit var clock: Clock

    /** RefreshTokenRotationTest와 동일한 이유로 매 테스트 시작 시 실제 현재 시각으로 되돌린다. */
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `만료된 access 토큰으로 보호 경로에 접근하면 401 이고 problem+json 이다`() {
        // 클록을 accessTokenExpiryMinutes 보다 더 과거로 밀어 발급하면, exp(발급 시각+만료분)가
        // 실제 시스템 시각 기준으로 이미 지난 값이 된다 — JwtDecoder의 만료 검증은 실제 시각과 비교한다.
        (clock as MutableTestClock).setTo(Instant.now().minus(Duration.ofMinutes(jwtProperties.accessTokenExpiryMinutes + 1)))
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 9001L))
        val expiredToken = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        mockMvc
            .perform(get("/api/members/__test-secured").header(HttpHeaders.AUTHORIZATION, "Bearer $expiredToken"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    @Test
    fun `서명이 다른 토큰으로 접근하면 401 이고 problem+json 이다`() {
        val otherSecretKey = SecretKeySpec("other-completely-different-jwt-secret-value".toByteArray(), "HmacSHA256")
        val otherEncoder = NimbusJwtEncoder.withSecretKey(otherSecretKey).algorithm(MacAlgorithm.HS256).build()
        val now = Instant.now()
        val claims =
            JwtClaimsSet
                .builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1_800))
                .subject("1")
                .claim("principalType", "MEMBER")
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val tokenFromOtherSecret = otherEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue

        mockMvc
            .perform(get("/api/members/__test-secured").header(HttpHeaders.AUTHORIZATION, "Bearer $tokenFromOtherSecret"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    @Test
    fun `alg 를 none 으로 바꾼 토큰으로 접근하면 401 이고 problem+json 이다`() {
        // T-02-06(알고리즘 confusion 방어) 회귀 테스트 — JwtConfigTest와 동일한 조립 방식.
        val header = base64UrlEncode("""{"alg":"none"}""")
        val payload = base64UrlEncode("""{"sub":"1","principalType":"MEMBER","exp":9999999999}""")
        val noneAlgToken = "$header.$payload."

        mockMvc
            .perform(get("/api/members/__test-secured").header(HttpHeaders.AUTHORIZATION, "Bearer $noneAlgToken"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    @Test
    fun `Authorization 헤더가 Bearer 없이 토큰만 있으면 401 이다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 9002L))
        val token = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        mockMvc
            .perform(get("/api/members/__test-secured").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `유효한 토큰이면 컨트롤러가 받은 principal의 principalId가 토큰 subject와 같다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 9003L))
        val token = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        mockMvc
            .perform(get("/api/members/__test-secured").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.principalId").value(member.id!!.toInt()))
    }

    @Test
    fun `회원 상태를 DB에서 INACTIVE로 바꾸면 같은 access 토큰으로 접근했을 때 principal의 memberStatus가 INACTIVE로 보인다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 9004L))
        val token = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        member.status = MemberStatus.INACTIVE
        memberRepository.saveAndFlush(member)

        mockMvc
            .perform(get("/api/members/__test-secured").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.memberStatus").value("INACTIVE"))
    }

    private fun newMember(kakaoId: Long): Member {
        val branch = branchRepository.findByName("송파점")!!
        return Member(
            branch = branch,
            name = null,
            phoneNumber = null,
            kakaoId = kakaoId,
            createdAt = OffsetDateTime.now(clock),
        )
    }

    private fun base64UrlEncode(json: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
}
