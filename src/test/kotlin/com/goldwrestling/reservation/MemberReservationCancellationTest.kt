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
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.reservation.dto.ChangeReservationRequest
import com.goldwrestling.reservation.dto.ReserveRequest
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSession
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
 * `MemberReservationService.cancel`/`change`의 원장 불변식·D-090(변경 원자성)·D-091(등록 취소
 * 이용권 방어, Pitfall 2)을 실제 PostgreSQL로 증명하는 통합테스트(04-10 Task 1).
 *
 * `MemberReservationServiceTest`와 동일하게 클래스에 `@Transactional`을 붙이지 않는다 —
 * `cancel`/`change`가 각자 여는 트랜잭션의 실제 커밋/롤백 결과(잔여·예약 상태가 요청 전과
 * 동일한지)를 검증해야 하기 때문이다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class MemberReservationCancellationTest {
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
        jdbcClient
            .sql("delete from pass_transaction where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
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
            .sql("delete from member where kakao_id >= :base")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from admin where login_id like :prefix")
            .param("prefix", "$ADMIN_LOGIN_PREFIX%")
            .update()
    }

    // ---------- 취소 ----------

    @Test
    fun `내일 수업 예약을 취소하면 잔여가 즉시 복구되고 CANCEL_REFUND 이력·알림이 남는다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.SESSION_PASS, "3.0")
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val reservation = memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, TUESDAY_THIS_WEEK))

        val response = memberReservationService.cancel(member.id!!, reservation.id)

        assertThat(response.status).isEqualTo(ReservationStatus.CANCELED)

        val refreshedReservation = reservationRepository.findById(reservation.id).get()
        assertThat(refreshedReservation.status).isEqualTo(ReservationStatus.CANCELED)
        assertThat(refreshedReservation.refunded).isTrue()
        assertThat(refreshedReservation.canceledByMember?.id).isEqualTo(member.id)

        val session = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, TUESDAY_THIS_WEEK)!!
        assertThat(session.reservedCount).isEqualTo(0)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("3.0"))

        val refundTransactions =
            passTransactionRepository
                .findAll()
                .filter { it.pass.id == pass.id && it.reason == TransactionReason.CANCEL_REFUND }
        assertThat(refundTransactions).hasSize(1)
        assertThat(refundTransactions.first().amount).isEqualByComparingTo(BigDecimal("1.0"))
        assertThat(refundTransactions.first().member?.id).isEqualTo(member.id)
        assertThat(refundTransactions.first().admin).isNull()

        val notifications = notificationRepository.findAll().filter { it.reservation?.id == reservation.id }
        assertThat(notifications.map { it.type }).contains(NotificationType.RESERVATION_CANCELED_BY_MEMBER)
    }

    @Test
    fun `당일 수업 예약을 취소하면 거부되고 잔여-예약인원-알림이 그대로다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.LESSON_PASS, "2.0")
        val schedule = scheduleOf(DayOfWeek.MONDAY, ClassType.LESSON, LocalTime.of(19, 0))
        val reservation = memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, MONDAY_THIS_WEEK))

        assertThatThrownBy { memberReservationService.cancel(member.id!!, reservation.id) }
            .isInstanceOf(SameDayModificationNotAllowedException::class.java)

        val refreshedReservation = reservationRepository.findById(reservation.id).get()
        assertThat(refreshedReservation.status).isEqualTo(ReservationStatus.ACTIVE)

        val session = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, MONDAY_THIS_WEEK)!!
        assertThat(session.reservedCount).isEqualTo(1)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("1.0"))

        val notifications = notificationRepository.findAll().filter { it.reservation?.id == reservation.id }
        assertThat(notifications.map { it.type }).doesNotContain(NotificationType.RESERVATION_CANCELED_BY_MEMBER)
    }

    @Test
    fun `이미 취소된 예약을 다시 취소하면 ReservationAlreadyCanceledException이 발생한다`() {
        val member = persistMember()
        persistPass(member, PassType.SESSION_PASS, "2.0")
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val reservation = memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, TUESDAY_THIS_WEEK))
        memberReservationService.cancel(member.id!!, reservation.id)

        assertThatThrownBy { memberReservationService.cancel(member.id!!, reservation.id) }
            .isInstanceOf(ReservationAlreadyCanceledException::class.java)
    }

    @Test
    fun `다른 회원의 예약 id로 취소를 시도하면 ReservationNotFoundException이 발생하고 예약은 그대로다`() {
        val owner = persistMember()
        val stranger = persistMember()
        persistPass(owner, PassType.SESSION_PASS, "2.0")
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val reservation = memberReservationService.reserve(owner.id!!, ReserveRequest(schedule.id!!, TUESDAY_THIS_WEEK))

        assertThatThrownBy { memberReservationService.cancel(stranger.id!!, reservation.id) }
            .isInstanceOf(ReservationNotFoundException::class.java)

        val refreshedReservation = reservationRepository.findById(reservation.id).get()
        assertThat(refreshedReservation.status).isEqualTo(ReservationStatus.ACTIVE)
    }

    @Test
    fun `등록 취소된 이용권으로 잡힌 예약을 취소하면 잔여가 그대로이고 CANCEL_REFUND 이력이 생기지 않는다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.LESSON_PASS, "3.0")
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.LESSON, LocalTime.of(11, 0))
        val reservation = memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, TUESDAY_THIS_WEEK))

        // D-089 정상 운영에서는 활성 예약이 있으면 등록 취소가 거부되지만, 이 테스트는 그 방어가
        // 뚫렸을 때(예: 휴강 등 다른 경로)를 대비한 방어 분기(D-091 "그래도 방어적으로 처리한다")를
        // 검증하기 위해 DB 상태를 직접 등록 취소로 만든다.
        val canceledPass = passRepository.findById(pass.id!!).get()
        canceledPass.status = PassStatus.CANCELED
        passRepository.saveAndFlush(canceledPass)

        memberReservationService.cancel(member.id!!, reservation.id)

        val refreshedReservation = reservationRepository.findById(reservation.id).get()
        assertThat(refreshedReservation.status).isEqualTo(ReservationStatus.CANCELED)

        val session = classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, TUESDAY_THIS_WEEK)!!
        assertThat(session.reservedCount).isEqualTo(0)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))

        val refundTransactions =
            passTransactionRepository
                .findAll()
                .filter { it.pass.id == pass.id && it.reason == TransactionReason.CANCEL_REFUND }
        assertThat(refundTransactions).isEmpty()
    }

    @Test
    fun `유효기간이 지난 이용권도 취소 시 잔여가 복구된다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.SESSION_PASS, "2.0")
        val schedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val reservation = memberReservationService.reserve(member.id!!, ReserveRequest(schedule.id!!, TUESDAY_THIS_WEEK))

        val expiredPass = passRepository.findById(pass.id!!).get()
        expiredPass.endDate = TUESDAY_THIS_WEEK.minusDays(1)
        passRepository.saveAndFlush(expiredPass)

        memberReservationService.cancel(member.id!!, reservation.id)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.status).isEqualTo(PassStatus.ACTIVE)
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))

        val refundTransactions =
            passTransactionRepository
                .findAll()
                .filter { it.pass.id == pass.id && it.reason == TransactionReason.CANCEL_REFUND }
        assertThat(refundTransactions).hasSize(1)
    }

    // ---------- 변경 ----------

    @Test
    fun `다른 타임으로 변경하면 기존 예약은 취소되고 새 예약이 생성되며 이력 2건과 알림 1건이 남는다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.SESSION_PASS, "3.0")
        val oldSchedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val newSchedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val original = memberReservationService.reserve(member.id!!, ReserveRequest(oldSchedule.id!!, TUESDAY_THIS_WEEK))

        val transactionIdsBeforeChange = passTransactionRepository.findAll().map { it.id }.toSet()
        val notificationIdsBeforeChange = notificationRepository.findAll().map { it.id }.toSet()

        val changed =
            memberReservationService.change(
                member.id!!,
                original.id,
                ChangeReservationRequest(newSchedule.id!!, TUESDAY_THIS_WEEK),
            )

        assertThat(changed.status).isEqualTo(ReservationStatus.ACTIVE)
        assertThat(changed.startTime).isEqualTo(LocalTime.of(13, 0))
        assertThat(changed.id).isNotEqualTo(original.id)

        val originalReservation = reservationRepository.findById(original.id).get()
        assertThat(originalReservation.status).isEqualTo(ReservationStatus.CANCELED)

        val oldSession = classSessionRepository.findByClassScheduleIdAndClassDate(oldSchedule.id!!, TUESDAY_THIS_WEEK)!!
        assertThat(oldSession.reservedCount).isEqualTo(0)
        val newSession = classSessionRepository.findByClassScheduleIdAndClassDate(newSchedule.id!!, TUESDAY_THIS_WEEK)!!
        assertThat(newSession.reservedCount).isEqualTo(1)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))

        val newTransactions = passTransactionRepository.findAll().filterNot { it.id in transactionIdsBeforeChange }
        assertThat(newTransactions).hasSize(2)
        assertThat(newTransactions.map { it.reason }).containsExactlyInAnyOrder(TransactionReason.CANCEL_REFUND, TransactionReason.RESERVE)

        val newNotifications = notificationRepository.findAll().filterNot { it.id in notificationIdsBeforeChange }
        assertThat(newNotifications).hasSize(1)
        assertThat(newNotifications.first().type).isEqualTo(NotificationType.RESERVATION_CHANGED_BY_MEMBER)
    }

    @Test
    fun `변경 대상이 정원 초과면 ReservationCapacityExceededException이 발생하고 기존 예약이 그대로 남는다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.SESSION_PASS, "3.0")
        val oldSchedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val newSchedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val original = memberReservationService.reserve(member.id!!, ReserveRequest(oldSchedule.id!!, TUESDAY_THIS_WEEK))
        persistFullSession(newSchedule, TUESDAY_THIS_WEEK, reservedCount = 10)

        assertThatThrownBy {
            memberReservationService.change(
                member.id!!,
                original.id,
                ChangeReservationRequest(newSchedule.id!!, TUESDAY_THIS_WEEK),
            )
        }.isInstanceOf(ReservationCapacityExceededException::class.java)

        val originalReservation = reservationRepository.findById(original.id).get()
        assertThat(originalReservation.status).isEqualTo(ReservationStatus.ACTIVE)

        val oldSession = classSessionRepository.findByClassScheduleIdAndClassDate(oldSchedule.id!!, TUESDAY_THIS_WEEK)!!
        assertThat(oldSession.reservedCount).isEqualTo(1)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))
    }

    @Test
    fun `변경 대상이 예약 창 밖이면 ReservationWindowClosedException이 발생하고 기존 예약이 그대로 남는다`() {
        val member = persistMember()
        val pass = persistPass(member, PassType.SESSION_PASS, "3.0")
        val oldSchedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val original = memberReservationService.reserve(member.id!!, ReserveRequest(oldSchedule.id!!, TUESDAY_THIS_WEEK))

        assertThatThrownBy {
            memberReservationService.change(
                member.id!!,
                original.id,
                ChangeReservationRequest(oldSchedule.id!!, TUESDAY_NEXT_WEEK),
            )
        }.isInstanceOf(ReservationWindowClosedException::class.java)

        val originalReservation = reservationRepository.findById(original.id).get()
        assertThat(originalReservation.status).isEqualTo(ReservationStatus.ACTIVE)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))
    }

    @Test
    fun `변경 대상 수업 종류가 다르면 ReservationTypeMismatchException이 발생하고 기존 예약이 그대로 남는다`() {
        val member = persistMember()
        persistPass(member, PassType.SESSION_PASS, "3.0")
        persistPass(member, PassType.LESSON_PASS, "3.0")
        val oldSchedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val lessonSchedule = scheduleOf(DayOfWeek.TUESDAY, ClassType.LESSON, LocalTime.of(19, 0))
        val original = memberReservationService.reserve(member.id!!, ReserveRequest(oldSchedule.id!!, TUESDAY_THIS_WEEK))

        assertThatThrownBy {
            memberReservationService.change(
                member.id!!,
                original.id,
                ChangeReservationRequest(lessonSchedule.id!!, TUESDAY_THIS_WEEK),
            )
        }.isInstanceOf(ReservationTypeMismatchException::class.java)

        val originalReservation = reservationRepository.findById(original.id).get()
        assertThat(originalReservation.status).isEqualTo(ReservationStatus.ACTIVE)
    }

    @Test
    fun `당일 예약을 변경하려 하면 SameDayModificationNotAllowedException이 발생하고 기존 예약이 그대로 남는다`() {
        val member = persistMember()
        persistPass(member, PassType.LESSON_PASS, "3.0")
        val oldSchedule = scheduleOf(DayOfWeek.MONDAY, ClassType.LESSON, LocalTime.of(19, 0))
        val newSchedule = scheduleOf(DayOfWeek.MONDAY, ClassType.LESSON, LocalTime.of(21, 0))
        val original = memberReservationService.reserve(member.id!!, ReserveRequest(oldSchedule.id!!, MONDAY_THIS_WEEK))

        assertThatThrownBy {
            memberReservationService.change(
                member.id!!,
                original.id,
                ChangeReservationRequest(newSchedule.id!!, MONDAY_THIS_WEEK),
            )
        }.isInstanceOf(SameDayModificationNotAllowedException::class.java)

        val originalReservation = reservationRepository.findById(original.id).get()
        assertThat(originalReservation.status).isEqualTo(ReservationStatus.ACTIVE)
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
                startDate = LocalDate.of(2091, 1, 1),
                endDate = LocalDate.of(2099, 12, 31),
                remainingCount = BigDecimal(remaining),
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    /** 정원 초과 재현용 — 실제 예약 10건 대신 세션 카운터를 직접 정원까지 채운다. */
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

    private companion object {
        const val KAKAO_ID_BASE = 9_400_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-resv-cxl-"
        const val BASE_CLOCK = "2092-05-05T08:00:00+09:00"
        val RANGE_FROM: LocalDate = LocalDate.of(2092, 5, 1)
        val RANGE_TO: LocalDate = LocalDate.of(2092, 5, 21)
        val MONDAY_THIS_WEEK: LocalDate = LocalDate.of(2092, 5, 5)
        val TUESDAY_THIS_WEEK: LocalDate = LocalDate.of(2092, 5, 6)
        val TUESDAY_NEXT_WEEK: LocalDate = LocalDate.of(2092, 5, 13)
    }
}
