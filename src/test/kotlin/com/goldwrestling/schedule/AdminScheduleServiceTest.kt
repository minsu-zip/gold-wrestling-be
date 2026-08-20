package com.goldwrestling.schedule

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.common.time.WeekRange
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassType
import com.goldwrestling.reservation.Reservation
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.dto.AdminBoardCellResponse
import com.goldwrestling.schedule.dto.AdminWeeklyBoardResponse
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * `AdminScheduleService.getWeeklyBoard`의 그리드 조립 규칙(빈 셀·명단·휴강·주 정규화)을 직접
 * 검증한다(SCHED-03, 04-11-PLAN Task 1). HTTP 레이어 없이 서비스만 호출해 조립 규칙과 인가·직렬화
 * 관심사를 분리한다 — 컨트롤러 계약은 `AdminScheduleControllerTest`(Task 2)가 검증한다.
 * 애노테이션 조합은 `MemberScheduleControllerTest`와 맞춘다(conventions §10.1 — 컨텍스트 캐시 재사용).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class AdminScheduleServiceTest {
    @Autowired
    private lateinit var adminScheduleService: AdminScheduleService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var classSessionRepository: ClassSessionRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var clock: Clock

    private var fixtureCounter = 70000L
    private lateinit var admin: Admin

    /**
     * `MutableTestClock`은 싱글턴 빈이라 이전 테스트가 밀어 둔 시각이 이어진다 — 매 테스트 시작 시
     * 실제 현재 시각으로 되돌린다(`AdminPassControllerTest`와 동일 관례). 이 테스트는 HTTP·JWT를
     * 거치지 않으므로 토큰 발급 시각 제약이 없다 — 고정된 시각을 기준으로 "지난 주"·"다음 다음 주"
     * 같은 상대 날짜를 계산해도 실행 시점에 따라 결과가 흔들리지 않는다.
     */
    @BeforeEach
    fun setUp() {
        (clock as MutableTestClock).setTo(Instant.now())
        admin = persistAdmin()
    }

    @Test
    fun `예약이 없는 예약제 셀은 classSessionId가 null이고 reservedCount 0 reservations 빈 배열이다`() {
        val monday = thisWeekMonday()
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)

        val response = adminScheduleService.getWeeklyBoard(songpaBranch().id!!, monday)
        val cell = cellAt(response, classDate, ClassType.SESSION, LocalTime.of(11, 0))

        assertThat(cell.classSessionId).isNull()
        assertThat(cell.reservedCount).isEqualTo(0)
        assertThat(cell.reservations).isEmpty()
    }

    @Test
    fun `예약 3건이 있는 예약제 셀은 reservedCount 3 reservations 3건이고 memberName이 포함된다`() {
        val monday = thisWeekMonday()
        val classDate = monday.plusDays(DayOfWeek.THURSDAY.value - 1L)
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val session = persistSession(schedule, classDate, reservedCount = 3)
        val members = (1..3).map { persistMember(name = "예약회원$it") }
        members.forEach { member ->
            val pass = persistPass(member, PassType.SESSION_PASS)
            persistReservation(member, session, pass, schedule, classDate)
        }

        val response = adminScheduleService.getWeeklyBoard(songpaBranch().id!!, monday)
        val cell = cellAt(response, classDate, ClassType.SESSION, LocalTime.of(11, 0))

        assertThat(cell.reservedCount).isEqualTo(3)
        assertThat(cell.capacity).isEqualTo(10)
        assertThat(cell.reservations).hasSize(3)
        assertThat(cell.reservations.map { it.memberName })
            .containsExactlyInAnyOrderElementsOf(members.map { it.name })
    }

    @Test
    fun `취소된 예약은 명단과 reservedCount에 반영되지 않는다`() {
        val monday = thisWeekMonday()
        val classDate = monday.plusDays(DayOfWeek.THURSDAY.value - 1L)
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val session = persistSession(schedule, classDate, reservedCount = 1)
        val activeMember = persistMember(name = "활성예약회원")
        val activePass = persistPass(activeMember, PassType.SESSION_PASS)
        persistReservation(activeMember, session, activePass, schedule, classDate)
        val canceledMember = persistMember(name = "취소예약회원")
        val canceledPass = persistPass(canceledMember, PassType.SESSION_PASS)
        persistReservation(canceledMember, session, canceledPass, schedule, classDate, status = ReservationStatus.CANCELED)

        val response = adminScheduleService.getWeeklyBoard(songpaBranch().id!!, monday)
        val cell = cellAt(response, classDate, ClassType.SESSION, LocalTime.of(13, 0))

        assertThat(cell.reservedCount).isEqualTo(1)
        assertThat(cell.reservations).hasSize(1)
        assertThat(cell.reservations[0].memberName).isEqualTo(activeMember.name)
    }

    @Test
    fun `휴강 처리된 세션이 있는 셀은 suspended true이고 cancelReason이 채워진다`() {
        val monday = thisWeekMonday()
        val classDate = monday.plusDays(DayOfWeek.WEDNESDAY.value - 1L)
        val schedule = findSchedule(DayOfWeek.WEDNESDAY, ClassType.LESSON, LocalTime.of(19, 0))
        persistSuspendedSession(schedule, classDate, "명절 휴강")

        val response = adminScheduleService.getWeeklyBoard(songpaBranch().id!!, monday)
        val cell = cellAt(response, classDate, ClassType.LESSON, LocalTime.of(19, 0))

        assertThat(cell.suspended).isTrue()
        assertThat(cell.cancelReason).isEqualTo("명절 휴강")
    }

    @Test
    fun `저녁반 셀은 capacity가 null이고 reservable false이며 reservations가 빈 배열이다`() {
        val monday = thisWeekMonday()

        val response = adminScheduleService.getWeeklyBoard(songpaBranch().id!!, monday)
        val cell = cellAt(response, monday, ClassType.EVENING, LocalTime.of(19, 0))

        assertThat(cell.capacity).isNull()
        assertThat(cell.reservable).isFalse()
        assertThat(cell.reservations).isEmpty()
    }

    @Test
    fun `지난 주 weekStart로 조회해도 정상적으로 그리드가 내려온다(관리자는 범위 제한 없음)`() {
        val lastWeekMonday = thisWeekMonday().minusWeeks(1)

        val response = adminScheduleService.getWeeklyBoard(songpaBranch().id!!, lastWeekMonday)

        assertThat(response.weekStart).isEqualTo(lastWeekMonday)
        assertThat(response.days).hasSize(7)
    }

    @Test
    fun `다음 다음 주 weekStart로 조회해도 정상적으로 그리드가 내려온다`() {
        val twoWeeksAhead = thisWeekMonday().plusWeeks(2)

        val response = adminScheduleService.getWeeklyBoard(songpaBranch().id!!, twoWeeksAhead)

        assertThat(response.weekStart).isEqualTo(twoWeeksAhead)
        assertThat(response.days).hasSize(7)
    }

    @Test
    fun `weekStart가 수요일이면 응답 weekStart는 그 주 월요일로 정규화된다`() {
        val monday = thisWeekMonday()
        val wednesday = monday.plusDays(2)

        val response = adminScheduleService.getWeeklyBoard(songpaBranch().id!!, wednesday)

        assertThat(response.weekStart).isEqualTo(monday)
        assertThat(response.weekEnd).isEqualTo(monday.plusDays(6))
    }

    @Test
    fun `weekStart를 생략하면 오늘이 속한 주가 조회된다`() {
        val monday = thisWeekMonday()

        val response = adminScheduleService.getWeeklyBoard(songpaBranch().id!!, null)

        assertThat(response.weekStart).isEqualTo(monday)
    }

    private fun thisWeekMonday(): LocalDate = WeekRange.of(LocalDate.now(clock)).monday

    private fun cellAt(
        response: AdminWeeklyBoardResponse,
        date: LocalDate,
        classType: ClassType,
        startTime: LocalTime,
    ): AdminBoardCellResponse {
        val day = response.days.first { it.date == date }
        return day.cells.first { it.classType == classType && it.startTime == startTime }
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun findSchedule(
        dayOfWeek: DayOfWeek,
        classType: ClassType,
        startTime: LocalTime,
    ): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == dayOfWeek && it.classType == classType && it.startTime == startTime }

    private fun persistMember(name: String): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "$name$fixtureCounter",
                phoneNumber = "0107777$fixtureCounter",
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
                loginId = "admin-schedule-board-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistSession(
        schedule: ClassSchedule,
        classDate: LocalDate,
        reservedCount: Int,
    ): ClassSession =
        classSessionRepository.saveAndFlush(
            ClassSession(
                classSchedule = schedule,
                classDate = classDate,
                classType = schedule.classType,
                startTime = schedule.startTime,
                endTime = schedule.endTime,
                capacity = schedule.capacity,
                reservedCount = reservedCount,
                status = ClassSessionStatus.SCHEDULED,
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun persistSuspendedSession(
        schedule: ClassSchedule,
        classDate: LocalDate,
        reason: String,
    ): ClassSession =
        classSessionRepository.saveAndFlush(
            ClassSession(
                classSchedule = schedule,
                classDate = classDate,
                classType = schedule.classType,
                startTime = schedule.startTime,
                endTime = schedule.endTime,
                capacity = schedule.capacity,
                reservedCount = 0,
                status = ClassSessionStatus.CANCELED,
                canceledAt = OffsetDateTime.now(clock),
                cancelReason = reason,
                canceledByAdmin = admin,
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun persistPass(
        member: Member,
        type: PassType,
    ): Pass =
        passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = type,
                status = PassStatus.ACTIVE,
                startDate = LocalDate.now(clock),
                endDate = LocalDate.now(clock).plusYears(1).minusDays(1),
                remainingCount = BigDecimal("5.0"),
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    private fun persistReservation(
        member: Member,
        session: ClassSession,
        pass: Pass,
        schedule: ClassSchedule,
        classDate: LocalDate,
        status: ReservationStatus = ReservationStatus.ACTIVE,
    ): Reservation {
        val canceled = status == ReservationStatus.CANCELED
        return reservationRepository.saveAndFlush(
            Reservation(
                member = member,
                classSession = session,
                pass = pass,
                classType = schedule.classType,
                classDate = classDate,
                startTime = schedule.startTime,
                status = status,
                reservedAt = OffsetDateTime.now(clock),
                canceledAt = if (canceled) OffsetDateTime.now(clock) else null,
                canceledByMember = if (canceled) member else null,
                refunded = if (canceled) true else null,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
