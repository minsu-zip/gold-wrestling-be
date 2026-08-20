package com.goldwrestling.schedule

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
import com.goldwrestling.reservation.Reservation
import com.goldwrestling.reservation.ReservationLedgerSupport
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.dto.SuspendClassSessionRequest
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.willReturn
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.TemporalAdjusters

/**
 * `AdminScheduleService.suspend`/`resume`(RESV-09, 04-14 Task 1·2)를 실제 PostgreSQL로 증명하는
 * 통합테스트. `AdminReservationCancellationTest`와 동일하게 클래스에 `@Transactional`을 붙이지
 * 않는다 — "휴강 처리가 실제로 커밋한 결과"(예약 N건 취소·이력·알림)를 봐야 하기 때문이다.
 *
 * 클록은 다른 04-13/04-14 통합테스트와 동일한 고정 값(`BASE_CLOCK`, 월요일)을 쓴다 — 회원 시간표
 * 조회 검증([ReservationWindow])이 "이번 주"를 판정하는 기준이 이 클록이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class ClassSessionSuspensionTest {
    @Autowired
    private lateinit var adminScheduleService: AdminScheduleService

    @Autowired
    private lateinit var scheduleService: ScheduleService

    @Autowired
    private lateinit var reservationLedgerSupport: ReservationLedgerSupport

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

    /**
     * 스파이인 이유는 `휴강 도중 회원이 먼저 취소한 건은 취소 건수에서 제외된다` 하나뿐이다 —
     * 그 테스트가 재현하려는 경합(스냅샷 조회와 취소 UPDATE 사이에 회원이 자가 취소)은 실제
     * 스레드로는 창이 너무 좁아 결정론적으로 만들 수 없어서, `cancelByAdminIfActive`가 0을
     * 반환하는 상황만 특정 예약 1건에 대해 주입한다. 나머지 테스트는 전부 실제 구현으로 위임된다.
     */
    @MockitoSpyBean
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

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
        // 휴강 복구 이력은 member_id가 항상 null이라(행위자가 관리자) pass_id 경유로 지운다
        // (AdminReservationCancellationTest의 cleanUp과 동일 관례).
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

    // ---------- Task 1: 휴강 처리 캐스케이드 ----------

    @Test
    fun `활성 예약 3건이 있는 수업을 휴강 처리하면 예약 전부가 취소되고 잔여가 복구되며 이력 3건과 알림 1건이 생긴다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val session = persistSession(schedule, classDate, reservedCount = 3)
        val reservations =
            (1..3).map {
                val member = persistMember()
                val pass = persistPass(member, admin, PassType.SESSION_PASS, "2.0", PassStatus.ACTIVE)
                persistReservation(member, session, schedule, classDate, pass)
            }

        val response =
            adminScheduleService.suspend(
                admin.id!!,
                SuspendClassSessionRequest(schedule.id!!, classDate, "공휴일 휴관"),
            )

        assertThat(response.status).isEqualTo(ClassSessionStatus.CANCELED)
        assertThat(response.canceledReservationCount).isEqualTo(3)
        assertThat(response.reservedCount).isEqualTo(0)
        assertThat(response.cancelReason).isEqualTo("공휴일 휴관")
        assertThat(response.canceledAt).isNotNull()

        val refreshedSession = classSessionRepository.findById(session.id!!).get()
        assertThat(refreshedSession.status).isEqualTo(ClassSessionStatus.CANCELED)
        assertThat(refreshedSession.reservedCount).isEqualTo(0)
        assertThat(refreshedSession.canceledByAdmin?.id).isEqualTo(admin.id)
        assertThat(refreshedSession.canceledAt).isNotNull()
        assertThat(refreshedSession.cancelReason).isEqualTo("공휴일 휴관")

        reservations.forEach { reservation ->
            val refreshed = reservationRepository.findById(reservation.id!!).get()
            assertThat(refreshed.status).isEqualTo(ReservationStatus.CANCELED)
            assertThat(refreshed.refunded).isTrue()
            assertThat(refreshed.canceledByAdmin?.id).isEqualTo(admin.id)

            val refreshedPass = passRepository.findById(reservation.pass.id!!).get()
            assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("3.0"))
        }

        val passIds = reservations.map { requireNotNull(it.pass.id) }
        val refundTransactions =
            passTransactionRepository.findAll().filter {
                it.reason == TransactionReason.CLASS_CANCELED_REFUND && it.pass.id in passIds
            }
        assertThat(refundTransactions).hasSize(3)
        assertThat(refundTransactions).allMatch { it.admin?.id == admin.id && it.member == null }

        val notifications = notificationRepository.findAll().filter { it.classSession?.id == session.id }
        assertThat(notifications).hasSize(1)
        assertThat(notifications.first().type).isEqualTo(NotificationType.CLASS_SESSION_SUSPENDED)
        assertThat(notifications.first().reservation).isNull()
        assertThat(notifications.first().message).contains("3")
    }

    @Test
    fun `휴강 도중 회원이 먼저 취소한 건은 취소 건수에서 제외된다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val session = persistSession(schedule, classDate, reservedCount = 3)
        val reservations =
            (1..3).map {
                val member = persistMember()
                val pass = persistPass(member, admin, PassType.SESSION_PASS, "2.0", PassStatus.ACTIVE)
                persistReservation(member, session, schedule, classDate, pass)
            }
        // 스냅샷 조회(④)에는 ACTIVE로 잡혔지만 취소 UPDATE(⑤) 시점엔 회원이 이미 취소해 0행이
        // 갱신되는 상황 — 이 건은 취소 건수에도, 복구·이력에도 들어가면 안 된다.
        val racedAway = reservations[1]
        willReturn(0)
            .given(reservationRepository)
            .cancelByAdminIfActive(eq(racedAway.id!!), anyArg(), anyBoolean(), anyArg())

        val response =
            adminScheduleService.suspend(
                admin.id!!,
                SuspendClassSessionRequest(schedule.id!!, classDate, "공휴일 휴관"),
            )

        // 스냅샷은 3건이었지만 실제 취소는 2건 — 응답과 알림 문구가 모두 2를 말해야 한다.
        assertThat(response.canceledReservationCount).isEqualTo(2)

        val notifications = notificationRepository.findAll().filter { it.classSession?.id == session.id }
        assertThat(notifications).hasSize(1)
        assertThat(notifications.first().message).contains("2")
        assertThat(notifications.first().message).doesNotContain("3")

        // 건너뛴 건은 복구되지 않았고(잔여 그대로), 이력도 남지 않았다 — 이중 복구 방어가 유지된다.
        val skippedPass = passRepository.findById(racedAway.pass.id!!).get()
        assertThat(skippedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))

        listOf(reservations[0], reservations[2]).forEach { reservation ->
            val refreshedPass = passRepository.findById(reservation.pass.id!!).get()
            assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("3.0"))
        }

        val passIds = reservations.map { requireNotNull(it.pass.id) }
        val refundTransactions =
            passTransactionRepository.findAll().filter {
                it.reason == TransactionReason.CLASS_CANCELED_REFUND && it.pass.id in passIds
            }
        assertThat(refundTransactions).hasSize(2)
        assertThat(refundTransactions.map { it.pass.id }).doesNotContain(racedAway.pass.id)
    }

    @Test
    fun `휴강 복구 이력은 CANCEL_REFUND가 아니라 CLASS_CANCELED_REFUND로 남는다`() {
        val admin = persistAdmin()
        val member = persistMember()
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val classDate = nextClassDate(DayOfWeek.THURSDAY)
        val session = persistSession(schedule, classDate, reservedCount = 1)
        val pass = persistPass(member, admin, PassType.SESSION_PASS, "1.0", PassStatus.ACTIVE)
        persistReservation(member, session, schedule, classDate, pass)

        adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "긴급 휴강"))

        val transactionsForPass = passTransactionRepository.findAll().filter { it.pass.id == pass.id }
        assertThat(transactionsForPass).hasSize(1)
        assertThat(transactionsForPass.first().reason).isEqualTo(TransactionReason.CLASS_CANCELED_REFUND)
        assertThat(transactionsForPass.map { it.reason }).doesNotContain(TransactionReason.CANCEL_REFUND)
    }

    @Test
    fun `예약이 없는 수업을 휴강해도 세션은 CANCELED되고 알림 1건이 생기며 PassTransaction은 생기지 않는다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.FRIDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.FRIDAY)
        val session = persistSession(schedule, classDate, reservedCount = 0)

        val response =
            adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "우천으로 인한 휴강"))

        assertThat(response.status).isEqualTo(ClassSessionStatus.CANCELED)
        assertThat(response.canceledReservationCount).isEqualTo(0)

        val notifications = notificationRepository.findAll().filter { it.classSession?.id == session.id }
        assertThat(notifications).hasSize(1)
        assertThat(notifications.first().message).contains("0")

        val transactions =
            passTransactionRepository.findAll().filter {
                it.admin?.id == admin.id && it.reason == TransactionReason.CLASS_CANCELED_REFUND
            }
        assertThat(transactions).isEmpty()
    }

    @Test
    fun `세션 행이 아직 없는 타임도 휴강 처리할 수 있다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.SATURDAY, ClassType.SESSION, LocalTime.of(9, 0))
        val classDate = nextClassDate(DayOfWeek.SATURDAY)
        assertThat(classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)).isNull()

        val response =
            adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "임시 휴강"))

        assertThat(response.status).isEqualTo(ClassSessionStatus.CANCELED)
        val created = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)
        assertThat(created).isNotNull()
        assertThat(created!!.status).isEqualTo(ClassSessionStatus.CANCELED)
    }

    @Test
    fun `이미 휴강된 수업을 다시 휴강 처리하면 ClassSessionCanceledException이 발생하고 아무 것도 변하지 않는다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.SATURDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.SATURDAY)
        adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "1차 휴강"))
        val sessionAfterFirst = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)!!

        assertThatThrownBy {
            adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "2차 휴강 시도"))
        }.isInstanceOf(ClassSessionCanceledException::class.java)

        val sessionAfterSecond = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)!!
        assertThat(sessionAfterSecond.cancelReason).isEqualTo(sessionAfterFirst.cancelReason)
        assertThat(sessionAfterSecond.canceledAt).isEqualTo(sessionAfterFirst.canceledAt)
    }

    @Test
    fun `취소된 예약 중 차감 이용권이 등록 취소 상태인 건은 예약은 취소되지만 복구·이력이 생기지 않는다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        val session = persistSession(schedule, classDate, reservedCount = 2)
        val activeMember = persistMember()
        val activePass = persistPass(activeMember, admin, PassType.SESSION_PASS, "1.5", PassStatus.ACTIVE)
        val activeReservation = persistReservation(activeMember, session, schedule, classDate, activePass)
        val canceledMember = persistMember()
        val canceledPass = persistPass(canceledMember, admin, PassType.SESSION_PASS, "0", PassStatus.CANCELED)
        val canceledReservation = persistReservation(canceledMember, session, schedule, classDate, canceledPass)

        adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "정전"))

        val refreshedActive = reservationRepository.findById(activeReservation.id!!).get()
        assertThat(refreshedActive.status).isEqualTo(ReservationStatus.CANCELED)
        val refreshedActivePass = passRepository.findById(activePass.id!!).get()
        assertThat(refreshedActivePass.remainingCount).isEqualByComparingTo(BigDecimal("2.5"))

        val refreshedCanceledReservation = reservationRepository.findById(canceledReservation.id!!).get()
        assertThat(refreshedCanceledReservation.status).isEqualTo(ReservationStatus.CANCELED)
        val refreshedCanceledPass = passRepository.findById(canceledPass.id!!).get()
        assertThat(refreshedCanceledPass.remainingCount).isEqualByComparingTo(BigDecimal("0"))

        val canceledPassTransactions =
            passTransactionRepository.findAll().filter {
                it.pass.id == canceledPass.id && it.reason == TransactionReason.CLASS_CANCELED_REFUND
            }
        assertThat(canceledPassTransactions).isEmpty()
    }

    @Test
    fun `휴강 후 그 타임에 새로 예약을 시도하면 ClassSessionCanceledException이 발생한다`() {
        val admin = persistAdmin()
        val member = persistMember()
        val schedule = findSchedule(DayOfWeek.WEDNESDAY, ClassType.LESSON, LocalTime.of(19, 0))
        val classDate = nextClassDate(DayOfWeek.WEDNESDAY)
        adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "코치 부재"))

        assertThatThrownBy {
            reservationLedgerSupport.createReservation(
                memberId = member.id!!,
                schedule = schedule,
                classDate = classDate,
                enforceWindow = false,
                admin = admin,
            )
        }.isInstanceOf(ClassSessionCanceledException::class.java)
    }

    @Test
    fun `휴강 후 회원 시간표 조회에서 그 셀이 suspended true, bookable false로 표시된다`() {
        val admin = persistAdmin()
        val member = persistMember()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = LocalDate.now(clock).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))
        adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "우천"))

        val weeklySchedule = scheduleService.getWeeklySchedule(member.id!!, null)
        val cell =
            weeklySchedule.days
                .first { it.date == classDate }
                .cells
                .first { it.classScheduleId == schedule.id }

        assertThat(cell.suspended).isTrue()
        assertThat(cell.bookable).isFalse()
    }

    @Test
    fun `존재하지 않는 시간표id로 휴강을 시도하면 ClassScheduleNotFoundException이 발생한다`() {
        val admin = persistAdmin()

        assertThatThrownBy {
            adminScheduleService.suspend(
                admin.id!!,
                SuspendClassSessionRequest(999_999_999L, LocalDate.now(clock), "사유"),
            )
        }.isInstanceOf(ClassScheduleNotFoundException::class.java)
    }

    @Test
    fun `요일이 다른 날짜로 휴강을 시도하면 ClassScheduleNotFoundException이 발생한다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val wrongDayDate = nextClassDate(DayOfWeek.WEDNESDAY)

        assertThatThrownBy {
            adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, wrongDayDate, "사유"))
        }.isInstanceOf(ClassScheduleNotFoundException::class.java)
    }

    // ---------- Task 2: 휴강 해제 + 엔드포인트 ----------

    @Test
    fun `휴강된 세션을 해제하면 상태가 SCHEDULED로 바뀌고 취소 메타데이터가 모두 null이 된다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.THURSDAY)
        adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "휴강 사유"))
        val sessionId = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)!!.id!!

        val response = adminScheduleService.resume(admin.id!!, sessionId)

        assertThat(response.status).isEqualTo(ClassSessionStatus.SCHEDULED)
        assertThat(response.cancelReason).isNull()
        assertThat(response.canceledAt).isNull()

        val refreshedSession = classSessionRepository.findById(sessionId).get()
        assertThat(refreshedSession.status).isEqualTo(ClassSessionStatus.SCHEDULED)
        assertThat(refreshedSession.cancelReason).isNull()
        assertThat(refreshedSession.canceledAt).isNull()
        assertThat(refreshedSession.canceledByAdmin).isNull()
    }

    @Test
    fun `휴강 해제 후 그 타임에 새 예약이 성공한다`() {
        val admin = persistAdmin()
        val member = persistMember()
        val schedule = findSchedule(DayOfWeek.FRIDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val classDate = nextClassDate(DayOfWeek.FRIDAY)
        adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "휴강 사유"))
        val sessionId = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)!!.id!!
        adminScheduleService.resume(admin.id!!, sessionId)
        persistPass(member, admin, PassType.SESSION_PASS, "3.0", PassStatus.ACTIVE)

        // ReservationLedgerSupport는 자체 트랜잭션 경계를 열지 않는 헬퍼라(호출부의 @Transactional에
        // 편승) 서비스 계층을 거치지 않고 직접 호출하는 이 테스트에서는 TransactionTemplate으로
        // 명시적 트랜잭션을 열어준다 — 그렇지 않으면 내부의 @Modifying 조건부 UPDATE가
        // TransactionRequiredException으로 실패한다.
        val newReservation =
            TransactionTemplate(transactionManager).execute {
                reservationLedgerSupport.createReservation(
                    memberId = member.id!!,
                    schedule = schedule,
                    classDate = classDate,
                    enforceWindow = false,
                    admin = admin,
                )
            }

        assertThat(newReservation!!.status).isEqualTo(ReservationStatus.ACTIVE)
    }

    @Test
    fun `휴강 해제 후에도 휴강으로 취소됐던 예약은 CANCELED 그대로이고 reserved_count는 0이다`() {
        val admin = persistAdmin()
        val member = persistMember()
        val schedule = findSchedule(DayOfWeek.SATURDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val classDate = nextClassDate(DayOfWeek.SATURDAY)
        val session = persistSession(schedule, classDate, reservedCount = 1)
        val pass = persistPass(member, admin, PassType.SESSION_PASS, "1.0", PassStatus.ACTIVE)
        val reservation = persistReservation(member, session, schedule, classDate, pass)
        adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "휴강 사유"))

        adminScheduleService.resume(admin.id!!, session.id!!)

        val refreshedReservation = reservationRepository.findById(reservation.id!!).get()
        assertThat(refreshedReservation.status).isEqualTo(ReservationStatus.CANCELED)
        val refreshedSession = classSessionRepository.findById(session.id!!).get()
        assertThat(refreshedSession.reservedCount).isEqualTo(0)
    }

    @Test
    fun `휴강 상태가 아닌 세션을 해제하려 하면 ClassSessionNotCanceledException이 발생한다`() {
        val admin = persistAdmin()
        val schedule = findSchedule(DayOfWeek.SUNDAY, ClassType.SESSION, LocalTime.of(9, 0))
        val classDate = nextClassDate(DayOfWeek.SUNDAY)
        val session = persistSession(schedule, classDate, reservedCount = 0)

        assertThatThrownBy {
            adminScheduleService.resume(admin.id!!, session.id!!)
        }.isInstanceOf(ClassSessionNotCanceledException::class.java)
    }

    @Nested
    inner class HttpContract {
        @Test
        fun `관리자 토큰으로 휴강 처리 요청하면 200과 CANCELED 상태가 온다`() {
            val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
            val classDate = nextClassDate(DayOfWeek.TUESDAY)
            val token = adminAccessToken()

            mockMvc
                .perform(
                    post("/api/admin/class-sessions/suspension")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate","reason":"공휴일"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("CANCELED"))
        }

        @Test
        fun `관리자 토큰으로 휴강 해제 요청하면 200과 SCHEDULED 상태가 온다`() {
            val admin = persistAdmin()
            val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))
            val classDate = nextClassDate(DayOfWeek.THURSDAY)
            adminScheduleService.suspend(admin.id!!, SuspendClassSessionRequest(schedule.id!!, classDate, "휴강 사유"))
            val sessionId = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)!!.id!!
            val token = adminAccessToken()

            mockMvc
                .perform(
                    post("/api/admin/class-sessions/$sessionId/resumption")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
        }

        @Test
        fun `회원 토큰으로 휴강 처리를 시도하면 403과 ACCESS_DENIED를 반환한다`() {
            val member = persistMember()
            val schedule = findSchedule(DayOfWeek.FRIDAY, ClassType.SESSION, LocalTime.of(11, 0))
            val classDate = nextClassDate(DayOfWeek.FRIDAY)
            val token = memberAccessToken(member)

            mockMvc
                .perform(
                    post("/api/admin/class-sessions/suspension")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate","reason":"공휴일"}"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
        }

        @Test
        fun `토큰 없이 휴강 처리를 시도하면 401과 UNAUTHENTICATED를 반환한다`() {
            val schedule = findSchedule(DayOfWeek.SATURDAY, ClassType.SESSION, LocalTime.of(9, 0))
            val classDate = nextClassDate(DayOfWeek.SATURDAY)

            mockMvc
                .perform(
                    post("/api/admin/class-sessions/suspension")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"classScheduleId":${schedule.id},"classDate":"$classDate","reason":"공휴일"}"""),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        }
    }

    // ---------- 픽스처 ----------

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    /**
     * Kotlin + 순수 Mockito 조합에서 non-null 파라미터 자리에 `any()`를 그대로 넘기면, 매처가
     * 등록되기는 하지만 반환값이 null이라 코틀린 컴파일러가 삽입한 null 검사에 먼저 걸려
     * `NullPointerException: any(...) must not be null`이 난다. 게다가 그 예외로 스터빙이 중단되면
     * 등록만 된 매처가 다음 호출에 잘못 적용돼 무관한 테스트까지 깨진다.
     *
     * 타입 파라미터를 거쳐 검사를 우회한다 — 실제로 매칭에 쓰이는 것은 위에서 등록된 매처이고
     * 이 반환값 자체는 사용되지 않는다.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyArg(): T {
        ArgumentMatchers.any<T>()
        return null as T
    }

    private fun findSchedule(
        day: DayOfWeek,
        type: ClassType,
        time: LocalTime,
    ): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == day && it.classType == type && it.startTime == time }

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
     * 같은 (schedule, classDate) 조합으로 여러 번 조회하는 테스트가 있어 `uq_class_session` 유니크
     * 제약에 걸리지 않도록 이미 있으면 재사용한다. [reservedCount]는 새로 만들 때만 적용된다 —
     * `resetReservedCount`/`suspendIfScheduled`(`@Modifying`)는 클래스에 `@Transactional`이 없는
     * 이 테스트의 픽스처에서 직접 호출하면 트랜잭션이 없어 `TransactionRequiredException`이
     * 나므로 쓰지 않는다(`AdminReservationCancellationTest`의 `persistSession`과 동일 관례).
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

    private fun persistReservation(
        member: Member,
        session: ClassSession,
        schedule: ClassSchedule,
        classDate: LocalDate,
        pass: Pass,
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

    private companion object {
        const val KAKAO_ID_BASE = 9_700_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-resv-suspend-"
        const val BASE_CLOCK = "2092-05-05T08:00:00+09:00"
        val RANGE_FROM: LocalDate = LocalDate.of(2092, 4, 20)
        val RANGE_TO: LocalDate = LocalDate.of(2092, 6, 10)
    }
}
