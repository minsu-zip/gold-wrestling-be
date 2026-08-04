package com.goldwrestling.pass

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.dto.ChangePassPeriodRequest
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
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * WF-03-01(03-REVIEW.md WR-04) 회귀 방어 — 두 관리자가 같은 이용권의 기간을 동시에 수정하면
 * 정확히 한쪽만 성공하고(`PassRepository.changePeriodIfUnchanged` compare-and-swap 조건부
 * UPDATE, D-021·D-072), `PassPeriodChange` 이력이 1건만 남으며 그 전값이 실제 DB 전값과
 * 일치하는지(lost update가 없는지) 검증한다.
 *
 * 애노테이션 조합·트랜잭션 미부여 이유는 [PassCancellationConcurrencyTest]와 동일하다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class PassPeriodChangeConcurrencyTest {
    @Autowired
    private lateinit var adminPassService: AdminPassService

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var passPeriodChangeRepository: PassPeriodChangeRepository

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

    private var memberId: Long? = null
    private val adminIds = mutableListOf<Long>()

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @AfterEach
    fun cleanUp() {
        // 외래키 순서상 pass_period_change → pass → member/admin 순으로 지운다.
        jdbcClient
            .sql("delete from pass_period_change where pass_id in (select id from pass where member_id = :memberId)")
            .param("memberId", memberId)
            .update()
        jdbcClient.sql("delete from pass where member_id = :memberId").param("memberId", memberId).update()
        jdbcClient.sql("delete from member where id = :memberId").param("memberId", memberId).update()
        adminIds.forEach { id -> jdbcClient.sql("delete from admin where id = :id").param("id", id).update() }
    }

    @Test
    fun `같은 이용권의 기간을 두 관리자가 동시에 수정하면 한 건만 성공하고 이력의 전값이 실제 전값과 일치한다`() {
        val member = persistMember()
        memberId = member.id
        val admin1 = persistAdmin()
        val admin2 = persistAdmin()
        adminIds.addAll(listOf(admin1.id!!, admin2.id!!))
        val originalStart = LocalDate.now(clock)
        val originalEnd = originalStart.plusYears(1).minusDays(1)
        val pass = persistPass(member, admin1, startDate = originalStart, endDate = originalEnd)

        val requests =
            listOf(
                admin1 to ChangePassPeriodRequest(newStartDate = null, newEndDate = originalEnd.plusMonths(1), reason = "관리자1 연장"),
                admin2 to ChangePassPeriodRequest(newStartDate = null, newEndDate = originalEnd.plusMonths(2), reason = "관리자2 연장"),
            )
        val executor = Executors.newFixedThreadPool(requests.size)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(requests.size)
        val successCount = AtomicInteger(0)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        requests.forEach { (admin, request) ->
            executor.submit {
                try {
                    startLatch.await()
                    adminPassService.changePeriod(pass.id!!, request, admin.id!!)
                    successCount.incrementAndGet()
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

        // 정확히 한 건만 성공하고, 패자는 PassStateConflictException(409, 동시 수정 충돌)을 받는다.
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failures).hasSize(1)
        assertThat(failures[0]).isInstanceOf(PassStateConflictException::class.java)

        // PassPeriodChange 이력은 승자 몫 1건만 남고, 그 전값은 실제 등록 시점 전값과 일치한다
        // (lost update였다면 승자의 전값이 패자가 미처 반영하지 못한 값으로 어긋났을 것이다).
        val history = passPeriodChangeRepository.findAllByPassIdOrderByOccurredAtDesc(pass.id!!)
        assertThat(history).hasSize(1)
        assertThat(history[0].previousStartDate).isEqualTo(originalStart)
        assertThat(history[0].previousEndDate).isEqualTo(originalEnd)
        assertThat(history[0].newEndDate).isIn(originalEnd.plusMonths(1), originalEnd.plusMonths(2))

        val reloaded = passRepository.findById(pass.id!!).get()
        assertThat(reloaded.endDate).isEqualTo(history[0].newEndDate)
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(): Member =
        memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "기간수정경쟁회원",
                phoneNumber = "01099990001",
                status = MemberStatus.ACTIVE,
                kakaoId = KAKAO_ID,
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private var adminCounter = 0L

    private fun persistAdmin(): Admin =
        adminRepository.saveAndFlush(
            Admin(
                name = "관리자",
                loginId = "admin-pass-period-race-${adminCounter++}",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun persistPass(
        member: Member,
        admin: Admin,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Pass =
        passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = PassType.SESSION_PASS,
                status = PassStatus.ACTIVE,
                startDate = startDate,
                endDate = endDate,
                remainingCount = BigDecimal("3.0"),
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private companion object {
        const val KAKAO_ID = 92_000_001L
    }
}
