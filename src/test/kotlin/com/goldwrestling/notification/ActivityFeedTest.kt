package com.goldwrestling.notification

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.attendance.AttendanceFixtures
import com.goldwrestling.auth.PrincipalType
import com.goldwrestling.auth.TokenService
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassType
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 관리자 활동 피드 API(`GET /api/admin/activity-feed`, NOTIF-03, D-129)의 HTTP 계약을 검증한다:
 * 필터 없음(읽음·미읽음 모두 최신순) · 기간 필터 · 종류 필터 · 기간+종류 AND 조합 · size 상한 400 ·
 * 회원 토큰 403.
 *
 * 애노테이션 조합·픽스처·정리 관례는 `AdminNotificationControllerTest`(06-09)와 동일하게 맞춘다
 * (conventions §10.1, 컨텍스트 캐시 재사용).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class ActivityFeedTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var classSessionRepository: ClassSessionRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L
    private var sessionDateCounter = 0L
    private var occurredAtOffset = 0L
    private val createdSessionIds = mutableListOf<Long>()
    private val createdNotificationIds = mutableListOf<Long>()

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @AfterEach
    fun cleanUp() {
        if (createdNotificationIds.isNotEmpty()) {
            notificationRepository.deleteAllById(createdNotificationIds)
            createdNotificationIds.clear()
        }
        if (createdSessionIds.isNotEmpty()) {
            jdbcClient
                .sql("delete from class_session where id in (:ids)")
                .param("ids", createdSessionIds)
                .update()
            createdSessionIds.clear()
        }
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
    fun `필터 없이 호출하면 읽음 미읽음 알림이 모두 최신순으로 나온다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val session = persistSession()
        val older = persistNotification(session, isRead = true, type = NotificationType.RESERVATION_CREATED)
        val newer = persistNotification(session, isRead = false, type = NotificationType.RESERVATION_CANCELED_BY_MEMBER)

        mockMvc
            .perform(get(BASE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].id").value(newer.id))
            .andExpect(jsonPath("$.content[1].id").value(older.id))
    }

    @Test
    fun `from to로 기간을 좁히면 그 범위 밖 알림이 제외된다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val session = persistSession()
        val base = OffsetDateTime.now(clock)
        val inRange = persistNotification(session, isRead = false, occurredAt = base)
        persistNotification(session, isRead = false, occurredAt = base.plusDays(10))

        mockMvc
            .perform(
                get(BASE_PATH)
                    .param("from", base.minusHours(1).toString())
                    .param("to", base.plusHours(1).toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(inRange.id))
    }

    @Test
    fun `type을 지정하면 해당 종류만 나온다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val session = persistSession()
        val created = persistNotification(session, isRead = false, type = NotificationType.RESERVATION_CREATED)
        persistNotification(session, isRead = false, type = NotificationType.RESERVATION_CANCELED_BY_MEMBER)

        mockMvc
            .perform(
                get(BASE_PATH)
                    .param("type", NotificationType.RESERVATION_CREATED.name)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(created.id))
    }

    @Test
    fun `기간과 종류를 함께 주면 두 조건이 AND로 적용된다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val session = persistSession()
        val base = OffsetDateTime.now(clock)
        val matches =
            persistNotification(session, isRead = false, type = NotificationType.RESERVATION_CREATED, occurredAt = base)
        // 종류는 맞지만 기간 밖
        persistNotification(
            session,
            isRead = false,
            type = NotificationType.RESERVATION_CREATED,
            occurredAt = base.plusDays(10),
        )
        // 기간은 맞지만 종류가 다름
        persistNotification(
            session,
            isRead = false,
            type = NotificationType.RESERVATION_CANCELED_BY_MEMBER,
            occurredAt = base,
        )

        mockMvc
            .perform(
                get(BASE_PATH)
                    .param("from", base.minusHours(1).toString())
                    .param("to", base.plusHours(1).toString())
                    .param("type", NotificationType.RESERVATION_CREATED.name)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(matches.id))
    }

    @Test
    fun `size가 101이면 400 VALIDATION_FAILED다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)

        mockMvc
            .perform(
                get(BASE_PATH)
                    .param("size", "101")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `회원 토큰으로 호출하면 403이다`() {
        val member = persistMember()
        val memberToken = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        mockMvc
            .perform(get(BASE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `읽음 처리된 알림도 피드에 나온다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val session = persistSession()
        val readNotification = persistNotification(session, isRead = true)

        mockMvc
            .perform(get(BASE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(readNotification.id))
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun sessionSchedule(): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == DayOfWeek.TUESDAY && it.classType == ClassType.SESSION && it.startTime == LocalTime.of(11, 0) }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(AttendanceFixtures.admin(loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter"))
    }

    private fun adminAccessToken(admin: Admin): String = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken

    private fun persistMember(): Member {
        fixtureCounter++
        val kakaoId = KAKAO_ID_BASE + fixtureCounter
        return memberRepository.saveAndFlush(AttendanceFixtures.member(songpaBranch(), kakaoId = kakaoId))
    }

    private fun persistSession(): ClassSession {
        val schedule = sessionSchedule()
        val session = classSessionRepository.saveAndFlush(AttendanceFixtures.classSession(schedule, nextClassDate()))
        createdSessionIds += requireNotNull(session.id)
        return session
    }

    /** 알림은 `Member` 엔티티를 참조하지 않는다 — [Notification.memberName]은 비정규화 문자열이다. */
    private fun persistNotification(
        session: ClassSession,
        isRead: Boolean,
        type: NotificationType = NotificationType.RESERVATION_CREATED,
        occurredAt: OffsetDateTime? = null,
    ): Notification {
        occurredAtOffset++
        val notification =
            notificationRepository.saveAndFlush(
                Notification(
                    type = type,
                    reservation = null,
                    classSession = session,
                    memberName = MEMBER_NAME,
                    classType = session.classType,
                    classDate = session.classDate,
                    startTime = session.startTime,
                    message = "테스트 알림 $occurredAtOffset",
                    occurredAt = occurredAt ?: OffsetDateTime.now(clock).plusSeconds(occurredAtOffset),
                    isRead = isRead,
                    readAt = if (isRead) OffsetDateTime.now(clock) else null,
                    createdAt = OffsetDateTime.now(clock),
                ),
            )
        createdNotificationIds += requireNotNull(notification.id)
        return notification
    }

    /** 매 호출마다 서로 다른 [LocalDate]를 써서 `uq_class_session`(class_schedule_id, class_date)을 피한다. */
    private fun nextClassDate(): LocalDate = BASE_SESSION_DATE.plusDays(sessionDateCounter++)

    private companion object {
        const val BASE_PATH = "/api/admin/activity-feed"
        const val KAKAO_ID_BASE = 9_880_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-feed-ctrl-"
        const val MEMBER_NAME = "피드테스트회원"
        val BASE_SESSION_DATE: LocalDate = LocalDate.of(2034, 5, 9) // 화요일
    }
}
