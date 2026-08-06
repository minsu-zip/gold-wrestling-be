package com.goldwrestling.auth

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.auth.kakao.KakaoApiClient
import com.goldwrestling.auth.kakao.KakaoAuthFailedException
import com.goldwrestling.auth.kakao.KakaoTokenResponse
import com.goldwrestling.auth.kakao.KakaoUnavailableException
import com.goldwrestling.auth.kakao.KakaoUserProfile
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.support.TestClockConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

/**
 * `POST /api/auth/kakao/login`의 회원 생성·재로그인·온보딩/거절 식별·실패 응답 계약을 검증하는
 * 통합테스트다. 카카오 API 자체의 HTTP 동작(폼 인코딩, 상태 코드 번역)은 [KakaoApiClientTest]가
 * 이미 검증했으므로, 여기서는 [KakaoApiClient]를 [MockitoBean]으로 대체해 **로그인 플로우와 회원
 * 생성 규칙**에만 집중한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class KakaoAuthControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var clock: Clock

    @MockitoBean
    private lateinit var kakaoApiClient: KakaoApiClient

    @Test
    fun `처음 보는 카카오 계정으로 로그인하면 200이고 PENDING 회원이 생성되며 onboardingCompleted는 false다`() {
        stubKakaoLogin(kakaoId = 3001L)

        mockMvc
            .perform(loginRequest("auth-code"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.member.status").value("PENDING"))
            .andExpect(jsonPath("$.member.onboardingCompleted").value(false))
            .andExpect(jsonPath("$.member.rejected").value(false))
            .andExpect(jsonPath("$.tokens.accessToken").exists())
            .andExpect(jsonPath("$.tokens.refreshToken").exists())
            .andExpect(jsonPath("$.member.rejectionReason").doesNotExist())

        val created = memberRepository.findByKakaoId(3001L)
        assertEquals(1, memberRepository.count())
        assertEquals(OffsetDateTime.now(clock), created!!.createdAt)
        assertEquals("송파점", created.branch.name)
    }

    @Test
    fun `같은 kakaoId로 두 번 로그인하면 회원 수가 늘지 않고 같은 memberId가 반환된다`() {
        stubKakaoLogin(kakaoId = 3002L)

        val firstMemberId =
            mockMvc
                .perform(loginRequest("auth-code-1"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
                .let { extractMemberId(it) }

        val secondMemberId =
            mockMvc
                .perform(loginRequest("auth-code-2"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
                .let { extractMemberId(it) }

        assertEquals(firstMemberId, secondMemberId)
        assertEquals(1, memberRepository.count())
    }

    @Test
    fun `온보딩을 마치지 않은 회원이 재로그인하면 onboardingCompleted는 여전히 false다`() {
        stubKakaoLogin(kakaoId = 3003L)
        mockMvc.perform(loginRequest("auth-code")).andExpect(status().isOk)

        mockMvc
            .perform(loginRequest("auth-code-again"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.member.onboardingCompleted").value(false))
    }

    @Test
    fun `온보딩을 마친 PENDING 회원이 로그인하면 onboardingCompleted true, status PENDING, rejected false다`() {
        val member = persistMember(kakaoId = 3004L, name = "홍길동", phoneNumber = "01012345678", status = MemberStatus.PENDING)
        given(kakaoApiClient.fetchUserProfile(anyString())).willReturn(KakaoUserProfile(member.kakaoId, null, null))
        given(kakaoApiClient.exchangeToken(anyString())).willReturn(dummyTokenResponse())

        mockMvc
            .perform(loginRequest("auth-code"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.member.status").value("PENDING"))
            .andExpect(jsonPath("$.member.onboardingCompleted").value(true))
            .andExpect(jsonPath("$.member.rejected").value(false))
    }

    @Test
    fun `거절 사유가 있는 INACTIVE 회원이 로그인하면 rejected는 true고 사유 원문은 응답에 없다`() {
        val member =
            persistMember(
                kakaoId = 3005L,
                name = "김철수",
                phoneNumber = "01099998888",
                status = MemberStatus.INACTIVE,
                rejectionReason = "내부 관리자 메모: 자격 미달",
            )
        given(kakaoApiClient.fetchUserProfile(anyString())).willReturn(KakaoUserProfile(member.kakaoId, null, null))
        given(kakaoApiClient.exchangeToken(anyString())).willReturn(dummyTokenResponse())

        mockMvc
            .perform(loginRequest("auth-code"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.member.rejected").value(true))
            .andExpect(jsonPath("$.member.rejectionReason").doesNotExist())
    }

    @Test
    fun `거절 사유가 없는 INACTIVE 회원은 rejected가 false다`() {
        val member =
            persistMember(
                kakaoId = 3006L,
                name = "박영희",
                phoneNumber = "01055557777",
                status = MemberStatus.INACTIVE,
                rejectionReason = null,
            )
        given(kakaoApiClient.fetchUserProfile(anyString())).willReturn(KakaoUserProfile(member.kakaoId, null, null))
        given(kakaoApiClient.exchangeToken(anyString())).willReturn(dummyTokenResponse())

        mockMvc
            .perform(loginRequest("auth-code"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.member.rejected").value(false))
    }

    @Test
    fun `code가 비어 있으면 400과 VALIDATION_FAILED를 반환한다`() {
        mockMvc
            .perform(loginRequest(""))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `카카오가 인가 코드를 거부하면 401과 KAKAO_AUTH_FAILED를 반환하고 회원이 생성되지 않는다`() {
        willThrow(KakaoAuthFailedException()).given(kakaoApiClient).exchangeToken(anyString())
        val before = memberRepository.count()

        mockMvc
            .perform(loginRequest("bad-code"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("KAKAO_AUTH_FAILED"))

        assertEquals(before, memberRepository.count())
    }

    @Test
    fun `카카오가 5xx면 502와 KAKAO_UNAVAILABLE을 반환하고 회원이 생성되지 않는다`() {
        willThrow(KakaoUnavailableException()).given(kakaoApiClient).exchangeToken(anyString())
        val before = memberRepository.count()

        mockMvc
            .perform(loginRequest("any-code"))
            .andExpect(status().isBadGateway)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("KAKAO_UNAVAILABLE"))

        assertEquals(before, memberRepository.count())
    }

    @Test
    fun `기본 지점이 DB에 없으면 로그인이 500과 INTERNAL_ERROR로 실패하고 회원이 생성되지 않는다`() {
        branchRepository.deleteAll()
        stubKakaoLogin(kakaoId = 3999L)
        val before = memberRepository.count()

        mockMvc
            .perform(loginRequest("any-code"))
            .andExpect(status().isInternalServerError)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))

        assertEquals(before, memberRepository.count())
    }

    private fun stubKakaoLogin(kakaoId: Long) {
        given(kakaoApiClient.exchangeToken(anyString())).willReturn(dummyTokenResponse())
        given(kakaoApiClient.fetchUserProfile(anyString())).willReturn(KakaoUserProfile(kakaoId, null, null))
    }

    private fun dummyTokenResponse(): KakaoTokenResponse =
        KakaoTokenResponse(
            accessToken = "kakao-access-token-for-test",
            tokenType = "bearer",
            expiresIn = 21599,
            refreshToken = null,
            scope = null,
        )

    private fun persistMember(
        kakaoId: Long,
        name: String?,
        phoneNumber: String?,
        status: MemberStatus,
        rejectionReason: String? = null,
    ): Member {
        val branch = branchRepository.findByName("송파점")!!
        return memberRepository.saveAndFlush(
            Member(
                branch = branch,
                name = name,
                phoneNumber = phoneNumber,
                status = status,
                kakaoId = kakaoId,
                rejectionReason = rejectionReason,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun loginRequest(code: String) =
        post("/api/auth/kakao/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"code":"$code"}""")

    private fun extractMemberId(responseBody: String): Long =
        Regex(""""memberId":(\d+)""")
            .find(responseBody)
            ?.groupValues
            ?.get(1)
            ?.toLong()
            ?: error("memberId를 응답에서 찾을 수 없습니다: $responseBody")
}
