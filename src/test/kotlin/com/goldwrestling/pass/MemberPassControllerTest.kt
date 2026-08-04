package com.goldwrestling.pass

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.auth.PrincipalType
import com.goldwrestling.auth.TokenService
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
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
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * `GET /api/members/me/passes`의 노출 범위·인가 계약을 검증하는 통합테스트(PASS-05, 03-10 Task 3).
 * 애노테이션 조합은 `MemberSearchTest`·`AdminPassControllerTest`와 동일하게 맞춘다(conventions §10.1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class MemberPassControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private var fixtureCounter = 40000L

    /** `MemberSearchTest`와 동일한 이유로 매 테스트 시작 시 실제 현재 시각으로 되돌린다. */
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `회원 토큰으로 GET api-members-me-passes 를 호출하면 200과 배열이 온다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        persistPass(member, admin, remaining = BigDecimal("3.0"))
        val token = memberAccessToken(member)

        mockMvc
            .perform(get("/api/members/me/passes").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `만료된 이용권이 목록에 포함되고 displayStatus가 EXPIRED다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val today = LocalDate.now(clock)
        val expiredPass =
            persistPass(
                member,
                admin,
                remaining = BigDecimal("3.0"),
                startDate = today.minusYears(2),
                endDate = today.minusDays(1),
            )
        val token = memberAccessToken(member)

        val body =
            mockMvc
                .perform(get("/api/members/me/passes").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val statuses = objectMapper.readTree(body).associate { it.get("passId").asLong() to it.get("displayStatus").asString() }
        assertThat(statuses[expiredPass.id]).isEqualTo("EXPIRED")
    }

    @Test
    fun `잔여 0인 이용권이 목록에 포함되고 displayStatus가 EXHAUSTED다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val exhaustedPass = persistPass(member, admin, remaining = BigDecimal.ZERO)
        val token = memberAccessToken(member)

        val body =
            mockMvc
                .perform(get("/api/members/me/passes").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val statuses = objectMapper.readTree(body).associate { it.get("passId").asLong() to it.get("displayStatus").asString() }
        assertThat(statuses[exhaustedPass.id]).isEqualTo("EXHAUSTED")
    }

    @Test
    fun `취소된 이용권은 목록에 없다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val usablePass = persistPass(member, admin, remaining = BigDecimal("5.0"))
        val canceledPass = persistPass(member, admin, remaining = BigDecimal("2.0"), status = PassStatus.CANCELED)
        val token = memberAccessToken(member)

        val body =
            mockMvc
                .perform(get("/api/members/me/passes").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val passIds: List<Long> = objectMapper.readTree(body).toList().map { it.get("passId").asLong() }
        assertThat(passIds.contains(usablePass.id)).isTrue()
        assertThat(passIds.contains(canceledPass.id)).isFalse()
    }

    @Test
    fun `응답 어디에도 cancelReason 값이 채워진 항목이 없다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        persistPass(member, admin, remaining = BigDecimal("5.0"))
        persistPass(member, admin, remaining = BigDecimal("2.0"), status = PassStatus.CANCELED)
        val token = memberAccessToken(member)

        val body =
            mockMvc
                .perform(get("/api/members/me/passes").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val cancelReasons = objectMapper.readTree(body).toList().map { it.get("cancelReason") }
        assertThat(cancelReasons).allSatisfy { assertThat(it.isNull).isTrue() }
    }

    @Test
    fun `다른 회원의 이용권이 섞이지 않는다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val otherMember = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        persistPass(member, admin, remaining = BigDecimal("5.0"))
        val otherPass = persistPass(otherMember, admin, remaining = BigDecimal("5.0"))
        val token = memberAccessToken(member)

        val body =
            mockMvc
                .perform(get("/api/members/me/passes").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val passIds: List<Long> = objectMapper.readTree(body).toList().map { it.get("passId").asLong() }
        assertThat(passIds.contains(otherPass.id)).isFalse()
    }

    @Test
    fun `ON_LEAVE 회원도 본인 이용권을 조회할 수 있다`() {
        val member = persistMember(status = MemberStatus.ON_LEAVE)
        val admin = persistAdmin()
        val pass = persistPass(member, admin, remaining = BigDecimal("3.0"))
        val token = memberAccessToken(member)

        val body =
            mockMvc
                .perform(get("/api/members/me/passes").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val passIds: List<Long> = objectMapper.readTree(body).toList().map { it.get("passId").asLong() }
        assertThat(passIds).contains(pass.id)
    }

    @Test
    fun `PENDING 회원도 본인 이용권을 조회할 수 있다 - 상태 게이트 없음(D-071)`() {
        val member = persistMember(status = MemberStatus.PENDING)
        val token = memberAccessToken(member)

        mockMvc
            .perform(get("/api/members/me/passes").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `관리자 토큰으로 호출하면 403과 ACCESS_DENIED를 반환한다`() {
        val token = adminAccessToken()

        mockMvc
            .perform(get("/api/members/me/passes").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `토큰 없이 호출하면 401과 UNAUTHENTICATED를 반환한다`() {
        mockMvc
            .perform(get("/api/members/me/passes"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(status: MemberStatus): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "이용권조회회원$fixtureCounter",
                phoneNumber = "0108765$fixtureCounter",
                status = status,
                kakaoId = fixtureCounter,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(
            Admin(
                name = "관리자",
                loginId = "admin-member-pass-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun adminAccessToken(): String {
        val admin = persistAdmin()
        return tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
    }

    private fun memberAccessToken(member: Member): String = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

    /** 조회 계약만 검증하는 테스트라 등록 API를 거치지 않고 `Pass`를 직접 저장한다(`AdminPassControllerTest`와 동일 관례). */
    private fun persistPass(
        member: Member,
        admin: Admin,
        remaining: BigDecimal?,
        type: PassType = PassType.SESSION_PASS,
        startDate: LocalDate = LocalDate.now(clock),
        endDate: LocalDate = LocalDate.now(clock).plusYears(1).minusDays(1),
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass {
        val pass =
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = type,
                status = PassStatus.ACTIVE,
                startDate = startDate,
                endDate = endDate,
                remainingCount = remaining,
                createdAt = OffsetDateTime.now(clock),
            )
        if (status == PassStatus.CANCELED) {
            pass.cancel(reason = "테스트 사전 취소", admin = admin, now = OffsetDateTime.now(clock))
        }
        return passRepository.saveAndFlush(pass)
    }
}
