package com.goldwrestling.member

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.auth.PrincipalType
import com.goldwrestling.auth.TokenService
import com.goldwrestling.auth.kakao.KakaoApiClient
import com.goldwrestling.auth.kakao.KakaoTokenResponse
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime

/**
 * `POST /api/admin/members/{memberId}/approval`·`/rejection`의 승인·거절 규칙(MEMBER-01,
 * policies §5.1·§5.2, D-034, D-044)을 검증하는 통합테스트. 애노테이션 조합은 기존 Phase 2
 * 통합테스트와 동일하게 `@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)`를
 * 쓴다(conventions §10.1 — 컨텍스트 캐시 재사용).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class MemberApprovalTest {
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

    @MockitoBean
    private lateinit var kakaoApiClient: KakaoApiClient

    /**
     * `MutableTestClock`은 싱글턴 빈이라 이전 테스트가 밀어 둔 시각이 이어진다. `JwtDecoder`의 만료
     * 검증은 실제 시스템 시각과 비교하므로(RefreshTokenRotationTest 참고), 매 테스트 시작 시 실제
     * 현재 시각으로 되돌린다.
     */
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    // ---------- 승인 ----------

    @Test
    fun `온보딩 완료 PENDING 회원 승인이 200이고 status가 ACTIVE로 바뀐다`() {
        val member =
            persistMember(kakaoId = 9001L, name = "홍길동", phoneNumber = "01011112222", status = MemberStatus.PENDING)
        val adminToken = adminAccessToken(loginId = "admin-approve-ok")

        mockMvc
            .perform(approveRequest(member.id!!, adminToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `승인된 회원은 상태 게이트를 통과한다`() {
        val member =
            persistMember(kakaoId = 9002L, name = "김철수", phoneNumber = "01022223333", status = MemberStatus.PENDING)
        val adminToken = adminAccessToken(loginId = "admin-approve-gate")

        mockMvc.perform(approveRequest(member.id!!, adminToken)).andExpect(status().isOk)

        memberRepository.flush()
        val statusAfter = memberRepository.findById(member.id!!).orElseThrow().status
        assertThat(statusAfter).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    fun `승인은 refresh 토큰을 폐기하지 않아 승인 후 같은 refresh로 갱신이 200이다`() {
        val member =
            persistMember(kakaoId = 9003L, name = "이영희", phoneNumber = "01033334444", status = MemberStatus.PENDING)
        val tokens = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)
        val adminToken = adminAccessToken(loginId = "admin-approve-no-revoke")

        mockMvc.perform(approveRequest(member.id!!, adminToken)).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"${tokens.refreshToken}"}"""),
            ).andExpect(status().isOk)
    }

    @Test
    fun `온보딩 미완료 PENDING 회원 승인이 409와 MEMBER_STATE_CONFLICT다`() {
        val member = persistMember(kakaoId = 9004L, name = null, phoneNumber = null, status = MemberStatus.PENDING)
        val adminToken = adminAccessToken(loginId = "admin-approve-incomplete")

        mockMvc
            .perform(approveRequest(member.id!!, adminToken))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MEMBER_STATE_CONFLICT"))
    }

    @Test
    fun `이미 ACTIVE인 회원 승인이 409와 MEMBER_STATE_CONFLICT다`() {
        val member =
            persistMember(kakaoId = 9005L, name = "박영희", phoneNumber = "01044445555", status = MemberStatus.ACTIVE)
        val adminToken = adminAccessToken(loginId = "admin-approve-already-active")

        mockMvc
            .perform(approveRequest(member.id!!, adminToken))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MEMBER_STATE_CONFLICT"))
    }

    @Test
    fun `INACTIVE 회원 승인이 409와 MEMBER_STATE_CONFLICT다`() {
        val member =
            persistMember(
                kakaoId = 9006L,
                name = "최민수",
                phoneNumber = "01055556666",
                status = MemberStatus.INACTIVE,
                rejectionReason = "자격 미달",
            )
        val adminToken = adminAccessToken(loginId = "admin-approve-inactive")

        mockMvc
            .perform(approveRequest(member.id!!, adminToken))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MEMBER_STATE_CONFLICT"))
    }

    @Test
    fun `존재하지 않는 memberId 승인이 404와 MEMBER_NOT_FOUND다`() {
        val adminToken = adminAccessToken(loginId = "admin-approve-not-found")

        mockMvc
            .perform(approveRequest(999999999L, adminToken))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
    }

    @Test
    fun `회원 토큰으로 승인을 호출하면 403이다`() {
        val target =
            persistMember(kakaoId = 9007L, name = "대상", phoneNumber = "01066667777", status = MemberStatus.PENDING)
        val requester =
            persistMember(kakaoId = 9008L, name = "요청자", phoneNumber = "01077778888", status = MemberStatus.ACTIVE)
        val memberToken = memberAccessToken(requester)

        mockMvc
            .perform(approveRequest(target.id!!, memberToken))
            .andExpect(status().isForbidden)
    }

    // ---------- 거절 ----------

    @Test
    fun `온보딩 완료 PENDING 회원을 사유와 함께 거절하면 200이고 status INACTIVE, rejectionReason이 저장된다`() {
        val member =
            persistMember(kakaoId = 9101L, name = "거절대상", phoneNumber = "01088889999", status = MemberStatus.PENDING)
        val adminToken = adminAccessToken(loginId = "admin-reject-ok")

        mockMvc
            .perform(rejectRequest(member.id!!, adminToken, "제출한 정보가 실제와 다릅니다."))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("INACTIVE"))
            .andExpect(jsonPath("$.rejectionReason").value("제출한 정보가 실제와 다릅니다."))
    }

    @Test
    fun `거절 직후 그 회원의 refresh 토큰으로 재발급을 시도하면 401과 REFRESH_TOKEN_INVALID다`() {
        val member =
            persistMember(kakaoId = 9102L, name = "거절대상2", phoneNumber = "01011119999", status = MemberStatus.PENDING)
        val tokens = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)
        val adminToken = adminAccessToken(loginId = "admin-reject-revoke")

        mockMvc.perform(rejectRequest(member.id!!, adminToken, "자격 미달")).andExpect(status().isOk)

        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"${tokens.refreshToken}"}"""),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
    }

    @Test
    fun `거절된 회원이 카카오 재로그인하면 응답의 rejected가 true다`() {
        val member =
            persistMember(kakaoId = 9103L, name = "거절대상3", phoneNumber = "01022229999", status = MemberStatus.PENDING)
        val adminToken = adminAccessToken(loginId = "admin-reject-relogin")

        mockMvc.perform(rejectRequest(member.id!!, adminToken, "자격 미달")).andExpect(status().isOk)

        given(kakaoApiClient.exchangeToken(anyString())).willReturn(dummyTokenResponse())
        given(kakaoApiClient.fetchKakaoId(anyString())).willReturn(9103L)

        mockMvc
            .perform(
                post("/api/auth/kakao/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"code":"any-code"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.member.rejected").value(true))
    }

    @Test
    fun `거절된 회원의 GET api-members-me 응답에 rejected true이고 rejectionReason 키가 없다`() {
        val member =
            persistMember(kakaoId = 9104L, name = "거절대상4", phoneNumber = "01033339999", status = MemberStatus.PENDING)
        val memberToken = memberAccessToken(member)
        val adminToken = adminAccessToken(loginId = "admin-reject-me")

        mockMvc.perform(rejectRequest(member.id!!, adminToken, "자격 미달")).andExpect(status().isOk)

        mockMvc
            .perform(
                get("/api/members/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.rejected").value(true))
            .andExpect(jsonPath("$.rejectionReason").doesNotExist())
    }

    @Test
    fun `사유가 공백이면 400과 VALIDATION_FAILED이고 상태가 바뀌지 않는다`() {
        val member =
            persistMember(kakaoId = 9105L, name = "거절대상5", phoneNumber = "01044449999", status = MemberStatus.PENDING)
        val adminToken = adminAccessToken(loginId = "admin-reject-blank")

        mockMvc
            .perform(rejectRequest(member.id!!, adminToken, "   "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        memberRepository.flush()
        val statusAfter = memberRepository.findById(member.id!!).orElseThrow().status
        assertThat(statusAfter).isEqualTo(MemberStatus.PENDING)
    }

    @Test
    fun `사유가 501자 이상이면 400과 VALIDATION_FAILED다`() {
        val member =
            persistMember(kakaoId = 9106L, name = "거절대상6", phoneNumber = "01055559999", status = MemberStatus.PENDING)
        val adminToken = adminAccessToken(loginId = "admin-reject-toolong")
        val longReason = "가".repeat(501)

        mockMvc
            .perform(rejectRequest(member.id!!, adminToken, longReason))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `PENDING이 아닌 회원 거절이 409와 MEMBER_STATE_CONFLICT다`() {
        val member =
            persistMember(kakaoId = 9107L, name = "거절대상7", phoneNumber = "01066669999", status = MemberStatus.ACTIVE)
        val adminToken = adminAccessToken(loginId = "admin-reject-not-pending")

        mockMvc
            .perform(rejectRequest(member.id!!, adminToken, "자격 미달"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MEMBER_STATE_CONFLICT"))
    }

    private fun approveRequest(
        memberId: Long,
        token: String,
    ) = post("/api/admin/members/$memberId/approval")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun rejectRequest(
        memberId: Long,
        token: String,
        reason: String,
    ) = post("/api/admin/members/$memberId/rejection")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"reason":${jsonQuote(reason)}}""")

    private fun jsonQuote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun dummyTokenResponse(): KakaoTokenResponse =
        KakaoTokenResponse(
            accessToken = "kakao-access-token-for-test",
            tokenType = "bearer",
            expiresIn = 21599,
            refreshToken = null,
            scope = null,
        )

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(
        kakaoId: Long,
        name: String?,
        phoneNumber: String?,
        status: MemberStatus,
        rejectionReason: String? = null,
    ): Member =
        memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = name,
                phoneNumber = phoneNumber,
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
}
