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

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private var fixtureCounter = 60000L

    /**
     * `MutableTestClock`은 싱글턴 빈이라 이전 테스트가 밀어 둔 시각이 이어진다
     * (`AdminPassControllerTest`·`MemberScheduleControllerTest`와 동일 관례). 매 테스트 시작 시
     * 실제 현재 시각으로 되돌려야 [tokenAtThisWeekMonday]가 계산하는 "이번 주"가 실제 이번 주와
     * 일치하고, 토큰의 `issuedAt`/`expiresAt`도 `JwtDecoder`가 검증하는 실제 시스템 시각과 어긋나지
     * 않는다.
     */
    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

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

    // classScheduleId는 Kotlin non-null 생성자 파라미터라, JSON에 키 자체가 없으면 Jackson
    // 역직렬화가 @Valid 검증이 실행되기도 전에 실패한다 — 그래서 여기서 나오는 코드는
    // VALIDATION_FAILED가 아니라 MALFORMED_REQUEST다(`AdminPassControllerTest`의 `note` 케이스와
    // 동일한 관례).
    @Test
    fun `classScheduleId 누락이면 400과 MALFORMED_REQUEST를 반환한다`() {
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
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
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

    // ---------- 취소·변경·본인 목록 (04-10) ----------

    @Test
    fun `예약을 취소하면 200과 CANCELED 상태의 ReservationResponse가 온다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        persistPass(member, PassType.SESSION_PASS, "3.0")
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)
        val reservationId = createReservation(token, schedule.id!!, classDate)

        mockMvc
            .perform(
                post("/api/members/me/reservations/$reservationId/cancellation")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELED"))
    }

    @Test
    fun `예약을 변경하면 200과 새 예약의 ReservationResponse가 온다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        persistPass(member, PassType.SESSION_PASS, "3.0")
        val oldSchedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val newSchedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)
        val reservationId = createReservation(token, oldSchedule.id!!, classDate)

        mockMvc
            .perform(
                post("/api/members/me/reservations/$reservationId/change")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":${newSchedule.id},"classDate":"$classDate"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.startTime").value("13:00:00"))
    }

    @Test
    fun `타인 예약 id로 취소·변경을 시도하면 404와 RESERVATION_NOT_FOUND를 반환한다`() {
        val owner = persistMember(MemberStatus.ACTIVE)
        val (ownerToken, monday) = tokenAtThisWeekMonday(owner)
        persistPass(owner, PassType.SESSION_PASS, "3.0")
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val newSchedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)
        val reservationId = createReservation(ownerToken, schedule.id!!, classDate)

        val stranger = persistMember(MemberStatus.ACTIVE)
        val (strangerToken, _) = tokenAtThisWeekMonday(stranger)

        mockMvc
            .perform(
                post("/api/members/me/reservations/$reservationId/cancellation")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $strangerToken"),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"))

        mockMvc
            .perform(
                post("/api/members/me/reservations/$reservationId/change")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $strangerToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":${newSchedule.id},"classDate":"$classDate"}"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"))
    }

    @Test
    fun `같은 세션에 두 회원이 예약해도 본인 목록에는 본인 예약만 보인다`() {
        val memberA = persistMember(MemberStatus.ACTIVE)
        val (tokenA, monday) = tokenAtThisWeekMonday(memberA)
        persistPass(memberA, PassType.SESSION_PASS, "3.0")
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)
        val reservationIdA = createReservation(tokenA, schedule.id!!, classDate)

        val memberB = persistMember(MemberStatus.ACTIVE)
        val (tokenB, _) = tokenAtThisWeekMonday(memberB)
        persistPass(memberB, PassType.SESSION_PASS, "3.0")
        createReservation(tokenB, schedule.id!!, classDate)

        val body =
            mockMvc
                .perform(get("/api/members/me/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer $tokenA"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn()
                .response.contentAsString

        val idsA = objectMapper.readTree(body).get("content").toList().map { it.get("id").asLong() }
        assertThat(idsA).containsExactly(reservationIdA)
    }

    @Test
    fun `취소된 예약은 본인 목록에서 제외된다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        persistPass(member, PassType.SESSION_PASS, "3.0")
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)
        val reservationId = createReservation(token, schedule.id!!, classDate)

        mockMvc
            .perform(
                post("/api/members/me/reservations/$reservationId/cancellation")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)

        mockMvc
            .perform(get("/api/members/me/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content.length()").value(0))
    }

    @Test
    fun `PENDING 회원은 취소·변경·목록조회 모두 403과 MEMBER_NOT_ACTIVE를 반환한다`() {
        val member = persistMember(MemberStatus.PENDING)
        val (token, monday) = tokenAtThisWeekMonday(member)
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)

        mockMvc
            .perform(
                post("/api/members/me/reservations/1/cancellation")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_ACTIVE"))

        mockMvc
            .perform(
                post("/api/members/me/reservations/1/change")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"classScheduleId":1,"classDate":"$classDate"}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_ACTIVE"))

        mockMvc
            .perform(get("/api/members/me/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_ACTIVE"))
    }

    @Test
    fun `본인 예약 목록은 page-size 파라미터가 동작하고 기본값이 적용되며 classDate-startTime 오름차순이다`() {
        val member = persistMember(MemberStatus.ACTIVE)
        val (token, monday) = tokenAtThisWeekMonday(member)
        persistPass(member, PassType.SESSION_PASS, "3.0")
        val scheduleEarly = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val scheduleLate = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val classDate = monday.plusDays(DayOfWeek.TUESDAY.value - 1L)
        createReservation(token, scheduleLate.id!!, classDate)
        createReservation(token, scheduleEarly.id!!, classDate)

        mockMvc
            .perform(get("/api/members/me/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.content[0].startTime").value("11:00:00"))
            .andExpect(jsonPath("$.content[1].startTime").value("13:00:00"))

        mockMvc
            .perform(
                get("/api/members/me/reservations")
                    .param("size", "1")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.content.length()").value(1))
    }

    /** 예약 생성 후 응답에서 id만 뽑아 이후 취소·변경 호출의 경로 변수로 쓴다. */
    private fun createReservation(
        token: String,
        scheduleId: Long,
        classDate: LocalDate,
    ): Long {
        val body =
            mockMvc
                .perform(
                    post("/api/members/me/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"classScheduleId":$scheduleId,"classDate":"$classDate"}"""),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(body).get("id").asLong()
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
