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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 관리자 알림 폴링·확인 처리 API 2종의 HTTP 계약을 검증한다(NOTIF-02, D-129): 목록 조회(미확인
 * 카운트 포함)·미확인만 보기 필터·모두 읽음(벌크 UPDATE 이후 재조회 회귀 방지)·권한.
 *
 * 애노테이션 조합은 `AdminAttendanceControllerTest`·`AdminBatchControllerTest`와 맞춘다
 * (conventions §10.1) — **클래스 레벨 `@Transactional`을 붙이지 않는다.** "모두 읽음" 벌크
 * UPDATE가 실제로 커밋돼야 [read-all 직후 재조회 테스트]가 stale 값 회귀를 실제로 검증할 수
 * 있다. 대신 [cleanUp]에서 이 클래스가 만든 데이터만 직접 지운다.
 *
 * `Notification.memberName`은 [Notification] 엔티티의 비정규화 표시 필드일 뿐 `Member`로의 FK가
 * 아니므로, 알림 픽스처는 실제 `Member`를 만들지 않고 문자열만 채운다 — 회원 토큰 발급 테스트만
 * 예외적으로 실제 `Member`가 필요하다(토큰 검증 시 존재 확인).
 *
 * clock은 `Instant.now()`로 리셋한다 — 과거로 고정하면 발급 토큰이 시스템 시각 기준으로 이미
 * 만료돼 401이 된다(`AdminAttendanceControllerTest`·`AdminBatchControllerTest` 선례).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class AdminNotificationControllerTest {
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
    fun `알림 3건(미확인 2 확인 1)을 넣고 GET하면 totalElements 3 unreadCount 2다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val session = persistSession()
        persistNotification(session, isRead = false)
        persistNotification(session, isRead = false)
        persistNotification(session, isRead = true)

        mockMvc
            .perform(get(BASE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notifications.totalElements").value(3))
            .andExpect(jsonPath("$.unreadCount").value(2))
    }

    @Test
    fun `unreadOnly=true로 GET하면 totalElements 2다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val session = persistSession()
        persistNotification(session, isRead = false)
        persistNotification(session, isRead = false)
        persistNotification(session, isRead = true)

        mockMvc
            .perform(
                get(BASE_PATH)
                    .param("unreadOnly", "true")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.notifications.totalElements").value(2))
    }

    /**
     * 벌크 UPDATE 직후 stale 값이 응답에 새지 않는지 확인하는 회귀 테스트(RESEARCH Pitfall 5) —
     * POST 응답의 `unreadCount`와 그 직후 별도 `GET`의 `unreadCount`를 모두 0으로 단언한다.
     */
    @Test
    fun `모두 읽음 처리 응답과 직후 재조회 둘 다 unreadCount가 0이다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val session = persistSession()
        persistNotification(session, isRead = false)
        persistNotification(session, isRead = false)

        mockMvc
            .perform(post("$BASE_PATH/read-all").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.updatedCount").value(2))
            .andExpect(jsonPath("$.unreadCount").value(0))

        mockMvc
            .perform(get(BASE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.unreadCount").value(0))
    }

    @Test
    fun `이미 읽은 알림의 readAt은 모두 읽음 재호출로 덮어써지지 않는다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val session = persistSession()
        val alreadyReadAt = OffsetDateTime.now(clock).minusDays(1)
        val alreadyRead = persistNotification(session, isRead = true, readAt = alreadyReadAt)
        persistNotification(session, isRead = false)

        mockMvc
            .perform(post("$BASE_PATH/read-all").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.updatedCount").value(1))

        val persisted = notificationRepository.findById(alreadyRead.id!!).orElseThrow()
        assertThat(persisted.readAt).isEqualTo(alreadyReadAt)
    }

    @Test
    fun `목록 응답 항목에 memberName classDate 등 비정규화 필드가 채워져 있다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val session = persistSession()
        persistNotification(session, isRead = false)

        mockMvc
            .perform(get(BASE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notifications.content[0].memberName").value(MEMBER_NAME))
            .andExpect(jsonPath("$.notifications.content[0].classDate").value(session.classDate.toString()))
            .andExpect(jsonPath("$.notifications.content[0].classType").value(session.classType.name))
    }

    @Test
    fun `회원 토큰으로 GET 호출 시 403과 ACCESS_DENIED를 반환한다`() {
        val member = persistMember()
        val memberToken = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

        mockMvc
            .perform(get(BASE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken"))
            .andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

        mockMvc
            .perform(post("$BASE_PATH/read-all").header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `size가 0이면 500이 아니라 400 VALIDATION_FAILED다`() {
        // 검증 없이 PageRequest.of(page, 0)에 들어가면 IllegalArgumentException이 나고
        // 포괄 핸들러가 이를 500으로 바꿔 버린다 — 잘못된 입력은 4xx여야 한다(conventions §8).
        val admin = persistAdmin()
        val token = adminAccessToken(admin)

        mockMvc
            .perform(
                get(BASE_PATH)
                    .param("size", "0")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `page가 음수면 500이 아니라 400 VALIDATION_FAILED다`() {
        val admin = persistAdmin()
        val token = adminAccessToken(admin)

        mockMvc
            .perform(
                get(BASE_PATH)
                    .param("page", "-1")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `size가 101이면 400 VALIDATION_FAILED다`() {
        // 상한이 없으면 30초 폴링 1회가 전체 테이블을 긁는다(T-06-32 페이지네이션 상한).
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
    fun `확인 여부 필드는 read가 아니라 isRead라는 이름으로 직렬화된다`() {
        // Kotlin의 is 접두 Boolean은 getter가 isRead()라 springdoc이 자바 빈 규약대로 read로
        // 스키마를 만든다. 실제 직렬화는 isRead라서, 이름을 고정하지 않으면 openapi.yaml 계약과
        // 실제 응답이 어긋나 FE가 생성한 타입으로 읽으면 항상 undefined가 된다(PR #20 리뷰 Warning).
        val admin = persistAdmin()
        val token = adminAccessToken(admin)
        val session = persistSession()
        persistNotification(session, isRead = false)

        mockMvc
            .perform(get(BASE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notifications.content[0].isRead").value(false))
            .andExpect(jsonPath("$.notifications.content[0].read").doesNotExist())
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

    /** 알림은 `Member` 엔티티를 참조하지 않는다 — [memberName]은 비정규화 문자열이다. */
    private fun persistNotification(
        session: ClassSession,
        isRead: Boolean,
        readAt: OffsetDateTime? = if (isRead) OffsetDateTime.now(clock) else null,
    ): Notification {
        occurredAtOffset++
        val notification =
            notificationRepository.saveAndFlush(
                Notification(
                    type = NotificationType.RESERVATION_CREATED,
                    reservation = null,
                    classSession = session,
                    memberName = MEMBER_NAME,
                    classType = session.classType,
                    classDate = session.classDate,
                    startTime = session.startTime,
                    message = "테스트 알림 $occurredAtOffset",
                    occurredAt = OffsetDateTime.now(clock).plusSeconds(occurredAtOffset),
                    isRead = isRead,
                    readAt = readAt,
                    createdAt = OffsetDateTime.now(clock),
                ),
            )
        createdNotificationIds += requireNotNull(notification.id)
        return notification
    }

    /** 매 호출마다 서로 다른 [LocalDate]를 써서 `uq_class_session`(class_schedule_id, class_date)을 피한다. */
    private fun nextClassDate(): LocalDate = BASE_SESSION_DATE.plusDays(sessionDateCounter++)

    private companion object {
        const val BASE_PATH = "/api/admin/notifications"
        const val KAKAO_ID_BASE = 9_870_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-notif-ctrl-"
        const val MEMBER_NAME = "알림테스트회원"
        val BASE_SESSION_DATE: LocalDate = LocalDate.of(2034, 2, 7) // 화요일
    }
}
