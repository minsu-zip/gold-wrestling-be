package com.goldwrestling.attendance

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.attendance.dto.CheckAttendanceRequest
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
import com.goldwrestling.reservation.Reservation
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleNotFoundException
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassSessionStatus
import com.goldwrestling.schedule.ClassType
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.TemporalAdjusters

/**
 * `AttendanceService`의 예약제/1:1 경로(명단 프리로드·건별 upsert)를 실제 PostgreSQL
 * (Testcontainers)로 증명하는 통합테스트(D-132, policies §6).
 *
 * `AttendanceRepositoryTest`·`InactivityBatchRunnerTest`와 동일한 애노테이션 조합을 쓴다
 * (conventions §10.1) — 스프링 컨텍스트가 늘어나지 않게 한다. 클래스에 `@Transactional`을 붙이지
 * 않는다 — `check`가 실제로 커밋한 결과를 이 테스트가 관측해야 하고, 예약자 아님·저녁반 오용
 * 시나리오는 예외 직후 트랜잭션이 abort 상태가 되므로 `@AfterEach`에서 이 클래스가 만든 데이터만
 * 직접 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class AttendanceServiceTest {
    @Autowired
    private lateinit var attendanceService: AttendanceService

    @Autowired
    private lateinit var attendanceRepository: AttendanceRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var classSessionRepository: ClassSessionRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var passTransactionRepository: PassTransactionRepository

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L
    private var sessionDateCounter = 0L
    private val createdSessionIds = mutableListOf<Long>()

    @AfterEach
    fun cleanUp() {
        jdbcClient
            .sql("delete from attendance where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql(
                "delete from pass_transaction where pass_id in " +
                    "(select id from pass where member_id in (select id from member where kakao_id >= :base))",
            ).param("base", KAKAO_ID_BASE)
            .update()
        jdbcClient
            .sql("delete from reservation where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
            .update()
        if (createdSessionIds.isNotEmpty()) {
            jdbcClient
                .sql("delete from class_session where id in (:ids)")
                .param("ids", createdSessionIds)
                .update()
            createdSessionIds.clear()
        }
        jdbcClient
            .sql("delete from pass where member_id in (select id from member where kakao_id >= :base)")
            .param("base", KAKAO_ID_BASE)
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
    fun `세션이 없으면 명단은 비어 있고 세션을 만들지 않는다`() {
        val schedule = songpaSessionSchedule()
        val classDate = nextClassDate(schedule)

        val roster = attendanceService.getRoster(schedule.id!!, classDate)

        assertThat(roster.classSessionId).isNull()
        assertThat(roster.entries).isEmpty()
        assertThat(sessionCount(schedule.id!!, classDate)).isZero()
    }

    @Test
    fun `예약제 수업 명단에는 활성 예약자가 전원 나오고 체크 전에는 상태가 비어 있다`() {
        val schedule = songpaSessionSchedule()
        val classDate = nextClassDate(schedule)
        val session = persistClassSession(schedule, classDate)
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = classDate.plusYears(1))
        persistActiveReservation(member, pass, session)

        val roster = attendanceService.getRoster(schedule.id!!, classDate)

        assertThat(roster.classSessionId).isEqualTo(session.id)
        assertThat(roster.entries).hasSize(1)
        val entry = roster.entries.first()
        assertThat(entry.memberId).isEqualTo(member.id)
        assertThat(entry.status).isNull()
        assertThat(entry.attendanceId).isNull()
        assertThat(entry.deducted).isFalse()
    }

    @Test
    fun `취소된 예약자는 명단에 나오지 않는다`() {
        val schedule = songpaSessionSchedule()
        val classDate = nextClassDate(schedule)
        val session = persistClassSession(schedule, classDate)
        val activeMember = persistMember()
        val activePass = persistSessionPass(activeMember, remaining = "3.0", endDate = classDate.plusYears(1))
        persistActiveReservation(activeMember, activePass, session)

        val canceledMember = persistMember()
        val canceledPass = persistSessionPass(canceledMember, remaining = "3.0", endDate = classDate.plusYears(1))
        val canceledReservation = persistActiveReservation(canceledMember, canceledPass, session)
        canceledReservation.status = ReservationStatus.CANCELED
        canceledReservation.canceledAt = FIXED_TIME
        canceledReservation.canceledByMember = canceledMember
        canceledReservation.refunded = true
        reservationRepository.saveAndFlush(canceledReservation)

        val roster = attendanceService.getRoster(schedule.id!!, classDate)

        assertThat(roster.entries.map { it.memberId }).containsExactly(activeMember.id)
    }

    @Test
    fun `불참으로 체크했다가 출석으로 정정할 수 있다`() {
        val schedule = songpaSessionSchedule()
        val classDate = nextClassDate(schedule)
        val session = persistClassSession(schedule, classDate)
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = classDate.plusYears(1))
        persistActiveReservation(member, pass, session)
        val admin = persistAdmin()

        attendanceService.check(admin.id!!, CheckAttendanceRequest(schedule.id!!, classDate, member.id!!, AttendanceStatus.ABSENT))
        val response =
            attendanceService.check(admin.id!!, CheckAttendanceRequest(schedule.id!!, classDate, member.id!!, AttendanceStatus.ATTENDED))

        assertThat(response.status).isEqualTo(AttendanceStatus.ATTENDED)
        assertThat(attendanceRepository.findAllByClassSessionId(session.id!!)).hasSize(1)
    }

    @Test
    fun `예약자가 아닌 회원은 출석 체크할 수 없다`() {
        val schedule = songpaSessionSchedule()
        val classDate = nextClassDate(schedule)
        persistClassSession(schedule, classDate)
        val member = persistMember()
        val admin = persistAdmin()

        assertThatThrownBy {
            attendanceService.check(admin.id!!, CheckAttendanceRequest(schedule.id!!, classDate, member.id!!, AttendanceStatus.ATTENDED))
        }.isInstanceOf(AttendanceMemberNotReservedException::class.java)
    }

    /**
     * D-146(D-136 미해결 항목 마감) — 보강 수업은 v1에서 허용하지 않으므로, 출석 체크 경로도 요일이
     * 어긋난 `(시간표, 날짜)` 조합으로는 세션을 만들지 않는다(policies §2). 예약 경로가 이미
     * 강제하던 불변식을 `ClassSessionService.getOrCreate`로 내려 전 쓰기 경로에 적용한 결과다.
     */
    @Test
    fun `시간표 요일과 다른 날짜로는 출석을 체크할 수 없다`() {
        val schedule = songpaSessionSchedule()
        val mismatchedDate = nextClassDate(schedule).plusDays(1)
        val member = persistMember()
        val admin = persistAdmin()

        assertThatThrownBy {
            attendanceService.check(
                admin.id!!,
                CheckAttendanceRequest(schedule.id!!, mismatchedDate, member.id!!, AttendanceStatus.ATTENDED),
            )
        }.isInstanceOf(ClassScheduleNotFoundException::class.java)

        // 거부가 세션 실체화보다 앞서므로 빈 세션도 남지 않는다.
        assertThat(sessionCount(schedule.id!!, mismatchedDate)).isZero()
    }

    @Test
    fun `저녁반 수업에는 예약자 출석 체크 경로를 쓸 수 없다`() {
        val schedule = songpaEveningSchedule()
        val classDate = nextClassDate(schedule)
        val member = persistMember()
        val admin = persistAdmin()

        assertThatThrownBy {
            attendanceService.check(admin.id!!, CheckAttendanceRequest(schedule.id!!, classDate, member.id!!, AttendanceStatus.ATTENDED))
        }.isInstanceOf(AttendanceClassTypeMismatchException::class.java)
    }

    @Test
    fun `출석 기록은 차감과 무관한 참고용 데이터라 잔여와 이력이 변하지 않는다`() {
        val schedule = songpaSessionSchedule()
        val classDate = nextClassDate(schedule)
        val session = persistClassSession(schedule, classDate)
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = classDate.plusYears(1))
        persistActiveReservation(member, pass, session)
        val admin = persistAdmin()

        attendanceService.check(admin.id!!, CheckAttendanceRequest(schedule.id!!, classDate, member.id!!, AttendanceStatus.ATTENDED))

        assertThat(passRepository.findById(pass.id!!).get().remainingCount).isEqualByComparingTo(BigDecimal("3.0"))
        assertThat(passTransactionRepository.findAll().count { it.pass.id == pass.id }).isZero()
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun songpaSessionSchedule(): ClassSchedule =
        classScheduleRepository.findAllByBranchId(songpaBranch().id!!).first { it.classType == ClassType.SESSION }

    private fun songpaEveningSchedule(): ClassSchedule =
        classScheduleRepository.findAllByBranchId(songpaBranch().id!!).first { it.classType == ClassType.EVENING }

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(AttendanceFixtures.member(songpaBranch(), kakaoId = KAKAO_ID_BASE + fixtureCounter))
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(AttendanceFixtures.admin(loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter"))
    }

    private fun persistSessionPass(
        member: Member,
        remaining: String,
        endDate: LocalDate,
    ): Pass =
        passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = persistAdmin(),
                type = PassType.SESSION_PASS,
                status = PassStatus.ACTIVE,
                startDate = endDate.minusYears(1).plusDays(1),
                endDate = endDate,
                remainingCount = BigDecimal(remaining),
                createdAt = AttendanceFixtures.FIXED_TIME,
            ),
        )

    /**
     * 매 호출마다 서로 다른 [LocalDate]를 써서 `uq_class_session`(class_schedule_id, class_date)을
     * 피하되, **항상 [schedule]의 요일에 맞춘 날짜만 반환한다**(D-146, policies §2) — 요일이 어긋난
     * 조합은 `ClassSessionService.getOrCreate`가 404로 거부한다. 하루씩이 아니라 1주씩 더해 같은
     * 요일을 유지하면서 날짜 유일성도 함께 얻는다.
     */
    private fun nextClassDate(schedule: ClassSchedule): LocalDate =
        BASE_SESSION_DATE
            .with(TemporalAdjusters.nextOrSame(schedule.dayOfWeek))
            .plusWeeks(sessionDateCounter++)

    private fun persistClassSession(
        schedule: ClassSchedule,
        classDate: LocalDate,
    ): ClassSession {
        val session =
            classSessionRepository.saveAndFlush(
                ClassSession(
                    classSchedule = schedule,
                    classDate = classDate,
                    classType = schedule.classType,
                    startTime = schedule.startTime,
                    endTime = schedule.endTime,
                    capacity = schedule.capacity,
                    reservedCount = 0,
                    status = ClassSessionStatus.SCHEDULED,
                    createdAt = AttendanceFixtures.FIXED_TIME,
                ),
            )
        createdSessionIds += session.id!!
        return session
    }

    private fun persistActiveReservation(
        member: Member,
        pass: Pass,
        session: ClassSession,
    ): Reservation =
        reservationRepository.saveAndFlush(
            Reservation(
                member = member,
                classSession = session,
                pass = pass,
                classType = session.classType,
                classDate = session.classDate,
                startTime = session.startTime,
                status = ReservationStatus.ACTIVE,
                reservedAt = AttendanceFixtures.FIXED_TIME,
                createdAt = AttendanceFixtures.FIXED_TIME,
            ),
        )

    private fun sessionCount(
        classScheduleId: Long,
        classDate: LocalDate,
    ): Long =
        jdbcClient
            .sql("select count(*) from class_session where class_schedule_id = :scheduleId and class_date = :classDate")
            .param("scheduleId", classScheduleId)
            .param("classDate", classDate)
            .query(Long::class.java)
            .single()

    companion object {
        const val KAKAO_ID_BASE = 9_820_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-attendance-service-"
        val BASE_SESSION_DATE: LocalDate = LocalDate.of(2031, 1, 1)
        val FIXED_TIME: OffsetDateTime = AttendanceFixtures.FIXED_TIME
    }
}
