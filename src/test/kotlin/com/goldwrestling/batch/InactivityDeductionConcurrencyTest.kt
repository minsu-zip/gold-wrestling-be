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
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * `InactivityDeductionService.deductOnce`의 조건부 UPDATE(D-021)가 **실제 동시 트랜잭션**에서도
 * 이중 차감을 막는지 검증한다 — `InactivityDeductionRaceTest`가 Mockito로 "0행이면 이력을 남기지
 * 않는다"는 분기 로직만 증명하는 것과 달리, 여기서는 두 스레드가 같은 이용권을 실제로 경쟁한다
 * (PR #14 리뷰 Info 대응, CLAUDE.md "동시성이 걸린 코드는 동시성 테스트를 함께 작성").
 *
 * 애노테이션 조합은 `InactivityBatchRunnerTest`와 동일하게 유지해 스프링 컨텍스트 캐시를 공유한다
 * (conventions §10.1). **이 클래스·메서드에는 `@Transactional`을 붙이지 않는다** — 각 스레드가
 * 별도 트랜잭션이어야 조건부 UPDATE 경쟁이 실제로 재현된다(add-domain-test §2·§4,
 * `PassCancellationConcurrencyTest` 선례). 그래서 [cleanUp]에서 만든 데이터를 직접 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class InactivityDeductionConcurrencyTest {
    @Autowired
    private lateinit var inactivityDeductionService: InactivityDeductionService

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
    private val today: LocalDate = BatchFixtures.FIXED_TODAY

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(BatchFixtures.FIXED_TIME.toInstant())
    }

    @AfterEach
    fun cleanUp() {
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
    fun `잔여 1회를 두 스레드가 동시에 차감하면 한 번만 성공하고 잔여가 음수가 되지 않는다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "1.0")

        val outcomes = runConcurrentDeductions(member.id!!, threadCount = 2)

        // 조건부 UPDATE(`remaining_count + :amount >= 0`)가 패자를 0행으로 되돌린다.
        assertThat(outcomes.trueCount).isEqualTo(1)
        assertThat(outcomes.failures).isEmpty()

        val reloaded = passRepository.findById(pass.id!!).get()
        assertThat(reloaded.remainingCount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(inactivityTransactionsOf(pass.id!!)).hasSize(1)
        assertLedgerInvariant(pass.id!!, "1.0")
    }

    @Test
    fun `잔여 2회를 세 스레드가 동시에 차감하면 정확히 두 번만 성공한다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "2.0")

        val outcomes = runConcurrentDeductions(member.id!!, threadCount = 3)

        assertThat(outcomes.trueCount).isEqualTo(2)
        assertThat(outcomes.failures).isEmpty()

        val reloaded = passRepository.findById(pass.id!!).get()
        assertThat(reloaded.remainingCount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(inactivityTransactionsOf(pass.id!!)).hasSize(2)
        assertLedgerInvariant(pass.id!!, "2.0")
    }

    /** [threadCount]개 스레드가 같은 순간에 [InactivityDeductionService.deductOnce]를 호출하게 한다. */
    private fun runConcurrentDeductions(
        memberId: Long,
        threadCount: Int,
    ): Outcomes {
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val trueCount = AtomicInteger(0)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    if (inactivityDeductionService.deductOnce(memberId)) {
                        trueCount.incrementAndGet()
                    }
                } catch (e: Throwable) {
                    failures.add(e)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()
        assertThat(completed).isTrue()

        return Outcomes(trueCount.get(), failures)
    }

    private data class Outcomes(
        val trueCount: Int,
        val failures: List<Throwable>,
    )

    /**
     * Core Value 불변식 — 잔여 = 등록 시 부여분 + 이력 합계. 이 테스트의 픽스처는 `INITIAL_GRANT`
     * 이력을 만들지 않으므로 부여분을 [initialRemaining]으로 직접 받는다.
     */
    private fun assertLedgerInvariant(
        passId: Long,
        initialRemaining: String,
    ) {
        val ledgerSum =
            passTransactionRepository
                .findAll()
                .filter { it.pass.id == passId }
                .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
        val remaining = passRepository.findById(passId).get().remainingCount!!
        assertThat(remaining).isEqualByComparingTo(BigDecimal(initialRemaining) + ledgerSum)
    }

    private fun inactivityTransactionsOf(passId: Long) =
        passTransactionRepository.findAll().filter { it.pass.id == passId && it.reason == TransactionReason.INACTIVITY }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(BatchFixtures.member(songpaBranch(), kakaoId = KAKAO_ID_BASE + fixtureCounter))
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(BatchFixtures.admin(loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter"))
    }

    private fun persistSessionPass(
        member: Member,
        remaining: String,
    ): Pass =
        passRepository.saveAndFlush(
            BatchFixtures.sessionPass(
                member = member,
                branch = songpaBranch(),
                registeredBy = persistAdmin(),
                remainingCount = BigDecimal(remaining),
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
        const val KAKAO_ID_BASE = 9_720_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-inactivity-deduction-concurrency-"
    }
}
