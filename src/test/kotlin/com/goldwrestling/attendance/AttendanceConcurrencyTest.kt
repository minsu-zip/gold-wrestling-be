package com.goldwrestling.attendance

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.attendance.dto.AddEveningAttendanceRequest
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassType
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 저녁반 출석 추가의 이중 차감 0건을 실제 PostgreSQL·실제 동시 트랜잭션으로 실증하는 동시성 테스트
 * (ATTEND-02, T-06-26, policies §8). `ReservationCapacityConcurrencyTest`(시나리오 C: 같은 회원의
 * 같은 타임 중복 요청) 골격을 그대로 이식한다.
 *
 * **이 클래스·메서드에는 `@Transactional`을 붙이지 않는다** — 각 스레드가 별도 트랜잭션이어야
 * `attendance` 회원×세션 유니크 위반과 `SESSION_PASS` 조건부 차감 경쟁이 실제로 재현된다
 * (add-domain-test §4). 트랜잭션 롤백에 기대지 않으므로 [cleanUp]에서 이 테스트가 만든 행을 FK
 * 역순으로 직접 지운다.
 *
 * 세션 행이 없는 상태에서 시작한다(D-094) — `getOrCreate`의 get-or-create 경쟁까지 함께 재현된다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class AttendanceConcurrencyTest {
    @Autowired
    private lateinit var attendanceService: AttendanceService

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var passTransactionRepository: PassTransactionRepository

    @Autowired
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var classSessionRepository: ClassSessionRepository

    @Autowired
    private lateinit var attendanceRepository: AttendanceRepository

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @AfterEach
    fun cleanUp() {
        // FK 역순: attendance → pass_transaction → class_session → pass → member → admin.
        jdbcClient
            .sql("delete from attendance where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql(
                "delete from pass_transaction where pass_id in " +
                    "(select id from pass where member_id in (select id from member where kakao_id >= :base))",
            ).param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from class_session where class_date = :date")
            .param("date", CLASS_DATE)
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
    fun `같은 회원이 같은 저녁반 수업에 동시에 10건을 요청하면 출석 1건, 이력 1건, 차감 0-5회만 반영된다`() {
        val schedule = songpaEveningSchedule()
        val admin = persistAdmin()
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "5.0", endDate = CLASS_DATE.plusYears(1))
        val threadCount = 10

        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    attendanceService.addEveningAttendance(
                        admin.id!!,
                        AddEveningAttendanceRequest(schedule.id!!, CLASS_DATE, member.id!!),
                    )
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

        // 성공 건수만 보지 않는다 — 이력 누락은 조용한 버그다(add-domain-test §4).
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failures).hasSize(9)
        assertThat(failures).allMatch { it is DuplicateAttendanceException }

        val session = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, CLASS_DATE)!!
        val attendances = attendanceRepository.findAllByClassSessionIdWithMember(session.id!!)
        assertThat(attendances).hasSize(1)

        val ledgerEntries = passTransactionRepository.findAll().filter { it.pass.id == pass.id }
        assertThat(ledgerEntries).hasSize(1)
        assertThat(ledgerEntries.first().reason).isEqualTo(TransactionReason.EVENING_HALF)
        assertThat(ledgerEntries.first().amount).isEqualByComparingTo(BigDecimal("-0.5"))

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("4.5"))
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun songpaEveningSchedule(): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == DayOfWeek.MONDAY && it.classType == ClassType.EVENING && it.startTime == LocalTime.of(19, 0) }

    private fun persistMember(): Member = memberRepository.saveAndFlush(AttendanceFixtures.member(songpaBranch(), kakaoId = KAKAO_ID_BASE))

    private fun persistAdmin(): Admin = adminRepository.saveAndFlush(AttendanceFixtures.admin(loginId = "${ADMIN_LOGIN_PREFIX}1"))

    private fun persistSessionPass(
        member: Member,
        remaining: String,
        endDate: LocalDate,
    ): Pass =
        passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = persistExtraAdmin(),
                type = PassType.SESSION_PASS,
                status = PassStatus.ACTIVE,
                startDate = endDate.minusYears(1).plusDays(1),
                endDate = endDate,
                remainingCount = BigDecimal(remaining),
                createdAt = AttendanceFixtures.FIXED_TIME,
            ),
        )

    private fun persistExtraAdmin(): Admin = adminRepository.saveAndFlush(AttendanceFixtures.admin(loginId = "${ADMIN_LOGIN_PREFIX}2"))

    private companion object {
        const val KAKAO_ID_BASE = 9_860_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-attend-race-"
        val CLASS_DATE: LocalDate = LocalDate.of(2035, 3, 5) // 월요일
    }
}
