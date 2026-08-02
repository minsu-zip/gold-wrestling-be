package com.goldwrestling.member

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.auth.PrincipalType
import com.goldwrestling.auth.TokenService
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * `GET /api/admin/members`·`GET /api/admin/members/{memberId}`의 인가·페이지네이션·검색·상세 계약을
 * 검증하는 통합테스트(MEMBER-01 목록 부분, MEMBER-02, D-035). 애노테이션 조합은 기존 Phase 2
 * 통합테스트와 동일하게 `@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)`를
 * 쓴다(conventions §10.1 — 컨텍스트 캐시 재사용).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class MemberSearchTest {
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
     * 검증은 실제 시스템 시각과 비교하므로(AdminAuthControllerTest 참고), 매 테스트 시작 시 실제
     * 현재 시각으로 되돌린다.
     */
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    // ---------- 인가 ----------

    @Test
    fun `관리자 토큰으로 GET api-admin-members 를 호출하면 200과 페이지 응답이 온다`() {
        persistMember(kakaoId = 8001L, name = "회원일", phoneNumber = "01011110000", status = MemberStatus.ACTIVE)
        val token = adminAccessToken(loginId = "admin-list-ok")

        mockMvc
            .perform(get("/api/admin/members").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.page").value(0))
    }

    @Test
    fun `회원 토큰으로 GET api-admin-members 를 호출하면 403과 ACCESS_DENIED를 반환한다`() {
        val member = persistMember(kakaoId = 8002L, name = "회원", phoneNumber = "01022220000", status = MemberStatus.ACTIVE)
        val token = memberAccessToken(member)

        mockMvc
            .perform(get("/api/admin/members").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `토큰 없이 GET api-admin-members 를 호출하면 401과 UNAUTHENTICATED를 반환한다`() {
        mockMvc
            .perform(get("/api/admin/members"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    // ---------- 페이지네이션 ----------

    @Test
    fun `size=2 요청 시 content 길이가 2이고 totalElements는 전체 수와 같다`() {
        repeat(5) { index ->
            persistMember(
                kakaoId = 8100L + index,
                name = "페이지회원$index",
                phoneNumber = "0103000${index}000",
                status = MemberStatus.ACTIVE,
            )
            (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        }
        val token = adminAccessToken(loginId = "admin-page-size")

        mockMvc
            .perform(get("/api/admin/members").param("size", "2").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
    }

    @Test
    fun `page=1을 요청하면 두 번째 페이지가 온다`() {
        repeat(5) { index ->
            persistMember(
                kakaoId = 8200L + index,
                name = "둘째페이지$index",
                phoneNumber = "0104000${index}000",
                status = MemberStatus.ACTIVE,
            )
            (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        }
        val token = adminAccessToken(loginId = "admin-page-second")

        mockMvc
            .perform(
                get("/api/admin/members")
                    .param("size", "2")
                    .param("page", "1")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.content.length()").value(2))
    }

    // ---------- 검색 ----------

    @Test
    fun `keyword로 이름 일부를 주면 해당 회원만 온다`() {
        persistMember(kakaoId = 8301L, name = "검색용홍길동", phoneNumber = "01055550000", status = MemberStatus.ACTIVE)
        persistMember(kakaoId = 8302L, name = "다른사람", phoneNumber = "01066660000", status = MemberStatus.ACTIVE)
        val token = adminAccessToken(loginId = "admin-keyword-name")

        mockMvc
            .perform(get("/api/admin/members").param("keyword", "홍길동").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("검색용홍길동"))
    }

    @Test
    fun `keyword로 하이픈 포함 전화번호를 주면 해당 회원이 온다`() {
        persistMember(kakaoId = 8303L, name = "전화검색", phoneNumber = "01077778888", status = MemberStatus.ACTIVE)
        val token = adminAccessToken(loginId = "admin-keyword-phone")

        mockMvc
            .perform(
                get("/api/admin/members")
                    .param("keyword", "010-7777")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].phoneNumber").value("01077778888"))
    }

    @Test
    fun `status=PENDING과 onboardingCompleted=true 요청 결과에 온보딩 미완료 PENDING 회원이 포함되지 않는다`() {
        val complete =
            persistMember(kakaoId = 8401L, name = "완료회원", phoneNumber = "01088880000", status = MemberStatus.PENDING)
        persistMember(kakaoId = 8402L, name = null, phoneNumber = null, status = MemberStatus.PENDING)
        val token = adminAccessToken(loginId = "admin-pending-filter")

        mockMvc
            .perform(
                get("/api/admin/members")
                    .param("status", "PENDING")
                    .param("onboardingCompleted", "true")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].memberId").value(complete.id))
    }

    @Test
    fun `정렬은 createdAt 내림차순이 기본이다`() {
        persistMember(kakaoId = 8501L, name = "오래된회원", phoneNumber = "01099990000", status = MemberStatus.ACTIVE)
        (clock as MutableTestClock).advance(Duration.ofMinutes(10))
        val newer =
            persistMember(kakaoId = 8502L, name = "최근회원", phoneNumber = "01099991111", status = MemberStatus.ACTIVE)
        val token = adminAccessToken(loginId = "admin-sort-order")

        mockMvc
            .perform(
                get("/api/admin/members")
                    .param("keyword", "회원")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].memberId").value(newer.id))
    }

    // ---------- 검증 실패 ----------

    @Test
    fun `size=0 이면 400과 VALIDATION_FAILED를 반환한다`() {
        val token = adminAccessToken(loginId = "admin-size-zero")

        mockMvc
            .perform(get("/api/admin/members").param("size", "0").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `size=101 이면 400과 VALIDATION_FAILED를 반환한다`() {
        val token = adminAccessToken(loginId = "admin-size-over")

        mockMvc
            .perform(get("/api/admin/members").param("size", "101").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `page가 음수면 400과 VALIDATION_FAILED를 반환한다`() {
        val token = adminAccessToken(loginId = "admin-page-negative")

        mockMvc
            .perform(get("/api/admin/members").param("page", "-1").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    // ---------- 목록의 사유 미노출 ----------

    @Test
    fun `목록 응답 항목에 rejectionReason 키가 없다`() {
        persistMember(
            kakaoId = 8601L,
            name = "거절회원",
            phoneNumber = "01012120000",
            status = MemberStatus.INACTIVE,
            rejectionReason = "내부 사유",
        )
        val token = adminAccessToken(loginId = "admin-list-no-reason")

        mockMvc
            .perform(
                get("/api/admin/members")
                    .param("keyword", "거절회원")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].rejectionReason").doesNotExist())
    }

    // ---------- 상세 ----------

    @Test
    fun `GET api-admin-members-id 가 200과 상세를 반환하고 rejectionReason kakaoId branchName을 포함한다`() {
        val rejected =
            persistMember(
                kakaoId = 8701L,
                name = "상세거절회원",
                phoneNumber = "01013130000",
                status = MemberStatus.INACTIVE,
                rejectionReason = "자격 미달",
            )
        val token = adminAccessToken(loginId = "admin-detail-ok")

        mockMvc
            .perform(get("/api/admin/members/${rejected.id}").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rejectionReason").value("자격 미달"))
            .andExpect(jsonPath("$.kakaoId").value(8701))
            .andExpect(jsonPath("$.branchName").value("송파점"))
    }

    @Test
    fun `존재하지 않는 memberId 상세 요청이 404와 MEMBER_NOT_FOUND를 반환한다`() {
        val token = adminAccessToken(loginId = "admin-detail-not-found")

        mockMvc
            .perform(get("/api/admin/members/999999999").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
    }

    @Test
    fun `상세를 회원 토큰으로 호출하면 403이다`() {
        val target =
            persistMember(kakaoId = 8801L, name = "상세대상", phoneNumber = "01014140000", status = MemberStatus.ACTIVE)
        val requester =
            persistMember(kakaoId = 8802L, name = "요청자", phoneNumber = "01015150000", status = MemberStatus.ACTIVE)
        val token = memberAccessToken(requester)

        mockMvc
            .perform(get("/api/admin/members/${target.id}").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
    }

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
