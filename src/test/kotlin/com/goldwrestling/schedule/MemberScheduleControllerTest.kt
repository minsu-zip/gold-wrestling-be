package com.goldwrestling.schedule

import com.goldwrestling.SEOUL_ZONE_ID
import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.auth.PrincipalType
import com.goldwrestling.auth.TokenService
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
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * `GET /api/members/me/schedule`의 그리드 계산·예약 창 판정·회원 노출 범위를 검증한다(SCHED-01·SCHED-02,
 * T-04-17~T-04-21). 애노테이션 조합은 `MemberPassControllerTest`와 동일하게 맞춘다(conventions §10.1).
 *
 * **토큰 발급은 반드시 실제 시각(`Instant.now()`)일 때 한다** — `JwtDecoder`는 이 프로젝트의 `Clock`
 * 빈이 아니라 실제 시스템 시각으로 만료를 검증한다(`JwtAuthenticationFilterTest`가 이미 확인한 동작).
 * 그래서 [tokenAtThisWeekMonday]는 토큰을 먼저 발급한 뒤에야 비즈니스 시계를 "이번 주 월요일 08:00"으로
 * 옮긴다 — 발급된 토큰의 유효기간은 실제 시스템 시각 기준으로 그대로 유지된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class MemberScheduleControllerTest {
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

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private var fixtureCounter = 50000L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `weekStart를 생략하면 이번 주 월~일 7일 그리드가 200으로 내려온다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)

        val body =
            mockMvc
                .perform(get("/api/members/me/schedule").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val root = objectMapper.readTree(body)
        assertThat(root.get("weekStart").asText()).isEqualTo(monday.toString())
        assertThat(root.get("weekEnd").asText()).isEqualTo(monday.plusDays(6).toString())
        assertThat(root.get("bookableWeek").asBoolean()).isTrue()
        assertThat(root.get("days").toList()).hasSize(7)
    }

    @Test
    fun `다음 주 weekStart로 조회하면 200이고 모든 셀이 bookable false다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        val nextWeekMonday = monday.plusWeeks(1)

        val body =
            mockMvc
                .perform(
                    get("/api/members/me/schedule")
                        .param("weekStart", nextWeekMonday.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                ).andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val root = objectMapper.readTree(body)
        assertThat(root.get("bookableWeek").asBoolean()).isFalse()
        val allCells = root.get("days").toList().flatMap { it.get("cells").toList() }
        assertThat(allCells).isNotEmpty()
        assertThat(allCells).allSatisfy { cell -> assertThat(cell.get("bookable").asBoolean()).isFalse() }
    }

    @Test
    fun `weekStart가 월요일이 아니면 409와 RESERVATION_WINDOW_CLOSED를 반환한다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)

        mockMvc
            .perform(
                get("/api/members/me/schedule")
                    .param("weekStart", monday.plusDays(1).toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("RESERVATION_WINDOW_CLOSED"))
    }

    @Test
    fun `weekStart가 조회 범위(2주) 밖이면 409와 RESERVATION_WINDOW_CLOSED를 반환한다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)

        mockMvc
            .perform(
                get("/api/members/me/schedule")
                    .param("weekStart", monday.plusWeeks(2).toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("RESERVATION_WINDOW_CLOSED"))
    }

    @Test
    fun `예약이 없는 예약제 셀은 classSessionId가 null이고 0-정원 capacity·bookable true로 내려온다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        // 시드 시간표에서 항상 존재하는 화요일 SESSION 11:00 — 아직 아무도 예약하지 않았다.
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)

        val body = getSchedule(token)
        val cell = cellAt(body, classDate, ClassType.SESSION, LocalTime.of(11, 0))

        assertThat(cell.get("classSessionId").isNull).isTrue()
        assertThat(cell.get("reservedCount").asInt()).isEqualTo(0)
        assertThat(cell.get("capacity").asInt()).isEqualTo(10)
        assertThat(cell.get("bookable").asBoolean()).isTrue()
    }

    @Test
    fun `저녁반 셀은 예약 대상이 아니고 항상 bookable false다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        // 월요일(=이번 주 08:00) EVENING 19:00 — 아직 시작 전이라도 저녁반은 예약 불가.
        val classDate = monday

        val body = getSchedule(token)
        val cell = cellAt(body, classDate, ClassType.EVENING, LocalTime.of(19, 0))

        assertThat(cell.get("classType").asText()).isEqualTo("EVENING")
        assertThat(cell.get("capacity").isNull).isTrue()
        assertThat(cell.get("reservable").asBoolean()).isFalse()
        assertThat(cell.get("bookable").asBoolean()).isFalse()
    }

    @Test
    fun `휴강 처리된 세션이 있는 셀은 suspended true이고 bookable false다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val (token, monday) = tokenAtThisWeekMonday(member)
        // 수요일 LESSON 19:00을 휴강 세션으로 미리 만들어 둔다.
        val classDate = monday.plusDays(DayOfWeek.WEDNESDAY.value - 1L)
        val schedule = findSchedule(DayOfWeek.WEDNESDAY, ClassType.LESSON, LocalTime.of(19, 0))
        persistSuspendedSession(schedule, classDate, admin)

        val body = getSchedule(token)
        val cell = cellAt(body, classDate, ClassType.LESSON, LocalTime.of(19, 0))

        assertThat(cell.get("suspended").asBoolean()).isTrue()
        assertThat(cell.get("bookable").asBoolean()).isFalse()
    }

    @Test
    fun `본인이 예약한 셀에는 myReservationId가 채워지고 타인 예약은 채워지지 않는다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val otherMember = persistMember(MemberStatus.ACTIVE, name = "타인예약회원")
        val admin = persistAdmin()
        val (token, monday) = tokenAtThisWeekMonday(member)
        // 목요일 SESSION 11:00 — 정원 10명, 본인·타인이 각각 한 자리씩 예약한다.
        val classDate = monday.plusDays(DayOfWeek.THURSDAY.value - 1L)
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val session = persistSession(schedule, classDate, reservedCount = 2)

        val myPass = persistPass(member, admin, PassType.SESSION_PASS)
        val myReservation = persistReservation(member, session, myPass, schedule, classDate)
        val otherPass = persistPass(otherMember, admin, PassType.SESSION_PASS)
        persistReservation(otherMember, session, otherPass, schedule, classDate)

        val body = getSchedule(token)
        val cell = cellAt(body, classDate, ClassType.SESSION, LocalTime.of(11, 0))

        assertThat(cell.get("myReservationId").asLong()).isEqualTo(myReservation.id)
        assertThat(cell.get("reservedCount").asInt()).isEqualTo(2)
    }

    @Test
    fun `응답 어디에도 회원 이름·전화번호가 없고 다른 회원 이름이 새어나가지 않는다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val otherMember = persistMember(MemberStatus.ACTIVE, name = "이름노출금지회원")
        val admin = persistAdmin()
        val (token, monday) = tokenAtThisWeekMonday(member)
        val classDate = monday.plusDays(DayOfWeek.THURSDAY.value - 1L)
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val session = persistSession(schedule, classDate, reservedCount = 1)
        val otherPass = persistPass(otherMember, admin, PassType.SESSION_PASS)
        persistReservation(otherMember, session, otherPass, schedule, classDate)

        val body = getSchedule(token)

        assertThat(body).doesNotContain("이름노출금지회원")
        assertThat(body).doesNotContain(otherMember.phoneNumber)
        assertThat(body).doesNotContain("memberName")
        assertThat(body).doesNotContain("phoneNumber")
        assertThat(body).doesNotContain("reservations")
    }

    @Test
    fun `PENDING 회원 요청은 403과 MEMBER_NOT_ACTIVE를 반환한다`() {
        val member = persistMember(MemberStatus.PENDING)
        val (token, _) = tokenAtThisWeekMonday(member)

        mockMvc
            .perform(get("/api/members/me/schedule").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_ACTIVE"))
    }

    private fun getSchedule(token: String): String =
        mockMvc
            .perform(get("/api/members/me/schedule").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andReturn()
            .response.contentAsString

    private fun cellAt(
        body: String,
        date: LocalDate,
        classType: ClassType,
        startTime: LocalTime,
    ): JsonNode {
        val root = objectMapper.readTree(body)
        val day = root.get("days").toList().first { LocalDate.parse(it.get("date").asText()) == date }
        return day
            .get("cells")
            .toList()
            .first {
                it.get("classType").asText() == classType.name &&
                    LocalTime.parse(it.get("startTime").asText()) == startTime
            }
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

    /**
     * 토큰을 실제 시각(`Instant.now()`) 기준으로 먼저 발급한 뒤, 비즈니스 시계를 "이번 주 월요일
     * 08:00 KST"로 옮긴다. 반환된 [LocalDate]는 그 월요일 날짜다 — 월요일은 이 시드 시간표에서
     * 가장 이른 타임(EVENING 19:00)조차 아직 시작 전이라, 이 주 전체가 "아직 시작 안 됨"으로
     * 취급된다.
     */
    private fun tokenAtThisWeekMonday(member: Member): Pair<String, LocalDate> {
        val token = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken
        val monday = WeekRange.of(LocalDate.now(clock)).monday
        (clock as MutableTestClock).setTo(
            ZonedDateTime.of(monday, LocalTime.of(8, 0), ZoneId.of(SEOUL_ZONE_ID)).toInstant(),
        )
        return token to monday
    }

    private fun persistMember(
        status: MemberStatus,
        name: String = "시간표조회회원",
    ): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "$name$fixtureCounter",
                phoneNumber = "0107654$fixtureCounter",
                status = status,
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
                loginId = "admin-member-schedule-$fixtureCounter",
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
        admin: Admin,
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
                cancelReason = "테스트 휴강",
                canceledByAdmin = admin,
                createdAt = OffsetDateTime.now(clock),
            ),
        )

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

    private fun persistReservation(
        member: Member,
        session: ClassSession,
        pass: Pass,
        schedule: ClassSchedule,
        classDate: LocalDate,
    ): Reservation =
        reservationRepository.saveAndFlush(
            Reservation(
                member = member,
                classSession = session,
                pass = pass,
                classType = schedule.classType,
                classDate = classDate,
                startTime = schedule.startTime,
                status = ReservationStatus.ACTIVE,
                reservedAt = OffsetDateTime.now(clock),
                createdAt = OffsetDateTime.now(clock),
            ),
        )
}
