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
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
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
 * `InactivityDeductionService.deductOnce`의 부분 차감·회당 재선택·시스템 주체 이력·불변식을
 * 실제 PostgreSQL(Testcontainers)로 증명하는 통합테스트(05-05-PLAN.md, BATCH-01·02, D-109).
 *
 * **클래스에 `@Transactional`을 붙이지 않는다.** `deductOnce`는 자체 트랜잭션 경계를 여는 서비스
 * 메서드다 — 테스트를 트랜잭션으로 감싸면 조건부 UPDATE의 flush/clear 동작과 실제 커밋 결과를
 * 검증할 수 없다(`MemberReservationServiceTest` 선례). 대신 `@AfterEach`에서 이 테스트가 만든
 * 행을 직접 지운다.
 *
 * 각 `SESSION_PASS` 픽스처는 초기 잔여와 같은 금액의 `INITIAL_GRANT` 이력을 함께 남긴다 —
 * `PassLedgerInvariantTest`처럼 "잔여 = 이력 합계" 불변식이 픽스처 생성 시점부터 성립하게 하기
 * 위해서다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class InactivityDeductionServiceTest {
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
    fun `잔여 3dot0인 SESSION_PASS 한 장에서 1회 차감하면 잔여가 2dot0이 되고 반환은 true다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30))

        val deducted = inactivityDeductionService.deductOnce(member.id!!)

        assertThat(deducted).isTrue()
        assertLedgerInvariant(pass.id!!, BigDecimal("2.0"))
        val history = inactivityTransactionsOf(pass.id!!)
        assertThat(history).hasSize(1)
        assertThat(history.first().amount).isEqualByComparingTo(BigDecimal("-1.0"))
    }

    @Test
    fun `차감 이력은 admin과 member가 둘 다 null인 시스템 주체로 남고 발생 시각은 고정 Clock과 같다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30))

        inactivityDeductionService.deductOnce(member.id!!)

        val history = inactivityTransactionsOf(pass.id!!).single()
        assertThat(history.admin).isNull()
        assertThat(history.member).isNull()
        assertThat(history.reason).isEqualTo(TransactionReason.INACTIVITY)
        assertThat(history.note).isNull()
        assertThat(history.occurredAt).isEqualTo(OffsetDateTime.now(clock))
    }

    @Test
    fun `잔여 0dot5인 SESSION_PASS는 0dot5만 차감되어 잔여가 0이 된다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "0.5", endDate = today.plusDays(30))

        val deducted = inactivityDeductionService.deductOnce(member.id!!)

        assertThat(deducted).isTrue()
        assertLedgerInvariant(pass.id!!, BigDecimal.ZERO)
        val history = inactivityTransactionsOf(pass.id!!).single()
        assertThat(history.amount).isEqualByComparingTo(BigDecimal("-0.5"))
    }

    @Test
    fun `잔여 0dot5인 장과 잔여 3dot0인 장을 함께 보유하면 만료 임박한 0dot5 장에서만 차감되고 부족분이 이월되지 않는다`() {
        val member = persistMember()
        val nearlyExpired = persistSessionPass(member, remaining = "0.5", endDate = today.plusDays(5))
        val farFromExpiry = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(60))

        val deducted = inactivityDeductionService.deductOnce(member.id!!)

        assertThat(deducted).isTrue()
        assertLedgerInvariant(nearlyExpired.id!!, BigDecimal.ZERO)
        assertLedgerInvariant(farFromExpiry.id!!, BigDecimal("3.0"))
        assertThat(inactivityTransactionsOf(farFromExpiry.id!!)).isEmpty()
    }

    @Test
    fun `endDate가 다른 두 장 중 만료가 임박한 장에서 차감된다`() {
        val member = persistMember()
        val soonToExpire = persistSessionPass(member, remaining = "2.0", endDate = today.plusDays(5))
        val laterExpiry = persistSessionPass(member, remaining = "2.0", endDate = today.plusDays(60))

        inactivityDeductionService.deductOnce(member.id!!)

        assertLedgerInvariant(soonToExpire.id!!, BigDecimal("1.0"))
        assertLedgerInvariant(laterExpiry.id!!, BigDecimal("2.0"))
    }

    @Test
    fun `endDate가 같은 두 장 중 id가 작은 장에서 차감된다`() {
        val member = persistMember()
        val sameEndDate = today.plusDays(30)
        val firstRegistered = persistSessionPass(member, remaining = "2.0", endDate = sameEndDate)
        val secondRegistered = persistSessionPass(member, remaining = "2.0", endDate = sameEndDate)

        inactivityDeductionService.deductOnce(member.id!!)

        assertLedgerInvariant(firstRegistered.id!!, BigDecimal("1.0"))
        assertLedgerInvariant(secondRegistered.id!!, BigDecimal("2.0"))
    }

    @Test
    fun `잔여 1dot0인 한 장에 연속 2회 호출하면 1회차만 성공하고 이력은 1건만 남는다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(30))

        val first = inactivityDeductionService.deductOnce(member.id!!)
        val second = inactivityDeductionService.deductOnce(member.id!!)

        assertThat(first).isTrue()
        assertThat(second).isFalse()
        assertLedgerInvariant(pass.id!!, BigDecimal.ZERO)
        assertThat(inactivityTransactionsOf(pass.id!!)).hasSize(1)
    }

    @Test
    fun `잔여 1dot0인 두 장을 보유하면 연속 2회 호출마다 회당 재선택으로 서로 다른 장에서 차감된다`() {
        val member = persistMember()
        val first = persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(5))
        val second = persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(60))

        val firstCall = inactivityDeductionService.deductOnce(member.id!!)
        val secondCall = inactivityDeductionService.deductOnce(member.id!!)

        assertThat(firstCall).isTrue()
        assertThat(secondCall).isTrue()
        assertLedgerInvariant(first.id!!, BigDecimal.ZERO)
        assertLedgerInvariant(second.id!!, BigDecimal.ZERO)
    }

    @Test
    fun `차감 가능한 장이 없는 회원은 false를 반환한다`() {
        val member = persistMember()

        val deducted = inactivityDeductionService.deductOnce(member.id!!)

        assertThat(deducted).isFalse()
    }

    @Test
    fun `만료된 SESSION_PASS만 보유한 회원은 false를 반환한다`() {
        val member = persistMember()
        val expired = persistSessionPass(member, remaining = "2.0", endDate = today.minusDays(1))

        val deducted = inactivityDeductionService.deductOnce(member.id!!)

        assertThat(deducted).isFalse()
        assertLedgerInvariant(expired.id!!, BigDecimal("2.0"))
    }

    @Test
    fun `잔여 0인 SESSION_PASS만 보유한 회원은 false를 반환한다`() {
        val member = persistMember()
        val exhausted = persistSessionPass(member, remaining = "0.0", endDate = today.plusDays(30))

        val deducted = inactivityDeductionService.deductOnce(member.id!!)

        assertThat(deducted).isFalse()
        assertLedgerInvariant(exhausted.id!!, BigDecimal.ZERO)
    }

    @Test
    fun `등록 취소된 SESSION_PASS만 보유한 회원은 false를 반환한다`() {
        val member = persistMember()
        persistCanceledSessionPass(member, remaining = "2.0", endDate = today.plusDays(30))

        val deducted = inactivityDeductionService.deductOnce(member.id!!)

        assertThat(deducted).isFalse()
    }

    @Test
    fun `LESSON_PASS만 보유한 회원은 false를 반환하고 LESSON_PASS 잔여는 변하지 않는다`() {
        val member = persistMember()
        val lessonPass = persistLessonPass(member, remaining = BigDecimal("2.0"), endDate = today.plusDays(30))

        val deducted = inactivityDeductionService.deductOnce(member.id!!)

        assertThat(deducted).isFalse()
        val reloaded = passRepository.findById(lessonPass.id!!).get()
        assertThat(reloaded.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    /** 잔여가 [expectedRemaining]과 같고, 동시에 이력 합계와도 같음을 함께 확인한다. */
    private fun assertLedgerInvariant(
        passId: Long,
        expectedRemaining: BigDecimal,
    ) {
        val pass = passRepository.findById(passId).get()
        val sumOfHistory = passTransactionRepository.sumAmountByPassId(passId)
        assertThat(pass.remainingCount).isEqualByComparingTo(expectedRemaining)
        assertThat(pass.remainingCount).isEqualByComparingTo(sumOfHistory)
    }

    private fun inactivityTransactionsOf(passId: Long): List<PassTransaction> =
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

    /**
     * `SESSION_PASS`를 만들고 초기 잔여와 같은 `INITIAL_GRANT` 이력을 함께 남겨 불변식 기준선을 맞춘다.
     * 잔여가 0이면 이력을 남기지 않는다 — `ck_pass_transaction_amount_nonzero`(V4)가 금액 0인
     * 이력을 거부한다(D-065와 같은 이유).
     */
    private fun persistSessionPass(
        member: Member,
        remaining: String,
        endDate: LocalDate,
    ): Pass {
        val amount = BigDecimal(remaining)
        val pass =
            passRepository.saveAndFlush(
                BatchFixtures.sessionPass(
                    member = member,
                    branch = songpaBranch(),
                    registeredBy = persistAdmin(),
                    remainingCount = amount,
                    endDate = endDate,
                    createdAt = BatchFixtures.FIXED_TIME,
                ),
            )
        if (amount.compareTo(BigDecimal.ZERO) != 0) {
            passTransactionRepository.saveAndFlush(
                BatchFixtures.passTransaction(
                    pass = pass,
                    amount = amount,
                    reason = TransactionReason.INITIAL_GRANT,
                    occurredAt = BatchFixtures.FIXED_TIME,
                ),
            )
        }
        return pass
    }

    /**
     * 등록 취소된 `SESSION_PASS`를 직접 구성한다. `PassRepository.cancelIfNotCanceled`는 커스텀
     * `@Modifying` 쿼리라 명시적 `@Transactional` 없이 호출하면 기본 readOnly 트랜잭션이 붙어
     * flush가 실패한다(이 테스트 클래스는 클래스 레벨 `@Transactional`을 의도적으로 배제한다) —
     * 대신 취소 메타데이터(`ck_pass_cancellation`, V4)를 채운 채로 바로 저장한다.
     */
    private fun persistCanceledSessionPass(
        member: Member,
        remaining: String,
        endDate: LocalDate,
    ): Pass {
        val admin = persistAdmin()
        return passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = PassType.SESSION_PASS,
                status = PassStatus.CANCELED,
                startDate = endDate.minusYears(1).plusDays(1),
                endDate = endDate,
                remainingCount = BigDecimal(remaining),
                canceledBy = admin,
                canceledAt = BatchFixtures.FIXED_TIME,
                cancelReason = "테스트 취소",
                createdAt = BatchFixtures.FIXED_TIME,
            ),
        )
    }

    private fun persistLessonPass(
        member: Member,
        remaining: BigDecimal,
        endDate: LocalDate,
    ): Pass =
        passRepository.saveAndFlush(
            BatchFixtures.pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = persistAdmin(),
                type = PassType.LESSON_PASS,
                remainingCount = remaining,
                endDate = endDate,
            ),
        )

    companion object {
        const val KAKAO_ID_BASE = 9_600_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-inactivity-deduction-"
    }
}
