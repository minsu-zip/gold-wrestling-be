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
import com.goldwrestling.notification.NotificationRepository
import com.goldwrestling.notification.NotificationType
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.reservation.dto.AdminCancelReservationRequest
import com.goldwrestling.reservation.dto.AdminChangeReservationRequest
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * `AdminReservationService.cancelByAdmin`/`changeByAdmin`(RESV-08, 04-13 Task 1)을 실제
 * PostgreSQL로 증명하는 통합테스트. `MemberReservationCancellationTest`와 동일하게 클래스에
 * `@Transactional`을 붙이지 않는다 — "정원 초과로 변경이 실패하면 기존 예약이 그대로 남는다"를
 * 검증하려면 `changeByAdmin`이 실제로 커밋/롤백한 결과를 봐야 하기 때문이다(같은 트랜잭션 안에서
 * 조건부 UPDATE 직후 재조회하면 아직 커밋되지 않은 중간 상태를 보게 된다).
 *
 * 클록은 `MemberReservationCancellationTest`와 동일한 고정 과거 값(`BASE_CLOCK`)으로 맞춘다 —
 * `NimbusJwtDecoder`의 기본 `exp` 검증은 실제 시스템 시각과 비교하지만, `exp`가 실제 현재보다
 * 훨씬 미래(고정 클록 발급 시각 + 만료분)라 토큰은 정상 검증된다(`AdminPassControllerTest`가
 * 우려하는 "고정 클록이 실제 시각보다 과거라 방금 발급한 토큰도 만료로 거부되는" 상황과는 다르다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class AdminReservationCancellationTest {
    @Autowired
    private lateinit var adminReservationService: AdminReservationService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var passTransactionRepository: PassTransactionRepository

    @Autowired
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var classSessionRepository: ClassSessionRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(OffsetDateTime.parse(BASE_CLOCK).toInstant())
    }

    @AfterEach
    fun cleanUp() {
        jdbcClient
            .sql("delete from notification where class_date between :from and :to")
            .param("from", RANGE_FROM)
            .param("to", RANGE_TO)
            .update()
        jdbcClient
            .sql("delete from reservation where class_date between :from and :to")
            .param("from", RANGE_FROM)
            .param("to", RANGE_TO)
            .update()
        // 대리 조작 이력은 member_id가 항상 null이라(행위자가 관리자) member_id 기준 필터로는
        // 지워지지 않는다 — pass_id 경유로 지운다(MemberReservationCancellationTest의 cleanUp과
        // 다른 점).
        jdbcClient
            .sql(
                "delete from pass_transaction where pass_id in " +
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
        // HttpContract 테스트가 tokenService.issueTokenPair로 발급한 refresh_token — member/admin
        // 삭제 전에 먼저 지운다(MemberReservationCancellationTest는 토큰을 발급하지 않아 이 단계가 없었다).
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

    // ---------- 대리 취소 ----------

    @Test
    fun `refund 기본값으로 대리 취소하면 잔여가 즉시 복구되고 CANCEL_REFUND 이력의 주체가 관리자다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val reservation = persistActiveReservation(member, admin, schedule, classDate, passRemainingAfterDeduction = "2.0")
        val passId = requireNotNull(reservation.pass.id)

        val response = adminReservationService.cancelByAdmin(admin.id!!, reservation.id!!, AdminCancelReservationRequest())

        assertThat(response.status).isEqualTo(ReservationStatus.CANCELED)
        assertThat(response.refunded).isTrue()

        val refreshedReservation = reservationRepository.findById(reservation.id!!).get()
        assertThat(refreshedReservation.canceledByAdmin?.id).isEqualTo(admin.id)

        val refreshedSession = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)!!
        assertThat(refreshedSession.reservedCount).isEqualTo(0)

        val refreshedPass = passRepository.findById(passId).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("3.0"))

        val refundTransactions =
            passTransactionRepository.findAll().filter { it.pass.id == passId && it.reason == TransactionReason.CANCEL_REFUND }
        assertThat(refundTransactions).hasSize(1)
        assertThat(refundTransactions.first().admin?.id).isEqualTo(admin.id)
        assertThat(refundTransactions.first().member).isNull()

        val notifications = notificationRepository.findAll().filter { it.reservation?.id == reservation.id }
        assertThat(notifications.map { it.type }).contains(NotificationType.RESERVATION_CANCELED_BY_ADMIN)
    }

    @Test
    fun `refund를 false로 지정해 대리 취소하면 잔여가 그대로이고 CANCEL_REFUND 이력이 생기지 않으며 refunded는 false로 저장된다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val reservation = persistActiveReservation(member, admin, schedule, classDate, passRemainingAfterDeduction = "1.5")
        val passId = requireNotNull(reservation.pass.id)

        val response =
            adminReservationService.cancelByAdmin(admin.id!!, reservation.id!!, AdminCancelReservationRequest(refund = false))

        assertThat(response.status).isEqualTo(ReservationStatus.CANCELED)
        assertThat(response.refunded).isFalse()

        val refreshedPass = passRepository.findById(passId).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("1.5"))

        val refundTransactions =
            passTransactionRepository.findAll().filter { it.pass.id == passId && it.reason == TransactionReason.CANCEL_REFUND }
        assertThat(refundTransactions).isEmpty()
    }

    @Test
    fun `당일 예약도 대리 취소할 수 있다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val today = LocalDate.now(clock)
        val schedule = findScheduleForDay(today.dayOfWeek, ClassType.LESSON)
        val reservation =
            persistActiveReservation(
                member,
                admin,
                schedule,
                today,
                passType = PassType.LESSON_PASS,
                passRemainingAfterDeduction = "1.0",
            )

        val response = adminReservationService.cancelByAdmin(admin.id!!, reservation.id!!, AdminCancelReservationRequest())

        assertThat(response.status).isEqualTo(ReservationStatus.CANCELED)
    }

    @Test
    fun `지난 수업 예약도 대리 취소할 수 있다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val yesterday = LocalDate.now(clock).minusDays(1)
        val schedule = findScheduleForDay(yesterday.dayOfWeek, ClassType.LESSON)
        val reservation =
            persistActiveReservation(
                member,
                admin,
                schedule,
                yesterday,
                passType = PassType.LESSON_PASS,
                passRemainingAfterDeduction = "1.0",
            )

        val response = adminReservationService.cancelByAdmin(admin.id!!, reservation.id!!, AdminCancelReservationRequest())

        assertThat(response.status).isEqualTo(ReservationStatus.CANCELED)
    }

    @Test
    fun `이미 취소된 예약을 다시 대리 취소하면 ReservationAlreadyCanceledException이 발생한다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.THURSDAY)
        val reservation = persistActiveReservation(member, admin, schedule, classDate)
        adminReservationService.cancelByAdmin(admin.id!!, reservation.id!!, AdminCancelReservationRequest())

        assertThatThrownBy {
            adminReservationService.cancelByAdmin(admin.id!!, reservation.id!!, AdminCancelReservationRequest())
        }.isInstanceOf(ReservationAlreadyCanceledException::class.java)
    }

    @Test
    fun `등록 취소된 이용권으로 잡힌 예약을 refund true로 대리 취소해도 잔여가 복구되지 않는다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val classDate = nextClassDate(DayOfWeek.THURSDAY)
        val reservation =
            persistActiveReservation(
                member,
                admin,
                schedule,
                classDate,
                passRemainingAfterDeduction = "0",
                passStatus = PassStatus.CANCELED,
            )
        val passId = requireNotNull(reservation.pass.id)

        adminReservationService.cancelByAdmin(admin.id!!, reservation.id!!, AdminCancelReservationRequest(refund = true))

        val refreshedPass = passRepository.findById(passId).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("0"))
        val refundTransactions =
            passTransactionRepository.findAll().filter { it.pass.id == passId && it.reason == TransactionReason.CANCEL_REFUND }
        assertThat(refundTransactions).isEmpty()
    }

    @Test
    fun `존재하지 않는 예약id로 대리 취소하면 ReservationNotFoundException이 발생한다`() {
        val admin = persistAdmin()

        assertThatThrownBy {
            adminReservationService.cancelByAdmin(admin.id!!, 999_999_999L, AdminCancelReservationRequest())
        }.isInstanceOf(ReservationNotFoundException::class.java)
    }

    // ---------- 대리 변경 ----------

    @Test
    fun `관리자가 다른 타임으로 대리 변경하면 기존 예약이 취소되고 새 예약이 생성되며 PassTransaction 2건의 주체가 모두 관리자다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val oldSchedule = findSchedule(DayOfWeek.FRIDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val newSchedule = findSchedule(DayOfWeek.FRIDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val classDate = nextClassDate(DayOfWeek.FRIDAY)
        val reservation = persistActiveReservation(member, admin, oldSchedule, classDate, passRemainingAfterDeduction = "2.0")
        val passId = requireNotNull(reservation.pass.id)
        val transactionIdsBefore = passTransactionRepository.findAll().map { it.id }.toSet()

        val response =
            adminReservationService.changeByAdmin(
                admin.id!!,
                reservation.id!!,
                AdminChangeReservationRequest(newSchedule.id!!, classDate),
            )

        assertThat(response.status).isEqualTo(ReservationStatus.ACTIVE)
        assertThat(response.startTime).isEqualTo(LocalTime.of(13, 0))
        assertThat(response.id).isNotEqualTo(reservation.id)

        val originalReservation = reservationRepository.findById(reservation.id!!).get()
        assertThat(originalReservation.status).isEqualTo(ReservationStatus.CANCELED)
        assertThat(originalReservation.canceledByAdmin?.id).isEqualTo(admin.id)

        val newTransactions = passTransactionRepository.findAll().filterNot { it.id in transactionIdsBefore }
        assertThat(newTransactions).hasSize(2)
        assertThat(newTransactions).allMatch { it.admin?.id == admin.id && it.member == null }
        assertThat(newTransactions.map { it.reason })
            .containsExactlyInAnyOrder(TransactionReason.CANCEL_REFUND, TransactionReason.RESERVE)

        val refreshedPass = passRepository.findById(passId).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))

        val notifications = notificationRepository.findAll().filter { it.reservation?.id == response.id }
        assertThat(notifications.map { it.type }).contains(NotificationType.RESERVATION_CHANGED_BY_ADMIN)
    }

    @Test
    fun `당일 예약도 대리 변경할 수 있다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val today = LocalDate.now(clock)
        val todayLessonSchedules =
            classScheduleRepository
                .findAllByBranchId(songpaBranch().id!!)
                .filter { it.dayOfWeek == today.dayOfWeek && it.classType == ClassType.LESSON }
        val oldSchedule = todayLessonSchedules[0]
        val newSchedule = todayLessonSchedules[1]
        val reservation =
            persistActiveReservation(
                member,
                admin,
                oldSchedule,
                today,
                passType = PassType.LESSON_PASS,
                passRemainingAfterDeduction = "1.0",
            )

        val response =
            adminReservationService.changeByAdmin(admin.id!!, reservation.id!!, AdminChangeReservationRequest(newSchedule.id!!, today))

        assertThat(response.status).isEqualTo(ReservationStatus.ACTIVE)
        assertThat(response.startTime).isEqualTo(newSchedule.startTime)
    }

    @Test
    fun `변경 대상 수업 종류가 다르면 ReservationTypeMismatchException이 발생하고 기존 예약이 그대로 남는다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val oldSchedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val lessonSchedule = findSchedule(DayOfWeek.TUESDAY, ClassType.LESSON, LocalTime.of(19, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val reservation = persistActiveReservation(member, admin, oldSchedule, classDate)

        assertThatThrownBy {
            adminReservationService.changeByAdmin(
                admin.id!!,
                reservation.id!!,
                AdminChangeReservationRequest(lessonSchedule.id!!, classDate),
            )
        }.isInstanceOf(ReservationTypeMismatchException::class.java)

        val originalReservation = reservationRepository.findById(reservation.id!!).get()
        assertThat(originalReservation.status).isEqualTo(ReservationStatus.ACTIVE)
    }

    @Test
    fun `변경 대상이 정원 초과면 ReservationCapacityExceededException이 발생하고 기존 예약이 그대로 남으며 정원 검사를 건너뛰지 않는다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val oldSchedule = findSchedule(DayOfWeek.SATURDAY, ClassType.SESSION, LocalTime.of(9, 0))
        val newSchedule = findSchedule(DayOfWeek.SATURDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.SATURDAY)
        val reservation = persistActiveReservation(member, admin, oldSchedule, classDate, passRemainingAfterDeduction = "2.0")
        val passId = requireNotNull(reservation.pass.id)
        persistFullSession(newSchedule, classDate, reservedCount = requireNotNull(newSchedule.capacity))

        assertThatThrownBy {
            adminReservationService.changeByAdmin(
                admin.id!!,
                reservation.id!!,
                AdminChangeReservationRequest(newSchedule.id!!, classDate),
            )
        }.isInstanceOf(ReservationCapacityExceededException::class.java)

        val originalReservation = reservationRepository.findById(reservation.id!!).get()
        assertThat(originalReservation.status).isEqualTo(ReservationStatus.ACTIVE)

        val refreshedPass = passRepository.findById(passId).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))
    }

    // ---------- HTTP 계약 ----------

    @Nested
    inner class HttpContract {
        @Test
        fun `관리자 토큰으로 대리 취소하면 200과 CANCELED 상태가 온다`() {
            val member = persistMember()
            val admin = persistAdmin()
            val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
            val classDate = nextClassDate(DayOfWeek.TUESDAY)
            val reservation = persistActiveReservation(member, admin, schedule, classDate)
            val token = adminAccessToken()

            mockMvc
                .perform(
                    post("/api/admin/reservations/${reservation.id}/cancellation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refund":true}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("CANCELED"))
        }

        @Test
        fun `회원 토큰으로 대리 취소를 시도하면 403과 ACCESS_DENIED를 반환한다`() {
            val member = persistMember()
            val admin = persistAdmin()
            val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(13, 0))
            val classDate = nextClassDate(DayOfWeek.TUESDAY)
            val reservation = persistActiveReservation(member, admin, schedule, classDate)
            val token = memberAccessToken(member)

            mockMvc
                .perform(
                    post("/api/admin/reservations/${reservation.id}/cancellation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refund":true}"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
        }

        @Test
        fun `토큰 없이 대리 취소를 시도하면 401과 UNAUTHENTICATED를 반환한다`() {
            val member = persistMember()
            val admin = persistAdmin()
            val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))
            val classDate = nextClassDate(DayOfWeek.THURSDAY)
            val reservation = persistActiveReservation(member, admin, schedule, classDate)

            mockMvc
                .perform(
                    post("/api/admin/reservations/${reservation.id}/cancellation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refund":true}"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        }

        @Test
        fun `관리자 토큰으로 대리 변경하면 200이 온다`() {
            val member = persistMember()
            val admin = persistAdmin()
            val oldSchedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(13, 0))
            val newSchedule = findSchedule(DayOfWeek.FRIDAY, ClassType.SESSION, LocalTime.of(11, 0))
            val oldClassDate = nextClassDate(DayOfWeek.THURSDAY)
            val newClassDate = nextClassDate(DayOfWeek.FRIDAY)
            val reservation = persistActiveReservation(member, admin, oldSchedule, oldClassDate)
            val token = adminAccessToken()

            mockMvc
                .perform(
                    post("/api/admin/reservations/${reservation.id}/change")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"classScheduleId":${newSchedule.id},"classDate":"$newClassDate"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("ACTIVE"))
        }

        @Test
        fun `회원 토큰으로 대리 변경을 시도하면 403과 ACCESS_DENIED를 반환한다`() {
            val member = persistMember()
            val admin = persistAdmin()
            val oldSchedule = findSchedule(DayOfWeek.FRIDAY, ClassType.SESSION, LocalTime.of(13, 0))
            val newSchedule = findSchedule(DayOfWeek.SATURDAY, ClassType.SESSION, LocalTime.of(9, 0))
            val oldClassDate = nextClassDate(DayOfWeek.FRIDAY)
            val newClassDate = nextClassDate(DayOfWeek.SATURDAY)
            val reservation = persistActiveReservation(member, admin, oldSchedule, oldClassDate)
            val token = memberAccessToken(member)

            mockMvc
                .perform(
                    post("/api/admin/reservations/${reservation.id}/change")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"classScheduleId":${newSchedule.id},"classDate":"$newClassDate"}"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
        }

        @Test
        fun `토큰 없이 대리 변경을 시도하면 401과 UNAUTHENTICATED를 반환한다`() {
            val member = persistMember()
            val admin = persistAdmin()
            val oldSchedule = findSchedule(DayOfWeek.SATURDAY, ClassType.SESSION, LocalTime.of(11, 0))
            val newSchedule = findSchedule(DayOfWeek.SATURDAY, ClassType.SESSION, LocalTime.of(13, 0))
            val classDate = nextClassDate(DayOfWeek.SATURDAY)
            val reservation = persistActiveReservation(member, admin, oldSchedule, classDate)

            mockMvc
                .perform(
                    post("/api/admin/reservations/${reservation.id}/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"classScheduleId":${newSchedule.id},"classDate":"$classDate"}"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        }
    }

    // ---------- 픽스처 ----------

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun findSchedule(
        day: DayOfWeek,
        type: ClassType,
        time: LocalTime,
    ): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == day && it.classType == type && it.startTime == time }

    private fun findScheduleForDay(
        day: DayOfWeek,
        type: ClassType,
    ): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == day && it.classType == type }

    /** `nextClassDate`의 검색 범위(최대 2주+6일) 안에서 [dayOfWeek]가 오는 첫 날짜. */
    private fun nextClassDate(dayOfWeek: DayOfWeek): LocalDate {
        var date = LocalDate.now(clock).plusWeeks(2)
        while (date.dayOfWeek != dayOfWeek) {
            date = date.plusDays(1)
        }
        return date
    }

    private fun persistMember(): Member {
        val kakaoId = KAKAO_ID_BASE + fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "회원$kakaoId",
                phoneNumber = "010$kakaoId".take(20),
                status = MemberStatus.ACTIVE,
                kakaoId = kakaoId,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(
            Admin(
                name = "관리자$fixtureCounter",
                loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun adminAccessToken(): String {
        val admin = persistAdmin()
        return tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!).accessToken
    }

    private fun memberAccessToken(member: Member): String = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!).accessToken

    private fun persistPass(
        member: Member,
        admin: Admin,
        type: PassType,
        remaining: String,
        status: PassStatus,
    ): Pass {
        val isCanceled = status == PassStatus.CANCELED
        return passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = type,
                status = status,
                startDate = LocalDate.of(2020, 1, 1),
                endDate = LocalDate.of(2099, 12, 31),
                remainingCount = BigDecimal(remaining),
                canceledBy = if (isCanceled) admin else null,
                canceledAt = if (isCanceled) OffsetDateTime.now(clock) else null,
                cancelReason = if (isCanceled) "테스트 등록 취소" else null,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    /**
     * 같은 (schedule, classDate) 조합으로 여러 예약을 만드는 테스트가 있어 `uq_class_session`
     * 유니크 제약에 걸리지 않도록 이미 있으면 재사용한다. [reservedCount]는 새로 만들 때만
     * 적용된다 — `incrementReservedCountIfCapacityAvailable`(`@Modifying`)은 클래스에
     * `@Transactional`이 없는 이 테스트의 픽스처에서 직접 호출하면 트랜잭션이 없어
     * `TransactionRequiredException`이 나므로 쓰지 않는다.
     */
    private fun persistSession(
        schedule: ClassSchedule,
        classDate: LocalDate,
        reservedCount: Int = 0,
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
                    reservedCount = reservedCount,
                    status = ClassSessionStatus.SCHEDULED,
                    createdAt = OffsetDateTime.now(clock),
                ),
            )

    /** 정원 초과 재현용 — 실제 예약 대신 세션 카운터를 직접 정원까지 채운다. */
    private fun persistFullSession(
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

    /**
     * 활성 예약을 이미 1회 차감된 상태로 직접 구성한다 — `reserve()`를 거치지 않는 이유는
     * 관리자 경로(당일·과거·예약 창 무시)를 재현하려면 회원 경로의 `ReservationWindow` 제약을
     * 우회해야 하기 때문이다. [passRemainingAfterDeduction]은 "이 예약이 이미 1.0을 차감한 뒤"의
     * 잔여값이다 — 취소 시 복구되면 `+1.0`한 값과 비교한다.
     */
    private fun persistActiveReservation(
        member: Member,
        admin: Admin,
        schedule: ClassSchedule,
        classDate: LocalDate,
        passType: PassType = PassType.SESSION_PASS,
        passRemainingAfterDeduction: String = "2.0",
        passStatus: PassStatus = PassStatus.ACTIVE,
    ): Reservation {
        val pass = persistPass(member, admin, passType, passRemainingAfterDeduction, passStatus)
        val session = persistSession(schedule, classDate, reservedCount = 1)
        return reservationRepository.saveAndFlush(
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

    private companion object {
        const val KAKAO_ID_BASE = 9_500_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-resv-admincxl-"
        const val BASE_CLOCK = "2092-05-05T08:00:00+09:00"
        val RANGE_FROM: LocalDate = LocalDate.of(2092, 4, 25)
        val RANGE_TO: LocalDate = LocalDate.of(2092, 6, 5)
    }
}
