package com.goldwrestling.reservation

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * `ReservationRepository`의 취소 compare-and-swap 2종과 부분 유니크 인덱스 3종(V6)의 실제 거부
 * 동작을 Testcontainers로 증명하는 통합테스트. 인덱스 위반 케이스는 각각 별도 `@Test` 메서드로
 * 나눠 "제약 위반 이후 같은 트랜잭션을 계속 쓰지 않는다"(PLAN.md action (5))를 지킨다 — 클래스
 * 레벨 `@Transactional`이 테스트 메서드마다 독립된 트랜잭션을 열고 끝나면 롤백한다.
 * 애노테이션 조합은 `PassRepositoryTest`와 동일하게 유지한다(conventions §10.1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class ReservationRepositoryTest {
    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var classSessionRepository: ClassSessionRepository

    @Autowired
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var clock: Clock

    private var fixtureCounter = 0L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `같은 세션에 같은 회원이 중복 ACTIVE 예약을 시도하면 두 번째가 DataIntegrityViolationException으로 실패한다`() {
        val member = persistMember()
        val session = persistClassSession()
        val pass = persistPass(member, PassType.SESSION_PASS)
        persistReservation(member, session, pass)

        assertThatThrownBy { persistReservation(member, session, pass) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `첫 예약을 취소한 뒤 같은 세션-회원으로 다시 예약하면 성공한다`() {
        val member = persistMember()
        val session = persistClassSession()
        val pass = persistPass(member, PassType.SESSION_PASS)
        val first = persistReservation(member, session, pass)

        val canceled = reservationRepository.cancelByMemberIfActive(first.id!!, member, OffsetDateTime.now(clock))
        assertThat(canceled).isEqualTo(1)

        val second = persistReservation(member, session, pass)
        assertThat(second.id).isNotNull()
    }

    @Test
    fun `LESSON 세션에 서로 다른 회원 2명이 ACTIVE 예약을 시도하면 두 번째가 실패한다`() {
        val session = persistClassSession(classType = ClassType.LESSON, capacity = 1)
        val member1 = persistMember()
        val member2 = persistMember()
        val pass1 = persistPass(member1, PassType.LESSON_PASS)
        val pass2 = persistPass(member2, PassType.LESSON_PASS)
        persistReservation(member1, session, pass1)

        assertThatThrownBy { persistReservation(member2, session, pass2) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `같은 회원이 같은 날짜-시각의 서로 다른 세션에 ACTIVE 예약 2건을 시도하면 두 번째가 실패한다`() {
        val member = persistMember()
        val classDate = nextClassDate()
        val startTime = LocalTime.of(11, 0)
        val sessionA =
            persistClassSession(classType = ClassType.SESSION, classDate = classDate, startTime = startTime, scheduleIndex = 0)
        val sessionB =
            persistClassSession(
                classType = ClassType.LESSON,
                classDate = classDate,
                startTime = startTime,
                capacity = 1,
                scheduleIndex = 1,
            )
        val passSession = persistPass(member, PassType.SESSION_PASS)
        val passLesson = persistPass(member, PassType.LESSON_PASS)
        persistReservation(member, sessionA, passSession)

        assertThatThrownBy { persistReservation(member, sessionB, passLesson) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `cancelByMemberIfActive는 ACTIVE 예약에 1을, 이미 CANCELED인 예약에 0을 반환한다`() {
        val member = persistMember()
        val session = persistClassSession()
        val pass = persistPass(member, PassType.SESSION_PASS)
        val reservation = persistReservation(member, session, pass)

        val firstCancel = reservationRepository.cancelByMemberIfActive(reservation.id!!, member, OffsetDateTime.now(clock))
        assertThat(firstCancel).isEqualTo(1)

        val secondCancel = reservationRepository.cancelByMemberIfActive(reservation.id!!, member, OffsetDateTime.now(clock))
        assertThat(secondCancel).isEqualTo(0)
    }

    @Test
    fun `cancelByAdminIfActive에 refunded=false를 넘기면 저장된 예약의 refunded가 false다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val session = persistClassSession()
        val pass = persistPass(member, PassType.SESSION_PASS)
        val reservation = persistReservation(member, session, pass)

        val updated = reservationRepository.cancelByAdminIfActive(reservation.id!!, admin, false, OffsetDateTime.now(clock))

        assertThat(updated).isEqualTo(1)
        val reloaded = reservationRepository.findById(reservation.id!!).get()
        assertThat(reloaded.refunded).isFalse()
    }

    @Test
    fun `existsByPassIdAndStatus는 활성 예약 유무를 정확히 반환한다`() {
        val member = persistMember()
        val session = persistClassSession()
        val pass = persistPass(member, PassType.SESSION_PASS)

        assertThat(reservationRepository.existsByPassIdAndStatus(pass.id!!, ReservationStatus.ACTIVE)).isFalse()

        persistReservation(member, session, pass)

        assertThat(reservationRepository.existsByPassIdAndStatus(pass.id!!, ReservationStatus.ACTIVE)).isTrue()
    }

    @Test
    fun `findAllByClassSessionIdInAndStatusAndMemberId는 같은 세션의 타인 예약을 제외한다`() {
        val me = persistMember()
        val other = persistMember()
        val session = persistClassSession()
        val myReservation = persistReservation(me, session, persistPass(me, PassType.SESSION_PASS))
        persistReservation(other, session, persistPass(other, PassType.SESSION_PASS))

        val found =
            reservationRepository.findAllByClassSessionIdInAndStatusAndMemberId(
                listOf(session.id!!),
                ReservationStatus.ACTIVE,
                me.id!!,
            )

        assertThat(found).extracting<Long> { it.id }.containsExactly(myReservation.id)
    }

    @Test
    fun `findAllByClassSessionIdInAndStatusAndMemberId는 본인의 취소된 예약을 제외한다`() {
        val me = persistMember()
        val session = persistClassSession()
        // 취소는 실제 취소 경로로 만든다 — CANCELED 행에 취소 메타데이터가 없으면
        // `ck_reservation_cancellation`(V6)이 INSERT 자체를 거부한다.
        val reservation = persistReservation(me, session, persistPass(me, PassType.SESSION_PASS))
        reservationRepository.cancelByMemberIfActive(reservation.id!!, me, OffsetDateTime.now(clock))

        val found =
            reservationRepository.findAllByClassSessionIdInAndStatusAndMemberId(
                listOf(session.id!!),
                ReservationStatus.ACTIVE,
                me.id!!,
            )

        assertThat(found).isEmpty()
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    /** [index]번째(오름차순 id)로 서로 다른 시간표를 골라 세션 간 `class_schedule_id`가 겹치지 않게 한다. */
    private fun anySchedule(index: Int = 0): ClassSchedule =
        classScheduleRepository.findAllByBranchId(songpaBranch().id!!).sortedBy { it.id!! }[index]

    private fun nextClassDate(): LocalDate {
        fixtureCounter++
        return LocalDate.now(clock).plusDays(fixtureCounter)
    }

    private fun persistClassSession(
        classType: ClassType = ClassType.SESSION,
        classDate: LocalDate = nextClassDate(),
        startTime: LocalTime = LocalTime.of(11, 0),
        capacity: Int? = 10,
        scheduleIndex: Int = 0,
    ): ClassSession {
        val schedule = anySchedule(scheduleIndex)
        return classSessionRepository.saveAndFlush(
            ClassSession(
                classSchedule = schedule,
                classDate = classDate,
                classType = classType,
                startTime = startTime,
                endTime = startTime.plusMinutes(90),
                capacity = capacity,
                reservedCount = 0,
                status = ClassSessionStatus.SCHEDULED,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistReservation(
        member: Member,
        classSession: ClassSession,
        pass: Pass,
        status: ReservationStatus = ReservationStatus.ACTIVE,
    ): Reservation =
        reservationRepository.saveAndFlush(
            Reservation(
                member = member,
                classSession = classSession,
                pass = pass,
                classType = classSession.classType,
                classDate = classSession.classDate,
                startTime = classSession.startTime,
                status = status,
                reservedAt = OffsetDateTime.now(clock),
                createdAt = OffsetDateTime.now(clock),
            ),
        )

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
                loginId = "admin-reservation-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistPass(
        member: Member,
        type: PassType,
    ): Pass {
        val admin = persistAdmin()
        return passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = type,
                status = PassStatus.ACTIVE,
                startDate = LocalDate.now(clock),
                endDate = LocalDate.now(clock).plusYears(1).minusDays(1),
                remainingCount = BigDecimal("5.0"),
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
