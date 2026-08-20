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
 * 미사용 차감의 **정책 시행일 하한**과 **1회 실행 상한**(D-119, CR-02)을 실제 PostgreSQL로
 * 실증하는 통합테스트. 05-VERIFICATION.md가 BATCH-01을 실패로 판정한 두 경로 중
 * "배포 후 첫 실행이 과거 전체를 소급 차감한다"를 닫는 근거다.
 *
 * ### 왜 클래스 레벨 `properties`인가
 * `build.gradle.kts`의 테스트 태스크는 시행일을 `2000-01-01`로 고정한다 — 프로덕션 기본값
 * (`2026-09-01`)이 배치 테스트의 고정 시각([BatchFixtures.FIXED_TODAY] = 2026-08-02)보다 미래라,
 * 고정하지 않으면 **모든 배치 테스트의 기준일이 시행일로 끌어올려져 기대 차감 수가 0이 되고**
 * 멱등·캐치업 테스트가 "아무것도 차감하지 않음"을 검증하는 빈 껍데기가 되기 때문이다.
 * 그래서 시행일 자체를 검증하는 이 클래스만 전역 값을 [POLICY_EFFECTIVE_DATE]로 덮어쓴다 —
 * 전역 값에 의존하면 이 테스트가 "하한이 실제로 작동하는가"를 증명하지 못한다.
 *
 * 시행일은 클래스 단위로 한 값만 줄 수 있으므로, 경계 두 케이스(시행일 −13일 / −15일)는
 * **프로퍼티가 아니라 clock을 옮겨서** 만든다([MutableTestClock.setTo]).
 *
 * **클래스에 트랜잭션 애노테이션을 붙이지 않는다** — [InactivityDeductionService.deductOnce]가
 * 회원별 독립 트랜잭션을 실제로 커밋해야 "두 번째 실행이 첫 실행의 이력을 본다"가 성립한다
 * (`InactivityBatchIdempotencyTest` 선례). 대신 `@AfterEach`가 이 테스트의 픽스처 대역만 지운다.
 */
