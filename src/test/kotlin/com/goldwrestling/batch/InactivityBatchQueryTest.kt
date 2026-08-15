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
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.PassType
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 배치가 회원 수백 명을 N+1 없이 판정할 수 있게 하는 벌크 조회 5종의 필터·정렬을 실제 PostgreSQL
 * (Testcontainers)로 증명하는 통합테스트(BATCH-01·02). 애노테이션 조합은 `PassDeductionCandidateTest`와
 * 동일하게 유지한다(conventions §10.1 — 컨텍스트 캐시 재사용).
 *
 * 시각은 [BatchFixtures.FIXED_TIME]으로 고정한다 — `endDate == today` 같은 경계 케이스를 상대
 * 날짜 계산 없이 그대로 단언하기 위해서다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class InactivityBatchQueryTest {
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
    private lateinit var clock: java.time.Clock

    private var fixtureCounter = 0L

    private val today: LocalDate = BatchFixtures.FIXED_TODAY

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(BatchFixtures.FIXED_TIME.toInstant())
    }

    // ── findMemberIdsWithDeductibleSessionPass ──────────────────────────────

    @Test
    fun `차감 가능한 SESSION_PASS를 가진 회원 id를 중복 없이 오름차순으로 반환한다`() {
        val member1 = persistMember()
        persistSessionPass(member1, remaining = "1.0", endDate = today.plusDays(30))
        persistSessionPass(member1, remaining = "2.0", endDate = today.plusDays(60))
        val member2 = persistMember()
        persistSessionPass(member2, remaining = "1.0", endDate = today.plusDays(30))

        val ids = passRepository.findMemberIdsWithDeductibleSessionPass(today)

        assertThat(ids).containsExactly(member1.id, member2.id)
    }

    @Test
    fun `ON_LEAVE 회원은 차감 가능한 SESSION_PASS가 있어도 결과에서 제외된다`() {
        val member = persistMember(status = MemberStatus.ON_LEAVE)
        persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(30))

        val ids = passRepository.findMemberIdsWithDeductibleSessionPass(today)

        assertThat(ids).doesNotContain(member.id)
    }

    @Test
    fun `잔여가 0인 SESSION_PASS만 가진 회원은 제외된다`() {
        val member = persistMember()
        persistSessionPass(member, remaining = "0.0", endDate = today.plusDays(30))

        val ids = passRepository.findMemberIdsWithDeductibleSessionPass(today)

        assertThat(ids).doesNotContain(member.id)
    }

    @Test
    fun `endDate가 어제인 SESSION_PASS만 가진 회원은 제외된다`() {
        val member = persistMember()
        persistSessionPass(member, remaining = "1.0", endDate = today.minusDays(1))

        val ids = passRepository.findMemberIdsWithDeductibleSessionPass(today)

        assertThat(ids).doesNotContain(member.id)
    }

    @Test
    fun `endDate가 오늘인 SESSION_PASS를 가진 회원은 포함된다`() {
        val member = persistMember()
        persistSessionPass(member, remaining = "1.0", endDate = today)

        val ids = passRepository.findMemberIdsWithDeductibleSessionPass(today)

        assertThat(ids).contains(member.id)
    }

    @Test
    fun `LESSON_PASS·EVENING_MEMBERSHIP만 가진 회원은 제외된다`() {
        val lessonOnly = persistMember()
        persistPass(lessonOnly, type = PassType.LESSON_PASS, remaining = BigDecimal("1.0"), endDate = today.plusDays(30))
        val eveningOnly = persistMember()
        persistPass(eveningOnly, type = PassType.EVENING_MEMBERSHIP, remaining = null, endDate = today.plusDays(30))

        val ids = passRepository.findMemberIdsWithDeductibleSessionPass(today)

        assertThat(ids).doesNotContain(lessonOnly.id, eveningOnly.id)
    }

    @Test
    fun `등록 취소된 SESSION_PASS만 가진 회원은 제외된다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(30))
        cancelPass(pass)

        val ids = passRepository.findMemberIdsWithDeductibleSessionPass(today)

        assertThat(ids).doesNotContain(member.id)
    }

    // ── findLastDeductibleSessionPassRegistrationDates ──────────────────────

    @Test
    fun `findLastDeductibleSessionPassRegistrationDates는 차감 가능한 장 중 가장 최근 등록일을 반환한다`() {
        val member = persistMember()
        persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(30), createdAt = today.minusDays(20).atTime9am())
        persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(60), createdAt = today.minusDays(5).atTime9am())

        val result = passRepository.findLastDeductibleSessionPassRegistrationDates(listOf(member.id!!), today)

        assertThat(result).hasSize(1)
        assertThat(result.first().getMemberId()).isEqualTo(member.id)
        assertThat(result.first().getTimestamp()).isEqualTo(today.minusDays(5).atTime9am())
    }

    @Test
    fun `findLastDeductibleSessionPassRegistrationDates는 만료·소진·취소된 장의 등록일을 후보에서 제외한다`() {
        val member = persistMember()
        val deductibleCreatedAt = today.minusDays(20).atTime9am()
        persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(30), createdAt = deductibleCreatedAt)
        // 더 최근에 등록됐지만 만료·소진·취소돼 후보가 아닌 장 3개 — 이 중 어느 것도 반환되면 안 된다.
        persistSessionPass(member, remaining = "1.0", endDate = today.minusDays(1), createdAt = today.minusDays(1).atTime9am())
        persistSessionPass(member, remaining = "0.0", endDate = today.plusDays(30), createdAt = today.minusDays(1).atTime9am())
        val canceled =
            persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(30), createdAt = today.minusDays(1).atTime9am())
        cancelPass(canceled)

        val result = passRepository.findLastDeductibleSessionPassRegistrationDates(listOf(member.id!!), today)

        assertThat(result).hasSize(1)
        assertThat(result.first().getTimestamp()).isEqualTo(deductibleCreatedAt)
    }

    @Test
    fun `findLastDeductibleSessionPassRegistrationDates는 memberIds에 없는 회원의 행을 포함하지 않는다`() {
        val inScope = persistMember()
        persistSessionPass(inScope, remaining = "1.0", endDate = today.plusDays(30))
        val outOfScope = persistMember()
        persistSessionPass(outOfScope, remaining = "1.0", endDate = today.plusDays(30))

        val result = passRepository.findLastDeductibleSessionPassRegistrationDates(listOf(inScope.id!!), today)

        assertThat(result).extracting<Long> { it.getMemberId() }.containsExactly(inScope.id)
    }

    // ── findDeductibleSessionPasses ──────────────────────────────────────────

    @Test
    fun `findDeductibleSessionPasses는 endDate 오름차순, 동률이면 id 오름차순으로 반환한다`() {
        val member = persistMember()
        val later = persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(60))
        val sooner = persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(10))
        val sameEndDateFirst = persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(10))

        val result = passRepository.findDeductibleSessionPasses(member.id!!, today)

        assertThat(result).extracting<Long> { it.id }.containsExactly(sooner.id, sameEndDateFirst.id, later.id)
    }

    @Test
    fun `findDeductibleSessionPasses는 잔여가 0-5인 장도 포함한다`() {
        val member = persistMember()
        val partial = persistSessionPass(member, remaining = "0.5", endDate = today.plusDays(30))

        val result = passRepository.findDeductibleSessionPasses(member.id!!, today)

        assertThat(result).extracting<Long> { it.id }.containsExactly(partial.id)
    }

    // ── findLastActiveReservationClassDates ─────────────────────────────────

    @Test
    fun `findLastActiveReservationClassDates는 회원별 ACTIVE 예약의 classDate 최댓값을 반환한다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "5.0", endDate = today.plusDays(30))
        persistReservation(member, pass, classDate = today.plusDays(3))
        persistReservation(member, pass, classDate = today.plusDays(10))

        val result = reservationRepository.findLastActiveReservationClassDates(listOf(member.id!!))

        assertThat(result).hasSize(1)
        assertThat(result.first().getMemberId()).isEqualTo(member.id)
        assertThat(result.first().getDate()).isEqualTo(today.plusDays(10))
    }

    @Test
    fun `취소된 예약만 있는 회원은 findLastActiveReservationClassDates 결과에 없다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "5.0", endDate = today.plusDays(30))
        val reservation = persistReservation(member, pass, classDate = today.plusDays(3))
        reservationRepository.cancelByMemberIfActive(reservation.id!!, member, OffsetDateTime.now(clock))

        val result = reservationRepository.findLastActiveReservationClassDates(listOf(member.id!!))

        assertThat(result).isEmpty()
    }

    @Test
    fun `findLastActiveReservationClassDates는 수업 종류를 가리지 않고 LESSON 예약도 인정한다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "5.0", endDate = today.plusDays(30))
        persistReservation(member, pass, classDate = today.plusDays(3), classType = ClassType.LESSON)

        val result = reservationRepository.findLastActiveReservationClassDates(listOf(member.id!!))

        assertThat(result).extracting<LocalDate> { it.getDate() }.containsExactly(today.plusDays(3))
    }

    @Test
    fun `findLastActiveReservationClassDates는 미래 날짜 예약도 최댓값으로 그대로 반환한다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "5.0", endDate = today.plusDays(400))
        persistReservation(member, pass, classDate = today.plusDays(200))

        val result = reservationRepository.findLastActiveReservationClassDates(listOf(member.id!!))

        assertThat(result).extracting<LocalDate> { it.getDate() }.containsExactly(today.plusDays(200))
    }

    // ── findLastPositiveAdjustTimestamps ─────────────────────────────────────

    @Test
    fun `findLastPositiveAdjustTimestamps는 양(+) ADMIN_ADJUST 이력의 occurredAt 최댓값을 반환한다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "5.0", endDate = today.plusDays(30))
        persistPassTransaction(pass, amount = "0.5", reason = TransactionReason.ADMIN_ADJUST, occurredAt = today.minusDays(10).atTime9am())
        persistPassTransaction(pass, amount = "1.0", reason = TransactionReason.ADMIN_ADJUST, occurredAt = today.minusDays(2).atTime9am())

        val result = passTransactionRepository.findLastPositiveAdjustTimestamps(listOf(member.id!!))

        assertThat(result).hasSize(1)
        assertThat(result.first().getTimestamp()).isEqualTo(today.minusDays(2).atTime9am())
    }

    @Test
    fun `음수 ADMIN_ADJUST만 있는 회원은 findLastPositiveAdjustTimestamps 결과에 없다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "5.0", endDate = today.plusDays(30))
        persistPassTransaction(pass, amount = "-0.5", reason = TransactionReason.ADMIN_ADJUST, occurredAt = today.minusDays(2).atTime9am())

        val result = passTransactionRepository.findLastPositiveAdjustTimestamps(listOf(member.id!!))

        assertThat(result).isEmpty()
    }

    @Test
    fun `INITIAL_GRANT 같은 다른 양수 사유는 findLastPositiveAdjustTimestamps 후보에 포함되지 않는다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "5.0", endDate = today.plusDays(30))
        persistPassTransaction(pass, amount = "5.0", reason = TransactionReason.INITIAL_GRANT, occurredAt = today.minusDays(2).atTime9am())

        val result = passTransactionRepository.findLastPositiveAdjustTimestamps(listOf(member.id!!))

        assertThat(result).isEmpty()
    }

    // ── findInactivityEventTimestamps ────────────────────────────────────────

    @Test
    fun `findInactivityEventTimestamps는 INACTIVITY 이력을 건별로 반환한다`() {
        val member = persistMember()
        val pass = persistSessionPass(member, remaining = "3.0", endDate = today.plusDays(30))
        persistPassTransaction(pass, amount = "-1.0", reason = TransactionReason.INACTIVITY, occurredAt = today.minusDays(30).atTime9am())
        persistPassTransaction(pass, amount = "-1.0", reason = TransactionReason.INACTIVITY, occurredAt = today.minusDays(16).atTime9am())
        persistPassTransaction(pass, amount = "-1.0", reason = TransactionReason.INACTIVITY, occurredAt = today.minusDays(2).atTime9am())

        val result = passTransactionRepository.findInactivityEventTimestamps(listOf(member.id!!))

        assertThat(result).hasSize(3)
    }

    // ── findReturnedFromLeaveTimestamps ──────────────────────────────────────

    @Test
    fun `findReturnedFromLeaveTimestamps는 returnedFromLeaveAt이 있는 회원만 반환한다`() {
        val returned = persistMember()
        returned.returnedFromLeaveAt = today.minusDays(5).atTime9am()
        memberRepository.saveAndFlush(returned)
        val neverLeft = persistMember()

        val result = memberRepository.findReturnedFromLeaveTimestamps(listOf(returned.id!!, neverLeft.id!!))

        assertThat(result).extracting<Long> { it.getMemberId() }.containsExactly(returned.id)
        assertThat(result.first().getTimestamp()).isEqualTo(today.minusDays(5).atTime9am())
    }

    // ── memberIds 범위·빈 목록 처리 ───────────────────────────────────────────

    @Test
    fun `네 조회 모두 memberIds에 없는 회원의 행을 포함하지 않고 빈 목록이면 빈 결과를 반환한다`() {
        val inScope = persistMember()
        val outOfScope = persistMember()
        val pass = persistSessionPass(inScope, remaining = "5.0", endDate = today.plusDays(30))
        persistReservation(inScope, pass, classDate = today.plusDays(3))
        persistPassTransaction(pass, amount = "0.5", reason = TransactionReason.ADMIN_ADJUST, occurredAt = today.minusDays(2).atTime9am())
        persistPassTransaction(pass, amount = "-1.0", reason = TransactionReason.INACTIVITY, occurredAt = today.minusDays(2).atTime9am())
        outOfScope.returnedFromLeaveAt = today.minusDays(2).atTime9am()
        memberRepository.saveAndFlush(outOfScope)

        val scoped = listOf(inScope.id!!)
        assertThat(reservationRepository.findLastActiveReservationClassDates(scoped))
            .extracting<Long> { it.getMemberId() }
            .containsOnly(inScope.id)
        assertThat(passTransactionRepository.findLastPositiveAdjustTimestamps(scoped))
            .extracting<Long> { it.getMemberId() }
            .containsOnly(inScope.id)
        assertThat(passTransactionRepository.findInactivityEventTimestamps(scoped))
            .extracting<Long> { it.getMemberId() }
            .containsOnly(inScope.id)
        assertThat(memberRepository.findReturnedFromLeaveTimestamps(scoped)).isEmpty()

        // 빈 memberIds — "in ()" 빈 컬렉션이 예외 없이 빈 결과를 반환하는지 실제로 실행해 확인한다.
        assertThat(reservationRepository.findLastActiveReservationClassDates(emptyList())).isEmpty()
        assertThat(passTransactionRepository.findLastPositiveAdjustTimestamps(emptyList())).isEmpty()
        assertThat(passTransactionRepository.findInactivityEventTimestamps(emptyList())).isEmpty()
        assertThat(memberRepository.findReturnedFromLeaveTimestamps(emptyList())).isEmpty()
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private fun LocalDate.atTime9am(): OffsetDateTime = this.atStartOfDay(BatchFixtures.FIXED_TIME.offset).plusHours(9).toOffsetDateTime()

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(status: MemberStatus = MemberStatus.ACTIVE): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(BatchFixtures.member(songpaBranch(), kakaoId = fixtureCounter, status = status))
    }

    private fun persistAdmin(): Admin {
        fixtureCounter++
        return adminRepository.saveAndFlush(BatchFixtures.admin(loginId = "admin-batch-query-$fixtureCounter"))
    }

    /** 실제 취소 경로로 취소한다 — `status = CANCELED`만 직접 대입하면 `ck_pass_cancellation`(V4)이 거부한다. */
    private fun cancelPass(pass: Pass) {
        passRepository.cancelIfNotCanceled(pass.id!!, "테스트 취소", persistAdmin(), OffsetDateTime.now(clock))
    }

    private fun persistSessionPass(
        member: Member,
        remaining: String,
        endDate: LocalDate,
        createdAt: OffsetDateTime = BatchFixtures.FIXED_TIME,
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass =
        passRepository.saveAndFlush(
            BatchFixtures.sessionPass(
                member = member,
                branch = songpaBranch(),
                registeredBy = persistAdmin(),
                remainingCount = BigDecimal(remaining),
                endDate = endDate,
                createdAt = createdAt,
                status = status,
            ),
        )

    private fun persistPass(
        member: Member,
        type: PassType,
        remaining: BigDecimal?,
        endDate: LocalDate,
    ): Pass =
        passRepository.saveAndFlush(
            BatchFixtures.pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = persistAdmin(),
                type = type,
                remainingCount = remaining,
                endDate = endDate,
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
    private fun persistReservation(
        member: Member,
        pass: Pass,
        classDate: LocalDate,
        classType: ClassType = ClassType.SESSION,
    ): Reservation {
        val session = persistClassSession(classDate, classType)
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

    private fun persistClassSession(
        classDate: LocalDate,
        classType: ClassType,
    ): ClassSession {
        val schedule = songpaSchedule(classType)
        return classSessionRepository.saveAndFlush(
            ClassSession(
                classSchedule = schedule,
                classDate = classDate,
                classType = classType,
                startTime = schedule.startTime,
                endTime = schedule.endTime,
                capacity = schedule.capacity,
                reservedCount = 0,
                status = ClassSessionStatus.SCHEDULED,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun songpaSchedule(classType: ClassType): ClassSchedule =
        classScheduleRepository.findAllByBranchId(songpaBranch().id!!).first { it.classType == classType }
}
