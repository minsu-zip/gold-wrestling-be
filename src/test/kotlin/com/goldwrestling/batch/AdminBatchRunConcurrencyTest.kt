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
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.TransactionReason
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
import org.springframework.core.task.TaskExecutor
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * **BATCH-04를 HTTP 계층에서 실증한다** — 관리자가 실행 버튼을 더블클릭하거나 두 관리자가 같은
 * 순간에 누르면 `POST /api/admin/batch/inactivity-runs` 중 **정확히 하나만 202**를 받고 나머지는
 * **409(`BATCH_ALREADY_RUNNING`)**로 거부된다는 것.
 *
 * `InactivityBatchRunConcurrencyTest`(05-13)는 러너를 직접 동시 호출해 "총 차감 1회"를 증명했다.
 * 이 클래스는 그보다 한 층 위, **요청이 실제로 들어오는 경로**를 검증한다 — 거부가 HTTP 409
 * `ProblemDetail`(D-017·D-118)로 나가는지, 거부된 요청이 이력을 남기지 않는지까지 본다.
 *
 * ### 왜 실행기 빈을 모의로 대체하는가 (이 클래스의 핵심 장치)
 * 실제 실행기를 쓰면 **경쟁이 재현되지 않을 수 있다.** 테스트 픽스처가 작아 첫 실행이 밀리초 단위로
 * 끝나 버리므로, 두 번째 요청이 도착할 때는 이미 `RUNNING` 행이 종료 상태로 확정돼 있고 두 요청이
 * **둘 다 202**를 받는다. 그래서 `inactivityBatchExecutor`를 [MockitoBean]으로 대체한다 — 모의
 * 실행기는 `execute`를 받아도 아무 일도 하지 않으므로 첫 실행이 테스트 내내 `RUNNING`으로 남고,
 * 뒤에 온 요청은 **반드시** 409를 받는다.
 *
 * 이 대체는 현실을 왜곡하지 않는다. **운영에서 배치는 수 초~수 분 돌기 때문에, "앞 실행이 아직
 * 안 끝난 상태"가 오히려 정상 상황이다.** 모의 실행기는 그 상태를 결정론적으로 고정할 뿐이다.
 *
 * ### 격리
 * **클래스·메서드에 트랜잭션 애노테이션을 붙이지 않는다** — 각 스레드가 별도 트랜잭션이어야
 * 경쟁이 재현되고, `RUNNING` 행이 커밋돼야 다른 스레드에게 보인다(conventions §10.4,
 * add-domain-test §4). 픽스처 대역은 이 클래스 전용([KAKAO_ID_BASE] ~ [KAKAO_ID_MAX])이고 정리
 * 조건은 하한이 아니라 **범위**로 좁힌다(WR-07).
 *
 * **[cleanUp]이 이 클래스가 만든 `batch_execution` 행, 특히 `RUNNING`으로 남은 것을 반드시
 * 지운다.** 모의 실행기 때문에 이 클래스는 매 테스트마다 끝나지 않는 `RUNNING` 행을 만든다 —
 * 하나라도 남으면 같은 컨테이너를 쓰는 **다른 테스트 클래스의 배치가 전부 409로 막혀**, 원인과
 * 무관한 곳에서 실패가 터진다(T-05D-11-01).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class AdminBatchRunConcurrencyTest {
    /**
     * 무동작 실행기 — `execute`를 받아도 본문을 돌리지 않는다. 그 결과 실행이 `RUNNING`에 머물러
     * "이미 실행 중" 상태가 테스트 내내 유지된다(위 클래스 KDoc 참조).
     */
    @MockitoBean(name = "inactivityBatchExecutor")
    private lateinit var executor: TaskExecutor

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
    private lateinit var clock: java.time.Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L
    private var baselineExecutionId: Long = 0

    @BeforeEach
    fun resetClockAndBaseline() {
        // 토큰 만료는 시스템 시각으로 판정되므로 과거로 고정하지 않는다(AdminBatchControllerTest와 동일한 이유).
        (clock as MutableTestClock).setTo(Instant.now())
        baselineExecutionId =
            jdbcClient
                .sql("select coalesce(max(id), 0) from batch_execution")
                .query(Long::class.java)
                .single()
    }

    @AfterEach
    fun cleanUp() {
        jdbcClient
            .sql("delete from batch_execution where id > :baseline")
            .param("baseline", baselineExecutionId)
            .update()
        jdbcClient
            .sql(
                "delete from pass_transaction where pass_id in " +
                    "(select id from pass where member_id in " +
                    "(select id from member where kakao_id >= :base and kakao_id < :max))",
            ).param("base", KAKAO_ID_BASE)
            .param("max", KAKAO_ID_MAX)
            .update()
        jdbcClient
            .sql("delete from pass where member_id in (select id from member where kakao_id >= :base and kakao_id < :max)")
            .param("base", KAKAO_ID_BASE)
            .param("max", KAKAO_ID_MAX)
            .update()
        jdbcClient
            .sql("delete from refresh_token where admin_id in (select id from admin where login_id like :prefix)")
            .param("prefix", "$ADMIN_LOGIN_PREFIX%")
            .update()
        jdbcClient
            .sql("delete from member where kakao_id >= :base and kakao_id < :max")
            .param("base", KAKAO_ID_BASE)
            .param("max", KAKAO_ID_MAX)
            .update()
        jdbcClient
            .sql("delete from admin where login_id like :prefix")
            .param("prefix", "$ADMIN_LOGIN_PREFIX%")
            .update()
    }

    @Test
    fun `두 스레드가 동시에 실행을 요청하면 정확히 하나만 202를 받고 나머지는 409다`() {
        val token = adminToken()

        val statuses = postConcurrently(token, threadCount = 2)

        assertThat(statuses.count { it == 202 }).isEqualTo(1)
        assertThat(statuses.count { it == 409 }).isEqualTo(1)
        // 거부된 요청은 이력을 한 줄도 남기지 않는다 — 접수된 실행 1건이 전부다
        assertThat(executionCountSinceBaseline()).isEqualTo(1)
        assertThat(runningExecutionCount()).isEqualTo(1)
    }

    @Test
    fun `네 스레드가 동시에 요청해도 실행 중 행은 정확히 1건이다`() {
        val token = adminToken()

        val statuses = postConcurrently(token, threadCount = 4)

        assertThat(statuses.count { it == 202 }).isEqualTo(1)
        assertThat(statuses.count { it == 409 }).isEqualTo(3)
        assertThat(runningExecutionCount()).isEqualTo(1)
    }

    @Test
    fun `실행 중 상태에서 순차로 다시 호출해도 409다`() {
        val token = adminToken()

        mockMvc
            .perform(post(RUNS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isAccepted)

        mockMvc
            .perform(post(RUNS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("BATCH_ALREADY_RUNNING"))
    }

    /**
     * 거부 응답은 관리자 화면에 그대로 노출된다 — 제약조건명·테이블명·SQL 조각이 새면 공격자가
     * 스키마를 유추할 재료가 된다(conventions §8, T-05D-15-02).
     */
    @Test
    fun `409 응답 본문에 제약조건명이나 SQL 같은 내부 정보가 담기지 않는다`() {
        val token = adminToken()
        mockMvc
            .perform(post(RUNS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isAccepted)

        val body =
            mockMvc
                .perform(post(RUNS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isConflict)
                .andReturn()
                .response.contentAsString

        assertThat(body).doesNotContain(
            "uq_batch_execution_running",
            "batch_execution",
            "insert",
            "constraint",
            "Exception",
        )
    }

    /**
     * **202는 "완료"가 아니라 "접수"다.** 응답을 받은 시점에는 차감이 아직 일어나지 않았다 —
     * 이 계약이 깨지면(컨트롤러가 본문을 동기로 돌리면) 여기서 잔여가 줄어 실패한다. WR-05가
     * 없애려는 것이 바로 그 동기 실행이다.
     */
    @Test
    fun `202를 받은 시점에는 아직 잔여가 줄지 않았다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        val pass = persistDeductiblePass(persistMember(), admin)

        mockMvc
            .perform(post(RUNS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isAccepted)

        assertThat(passRepository.findById(pass.id!!).get().remainingCount).isEqualByComparingTo(BigDecimal("3.0"))
        assertThat(inactivityCountOf(pass.id!!)).isZero()
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** [threadCount]개 스레드가 같은 순간에 실행 접수 POST를 보내게 하고, 받은 상태코드를 모은다. */
    private fun postConcurrently(
        token: String,
        threadCount: Int,
    ): List<Int> {
        val pool = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        // 스레드가 동시에 쓰므로 동기화된 컬렉션에 모은다
        val statuses = Collections.synchronizedList(mutableListOf<Int>())

        repeat(threadCount) {
            pool.submit {
                try {
                    startLatch.await()
                    val response =
                        mockMvc
                            .perform(post(RUNS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                            .andReturn()
                            .response
                    statuses.add(response.status)
                } catch (e: Exception) {
                    // 상태코드로 표현되지 않은 실패는 그대로 드러나야 한다 — 삼키면 "409가 1건"이
                    // 우연히 맞아떨어지는 초록불이 된다
                    statuses.add(UNEXPECTED_FAILURE_STATUS)
                    logger.error("동시 실행 요청이 예외로 끝났습니다.", e)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = doneLatch.await(60, TimeUnit.SECONDS)
        pool.shutdown()
        assertThat(completed).isTrue()
        assertThat(statuses).hasSize(threadCount)

        return statuses.toList()
    }

    private fun executionCountSinceBaseline(): Int =
        jdbcClient
            .sql("select count(*) from batch_execution where id > :baseline")
            .param("baseline", baselineExecutionId)
            .query(Int::class.java)
            .single()

    private fun runningExecutionCount(): Int =
        jdbcClient
            .sql("select count(*) from batch_execution where status = 'RUNNING'")
            .query(Int::class.java)
            .single()

    private fun inactivityCountOf(passId: Long): Int =
        passTransactionRepository.findAll().count { it.pass.id == passId && it.reason == TransactionReason.INACTIVITY }

    private fun adminToken(): String {
        val admin = persistAdmin()
        return tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(BatchFixtures.admin(loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter"))
    }

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(BatchFixtures.member(songpaBranch(), kakaoId = KAKAO_ID_BASE + fixtureCounter))
    }

    /** 기준일이 14일 전이라 부족분 1이 나오는 `SESSION_PASS` — 본문이 돌았다면 반드시 잔여가 줄어든다. */
    private fun persistDeductiblePass(
        member: Member,
        admin: Admin,
    ): Pass =
        passRepository.saveAndFlush(
            BatchFixtures.sessionPass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                remainingCount = BigDecimal("3.0"),
                endDate = LocalDate.now(clock).plusDays(30),
                createdAt = OffsetDateTime.now(clock).minusDays(14),
            ),
        )

    companion object {
        /**
         * 이 클래스 전용 픽스처 대역.
         *
         * 플랜은 `9_760_000_000L`을 지정했지만 그 대역은 05-14의
         * `InactivityBatchDeductionLimitOverrideTest`가 이미 쓰고 있어 한 칸 올렸다 — 대역이 겹치면
         * 한 클래스의 `@AfterEach`가 다른 클래스의 픽스처를 지운다(WR-07이 범위 정리를 도입한 이유).
         */
        const val KAKAO_ID_BASE = 9_770_000_000L
        const val KAKAO_ID_MAX = KAKAO_ID_BASE + 10_000L
        const val ADMIN_LOGIN_PREFIX = "admin-batch-run-concurrency-"

        private const val RUNS_PATH = "/api/admin/batch/inactivity-runs"

        /** 상태코드가 아닌 실패(예외)를 상태 목록에 섞어 넣어 단언에서 반드시 드러나게 하는 표식. */
        private const val UNEXPECTED_FAILURE_STATUS = -1

        private val logger = org.slf4j.LoggerFactory.getLogger(AdminBatchRunConcurrencyTest::class.java)
    }
}
