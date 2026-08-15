package com.goldwrestling.batch

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
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.TransactionReason
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * `InactivityBatchRunner.run`이 벌크 조회(05-04) → 순수 계산(05-03) → 회원별 차감(05-05)을
 * 실제로 엮어내는지 실제 PostgreSQL(Testcontainers)로 증명하는 통합테스트(05-06-PLAN.md, BATCH-01·02).
 *
 * **클래스에 트랜잭션 애노테이션을 붙이지 않는다.** [InactivityDeductionService.deductOnce]가 회원별로
 * 독립 트랜잭션을 커밋해야 그 결과를 이 테스트가 실제로 관측할 수 있다(`InactivityDeductionServiceTest`
 * 선례) — 대신 `@AfterEach`에서 이 테스트가 만든 행을 직접 지운다.
 *
 * 회원 단위 예외 격리(한 회원의 예외가 나머지 회원 처리를 막지 않는다)와 `PARTIAL_FAILURE` 집계는
 * 예외 주입에 스파이 빈이 필요해 스프링 컨텍스트가 갈리므로 [InactivityBatchFailureIsolationTest]가
 * 맡는다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
class InactivityBatchRunnerTest {
    @Autowired
    private lateinit var inactivityBatchRunner: InactivityBatchRunner

    @Autowired
    private lateinit var passRepository: PassRepository

    @Autowired
    private lateinit var passTransactionRepository: PassTransactionRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var classScheduleRepository: ClassScheduleRepository

