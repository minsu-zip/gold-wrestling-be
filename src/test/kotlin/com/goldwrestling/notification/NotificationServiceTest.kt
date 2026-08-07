package com.goldwrestling.notification

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
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
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * `NotificationService`의 4개 팩토리 메서드가 `<behavior>`(04-07-PLAN.md Task 1)를 지키는지
 * 실제 PostgreSQL(Testcontainers)로 증명한다. 애노테이션 조합은 `PassDeductionCandidateTest`와
 * 동일하게 유지한다(conventions §10.1).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class NotificationServiceTest {
    @Autowired
    private lateinit var notificationService: NotificationService

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var classSessionRepository: ClassSessionRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var clock: Clock

    private var fixtureCounter = 0L
    private var sessionDateCounter = 0

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `회원 예약 알림은 RESERVATION_CREATED로 생성되고 회원명-수업정보가 비정규화된다`() {
        val reservation = persistReservation()

        val notification = notificationService.createReservationCreated(reservation)

        assertThat(notification.type).isEqualTo(NotificationType.RESERVATION_CREATED)
        assertThat(notification.reservation?.id).isEqualTo(reservation.id)
        assertThat(notification.classSession).isNull()
        assertThat(notification.memberName).isEqualTo(reservation.member.name)
        assertThat(notification.classType).isEqualTo(reservation.classType)
        assertThat(notification.classDate).isEqualTo(reservation.classDate)
        assertThat(notification.startTime).isEqualTo(reservation.startTime)
        assertThat(notification.isRead).isFalse()
        assertThat(notification.readAt).isNull()
        assertThat(notification.occurredAt).isEqualTo(OffsetDateTime.now(clock))
    }

    @Test
    fun `회원 셀프 취소 알림은 RESERVATION_CANCELED_BY_MEMBER로 생성된다`() {
        val reservation = persistReservation()

        val notification = notificationService.createReservationCanceled(reservation, byAdmin = false)

        assertThat(notification.type).isEqualTo(NotificationType.RESERVATION_CANCELED_BY_MEMBER)
        assertThat(notification.reservation?.id).isEqualTo(reservation.id)
    }

    @Test
    fun `관리자 대리 취소 알림은 RESERVATION_CANCELED_BY_ADMIN으로 생성된다`() {
        val reservation = persistReservation()

        val notification = notificationService.createReservationCanceled(reservation, byAdmin = true)

        assertThat(notification.type).isEqualTo(NotificationType.RESERVATION_CANCELED_BY_ADMIN)
    }

    @Test
    fun `회원 셀프 변경 알림은 RESERVATION_CHANGED_BY_MEMBER로 새 예약을 참조한다`() {
        val reservation = persistReservation()

        val notification = notificationService.createReservationChanged(reservation, byAdmin = false)

        assertThat(notification.type).isEqualTo(NotificationType.RESERVATION_CHANGED_BY_MEMBER)
        assertThat(notification.reservation?.id).isEqualTo(reservation.id)
    }

    @Test
    fun `관리자 대리 변경 알림은 RESERVATION_CHANGED_BY_ADMIN으로 생성된다`() {
        val reservation = persistReservation()

        val notification = notificationService.createReservationChanged(reservation, byAdmin = true)

        assertThat(notification.type).isEqualTo(NotificationType.RESERVATION_CHANGED_BY_ADMIN)
    }

    @Test
    fun `휴강 알림은 CLASS_SESSION_SUSPENDED로 예약 없이 세션만 참조하고 취소 건수를 메시지에 담는다`() {
        val session = persistSession()

        val notification = notificationService.createClassSessionSuspended(session, canceledCount = 7)

        assertThat(notification.type).isEqualTo(NotificationType.CLASS_SESSION_SUSPENDED)
        assertThat(notification.reservation).isNull()
        assertThat(notification.classSession?.id).isEqualTo(session.id)
        assertThat(notification.memberName).isNull()
        assertThat(notification.classDate).isEqualTo(session.classDate)
        assertThat(notification.startTime).isEqualTo(session.startTime)
        assertThat(notification.message).contains("7건")
    }

    @Test
    fun `수업 종류 한글 라벨이 메시지에 포함된다`() {
        val reservation = persistReservation()

        val notification = notificationService.createReservationCreated(reservation)

        assertThat(notification.message).contains("예약제 수업")
        assertThat(notification.message).contains(reservation.member.name!!)
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun sessionSchedule(): ClassSchedule =
        classScheduleRepository
            .findAllByBranchId(songpaBranch().id!!)
            .first { it.dayOfWeek == DayOfWeek.TUESDAY && it.classType == ClassType.SESSION && it.startTime.hour == 11 }

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "회원$fixtureCounter",
                phoneNumber = "0101234$fixtureCounter",
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
                loginId = "admin-notif-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistSession(): ClassSession {
        val schedule = sessionSchedule()
        sessionDateCounter++
        return classSessionRepository.saveAndFlush(
            ClassSession(
                classSchedule = schedule,
                classDate = LocalDate.of(2099, 4, sessionDateCounter),
                classType = schedule.classType,
                startTime = schedule.startTime,
                endTime = schedule.endTime,
                capacity = schedule.capacity,
                reservedCount = 0,
                status = ClassSessionStatus.SCHEDULED,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistPass(member: Member): Pass {
        val admin = persistAdmin()
        return passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = PassType.SESSION_PASS,
                status = PassStatus.ACTIVE,
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2099, 12, 31),
                remainingCount = BigDecimal("5.0"),
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistReservation(): Reservation {
        val member = persistMember()
        val session = persistSession()
        val pass = persistPass(member)
        return reservationRepository.saveAndFlush(
            Reservation(
                member = member,
                classSession = session,
                pass = pass,
                classType = session.classType,
                classDate = session.classDate,
                startTime = session.startTime,
                status = ReservationStatus.ACTIVE,
                reservedAt = OffsetDateTime.now(clock),
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
