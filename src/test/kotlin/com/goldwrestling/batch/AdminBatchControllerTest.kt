package com.goldwrestling.batch

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
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * `POST /api/admin/batch/inactivity-runs`의 HTTP 계약(인가·응답 필드·중복 실행 안전성)을
 * 검증한다(BATCH-04, 05-08-PLAN Task 2). 애노테이션 조합은 `AdminScheduleControllerTest`와
 * 맞춘다(conventions §10.1) — 단, **클래스 레벨 `@Transactional`을 붙이지 않는다**. 차감이
 * 실제로 커밋돼야(같은 회원에 대한 연속 2회 호출이 서로 다른 물리 트랜잭션으로 처리돼야) D-106
 * 멱등성을 HTTP 레벨에서 실증할 수 있다 — 대신 `@AfterEach`에서 이 클래스가 만든 데이터만 지운다.
 *
 * **clock을 `BatchFixtures.FIXED_TIME`(과거 고정 시각)이 아니라 실제 `Instant.now()`로
 * 리셋한다**(`AdminScheduleControllerTest` 관례) — `TokenService.issueTokenPair`가 만드는
 * access 토큰의 `exp` 클레임은 이 앱의 `clock` 빈으로 계산되지만, `NimbusJwtDecoder`는 검증 시
 * 실제 시스템 시각을 기준으로 만료를 판정한다. clock을 과거로 고정하면 발급 직후의 토큰이
 * 시스템 시각 기준으로는 이미 만료돼 있어 모든 인증이 401로 실패한다. "14일 전" 같은 상대 날짜는
 * `LocalDate.now(clock)`으로 매 테스트 시점 기준 상대값을 계산해 얻는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class AdminBatchControllerTest {
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
    private lateinit var batchExecutionRepository: BatchExecutionRepository

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L
    private val createdBatchExecutionIds = mutableListOf<Long>()

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @AfterEach
    fun cleanUp() {
        if (createdBatchExecutionIds.isNotEmpty()) {
            batchExecutionRepository.deleteAllById(createdBatchExecutionIds)
            createdBatchExecutionIds.clear()
        }
        jdbcClient
            .sql(
                "delete from pass_transaction where pass_id in " +
                    "(select id from pass where member_id in (select id from member where kakao_id >= :base))",
            ).param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from pass where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        // refresh_token이 member/admin을 FK로 참조한다 — 토큰 발급 테스트가 만든 행을 먼저 지운다.
        jdbcClient
            .sql("delete from refresh_token where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql(
                "delete from refresh_token where admin_id in " +
                    "(select id from admin where login_id like :prefix)",
            ).param("prefix", "$ADMIN_LOGIN_PREFIX%")
            .update()
        jdbcClient
            .sql("delete from member where kakao_id >= :base")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from admin where login_id like :prefix")
            .param("prefix", "$ADMIN_LOGIN_PREFIX%")
            .update()
    }

    @Test
    fun `관리자 토큰으로 호출하면 200이고 실행 결과 필드가 모두 채워진다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken

        val body = runBatch(token)

        assertThat(body.get("batchExecutionId").asLong()).isPositive()
        assertThat(body.get("trigger").asText()).isEqualTo("MANUAL")
        assertThat(body.get("triggeredByAdminId").asLong()).isEqualTo(admin.id)
        assertThat(body.get("startedAt").asText()).isNotBlank()
        assertThat(body.get("finishedAt").asText()).isNotBlank()
        assertThat(body.get("processedMemberCount")).isNotNull()
        assertThat(body.get("deductedCount")).isNotNull()
        assertThat(body.get("skippedCount")).isNotNull()
        assertThat(body.get("status").asText()).isIn("SUCCESS", "PARTIAL_FAILURE")
    }

    @Test
    fun `차감 대상이 있으면 실제로 잔여가 줄고 deductedCount가 1 이상이다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        val member = persistMember()
        val pass = persistDeductiblePass(member, admin)

        val body = runBatch(token)

        assertThat(body.get("deductedCount").asInt()).isGreaterThanOrEqualTo(1)
        val remaining = passRepository.findById(pass.id!!).get().remainingCount
        assertThat(remaining).isLessThan(BigDecimal("3.0"))
    }

    @Test
    fun `같은 요청을 연속 2회 보내면 두 번째 응답의 deductedCount는 0이다(D-106)`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        val member = persistMember()
        persistDeductiblePass(member, admin)

        val first = runBatch(token)
        val second = runBatch(token)

        assertThat(first.get("deductedCount").asInt()).isGreaterThanOrEqualTo(1)
        assertThat(second.get("deductedCount").asInt()).isEqualTo(0)
    }

    @Test
    fun `회원 토큰으로 호출하면 403과 ACCESS_DENIED를 반환한다`() {
        val member = persistMember()
        val token = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        mockMvc
            .perform(post("/api/admin/batch/inactivity-runs").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `토큰 없이 호출하면 401과 UNAUTHENTICATED를 반환한다`() {
        mockMvc
            .perform(post("/api/admin/batch/inactivity-runs"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `응답 본문에 회원 개인정보나 회원 id 목록이 담기지 않는다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        val member = persistMember()
        persistDeductiblePass(member, admin)

        val root = runBatch(token)

        assertThat(root.toString()).doesNotContain(member.name, member.phoneNumber)
        assertThat(root.propertyNames().asSequence().toList()).doesNotContain("memberIds")
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun runBatch(token: String): JsonNode {
        val raw =
            mockMvc
                .perform(post("/api/admin/batch/inactivity-runs").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        val root = objectMapper.readTree(raw)
        createdBatchExecutionIds += root.get("batchExecutionId").asLong()
        return root
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(BatchFixtures.member(songpaBranch(), kakaoId = KAKAO_ID_BASE + fixtureCounter))
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(BatchFixtures.admin(loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter"))
    }

    /** 기준일이 14일 전이라 부족분 1이 나오는 `SESSION_PASS`(`InactivityBatchFailureIsolationTest`와 동일 조건). */
    private fun persistDeductiblePass(
        member: Member,
        admin: Admin,
    ): Pass {
        val today = LocalDate.now(clock)
        return passRepository.saveAndFlush(
            BatchFixtures.sessionPass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                remainingCount = BigDecimal("3.0"),
                endDate = today.plusDays(30),
                createdAt = OffsetDateTime.now(clock).minusDays(14),
            ),
        )
    }

    companion object {
        const val KAKAO_ID_BASE = 9_720_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-batch-ctrl-"
    }
}
