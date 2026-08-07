package com.goldwrestling.reservation

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.auth.PrincipalType
import com.goldwrestling.auth.TokenService
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassType
import com.goldwrestling.reservation.dto.ReservationSearchCondition
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassSessionStatus
import com.goldwrestling.schedule.ClassType
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 관리자 전체 예약 조회(RESV-07, 04-12)의 검색 규칙(Task 1)을 검증한다. HTTP 계약(Task 2)은
 * 이 파일에 `@Nested`로 추가된다. 애노테이션 조합은 `AdminScheduleServiceTest`·
 * `AdminScheduleControllerTest`와 맞춘다(conventions §10.1 — 컨텍스트 캐시 재사용).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class AdminReservationSearchTest {
    @Autowired
    private lateinit var adminReservationService: AdminReservationService

    @Autowired
    private lateinit var mockMvc: MockMvc

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
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var clock: Clock

    private var fixtureCounter = 90000L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    // ---------- Task 1: 서비스 레벨 검색 규칙 ----------

    @Test
    fun `필터 없이 조회하면 전체 예약이 reservedAt 내림차순으로 페이지 단위 반환된다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val member1 = persistMember("먼저예약")
        val older = persistReservation(member1, admin, schedule, classDate)
        (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        val member2 = persistMember("나중예약")
        val newer = persistReservation(member2, admin, schedule, classDate)

        val result = adminReservationService.search(ReservationSearchCondition())

        assertThat(result.content).hasSizeGreaterThanOrEqualTo(2)
        val ids = result.content.map { it.id }
        assertThat(ids.indexOf(newer.id)).isLessThan(ids.indexOf(older.id))
    }

    @Test
    fun `from-to를 지정하면 classDate가 그 범위 안인 예약만 조회된다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val inRangeDate = nextClassDate(DayOfWeek.TUESDAY)
        val outOfRangeDate = inRangeDate.plusWeeks(4)
        val memberInRange = persistMember("범위안회원")
        val inRange = persistReservation(memberInRange, admin, schedule, inRangeDate)
        val memberOutOfRange = persistMember("범위밖회원")
        persistReservation(memberOutOfRange, admin, schedule, outOfRangeDate)

        val result =
            adminReservationService.search(
                ReservationSearchCondition(from = inRangeDate, to = inRangeDate),
            )

        assertThat(result.content.map { it.id }).contains(inRange.id)
        assertThat(result.content).allMatch { it.classDate == inRangeDate }
    }

    @Test
    fun `classType=LESSON을 지정하면 1대1 예약만 조회된다`() {
        val admin = persistAdmin()
        val sessionSchedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val lessonSchedule = findSchedule(DayOfWeek.TUESDAY, ClassType.LESSON, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val sessionMember = persistMember("예약제회원")
        persistReservation(sessionMember, admin, sessionSchedule, classDate, PassType.SESSION_PASS)
        val lessonMember = persistMember("레슨회원")
        val lessonReservation = persistReservation(lessonMember, admin, lessonSchedule, classDate, PassType.LESSON_PASS)

        val result = adminReservationService.search(ReservationSearchCondition(classType = ClassType.LESSON))

        assertThat(result.content).allMatch { it.classType == ClassType.LESSON }
        assertThat(result.content.map { it.id }).contains(lessonReservation.id)
    }

    @Test
    fun `keyword로 회원명 일부를 주면 해당 회원 예약만 조회된다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val target = persistMember("검색용홍길동")
        val targetReservation = persistReservation(target, admin, schedule, classDate)
        val other = persistMember("다른회원")
        persistReservation(other, admin, schedule, classDate)

        val result = adminReservationService.search(ReservationSearchCondition(keyword = "홍길"))

        assertThat(result.content.map { it.id }).containsExactly(targetReservation.id)
    }

    @Test
    fun `keyword가 퍼센트 기호이면 전체 조회로 확장되지 않는다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val member = persistMember("와일드카드검증회원")
        persistReservation(member, admin, schedule, classDate)
        val totalWithoutFilter = adminReservationService.search(ReservationSearchCondition()).totalElements

        val result = adminReservationService.search(ReservationSearchCondition(keyword = "%"))

        assertThat(result.totalElements).isLessThan(totalWithoutFilter)
    }

    @Test
    fun `keyword 앞뒤 공백은 정규화되어 매칭된다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val member = persistMember("공백검증회원")
        val reservation = persistReservation(member, admin, schedule, classDate)

        val result = adminReservationService.search(ReservationSearchCondition(keyword = "  공백검증  "))

        assertThat(result.content.map { it.id }).contains(reservation.id)
    }

    @Test
    fun `status 필터를 생략하면 취소된 예약도 함께 반환된다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val member = persistMember("취소포함검증회원")
        val canceled = persistReservation(member, admin, schedule, classDate, canceledByAdmin = admin)

        val result = adminReservationService.search(ReservationSearchCondition(keyword = "취소포함검증"))

        assertThat(result.content.map { it.id }).contains(canceled.id)
        assertThat(result.content.first { it.id == canceled.id }.status).isEqualTo(ReservationStatus.CANCELED)
    }

    @Test
    fun `취소된 예약의 응답은 status refunded canceledAt canceledByType canceledByName을 포함한다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val member = persistMember("취소메타검증회원")
        val canceled = persistReservation(member, admin, schedule, classDate, canceledByAdmin = admin)

        val result = adminReservationService.search(ReservationSearchCondition(keyword = "취소메타검증"))

        val response = result.content.first { it.id == canceled.id }
        assertThat(response.status).isEqualTo(ReservationStatus.CANCELED)
        assertThat(response.refunded).isTrue()
        assertThat(response.canceledAt).isNotNull()
        assertThat(response.canceledByType).isEqualTo(PrincipalType.ADMIN)
        assertThat(response.canceledByName).isEqualTo(admin.name)
    }

    @Test
    fun `page=1 size=2 요청 시 두 번째 페이지 2건이 오고 totalElements는 전체 건수다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val keyword = "페이지검증회원"
        repeat(5) { index ->
            val member = persistMember("$keyword$index")
            persistReservation(member, admin, schedule, classDate)
            (clock as MutableTestClock).advance(Duration.ofMinutes(1))
        }

        val result = adminReservationService.search(ReservationSearchCondition(keyword = keyword, page = 1, size = 2))

        assertThat(result.content).hasSize(2)
        assertThat(result.totalElements).isEqualTo(5)
    }

    @Test
    fun `from이 to보다 뒤이면 InvalidReservationSearchRangeException이 발생한다`() {
        val today = LocalDate.now(clock)

        assertThatThrownBy {
            adminReservationService.search(ReservationSearchCondition(from = today, to = today.minusDays(1)))
        }.isInstanceOf(InvalidReservationSearchRangeException::class.java)
    }

    // ---------- 픽스처 ----------

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun findSchedule(
        dayOfWeek: DayOfWeek,
        classType: ClassType,
        startTime: LocalTime,
    ): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == dayOfWeek && it.classType == classType && it.startTime == startTime }

    /** 다음 [dayOfWeek]가 오는 날짜(2주 뒤부터 탐색 — 오늘·이번 주와 겹치지 않는 안전한 미래 날짜). */
    private fun nextClassDate(dayOfWeek: DayOfWeek): LocalDate {
        var date = LocalDate.now(clock).plusWeeks(2)
        while (date.dayOfWeek != dayOfWeek) {
            date = date.plusDays(1)
        }
        return date
    }

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
                name = "관리자$fixtureCounter",
                loginId = "admin-reservation-search-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun adminAccessToken(): String {
        val admin = persistAdmin()
        return tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
    }

    private fun persistPass(
        member: Member,
        admin: Admin,
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

    /**
     * 같은 (schedule, classDate) 조합으로 여러 예약을 만드는 테스트가 많아 `uq_class_session`
     * 유니크 제약(class_schedule_id, class_date)에 걸리지 않도록 이미 있으면 재사용한다.
     */
    private fun persistSession(
        schedule: ClassSchedule,
        classDate: LocalDate,
    ): ClassSession =
        classSessionRepository.findByClassScheduleIdAndClassDate(requireNotNull(schedule.id), classDate)
            ?: classSessionRepository.saveAndFlush(
                ClassSession(
                    classSchedule = schedule,
                    classDate = classDate,
                    classType = schedule.classType,
                    startTime = schedule.startTime,
                    endTime = schedule.endTime,
                    capacity = schedule.capacity,
                    reservedCount = 0,
                    status = ClassSessionStatus.SCHEDULED,
                    createdAt = OffsetDateTime.now(clock),
                ),
            )

    private fun persistReservation(
        member: Member,
        admin: Admin,
        schedule: ClassSchedule,
        classDate: LocalDate,
        passType: PassType = PassType.SESSION_PASS,
        canceledByAdmin: Admin? = null,
    ): Reservation {
        val pass = persistPass(member, admin, passType)
        val session = persistSession(schedule, classDate)
        val canceled = canceledByAdmin != null
        return reservationRepository.saveAndFlush(
            Reservation(
                member = member,
                classSession = session,
                pass = pass,
                classType = schedule.classType,
                classDate = classDate,
                startTime = schedule.startTime,
                status = if (canceled) ReservationStatus.CANCELED else ReservationStatus.ACTIVE,
                reservedAt = OffsetDateTime.now(clock),
                canceledAt = if (canceled) OffsetDateTime.now(clock) else null,
                canceledByAdmin = canceledByAdmin,
                refunded = if (canceled) true else null,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
