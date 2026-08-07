package com.goldwrestling.reservation

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
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassSessionStatus
import com.goldwrestling.schedule.ClassType
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * `POST /api/members/me/reservations`의 성공·대표 실패(409 5종)·인가 계약을 검증하는 통합테스트
 * (RESV-01·02, 04-08 Task 1). 애노테이션 조합은 `MemberScheduleControllerTest`와 동일하게 맞춘다
 * (conventions §10.1 — 컨텍스트 캐시 재사용).
 *
 * 토큰 발급·시각 고정은 `MemberScheduleControllerTest.tokenAtThisWeekMonday`와 동일한 이유·순서를
 * 따른다 — 토큰은 실제 시각 기준으로 먼저 발급하고, 그 다음 비즈니스 시계를 "이번 주 월요일 08:00"으로
 * 옮긴다. 이 시각이면 이번 주 어떤 타임도 아직 시작 전이라 요일에 따라 테스트가 흔들리지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class MemberReservationControllerTest {
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
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var clock: Clock

    private var fixtureCounter = 60000L

    // ---------- 성공 ----------

    @Test
    fun `회원 토큰 + 정상 요청으로 예약하면 201과 ReservationResponse가 온다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        persistPass(member, PassType.SESSION_PASS, "3.0")
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)

        mockMvc
            .perform(
                post("/api/members/me/reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.classType").value("SESSION"))
            .andExpect(jsonPath("$.passId").exists())
    }

    // ---------- 형식 검증 ----------

    @Test
    fun `classScheduleId 누락이면 400과 VALIDATION_FAILED를 반환한다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)

        mockMvc
            .perform(
                post("/api/members/me/reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classDate":"$classDate"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    // ---------- 409 5종 ----------

    @Test
    fun `정원이 찬 수업을 예약하면 409와 RESERVATION_CAPACITY_EXCEEDED를 반환한다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        persistPass(member, PassType.SESSION_PASS, "3.0")
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)
        persistSession(schedule, classDate, reservedCount = 10)

        mockMvc
            .perform(
                post("/api/members/me/reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("RESERVATION_CAPACITY_EXCEEDED"))
    }

    @Test
    fun `잔여 부족한 이용권으로 예약하면 409와 INSUFFICIENT_PASS_COUNT를 반환한다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        persistPass(member, PassType.SESSION_PASS, "0.5")
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)

        mockMvc
            .perform(
                post("/api/members/me/reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_PASS_COUNT"))
    }

    @Test
    fun `같은 시간에 중복 예약하면 409와 DUPLICATE_RESERVATION을 반환한다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        persistPass(member, PassType.SESSION_PASS, "3.0")
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)
        val body = """{"classScheduleId":${schedule.id},"classDate":"$classDate"}"""

        mockMvc
            .perform(
                post("/api/members/me/reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                post("/api/members/me/reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("DUPLICATE_RESERVATION"))
    }

    @Test
    fun `다음 주 수업 예약은 409와 RESERVATION_WINDOW_CLOSED를 반환한다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        persistPass(member, PassType.SESSION_PASS, "3.0")
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val nextWeekClassDate = monday.plusWeeks(1).plusDays(DayOfWeek.TUESDAY.value - 1L)

        mockMvc
            .perform(
                post("/api/members/me/reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":${schedule.id},"classDate":"$nextWeekClassDate"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("RESERVATION_WINDOW_CLOSED"))
    }

    @Test
    fun `휴강된 수업에 대한 예약 시도는 409와 CLASS_SESSION_CANCELED를 반환한다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val admin = persistAdmin()
        val (token, monday) = tokenAtThisWeekMonday(member)
        persistPass(member, PassType.LESSON_PASS, "3.0")
        val schedule = findSchedule(DayOfWeek.WEDNESDAY, ClassType.LESSON, LocalTime.of(19, 0))
        val classDate = monday.plusDays(DayOfWeek.WEDNESDAY.value - 1L)
        persistSuspendedSession(schedule, classDate, admin)

        mockMvc
            .perform(
                post("/api/members/me/reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CLASS_SESSION_CANCELED"))
    }

    // ---------- 회원 상태 게이트 ----------

    @Test
    fun `PENDING 회원은 403과 MEMBER_NOT_ACTIVE를 반환한다`() {
        val member = persistMember(MemberStatus.PENDING)
        val (token, monday) = tokenAtThisWeekMonday(member)
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)

        mockMvc
            .perform(
                post("/api/members/me/reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate"}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_ACTIVE"))
    }

    // ---------- 인가 ----------

    @Test
    fun `토큰 없이 호출하면 401과 UNAUTHENTICATED를 반환한다`() {
        mockMvc
            .perform(
                post("/api/members/me/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":1,"classDate":"2099-01-06"}"""),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `관리자 토큰으로 호출하면 403과 ACCESS_DENIED를 반환한다`() {
        val token = adminAccessToken()

        mockMvc
            .perform(
                post("/api/members/me/reservations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":1,"classDate":"2099-01-06"}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
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
     * `MemberScheduleControllerTest.tokenAtThisWeekMonday`와 동일 관례 — 토큰은 실제 시각 기준으로
     * 먼저 발급하고, 그 다음 비즈니스 시계를 "이번 주 월요일 08:00 KST"로 옮긴다. 이 시각이면 이번 주
     * 어떤 타임(가장 이른 월요일 EVENING 19:00)도 아직 시작 전이라, 이번 주 전체가 예약 가능하다.
     */
    private fun tokenAtThisWeekMonday(member: Member): Pair<String, LocalDate> {
        val token = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken
        val monday = WeekRange.of(LocalDate.now(clock)).monday
        (clock as MutableTestClock).setTo(
            ZonedDateTime.of(monday, LocalTime.of(8, 0), ZoneId.of(SEOUL_ZONE_ID)).toInstant(),
        )
        return token to monday
    }

    private fun persistMember(status: MemberStatus): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "예약생성회원$fixtureCounter",
                phoneNumber = "0106543$fixtureCounter",
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
                loginId = "admin-member-reservation-$fixtureCounter",
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
                startDate = LocalDate.now(clock).minusMonths(1),
                endDate = LocalDate.now(clock).plusYears(1),
                remainingCount = BigDecimal(remaining),
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    /** 정원 초과 재현용 — 실제 예약 10건 대신 세션 카운터를 직접 정원까지 채운다. */
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
}
