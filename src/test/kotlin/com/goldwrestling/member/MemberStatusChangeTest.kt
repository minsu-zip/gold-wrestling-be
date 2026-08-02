package com.goldwrestling.member

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.auth.PrincipalType
import com.goldwrestling.auth.TokenService
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime

/**
 * `PATCH /api/admin/members/{memberId}/status`의 상태 변경·강제 로그아웃 규칙(MEMBER-03, D-033,
 * D-034, D-044)을 검증하는 통합테스트. 애노테이션 조합은 기존 Phase 2 통합테스트와 동일하게
 * `@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)`를 쓴다
 * (conventions §10.1 — 컨텍스트 캐시 재사용).
 *
 * "기존 access 토큰이 상태 게이트에 막힌다"(D-033 노트)의 최종 회귀 방어선을 증명하기 위해,
 * `MemberStateGate.requireActive`를 실제로 호출하는 테스트 전용 컨트롤러([TestGatedEndpoints])를
 * `@TestConfiguration`으로 등록한다(`SecurityFilterChainTest`와 동일 패턴).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class, MemberStatusChangeTest.TestGatedEndpoints::class)
@Transactional
class MemberStatusChangeTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var clock: Clock

    /**
     * `MutableTestClock`은 싱글턴 빈이라 이전 테스트가 밀어 둔 시각이 이어진다. `JwtDecoder`의 만료
     * 검증은 실제 시스템 시각과 비교하므로(RefreshTokenRotationTest 참고), 매 테스트 시작 시 실제
     * 현재 시각으로 되돌린다.
     */
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `ACTIVE에서 ON_LEAVE로 바꾸면 200이고 status가 ON_LEAVE다`() {
        val member = persistMember(kakaoId = 9201L, status = MemberStatus.ACTIVE)
        val adminToken = adminAccessToken(loginId = "admin-status-onleave")

        mockMvc
            .perform(statusRequest(member.id!!, adminToken, "ON_LEAVE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ON_LEAVE"))
    }

    @Test
    fun `ACTIVE에서 ON_LEAVE 전환 시 그 회원의 refresh 토큰이 전부 폐기된다`() {
        val member = persistMember(kakaoId = 9202L, status = MemberStatus.ACTIVE)
        val tokens = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)
        val adminToken = adminAccessToken(loginId = "admin-status-onleave-revoke")

        mockMvc.perform(statusRequest(member.id!!, adminToken, "ON_LEAVE")).andExpect(status().isOk)

        mockMvc
            .perform(refreshRequest(tokens.refreshToken))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
    }

    @Test
    fun `ACTIVE에서 INACTIVE 전환 시에도 refresh가 폐기된다`() {
        val member = persistMember(kakaoId = 9203L, status = MemberStatus.ACTIVE)
        val tokens = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)
        val adminToken = adminAccessToken(loginId = "admin-status-inactive-revoke")

        mockMvc.perform(statusRequest(member.id!!, adminToken, "INACTIVE")).andExpect(status().isOk)

        mockMvc
            .perform(refreshRequest(tokens.refreshToken))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
    }

    @Test
    fun `ON_LEAVE에서 ACTIVE 전환 시에는 refresh가 폐기되지 않는다`() {
        val member = persistMember(kakaoId = 9204L, status = MemberStatus.ON_LEAVE)
        val tokens = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)
        val adminToken = adminAccessToken(loginId = "admin-status-active-no-revoke")

        mockMvc.perform(statusRequest(member.id!!, adminToken, "ACTIVE")).andExpect(status().isOk)

        mockMvc.perform(refreshRequest(tokens.refreshToken)).andExpect(status().isOk)
    }

    @Test
    fun `INACTIVE에서 PENDING 전환 시 rejectionReason이 null로 초기화된다`() {
        val member =
            persistMember(kakaoId = 9205L, status = MemberStatus.INACTIVE, rejectionReason = "자격 미달")
        val adminToken = adminAccessToken(loginId = "admin-status-pending-reset")

        mockMvc
            .perform(statusRequest(member.id!!, adminToken, "PENDING"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.rejectionReason").doesNotExist())
    }

    @Test
    fun `INACTIVE에서 PENDING 전환 시 refresh가 폐기된다`() {
        val member =
            persistMember(kakaoId = 9206L, status = MemberStatus.INACTIVE, rejectionReason = "자격 미달")
        val tokens = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)
        val adminToken = adminAccessToken(loginId = "admin-status-pending-revoke")

        mockMvc.perform(statusRequest(member.id!!, adminToken, "PENDING")).andExpect(status().isOk)

        mockMvc
            .perform(refreshRequest(tokens.refreshToken))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
    }

    @Test
    fun `같은 상태로 바꾸는 요청은 200이고 부작용이 없다`() {
        val member = persistMember(kakaoId = 9207L, status = MemberStatus.ACTIVE)
        val adminToken = adminAccessToken(loginId = "admin-status-idempotent")

        mockMvc.perform(statusRequest(member.id!!, adminToken, "ACTIVE")).andExpect(status().isOk)
        mockMvc
            .perform(statusRequest(member.id!!, adminToken, "ACTIVE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))

        memberRepository.flush()
        assertThat(memberRepository.count()).isEqualTo(1)
    }

    @Test
    fun `status 값이 enum에 없는 문자열이면 400과 MALFORMED_REQUEST다`() {
        val member = persistMember(kakaoId = 9208L, status = MemberStatus.ACTIVE)
        val adminToken = adminAccessToken(loginId = "admin-status-malformed")

        mockMvc
            .perform(
                patch("/api/admin/members/${member.id}/status")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"NOT_A_REAL_STATUS"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
    }

    /**
     * `status`는 Kotlin non-null 생성자 파라미터라 JSON에 키 자체가 없으면 `@Valid`가 실행되기 전
     * jackson-module-kotlin의 역직렬화 단계에서 이미 실패한다(`MissingKotlinParameterException` →
     * `HttpMessageNotReadableException`) — `ErrorCode.MALFORMED_REQUEST`의 KDoc이 명시하는
     * "필수 파라미터 누락"에 해당한다. `@field:NotNull`(`VALIDATION_FAILED`)은 필드가 "존재하되
     * 값이 비었을 때"만 실행되는데 nullable하지 않은 타입에는 그 상태 자체가 있을 수 없다 —
     * `TokenControllerTest`의 "본문이 JSON이 아니면 MALFORMED_REQUEST" 케이스와 같은 계열이다.
     */
    @Test
    fun `status 필드가 없으면 400과 MALFORMED_REQUEST다`() {
        val member = persistMember(kakaoId = 9209L, status = MemberStatus.ACTIVE)
        val adminToken = adminAccessToken(loginId = "admin-status-missing")

        mockMvc
            .perform(
                patch("/api/admin/members/${member.id}/status")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
    }

    @Test
    fun `존재하지 않는 memberId면 404와 MEMBER_NOT_FOUND다`() {
        val adminToken = adminAccessToken(loginId = "admin-status-not-found")

        mockMvc
            .perform(statusRequest(999999999L, adminToken, "ACTIVE"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
    }

    @Test
    fun `회원 토큰으로 호출하면 403이다`() {
        val target = persistMember(kakaoId = 9210L, status = MemberStatus.ACTIVE)
        val requester = persistMember(kakaoId = 9211L, status = MemberStatus.ACTIVE)
        val memberToken = memberAccessToken(requester)

        mockMvc
            .perform(statusRequest(target.id!!, memberToken, "ON_LEAVE"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `상태를 ON_LEAVE로 바꾼 뒤 기존 access 토큰으로 게이트 걸린 경로를 호출하면 403과 MEMBER_NOT_ACTIVE다`() {
        val member = persistMember(kakaoId = 9212L, status = MemberStatus.ACTIVE)
        val memberToken = memberAccessToken(member)
        val adminToken = adminAccessToken(loginId = "admin-status-gate-block")

        mockMvc
            .perform(get("/api/members/__gated").header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken"))
            .andExpect(status().isOk)

        mockMvc.perform(statusRequest(member.id!!, adminToken, "ON_LEAVE")).andExpect(status().isOk)

        mockMvc
            .perform(get("/api/members/__gated").header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_ACTIVE"))
    }

    private fun statusRequest(
        memberId: Long,
        token: String,
        status: String,
    ) = patch("/api/admin/members/$memberId/status")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"status":"$status"}""")

    private fun refreshRequest(refreshToken: String) =
        post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"refreshToken":"$refreshToken"}""")

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(
        kakaoId: Long,
        status: MemberStatus,
        rejectionReason: String? = null,
    ): Member =
        memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "회원$kakaoId",
                phoneNumber = "010$kakaoId",
                status = status,
                kakaoId = kakaoId,
                rejectionReason = rejectionReason,
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun persistAdmin(loginId: String): Admin =
        adminRepository.saveAndFlush(
            Admin(
                name = "관리자",
                loginId = loginId,
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun adminAccessToken(loginId: String): String {
        val admin = persistAdmin(loginId)
        return tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
    }

    private fun memberAccessToken(member: Member): String = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

    @TestConfiguration(proxyBeanMethods = false)
    class TestGatedEndpoints {
        @Bean
        fun testGatedController(memberStateGate: MemberStateGate): TestGatedController = TestGatedController(memberStateGate)
    }

    /**
     * 이 phase에서 `MemberStateGate.requireActive`를 실제로 호출하는 프로덕션 엔드포인트가 아직
     * 없으므로(예약·이용권은 이후 phase), 상태 게이트의 즉시 반영을 증명하기 위한 테스트 전용
     * 컨트롤러다(`SecurityFilterChainTest`와 동일 패턴). `@TestConfiguration`으로만 등록되어
     * `docs/api/openapi.yaml`에는 영향을 주지 않는다.
     */
    @RestController
    class TestGatedController(
        private val memberStateGate: MemberStateGate,
    ) {
        @GetMapping("/api/members/__gated")
        fun gated(
            @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        ): ResponseEntity<Void> {
            memberStateGate.requireActive(principal)
            return ResponseEntity.ok().build()
        }
    }
}