    @Autowired
    private lateinit var classSessionRepository: ClassSessionRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var batchExecutionRepository: BatchExecutionRepository

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private var fixtureCounter = 0L
    private val today: LocalDate = BatchFixtures.FIXED_TODAY
    private val createdSessionIds = mutableListOf<Long>()
    private val createdBatchExecutionIds = mutableListOf<Long>()

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(BatchFixtures.FIXED_TIME.toInstant())
    }

    @AfterEach
    fun cleanUp() {
        if (createdBatchExecutionIds.isNotEmpty()) {
            batchExecutionRepository.deleteAllById(createdBatchExecutionIds)
            createdBatchExecutionIds.clear()
        }
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

    // ── BATCH-01: 기준일 경과·캐치업 ─────────────────────────────────────────

    @Test
    fun `기준일 14일 전인 회원은 1회 차감된다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30), createdAt = today.minusDays(14).atTime9am())

        inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null).let { createdBatchExecutionIds += it.id!! }

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(1)
    }

    @Test
    fun `기준일 42일 전인 회원은 3회 차감된다(캐치업)`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "5.0", endDate = today.plusDays(60), createdAt = today.minusDays(42).atTime9am())

        inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null).let { createdBatchExecutionIds += it.id!! }

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(3)
    }

    @Test
    fun `기준일 13일 전인 회원은 차감되지 않는다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "2.0", endDate = today.plusDays(30), createdAt = today.minusDays(13).atTime9am())

        inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null).let { createdBatchExecutionIds += it.id!! }

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
        assertThat(inactivityCountOf(pass.id!!)).isZero()
    }

    // ── BATCH-02: 예외 3종 ────────────────────────────────────────────────

    @Test
    fun `ON_LEAVE 회원은 대상에서 제외돼 잔여·이력이 변하지 않는다`() {
        val member = persistMember(status = MemberStatus.ON_LEAVE)
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30), createdAt = today.minusDays(60).atTime9am())

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        createdBatchExecutionIds += result.id!!

        assertThat(result.processedMemberCount).isZero()
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("3.0"))
        assertThat(inactivityCountOf(pass.id!!)).isZero()
    }

    @Test
    fun `잔여 0인 SESSION_PASS만 가진 회원은 대상에서 제외돼 변화가 없다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "0.0", endDate = today.plusDays(30), createdAt = today.minusDays(60).atTime9am())

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        createdBatchExecutionIds += result.id!!

        assertThat(result.processedMemberCount).isZero()
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `만료된 SESSION_PASS만 가진 회원은 대상에서 제외돼 변화가 없다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "2.0", endDate = today.minusDays(1), createdAt = today.minusDays(60).atTime9am())

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        createdBatchExecutionIds += result.id!!

        assertThat(result.processedMemberCount).isZero()
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
    }

    // ── D-105 기준일 후보 조합 ─────────────────────────────────────────────

    @Test
    fun `어제 수업을 예약하고 취소하지 않은 회원은 기준일이 어제라 차감되지 않는다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30), createdAt = today.minusDays(60).atTime9am())
        persistActiveReservation(member, pass, classDate = today.minusDays(1))

        inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null).let { createdBatchExecutionIds += it.id!! }

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("3.0"))
    }

    @Test
    fun `15일 전 예약이 취소됐으면 그 수업일은 후보가 아니라 등록일 기준으로 1회 차감된다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "2.0", endDate = today.plusDays(30), createdAt = today.minusDays(20).atTime9am())
        val reservation = persistActiveReservation(member, pass, classDate = today.minusDays(15))
        // reservationRepository.cancelByMemberIfActive는 커스텀 @Modifying 쿼리라 명시적 트랜잭션
        // 애노테이션 없이 호출하면 flush가 실패한다(InactivityDeductionServiceTest 선례) — 상속
        // CRUD saveAndFlush로 취소 상태를 직접 반영한다.
        reservation.status = ReservationStatus.CANCELED
        reservation.canceledAt = OffsetDateTime.now(clock)
        reservation.canceledByMember = member
        reservation.refunded = true
        reservationRepository.saveAndFlush(reservation)

        inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null).let { createdBatchExecutionIds += it.id!! }

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("1.0"))
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(1)
    }

    @Test
    fun `20일 전 등록에 3일 전 휴회 복귀가 있으면 복귀일이 기준일이라 차감되지 않는다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "2.0", endDate = today.plusDays(30), createdAt = today.minusDays(20).atTime9am())
        member.returnedFromLeaveAt = today.minusDays(3).atTime9am()
        memberRepository.saveAndFlush(member)

        inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null).let { createdBatchExecutionIds += it.id!! }

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
    }

    @Test
    fun `20일 전 등록에 2일 전 양(+) 수동 가감이 있으면 가감일이 기준일이라 차감되지 않는다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "2.0", endDate = today.plusDays(30), createdAt = today.minusDays(20).atTime9am())
        persistPassTransaction(pass, amount = "0.5", reason = TransactionReason.ADMIN_ADJUST, occurredAt = today.minusDays(2).atTime9am())

        inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null).let { createdBatchExecutionIds += it.id!! }

        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
    }

    // ── 집계·회당 재선택·소진 ──────────────────────────────────────────────

    @Test
    fun `대상 회원 2명 중 1명만 차감 대상이면 이력에 processedMemberCount 2, deductedCount 1이 기록된다`() {
        val deductibleMember = persistMember()
        val deductiblePass =
            persistSessionPass(
                deductibleMember,
                remaining = "2.0",
                endDate = today.plusDays(30),
                createdAt = today.minusDays(20).atTime9am(),
            )
        val untouchedMember = persistMember()
        val untouchedPass =
            persistSessionPass(untouchedMember, remaining = "2.0", endDate = today.plusDays(30), createdAt = today.minusDays(5).atTime9am())

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        createdBatchExecutionIds += result.id!!

        assertThat(result.processedMemberCount).isEqualTo(2)
        assertThat(result.deductedCount).isEqualTo(1)
        assertThat(remainingOf(deductiblePass.id!!)).isEqualByComparingTo(BigDecimal("1.0"))
        assertThat(remainingOf(untouchedPass.id!!)).isEqualByComparingTo(BigDecimal("2.0"))
    }

    @Test
    fun `부족분이 2인데 잔여 1dot0인 장 한 장뿐이면 1회만 차감되고 스킵 1건으로 루프가 멈춘다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(30), createdAt = today.minusDays(28).atTime9am())

        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        createdBatchExecutionIds += result.id!!

        assertThat(result.deductedCount).isEqualTo(1)
        assertThat(result.skippedCount).isEqualTo(1)
        assertThat(result.status).isEqualTo(BatchExecutionStatus.SUCCESS)
        assertThat(remainingOf(pass.id!!)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(inactivityCountOf(pass.id!!)).isEqualTo(1)
    }

    // ── 대상 회원 0명·트리거 ──────────────────────────────────────────────

    @Test
    fun `대상 회원이 0명이면 0집계 SUCCESS 이력이 남고 트리거가 SCHEDULED면 triggeredByAdminId가 null이다`() {
        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        createdBatchExecutionIds += result.id!!

        assertThat(result.processedMemberCount).isZero()
        assertThat(result.deductedCount).isZero()
        assertThat(result.skippedCount).isZero()
        assertThat(result.status).isEqualTo(BatchExecutionStatus.SUCCESS)
        assertThat(result.trigger).isEqualTo(BatchTrigger.SCHEDULED)
        assertThat(result.triggeredByAdminId).isNull()
    }

    @Test
    fun `트리거가 MANUAL이면 triggeredByAdminId에 관리자 id가 채워진다`() {
        val admin = persistAdmin()

        val result = inactivityBatchRunner.run(BatchTrigger.MANUAL, admin.id)
        createdBatchExecutionIds += result.id!!

        assertThat(result.trigger).isEqualTo(BatchTrigger.MANUAL)
        assertThat(result.triggeredByAdminId).isEqualTo(admin.id)
    }

    @Test
    fun `startedAt과 finishedAt은 고정 시각 기준으로 채워지고 finishedAt이 startedAt보다 이르지 않다`() {
        val result = inactivityBatchRunner.run(BatchTrigger.SCHEDULED, null)
        createdBatchExecutionIds += result.id!!

        assertThat(result.startedAt).isEqualTo(OffsetDateTime.now(clock))
        assertThat(result.finishedAt).isAfterOrEqualTo(result.startedAt)
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun LocalDate.atTime9am(): OffsetDateTime = this.atStartOfDay(BatchFixtures.FIXED_TIME.offset).plusHours(9).toOffsetDateTime()

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun remainingOf(passId: Long): BigDecimal = passRepository.findById(passId).get().remainingCount!!

    private fun inactivityCountOf(passId: Long): Int =
        passTransactionRepository.findAll().count { it.pass.id == passId && it.reason == TransactionReason.INACTIVITY }

    private fun persistMember(status: MemberStatus = MemberStatus.ACTIVE): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            BatchFixtures.member(songpaBranch(), kakaoId = KAKAO_ID_BASE + fixtureCounter, status = status),
        )
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(BatchFixtures.admin(loginId = "$ADMIN_LOGIN_PREFIX$fixtureCounter"))
    }

    private fun persistSessionPass(
        member: Member,
        remaining: String,
        endDate: LocalDate,
        createdAt: OffsetDateTime,
    ): Pass =
        passRepository.saveAndFlush(
            BatchFixtures.sessionPass(
                member = member,
                branch = songpaBranch(),
                registeredBy = persistAdmin(),
                remainingCount = BigDecimal(remaining),
                endDate = endDate,
                createdAt = createdAt,
            ),
        )

    private fun persistPassTransaction(
        pass: Pass,
        amount: String,
        reason: TransactionReason,
        occurredAt: OffsetDateTime,
    ): PassTransaction =
        passTransactionRepository.saveAndFlush(
            BatchFixtures.passTransaction(pass = pass, amount = BigDecimal(amount), reason = reason, occurredAt = occurredAt),
        )

    /** [today]와 무관하게 스케줄만 다른 세션 하나를 매번 새 [classDate]로 만든다(`uq_class_session`). */
    private fun persistActiveReservation(
        member: Member,
        pass: Pass,
        classDate: LocalDate,
    ): Reservation {
        val session = persistClassSession(classDate)
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

    private fun persistClassSession(classDate: LocalDate): ClassSession {
        val schedule = songpaSchedule(ClassType.SESSION)
        val session =
            classSessionRepository.saveAndFlush(
                ClassSession(
                    classSchedule = schedule,
                    classDate = classDate,
                    classType = ClassType.SESSION,
                    startTime = schedule.startTime,
                    endTime = schedule.endTime,
                    capacity = schedule.capacity,
                    reservedCount = 0,
                    status = ClassSessionStatus.SCHEDULED,
                    createdAt = OffsetDateTime.now(clock),
                ),
            )
        createdSessionIds += session.id!!
        return session
    }

    private fun songpaSchedule(classType: ClassType): ClassSchedule =
        classScheduleRepository.findAllByBranchId(songpaBranch().id!!).first { it.classType == classType }

    companion object {
        const val KAKAO_ID_BASE = 9_700_000_000L
        const val ADMIN_LOGIN_PREFIX = "admin-inactivity-batch-runner-"
    }
}