@SpringBootTest(properties = ["goldwrestling.batch.inactivity.policy-effective-date=2026-08-01"])
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class InactivityBatchPolicyLimitTest {
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

    @BeforeEach
    fun setUp() {
        batchExecutionIdBaseline =
            jdbcClient.sql("select coalesce(max(id), 0) from batch_execution").query(Long::class.java).single()
    }

    @AfterEach
    fun cleanUp() {
        // 예외 경로에서도 실행 이력이 남을 수 있어 반환값이 아니라 baseline으로 지운다(05-13 관례).
        jdbcClient
            .sql("delete from batch_execution where id > :baseline")
            .param("baseline", batchExecutionIdBaseline)
            .update()
        // 픽스처 대역을 하한이 아니라 **범위**로 좁힌다(WR-07) — 다른 테스트 클래스의 행을 지우지 않는다.
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

    // ── 1회 실행 상한 (D-119) ────────────────────────────────────────────────

    @Test
    fun `200일 방치된 회원도 한 번의 실행에서 차감은 1회다`() {
        val today = POLICY_EFFECTIVE_DATE.plusDays(200)
        setToday(today)
        val pass = persistNeglectedSessionPass(remaining = "5.0", today = today, neglectedDays = 200)

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)

        // 상한이 없으면 기대 차감 수는 200 / 14 = 14회이고 잔여 5.0이 한 실행에 0이 된다(CR-02).
        assertThat(result.deductedCount).isEqualTo(1)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("4.0"))
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(1)
        assertLedgerInvariant(pass.id!!)
    }

    @Test
    fun `상한으로 잘린 부족분은 스킵으로 세지 않는다 — skippedCount는 대상 소진·경쟁 패배 전용이다`() {
        val today = POLICY_EFFECTIVE_DATE.plusDays(200)
        setToday(today)
        persistNeglectedSessionPass(remaining = "5.0", today = today, neglectedDays = 200)

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)

        // 상한 적용은 사고가 아니라 정상 예정 동작이다(D-113) — 로그로만 남긴다.
        assertThat(result.skippedCount).isZero()
        assertThat(result.status).isEqualTo(BatchExecutionStatus.SUCCESS)
    }

    @Test
    fun `하루 뒤 다시 실행하면 또 1회만 차감된다 — 밀린 주기는 다음 실행들이 이어받는다`() {
        val firstDay = POLICY_EFFECTIVE_DATE.plusDays(200)
        setToday(firstDay)
        val pass = persistNeglectedSessionPass(remaining = "5.0", today = firstDay, neglectedDays = 200)

        val first = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        setToday(firstDay.plusDays(1))
        val second = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)

        assertThat(first.deductedCount).isEqualTo(1)
        assertThat(second.deductedCount).isEqualTo(1)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("3.0"))
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(2)
        assertLedgerInvariant(pass.id!!)
    }

    // ── 정책 시행일 하한의 경계 (D-119) ─────────────────────────────────────

    @Test
    fun `정책 시행일이 오늘 기준 13일 전이면 부족분이 0이라 차감이 없다 — 시행일 이전 기간은 부채가 아니다`() {
        val today = POLICY_EFFECTIVE_DATE.plusDays(13)
        setToday(today)
        val pass = persistNeglectedSessionPass(remaining = "5.0", today = today, neglectedDays = 200)

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)

        assertThat(result.deductedCount).isZero()
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("5.0"))
        assertThat(inactivityCountOf(pass.id!!)).isZero()
    }

    @Test
    fun `정책 시행일이 오늘 기준 15일 전이면 정확히 1회 차감된다`() {
        val today = POLICY_EFFECTIVE_DATE.plusDays(15)
        setToday(today)
        val pass = persistNeglectedSessionPass(remaining = "5.0", today = today, neglectedDays = 200)

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)

        assertThat(result.deductedCount).isEqualTo(1)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("4.0"))
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(1)
        assertLedgerInvariant(pass.id!!)
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    /** clock을 [date] 10:00 `Asia/Seoul`로 옮긴다 — 시행일 경계는 프로퍼티가 아니라 이 이동으로 만든다. */
    private fun setToday(date: LocalDate) {
        (clock as MutableTestClock).setTo(date.atTime(10, 0).atOffset(BatchFixtures.FIXED_TIME.offset).toInstant())
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun remainingOf(passId: Long): BigDecimal = passRepository.findById(passId).get().remainingCount!!

    private fun inactivityCountOf(passId: Long): Int =
        passTransactionRepository.findAll().count { it.pass.id == passId && it.reason == TransactionReason.INACTIVITY }

    /** 잔여 == `PassTransaction` 이력 합계(Core Value). */
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

    /**
     * [neglectedDays]일 전에 등록되고 그 뒤 한 번도 쓰이지 않은 `SESSION_PASS`를 만든다 —
     * 기준일 후보가 등록일 하나뿐이라 하한이 없으면 그대로 소급 차감 대상이 된다.
     * 초기 잔여만큼의 `INITIAL_GRANT` 이력을 함께 남겨 원장 불변식의 기준선을 세운다.
     */
    private fun persistNeglectedSessionPass(
        remaining: String,
        today: LocalDate,
        neglectedDays: Long,
    ): Pass {
        val registeredAt =
            today.minusDays(neglectedDays).atTime(9, 0).atOffset(BatchFixtures.FIXED_TIME.offset)
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
        /** `@SpringBootTest(properties = ...)`에 준 값과 **같은 날짜**여야 한다 — 애노테이션은 상수를 못 받는다. */
        private val POLICY_EFFECTIVE_DATE: LocalDate = LocalDate.of(2026, 8, 1)

        const val KAKAO_ID_BASE = 9_750_000_000L
        const val KAKAO_ID_MAX = KAKAO_ID_BASE + 10_000L
        const val ADMIN_LOGIN_PREFIX = "admin-inactivity-policy-limit-"
    }
}
