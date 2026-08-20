package com.goldwrestling.reservation

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.common.error.DomainException
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.reservation.dto.ReserveRequest
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassType
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * "초과 예약 0건"(Core Value)을 실제 PostgreSQL·실제 동시 트랜잭션으로 증명하는 동시성 테스트
 * (RESV-06, D-098, policies §8). 세 시나리오 모두 성공 건수·DB 활성 예약 행 수·`PassTransaction`
 * `RESERVE` 이력 건수·세션 `reservedCount`가 서로 일치함을 함께 단언한다(ROADMAP 성공기준 4 "잔여 =
 * 이력 합계" 원장 불변식).
 *
 * 애노테이션 조합은 `PassCancellationConcurrencyTest`·`ClassSessionConcurrencyTest`와 동일하게
 * 유지한다(conventions §10.1). **이 클래스·메서드에는 `@Transactional`을 붙이지 않는다** — 각
 * 스레드가 별도 트랜잭션이어야 정원·잔여 조건부 UPDATE 경쟁이 실제로 재현된다(add-domain-test
 * §2·§4). 트랜잭션 롤백에 기대지 않으므로 [cleanUp]에서 이 테스트가 만든 행을 FK 역순으로 직접
 * 지운다.
 *
 * 세 시나리오 모두 **세션 행이 없는 상태에서 시작한다**(D-094) — `getOrCreate`의 get-or-create 경쟁까지
 * 함께 재현된다. 시각은 `MutableTestClock`으로 미래의 한 주(2094-03-01 월요일)에 고정한다 — 고정하지
 * 않으면 테스트를 돌리는 요일에 따라 `ReservationWindowClosedException`으로 전부 실패한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class ReservationCapacityConcurrencyTest {
    @Autowired
    private lateinit var memberReservationService: MemberReservationService

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
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(OffsetDateTime.parse(BASE_CLOCK).toInstant())
    }

    @AfterEach
    fun cleanUp() {
        // FK 역순: notification → pass_transaction → reservation → class_session → pass → member → admin.
        jdbcClient
            .sql("delete from notification where class_date between :from and :to")
            .param("from", RANGE_FROM)
            .param("to", RANGE_TO)
            .update()
        jdbcClient
            .sql("delete from pass_transaction where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from reservation where class_date between :from and :to")
            .param("from", RANGE_FROM)
            .param("to", RANGE_TO)
            .update()
        jdbcClient
            .sql("delete from class_session where class_date between :from and :to")
            .param("from", RANGE_FROM)
            .param("to", RANGE_TO)
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

    // ---------- 시나리오 A: 예약제 정원 경쟁 ----------

    @Test
    fun `정원 10명 수업에 20명이 동시에 예약하면 정확히 10건만 성공하고 실패한 회원의 잔여는 그대로 1-0이다`() {
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = SCENARIO_A_DATE
        val threadCount = 20

        data class Attempt(
            val memberId: Long,
            val passId: Long,
            val error: Throwable?,
        )

        val members = (1..threadCount).map { persistMember() }
        val passes = members.associate { it.id!! to persistPass(it, PassType.SESSION_PASS, "1.0") }

        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val attempts = Collections.synchronizedList(mutableListOf<Attempt>())

        members.forEach { member ->
            executor.submit {
                try {
                    startLatch.await()
                    try {
                        memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, classDate))
                        attempts.add(Attempt(member.id!!, passes.getValue(member.id!!).id!!, null))
                    } catch (e: Throwable) {
                        attempts.add(Attempt(member.id!!, passes.getValue(member.id!!).id!!, e))
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()
        assertThat(completed).isTrue()

        val successes = attempts.filter { it.error == null }
        val failures = attempts.mapNotNull { it.error }
        assertThat(failures).hasSize(10)
        assertThat(failures).allMatch { it is ReservationCapacityExceededException }
        assertThat(failures).allMatch { it is DomainException }

        val session = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)!!
        val activeReservations = reservationRepository.findAllByClassSessionIdAndStatus(session.id!!, ReservationStatus.ACTIVE)
        val reserveTransactions =
            passTransactionRepository.findAll().filter {
                it.pass.id in passes.values.mapNotNull { p -> p.id } &&
                    it.reason == TransactionReason.RESERVE
            }

        // 4자 일치 — 성공 건수 = 활성 예약 행 수 = RESERVE 이력 건수 = 세션 reservedCount.
        assertThat(successes).hasSize(10)
        assertThat(activeReservations).hasSize(10)
        assertThat(reserveTransactions).hasSize(10)
        assertThat(session.reservedCount).isEqualTo(10)

        // 실패한 회원의 잔여는 차감 이전 그대로 1.0이다.
        val failedMemberIds = attempts.filter { it.error != null }.map { it.memberId }
        failedMemberIds.forEach { memberId ->
            val passId = passes.getValue(memberId).id!!
            val refreshed = passRepository.findById(passId).get()
            assertThat(refreshed.remainingCount).isEqualByComparingTo(BigDecimal("1.0"))
        }
        val succeededMemberIds = attempts.filter { it.error == null }.map { it.memberId }
        succeededMemberIds.forEach { memberId ->
            val passId = passes.getValue(memberId).id!!
            val refreshed = passRepository.findById(passId).get()
            assertThat(refreshed.remainingCount).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    // ---------- 시나리오 B: 1:1 슬롯 경쟁 ----------

    @Test
    fun `1대1 레슨 슬롯에 10명이 동시에 예약하면 정확히 1건만 성공한다`() {
        val schedule = scheduleOf(DayOfWeek.THURSDAY, ClassType.LESSON, LocalTime.of(11, 0))
        val classDate = SCENARIO_B_DATE
        val threadCount = 10

        val members = (1..threadCount).map { persistMember() }
        val passes = members.associate { it.id!! to persistPass(it, PassType.LESSON_PASS, "1.0") }

        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        members.forEach { member ->
            executor.submit {
                try {
                    startLatch.await()
                    memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, classDate))
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

        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failures).hasSize(9)
        assertThat(failures).allMatch { it is DomainException }

        val session = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)!!
        val activeReservations = reservationRepository.findAllByClassSessionIdAndStatus(session.id!!, ReservationStatus.ACTIVE)
        val reserveTransactions =
            passTransactionRepository.findAll().filter {
                it.pass.id in passes.values.mapNotNull { p -> p.id } &&
                    it.reason == TransactionReason.RESERVE
            }

        assertThat(activeReservations).hasSize(1)
        assertThat(reserveTransactions).hasSize(1)
        assertThat(session.reservedCount).isEqualTo(1)
    }

    // ---------- 시나리오 C: 같은 회원의 같은 타임 중복 요청 ----------

    @Test
    fun `같은 회원이 같은 타임에 동시에 10건을 요청하면 1건만 성공하고 잔여가 정확히 1회만 차감된다`() {
        val schedule = scheduleOf(DayOfWeek.FRIDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = SCENARIO_C_DATE
        val threadCount = 10

        val member = persistMember()
        val pass = persistPass(member, PassType.SESSION_PASS, "5.0")

        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, classDate))
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

        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failures).hasSize(9)
        // 실패는 DuplicateReservationException(부분 유니크 인덱스) 또는 ReservationCapacityExceededException/
        // InsufficientPassCountException(경쟁 순서에 따라 먼저 걸리는 조건부 UPDATE가 다를 수 있다) 중
        // 하나다 — 공통으로 요구되는 건 "새어나온 예외가 없다"는 것뿐이다(DataIntegrityViolationException·
        // LazyInitializationException은 실패로 본다).
        assertThat(failures).allMatch { it is DomainException }

        val session = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)!!
        val activeReservations = reservationRepository.findAllByClassSessionIdAndStatus(session.id!!, ReservationStatus.ACTIVE)
        val reserveTransactions =
            passTransactionRepository.findAll().filter {
                it.pass.id == pass.id &&
                    it.reason == TransactionReason.RESERVE
            }

        assertThat(activeReservations).hasSize(1)
        assertThat(reserveTransactions).hasSize(1)
        assertThat(session.reservedCount).isEqualTo(1)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("4.0"))
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun scheduleOf(
        day: DayOfWeek,
        type: ClassType,
        time: LocalTime,
    ): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == day && it.classType == type && it.startTime == time }

    private fun persistMember(): Member {
        val kakaoId = KAKAO_ID_BASE + fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "정원경쟁회원$kakaoId",
                phoneNumber = "010$kakaoId".take(20),
                status = MemberStatus.ACTIVE,
                kakaoId = kakaoId,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(
            Admin(
                name = "관리자",
                loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistPass(
        member: Member,
        type: PassType,
        remaining: String,
    ): Pass {
        val admin = persistAdmin()
        return passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = type,
                status = PassStatus.ACTIVE,
                startDate = LocalDate.of(2089, 1, 1),
                endDate = LocalDate.of(2099, 12, 31),
                remainingCount = BigDecimal(remaining),
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private companion object {
        const val KAKAO_ID_BASE = 9_400_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-resv-race-"
        const val BASE_CLOCK = "2094-03-01T08:00:00+09:00"
        val RANGE_FROM: LocalDate = LocalDate.of(2094, 3, 1)
        val RANGE_TO: LocalDate = LocalDate.of(2094, 3, 7)
        val SCENARIO_A_DATE: LocalDate = LocalDate.of(2094, 3, 2) // 화요일
        val SCENARIO_B_DATE: LocalDate = LocalDate.of(2094, 3, 4) // 목요일
        val SCENARIO_C_DATE: LocalDate = LocalDate.of(2094, 3, 5) // 금요일
    }
}
