package com.goldwrestling.batch

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * **BATCH-04의 실증** — `InactivityBatchRunner.run`을 여러 스레드가 동시에 호출해도 총 `INACTIVITY`
 * 차감이 1회를 넘지 않음을 실제 PostgreSQL(Testcontainers)에서 증명한다.
 *
 * ### 이 테스트가 무엇을 뒤집는가
 * `05-VERIFICATION.md`가 BATCH-04를 **실패**로 판정한 근거는 "`run()`을 직렬화하는 장치가 없어
 * 두 실행이 각자 부족분 1을 읽고 각자 차감한다"였다(05-REVIEW.md CR-01). 그 시나리오는 그때까지
 * **아무도 실행해 본 적이 없었다** — 기존 `InactivityDeductionConcurrencyTest`는 `deductOnce`
 * 한 단계의 조건부 UPDATE만 경쟁시켰고, 그 조건부 UPDATE는 잔여 음수만 막지 "같은 주기가 이미
 * 차감됐는가"는 보지 않는다. 러너 레벨의 경쟁을 재현하는 것은 이 클래스가 처음이다.
 *
 * ### 왜 "예외가 정확히 1건"을 단언하지 않는가
 * 두 실행이 **시간상 겹치면** 뒤에 온 쪽은 `uq_batch_execution_running`(V10, D-117)에 막혀
 * [BatchAlreadyRunningException]을 받는다. 그런데 앞 실행이 아주 빨리 끝나면 뒤 실행은 거부되지
 * 않고 정상 시작하고, 대신 원장에 이미 `INACTIVITY` 이력이 있어 **부족분 0**을 계산한다(D-106
 * 상태 기반 캐치업). 스레드 스케줄링에 따라 둘 중 어느 쪽이든 나올 수 있고, **어느 쪽이든 총
 * 차감은 1회**다 — 그 불변식이 BATCH-04가 요구하는 것이고, 예외 건수는 아니다. 예외 건수를
 * 단언하면 CI 부하에 따라 깜빡이는(flaky) 테스트가 된다. 대신 실패한 호출이 **전부** 중복 실행
 * 거부인지(다른 종류의 예외 0건)를 확인한다.
 *
 * ### 격리
 * **클래스·메서드에 트랜잭션 애노테이션을 붙이지 않는다** — 각 스레드가 별도 트랜잭션이어야
 * 경쟁이 재현되고, `RUNNING` 행이 커밋돼야 다른 스레드에게 보인다(add-domain-test §4,
 * conventions §10.4). 그래서 [cleanUp]이 직접 지운다. 픽스처 대역은 이 클래스 전용
 * ([KAKAO_ID_BASE] ~ [KAKAO_ID_MAX])이고 정리 조건은 하한이 아니라 **범위**로 좁힌다 — 하한만
 * 쓰면 더 큰 대역을 쓰는 다른 클래스의 픽스처까지 지운다.
 *
 * **이 클래스가 만든 `batch_execution` 행은 반드시 지운다.** `RUNNING`이 하나라도 남으면 같은
 * 컨테이너를 쓰는 다른 테스트 클래스의 배치 실행이 전부 409로 막혀, 원인과 무관한 곳에서 실패가
 * 터진다(T-05D-11-01).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class InactivityBatchRunConcurrencyTest {
    @Autowired
    private lateinit var inactivityBatchRunner: InactivityBatchRunner

    @Autowired
    private lateinit var batchExecutionRepository: BatchExecutionRepository

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var passTransactionRepository: PassTransactionRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L
    private var baselineExecutionId: Long = 0
    private val today: LocalDate = BatchFixtures.FIXED_TODAY

    @BeforeEach
    fun resetClockAndBaseline() {
        (clock as MutableTestClock).setTo(BatchFixtures.FIXED_TIME.toInstant())
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
            .sql("delete from member where kakao_id >= :base and kakao_id < :max")
            .param("base", KAKAO_ID_BASE)
            .param("max", KAKAO_ID_MAX)
            .update()
        jdbcClient
            .sql("delete from admin where login_id like :prefix")
            .param("prefix", "$ADMIN_LOGIN_PREFIX%")
            .update()
    }

    /**
     * 결정론 버전 — 경쟁을 스레드로 만들지 않고 `RUNNING` 행을 미리 심어 "실행 중"을 고정한다.
     * 아래 경쟁 테스트가 우연히 겹치지 않아 통과하는 경우에도 이 테스트는 거부 경로를 반드시
     * 밟으므로, 직렬화 장치가 사라지면 여기서 먼저 깨진다.
     */
    @Test
    fun `이미 실행 중이면 run이 거부되고 잔여와 이력이 전혀 변하지 않는다`() {
        val member = persistMember()
        val pass = persistDeductiblePass(member)
        batchExecutionRepository.saveAndFlush(
            BatchExecution(
                trigger = BatchTrigger.SCHEDULED,
                triggeredByAdminId = null,
                startedAt = OffsetDateTime.now(clock),
                status = BatchExecutionStatus.RUNNING,
            ),
        )

        assertThatThrownBy { inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null) }
            .isInstanceOf(BatchAlreadyRunningException::class.java)

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("3.0"))
        assertThat(inactivityCountOf(pass.id!!)).isZero()
        // 거부된 실행은 이력도 남기지 않는다 — 미리 심은 RUNNING 행 1건이 전부다
        assertThat(executionsSinceBaseline()).hasSize(1)
    }

    @Test
    fun `네 스레드가 동시에 run을 호출해도 총 INACTIVITY 차감은 1회다`() {
        val member = persistMember()
        val pass = persistDeductiblePass(member)

        val outcome = runConcurrently(threadCount = 4)

        // ① 원장이 진실이다 — 성공 건수가 아니라 DB의 실제 이력 건수를 본다(conventions §10.4)
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(1)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))

        // ② 확정된 실행 이력들의 집계도 총 1회여야 한다 — 원장과 이력이 어긋나면 관리자가 보는
        //    숫자와 회원이 보는 잔여가 갈린다
        val executions = batchExecutionRepository.findAllById(outcome.startedExecutionIds)
        assertThat(executions.sumOf { it.deductedCount }).isEqualTo(1)
        assertThat(executions).allSatisfy {
            assertThat(it.status).isNotEqualTo(BatchExecutionStatus.RUNNING)
            assertThat(it.finishedAt).isNotNull()
        }

        // ③ 실패한 호출이 있었다면 전부 중복 실행 거부여야 한다(다른 종류의 예외 0건).
        //    건수는 단언하지 않는다 — 위 KDoc "왜 예외가 정확히 1건이 아닌가" 참조
        assertThat(outcome.failures).allSatisfy {
            assertThat(it).isInstanceOf(BatchAlreadyRunningException::class.java)
        }

        // ④ 어떤 경로로 끝났든 실행 중 행이 방치되지 않는다(WR-02)
        assertThat(batchExecutionRepository.findFirstByStatus(BatchExecutionStatus.RUNNING)).isNull()
    }

    /** [threadCount]개 스레드가 같은 순간에 [InactivityBatchRunner.run]을 호출하게 한다. */
    private fun runConcurrently(threadCount: Int): Outcome {
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val startedExecutionIds = Collections.synchronizedList(mutableListOf<Long>())
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    startedExecutionIds += inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null).id!!
                } catch (e: Throwable) {
                    failures.add(e)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = doneLatch.await(60, TimeUnit.SECONDS)
        executor.shutdown()
        assertThat(completed).isTrue()

        return Outcome(startedExecutionIds.toList(), failures.toList())
    }

    private data class Outcome(
        val startedExecutionIds: List<Long>,
        val failures: List<Throwable>,
    )

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun executionsSinceBaseline(): List<BatchExecution> =
        batchExecutionRepository.findAll().filter { it.id!! > baselineExecutionId }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun remainingOf(passId: Long): BigDecimal = passRepository.findById(passId).get().remainingCount!!

    private fun inactivityCountOf(passId: Long): Int =
        passTransactionRepository.findAll().count { it.pass.id == passId && it.reason == TransactionReason.INACTIVITY }

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(BatchFixtures.member(songpaBranch(), kakaoId = KAKAO_ID_BASE + fixtureCounter))
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(BatchFixtures.admin(loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter"))
    }

    /** 기준일(등록일)이 14일 전이라 부족분이 정확히 1인 `SESSION_PASS` — 이중 차감이 나면 잔여가 1.0이 된다. */
    private fun persistDeductiblePass(member: Member): Pass =
        passRepository.saveAndFlush(
            BatchFixtures.sessionPass(
                member = member,
                branch = songpaBranch(),
                registeredBy = persistAdmin(),
                remainingCount = BigDecimal("3.0"),
                endDate = today.plusDays(30),
                createdAt =
                    today
                        .minusDays(14)
                        .atStartOfDay(BatchFixtures.FIXED_TIME.offset)
                        .plusHours(9)
                        .toOffsetDateTime(),
            ),
        )

    companion object {
        const val KAKAO_ID_BASE = 9_740_000_000L
        const val KAKAO_ID_MAX = KAKAO_ID_BASE + 10_000L
        const val ADMIN_LOGIN_PREFIX = "admin-inactivity-run-concurrency-"
    }
}
