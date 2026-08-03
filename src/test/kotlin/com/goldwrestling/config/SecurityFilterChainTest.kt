package com.goldwrestling.config

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.auth.PrincipalType
import com.goldwrestling.auth.TokenService
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime

/**
 * `SecurityConfig`의 URL 인가 규칙(공개 / `ROLE_MEMBER` / `ROLE_ADMIN`)이 실제 요청에서 그대로
 * 동작하는지 증명하는 필터체인 통합테스트(02-05, ROADMAP Phase 2 Note).
 *
 * 테스트 전용 컨트롤러([TestSecuredController])를 `@TestConfiguration`(`@Import`)으로만 등록해
 * 실제 도메인 컨트롤러를 오염시키지 않는다(`GlobalExceptionHandlerTest`와 동일 패턴). 경로는
 * 실제 인가 규칙(`/api/members` 하위, `/api/admin` 하위)에 그대로 걸리도록 `__test-secured` 하위로 둔다.
 *
 * `JwtAuthenticationFilterTest`가 이 클래스의 [TestEndpoints]를 재사용해 `@Import` 조합을 동일하게
 * 맞춘다(conventions §10.1 — 컨텍스트 캐시 재사용).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class, SecurityFilterChainTest.TestEndpoints::class)
@Transactional
class SecurityFilterChainTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var clock: Clock

    /**
     * `MutableTestClock`은 싱글턴 빈이라 이전 테스트가 밀어 둔 시각이 이어진다. `JwtDecoder`의 만료
     * 검증은 주입받은 [Clock]이 아니라 **실제 시스템 시각**과 비교하므로(RefreshTokenRotationTest 참고),
     * 매 테스트 시작 시 실제 현재 시각으로 되돌려야 여기서 발급한 토큰이 곧바로 "만료됨"으로 거부되지 않는다.
     */
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `api-system-health 는 인증 없이 200 이다`() {
        mockMvc.perform(get("/api/system/health")).andExpect(status().isOk)
    }

    @Test
    fun `actuator-health 는 인증 없이 200 이다`() {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk)
    }

    @Test
    fun `v3-api-docs 는 인증 없이 200 이다`() {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk)
    }

    @Test
    fun `api-auth-refresh 는 인증 없이 접근 가능하다`() {
        // 본문이 비어 있어 검증 실패(400)로 거부되더라도, 401(UNAUTHENTICATED)이 아니어야
        // "access 토큰 없이 호출 가능하다"는 계약이 지켜진 것이다.
        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":""}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `회원 전용 테스트 경로에 토큰 없이 접근하면 401 이고 problem+json 과 code UNAUTHENTICATED 다`() {
        mockMvc
            .perform(get("/api/members/__test-secured"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `관리자 전용 테스트 경로에 회원 토큰으로 접근하면 403 이고 code ACCESS_DENIED 다`() {
        val token = issueMemberToken()

        mockMvc
            .perform(get("/api/admin/__test-secured").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `관리자 전용 테스트 경로에 관리자 토큰으로 접근하면 200 이다`() {
        val token = issueAdminToken()

        mockMvc
            .perform(get("/api/admin/__test-secured").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `회원 전용 테스트 경로에 관리자 토큰으로 접근하면 403 이다`() {
        val token = issueAdminToken()

        mockMvc
            .perform(get("/api/members/__test-secured").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `매핑되지 않은 경로에 인증 없이 접근하면 401 이다`() {
        val result = mockMvc.perform(get("/completely-unmapped-path")).andReturn()

        assertThat(result.response.status).isEqualTo(401)
    }

    private fun issueMemberToken(): String {
        val branch = branchRepository.findByName("송파점")!!
        val member =
            memberRepository.saveAndFlush(
                Member(
                    branch = branch,
                    name = null,
                    phoneNumber = null,
                    kakaoId = System.nanoTime(),
                    createdAt = OffsetDateTime.now(clock),
                ),
            )
        return tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken
    }

    private fun issueAdminToken(): String {
        val admin =
            adminRepository.saveAndFlush(
                Admin(
                    name = "테스트관리자",
                    loginId = "test-admin-${System.nanoTime()}",
                    passwordHash = "{noop}test-password-hash",
                    createdAt = OffsetDateTime.now(clock),
                ),
            )
        return tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestEndpoints {
        @Bean
        fun testSecuredController(): TestSecuredController = TestSecuredController()
    }

    /**
     * `SecurityConfig`의 실제 URL 규칙(`/api/members` 하위 → `ROLE_MEMBER`, `/api/admin` 하위 →
     * `ROLE_ADMIN`)에 그대로 걸리도록 경로를 잡은 테스트 전용 컨트롤러. 프로덕션 컨트롤러가 아니므로
     * `docs/api/openapi.yaml`에는 영향을 주지 않는다(`@TestConfiguration`으로만 등록됨).
     */
    @RestController
    class TestSecuredController {
        @GetMapping("/api/members/__test-secured")
        fun memberSecured(
            @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        ): ResponseEntity<Map<String, Any?>> =
            ResponseEntity.ok(
                mapOf(
                    "principalId" to principal.principalId,
                    "memberStatus" to principal.memberStatus?.name,
                ),
            )

        @GetMapping("/api/admin/__test-secured")
        fun adminSecured(): ResponseEntity<Void> = ResponseEntity.ok().build()
    }
}
