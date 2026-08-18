package com.goldwrestling.attendance

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.auth.PrincipalType
import com.goldwrestling.auth.TokenService
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassType
import com.goldwrestling.reservation.Reservation
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassSessionStatus
import com.goldwrestling.schedule.ClassType
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 출석 관리자 API 4종의 HTTP 계약(ATTEND-01·02, D-132)을 검증한다: 명단 조회·예약제/1:1 체크·
 * 저녁반 추가·삭제의 성공 경로와 대표 실패 5종. 애노테이션 조합은 `AdminBatchControllerTest`와
 * 맞춘다(conventions §10.1) — **클래스 레벨 `@Transactional`을 붙이지 않는다.** 저녁반 차감이
 * 실제로 커밋돼야 삭제(복구) 케이스가 의미를 갖고, 409 거부 시나리오는 트랜잭션이 abort 상태가
 * 되므로 [cleanUp]에서 이 클래스가 만든 데이터만 직접 지운다.
 *
 * clock은 `Instant.now()`로 리셋한다 — 과거로 고정하면 발급 토큰이 시스템 시각 기준으로 이미
 * 만료돼 401이 된다(`AdminScheduleControllerTest`·`AdminBatchControllerTest` 선례).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class AdminAttendanceControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var classSessionRepository: ClassSessionRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L
    private var sessionDateCounter = 0L
    private val createdSessionIds = mutableListOf<Long>()

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @AfterEach
    fun cleanUp() {
        jdbcClient
            .sql("delete from attendance where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from reservation where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql(
                "delete from pass_transaction where pass_id in " +
                    "(select id from pass where member_id in (select id from member where kakao_id >= :base))",
            ).param("base", KAKAO_ID_BASE)
            .update()
        if (createdSessionIds.isNotEmpty()) {
            jdbcClient
                .sql("delete from class_session where id in (:ids)")
                .param("ids", createdSessionIds)
                .update()
            createdSessionIds.clear()
        }
        jdbcClient
            .sql("delete from pass where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from refresh_token where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from refresh_token where admin_id in (select id from admin where login_id like :prefix)")
            .param("prefix", "$ADMIN_LOGIN_PREFIX%")
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
    fun `명단을 조회하면 200과 함께 미체크 예약자의 status가 null로 내려온다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val schedule = sessionSchedule()
        val classDate = nextClassDate()
        val session = persistSession(schedule, classDate)
        val member = persistMember()
        val pass = persistSessionPass(member, "5.0", classDate.plusYears(1))
        persistReservation(member, session, pass, schedule, classDate)

        mockMvc
            .perform(
                get(BASE_PATH)
                    .param("classScheduleId", schedule.id.toString())
                    .param("classDate", classDate.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.classSessionId").value(session.id))
            .andExpect(jsonPath("$.entries[0].memberId").value(member.id))
            .andExpect(jsonPath("$.entries[0].status").doesNotExist())
    }

    @Test
    fun `예약제 체크는 200과 함께 status ATTENDED를 반환한다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val schedule = sessionSchedule()
        val classDate = nextClassDate()
        val session = persistSession(schedule, classDate)
        val member = persistMember()
        val pass = persistSessionPass(member, "5.0", classDate.plusYears(1))
        persistReservation(member, session, pass, schedule, classDate)

        mockMvc
            .perform(
                put(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .content(
                        """{"classScheduleId":${schedule.id},"classDate":"$classDate","memberId":${member.id},"status":"ATTENDED"}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ATTENDED"))
            .andExpect(jsonPath("$.deducted").value(false))
    }

    @Test
    fun `저녁반 출석 추가는 회비 없는 회원이면 200과 deducted true를 반환한다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val schedule = eveningSchedule()
        val classDate = nextClassDate()
        val member = persistMember()
        persistSessionPass(member, "2.0", classDate.plusYears(1))

        val result =
            mockMvc
                .perform(
                    post("$BASE_PATH/evening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate","memberId":${member.id}}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.deducted").value(true))
                .andReturn()
                .response.contentAsString
        trackSession(objectMapper.readTree(result).get("classSessionId").asLong())
    }

    @Test
    fun `출석을 삭제하면 204를 반환한다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val schedule = eveningSchedule()
        val classDate = nextClassDate()
        val member = persistMember()
        persistSessionPass(member, "2.0", classDate.plusYears(1))

        val created =
            mockMvc
                .perform(
                    post("$BASE_PATH/evening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate","memberId":${member.id}}"""),
                ).andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        val body = objectMapper.readTree(created)
        trackSession(body.get("classSessionId").asLong())
        val attendanceId = body.get("id").asLong()

        mockMvc
            .perform(delete("$BASE_PATH/$attendanceId").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `예약자가 아닌 회원을 체크하면 409와 ATTENDANCE_MEMBER_NOT_RESERVED를 반환한다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val schedule = sessionSchedule()
        val classDate = nextClassDate()
        val nonReservedMember = persistMember()

        mockMvc
            .perform(
                put(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .content(
                        """{"classScheduleId":${schedule.id},"classDate":"$classDate","memberId":${nonReservedMember.id},"status":"ATTENDED"}""",
                    ),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ATTENDANCE_MEMBER_NOT_RESERVED"))
        // check() 전체가 @Transactional이라 getOrCreate가 만들었을 세션도 함께 롤백된다 — 정리 불필요.
    }

    @Test
    fun `저녁반 세션에 예약제 체크 API를 호출하면 409와 ATTENDANCE_CLASS_TYPE_MISMATCH를 반환한다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val schedule = eveningSchedule()
        val classDate = nextClassDate()
        val member = persistMember()

        mockMvc
            .perform(
                put(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .content(
                        """{"classScheduleId":${schedule.id},"classDate":"$classDate","memberId":${member.id},"status":"ATTENDED"}""",
                    ),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ATTENDANCE_CLASS_TYPE_MISMATCH"))
    }

    @Test
    fun `잔여가 부족한 회원의 저녁반 출석 추가는 409와 EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE를 반환한다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val schedule = eveningSchedule()
        val classDate = nextClassDate()
        val member = persistMember()
        persistSessionPass(member, "0.0", classDate.plusYears(1))

        mockMvc
            .perform(
                post("$BASE_PATH/evening")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate","memberId":${member.id}}"""),
            ).andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE"))
        // addEveningAttendance 전체가 @Transactional이라 getOrCreate가 만들었을 세션도 함께 롤백된다.
    }

    @Test
    fun `없는 출석 id를 삭제하면 404와 ATTENDANCE_NOT_FOUND를 반환한다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)

        mockMvc
            .perform(delete("$BASE_PATH/$MISSING_ATTENDANCE_ID").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("ATTENDANCE_NOT_FOUND"))
    }

    @Test
    fun `회원 토큰으로 호출하면 403과 ACCESS_DENIED를 반환한다`() {
        val member = persistMember()
        val memberToken = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken
        val schedule = sessionSchedule()
        val classDate = nextClassDate()

        mockMvc
            .perform(
                get(BASE_PATH)
                    .param("classScheduleId", schedule.id.toString())
                    .param("classDate", classDate.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken"),
            ).andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

        mockMvc
            .perform(
                put(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken")
                    .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate","memberId":${member.id},"status":"ATTENDED"}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

        mockMvc
            .perform(
                post("$BASE_PATH/evening")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken")
                    .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate","memberId":${member.id}}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

        mockMvc
            .perform(delete("$BASE_PATH/$MISSING_ATTENDANCE_ID").header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun sessionSchedule(): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == DayOfWeek.TUESDAY && it.classType == ClassType.SESSION && it.startTime == LocalTime.of(11, 0) }

    private fun eveningSchedule(): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == DayOfWeek.MONDAY && it.classType == ClassType.EVENING && it.startTime == LocalTime.of(19, 0) }

    private fun persistMember(): Member {
        fixtureCounter++
        val kakaoId = KAKAO_ID_BASE + fixtureCounter
        return memberRepository.saveAndFlush(AttendanceFixtures.member(songpaBranch(), kakaoId = kakaoId))
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(AttendanceFixtures.admin(loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter"))
    }

    private fun adminAccessToken(admin: Admin): String = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken

    private fun persistSession(
        schedule: ClassSchedule,
        classDate: LocalDate,
        reservedCount: Int = 0,
    ): ClassSession {
        val session =
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
        trackSession(session.id)
        return session
    }

    private fun persistSessionPass(
        member: Member,
        remaining: String,
        endDate: LocalDate,
    ): Pass =
        passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = persistAdmin(),
                type = PassType.SESSION_PASS,
                status = PassStatus.ACTIVE,
                startDate = endDate.minusYears(1).plusDays(1),
                endDate = endDate,
                remainingCount = BigDecimal(remaining),
                createdAt = AttendanceFixtures.FIXED_TIME,
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
                canceledAt = null,
                canceledByMember = null,
                refunded = null,
                createdAt = OffsetDateTime.now(clock),
            ),
        )

    /** 매 호출마다 서로 다른 [LocalDate]를 써서 `uq_class_session`(class_schedule_id, class_date)을 피한다. */
    private fun nextClassDate(): LocalDate = BASE_SESSION_DATE.plusDays(sessionDateCounter++)

    private fun trackSession(sessionId: Long?) {
        if (sessionId != null) createdSessionIds += sessionId
    }

    private companion object {
        const val BASE_PATH = "/api/admin/attendances"
        const val KAKAO_ID_BASE = 9_850_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-attendance-ctrl-"
        const val MISSING_ATTENDANCE_ID = 999_999_999L
        val BASE_SESSION_DATE: LocalDate = LocalDate.of(2034, 1, 2) // 월요일
    }
}
