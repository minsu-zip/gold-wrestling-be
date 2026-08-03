package com.goldwrestling.pass

// 03-09가 등록 취소 케이스를 이 클래스에 추가한다 — 취소 상쇄까지 포함한 전체 순환은 그 플랜에서 닫힌다.

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.dto.AdjustPassRequest
import com.goldwrestling.pass.dto.RegisterPassRequest
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
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime

/**
 * "회원이 보는 잔여 횟수는 항상 실제 사용 가능 횟수와 일치한다"(PROJECT Core Value, policies §4.1)를
 * 실제 PostgreSQL(Testcontainers)에서 증명하는 통합테스트. 등록·가감(성공/거부)을 거친 뒤 매번
 * `pass.remainingCount`가 `PassTransaction` 이력 합계(`sumAmountByPassId`)와 같음을 확인한다.
 *
 * `MockMvc`가 아니라 `AdminPassService`를 직접 주입해 호출한다 — 이 테스트가 증명하려는 것은
 * HTTP 계약이 아니라 서비스 트랜잭션이 남기는 데이터의 정합성이다. 애노테이션 조합은
 * `MemberSearchTest`와 동일하게 맞춘다(conventions §10.1 — 컨텍스트 캐시 재사용).
 *
 * `isEqualTo` 대신 `isEqualByComparingTo`를 쓴다 — `3.5 != 3.50`(D-016).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class PassLedgerInvariantTest {
    @Autowired
    private lateinit var adminPassService: AdminPassService

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

    private var fixtureCounter = 30000L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `초기 3dot0으로 등록한 횟수권의 잔여는 이력 합계와 같다`() {
        val member = persistMember()
        val admin = persistAdmin()

        val response =
            adminPassService.register(
                member.id!!,
                RegisterPassRequest(type = PassType.SESSION_PASS, initialCount = BigDecimal("3.0")),
                admin.id!!,
            )

        assertLedgerInvariant(response.passId, BigDecimal("3.0"))
    }

    @Test
    fun `-0dot5 +1dot0을 연속 가감한 뒤에도 잔여가 이력 합계와 같다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val passId =
            adminPassService
                .register(
                    member.id!!,
                    RegisterPassRequest(type = PassType.SESSION_PASS, initialCount = BigDecimal("3.0")),
                    admin.id!!,
                ).passId

        adminPassService.adjust(passId, AdjustPassRequest(BigDecimal("-0.5"), "저녁반 참여 보정"), admin.id!!)
        adminPassService.adjust(passId, AdjustPassRequest(BigDecimal("1.0"), "이벤트 보상"), admin.id!!)

        assertLedgerInvariant(passId, BigDecimal("3.5"))
    }

    @Test
    fun `잔여를 정확히 0으로 만드는 가감 뒤에도 잔여가 이력 합계와 같다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val passId =
            adminPassService
                .register(
                    member.id!!,
                    RegisterPassRequest(type = PassType.SESSION_PASS, initialCount = BigDecimal("1.0")),
                    admin.id!!,
                ).passId

        adminPassService.adjust(passId, AdjustPassRequest(BigDecimal("-1.0"), "전량 소진 처리"), admin.id!!)

        assertLedgerInvariant(passId, BigDecimal.ZERO)
    }

    @Test
    fun `거부된 가감 시도 뒤에도 잔여와 이력 건수가 시도 전과 동일하다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val passId =
            adminPassService
                .register(
                    member.id!!,
                    RegisterPassRequest(type = PassType.SESSION_PASS, initialCount = BigDecimal("2.0")),
                    admin.id!!,
                ).passId
        val remainingBefore = passRepository.findById(passId).get().remainingCount
        val countBefore = countTransactions(passId)

        // 음수 결과가 되는 가감 (잔여 2.0인데 -5.0)
        assertThatThrownBy {
            adminPassService.adjust(passId, AdjustPassRequest(BigDecimal("-5.0"), "과다 차감 시도"), admin.id!!)
        }.isInstanceOf(InsufficientPassCountException::class.java)

        // 0.5 배수 위반
        assertThatThrownBy {
            adminPassService.adjust(passId, AdjustPassRequest(BigDecimal("0.3"), "단위 위반 시도"), admin.id!!)
        }.isInstanceOf(InvalidAdjustmentUnitException::class.java)

        val reloaded = passRepository.findById(passId).get()
        assertThat(reloaded.remainingCount).isEqualByComparingTo(remainingBefore)
        assertThat(countTransactions(passId)).isEqualTo(countBefore)
        assertLedgerInvariant(passId, remainingBefore!!)
    }

    @Test
    fun `기간제 이용권은 잔여가 null이고 PassTransaction 이력이 0건이다`() {
        val member = persistMember()
        val admin = persistAdmin()

        val response =
            adminPassService.register(
                member.id!!,
                RegisterPassRequest(type = PassType.EVENING_MEMBERSHIP, term = EveningMembershipTerm.ONE_MONTH),
                admin.id!!,
            )

        val pass = passRepository.findById(response.passId).get()
        assertThat(pass.remainingCount).isNull()
        assertThat(countTransactions(response.passId)).isEqualTo(0)
    }

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

    private fun countTransactions(passId: Long): Int = passTransactionRepository.findAll().count { it.pass.id == passId }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "원장회원$fixtureCounter",
                phoneNumber = "0108765$fixtureCounter",
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
                loginId = "admin-pass-ledger-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
