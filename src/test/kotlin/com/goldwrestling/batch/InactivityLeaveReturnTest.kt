package com.goldwrestling.batch

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.AdminMemberService
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
 * **차감 제외 상태(`ON_LEAVE`·`INACTIVE`, `MemberStatus.DEDUCTION_EXCLUDED`)에서 벗어나는 경로**에서
 * 제외 기간이 소급 차감되지 않는다는 것을 실제 배치 실행으로 실증한다
 * (CR-04, WR-06, BATCH-02, D-111·D-147 정정). 우회 복귀(`ON_LEAVE`→`INACTIVE`→`ACTIVE`)와
 * 단순 복귀(`INACTIVE`→`ACTIVE`)를 모두 덮는다.
 *
 * `AdminMemberService.changeStatus`가 이 수정 전에는 `ON_LEAVE`→`ACTIVE` **직행**에서만
 * `deductionExclusionExitedAt`을 기록했다 — 그래서 우회 경로는 기준일이 휴회 시작 이전(등록일 등)으로
 * 되돌아가 휴회 기간 전체가 소급 차감됐다(6개월 휴회 복귀 시 최대 12회). 이 테스트는 상태 전이를
 * `AdminMemberService.changeStatus`로 실제로 일으켜 그 회귀를 방어한다 — 엔티티에 직접
 * `deductionExclusionExitedAt`을 대입하면 이번 수정의 회귀 방어가 되지 않는다.
 *
 * 애노테이션 조합은 `InactivityBatchIdempotencyTest`와 동일하게
 * `@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)`로 맞춰 컨텍스트
 * 캐시를 공유한다(conventions §10.1). 클래스 레벨 `@Transactional`을 붙이지 않는다 —
 * `deductOnce`가 실제로 커밋돼야 배치 결과를 관찰할 수 있다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class InactivityLeaveReturnTest {
    @Autowired
    private lateinit var inactivityBatchRunner: InactivityBatchRunner

    @Autowired
    private lateinit var adminMemberService: AdminMemberService

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
    private val createdBatchExecutionIds = mutableListOf<Long>()

    @AfterEach
    fun cleanUp() {
        if (createdBatchExecutionIds.isNotEmpty()) {
            batchExecutionRepository.deleteAllById(createdBatchExecutionIds)
            createdBatchExecutionIds.clear()
        }
        jdbcClient
            .sql(
                "delete from pass_transaction where pass_id in " +
                    "(select id from pass where member_id in " +
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
    fun `ON_LEAVE에서 INACTIVE를 거쳐 ACTIVE로 우회 복귀한 회원은 복귀 13일 뒤 배치에서 소급 차감되지 않는다`() {
        val registeredAt = OffsetDateTime.parse("2025-06-01T09:00:00+09:00")
        val leaveExitAt = registeredAt.plusDays(200)
        val admin = persistAdmin()

        val leaveReturnMember = persistMember()
        val leaveReturnPass =
            persistSessionPass(
                member = leaveReturnMember,
                remaining = "5.0",
                endDate = registeredAt.toLocalDate().plusDays(400),
                createdAt = registeredAt,
            )
        setClock(registeredAt)
        adminMemberService.changeStatus(leaveReturnMember.id!!, MemberStatus.ON_LEAVE)
        setClock(leaveExitAt)
        adminMemberService.changeStatus(leaveReturnMember.id!!, MemberStatus.INACTIVE)
        setClock(leaveExitAt.plusDays(1))
        adminMemberService.changeStatus(leaveReturnMember.id!!, MemberStatus.ACTIVE)

        // 대조군 — 휴회를 거치지 않고 같은 기간(200일) 방치된 회원. 같은 실행에서 차감이
        // 발생함을 보여, 기준일 리셋이 "휴회 이탈"에서만 일어난다는 것을 확인한다.
        val idleMember = persistMember()
        val idlePass =
            persistSessionPass(
                member = idleMember,
                remaining = "5.0",
                endDate = registeredAt.toLocalDate().plusDays(400),
                createdAt = registeredAt,
            )

        setClock(leaveExitAt.plusDays(13))
        runOnce(BatchTrigger.MANUAL, admin.id)

        assertThat(remainingOf(leaveReturnPass.id!!)).isEqualByComparingTo(BigDecimal("5.0"))
        assertThat(remainingOf(idlePass.id!!)).isLessThan(BigDecimal("5.0"))
        assertLedgerInvariant(leaveReturnPass.id!!)
        assertLedgerInvariant(idlePass.id!!)
    }

    @Test
    fun `우회 복귀 후 15일째 배치를 돌리면 휴회 이전 200일이 소급되지 않고 정확히 1회만 차감된다`() {
        val registeredAt = OffsetDateTime.parse("2025-06-01T09:00:00+09:00")
        val leaveExitAt = registeredAt.plusDays(200)
        val admin = persistAdmin()

        val member = persistMember()
        val pass =
            persistSessionPass(
                member = member,
                remaining = "5.0",
                endDate = registeredAt.toLocalDate().plusDays(400),
                createdAt = registeredAt,
            )
        setClock(registeredAt)
        adminMemberService.changeStatus(member.id!!, MemberStatus.ON_LEAVE)
        setClock(leaveExitAt)
        adminMemberService.changeStatus(member.id!!, MemberStatus.INACTIVE)
        setClock(leaveExitAt.plusDays(1))
        adminMemberService.changeStatus(member.id!!, MemberStatus.ACTIVE)

        setClock(leaveExitAt.plusDays(15))
        val result = runOnce(BatchTrigger.MANUAL, admin.id)

        assertThat(result.deductedCount).isEqualTo(1)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("4.0"))
        assertLedgerInvariant(pass.id!!)
    }

    @Test
    fun `휴회를 거치지 않고 200일 방치된 회원은 같은 실행에서 차감이 발생한다`() {
        val registeredAt = OffsetDateTime.parse("2025-06-01T09:00:00+09:00")
        val admin = persistAdmin()

        val idleMember = persistMember()
        val idlePass =
            persistSessionPass(
                member = idleMember,
                remaining = "5.0",
                endDate = registeredAt.toLocalDate().plusDays(400),
                createdAt = registeredAt,
            )

        setClock(registeredAt.plusDays(200))
        val result = runOnce(BatchTrigger.MANUAL, admin.id)

        assertThat(result.deductedCount).isGreaterThan(0)
        assertThat(remainingOf(idlePass.id!!)).isLessThan(BigDecimal("5.0"))
        assertLedgerInvariant(idlePass.id!!)
    }

    /**
     * WR-06 / D-147 — `INACTIVE`(탈퇴·장기 미이용)도 차감 예외 상태다(policies §4.3).
     *
     * 종전에는 대상 조회 필터가 `ON_LEAVE`만 제외해, 탈퇴한 회원의 `SESSION_PASS` 잔여가 2주마다
     * 계속 깎여 결국 0이 됐다 — 나중에 환불을 요구하면 "시스템이 다 썼다고 한다"는 분쟁이 된다.
     */
    @Test
    fun `INACTIVE 회원은 200일 방치돼도 차감 대상에서 제외된다`() {
        val registeredAt = OffsetDateTime.parse("2025-06-01T09:00:00+09:00")
        val admin = persistAdmin()

        val inactiveMember = persistMember()
        val inactivePass =
            persistSessionPass(
                member = inactiveMember,
                remaining = "5.0",
                endDate = registeredAt.toLocalDate().plusDays(400),
                createdAt = registeredAt,
            )
        setClock(registeredAt)
        adminMemberService.changeStatus(inactiveMember.id!!, MemberStatus.INACTIVE)

        // 대조군 — 같은 기간 방치됐지만 ACTIVE인 회원. 같은 실행에서 차감이 발생함을 보여
        // "배치가 아예 안 돌았다"가 아니라 "이 회원만 제외됐다"를 확인한다.
        val activeMember = persistMember()
        val activePass =
            persistSessionPass(
                member = activeMember,
                remaining = "5.0",
                endDate = registeredAt.toLocalDate().plusDays(400),
                createdAt = registeredAt,
            )

        setClock(registeredAt.plusDays(200))
        runOnce(BatchTrigger.MANUAL, admin.id)

        assertThat(remainingOf(inactivePass.id!!)).isEqualByComparingTo(BigDecimal("5.0"))
        assertThat(remainingOf(activePass.id!!)).isLessThan(BigDecimal("5.0"))
        assertLedgerInvariant(inactivePass.id!!)
        assertLedgerInvariant(activePass.id!!)
    }

    /**
     * WR-06 / D-147 — `INACTIVE`에서 복귀하면 **전환일이 기준일 후보 ③**이 되어 2주 유예가 새로
     * 시작된다(D-105 규칙 재사용). 이것이 없으면 복귀 즉시 비활성 기간 전체가 부족분으로 계산돼
     * 관리자가 되살린 회원의 잔여가 그 자리에서 몰수된다.
     */
    @Test
    fun `INACTIVE에서 복귀한 회원은 복귀 13일 뒤 배치에서 소급 차감되지 않는다`() {
        val registeredAt = OffsetDateTime.parse("2025-06-01T09:00:00+09:00")
        val reactivatedAt = registeredAt.plusDays(200)
        val admin = persistAdmin()

        val member = persistMember()
        val pass =
            persistSessionPass(
                member = member,
                remaining = "5.0",
                endDate = registeredAt.toLocalDate().plusDays(400),
                createdAt = registeredAt,
            )
        setClock(registeredAt)
        adminMemberService.changeStatus(member.id!!, MemberStatus.INACTIVE)
        setClock(reactivatedAt)
        adminMemberService.changeStatus(member.id!!, MemberStatus.ACTIVE)

        setClock(reactivatedAt.plusDays(13))
        runOnce(BatchTrigger.MANUAL, admin.id)

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("5.0"))
        assertLedgerInvariant(pass.id!!)
    }

    /** 위 테스트의 짝 — 복귀 15일째에는 새 유예가 만료돼 **정확히 1회만** 차감된다(캐치업 아님). */
    @Test
    fun `INACTIVE에서 복귀한 회원은 복귀 15일째에 정확히 1회만 차감된다`() {
        val registeredAt = OffsetDateTime.parse("2025-06-01T09:00:00+09:00")
        val reactivatedAt = registeredAt.plusDays(200)
        val admin = persistAdmin()

        val member = persistMember()
        val pass =
            persistSessionPass(
                member = member,
                remaining = "5.0",
                endDate = registeredAt.toLocalDate().plusDays(400),
                createdAt = registeredAt,
            )
        setClock(registeredAt)
        adminMemberService.changeStatus(member.id!!, MemberStatus.INACTIVE)
        setClock(reactivatedAt)
        adminMemberService.changeStatus(member.id!!, MemberStatus.ACTIVE)

        setClock(reactivatedAt.plusDays(15))
        val result = runOnce(BatchTrigger.MANUAL, admin.id)

        assertThat(result.deductedCount).isEqualTo(1)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("4.0"))
        assertLedgerInvariant(pass.id!!)
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun setClock(at: OffsetDateTime) {
        (clock as MutableTestClock).setTo(at.toInstant())
    }

    private fun runOnce(
        trigger: BatchTrigger,
        triggeredByAdminId: Long?,
    ): BatchExecution {
        val result = inactivityBatchRunner.run(trigger, triggeredByAdminId)
        createdBatchExecutionIds += result.id!!
        return result
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun remainingOf(passId: Long): BigDecimal = passRepository.findById(passId).get().remainingCount!!

    /** 잔여 == `PassTransaction` 이력 합계(Core Value) — 05-07-PLAN.md 선례의 필수 종료 단언. */
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

    companion object {
        const val KAKAO_ID_BASE = 9_730_000_000L
        const val KAKAO_ID_MAX = KAKAO_ID_BASE + 10_000L
        const val ADMIN_LOGIN_PREFIX = "admin-inactivity-leave-return-"
    }
}
