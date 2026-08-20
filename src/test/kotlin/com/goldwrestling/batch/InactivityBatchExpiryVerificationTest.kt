package com.goldwrestling.batch

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.pass.AdminPassService
import com.goldwrestling.pass.InsufficientPassCountException
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassDisplayStatus
import com.goldwrestling.pass.PassFixtures
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
import com.goldwrestling.pass.dto.ChangePassPeriodRequest
import com.goldwrestling.reservation.MemberReservationService
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.reservation.dto.ReserveRequest
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassType
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.time.ZoneOffset

/**
 * 이 파일은 BATCH-03의 **유일한 산출물**이다 — D-107로 `EXPIRED` 영속화 배치를 만들지 않기로
 * 확정했고(진실 원천 이원화 방지), 대신 기존 메커니즘이 실제로 만료 이용권의 사용을 막는지를
 * 여기서 실증한다(05-07-PLAN.md Task 2).
 *
 * 세 축을 각각 단언한다 — ① 표시 상태 계산(D-064·D-066) ② 예약 거부(Phase 4 경로, 유효기간
 * 판정 기준일은 예약일이 아니라 수업날이라는 D-091) ③ 미사용 차감 대상에서 제외(BATCH-02와 연결).
 * 네 번째 테스트는 "상태를 저장하지 않는" 이 설계의 이점 — 유효기간을 미래로 수정하면 되돌리기
 * 로직 없이 자연히 되살아난다(D-056)는 것을 실증한다.
 *
 * **이 태스크는 프로덕션 코드를 한 줄도 바꾸지 않는다.** 여기 테스트가 실패하면 그것은 테스트
 * 문제가 아니라 D-107의 전제("기존 메커니즘으로 이미 충족")가 깨졌다는 뜻이다.
 *
 * 클래스에 트랜잭션 애노테이션을 붙이지 않는다 — `reserve`·`changePeriod`·배치 러너가 각자
 * 여는 트랜잭션이 실제로 커밋돼야 "요청 전과 동일하다"를 이 테스트가 실제로 관측할 수 있다
 * (`MemberReservationServiceTest`·`InactivityBatchRunnerTest` 선례). 대신 `@AfterEach`에서
 * 이 테스트가 만든 행을 직접 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class InactivityBatchExpiryVerificationTest {
    @Autowired
    private lateinit var memberReservationService: MemberReservationService

    @Autowired
    private lateinit var adminPassService: AdminPassService

    @Autowired
    private lateinit var inactivityBatchRunner: InactivityBatchRunner

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
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var batchExecutionRepository: BatchExecutionRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L
    private val createdBatchExecutionIds = mutableListOf<Long>()

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(OffsetDateTime.parse(BASE_CLOCK).toInstant())
    }

    @AfterEach
    fun cleanUp() {
        if (createdBatchExecutionIds.isNotEmpty()) {
            batchExecutionRepository.deleteAllById(createdBatchExecutionIds)
            createdBatchExecutionIds.clear()
        }
        jdbcClient
            .sql("delete from reservation where class_date between :from and :to")
            .param("from", RANGE_FROM)
            .param("to", RANGE_TO)
            .update()
        jdbcClient
            .sql(
                "delete from pass_transaction where pass_id in " +
                    "(select id from pass where member_id in (select id from member where kakao_id >= :base))",
            ).param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql(
                "delete from pass_period_change where pass_id in " +
                    "(select id from pass where member_id in (select id from member where kakao_id >= :base))",
            ).param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from pass where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from class_session where class_date between :from and :to")
            .param("from", RANGE_FROM)
            .param("to", RANGE_TO)
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

    // ── ① 표시 상태(D-064·D-066) ─────────────────────────────────────────

    @Test
    fun `종료일이 어제면 잔여가 남아 있어도 만료 상태로 표시된다`() {
        val pass = transientSessionPass(endDate = TODAY.minusDays(1), remaining = "3.0")

        assertThat(pass.displayStatus(TODAY)).isEqualTo(PassDisplayStatus.EXPIRED)
    }

    @Test
    fun `종료일이 오늘이면 잔여가 남아 있을 때 사용 가능 상태로 표시된다`() {
        val pass = transientSessionPass(endDate = TODAY, remaining = "3.0")

        assertThat(pass.displayStatus(TODAY)).isEqualTo(PassDisplayStatus.USABLE)
    }

    // ── ② 예약 거부(Phase 4 경로, D-091 수업날 기준) ─────────────────────

    @Test
    fun `만료된 SESSION_PASS만 가진 회원은 예약이 거부되고 잔여-예약-이력이 요청 전과 같다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "2.0", endDate = TODAY.minusDays(1), createdAt = TODAY.minusDays(60).atTime9am())
        val schedule = scheduleOf(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))

        assertThatThrownBy {
            memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, THURSDAY_THIS_WEEK))
        }.isInstanceOf(InsufficientPassCountException::class.java)

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(reservationRepository.existsByPassIdAndStatus(pass.id!!, ReservationStatus.ACTIVE)).isFalse()
        assertThat(passTransactionCountOf(pass.id!!)).isZero()
    }

    @Test
    fun `오늘은 유효하지만 수업날에는 만료되는 이용권으로는 그 수업을 예약할 수 없다`() {
        val member = persistMember()
        val pass =
            persistSessionPass(member, remaining = "2.0", endDate = WEDNESDAY_THIS_WEEK, createdAt = TODAY.minusDays(60).atTime9am())
        val schedule = scheduleOf(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))
        // 유효기간 우회 차단(D-091)의 전제 — 오늘(TODAY) 기준으로는 아직 유효해야 한다.
        assertThat(pass.displayStatus(TODAY)).isEqualTo(PassDisplayStatus.USABLE)

        assertThatThrownBy {
            memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, THURSDAY_THIS_WEEK))
        }.isInstanceOf(InsufficientPassCountException::class.java)

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(reservationRepository.existsByPassIdAndStatus(pass.id!!, ReservationStatus.ACTIVE)).isFalse()
    }

    // ── ③ 미사용 차감 대상 제외(BATCH-02와 연결) ─────────────────────────

    @Test
    fun `만료된 SESSION_PASS만 가진 회원은 기준일이 오래돼도 배치 대상에서 제외된다`() {
        val member = persistMember()
        val pass =
            persistSessionPass(member, remaining = "2.0", endDate = TODAY.minusDays(1), createdAt = TODAY.minusDays(200).atTime9am())

        assertThat(passRepository.findMemberIdsWithDeductibleSessionPass(TODAY)).doesNotContain(member.id)

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        createdBatchExecutionIds += result.id!!

        assertThat(result.processedMemberCount).isZero()
        assertThat(result.deductedCount).isZero()
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(passTransactionCountOf(pass.id!!)).isZero()
    }

    // ── ④ 기간 수정으로 되살아남(D-056) ───────────────────────────────────

    @Test
    fun `만료된 이용권도 유효기간을 미래로 수정하면 사용 가능으로 돌아오고 배치 대상에 다시 포함된다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val pass =
            persistSessionPass(member, remaining = "2.0", endDate = TODAY.minusDays(1), createdAt = TODAY.minusDays(200).atTime9am())
        assertThat(passRepository.findById(pass.id!!).get().displayStatus(TODAY)).isEqualTo(PassDisplayStatus.EXPIRED)
        assertThat(passRepository.findMemberIdsWithDeductibleSessionPass(TODAY)).doesNotContain(member.id)

        // 기존 기간 수정 경로(AdminPassService)를 그대로 호출한다 — 새 코드를 만들지 않는다.
        adminPassService.changePeriod(
            pass.id!!,
            ChangePassPeriodRequest(newStartDate = null, newEndDate = TODAY.plusDays(30), reason = "유효기간 연장 — 되살아남 검증"),
            admin.id!!,
        )

        val revived = passRepository.findById(pass.id!!).get()
        assertThat(revived.displayStatus(TODAY)).isEqualTo(PassDisplayStatus.USABLE)
        assertThat(passRepository.findMemberIdsWithDeductibleSessionPass(TODAY)).contains(member.id)
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun LocalDate.atTime9am(): OffsetDateTime = OffsetDateTime.of(this, LocalTime.of(9, 0), ZoneOffset.of("+09:00"))

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun scheduleOf(
        day: DayOfWeek,
        type: ClassType,
        time: LocalTime,
    ): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == day && it.classType == type && it.startTime == time }

    private fun remainingOf(passId: Long): BigDecimal = passRepository.findById(passId).get().remainingCount!!

    private fun passTransactionCountOf(passId: Long): Int = passTransactionRepository.findAll().count { it.pass.id == passId }

    /** DB에 저장하지 않는 `Pass` — `displayStatus`는 순수 엔티티 메서드라 영속화가 필요 없다. */
    private fun transientSessionPass(
        endDate: LocalDate,
        remaining: String,
    ): Pass =
        Pass(
            member = PassFixtures.member(),
            branch = PassFixtures.branch(),
            registeredBy = PassFixtures.admin(),
            type = PassType.SESSION_PASS,
            status = PassStatus.ACTIVE,
            startDate = endDate.minusYears(1).plusDays(1),
            endDate = endDate,
            remainingCount = BigDecimal(remaining),
            createdAt = PassFixtures.FIXED_TIME,
        )

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

    private fun persistSessionPass(
        member: Member,
        remaining: String,
        endDate: LocalDate,
        createdAt: OffsetDateTime,
    ): Pass =
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

    private companion object {
        const val KAKAO_ID_BASE = 9_720_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-inactivity-expiry-"
        const val BASE_CLOCK = "2095-06-07T08:00:00+09:00"
        val TODAY: LocalDate = LocalDate.of(2095, 6, 7)
        val WEDNESDAY_THIS_WEEK: LocalDate = LocalDate.of(2095, 6, 8)
        val THURSDAY_THIS_WEEK: LocalDate = LocalDate.of(2095, 6, 9)
        val RANGE_FROM: LocalDate = LocalDate.of(2095, 6, 1)
        val RANGE_TO: LocalDate = LocalDate.of(2095, 6, 21)
    }
}
