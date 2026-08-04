package com.goldwrestling.pass

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * `PassRepository`의 조건부 UPDATE 2종·`PassTransactionRepository`의 합계 집계·`ck_pass_remaining_count_by_type`
 * CHECK 제약을 실제 PostgreSQL(Testcontainers)에서 증명하는 통합테스트. 애노테이션 조합은
 * `MemberSearchTest`와 동일하게 유지한다(conventions §10.1 — 컨텍스트 캐시 재사용).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class PassRepositoryTest {
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

    private var fixtureCounter = 9000L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `adjustRemainingCount 0-5 가산은 1을 반환하고 잔여가 0-5 늘어난다`() {
        val pass = persistPass(remaining = BigDecimal("1.0"))

        val updated = passRepository.adjustRemainingCount(pass.id!!, BigDecimal("0.5"))

        assertThat(updated).isEqualTo(1)
        val reloaded = passRepository.findById(pass.id!!).get()
        assertThat(reloaded.remainingCount).isEqualByComparingTo(BigDecimal("1.5"))
    }

    @Test
    fun `adjustRemainingCount 결과가 음수가 되면 0을 반환하고 잔여가 그대로다`() {
        val pass = persistPass(remaining = BigDecimal("0.5"))

        val updated = passRepository.adjustRemainingCount(pass.id!!, BigDecimal("-1.0"))

        assertThat(updated).isEqualTo(0)
        val reloaded = passRepository.findById(pass.id!!).get()
        assertThat(reloaded.remainingCount).isEqualByComparingTo(BigDecimal("0.5"))
    }

    @Test
    fun `잔여를 정확히 0으로 만드는 가감은 성공한다`() {
        val pass = persistPass(remaining = BigDecimal("1.0"))

        val updated = passRepository.adjustRemainingCount(pass.id!!, BigDecimal("-1.0"))

        assertThat(updated).isEqualTo(1)
        val reloaded = passRepository.findById(pass.id!!).get()
        assertThat(reloaded.remainingCount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `zeroRemainingCount는 DB 잔여가 0-50으로 저장돼 있어도 1을 반환한다`() {
        val pass = persistPass(remaining = BigDecimal("0.50"))

        val updated = passRepository.zeroRemainingCount(pass.id!!, BigDecimal("0.5"))

        assertThat(updated).isEqualTo(1)
        val reloaded = passRepository.findById(pass.id!!).get()
        assertThat(reloaded.remainingCount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `zeroRemainingCount가 실제 잔여와 다르면 0을 반환하고 값이 그대로다`() {
        val pass = persistPass(remaining = BigDecimal("1.0"))

        val updated = passRepository.zeroRemainingCount(pass.id!!, BigDecimal("0.5"))

        assertThat(updated).isEqualTo(0)
        val reloaded = passRepository.findById(pass.id!!).get()
        assertThat(reloaded.remainingCount).isEqualByComparingTo(BigDecimal("1.0"))
    }

    @Test
    fun `sumAmountByPassId는 이력이 없으면 0을 반환한다`() {
        val pass = persistPass(remaining = BigDecimal("1.0"))

        val sum = passTransactionRepository.sumAmountByPassId(pass.id!!)

        assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `기간제 이용권에 remainingCount를 채워 저장하면 DB CHECK가 저장을 거부한다`() {
        val member = persistMember()
        val admin = persistAdmin()

        assertThatThrownBy {
            passRepository.saveAndFlush(
                Pass(
                    member = member,
                    branch = songpaBranch(),
                    registeredBy = admin,
                    type = PassType.EVENING_MEMBERSHIP,
                    status = PassStatus.ACTIVE,
                    startDate = LocalDate.now(clock),
                    endDate = LocalDate.now(clock).plusMonths(1).minusDays(1),
                    remainingCount = BigDecimal("1.0"),
                    createdAt = OffsetDateTime.now(clock),
                ),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "회원$fixtureCounter",
                phoneNumber = "0101234$fixtureCounter",
                status = MemberStatus.ACTIVE,
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
                loginId = "admin-pass-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistPass(
        remaining: BigDecimal,
        type: PassType = PassType.SESSION_PASS,
    ): Pass {
        val member = persistMember()
        val admin = persistAdmin()
        return passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = type,
                status = PassStatus.ACTIVE,
                startDate = LocalDate.now(clock),
                endDate = LocalDate.now(clock).plusYears(1).minusDays(1),
                remainingCount = remaining,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
