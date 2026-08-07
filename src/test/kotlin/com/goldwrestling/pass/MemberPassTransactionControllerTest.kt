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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * `GET /api/members/me/pass-transactions`의 노출 범위·인가·페이지네이션 계약을 검증하는
 * 통합테스트(PASS-06, 03-10 Task 3). 애노테이션 조합은 `MemberSearchTest`·`MemberPassControllerTest`와
 * 동일하게 맞춘다(conventions §10.1).
 *
 * **`.map`을 JSON 배열 노드에 직접 체이닝하지 않는다** — `tools.jackson.databind.JsonNode`가
 * `Iterable<JsonNode>`이면서 동시에 자기 자신에 함수를 적용하는 멤버 `map(Function): R`을 갖고 있어,
 * Kotlin이 `Iterable.map` 확장 대신 이 멤버를 골라 반환 타입이 리스트가 아닌 단일 값이 된다
 * (`MemberPassControllerTest`에서 실제로 재현·수정된 문제). `.toList()`로 먼저 `List<JsonNode>`로
 * 바꾼 뒤에만 `.map`/`.associate`를 체이닝한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class MemberPassTransactionControllerTest {
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

    private var fixtureCounter = 50000L

    /** `MemberSearchTest`와 동일한 이유로 매 테스트 시작 시 실제 현재 시각으로 되돌린다. */
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    // ---------- 응답 형태 ----------

    @Test
    fun `회원 토큰으로 조회하면 200과 content-page-size-totalElements-totalPages가 모두 온다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val pass = persistPass(member, admin)
        persistTransaction(pass, admin)
        val token = memberAccessToken(member)

        mockMvc
            .perform(get("/api/members/me/pass-transactions").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").exists())
            .andExpect(jsonPath("$.totalElements").exists())
            .andExpect(jsonPath("$.totalPages").exists())
    }

    @Test
    fun `취소된 이용권의 이력은 응답에 없다 - 본인 이용권 목록과 노출 범위 일치(D-073)`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val activePass = persistPass(member, admin)
        val canceledPass = persistPass(member, admin, status = PassStatus.CANCELED)
        val visibleTransaction = persistTransaction(activePass, admin)
        persistTransaction(canceledPass, admin, reason = TransactionReason.INITIAL_GRANT)
        persistTransaction(canceledPass, admin, reason = TransactionReason.REGISTRATION_CANCELED)
        val token = memberAccessToken(member)

        mockMvc
            .perform(get("/api/members/me/pass-transactions").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].transactionId").value(visibleTransaction.id))

        // passId 필터로 직접 지정해도 취소된 이용권의 이력은 비어 있다
        mockMvc
            .perform(
                get("/api/members/me/pass-transactions")
                    .param("passId", canceledPass.id.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    // ---------- 페이지네이션 ----------

    @Test
    fun `size=2 요청 시 content 길이가 2이고 totalElements는 전체 이력 수와 같다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val pass = persistPass(member, admin)
        repeat(5) {
            persistTransaction(pass, admin)
            (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        }
        val token = memberAccessToken(member)

        mockMvc
            .perform(
                get("/api/members/me/pass-transactions")
                    .param("size", "2")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
    }

    @Test
    fun `page=1을 요청하면 두 번째 페이지가 온다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val pass = persistPass(member, admin)
        repeat(5) {
            persistTransaction(pass, admin)
            (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        }
        val token = memberAccessToken(member)

        mockMvc
            .perform(
                get("/api/members/me/pass-transactions")
                    .param("size", "2")
                    .param("page", "1")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.content.length()").value(2))
    }

    // ---------- passId 필터·소유권 격리 ----------

    @Test
    fun `passId 필터를 주면 해당 이용권의 이력만 나온다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val targetPass = persistPass(member, admin)
        val otherOwnPass = persistPass(member, admin)
        persistTransaction(targetPass, admin, amount = BigDecimal("1.0"))
        (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        persistTransaction(targetPass, admin, amount = BigDecimal("2.0"))
        (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        persistTransaction(otherOwnPass, admin, amount = BigDecimal("3.0"))
        val token = memberAccessToken(member)

        val body =
            mockMvc
                .perform(
                    get("/api/members/me/pass-transactions")
                        .param("passId", targetPass.id.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn()
                .response.contentAsString

        val passIds =
            objectMapper
                .readTree(body)
                .get("content")
                .toList()
                .map { it.get("passId").asLong() }
        assertThat(passIds).allSatisfy { assertThat(it).isEqualTo(targetPass.id) }
    }

    @Test
    fun `타인의 passId로 필터하면 totalElements가 0이다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val otherMember = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val otherPass = persistPass(otherMember, admin)
        persistTransaction(otherPass, admin)
        val token = memberAccessToken(member)

        mockMvc
            .perform(
                get("/api/members/me/pass-transactions")
                    .param("passId", otherPass.id.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(0))
    }

    @Test
    fun `다른 회원의 이력이 섞이지 않는다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val otherMember = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val myPass = persistPass(member, admin)
        val otherPass = persistPass(otherMember, admin)
        persistTransaction(myPass, admin)
        val otherTransaction = persistTransaction(otherPass, admin)
        val token = memberAccessToken(member)

        val body =
            mockMvc
                .perform(get("/api/members/me/pass-transactions").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val transactionIds =
            objectMapper
                .readTree(body)
                .get("content")
                .toList()
                .map { it.get("transactionId").asLong() }
        assertThat(transactionIds).doesNotContain(otherTransaction.id)
    }

    // ---------- 정렬 ----------

    @Test
    fun `정렬이 occurredAt 내림차순이다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val pass = persistPass(member, admin)
        persistTransaction(pass, admin, amount = BigDecimal("1.0"))
        (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        persistTransaction(pass, admin, amount = BigDecimal("2.0"))
        (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        val latest = persistTransaction(pass, admin, amount = BigDecimal("3.0"))
        val token = memberAccessToken(member)

        mockMvc
            .perform(get("/api/members/me/pass-transactions").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].transactionId").value(latest.id))
    }

    // ---------- 검증 실패 ----------

    @Test
    fun `size=0 이면 400과 VALIDATION_FAILED를 반환한다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val token = memberAccessToken(member)

        mockMvc
            .perform(
                get("/api/members/me/pass-transactions")
                    .param("size", "0")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `size=101 이면 400과 VALIDATION_FAILED를 반환한다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val token = memberAccessToken(member)

        mockMvc
            .perform(
                get("/api/members/me/pass-transactions")
                    .param("size", "101")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    // ---------- 인가 ----------

    @Test
    fun `ON_LEAVE 회원도 본인 이력을 조회할 수 있다 - 상태 게이트 없음(D-071)`() {
        val member = persistMember(status = MemberStatus.ON_LEAVE)
        val token = memberAccessToken(member)

        mockMvc
            .perform(get("/api/members/me/pass-transactions").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `관리자 토큰으로 호출하면 403과 ACCESS_DENIED를 반환한다`() {
        val token = adminAccessToken()

        mockMvc
            .perform(get("/api/members/me/pass-transactions").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `토큰 없이 호출하면 401과 UNAUTHENTICATED를 반환한다`() {
        mockMvc
            .perform(get("/api/members/me/pass-transactions"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(status: MemberStatus): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "이력조회회원$fixtureCounter",
                phoneNumber = "0107654$fixtureCounter",
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
                loginId = "admin-member-tx-$fixtureCounter",
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
        remaining: BigDecimal = BigDecimal("10.0"),
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass {
        // ck_pass_cancellation(V4)이 CANCELED를 취소 메타데이터 3종 동시 존재와 묶어 강제한다
        // (`MemberPassControllerTest.persistPass`와 동일 패턴).
        val isCanceled = status == PassStatus.CANCELED
        return passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = PassType.SESSION_PASS,
                status = status,
                startDate = LocalDate.now(clock),
                endDate = LocalDate.now(clock).plusYears(1).minusDays(1),
                remainingCount = remaining,
                canceledBy = if (isCanceled) admin else null,
                canceledAt = if (isCanceled) OffsetDateTime.now(clock) else null,
                cancelReason = if (isCanceled) "테스트 사전 취소" else null,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    /** `PassTransaction`은 append-only 원장이라(`PassTransaction.kt` KDoc) 이력 자체를 직접 저장한다. */
    private fun persistTransaction(
        pass: Pass,
        admin: Admin,
        amount: BigDecimal = BigDecimal("1.0"),
        reason: TransactionReason = TransactionReason.ADMIN_ADJUST,
        note: String? = "테스트 이력",
    ): PassTransaction =
        passTransactionRepository.saveAndFlush(
            PassTransaction(
                pass = pass,
                amount = amount,
                reason = reason,
                note = note,
                admin = admin,
                member = null,
                occurredAt = OffsetDateTime.now(clock),
            ),
        )
}
