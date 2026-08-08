package com.goldwrestling.pass

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.dto.CancelPassRequest
import com.goldwrestling.reservation.AdminReservationService
import com.goldwrestling.reservation.Reservation
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.reservation.dto.AdminCancelReservationRequest
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
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 이용권 등록 취소 선행 조건(D-089, 04-13 Task 2) 통합테스트. `AdminPassService.cancel`이 활성
 * 예약이 있으면 [PassHasActiveReservationException]을 던지고, 관리자가 대리 취소(RESV-08,
 * `AdminReservationService.cancelByAdmin`)로 예약을 먼저 정리하면 그 뒤에는 등록 취소가
 * 성공한다는 2단계 절차를 실증한다.
 *
 * 애노테이션 조합은 `AdminPassControllerTest`와 맞춘다(conventions §10.1, 컨텍스트 캐시 재사용) —
 * 이 파일은 실패(거부) 경로만 사전 판정 단계에서 걸리므로(조건부 UPDATE 이전) 클래스
 * `@Transactional`을 붙여도 "변경 전 상태 그대로"를 안전하게 검증할 수 있다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class PassCancellationWithReservationTest {
    @Autowired
    private lateinit var adminPassService: AdminPassService

    @Autowired
    private lateinit var adminReservationService: AdminReservationService

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
    private lateinit var clock: Clock

    private var fixtureCounter = 30000L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `활성 예약이 있는 이용권을 등록 취소하면 409 PASS_HAS_ACTIVE_RESERVATION이고 상태-잔여-이력이 변하지 않는다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val pass = persistPass(member, admin, remaining = "2.0")
        val schedule = findSchedule(DayOfWeek.TUESDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.TUESDAY)
        persistActiveReservation(member, pass, schedule, classDate)

        assertThatThrownBy {
            adminPassService.cancel(pass.id!!, CancelPassRequest("오등록 정정 시도"), admin.id!!)
        }.isInstanceOf(PassHasActiveReservationException::class.java)

        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.status).isEqualTo(PassStatus.ACTIVE)
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(refreshedPass.canceledAt).isNull()

        val transactions = passTransactionRepository.findAll().filter { it.pass.id == pass.id }
        assertThat(transactions).isEmpty()
    }

    @Test
    fun `예약한 적 없는 이용권은 정상 취소된다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val pass = persistPass(member, admin, remaining = "2.0")

        val response = adminPassService.cancel(pass.id!!, CancelPassRequest("오등록 정정"), admin.id!!)

        assertThat(response.displayStatus).isEqualTo(PassDisplayStatus.CANCELED)
        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.status).isEqualTo(PassStatus.CANCELED)
    }

    @Test
    fun `예약이 있었지만 모두 취소된 이용권은 정상 취소된다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val pass = persistPass(member, admin, remaining = "2.0")
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.THURSDAY)
        persistCanceledReservation(member, admin, pass, schedule, classDate)

        val response = adminPassService.cancel(pass.id!!, CancelPassRequest("오등록 정정"), admin.id!!)

        assertThat(response.displayStatus).isEqualTo(PassDisplayStatus.CANCELED)
    }

    @Test
    fun `관리자가 대리 취소(refund=false)로 예약을 정리한 뒤 등록 취소하면 성공한다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val pass = persistPass(member, admin, remaining = "2.0")
        val schedule = findSchedule(DayOfWeek.FRIDAY, ClassType.SESSION, LocalTime.of(11, 0))
        val classDate = nextClassDate(DayOfWeek.FRIDAY)
        val reservation = persistActiveReservation(member, pass, schedule, classDate)

        adminReservationService.cancelByAdmin(admin.id!!, reservation.id!!, AdminCancelReservationRequest(refund = false))

        // D-089 2단계 절차 — 대리 취소(복구 안 함)로 정리한 뒤에는 활성 예약이 없으므로 등록
        // 취소가 성공한다.
        val response = adminPassService.cancel(pass.id!!, CancelPassRequest("오등록 정정, 대리 취소 후 정리"), admin.id!!)

        assertThat(response.displayStatus).isEqualTo(PassDisplayStatus.CANCELED)
        val refreshedPass = passRepository.findById(pass.id!!).get()
        // refund=false라 대리 취소 시점 잔여(2.0)가 그대로였고, 상쇄(-2.0)로 0이 된다.
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `관리자가 대리 취소(refund=true)로 정리한 뒤 등록 취소하면 성공하고 복구된 잔여가 상쇄로 0이 된다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val pass = persistPass(member, admin, remaining = "2.0")
        val schedule = findSchedule(DayOfWeek.SATURDAY, ClassType.SESSION, LocalTime.of(9, 0))
        val classDate = nextClassDate(DayOfWeek.SATURDAY)
        val reservation = persistActiveReservation(member, pass, schedule, classDate)

        adminReservationService.cancelByAdmin(admin.id!!, reservation.id!!, AdminCancelReservationRequest(refund = true))
        val restoredPass = passRepository.findById(pass.id!!).get()
        assertThat(restoredPass.remainingCount).isEqualByComparingTo(BigDecimal("3.0"))

        val response = adminPassService.cancel(pass.id!!, CancelPassRequest("오등록 정정, 대리 취소(복구) 후 정리"), admin.id!!)

        assertThat(response.displayStatus).isEqualTo(PassDisplayStatus.CANCELED)
        val refreshedPass = passRepository.findById(pass.id!!).get()
        assertThat(refreshedPass.remainingCount).isEqualByComparingTo(BigDecimal.ZERO)
        val cancelTransactions =
            passTransactionRepository.findAll().filter { it.pass.id == pass.id && it.reason == TransactionReason.REGISTRATION_CANCELED }
        assertThat(cancelTransactions).hasSize(1)
        assertThat(cancelTransactions.first().amount).isEqualByComparingTo(BigDecimal("-3.0"))
    }

    @Test
    fun `다른 이용권의 활성 예약은 이 이용권의 등록 취소를 막지 않는다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val targetPass = persistPass(member, admin, remaining = "2.0")
        val otherPass = persistPass(member, admin, remaining = "2.0")
        val schedule = findSchedule(DayOfWeek.THURSDAY, ClassType.SESSION, LocalTime.of(13, 0))
        val classDate = nextClassDate(DayOfWeek.THURSDAY)
        persistActiveReservation(member, otherPass, schedule, classDate)

        val response = adminPassService.cancel(targetPass.id!!, CancelPassRequest("오등록 정정"), admin.id!!)

        assertThat(response.displayStatus).isEqualTo(PassDisplayStatus.CANCELED)
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

    private fun nextClassDate(dayOfWeek: DayOfWeek): LocalDate {
        var date = LocalDate.now(clock).plusWeeks(2)
        while (date.dayOfWeek != dayOfWeek) {
            date = date.plusDays(1)
        }
        return date
    }

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "예약전제이용권회원$fixtureCounter",
                phoneNumber = "0106666$fixtureCounter",
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
                name = "관리자$fixtureCounter",
                loginId = "admin-pass-resv-precheck-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistPass(
        member: Member,
        admin: Admin,
        remaining: String,
    ): Pass =
        passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = PassType.SESSION_PASS,
                status = PassStatus.ACTIVE,
                startDate = LocalDate.now(clock),
                endDate = LocalDate.now(clock).plusYears(1).minusDays(1),
                remainingCount = BigDecimal(remaining),
                createdAt = OffsetDateTime.now(clock),
            ),
        )

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

    private fun persistActiveReservation(
        member: Member,
        pass: Pass,
        schedule: ClassSchedule,
        classDate: LocalDate,
    ): Reservation {
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

    private fun persistCanceledReservation(
        member: Member,
        admin: Admin,
        pass: Pass,
        schedule: ClassSchedule,
        classDate: LocalDate,
    ): Reservation {
        val session = persistSession(schedule, classDate, reservedCount = 0)
        return reservationRepository.saveAndFlush(
            Reservation(
                member = member,
                classSession = session,
                pass = pass,
                classType = schedule.classType,
                classDate = classDate,
                startTime = schedule.startTime,
                status = ReservationStatus.CANCELED,
                reservedAt = OffsetDateTime.now(clock),
                canceledAt = OffsetDateTime.now(clock),
                canceledByAdmin = admin,
                refunded = true,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
