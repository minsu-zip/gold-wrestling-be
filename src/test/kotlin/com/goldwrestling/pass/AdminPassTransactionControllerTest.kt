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
 * `GET /api/admin/members/{memberId}/pass-transactions`의 노출 범위·인가·페이지네이션 계약을
 * 검증하는 통합테스트(PASS-05, BE-REQ-003, D-149).
 *
 * 이 엔드포인트의 존재 이유가 **회원용과 노출 범위가 다르다는 것**이므로, 짝이 되는
 * `MemberPassTransactionControllerTest`가 "숨긴다"고 단언하는 두 가지를 여기서는 "보인다"로
 * 뒤집어 고정한다 — 관리자 메모(`note`, D-070)와 취소된 이용권의 이력(D-073). 두 파일이 같은
 * 데이터에 대해 반대 방향을 주장하므로, 한쪽 조건을 실수로 다른 쪽에 복사하면 반드시 실패한다.
 *
 * 애노테이션 조합은 `MemberPassTransactionControllerTest`와 동일하게 맞춘다(conventions §10.1) —
 * 스프링 컨텍스트 캐시를 공유하기 위해서다.
 *
 * **`.map`을 JSON 배열 노드에 직접 체이닝하지 않는다** — `tools.jackson.databind.JsonNode`가
 * `Iterable<JsonNode>`이면서 동시에 멤버 `map(Function): R`을 갖고 있어 Kotlin이 확장 대신 멤버를
 * 고른다. `.toList()`로 먼저 `List<JsonNode>`로 바꾼 뒤에만 체이닝한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class AdminPassTransactionControllerTest {
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

    private var fixtureCounter = 70000L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    // ---------- 응답 형태 ----------

    @Test
    fun `관리자 토큰으로 조회하면 200과 content-page-size-totalElements-totalPages가 모두 온다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val pass = persistPass(member, admin)
        persistTransaction(pass, admin)

        mockMvc
            .perform(adminGet(member, adminAccessToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").exists())
            .andExpect(jsonPath("$.totalElements").exists())
            .andExpect(jsonPath("$.totalPages").exists())
    }

    // ---------- 회원용과 반대인 두 가지 ----------

    /**
     * D-070이 감춘 것은 "회원에게"이지 관리자에게가 아니다. 관리자는 자신이 수동 가감 때 남긴
     * 사유 메모를 다시 볼 수 있어야 한다 — BE-REQ-003이 이 엔드포인트를 요청한 핵심 이유다.
     */
    @Test
    fun `관리자 이력 응답에는 note가 포함된다 - 회원 응답과 반대(D-070)`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val pass = persistPass(member, admin)
        persistTransaction(pass, admin, note = "이벤트 보상 2회 지급")

        mockMvc
            .perform(adminGet(member, adminAccessToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].note").value("이벤트 보상 2회 지급"))
    }

    /**
     * D-073은 회원 화면에서 취소 이용권의 이력을 감춘다(목록에 없는 passId의 행이 보이는 혼란 방지).
     * 관리자 화면은 반대로 오등록 정정의 상쇄 이력(`REGISTRATION_CANCELED`)까지 보여야 감사가
     * 가능하다(D-059).
     */
    @Test
    fun `취소된 이용권의 이력도 응답에 포함되고 passStatus로 구분된다 - 회원 응답과 반대(D-073)`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val activePass = persistPass(member, admin)
        val canceledPass = persistPass(member, admin, status = PassStatus.CANCELED)
        persistTransaction(activePass, admin)
        (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        persistTransaction(canceledPass, admin, reason = TransactionReason.INITIAL_GRANT)
        (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        persistTransaction(canceledPass, admin, reason = TransactionReason.REGISTRATION_CANCELED)

        val body =
            mockMvc
                .perform(adminGet(member, adminAccessToken()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalElements").value(3))
                .andReturn()
                .response.contentAsString

        val statusesByPassId =
            objectMapper
                .readTree(body)
                .get("content")
                .toList()
                .associate { it.get("passId").asLong() to it.get("passStatus").asString() }
        assertThat(statusesByPassId[canceledPass.id]).isEqualTo("CANCELED")
        assertThat(statusesByPassId[activePass.id]).isEqualTo("ACTIVE")
    }

    // ---------- 스코프 ----------

    @Test
    fun `다른 회원의 이력이 섞이지 않는다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val otherMember = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        persistTransaction(persistPass(member, admin), admin)
        val otherTransaction = persistTransaction(persistPass(otherMember, admin), admin)

        val body =
            mockMvc
                .perform(adminGet(member, adminAccessToken()))
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

    /** 오타로 빈 화면을 보고 "이력이 없다"고 오독하지 않도록, 없는 회원은 빈 페이지가 아니라 404다. */
    @Test
    fun `존재하지 않는 회원이면 404와 MEMBER_NOT_FOUND를 반환한다`() {
        mockMvc
            .perform(
                get("/api/admin/members/{memberId}/pass-transactions", 99_999_999L)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${adminAccessToken()}"),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
    }

    // ---------- passId 필터·정렬·페이지네이션 ----------

    @Test
    fun `passId 필터를 주면 해당 이용권의 이력만 나온다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val targetPass = persistPass(member, admin)
        val otherOwnPass = persistPass(member, admin)
        persistTransaction(targetPass, admin)
        (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        persistTransaction(otherOwnPass, admin)

        mockMvc
            .perform(
                adminGet(member, adminAccessToken()).param("passId", targetPass.id.toString()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].passId").value(targetPass.id))
    }

    @Test
    fun `정렬이 occurredAt 내림차순이다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val pass = persistPass(member, admin)
        persistTransaction(pass, admin, amount = BigDecimal("1.0"))
        (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        val latest = persistTransaction(pass, admin, amount = BigDecimal("2.0"))

        mockMvc
            .perform(adminGet(member, adminAccessToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].transactionId").value(latest.id))
    }

    @Test
    fun `size=2 요청 시 content 길이가 2이고 totalElements는 전체 이력 수와 같다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val pass = persistPass(member, admin)
        repeat(5) {
            persistTransaction(pass, admin)
            (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        }

        mockMvc
            .perform(adminGet(member, adminAccessToken()).param("size", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
    }

    @Test
    fun `size=0 이면 400과 VALIDATION_FAILED를 반환한다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)

        mockMvc
            .perform(adminGet(member, adminAccessToken()).param("size", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    // ---------- 인가 ----------

    @Test
    fun `회원 토큰으로 호출하면 403과 ACCESS_DENIED를 반환한다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)
        val token = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        mockMvc
            .perform(adminGet(member, token))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `토큰 없이 호출하면 401과 UNAUTHENTICATED를 반환한다`() {
        val member = persistMember(status = MemberStatus.ACTIVE)

        mockMvc
            .perform(get("/api/admin/members/{memberId}/pass-transactions", member.id!!))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun adminGet(
        member: Member,
        token: String,
    ) = get("/api/admin/members/{memberId}/pass-transactions", member.id!!)
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(status: MemberStatus): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "관리자이력조회회원$fixtureCounter",
                phoneNumber = "0105432$fixtureCounter",
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
                loginId = "admin-admin-tx-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun adminAccessToken(): String = tokenService.issueTokenPair(PrincipalType.ADMIN, persistAdmin().id!!).accessToken

    /** 조회 계약만 검증하는 테스트라 등록 API를 거치지 않고 `Pass`를 직접 저장한다. */
    private fun persistPass(
        member: Member,
        admin: Admin,
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass {
        // ck_pass_cancellation(V4)이 CANCELED를 취소 메타데이터 3종 동시 존재와 묶어 강제한다.
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
                remainingCount = BigDecimal("10.0"),
                canceledBy = if (isCanceled) admin else null,
                canceledAt = if (isCanceled) OffsetDateTime.now(clock) else null,
                cancelReason = if (isCanceled) "테스트 사전 취소" else null,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    /** `PassTransaction`은 append-only 원장이라 이력 자체를 직접 저장한다. */
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
