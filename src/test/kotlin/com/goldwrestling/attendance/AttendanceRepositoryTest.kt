package com.goldwrestling.attendance

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassType
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate

/**
 * `Attendance`의 회원×세션 유니크(T-06-03)·상태 정정·CR-03 벌크 조회(T-06-04)를 실제
 * PostgreSQL(Testcontainers)에서 증명하는 통합테스트(policies §6).
 *
 * 애노테이션 조합을 `InactivityBatchRunnerTest`와 동일하게 맞춘다(conventions §10.1) — 이후
 * 06-06~06-08(출석 서비스·컨트롤러·동시성)이 조건부 원자 갱신을 검증할 때 같은 조합을 재사용해
 * 스프링 컨텍스트가 늘어나지 않게 한다. 클래스에 `@Transactional`을 붙이지 않는다 — 유니크 위반
 * 단언 직후 PostgreSQL 트랜잭션이 abort 상태가 되므로, 대신 `@AfterEach`에서 이 클래스가 만든
 * 데이터만 직접 지운다(`BatchExecutionRepositoryTest` 선례와 반대 이유로 같은 결론).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class AttendanceRepositoryTest {
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
        if (createdSessionIds.isNotEmpty()) {
            jdbcClient
                .sql("delete from class_session where id in (:ids)")
                .param("ids", createdSessionIds)
                .update()
            createdSessionIds.clear()
        }
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
    fun `같은 회원을 같은 수업에 두 번 출석 기록할 수 없다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val session = persistClassSession()
        attendanceRepository.saveAndFlush(AttendanceFixtures.attendance(session, member, AttendanceStatus.ATTENDED, admin))

        assertThatThrownBy {
            attendanceRepository.saveAndFlush(AttendanceFixtures.attendance(session, member, AttendanceStatus.ATTENDED, admin))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `불참에서 출석으로 정정해도 새 행이 생기지 않는다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val session = persistClassSession()
        val saved = attendanceRepository.saveAndFlush(AttendanceFixtures.attendance(session, member, AttendanceStatus.ABSENT, admin))

        saved.status = AttendanceStatus.ATTENDED
        attendanceRepository.saveAndFlush(saved)

        val recorded = attendanceRepository.findAllByClassSessionId(session.id!!)
        assertThat(recorded).hasSize(1)
        assertThat(recorded.first().status).isEqualTo(AttendanceStatus.ATTENDED)
    }

    @Test
    fun `마지막 출석일은 출석(ATTENDED) 기록만 기준으로 한다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val attendedSession = persistClassSession()
        val absentSession = persistClassSession()
        attendanceRepository.saveAndFlush(AttendanceFixtures.attendance(attendedSession, member, AttendanceStatus.ATTENDED, admin))
        attendanceRepository.saveAndFlush(AttendanceFixtures.attendance(absentSession, member, AttendanceStatus.ABSENT, admin))

        val result = attendanceRepository.findLastAttendedClassDates(listOf(member.id!!))

        assertThat(result).hasSize(1)
        assertThat(result.first().getDate()).isEqualTo(attendedSession.classDate)
    }

    @Test
    fun `불참만 있는 회원은 마지막 출석일 후보에 나타나지 않는다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val session = persistClassSession()
        attendanceRepository.saveAndFlush(AttendanceFixtures.attendance(session, member, AttendanceStatus.ABSENT, admin))

        val result = attendanceRepository.findLastAttendedClassDates(listOf(member.id!!))

        assertThat(result.map { it.getMemberId() }).doesNotContain(member.id)
    }

    @Test
    fun `회원별로 가장 최근 출석 수업일을 반환한다`() {
        val admin = persistAdmin()
        val member1 = persistMember()
        val member1EarlySession = persistClassSession()
        val member1LateSession = persistClassSession()
        attendanceRepository.saveAndFlush(AttendanceFixtures.attendance(member1EarlySession, member1, AttendanceStatus.ATTENDED, admin))
        attendanceRepository.saveAndFlush(AttendanceFixtures.attendance(member1LateSession, member1, AttendanceStatus.ATTENDED, admin))

        val member2 = persistMember()
        val member2EarlySession = persistClassSession()
        val member2LateSession = persistClassSession()
        attendanceRepository.saveAndFlush(AttendanceFixtures.attendance(member2EarlySession, member2, AttendanceStatus.ATTENDED, admin))
        attendanceRepository.saveAndFlush(AttendanceFixtures.attendance(member2LateSession, member2, AttendanceStatus.ATTENDED, admin))

        val result = attendanceRepository.findLastAttendedClassDates(listOf(member1.id!!, member2.id!!))
        val dateByMember = result.associate { it.getMemberId() to it.getDate() }

        assertThat(dateByMember[member1.id]).isEqualTo(member1LateSession.classDate)
        assertThat(dateByMember[member2.id]).isEqualTo(member2LateSession.classDate)
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun songpaSessionSchedule(): ClassSchedule =
        classScheduleRepository.findAllByBranchId(songpaBranch().id!!).first { it.classType == ClassType.SESSION }

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(AttendanceFixtures.member(songpaBranch(), kakaoId = KAKAO_ID_BASE + fixtureCounter))
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(AttendanceFixtures.admin(loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter"))
    }

    /** 매 호출마다 서로 다른 [LocalDate]를 써서 `uq_class_session`(class_schedule_id, class_date)을 피한다. */
    private fun persistClassSession(): ClassSession {
        val classDate = BASE_SESSION_DATE.plusDays(sessionDateCounter++)
        val session = classSessionRepository.saveAndFlush(AttendanceFixtures.classSession(songpaSessionSchedule(), classDate))
        createdSessionIds += session.id!!
        return session
    }

    companion object {
        const val KAKAO_ID_BASE = 9_800_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-attendance-repository-"
        val BASE_SESSION_DATE: LocalDate = LocalDate.of(2030, 1, 1)
    }
}
