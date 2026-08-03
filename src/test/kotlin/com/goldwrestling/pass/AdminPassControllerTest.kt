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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
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
 * `POST /api/admin/members/{memberId}/passes`의 성공·대표 실패·인가 계약을 검증하는 통합테스트
 * (PASS-01·PASS-02, 03-06 Task 3). 애노테이션 조합은 `MemberSearchTest`와 한 글자도 다르지 않게
 * 맞춘다(conventions §10.1 — 컨텍스트 캐시 재사용).
 *
 * 이 클래스는 이후 03-07(가감)·03-08(기간수정)·03-09(취소)가 통합테스트를 추가할 자리이므로
 * 섹션 주석으로 구획을 나눠 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class AdminPassControllerTest {
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
    private lateinit var passTransactionRepository: PassTransactionRepository

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private var fixtureCounter = 20000L

    /**
     * `MutableTestClock`은 싱글턴 빈이라 이전 테스트가 밀어 둔 시각이 이어진다. `JwtDecoder`의 만료
     * 검증은 애플리케이션 `Clock`이 아니라 **실제 시스템 시각**과 비교하므로(`MemberSearchTest` 참고),
     * 실제 현재 시각과 크게 어긋난 값으로 고정하면 방금 발급한 토큰도 만료된 것으로 거부된다 —
     * 매 테스트 시작 시 실제 현재 시각으로 되돌린다. `startDate` 기본값 단언은 고정된 상수가 아니라
     * `LocalDate.now(clock)`로 그때그때 계산해 이 시각과 항상 일치하게 한다.
     */
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    // ---------- 등록 ----------

    @Test
    fun `관리자 토큰 + 유효 요청으로 횟수권을 등록하면 201과 passId displayStatus endDate Location 헤더가 온다`() {
        val member = persistMember()
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/admin/members/${member.id}/passes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"SESSION_PASS","initialCount":10.0}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.passId").exists())
            .andExpect(jsonPath("$.displayStatus").value("USABLE"))
            .andExpect(jsonPath("$.endDate").exists())
            .andExpect(header().exists(HttpHeaders.LOCATION))
    }

    @Test
    fun `횟수권 등록 후 pass_transaction에 INITIAL_GRANT 1건이 생기고 amount가 초기 횟수와 같다`() {
        val member = persistMember()
        val token = adminAccessToken()

        val responseBody =
            mockMvc
                .perform(
                    post("/api/admin/members/${member.id}/passes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"type":"SESSION_PASS","initialCount":10.0}"""),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString

        val passId = objectMapper.readTree(responseBody).get("passId").asLong()
        val transactions = passTransactionRepository.findAll().filter { it.pass.id == passId }
        assertThat(transactions).hasSize(1)
        assertThat(transactions[0].reason).isEqualTo(TransactionReason.INITIAL_GRANT)
        assertThat(transactions[0].amount).isEqualByComparingTo(BigDecimal("10.0"))
    }

    @Test
    fun `저녁반 등록은 201이고 remainingCount가 null이며 이력이 0건이다`() {
        val member = persistMember()
        val token = adminAccessToken()

        val responseBody =
            mockMvc
                .perform(
                    post("/api/admin/members/${member.id}/passes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"type":"EVENING_MEMBERSHIP","term":"ONE_MONTH"}"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.remainingCount").doesNotExist())
                .andReturn()
                .response.contentAsString

        val passId = objectMapper.readTree(responseBody).get("passId").asLong()
        val transactions = passTransactionRepository.findAll().filter { it.pass.id == passId }
        assertThat(transactions).isEmpty()
    }

    @Test
    fun `startDate 미지정이면 고정된 테스트 Clock의 오늘로 등록된다`() {
        val member = persistMember()
        val token = adminAccessToken()
        val today = LocalDate.now(clock)

        mockMvc
            .perform(
                post("/api/admin/members/${member.id}/passes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"SESSION_PASS","initialCount":5.0}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.startDate").value(today.toString()))
    }

    @Test
    fun `startDate를 과거로 지정하면 201로 등록된다`() {
        val member = persistMember()
        val token = adminAccessToken()
        val pastDate = LocalDate.now(clock).minusMonths(2)

        mockMvc
            .perform(
                post("/api/admin/members/${member.id}/passes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"SESSION_PASS","initialCount":5.0,"startDate":"$pastDate"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.startDate").value(pastDate.toString()))
    }

    @Test
    fun `횟수권에 initialCount 0-3을 주면 400과 INVALID_ADJUSTMENT_UNIT을 반환한다`() {
        val member = persistMember()
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/admin/members/${member.id}/passes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"SESSION_PASS","initialCount":0.3}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_ADJUSTMENT_UNIT"))
    }

    @Test
    fun `기간제에 initialCount를 지정하면 409과 PASS_TYPE_NOT_ADJUSTABLE을 반환한다`() {
        val member = persistMember()
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/admin/members/${member.id}/passes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"EVENING_MEMBERSHIP","term":"ONE_MONTH","initialCount":5.0}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("PASS_TYPE_NOT_ADJUSTABLE"))
    }

    @Test
    fun `존재하지 않는 memberId로 등록하면 404와 MEMBER_NOT_FOUND를 반환한다`() {
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/admin/members/999999999/passes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"SESSION_PASS","initialCount":5.0}"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
    }

    // ---------- 인가 ----------

    @Test
    fun `회원 토큰으로 등록을 시도하면 403과 ACCESS_DENIED를 반환한다`() {
        val member = persistMember()
        val token = memberAccessToken(member)

        mockMvc
            .perform(
                post("/api/admin/members/${member.id}/passes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"SESSION_PASS","initialCount":5.0}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `토큰 없이 등록을 시도하면 401과 UNAUTHENTICATED를 반환한다`() {
        val member = persistMember()

        mockMvc
            .perform(
                post("/api/admin/members/${member.id}/passes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"SESSION_PASS","initialCount":5.0}"""),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    // ---------- 수동 가감 ----------

    @Test
    fun `관리자 토큰 + 유효 요청으로 가감하면 200과 즉시 갱신된 remainingCount를 반환한다`() {
        val pass = persistPass(remaining = BigDecimal("2.0"))
        val token = adminAccessToken()

        val responseBody =
            mockMvc
                .perform(
                    post("/api/admin/passes/${pass.id}/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"amount":1.0,"note":"이벤트 보상"}"""),
                ).andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        // JSON 수치 비교는 스케일에 민감해(예: 3.0 vs 3.00) BigDecimal로 파싱해 compareTo 기반으로 단언한다.
        val remaining = objectMapper.readTree(responseBody).get("remainingCount").decimalValue()
        assertThat(remaining).isEqualByComparingTo(BigDecimal("3.0"))
    }

    @Test
    fun `가감 성공 후 ADMIN_ADJUST 이력 1건이 생기고 amount note가 요청과 같다`() {
        val pass = persistPass(remaining = BigDecimal("2.0"))
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/admin/passes/${pass.id}/adjustments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":1.0,"note":"이벤트 보상"}"""),
            ).andExpect(status().isOk)

        val transactions = passTransactionRepository.findAll().filter { it.pass.id == pass.id }
        assertThat(transactions).hasSize(1)
        assertThat(transactions[0].reason).isEqualTo(TransactionReason.ADMIN_ADJUST)
        assertThat(transactions[0].amount).isEqualByComparingTo(BigDecimal("1.0"))
        assertThat(transactions[0].note).isEqualTo("이벤트 보상")
    }

    @Test
    fun `가감 수량이 0dot5 단위가 아니면 400과 INVALID_ADJUSTMENT_UNIT을 반환한다`() {
        val pass = persistPass(remaining = BigDecimal("2.0"))
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/admin/passes/${pass.id}/adjustments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":0.3,"note":"보정"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_ADJUSTMENT_UNIT"))
    }

    @Test
    fun `잔여 0dot5에 -1dot0 가감을 시도하면 409와 INSUFFICIENT_PASS_COUNT를 반환한다`() {
        val pass = persistPass(remaining = BigDecimal("0.5"))
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/admin/passes/${pass.id}/adjustments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":-1.0,"note":"보정"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_PASS_COUNT"))
    }

    @Test
    fun `기간제 이용권에 가감을 시도하면 409와 PASS_TYPE_NOT_ADJUSTABLE을 반환한다`() {
        val pass = persistPass(remaining = null, type = PassType.EVENING_MEMBERSHIP)
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/admin/passes/${pass.id}/adjustments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":0.5,"note":"보정"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("PASS_TYPE_NOT_ADJUSTABLE"))
    }

    /**
     * `note`는 Kotlin non-null 생성자 파라미터라 JSON에 키 자체가 없으면 `@Valid`가 실행되기 전
     * jackson-module-kotlin 역직렬화 단계에서 이미 실패한다(`MissingKotlinParameterException` →
     * `HttpMessageNotReadableException`) — `MALFORMED_REQUEST`의 KDoc이 명시하는 "필수 파라미터
     * 누락"에 해당한다. `@field:NotBlank`(`VALIDATION_FAILED`)는 필드가 "존재하되 값이 비었을
     * 때"만 실행되는데 nullable하지 않은 타입에는 그 상태 자체가 있을 수 없다 —
     * `MemberStatusChangeTest`의 "status 필드가 없으면 MALFORMED_REQUEST" 케이스와 같은 계열이다.
     */
    @Test
    fun `note 필드가 없으면 400과 MALFORMED_REQUEST를 반환한다`() {
        val pass = persistPass(remaining = BigDecimal("2.0"))
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/admin/passes/${pass.id}/adjustments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":1.0}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
    }

    @Test
    fun `note가 공백이면 400과 VALIDATION_FAILED를 반환한다`() {
        val pass = persistPass(remaining = BigDecimal("2.0"))
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/admin/passes/${pass.id}/adjustments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":1.0,"note":"   "}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `존재하지 않는 passId로 가감하면 404와 PASS_NOT_FOUND를 반환한다`() {
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/admin/passes/999999999/adjustments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":1.0,"note":"보정"}"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PASS_NOT_FOUND"))
    }

    @Test
    fun `회원 토큰으로 가감을 시도하면 403과 ACCESS_DENIED를 반환한다`() {
        val pass = persistPass(remaining = BigDecimal("2.0"))
        val token = memberAccessToken(pass.member)

        mockMvc
            .perform(
                post("/api/admin/passes/${pass.id}/adjustments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":1.0,"note":"보정"}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `토큰 없이 가감을 시도하면 401과 UNAUTHENTICATED를 반환한다`() {
        val pass = persistPass(remaining = BigDecimal("2.0"))

        mockMvc
            .perform(
                post("/api/admin/passes/${pass.id}/adjustments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":1.0,"note":"보정"}"""),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "이용권회원$fixtureCounter",
                phoneNumber = "0109876$fixtureCounter",
                status = MemberStatus.ACTIVE,
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
                loginId = "admin-pass-reg-$fixtureCounter",
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

    /** 가감 대상 이용권을 등록 API를 거치지 않고 직접 저장한다 — 가감 계약만 검증하는 테스트라 등록 경로와 결합할 필요가 없다. */
    private fun persistPass(
        remaining: BigDecimal?,
        type: PassType = PassType.SESSION_PASS,
    ): Pass {
        val member = persistMember()
        val admin = persistAdmin()
        return passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = type,
                status = PassStatus.ACTIVE,
                startDate = LocalDate.now(clock),
                endDate = LocalDate.now(clock).plusYears(1).minusDays(1),
                remainingCount = remaining,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
