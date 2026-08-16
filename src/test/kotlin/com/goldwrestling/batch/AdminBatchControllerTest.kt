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
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.core.task.TaskExecutor
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
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
 * 관리자 배치 API 3종의 HTTP 계약을 검증한다(BATCH-04, WR-05·IN-03):
 * `POST /api/admin/batch/inactivity-runs`(실행 접수) · `GET .../{id}`(진행 상태) · `GET ...`(최근 목록).
 *
 * 애노테이션 조합은 `AdminScheduleControllerTest`와 맞춘다(conventions §10.1) — 단, **클래스 레벨
 * `@Transactional`을 붙이지 않는다**. 차감이 실제로 커밋돼야 D-106 멱등성을 HTTP 레벨에서 실증할 수
 * 있고, 무엇보다 **본문이 다른 스레드에서 도는 지금은 테스트 트랜잭션 안의 데이터가 그 스레드에게
 * 아예 보이지 않는다.** 대신 [cleanUp]이 이 클래스가 만든 데이터만 지운다.
 *
 * **clock을 `BatchFixtures.FIXED_TIME`(과거 고정 시각)이 아니라 실제 `Instant.now()`로
 * 리셋한다**(`AdminScheduleControllerTest` 관례) — `TokenService.issueTokenPair`가 만드는
 * access 토큰의 `exp` 클레임은 이 앱의 `clock` 빈으로 계산되지만, `NimbusJwtDecoder`는 검증 시
 * 실제 시스템 시각을 기준으로 만료를 판정한다. clock을 과거로 고정하면 발급 직후의 토큰이
 * 시스템 시각 기준으로는 이미 만료돼 있어 모든 인증이 401로 실패한다. "14일 전" 같은 상대 날짜는
 * `LocalDate.now(clock)`으로 매 테스트 시점 기준 상대값을 계산해 얻는다.
 *
 * ### POST가 이제 결과를 돌려주지 않는다 — 관측 지점이 바뀌었다
 * 실행 결과(`deductedCount`·`status`)는 POST 응답이 아니라 **단건 조회**로 확인한다. POST는 실행을
 * 접수하고 202를 즉시 돌려줄 뿐이다(WR-05). 그래서 결과를 보는 테스트는 [awaitFinished]로 비동기
 * 완료를 기다린 뒤 `GET .../{id}`를 부른다.
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
    private lateinit var applicationContext: ApplicationContext

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
            // **지우기 전에 반드시 끝나기를 기다린다.** 본문이 다른 스레드에서 아직 돌고 있는데
            // 회원·이용권을 지우면 그 스레드가 사라진 행을 건드리고, 최악의 경우 확정에 실패해
            // `RUNNING` 행이 남는다 — 남으면 같은 컨테이너를 쓰는 다른 테스트 클래스의 배치가
            // 전부 409로 막힌다(T-05D-11-01).
            createdBatchExecutionIds.forEach { runCatching { awaitFinished(it) } }
            batchExecutionRepository.deleteAllById(createdBatchExecutionIds)
            createdBatchExecutionIds.clear()
        }
        // 단언이 중간에 실패해 id를 기록하지 못한 실행까지 확실히 지운다 — 남으면 아래 관리자 삭제가
        // `fk_batch_execution_admin`에 걸려 **이 클래스의 다른 테스트가 연쇄로 깨진다**(원인과 무관한 실패).
        jdbcClient
            .sql(
                "delete from batch_execution where triggered_by_admin_id in " +
                    "(select id from admin where login_id like :prefix)",
            ).param("prefix", "$ADMIN_LOGIN_PREFIX%")
            .update()
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

    /**
     * 테스트 컨텍스트에서 cron 스케줄러가 꺼져 있는지 확인한다(D-116).
     *
     * 이 단언이 깨지면 `@EnableScheduling`이 살아 있는 컨텍스트에서 테스트가 도는 것이고, CI가
     * 04:00 `Asia/Seoul`을 걸치는 순간 실제 배치가 이 클래스가 만든 회원의 잔여를 깎아
     * **재현되지 않는 실패**를 만든다. 끄는 주체는 `build.gradle.kts`의 테스트 태스크가 넣는
     * `goldwrestling.batch.inactivity-scheduler-enabled=false` system property다.
     *
     * 수동 실행 API는 이 게이트와 무관하게 동작해야 한다 — 아래 테스트들이 그걸 증명한다.
     */
    @Test
    fun `테스트 컨텍스트에는 cron 스케줄러 빈이 등록되지 않는다`() {
        assertThat(applicationContext.getBeanNamesForType(InactivityBatchScheduler::class.java)).isEmpty()
    }

    /**
     * 배치 전용 실행기를 등록해도 스프링 부트의 기본 실행기(`applicationTaskExecutor`)가 살아 있어야
     * 한다. 부트의 기본 실행기는 `@ConditionalOnMissingBean(Executor)` 조건이라, 우리 빈을
     * `defaultCandidate = false` 없이 등록하면 **기본 실행기가 통째로 사라진다** — 서블릿 비동기 처리와
     * `spring.task.execution.*` 설정이 조용히 무력화되는데, 어디서도 오류가 나지 않아 알아채기 어렵다.
     */
    @Test
    fun `배치 전용 실행기를 등록해도 스프링 기본 실행기가 사라지지 않는다`() {
        val executorBeans = applicationContext.getBeanNamesForType(TaskExecutor::class.java).toList()

        assertThat(executorBeans).contains("inactivityBatchExecutor", "applicationTaskExecutor")
    }

    @Test
    fun `관리자 토큰으로 호출하면 202와 실행 중 상태를 즉시 돌려준다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken

        val body = runBatch(token)

        assertThat(body.get("batchExecutionId").asLong()).isPositive()
        assertThat(body.get("trigger").asText()).isEqualTo("MANUAL")
        assertThat(body.get("triggeredByAdminId").asLong()).isEqualTo(admin.id)
        assertThat(body.get("startedAt").asText()).isNotBlank()
        // 아직 끝나지 않았으므로 종료 시각이 없다 — 이 두 단언이 "202는 접수일 뿐"이라는 계약이다
        assertThat(body.get("status").asText()).isEqualTo("RUNNING")
        assertThat(body.get("finishedAt").isNull).isTrue()
    }

    @Test
    fun `202 응답의 Location 헤더가 진행 상태 조회 경로를 가리킨다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken

        val result =
            mockMvc
                .perform(post(RUNS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isAccepted)
                .andReturn()
        val body = objectMapper.readTree(result.response.contentAsString)
        val batchExecutionId = body.get("batchExecutionId").asLong()
        createdBatchExecutionIds += batchExecutionId

        assertThat(result.response.getHeader(HttpHeaders.LOCATION)).isEqualTo("$RUNS_PATH/$batchExecutionId")
        // 그 경로가 실제로 조회 가능한 자원이어야 한다
        awaitFinished(batchExecutionId)
        getRun(token, batchExecutionId)
    }

    @Test
    fun `차감 대상이 있으면 실제로 잔여가 줄고 실행 결과의 deductedCount가 1 이상이다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        val member = persistMember()
        val pass = persistDeductiblePass(member, admin)

        val batchExecutionId = runBatch(token).get("batchExecutionId").asLong()
        awaitFinished(batchExecutionId)

        val finished = getRun(token, batchExecutionId)
        assertThat(finished.get("status").asText()).isEqualTo("SUCCESS")
        assertThat(finished.get("deductedCount").asInt()).isGreaterThanOrEqualTo(1)
        assertThat(finished.get("finishedAt").asText()).isNotBlank()
        assertThat(passRepository.findById(pass.id!!).get().remainingCount).isLessThan(BigDecimal("3.0"))
    }

    @Test
    fun `같은 요청을 연속 2회 보내면 두 번째 실행의 deductedCount는 0이다(D-106)`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        val member = persistMember()
        persistDeductiblePass(member, admin)

        val first = runAndAwait(token)
        val second = runAndAwait(token)

        assertThat(getRun(token, first).get("deductedCount").asInt()).isGreaterThanOrEqualTo(1)
        assertThat(getRun(token, second).get("deductedCount").asInt()).isEqualTo(0)
    }

    @Test
    fun `없는 실행 id를 조회하면 404와 BATCH_EXECUTION_NOT_FOUND를 반환한다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken

        val raw =
            mockMvc
                .perform(get("$RUNS_PATH/$MISSING_EXECUTION_ID").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BATCH_EXECUTION_NOT_FOUND"))
                .andReturn()
                .response.contentAsString

        // 존재 여부를 탐색할 수 있는 단서(요청한 id)를 **사용자 대면 문구**에 담지 않는다
        // (conventions §8). `instance`는 요청 경로 그 자체라 id가 들어가는 것이 정상이다.
        assertThat(objectMapper.readTree(raw).get("detail").asText()).doesNotContain("$MISSING_EXECUTION_ID")
    }

    @Test
    fun `실행 이력 목록은 시작 시각 내림차순이고 limit이 반영된다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken

        val first = runAndAwait(token)
        val second = runAndAwait(token)

        val recent = listRuns(token, limit = 2)
        assertThat(recent).hasSize(2)
        assertThat(recent.map { it.get("batchExecutionId").asLong() }).containsExactly(second, first)

        // limit이 실제로 건수를 자른다
        assertThat(listRuns(token, limit = 1).map { it.get("batchExecutionId").asLong() }).containsExactly(second)
    }

    /**
     * 범위를 벗어난 `limit`은 조용히 보정하지 않고 400으로 거부한다(PR #16 리뷰 Info 1).
     *
     * 잘못된 입력을 서비스에서 `coerceIn`으로 고쳐 200을 돌려주면 호출자는 **자기 요청이 무시된 줄
     * 모른다** — `limit=0`을 보내고 20건을 받아도 그게 보정 결과인지 실제 결과인지 구분할 수 없다.
     * 페이징 파라미터 검증은 컨트롤러가 한다는 이 저장소 관례(`MemberSearchCondition`·
     * `PassTransactionSearchCondition`)를 따른다.
     */
    @ParameterizedTest(name = "limit={0}")
    @ValueSource(ints = [0, -1, 101])
    fun `limit이 1부터 100 범위를 벗어나면 400과 VALIDATION_FAILED를 반환한다`(limit: Int) {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken

        mockMvc
            .perform(
                get(RUNS_PATH)
                    .param("limit", limit.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `limit 경계값 1과 100은 정상 조회된다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        runAndAwait(token)

        assertThat(listRuns(token, limit = 1)).hasSize(1)
        assertThat(listRuns(token, limit = AdminBatchService.MAX_LIMIT)).isNotEmpty()
    }

    @Test
    fun `회원 토큰으로 호출하면 403과 ACCESS_DENIED를 반환한다`() {
        val member = persistMember()
        val token = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        mockMvc
            .perform(post(RUNS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `토큰 없이 호출하면 401과 UNAUTHENTICATED를 반환한다`() {
        mockMvc
            .perform(post(RUNS_PATH))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    /**
     * 새로 생긴 조회 엔드포인트 2종도 `/api/admin` 하위 인가 규칙을 그대로 상속한다(D-040, T-05D-15-01).
     * 실행 이력에는 차감 집계가 담기므로 회원에게 열리면 안 된다.
     */
    @Test
    fun `실행 이력 조회 2종도 회원 토큰이면 403이고 토큰이 없으면 401이다`() {
        val member = persistMember()
        val memberToken = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        listOf(RUNS_PATH, "$RUNS_PATH/$MISSING_EXECUTION_ID").forEach { path ->
            mockMvc
                .perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken"))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
            mockMvc
                .perform(get(path))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        }
    }

    @Test
    fun `응답 본문에 회원 개인정보나 회원 id 목록이 담기지 않는다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        val member = persistMember()
        persistDeductiblePass(member, admin)

        val accepted = runBatch(token)
        val batchExecutionId = accepted.get("batchExecutionId").asLong()
        awaitFinished(batchExecutionId)
        val finished = getRun(token, batchExecutionId)

        listOf(accepted, finished).forEach { node ->
            assertThat(node.toString()).doesNotContain(member.name, member.phoneNumber)
            assertThat(node.propertyNames().asSequence().toList()).doesNotContain("memberIds")
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** 실행을 접수시키고(202) 응답 본문을 돌려준다. 이 시점의 상태는 아직 `RUNNING`이다. */
    private fun runBatch(token: String): JsonNode {
        val raw =
            mockMvc
                .perform(post(RUNS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isAccepted)
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn()
                .response.contentAsString
        val root = objectMapper.readTree(raw)
        createdBatchExecutionIds += root.get("batchExecutionId").asLong()
        return root
    }

    /** 접수 → 완료 대기를 한 번에 하고 실행 id를 돌려준다. */
    private fun runAndAwait(token: String): Long {
        val batchExecutionId = runBatch(token).get("batchExecutionId").asLong()
        awaitFinished(batchExecutionId)
        return batchExecutionId
    }

    private fun getRun(
        token: String,
        batchExecutionId: Long,
    ): JsonNode {
        val raw =
            mockMvc
                .perform(get("$RUNS_PATH/$batchExecutionId").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(raw)
    }

    private fun listRuns(
        token: String,
        limit: Int,
    ): List<JsonNode> {
        val raw =
            mockMvc
                .perform(
                    get(RUNS_PATH)
                        .param("limit", limit.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                ).andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        val array = objectMapper.readTree(raw)
        return (0 until array.size()).map { array.get(it) }
    }

    /**
     * 실행 이력이 `RUNNING`에서 벗어날 때까지 짧은 간격으로 폴링한다.
     *
     * **이 대기는 conventions §10.3이 금지하는 "시각 경과를 기다리는 `Thread.sleep`"이 아니다.**
     * 정책상 며칠이 지나기를 기다리는 것이 아니라 **다른 스레드의 작업 완료**를 기다리는 것이고,
     * 그 스레드가 언제 끝나는지는 우리가 제어할 수 없다. 정책 시각은 여전히 `Clock` 빈으로 고정한다.
     */
    private fun awaitFinished(
        batchExecutionId: Long,
        timeoutMillis: Long = AWAIT_TIMEOUT_MILLIS,
    ): BatchExecution {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val execution = batchExecutionRepository.findById(batchExecutionId).orElse(null)
            if (execution != null && execution.status != BatchExecutionStatus.RUNNING) {
                return execution
            }
            Thread.sleep(AWAIT_POLL_MILLIS)
        }
        return fail("배치 실행($batchExecutionId)이 ${timeoutMillis}ms 안에 끝나지 않았습니다.")
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
        private const val RUNS_PATH = "/api/admin/batch/inactivity-runs"

        /** 실제로 존재할 수 없는 실행 id — 시퀀스가 여기까지 오려면 이 앱이 수백 년 돌아야 한다. */
        private const val MISSING_EXECUTION_ID = 9_999_999L
        private const val AWAIT_TIMEOUT_MILLIS = 10_000L
        private const val AWAIT_POLL_MILLIS = 20L
    }
}
