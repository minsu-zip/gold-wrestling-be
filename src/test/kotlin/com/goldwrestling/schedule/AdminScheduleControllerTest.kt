package com.goldwrestling.schedule

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminBranch
import com.goldwrestling.admin.AdminBranchRepository
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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

/**
 * `GET /api/admin/schedule/board`의 HTTP 계약(상태코드·인가·쿼리 파라미터 바인딩·JSON 직렬화)을
 * 검증한다(SCHED-03, 04-11-PLAN Task 2). 그리드 조립 규칙 자체는 `AdminScheduleServiceTest`(Task 1)가
 * 이미 고정했으므로, 여기서는 HTTP 계약에만 단언을 집중한다. 애노테이션 조합은
 * `MemberScheduleControllerTest`·`AdminPassControllerTest`와 맞춘다(conventions §10.1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class AdminScheduleControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var adminBranchRepository: AdminBranchRepository

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

    private var fixtureCounter = 80000L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `weekStart를 생략하면 이번 주 월~일 7일 그리드가 200으로 내려온다`() {
        val token = adminAccessToken()
        val monday = WeekRange.of(LocalDate.now(clock)).monday

        val body =
            mockMvc
                .perform(get("/api/admin/schedule/board").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        val root = objectMapper.readTree(body)
        assertThat(root.get("weekStart").asText()).isEqualTo(monday.toString())
        assertThat(root.get("weekEnd").asText()).isEqualTo(monday.plusDays(6).toString())
        assertThat(root.get("days").toList()).hasSize(7)
    }

    @Test
    fun `지난 주 월요일 weekStart로 조회해도 200이다(관리자는 범위 제한 없음)`() {
        val token = adminAccessToken()
        val lastWeekMonday = WeekRange.of(LocalDate.now(clock)).monday.minusWeeks(1)

        mockMvc
            .perform(
                get("/api/admin/schedule/board")
                    .param("weekStart", lastWeekMonday.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.weekStart").value(lastWeekMonday.toString()))
    }

    @Test
    fun `수요일 weekStart로 조회하면 200이고 응답 weekStart는 그 주 월요일로 정규화된다`() {
        val token = adminAccessToken()
        val monday = WeekRange.of(LocalDate.now(clock)).monday
        val wednesday = monday.plusDays(2)

        mockMvc
            .perform(
                get("/api/admin/schedule/board")
                    .param("weekStart", wednesday.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.weekStart").value(monday.toString()))
    }

    @Test
    fun `예약 3건 있는 셀은 reservations 3건이고 각 항목에 memberName이 포함된다`() {
        val token = adminAccessToken()
        val admin = persistAdmin()
        val monday = WeekRange.of(LocalDate.now(clock)).monday
        val classDate = monday.plusDays(DayOfWeek.THURSDAY.value - 1L)
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val session = persistSession(schedule, classDate, reservedCount = 3)
        val members = (1..3).map { persistMember(name = "명단회원$it") }
        members.forEach { member ->
            val pass = persistPass(member, admin, PassType.SESSION_PASS)
            persistReservation(member, session, pass, schedule, classDate)
        }

        val body = getBoard(token)
        val cell = cellAt(body, classDate, ClassType.SESSION, LocalTime.of(11, 0))

        assertThat(cell.get("reservations").toList()).hasSize(3)
        val memberNames = cell.get("reservations").toList().map { it.get("memberName").asText() }
        assertThat(memberNames).containsExactlyInAnyOrderElementsOf(members.map { it.name })
    }

    @Test
    fun `취소된 예약이 있는 셀은 명단에서 제외되고 reservedCount에도 반영되지 않는다`() {
        val token = adminAccessToken()
        val admin = persistAdmin()
        val monday = WeekRange.of(LocalDate.now(clock)).monday
        val classDate = monday.plusDays(DayOfWeek.THURSDAY.value - 1L)
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val session = persistSession(schedule, classDate, reservedCount = 1)
        val activeMember = persistMember(name = "활성회원")
        val activePass = persistPass(activeMember, admin, PassType.SESSION_PASS)
        persistReservation(activeMember, session, activePass, schedule, classDate)
        val canceledMember = persistMember(name = "취소회원")
        val canceledPass = persistPass(canceledMember, admin, PassType.SESSION_PASS)
        persistReservation(canceledMember, session, canceledPass, schedule, classDate, status = ReservationStatus.CANCELED)

        val body = getBoard(token)
        val cell = cellAt(body, classDate, ClassType.SESSION, LocalTime.of(13, 0))

        assertThat(cell.get("reservedCount").asInt()).isEqualTo(1)
        assertThat(cell.get("reservations").toList()).hasSize(1)
        assertThat(cell.get("reservations")[0].get("memberName").asText()).isEqualTo(activeMember.name)
    }

    @Test
    fun `휴강 처리된 셀은 suspended true이고 cancelReason이 포함된다`() {
        val token = adminAccessToken()
        val admin = persistAdmin()
        val monday = WeekRange.of(LocalDate.now(clock)).monday
        val classDate = monday.plusDays(DayOfWeek.WEDNESDAY.value - 1L)
        val schedule = findSchedule(DayOfWeek.WEDNESDAY, ClassType.LESSON, LocalTime.of(19, 0))
        persistSuspendedSession(schedule, classDate, admin, "설비 점검")

        val body = getBoard(token)
        val cell = cellAt(body, classDate, ClassType.LESSON, LocalTime.of(19, 0))

        assertThat(cell.get("suspended").asBoolean()).isTrue()
        assertThat(cell.get("cancelReason").asText()).isEqualTo("설비 점검")
    }

    @Test
    fun `회원 토큰으로 조회하면 403과 ACCESS_DENIED를 반환한다`() {
        val member = persistMember(name = "회원")
        val token = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        mockMvc
            .perform(get("/api/admin/schedule/board").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `토큰 없이 조회하면 401과 UNAUTHENTICATED를 반환한다`() {
        mockMvc
            .perform(get("/api/admin/schedule/board"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `소속되지 않은 branchId를 명시하면 403과 ADMIN_BRANCH_NOT_ASSIGNED를 반환한다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        val otherBranch = branchRepository.saveAndFlush(Branch(name = "미배정지점"))

        mockMvc
            .perform(
                get("/api/admin/schedule/board")
                    .param("branchId", otherBranch.id.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ADMIN_BRANCH_NOT_ASSIGNED"))
    }

    @Test
    fun `소속된 branchId를 명시하면 200이다`() {
        val admin = persistAdmin()
        val branch = songpaBranch()
        adminBranchRepository.saveAndFlush(AdminBranch(admin = admin, branch = branch))
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken

        mockMvc
            .perform(
                get("/api/admin/schedule/board")
                    .param("branchId", branch.id.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
    }

    /**
     * 휴강 처리·해제(POST)에도 지점 소속 검증이 걸리는지 고정한다 — 조회(GET)만 `resolveBranchId`로
     * 검증하고 변경은 검증하지 않던 비대칭을 없앤 수정(PR #11 리뷰 Warning)의 회귀 방지다.
     * 지점이 늘어나는 순간 `classScheduleId`만 알면 타 지점 수업을 휴강(예약 N건 자동 취소 +
     * 차감 복구)시킬 수 있었다.
     */
    @Test
    fun `타 지점에만 소속된 관리자가 휴강 처리하면 403과 ADMIN_BRANCH_NOT_ASSIGNED를 반환한다`() {
        val admin = persistAdmin()
        val otherBranch = branchRepository.saveAndFlush(Branch(name = "미배정지점"))
        adminBranchRepository.saveAndFlush(AdminBranch(admin = admin, branch = otherBranch))
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        val monday = WeekRange.of(LocalDate.now(clock)).monday
        val schedule = findSchedule(DayOfWeek.WEDNESDAY, ClassType.LESSON, LocalTime.of(19, 0))
        val classDate = monday.plusDays(DayOfWeek.WEDNESDAY.value - 1L)

        mockMvc
            .perform(
                post("/api/admin/class-sessions/suspension")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate","reason":"타 지점 휴강 시도"}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ADMIN_BRANCH_NOT_ASSIGNED"))
    }

    @Test
    fun `타 지점에만 소속된 관리자가 휴강 해제하면 403과 ADMIN_BRANCH_NOT_ASSIGNED를 반환한다`() {
        val owner = persistAdmin()
        val admin = persistAdmin()
        val otherBranch = branchRepository.saveAndFlush(Branch(name = "미배정지점"))
        adminBranchRepository.saveAndFlush(AdminBranch(admin = admin, branch = otherBranch))
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        val monday = WeekRange.of(LocalDate.now(clock)).monday
        val schedule = findSchedule(DayOfWeek.WEDNESDAY, ClassType.LESSON, LocalTime.of(19, 0))
        val session =
            persistSuspendedSession(schedule, monday.plusDays(DayOfWeek.WEDNESDAY.value - 1L), owner, "설비 점검")

        mockMvc
            .perform(
                post("/api/admin/class-sessions/${session.id}/resumption")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ADMIN_BRANCH_NOT_ASSIGNED"))
    }

    /**
     * `admin_branch` 매핑이 **하나도 없는** 관리자(시드 관리자를 포함한 현재 모든 관리자,
     * `AdminBranchRepository` KDoc)는 지점이 하나뿐인 동안 계속 휴강 처리를 할 수 있어야 한다.
     * 위 인가 검증을 `existsByAdminIdAndBranchId` 하나로만 짜면 이 관리자들이 전부 403이 되어
     * 휴강 기능 자체가 막힌다 — 그 회귀를 막는 테스트다.
     */
    @Test
    fun `지점 매핑이 없는 관리자도 지점이 하나뿐이면 휴강 처리할 수 있다`() {
        val admin = persistAdmin()
        val token = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
        val monday = WeekRange.of(LocalDate.now(clock)).monday
        val schedule = findSchedule(DayOfWeek.WEDNESDAY, ClassType.LESSON, LocalTime.of(19, 0))
        val classDate = monday.plusDays(DayOfWeek.WEDNESDAY.value - 1L)

        mockMvc
            .perform(
                post("/api/admin/class-sessions/suspension")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate","reason":"매핑 없는 관리자"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELED"))
    }

    private fun getBoard(token: String): String =
        mockMvc
            .perform(get("/api/admin/schedule/board").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
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

    private fun persistMember(name: String): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "$name$fixtureCounter",
                phoneNumber = "0108888$fixtureCounter",
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
                loginId = "admin-schedule-board-ctrl-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun adminAccessToken(): String {
        val admin = persistAdmin()
        return tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
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
