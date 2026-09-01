package com.goldwrestling.reservation

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * [ReservationLedgerSupport]의 두 복구 경로가 "판정 이후 이용권이 CANCELED로 바뀐" 같은 상황에서
 * 서로 다르게 반응하는지 고정한다(D-145, 이슈 #12/WR-02).
 *
 * - 단일 취소 경로([ReservationLedgerSupport.restorePassAfterCancellation]): 호출부가 넘긴 스냅샷
 *   상태가 이미 낡았으면 `IllegalStateException`으로 요청을 실패시킨다 — 원래 동작 유지.
 * - 휴강 캐스케이드 경로([ReservationLedgerSupport.restorePassAfterSuspension]): 재조회 시점엔
 *   ACTIVE였어도 조건부 UPDATE가 0행이면 예외 없이 건너뛴다. 이 경로는 `ClassSessionSuspensionTest`의
 *   경합 테스트로는 닿지 않는다(거기서는 등록취소가 **먼저 커밋**되어 재조회가 CANCELED를 읽고 조기
 *   반환한다). 여기서는 등록취소 트랜잭션이 이용권 행을 **잠근 채 커밋을 미룬 상태**에서 복구를 별도
 *   스레드로 시작시켜, `findById`는 ACTIVE를 읽고 UPDATE는 잠금 대기에 걸리게 만든다 — 그 대기가
 *   `pg_stat_activity`로 관측된 뒤에야 등록취소를 커밋하므로, UPDATE가 깨어나 `WHERE`를 재평가해
 *   0행을 반환하는 순서가 항상 같다(READ COMMITTED의 재평가 동작 그 자체를 재현한다).
 *
 * 애노테이션 조합은 `PassCancellationConcurrencyTest`와 동일하게 유지해 스프링 컨텍스트 캐시를
 * 공유한다(conventions §10.1). 클래스 레벨 `@Transactional`은 붙이지 않는다 —
 * [ReservationLedgerSupport]는 자체 트랜잭션 경계를 열지 않는 헬퍼라 호출부의 `@Transactional`에
 * 편승하는데, 이 테스트는 서비스 계층을 거치지 않고 직접 호출하므로 `TransactionTemplate`으로
 * 명시적 트랜잭션을 열어야 한다(`ClassSessionSuspensionTest`의 `createReservation` 직접 호출
 * 선례와 동일 — 안 그러면 `TransactionRequiredException`이 난다). 데이터 정리는 `@AfterEach`에서
 * `JdbcClient`로 직접 한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class ReservationLedgerRestoreTest {
    @Autowired
    private lateinit var reservationLedgerSupport: ReservationLedgerSupport

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var passTransactionRepository: PassTransactionRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private var memberId: Long? = null
    private var adminId: Long? = null

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @AfterEach
    fun cleanUp() {
        jdbcClient
            .sql("delete from pass_transaction where pass_id in (select id from pass where member_id = :memberId)")
            .param("memberId", memberId)
            .update()
        jdbcClient.sql("delete from pass where member_id = :memberId").param("memberId", memberId).update()
        jdbcClient.sql("delete from member where id = :memberId").param("memberId", memberId).update()
        jdbcClient.sql("delete from admin where id = :adminId").param("adminId", adminId).update()
    }

    @Test
    fun `단일 취소 복구는 이용권이 등록취소돼 있으면 IllegalStateException을 던진다`() {
        val member = persistMember()
        memberId = member.id
        val admin = persistAdmin()
        adminId = admin.id
        val pass = persistPass(member, admin, remaining = "1.0")
        // 이미 CANCELED로 실제 취소 경로(cancelIfNotCanceled)를 거친 이용권 — 호출부가 들고 있는
        // passStatus = ACTIVE는 상태 전환 이전에 뜬 stale 스냅샷 상황을 재현한다. @Modifying
        // 쿼리는 명시적 트랜잭션 없이 호출하면 TransactionRequiredException이 나므로 감싼다.
        TransactionTemplate(transactionManager).executeWithoutResult {
            passRepository.cancelIfNotCanceled(pass.id!!, "테스트 등록 취소", admin, OffsetDateTime.now(clock))
        }

        assertThatThrownBy {
            TransactionTemplate(transactionManager).executeWithoutResult {
                reservationLedgerSupport.restorePassAfterCancellation(
                    passId = pass.id!!,
                    passStatus = PassStatus.ACTIVE,
                    refundRequested = true,
                    canceledAt = OffsetDateTime.now(clock),
                    member = null,
                    admin = admin,
                )
            }
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `휴강 복구는 재조회 시점엔 ACTIVE였어도 UPDATE가 0행이면 예외 없이 건너뛰고 잔여-이력을 남기지 않는다`() {
        val member = persistMember()
        memberId = member.id
        val admin = persistAdmin()
        adminId = admin.id
        val pass = persistPass(member, admin, remaining = "1.0")
        val passId = pass.id!!

        val executor = Executors.newSingleThreadExecutor()
        try {
            // T2(메인 스레드): 등록취소 트랜잭션을 열어 이용권 행을 잠근다. 이 블록을 빠져나갈 때 커밋된다.
            val restoreFuture =
                TransactionTemplate(transactionManager).execute {
                    passRepository.cancelIfNotCanceled(passId, "휴강과 동시 발생한 등록취소", admin, OffsetDateTime.now(clock))

                    // T1(별도 스레드): T2가 아직 커밋 전이라 findById는 ACTIVE를 읽고 shouldRestore=true로
                    // 진행하다가, adjustRemainingCount UPDATE에서 T2가 쥔 행 잠금에 걸려 대기한다.
                    val future =
                        executor.submit {
                            TransactionTemplate(transactionManager).executeWithoutResult {
                                reservationLedgerSupport.restorePassAfterSuspension(
                                    passId = passId,
                                    reservationId = 0L,
                                    canceledAt = OffsetDateTime.now(clock),
                                    admin = admin,
                                )
                            }
                        }
                    awaitLockWaitOnUpdate("pass")
                    future
                }!!

            // T2 커밋 직후 T1의 UPDATE가 깨어나 갱신된 행(CANCELED)으로 WHERE를 재평가 → 0행 → 스킵.
            assertThatCode { restoreFuture.get(10, TimeUnit.SECONDS) }.doesNotThrowAnyException()
        } finally {
            executor.shutdownNow()
        }

        val refreshed = passRepository.findById(passId).get()
        assertThat(refreshed.status).isEqualTo(PassStatus.CANCELED)
        assertThat(refreshed.remainingCount).isEqualByComparingTo(BigDecimal("1.0"))
        assertThat(passTransactionRepository.findAll().filter { it.pass.id == passId }).isEmpty()
    }

    /**
     * 다른 세션의 `update <table> ...` 문이 행 잠금 대기(`wait_event_type = 'Lock'`)에 들어갈 때까지
     * 폴링한다. Testcontainers가 만든 DB 사용자는 자기 세션들을 `pg_stat_activity`에서 볼 수 있다.
     *
     * 이 폴링은 T2 트랜잭션 **안에서** 돈다. PostgreSQL은 한 트랜잭션에서 `pg_stat_activity`를 처음
     * 읽은 결과를 그 트랜잭션이 끝날 때까지 캐시하므로(문서: "the same information will be displayed
     * throughout the transaction"), 매 폴링 전에 `pg_stat_clear_snapshot()`으로 캐시를 버려야 T1이
     * 뒤늦게 대기에 들어간 것을 볼 수 있다 — 안 그러면 첫 폴링의 0이 10초 내내 반복된다.
     */
    private fun awaitLockWaitOnUpdate(tableName: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            jdbcClient.sql("select pg_stat_clear_snapshot()").query().listOfRows()
            val waiting =
                jdbcClient
                    .sql("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query ilike :pattern")
                    .param("pattern", "%update $tableName %")
                    .query(Long::class.javaObjectType)
                    .single()
            if (waiting > 0) return
            Thread.sleep(20)
        }
        throw AssertionError("복구 UPDATE가 10초 안에 잠금 대기에 들어가지 않았다 — 경합 재현 전제가 깨졌다.")
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(): Member =
        memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "원장복구회원",
                phoneNumber = "01099980000",
                status = MemberStatus.ACTIVE,
                kakaoId = KAKAO_ID,
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun persistAdmin(): Admin =
        adminRepository.saveAndFlush(
            Admin(
                name = "관리자",
                loginId = "admin-ledger-restore-test",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun persistPass(
        member: Member,
        admin: Admin,
        remaining: String,
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
                remainingCount = BigDecimal(remaining),
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private companion object {
        const val KAKAO_ID = 91_000_002L
    }
}
