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
import java.time.OffsetDateTime

/**
 * `InactivityBatchRunner.run`의 멱등·캐치업(BATCH-04, D-106)을 실제 PostgreSQL(Testcontainers)로
 * 반복 실행해 실증하는 통합테스트(05-07-PLAN.md Task 1, conventions §10.0 "배치 → 멱등성 테스트").
 *
 * **클래스에 트랜잭션 애노테이션을 붙이지 않는다.** [InactivityDeductionService.deductOnce]가
 * 회원별로 독립 트랜잭션을 실제로 커밋해야 "두 번째 실행이 첫 실행의 이력을 본다"가 성립한다
 * (05-06 `InactivityBatchRunnerTest` 선례) — 대신 `@AfterEach`에서 이 테스트가 만든 행을 지운다.
 *
 * 모든 시나리오는 끝에 `assertLedgerInvariant(passId)`(잔여 == `PassTransactionRepository.sumAmountByPassId`)를
 * 호출한다 — 이 값이 성립하려면 픽스처가 만드는 초기 잔여도 `INITIAL_GRANT` 이력으로 뒷받침돼야 하므로,
 * [persistSessionPass]가 `Pass`와 함께 `INITIAL_GRANT` `PassTransaction`을 함께 저장한다
 * (`AdminPassService.register`가 프로덕션 경로에서 하는 일을 픽스처가 대신한다).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class InactivityBatchIdempotencyTest {
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
    private lateinit var batchExecutionRepository: BatchExecutionRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L
    private val today: LocalDate = BatchFixtures.FIXED_TODAY
    private val createdBatchExecutionIds = mutableListOf<Long>()

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(BatchFixtures.FIXED_TIME.toInstant())
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
    fun `같은 날 배치를 두 번 실행해도 이중 차감이 없다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30), createdAt = today.minusDays(14).atTime9am())

        val first = runOnce(BatchTrigger.SCHEDULED, null)
        val second = runOnce(BatchTrigger.SCHEDULED, null)

        assertThat(first.deductedCount).isEqualTo(1)
        assertThat(second.deductedCount).isZero()
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(1)
        assertThat(createdBatchExecutionIds).hasSize(2)
        assertLedgerInvariant(pass.id!!)
    }

    @Test
    fun `같은 날 5회 실행해도 이력이 1건으로 반복 호출에 안전하다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30), createdAt = today.minusDays(14).atTime9am())

        repeat(5) { runOnce(BatchTrigger.SCHEDULED, null) }

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(1)
        assertLedgerInvariant(pass.id!!)
    }

    /**
     * D-119의 1회 실행 상한이 캐치업의 **속도**만 바꾸고 **총량**은 그대로 둔다는 것을 고정한다 —
     * 상한 도입 전에는 첫 실행이 3회를 몰아서 깎았다. 밀린 주기가 사라지지 않고 실행마다 1회씩
     * 이어받는 것이 D-106 상태 기반 캐치업이 여전히 작동한다는 증거다.
     */
    @Test
    fun `배치가 6주 밀리면 실행마다 1회씩 이어받아 밀린 3회를 결국 다 차감한다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30), createdAt = today.minusDays(42).atTime9am())

        val results = (1..4).map { runOnce(BatchTrigger.SCHEDULED, null) }

        assertThat(results.map { it.deductedCount }).containsExactly(1, 1, 1, 0)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(3)
        assertLedgerInvariant(pass.id!!)
    }

    @Test
    fun `14일 뒤 재실행하면 밀린 한 주기만큼만 추가로 차감된다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30), createdAt = today.minusDays(14).atTime9am())
        runOnce(BatchTrigger.SCHEDULED, null)

        (clock as MutableTestClock).setTo(BatchFixtures.FIXED_TIME.plusDays(14).toInstant())
        runOnce(BatchTrigger.SCHEDULED, null)

        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(2)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("1.0"))
        assertLedgerInvariant(pass.id!!)
    }

    /**
     * 잔여가 차감량(1.0)보다 적으면 잔여만큼만 차감한다(policies §4.3). 상한 `1` 아래에서는 이
     * 부분 차감이 실행 두 번에 걸쳐 일어나고, 소진된 회원은 **다음 실행의 대상 목록에서 아예
     * 빠진다**(`remainingCount > 0` 필터) — 그래서 세 번째 실행은 스킵이 아니라 처리 인원 0이다.
     * (상한 도입 전에는 한 실행 안에서 2회 차감 + 대상 소진 스킵 1건이었다 — 그 스킵 계약은
     * `InactivityBatchDeductionLimitOverrideTest`가 이어받았다.)
     */
    @Test
    fun `캐치업 중 잔여가 부분 소진되면 잔여만큼만 차감되고 소진 후에는 대상에서 빠진다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "1.5", endDate = today.plusDays(30), createdAt = today.minusDays(42).atTime9am())

        val first = runOnce(BatchTrigger.SCHEDULED, null)
        val second = runOnce(BatchTrigger.SCHEDULED, null)
        val third = runOnce(BatchTrigger.SCHEDULED, null)

        assertThat(first.deductedCount).isEqualTo(1)
        assertThat(second.deductedCount).isEqualTo(1)
        assertThat(third.processedMemberCount).isZero()
        assertThat(third.deductedCount).isZero()
        assertThat(third.status).isEqualTo(BatchExecutionStatus.SUCCESS)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(2)
        assertLedgerInvariant(pass.id!!)
    }

    @Test
    fun `차감 후 관리자가 양의 가감을 하면 가감일부터 유예가 다시 시작돼 추가 차감이 없다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30), createdAt = today.minusDays(42).atTime9am())
        // 1회 실행 상한(D-119) 때문에 밀린 3주기를 따라잡으려면 실행이 3번 필요하다.
        repeat(3) { runOnce(BatchTrigger.SCHEDULED, null) }
        val remainingAfterCatchUp = remainingOf(pass.id!!)
        assertThat(remainingAfterCatchUp).isEqualByComparingTo(BigDecimal.ZERO)

        applyPositiveAdjustment(pass, amount = "2.0", occurredAt = today.atTime9am())
        val result = runOnce(BatchTrigger.SCHEDULED, null)

        assertThat(result.deductedCount).isZero()
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(3)
        assertLedgerInvariant(pass.id!!)
    }

    @Test
    fun `재기동으로 SCHEDULED와 MANUAL 실행이 같은 날 섞여도 총 차감은 1건이다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30), createdAt = today.minusDays(14).atTime9am())
        val admin = persistAdmin()

        val scheduledResult = runOnce(BatchTrigger.SCHEDULED, null)
        val manualResult = runOnce(BatchTrigger.MANUAL, admin.id)

        assertThat(scheduledResult.deductedCount).isEqualTo(1)
        assertThat(manualResult.deductedCount).isZero()
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(1)
        assertLedgerInvariant(pass.id!!)
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun runOnce(
        trigger: BatchTrigger,
        triggeredByAdminId: Long?,
    ): BatchExecution {
        val result = inactivityBatchRunner.run(trigger, triggeredByAdminId)
        createdBatchExecutionIds += result.id!!
        return result
    }

    private fun LocalDate.atTime9am(): OffsetDateTime = this.atStartOfDay(BatchFixtures.FIXED_TIME.offset).plusHours(9).toOffsetDateTime()

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun remainingOf(passId: Long): BigDecimal = passRepository.findById(passId).get().remainingCount!!

    private fun inactivityCountOf(passId: Long): Int =
        passTransactionRepository.findAll().count { it.pass.id == passId && it.reason == TransactionReason.INACTIVITY }

    /** 잔여 == `PassTransaction` 이력 합계(Core Value) — 05-07-PLAN.md의 필수 종료 단언. */
    private fun assertLedgerInvariant(passId: Long) {
        val pass = passRepository.findById(passId).get()
        val sumOfHistory = passTransactionRepository.sumAmountByPassId(passId)
        assertThat(pass.remainingCount).isEqualByComparingTo(sumOfHistory)
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

    /** `SESSION_PASS`와 함께 초기 잔여만큼의 `INITIAL_GRANT` 이력을 저장해 원장 불변식의 기준선을 세운다. */
    private fun persistSessionPass(
        member: Member,
        remaining: String,
        endDate: LocalDate,
        createdAt: OffsetDateTime,
    ): Pass {
        val pass =
            passRepository.saveAndFlush(
                BatchFixtures.sessionPass(
                    member = member,
                    branch = songpaBranch(),
                    registeredBy = persistAdmin(),
                    remainingCount = BigDecimal(remaining),
                    endDate = endDate,
                    createdAt = createdAt,
                ),
            )
        passTransactionRepository.saveAndFlush(
            BatchFixtures.passTransaction(
                pass = pass,
                amount = BigDecimal(remaining),
                reason = TransactionReason.INITIAL_GRANT,
                occurredAt = createdAt,
            ),
        )
        return pass
    }

    /**
     * 기준일 후보 ⑤(D-105)를 만든다 — 잔여를 실제로 늘리고 그만큼 `ADMIN_ADJUST` 이력을 남긴다.
     * 호출부가 들고 있던 [pass] 참조는 배치 차감(조건부 UPDATE, 준영속)으로 값이 낡았을 수 있어
     * DB에서 다시 조회한 값 위에 가감한다.
     */
    private fun applyPositiveAdjustment(
        pass: Pass,
        amount: String,
        occurredAt: OffsetDateTime,
    ) {
        val refreshedPass = passRepository.findById(pass.id!!).get()
        refreshedPass.remainingCount = refreshedPass.remainingCount!! + BigDecimal(amount)
        passRepository.saveAndFlush(refreshedPass)
        passTransactionRepository.saveAndFlush(
            BatchFixtures.passTransaction(
                pass = refreshedPass,
                amount = BigDecimal(amount),
                reason = TransactionReason.ADMIN_ADJUST,
                occurredAt = occurredAt,
            ),
        )
    }

    companion object {
        const val KAKAO_ID_BASE = 9_710_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-inactivity-idempotency-"
    }
}
