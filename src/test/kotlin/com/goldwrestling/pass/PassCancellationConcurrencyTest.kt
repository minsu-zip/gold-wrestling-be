package com.goldwrestling.pass

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.dto.CancelPassRequest
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
 * T-03-38(03-REVIEW.md WR-03) 회귀 방어 — 두 관리자가 같은 이용권을 동시에 취소하면 정확히
 * 한쪽만 성공하고(`PassRepository.cancelIfNotCanceled` 조건부 UPDATE, D-021·D-072), 취소
 * 메타데이터가 승자 것만 남으며, 상쇄 `PassTransaction` 이력이 중복 생성되지 않는지 검증한다.
 *
 * 애노테이션 조합은 `AdminPassControllerTest`와 동일하게 유지해 스프링 컨텍스트 캐시를
 * 공유한다(conventions §10.1). **이 클래스·메서드에는 `@Transactional`을 붙이지 않는다** — 각
 * 스레드가 별도 트랜잭션이어야 조건부 UPDATE 경쟁이 실제로 재현된다(add-domain-test §2·§4).
 * 트랜잭션 롤백에 기대지 않으므로 [cleanUp]에서 이 테스트가 만든 데이터를 직접 지운다
 * (`RefreshTokenRotationConcurrencyTest`와 동일 관례).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class PassCancellationConcurrencyTest {
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
        // 외래키 순서상 pass_transaction → pass → member/admin 순으로 지운다.
        jdbcClient
            .sql("delete from pass_transaction where pass_id in (select id from pass where member_id = :memberId)")
            .param("memberId", memberId)
            .update()
        jdbcClient.sql("delete from pass where member_id = :memberId").param("memberId", memberId).update()
        jdbcClient.sql("delete from member where id = :memberId").param("memberId", memberId).update()
        adminIds.forEach { id -> jdbcClient.sql("delete from admin where id = :id").param("id", id).update() }
    }

    @Test
    fun `같은 이용권을 두 관리자가 동시에 취소하면 한 건만 성공하고 상쇄 이력이 중복 생성되지 않는다`() {
        val member = persistMember()
        memberId = member.id
        val admin1 = persistAdmin()
        val admin2 = persistAdmin()
        adminIds.addAll(listOf(admin1.id!!, admin2.id!!))
        val pass = persistPass(member, admin1, remaining = BigDecimal("3.0"))

        val requests =
            listOf(
                admin1 to "관리자1 취소",
                admin2 to "관리자2 취소",
            )
        val executor = Executors.newFixedThreadPool(requests.size)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(requests.size)
        val successCount = AtomicInteger(0)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        requests.forEach { (admin, reason) ->
            executor.submit {
                try {
                    startLatch.await()
                    adminPassService.cancel(pass.id!!, CancelPassRequest(reason), admin.id!!)
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

        // 정확히 한 건만 성공하고, 패자는 PassAlreadyCanceledException(409)을 받는다.
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failures).hasSize(1)
        assertThat(failures[0]).isInstanceOf(PassAlreadyCanceledException::class.java)

        val reloaded = passRepository.findById(pass.id!!).get()
        assertThat(reloaded.status).isEqualTo(PassStatus.CANCELED)
        assertThat(reloaded.remainingCount).isEqualByComparingTo(BigDecimal.ZERO)
        // 취소 메타데이터는 승자 것만 남는다 — 두 관리자·사유 중 정확히 하나만 남고 섞이지 않는다.
        assertThat(reloaded.canceledBy?.id).isIn(admin1.id, admin2.id)
        assertThat(reloaded.cancelReason).isIn("관리자1 취소", "관리자2 취소")

        // 상쇄 이력은 승자 몫 1건만 남는다 — 패자가 이미 취소된 이용권에 대해 이력을 남기지 않는다.
        val transactions = passTransactionRepository.findAll().filter { it.pass.id == pass.id }
        assertThat(transactions).hasSize(1)
        assertThat(transactions[0].reason).isEqualTo(TransactionReason.REGISTRATION_CANCELED)
        assertThat(transactions[0].amount).isEqualByComparingTo(BigDecimal("-3.0"))
        assertThat(transactions[0].note).isEqualTo(reloaded.cancelReason)
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(): Member =
        memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "취소경쟁회원",
                phoneNumber = "01099990000",
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
                loginId = "admin-pass-cancel-race-${adminCounter++}",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun persistPass(
        member: Member,
        admin: Admin,
        remaining: BigDecimal,
    ): Pass =
        passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = PassType.SESSION_PASS,
                status = PassStatus.ACTIVE,
                startDate = LocalDate.now(clock),
                endDate = LocalDate.now(clock).plusYears(1).minusDays(1),
                remainingCount = remaining,
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private companion object {
        const val KAKAO_ID = 91_000_001L
    }
}
