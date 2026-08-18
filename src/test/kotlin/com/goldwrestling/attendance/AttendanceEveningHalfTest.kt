package com.goldwrestling.attendance

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.attendance.dto.AddEveningAttendanceRequest
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleRepository
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
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 저녁반 0.5회 차감(`AttendanceService.addEveningAttendance`/`delete`, ATTEND-02, policies §4.2,
 * D-128)을 실제 PostgreSQL(Testcontainers)로 증명하는 통합테스트.
 *
 * `AttendanceServiceTest`와 **동일한 애노테이션 조합**을 쓴다(conventions §10.1) — 스프링 컨텍스트가
 * 늘어나지 않게 한다. 클래스에 `@Transactional`을 붙이지 않는다 — 서비스가 실제로 커밋한 잔여·원장
 * 변화를 이 테스트가 관측해야 하고, 409 거부 시나리오는 예외 직후 트랜잭션이 abort 상태가 되므로
 * `@AfterEach`에서 이 클래스가 만든 데이터만 직접 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class AttendanceEveningHalfTest {
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
    fun `유효한 저녁반 회비가 있으면 차감하지 않는다`() {
        val schedule = songpaEveningSchedule()
        val classDate = nextClassDate()
        val member = persistMember()
        val admin = persistAdmin()
        persistEveningMembership(member, startDate = classDate.minusMonths(1), endDate = classDate.plusMonths(1))
        val sessionPass = persistSessionPass(member, remaining = "2.0", endDate = classDate.plusYears(1))

        val response =
            attendanceService.addEveningAttendance(
                admin.id!!,
                AddEveningAttendanceRequest(schedule.id!!, classDate, member.id!!),
            )
        trackSession(response.classSessionId)

        assertThat(response.deducted).isFalse()
        assertThat(passRepository.findById(sessionPass.id!!).get().remainingCount).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(passTransactionRepository.findAll().count { it.pass.id == sessionPass.id }).isZero()
        assertThat(attendanceRepository.findById(response.id).get().passTransaction).isNull()
    }

    @Test
    fun `회비가 없으면 만료가 가장 임박한 횟수권에서 0-5회를 차감한다`() {
        val schedule = songpaEveningSchedule()
        val classDate = nextClassDate()
        val member = persistMember()
        val admin = persistAdmin()
        val soonerPass = persistSessionPass(member, remaining = "2.0", endDate = classDate.plusMonths(1))
        val laterPass = persistSessionPass(member, remaining = "2.0", endDate = classDate.plusYears(1))

        val response =
            attendanceService.addEveningAttendance(
                admin.id!!,
                AddEveningAttendanceRequest(schedule.id!!, classDate, member.id!!),
            )
        trackSession(response.classSessionId)

        assertThat(response.deducted).isTrue()
        assertThat(passRepository.findById(soonerPass.id!!).get().remainingCount).isEqualByComparingTo(BigDecimal("1.5"))
        assertThat(passRepository.findById(laterPass.id!!).get().remainingCount).isEqualByComparingTo(BigDecimal("2.0"))
        val ledgerEntries = passTransactionRepository.findAll().filter { it.pass.id == soonerPass.id }
        assertThat(ledgerEntries).hasSize(1)
        assertThat(ledgerEntries.first().reason).isEqualTo(TransactionReason.EVENING_HALF)
        assertThat(ledgerEntries.first().amount).isEqualByComparingTo(BigDecimal("-0.5"))
    }

    @Test
    fun `잔여 0-5회면 저녁반 0-5회 참여만 가능하다`() {
        val schedule = songpaEveningSchedule()
        val classDate = nextClassDate()
        val member = persistMember()
        val admin = persistAdmin()
        val pass = persistSessionPass(member, remaining = "0.5", endDate = classDate.plusYears(1))

        val response =
            attendanceService.addEveningAttendance(
                admin.id!!,
                AddEveningAttendanceRequest(schedule.id!!, classDate, member.id!!),
            )
        trackSession(response.classSessionId)

        assertThat(response.deducted).isTrue()
        assertThat(passRepository.findById(pass.id!!).get().remainingCount).isEqualByComparingTo(BigDecimal("0.0"))
    }

    @Test
    fun `회비도 없고 잔여가 0-5회 미만이면 저녁반 출석을 기록할 수 없다`() {
        val schedule = songpaEveningSchedule()
        val classDate = nextClassDate()
        val member = persistMember()
        val admin = persistAdmin()
        val pass = persistSessionPass(member, remaining = "0.0", endDate = classDate.plusYears(1))

        assertThatThrownBy {
            attendanceService.addEveningAttendance(admin.id!!, AddEveningAttendanceRequest(schedule.id!!, classDate, member.id!!))
        }.isInstanceOf(EveningAttendanceDeductionUnavailableException::class.java)

        // @Transactional 메서드 전체가 롤백되므로 getOrCreate가 만들었던 ClassSession도 함께 사라진다
        // — 정리할 세션이 없다(추적 불필요).
        assertThat(classSessionRepository.findByClassScheduleIdAndClassDate(schedule.id!!, classDate)).isNull()
        assertThat(attendanceRepository.findAll().none { it.member.id == member.id }).isTrue()
        assertThat(passRepository.findById(pass.id!!).get().remainingCount).isEqualByComparingTo(BigDecimal("0.0"))
    }

    @Test
    fun `저녁반 출석을 삭제하면 0-5회가 복구된다`() {
        val schedule = songpaEveningSchedule()
        val classDate = nextClassDate()
        val member = persistMember()
        val admin = persistAdmin()
        val pass = persistSessionPass(member, remaining = "2.0", endDate = classDate.plusYears(1))

        val response =
            attendanceService.addEveningAttendance(
                admin.id!!,
                AddEveningAttendanceRequest(schedule.id!!, classDate, member.id!!),
            )
        trackSession(response.classSessionId)
        assertThat(passRepository.findById(pass.id!!).get().remainingCount).isEqualByComparingTo(BigDecimal("1.5"))

        attendanceService.delete(admin.id!!, response.id)

        assertThat(passRepository.findById(pass.id!!).get().remainingCount).isEqualByComparingTo(BigDecimal("2.0"))
        val ledgerEntries = passTransactionRepository.findAll().filter { it.pass.id == pass.id }
        assertThat(ledgerEntries).hasSize(2)
        assertThat(
            ledgerEntries.map { it.reason },
        ).containsExactlyInAnyOrder(TransactionReason.EVENING_HALF, TransactionReason.EVENING_HALF_REFUND)
        assertThat(attendanceRepository.findById(response.id)).isEmpty()
    }

    @Test
    fun `유효기간 판정 기준일은 오늘이 아니라 수업날이다`() {
        val schedule = songpaEveningSchedule()
        // 오늘(FIXED_TODAY = 2026-08-02) 기준으로는 이미 만료됐지만, 수업날(과거) 기준으로는 유효한 이용권.
        val classDate = LocalDate.of(2026, 6, 1)
        val member = persistMember()
        val admin = persistAdmin()
        val pass = persistSessionPass(member, remaining = "1.0", endDate = LocalDate.of(2026, 6, 15))

        val response =
            attendanceService.addEveningAttendance(
                admin.id!!,
                AddEveningAttendanceRequest(schedule.id!!, classDate, member.id!!),
            )
        trackSession(response.classSessionId)

        assertThat(response.deducted).isTrue()
        assertThat(passRepository.findById(pass.id!!).get().remainingCount).isEqualByComparingTo(BigDecimal("0.5"))
    }

    @Test
    fun `같은 회원을 같은 저녁반 수업에 두 번 추가할 수 없다`() {
        val schedule = songpaEveningSchedule()
        val classDate = nextClassDate()
        val member = persistMember()
        val admin = persistAdmin()
        val pass = persistSessionPass(member, remaining = "2.0", endDate = classDate.plusYears(1))

        val response =
            attendanceService.addEveningAttendance(
                admin.id!!,
                AddEveningAttendanceRequest(schedule.id!!, classDate, member.id!!),
            )
        trackSession(response.classSessionId)

        assertThatThrownBy {
            attendanceService.addEveningAttendance(admin.id!!, AddEveningAttendanceRequest(schedule.id!!, classDate, member.id!!))
        }.isInstanceOf(DuplicateAttendanceException::class.java)

        assertThat(passRepository.findById(pass.id!!).get().remainingCount).isEqualByComparingTo(BigDecimal("1.5"))
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

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

    /** 기간제(`EVENING_MEMBERSHIP`)는 `remainingCount`가 null이다(policies §4.2a). */
    private fun persistEveningMembership(
        member: Member,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Pass =
        passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = persistAdmin(),
                type = PassType.EVENING_MEMBERSHIP,
                status = PassStatus.ACTIVE,
                startDate = startDate,
                endDate = endDate,
                remainingCount = null,
                createdAt = AttendanceFixtures.FIXED_TIME,
            ),
        )

    /** 매 호출마다 서로 다른 [LocalDate]를 써서 `uq_class_session`(class_schedule_id, class_date)을 피한다. */
    private fun nextClassDate(): LocalDate = BASE_SESSION_DATE.plusDays(sessionDateCounter++)

    private fun trackSession(sessionId: Long?) {
        if (sessionId != null) createdSessionIds += sessionId
    }

    companion object {
        const val KAKAO_ID_BASE = 9_830_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-evening-half-"
        val BASE_SESSION_DATE: LocalDate = LocalDate.of(2033, 1, 1)
    }
}
