package com.goldwrestling.member

import com.goldwrestling.TestcontainersConfiguration
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
import org.springframework.jdbc.core.simple.JdbcClient
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
 * `GET /api/members/me`·`POST /api/members/me/onboarding`의 상태별 접근 가능 범위, 온보딩
 * 형식 검증·재제출 차단, 저장 형식(하이픈 제거)을 검증하는 통합테스트(AUTH-04, AUTH-05, MEMBER-04).
 *
 * 애노테이션 조합은 기존 Phase 2 통합테스트와 동일하게 `@Import(TestcontainersConfiguration::class,
 * TestClockConfiguration::class)`를 쓴다(conventions §10.1 — 컨텍스트 캐시 재사용).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class MemberProfileTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @MockitoBean
    private lateinit var kakaoApiClient: KakaoApiClient

    /**
     * `MutableTestClock`은 싱글턴 빈이라 이전 테스트가 밀어 둔 시각이 이어진다. `JwtDecoder`의 기본
     * 만료 검증은 발급받은 [Clock]이 아니라 실제 시스템 시각과 비교하므로(RefreshTokenRotationTest
     * 참고), 매 테스트 시작 시 실제 현재 시각으로 되돌린다 — 그렇지 않으면 access 토큰이 곧바로
     * 만료된 것으로 취급되어 모든 인증된 요청이 401로 실패한다.
     */
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    // ---------- GET /api/members/me ----------

    @Test
    fun `토큰 없이 GET api-members-me 를 호출하면 401과 UNAUTHENTICATED를 반환한다`() {
        mockMvc
            .perform(get("/api/members/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `PENDING이고 온보딩 미완료인 회원이 GET api-members-me 를 호출하면 200이고 이름 전화번호는 null이다`() {
        val member = persistMember(kakaoId = 5001L, name = null, phoneNumber = null, status = MemberStatus.PENDING)
        val token = accessTokenFor(member)

        mockMvc
            .perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.memberId").value(member.id))
            .andExpect(jsonPath("$.name").doesNotExist())
            .andExpect(jsonPath("$.phoneNumber").doesNotExist())
            .andExpect(jsonPath("$.onboardingCompleted").value(false))
            .andExpect(jsonPath("$.rejectionReason").doesNotExist())
    }

    @Test
    fun `PENDING이고 온보딩 완료인 회원이 GET api-members-me 를 호출하면 이름 전화번호가 채워져 있다`() {
        val member =
            persistMember(kakaoId = 5002L, name = "홍길동", phoneNumber = "01012345678", status = MemberStatus.PENDING)
        val token = accessTokenFor(member)

        mockMvc
            .perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("홍길동"))
            .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
            .andExpect(jsonPath("$.onboardingCompleted").value(true))
    }

    @Test
    fun `ACTIVE 회원이 GET api-members-me 를 호출하면 200이다`() {
        val member =
            persistMember(kakaoId = 5003L, name = "김철수", phoneNumber = "01099998888", status = MemberStatus.ACTIVE)
        val token = accessTokenFor(member)

        mockMvc
            .perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `거절된 회원이 GET api-members-me 를 호출하면 200이고 rejected는 true다`() {
        val member =
            persistMember(
                kakaoId = 5004L,
                name = "박영희",
                phoneNumber = "01055557777",
                status = MemberStatus.INACTIVE,
                rejectionReason = "내부 관리자 메모: 자격 미달",
            )
        val token = accessTokenFor(member)

        mockMvc
            .perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rejected").value(true))
            .andExpect(jsonPath("$.rejectionReason").doesNotExist())
    }

    @Test
    fun `응답의 memberId는 토큰 주체와 같다`() {
        val member =
            persistMember(kakaoId = 5005L, name = "이순신", phoneNumber = "01011112222", status = MemberStatus.ACTIVE)
        val token = accessTokenFor(member)

        mockMvc
            .perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.memberId").value(member.id))
    }

    // ---------- POST /api/members/me/onboarding ----------

    @Test
    fun `PENDING이고 미완료인 회원이 유효한 이름 전화번호를 보내면 200이고 온보딩이 완료된다`() {
        val member = persistMember(kakaoId = 5101L, name = null, phoneNumber = null, status = MemberStatus.PENDING)
        val token = accessTokenFor(member)

        mockMvc
            .perform(onboardingRequest(token, name = "정약용", phoneNumber = "010-1234-5678"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.onboardingCompleted").value(true))
            .andExpect(jsonPath("$.name").value("정약용"))
            .andExpect(jsonPath("$.phoneNumber").value("01012345678"))

        // MemberProfileService는 dirty checking에 기대 별도 save()/flush()를 호출하지 않는다(주석 참고).
        // 이 테스트 트랜잭션은 커밋되지 않으므로(@Transactional 롤백), UPDATE가 실제 DB로 나가는 시점은
        // 트랜잭션 커밋 또는 명시적 flush뿐이다 — 같은 커넥션을 쓰는 raw JDBC 조회로 값을 확인하려면
        // 여기서 명시적으로 flush해야 한다(RefreshTokenRotationTest의 INSERT는 IDENTITY 생성 때문에
        // 이미 즉시 flush되지만, 이번은 기존 행의 UPDATE라 그 특혜가 없다).
        memberRepository.flush()

        val storedPhoneNumber =
            jdbcClient
                .sql("select phone_number from member where id = :id")
                .param("id", member.id)
                .query(String::class.java)
                .single()
        assertThat(storedPhoneNumber).doesNotContain("-")
        assertThat(storedPhoneNumber).isEqualTo("01012345678")

        val statusAfter =
            jdbcClient
                .sql("select status from member where id = :id")
                .param("id", member.id)
                .query(String::class.java)
                .single()
        assertThat(statusAfter).isEqualTo("PENDING")
    }

    @Test
    fun `같은 회원이 온보딩을 다시 제출하면 409와 ONBOARDING_ALREADY_COMPLETED를 반환한다`() {
        val member =
            persistMember(kakaoId = 5102L, name = "기존이름", phoneNumber = "01000000000", status = MemberStatus.PENDING)
        val token = accessTokenFor(member)

        mockMvc
            .perform(onboardingRequest(token, name = "새이름", phoneNumber = "010-9999-8888"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ONBOARDING_ALREADY_COMPLETED"))
    }

    @Test
    fun `ACTIVE 회원이 온보딩을 제출하면 409와 MEMBER_STATE_CONFLICT를 반환한다`() {
        // 실제 운영에서는 승인(PENDING→ACTIVE)이 온보딩 완료를 전제하지만, MemberStateGate는
        // 온보딩 완료 검사를 먼저 수행하므로(D-042 유의) 그 분기와 상태 분기를 각각 독립적으로
        // 검증하려면 "온보딩 미완료인 ACTIVE" 조합으로 상태 분기만 단독으로 발동시켜야 한다.
        val member =
            persistMember(kakaoId = 5103L, name = null, phoneNumber = null, status = MemberStatus.ACTIVE)
        val token = accessTokenFor(member)

        mockMvc
            .perform(onboardingRequest(token, name = "새이름", phoneNumber = "010-9999-8888"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MEMBER_STATE_CONFLICT"))
    }

    @Test
    fun `거절된 회원이 온보딩을 제출하면 409와 MEMBER_STATE_CONFLICT를 반환한다`() {
        val member =
            persistMember(
                kakaoId = 5104L,
                name = null,
                phoneNumber = null,
                status = MemberStatus.INACTIVE,
                rejectionReason = "자격 미달",
            )
        val token = accessTokenFor(member)

        mockMvc
            .perform(onboardingRequest(token, name = "새이름", phoneNumber = "010-9999-8888"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MEMBER_STATE_CONFLICT"))
    }

    @Test
    fun `전화번호 형식이 틀리면 400과 VALIDATION_FAILED를 반환하고 DB가 변경되지 않는다`() {
        val member = persistMember(kakaoId = 5105L, name = null, phoneNumber = null, status = MemberStatus.PENDING)
        val token = accessTokenFor(member)

        mockMvc
            .perform(onboardingRequest(token, name = "정약용", phoneNumber = "02-1234-5678"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        assertNameAndPhoneUnchanged(member.id!!)
    }

    @Test
    fun `이름이 공백이면 400과 VALIDATION_FAILED를 반환한다`() {
        val member = persistMember(kakaoId = 5106L, name = null, phoneNumber = null, status = MemberStatus.PENDING)
        val token = accessTokenFor(member)

        mockMvc
            .perform(onboardingRequest(token, name = "   ", phoneNumber = "01012345678"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        assertNameAndPhoneUnchanged(member.id!!)
    }

    @Test
    fun `이름이 51자 이상이면 400과 VALIDATION_FAILED를 반환한다`() {
        val member = persistMember(kakaoId = 5107L, name = null, phoneNumber = null, status = MemberStatus.PENDING)
        val token = accessTokenFor(member)
        val longName = "가".repeat(51)

        mockMvc
            .perform(onboardingRequest(token, name = longName, phoneNumber = "01012345678"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        assertNameAndPhoneUnchanged(member.id!!)
    }

    @Test
    fun `온보딩 완료 후 카카오 재로그인하면 응답의 onboardingCompleted가 true다`() {
        val member = persistMember(kakaoId = 5108L, name = null, phoneNumber = null, status = MemberStatus.PENDING)
        val token = accessTokenFor(member)

        mockMvc
            .perform(onboardingRequest(token, name = "정약용", phoneNumber = "01012345678"))
            .andExpect(status().isOk)

        given(kakaoApiClient.exchangeToken(anyString())).willReturn(dummyTokenResponse())
        given(kakaoApiClient.fetchKakaoId(anyString())).willReturn(5108L)

        mockMvc
            .perform(
                post("/api/auth/kakao/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"code":"any-code"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.member.onboardingCompleted").value(true))
    }

    private fun assertNameAndPhoneUnchanged(memberId: Long) {
        val row =
            jdbcClient
                .sql("select name, phone_number from member where id = :id")
                .param("id", memberId)
                .query { rs, _ -> rs.getString("name") to rs.getString("phone_number") }
                .single()
        assertThat(row.first).isNull()
        assertThat(row.second).isNull()
    }

    private fun accessTokenFor(member: Member): String = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

    private fun onboardingRequest(
        token: String,
        name: String,
        phoneNumber: String,
    ) = post("/api/members/me/onboarding")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"name":"$name","phoneNumber":"$phoneNumber"}""")

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
}
