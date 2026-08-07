package com.goldwrestling.pass

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.MemberStatus
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * `PassRepository.findDeductionCandidates`의 정렬·필터를 실제 PostgreSQL(Testcontainers)로
 * 증명하는 통합테스트(D-091). 애노테이션 조합은 `PassRepositoryTest`와 동일하게 유지한다(conventions §10.1).
 *
 * 이 쿼리는 `classDate`(수업 날짜)로 만료를 판정한다 — **예약일이 아니다.** "오늘은 유효, 수업날엔 만료"
 * 케이스가 이 파일의 핵심 테스트다(T-04-23).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class PassDeductionCandidateTest {
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

    private val classDate: LocalDate = LocalDate.of(2026, 9, 1)

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `잔여 1-0 한 장과 잔여 0-5 한 장이 있으면 후보는 1-0짜리 한 장뿐이다`() {
        val member = persistMember()
        val enough = persistPass(member, remaining = "1.0", endDate = classDate.plusDays(30))
        persistPass(member, remaining = "0.5", endDate = classDate.plusDays(30))

        val candidates = findCandidates(member)

        assertThat(candidates).extracting<Long> { it.id }.containsExactly(enough.id)
    }

    @Test
    fun `잔여 0-5 두 장은 합산되지 않아 후보가 0건이다`() {
        val member = persistMember()
        persistPass(member, remaining = "0.5", endDate = classDate.plusDays(30))
        persistPass(member, remaining = "0.5", endDate = classDate.plusDays(30))

        val candidates = findCandidates(member)

        assertThat(candidates).isEmpty()
    }

    @Test
    fun `endDate가 수업날과 같으면 후보에 포함된다`() {
        val member = persistMember()
        val pass = persistPass(member, remaining = "1.0", endDate = classDate)

        val candidates = findCandidates(member)

        assertThat(candidates).extracting<Long> { it.id }.containsExactly(pass.id)
    }

    @Test
    fun `endDate가 수업날 하루 전이면 제외된다`() {
        val member = persistMember()
        persistPass(member, remaining = "1.0", endDate = classDate.minusDays(1))

        val candidates = findCandidates(member)

        assertThat(candidates).isEmpty()
    }

    @Test
    fun `오늘은 유효하지만 수업날에는 만료되는 이용권은 제외된다`() {
        val member = persistMember()
        // 오늘(clock 기준) 이후이지만 예약하려는 수업 날짜(classDate)보다는 이전인 종료일 —
        // 예약일이 아니라 수업날 기준으로 판정해야만 이 케이스가 걸러진다.
        persistPass(member, remaining = "1.0", endDate = classDate.minusDays(10))

        val candidates = findCandidates(member)

        assertThat(candidates).isEmpty()
    }

    @Test
    fun `등록 취소된 이용권은 잔여가 남아 있어도 제외된다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val pass = persistPass(member, remaining = "1.0", endDate = classDate.plusDays(30))
        passRepository.cancelIfNotCanceled(pass.id!!, "오등록 정정", admin, OffsetDateTime.now(clock))

        val candidates = findCandidates(member)

        assertThat(candidates).isEmpty()
    }

    @Test
    fun `SESSION_PASS만 보유한 회원의 LESSON 예약은 후보가 0건이다`() {
        val member = persistMember()
        persistPass(member, remaining = "1.0", endDate = classDate.plusDays(30), type = PassType.SESSION_PASS)

        val candidates =
            passRepository.findDeductionCandidates(
                member.id!!,
                PassType.LESSON_PASS,
                classDate,
                BigDecimal("1.0"),
            )

        assertThat(candidates).isEmpty()
    }

    @Test
    fun `잔여 1-0 두 장은 endDate가 빠른 쪽이 먼저 온다`() {
        val member = persistMember()
        val later = persistPass(member, remaining = "1.0", endDate = classDate.plusDays(60))
        val sooner = persistPass(member, remaining = "1.0", endDate = classDate.plusDays(10))

        val candidates = findCandidates(member)

        assertThat(candidates).extracting<Long> { it.id }.containsExactly(sooner.id, later.id)
    }

    @Test
    fun `잔여 1-0 두 장이 endDate까지 같으면 id가 작은 쪽이 먼저 온다`() {
        val member = persistMember()
        val sameEndDate = classDate.plusDays(30)
        val first = persistPass(member, remaining = "1.0", endDate = sameEndDate)
        val second = persistPass(member, remaining = "1.0", endDate = sameEndDate)

        val candidates = findCandidates(member)

        assertThat(candidates).extracting<Long> { it.id }.containsExactly(first.id, second.id)
    }

    @Test
    fun `다른 회원의 이용권은 후보에 포함되지 않는다`() {
        val me = persistMember()
        val other = persistMember()
        persistPass(other, remaining = "1.0", endDate = classDate.plusDays(30))

        val candidates = findCandidates(me)

        assertThat(candidates).isEmpty()
    }

    private fun findCandidates(member: Member): List<Pass> =
        passRepository.findDeductionCandidates(
            member.id!!,
            PassType.SESSION_PASS,
            classDate,
            BigDecimal("1.0"),
        )

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

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
                loginId = "admin-deduction-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistPass(
        member: Member,
        remaining: String,
        endDate: LocalDate,
        type: PassType = PassType.SESSION_PASS,
    ): Pass {
        val admin = persistAdmin()
        return passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = type,
                status = PassStatus.ACTIVE,
                startDate = endDate.minusYears(1).plusDays(1),
                endDate = endDate,
                remainingCount = BigDecimal(remaining),
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
