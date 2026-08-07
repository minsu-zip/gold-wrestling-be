package com.goldwrestling.reservation

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.notification.NotificationRepository
import com.goldwrestling.notification.NotificationType
import com.goldwrestling.pass.InsufficientPassCountException
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.reservation.dto.ReserveRequest
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSessionCanceledException
import com.goldwrestling.schedule.ClassSessionNotReservableException
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassSessionStatus
import com.goldwrestling.schedule.ClassType
import com.goldwrestling.schedule.ReservationWindowClosedException
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * `MemberReservationService.reserve`의 성공·8가지 거부 경로를 실제 PostgreSQL(Testcontainers)로
 * 증명하는 통합테스트(04-07-PLAN.md Task 2).
 *
 * **클래스에 `@Transactional`을 붙이지 않는다.** `reserve`는 자체 `@Transactional` 경계를 여는
 * 서비스 메서드다 — 테스트 메서드까지 `@Transactional`로 감싸면 `reserve` 호출이 테스트의 트랜잭션에
 * 참여(REQUIRED)해, 실패 시 Spring이 표시하는 rollback-only 마킹이 **테스트가 검증을 마칠 때까지
 * 실제 DB에 반영되지 않는다** — "잔여·예약 인원이 요청 전과 동일하다"는 이 플랜의 핵심 단언을
 * 검증할 수 없게 된다. 대신 `reserve` 호출마다 실제로 커밋/롤백되는 독립 트랜잭션을 쓰고,
 * `@AfterEach`에서 이 테스트가 만든 행을 직접 지운다(`ClassSessionConcurrencyTest`와 동일 관례).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class MemberReservationServiceTest {
    @Autowired
    private lateinit var memberReservationService: MemberReservationService

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
        jdbcClient.sql("delete from notification where class_date between :from and :to")
            .param("from", RANGE_FROM).param("to", RANGE_TO).update()
        jdbcClient.sql("delete from reservation where class_date between :from and :to")
            .param("from", RANGE_FROM).param("to", RANGE_TO).update()
        jdbcClient.sql("delete from pass_transaction where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE).update()
        jdbcClient.sql("delete from pass where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE).update()
        jdbcClient.sql("delete from class_session where class_date between :from and :to")
            .param("from", RANGE_FROM).param("to", RANGE_TO).update()
        jdbcClient.sql("delete from member where kakao_id >= :base")
            .param("base", KAKAO_ID_BASE).update()
        jdbcClient.sql("delete from admin where login_id like :prefix")
            .param("prefix", "$ADMIN_LOGIN_PREFIX%").update()
    }

    @Test
    fun `예약제 수업을 예약하면 SESSION_PASS 잔여가 정확히 1 줄고 예약이 생성된다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.SESSION_PASS, "3.0")
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))

        val response = memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, TUESDAY_THIS_WEEK))

        assertThat(response.classType).isEqualTo(ClassType.SESSION)
        assertThat(response.status).isEqualTo(ReservationStatus.ACTIVE)
        assertThat(response.passId).isEqualTo(pass.id)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))

        val session = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, TUESDAY_THIS_WEEK)!!
        assertThat(session.reservedCount).isEqualTo(1)

        val reservations = reservationRepository.findAllByClassSessionIdAndStatus(session.id!!, ReservationStatus.ACTIVE)
        assertThat(reservations).hasSize(1)

        val notifications = notificationRepository.findAll().filter { it.reservation?.id == reservations.first().id }
        assertThat(notifications).hasSize(1)
        assertThat(notifications.first().type).isEqualTo(NotificationType.RESERVATION_CREATED)
    }

    @Test
    fun `1대1 레슨을 예약하면 LESSON_PASS 잔여가 정확히 1 준다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.LESSON_PASS, "2.0")
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.LESSON, LocalTime.of(21, 0))

        val response = memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, TUESDAY_THIS_WEEK))

        assertThat(response.classType).isEqualTo(ClassType.LESSON)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("1.0"))

        val session = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, TUESDAY_THIS_WEEK)!!
        assertThat(session.reservedCount).isEqualTo(1)
    }

    @Test
    fun `저녁반 타임은 예약할 수 없고 세션도 생성되지 않는다`() {
        val member = persistMember()
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.EVENING, LocalTime.of(19, 0))

        assertThatThrownBy { memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, TUESDAY_THIS_WEEK)) }
            .isInstanceOf(ClassSessionNotReservableException::class.java)

        assertThat(classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, TUESDAY_THIS_WEEK)).isNull()
    }

    @Test
    fun `휴강된 수업은 예약할 수 없고 잔여-예약인원이 요청 전과 동일하다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.LESSON_PASS, "2.0")
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.LESSON, LocalTime.of(19, 0))
        val canceledSession = persistCanceledSession(schedule, TUESDAY_THIS_WEEK)

        assertThatThrownBy { memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, TUESDAY_THIS_WEEK)) }
            .isInstanceOf(ClassSessionCanceledException::class.java)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))

        val refreshedSession = classSessionRepository.findById(canceledSession.id!!).get()
        assertThat(refreshedSession.reservedCount).isEqualTo(0)

        assertThat(reservationRepository.existsByPassIdAndStatus(pass.id!!, ReservationStatus.ACTIVE)).isFalse()
    }

    @Test
    fun `다음 주 수업은 예약 창 밖이라 예약할 수 없다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.LESSON_PASS, "2.0")
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.LESSON, LocalTime.of(11, 0))

        assertThatThrownBy { memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, TUESDAY_NEXT_WEEK)) }
            .isInstanceOf(ReservationWindowClosedException::class.java)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, TUESDAY_NEXT_WEEK)).isNull()
    }

    @Test
    fun `이미 시작된 수업은 예약할 수 없다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.SESSION_PASS, "2.0")
        val schedule = scheduleOf(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(13, 0))
        (clock as MutableTestClock).setTo(OffsetDateTime.parse("2090-03-09T14:00:00+09:00").toInstant())

        assertThatThrownBy { memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, THURSDAY_THIS_WEEK)) }
            .isInstanceOf(ReservationWindowClosedException::class.java)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, THURSDAY_THIS_WEEK)).isNull()
    }

    @Test
    fun `같은 회원이 같은 날짜-시각에 두 번째 예약을 시도하면 거부되고 두 번째 이용권은 차감되지 않는다`() {
        val member = persistMember()
        persistPass(member, PassType.SESSION_PASS, "2.0")
        val lessonPass = persistPass(member, PassType.LESSON_PASS, "2.0")
        val sessionSchedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val lessonSchedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.LESSON, LocalTime.of(13, 0))

        memberReservationService.reserve(member.id!!, ReserveRequest(sessionSchedule.id!!, TUESDAY_THIS_WEEK))

        assertThatThrownBy {
            memberReservationService.reserve(member.id!!, ReserveRequest(lessonSchedule.id!!, TUESDAY_THIS_WEEK))
        }.isInstanceOf(DuplicateReservationException::class.java)

        val refreshedLessonPass = passRepository.findById(lessonPass.id!!).get()
        assertThat(refreshedLessonPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))
    }

    @Test
    fun `정원이 찬 1대1 레슨은 다음 예약을 거부하고 잔여-예약인원이 그대로다`() {
        val memberA = persistMember()
        persistPass(memberA, PassType.LESSON_PASS, "2.0")
        val memberB = persistMember()
        val passB = persistPass(memberB, PassType.LESSON_PASS, "2.0")
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.LESSON, LocalTime.of(11, 0))

        memberReservationService.reserve(memberA.id!!, ReserveRequest(schedule.id!!, TUESDAY_THIS_WEEK))

        assertThatThrownBy { memberReservationService.reserve(memberB.id!!, ReserveRequest(schedule.id!!, TUESDAY_THIS_WEEK)) }
            .isInstanceOf(ReservationCapacityExceededException::class.java)

        val session = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, TUESDAY_THIS_WEEK)!!
        assertThat(session.reservedCount).isEqualTo(1)

        val refreshedPassB = passRepository.findById(passB.id!!).get()
        assertThat(refreshedPassB.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))

        assertThat(reservationRepository.findAllByClassSessionIdAndStatus(session.id!!, ReservationStatus.ACTIVE)).hasSize(1)
    }

    @Test
    fun `잔여 0-5회 이용권으로는 1회짜리 예약제 수업을 예약할 수 없다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.SESSION_PASS, "0.5")
        val schedule = scheduleOf(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))

        assertThatThrownBy { memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, THURSDAY_THIS_WEEK)) }
            .isInstanceOf(InsufficientPassCountException::class.java)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("0.5"))
        assertThat(classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, THURSDAY_THIS_WEEK)).isNull()
    }

    @Test
    fun `예약 성공 시 PassTransaction amount는 음수 1-0이고 회원이 주체다`() {
        val member = persistMember()
        persistPass(member, PassType.LESSON_PASS, "3.0")
        val schedule = scheduleOf(DayOfWeek.THURSDAY, ClassType.LESSON, LocalTime.of(11, 0))

        val response = memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, THURSDAY_THIS_WEEK))

        val transactions =
            passTransactionRepository
                .findAll()
                .filter { it.pass.id == response.passId && it.reason == TransactionReason.RESERVE }
        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().amount).isEqualByComparingTo(BigDecimal("-1.0"))
        assertThat(transactions.first().member?.id).isEqualTo(member.id)
        assertThat(transactions.first().admin).isNull()
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun scheduleOf(
        day: DayOfWeek,
        type: ClassType,
        time: LocalTime,
    ): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == day && it.classType == type && it.startTime == time }

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
                name = "관리자",
                loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
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
                startDate = LocalDate.of(2089, 1, 1),
                endDate = LocalDate.of(2099, 12, 31),
                remainingCount = BigDecimal(remaining),
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    /** 휴강 케이스 전용 — 실제 휴강 처리 경로(04-14)를 거치지 않고 상태만 직접 만든다. */
    private fun persistCanceledSession(
        schedule: ClassSchedule,
        date: LocalDate,
    ): com.goldwrestling.schedule.ClassSession {
        val admin = persistAdmin()
        val now = OffsetDateTime.now(clock)
        return classSessionRepository.saveAndFlush(
            com.goldwrestling.schedule.ClassSession(
                classSchedule = schedule,
                classDate = date,
                classType = schedule.classType,
                startTime = schedule.startTime,
                endTime = schedule.endTime,
                capacity = schedule.capacity,
                reservedCount = 0,
                status = ClassSessionStatus.CANCELED,
                canceledAt = now,
                cancelReason = "테스트 휴강",
                canceledByAdmin = admin,
                createdAt = now,
            ),
        )
    }

    private companion object {
        const val KAKAO_ID_BASE = 9_300_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-resv-svc-"
        const val BASE_CLOCK = "2090-03-07T08:00:00+09:00"
        val RANGE_FROM: LocalDate = LocalDate.of(2090, 3, 1)
        val RANGE_TO: LocalDate = LocalDate.of(2090, 3, 21)
        val TUESDAY_THIS_WEEK: LocalDate = LocalDate.of(2090, 3, 7)
        val THURSDAY_THIS_WEEK: LocalDate = LocalDate.of(2090, 3, 9)
        val TUESDAY_NEXT_WEEK: LocalDate = LocalDate.of(2090, 3, 14)
    }
}
