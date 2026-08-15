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
import com.goldwrestling.pass.PassType
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
        persistSessionPass(member, remaining = "1.0", endDate = today.plusDays(30), status = PassStatus.CANCELED)

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
        persistSessionPass(
            member,
            remaining = "1.0",
            endDate = today.plusDays(30),
            createdAt = today.minusDays(1).atTime9am(),
            status = PassStatus.CANCELED,
        )

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
}
