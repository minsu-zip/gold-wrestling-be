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
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * `InactivityBatchRunner`의 **회원 단위 예외 격리**(D-112)와 `PARTIAL_FAILURE` 집계, 그리고
 * 수동 실행 관리자 해석(D-113 `ck_batch_execution_trigger`)의 거부 경로를 실제 PostgreSQL로 검증한다.
 *
 * 별도 클래스인 이유: 예외 주입에 [MockitoSpyBean]이 필요해 `InactivityBatchRunnerTest`와 스프링
 * 컨텍스트가 갈린다. 그 클래스는 예외 격리를 "05-07에서 다룬다"고 미뤘으나 05-07(멱등·만료 실증)이
 * 다루지 않아 커버리지가 두 플랜 사이로 빠져 있었다 — PR #14 리뷰에서 지적된 갭을 여기서 닫는다.
 *
 * **클래스에 트랜잭션 애노테이션을 붙이지 않는다** — [InactivityDeductionService.deductOnce]가 회원별로
 * 독립 커밋해야 그 결과를 관측할 수 있다(`InactivityBatchRunnerTest` 선례). 대신 `@AfterEach`에서 직접 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class InactivityBatchFailureIsolationTest {
    @Autowired
    private lateinit var inactivityBatchRunner: InactivityBatchRunner

    /**
     * 스파이인 이유는 예외 격리 검증 하나뿐이다 — 회원 1명분 차감이 실패하는 상황은 DB 픽스처만으로
     * 결정론적으로 만들 수 없어(잔여 부족·경쟁 패배는 예외가 아니라 `false` 반환이다) 특정 회원 id에
     * 대해서만 예외를 주입한다. 나머지 회원은 실제 구현에 그대로 위임된다.
     */
    @MockitoSpyBean
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

    // ── D-112: 회원 단위 예외 격리 ────────────────────────────────────────────

    @Test
    fun `한 회원의 예외가 나머지 회원의 차감을 롤백하거나 중단시키지 않는다`() {
        val failing = persistMember()
        val healthy = persistMember()
        val failingPass = persistDeductiblePass(failing)
        val healthyPass = persistDeductiblePass(healthy)
        willThrow(IllegalStateException("의도적 실패")).given(inactivityDeductionService).deductOnce(failing.id!!)

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        createdBatchExecutionIds += result.id!!

        assertThat(remainingOf(healthyPass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(inactivityCountOf(healthyPass.id!!)).isEqualTo(1)
        assertThat(remainingOf(failingPass.id!!)).isEqualByComparingTo(BigDecimal("3.0"))
        assertThat(inactivityCountOf(failingPass.id!!)).isZero()
    }

    @Test
    fun `실패 회원이 있으면 PARTIAL_FAILURE로 남고 errorSummary에 그 회원 id가 적힌다`() {
        val failing = persistMember()
        val healthy = persistMember()
        persistDeductiblePass(failing)
        persistDeductiblePass(healthy)
        willThrow(IllegalStateException("의도적 실패")).given(inactivityDeductionService).deductOnce(failing.id!!)

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        createdBatchExecutionIds += result.id!!

        assertThat(result.status).isEqualTo(BatchExecutionStatus.PARTIAL_FAILURE)
        assertThat(result.processedMemberCount).isEqualTo(2)
        assertThat(result.deductedCount).isEqualTo(1)
        assertThat(result.errorSummary).contains("memberId=${failing.id}", "의도적 실패")
    }

    @Test
    fun `실패 회원이 없으면 SUCCESS로 남고 errorSummary는 null이다`() {
        val member = persistMember()
        persistDeductiblePass(member)

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        createdBatchExecutionIds += result.id!!

        assertThat(result.status).isEqualTo(BatchExecutionStatus.SUCCESS)
        assertThat(result.deductedCount).isEqualTo(1)
        assertThat(result.errorSummary).isNull()
    }

    // ── D-113: 수동 실행 관리자 해석 거부 경로 ────────────────────────────────

    @Test
    fun `MANUAL 트리거인데 관리자 id가 없으면 실행 이력을 남기지 않고 거부한다`() {
        val before = batchExecutionRepository.count()

        assertThatThrownBy { inactivityBatchRunner.run(BatchTrigger.MANUAL, null) }
            .isInstanceOf(IllegalArgumentException::class.java)

        assertThat(batchExecutionRepository.count()).isEqualTo(before)
    }

    @Test
    fun `MANUAL 트리거의 관리자 id가 존재하지 않으면 실행 이력을 남기지 않고 거부한다`() {
        val before = batchExecutionRepository.count()

        assertThatThrownBy { inactivityBatchRunner.run(BatchTrigger.MANUAL, ABSENT_ADMIN_ID) }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(batchExecutionRepository.count()).isEqualTo(before)
    }

    // ── fixtures ──────────────────────────────────────────────────────────

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

    /** 기준일이 14일 전이라 부족분 1이 나오는 `SESSION_PASS` — 이 클래스의 모든 대상 회원이 같은 조건이다. */
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
        const val KAKAO_ID_BASE = 9_710_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-inactivity-batch-failure-"
        const val ABSENT_ADMIN_ID = 9_999_999L
    }
}
