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

/**
 * 1회 실행 상한(D-119)을 기본값 `1`이 아닌 값으로 **덮어썼을 때**의 동작을 고정한다.
 *
 * ### 왜 이 클래스가 필요한가 (두 가지 다 다른 곳에서는 검증할 수 없다)
 * 1. **상한이 상수가 아니라 설정값이라는 증명.** 나머지 모든 테스트는 상한 기본값 `1` 아래에서
 *    돈다 — 러너가 `minOf(shortfall, properties.maxDeductionsPerRun)`이 아니라
 *    `minOf(shortfall, 1)`로 하드코딩돼 있어도 전부 초록불이다. 여기서만 그 차이가 드러난다.
 * 2. **대상 소진 스킵(D-113)의 계약.** 상한이 `1`이면 한 회원당 [InactivityDeductionService.deductOnce]
 *    호출이 최대 1회인데, 그 첫 호출은 대상 회원 벌크 조회와 필터가 같아 단일 스레드에서는 항상
 *    성공한다 — 즉 상한 `1` 아래에서 "대상 소진 → 스킵 → 루프 중단" 경로는 **경쟁이 없으면
 *    도달 불가능**하다. 상한을 올려야 그 분기가 되살아난다.
 *    (원래 `InactivityBatchRunnerTest`에 있던 계약을 상한 도입에 맞춰 이리로 옮겼다.)
 *
 * 시행일은 전역 테스트 값(`build.gradle.kts`의 `2000-01-01`)을 그대로 쓴다 — 이 클래스의 관심사는
 * 상한이지 하한이 아니다. 하한의 경계는 [InactivityBatchPolicyLimitTest]가 담당한다.
 */
@SpringBootTest(properties = ["goldwrestling.batch.inactivity.max-deductions-per-run=3"])
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class InactivityBatchDeductionLimitOverrideTest {
    @Autowired
    private lateinit var inactivityBatchRunner: InactivityBatchRunner

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
    private var batchExecutionIdBaseline = 0L
    private val today: LocalDate = BatchFixtures.FIXED_TODAY

    @BeforeEach
    fun setUp() {
        (clock as MutableTestClock).setTo(BatchFixtures.FIXED_TIME.toInstant())
        batchExecutionIdBaseline =
            jdbcClient.sql("select coalesce(max(id), 0) from batch_execution").query(Long::class.java).single()
    }

    @AfterEach
    fun cleanUp() {
        jdbcClient
            .sql("delete from batch_execution where id > :baseline")
            .param("baseline", batchExecutionIdBaseline)
            .update()
        jdbcClient
            .sql(
                "delete from pass_transaction where pass_id in (select id from pass where member_id in " +
                    "(select id from member where kakao_id >= :base and kakao_id < :max))",
            ).param("base", KAKAO_ID_BASE)
            .param("max", KAKAO_ID_MAX)
            .update()
        jdbcClient
            .sql(
                "delete from pass where member_id in " +
                    "(select id from member where kakao_id >= :base and kakao_id < :max)",
            ).param("base", KAKAO_ID_BASE)
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

    @Test
    fun `상한을 3으로 올리면 한 실행에서 3회까지 차감된다 — 상한은 코드 상수가 아니라 설정값이다`() {
        val pass = persistSessionPass(remaining = "5.0", neglectedDays = 42)

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)

        assertThat(result.deductedCount).isEqualTo(3)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(3)
        assertLedgerInvariant(pass.id!!)
    }

    @Test
    fun `부족분이 2인데 잔여 1dot0인 장 한 장뿐이면 1회만 차감되고 스킵 1건으로 루프가 멈춘다`() {
        val pass = persistSessionPass(remaining = "1.0", neglectedDays = 28)

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)

        assertThat(result.deductedCount).isEqualTo(1)
        assertThat(result.skippedCount).isEqualTo(1)
        assertThat(result.status).isEqualTo(BatchExecutionStatus.SUCCESS)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(1)
        assertLedgerInvariant(pass.id!!)
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun remainingOf(passId: Long): BigDecimal = passRepository.findById(passId).get().remainingCount!!

    private fun inactivityCountOf(passId: Long): Int =
        passTransactionRepository.findAll().count { it.pass.id == passId && it.reason == TransactionReason.INACTIVITY }

    private fun assertLedgerInvariant(passId: Long) {
        val pass = passRepository.findById(passId).get()
        assertThat(pass.remainingCount).isEqualByComparingTo(passTransactionRepository.sumAmountByPassId(passId))
    }

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            BatchFixtures.member(songpaBranch(), kakaoId = KAKAO_ID_BASE + fixtureCounter),
        )
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(BatchFixtures.admin(loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter"))
    }

    private fun persistSessionPass(
        remaining: String,
        neglectedDays: Long,
    ): Pass {
        val registeredAt = today.minusDays(neglectedDays).atTime(9, 0).atOffset(BatchFixtures.FIXED_TIME.offset)
        val pass =
            passRepository.saveAndFlush(
                BatchFixtures.sessionPass(
                    member = persistMember(),
                    branch = songpaBranch(),
                    registeredBy = persistAdmin(),
                    remainingCount = BigDecimal(remaining),
                    endDate = today.plusDays(30),
                    createdAt = registeredAt,
                ),
            )
        passTransactionRepository.saveAndFlush(
            BatchFixtures.passTransaction(
                pass = pass,
                amount = BigDecimal(remaining),
                reason = TransactionReason.INITIAL_GRANT,
                occurredAt = registeredAt,
            ),
        )
        return pass
    }

    companion object {
        const val KAKAO_ID_BASE = 9_760_000_000L
        const val KAKAO_ID_MAX = KAKAO_ID_BASE + 10_000L
        const val ADMIN_LOGIN_PREFIX = "admin-inactivity-limit-override-"
    }
}
