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
 * `PassRepository.existsActiveEveningMembership`의 수업날 기준 유효성 판정(policies §4.2, D-091,
 * D-128)을 실제 PostgreSQL(Testcontainers)에서 증명하는 통합테스트. 애노테이션 조합은
 * `PassRepositoryTest`와 동일하게 유지한다(conventions §10.1 — 컨텍스트 캐시 재사용). 기존
 * `PassRepositoryTest`는 수정하지 않고 새 파일로 만든다 — 06-05와 다른 플랜이 같은 파일을 동시에
 * 건드리지 않게 하기 위해서다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class PassRepositoryEveningMembershipTest {
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

    private var fixtureCounter = 9500L

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `수업날이 회비 기간 안이면 true를 반환한다`() {
        val member = persistMember()
        persistEveningMembership(member, startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 31))

        val result = passRepository.existsActiveEveningMembership(member.id!!, LocalDate.of(2026, 8, 15))

        assertThat(result).isTrue()
    }

    @Test
    fun `수업날이 종료일 당일이면 true를 반환한다`() {
        val member = persistMember()
        persistEveningMembership(member, startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 31))

        val result = passRepository.existsActiveEveningMembership(member.id!!, LocalDate.of(2026, 8, 31))

        assertThat(result).isTrue()
    }

    @Test
    fun `수업날이 종료일 다음날이면 false를 반환한다`() {
        val member = persistMember()
        persistEveningMembership(member, startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 31))

        val result = passRepository.existsActiveEveningMembership(member.id!!, LocalDate.of(2026, 9, 1))

        assertThat(result).isFalse()
    }

    @Test
    fun `수업날이 시작일 이전이면 false를 반환한다`() {
        val member = persistMember()
        persistEveningMembership(member, startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 31))

        val result = passRepository.existsActiveEveningMembership(member.id!!, LocalDate.of(2026, 7, 31))

        assertThat(result).isFalse()
    }

    @Test
    fun `취소된 회비는 false를 반환한다`() {
        val member = persistMember()
        val admin = persistAdmin()
        val membership =
            persistEveningMembership(member, startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 31))
        passRepository.cancelIfNotCanceled(
            membership.id!!,
            "오등록 정정",
            admin,
            OffsetDateTime.now(clock),
        )

        val result = passRepository.existsActiveEveningMembership(member.id!!, LocalDate.of(2026, 8, 15))

        assertThat(result).isFalse()
    }

    @Test
    fun `SESSION_PASS만 있고 EVENING_MEMBERSHIP이 없으면 false를 반환한다`() {
        val member = persistMember()
        persistSessionPass(member, startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2027, 7, 31))

        val result = passRepository.existsActiveEveningMembership(member.id!!, LocalDate.of(2026, 8, 15))

        assertThat(result).isFalse()
    }

    @Test
    fun `오늘 기준으로는 만료됐지만 수업날 기준으로는 유효한 소급 입력 회비는 true를 반환한다`() {
        (clock as MutableTestClock).setTo(LocalDate.of(2026, 9, 15).atStartOfDay(clock.zone).toInstant())
        val member = persistMember()
        val classDate = LocalDate.of(2026, 8, 15)
        persistEveningMembership(member, startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 31))

        val result = passRepository.existsActiveEveningMembership(member.id!!, classDate)

        assertThat(result).isTrue()
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(): Member {
        fixtureCounter++
        return memberRepository.saveAndFlush(
            Member(
                branch = songpaBranch(),
                name = "회원$fixtureCounter",
                phoneNumber = "0102345$fixtureCounter",
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
                loginId = "admin-evening-$fixtureCounter",
                passwordHash = "{noop}not-used-in-this-test",
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistEveningMembership(
        member: Member,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Pass {
        val admin = persistAdmin()
        return passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = PassType.EVENING_MEMBERSHIP,
                status = PassStatus.ACTIVE,
                startDate = startDate,
                endDate = endDate,
                remainingCount = null,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }

    private fun persistSessionPass(
        member: Member,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Pass {
        val admin = persistAdmin()
        return passRepository.saveAndFlush(
            Pass(
                member = member,
                branch = songpaBranch(),
                registeredBy = admin,
                type = PassType.SESSION_PASS,
                status = PassStatus.ACTIVE,
                startDate = startDate,
                endDate = endDate,
                remainingCount = BigDecimal("1.0"),
                createdAt = OffsetDateTime.now(clock),
            ),
        )
    }
}
