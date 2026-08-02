package com.goldwrestling.member

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.domain.Specification
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

/**
 * [MemberSpecifications]가 실제 DB 위에서 policies §5.1(관리자 승인 목록)·D-035(통합 검색)·D-041(전화번호
 * 저장 형식)대로 동작하는지 검증한다. 쿼리는 실행해 보지 않으면 맞는지 알 수 없다(conventions §10.0).
 *
 * MockMvc는 필요 없다 — `MemberRepository.findAll(spec)`을 직접 호출한다. 애노테이션 조합은
 * `@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)`로 Phase 2의 다른
 * 시각 의존 통합테스트와 동일하게 유지한다(conventions §10.1, 컨텍스트 캐시 재사용). 테스트 간 격리는
 * `@Transactional` 롤백을 쓴다 — 동시성 테스트가 아니므로 허용된다(add-domain-test §2).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class MemberSpecificationTest {
    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var clock: Clock

    // ---------- 공통 픽스처 ----------
    // 온보딩 완료 PENDING 2명(m1, m2), 온보딩 미완료 PENDING 1명(m3, name=null), ACTIVE 1명(m4),
    // ON_LEAVE 1명(m5), 이름이 빈 문자열인 PENDING 1명(m6, 온보딩 미완료).
    private lateinit var m1: Member
    private lateinit var m2: Member
    private lateinit var m3: Member
    private lateinit var m4: Member
    private lateinit var m5: Member
    private lateinit var m6: Member

    private fun setUpFixtures() {
        val branch = songpaBranch()
        m1 = persistMember(branch, kakaoId = 9001L, name = "김민수", phoneNumber = "01011112222", status = MemberStatus.PENDING)
        m2 = persistMember(branch, kakaoId = 9002L, name = "이영희", phoneNumber = "01033334444", status = MemberStatus.PENDING)
        m3 = persistMember(branch, kakaoId = 9003L, name = null, phoneNumber = "01044445555", status = MemberStatus.PENDING)
        m4 = persistMember(branch, kakaoId = 9004L, name = "최활성", phoneNumber = "01055556666", status = MemberStatus.ACTIVE)
        m5 = persistMember(branch, kakaoId = 9005L, name = "정휴회", phoneNumber = "01077778888", status = MemberStatus.ON_LEAVE)
        m6 = persistMember(branch, kakaoId = 9006L, name = "", phoneNumber = "01099990000", status = MemberStatus.PENDING)
    }

    // ---------- keywordContains ----------

    @Test
    fun `keywordContains에 null을 주면 조건이 없다`() {
        assertThat(MemberSpecifications.keywordContains(null)).isNull()
    }

    @Test
    fun `keywordContains에 공백만 주면 조건이 없다`() {
        assertThat(MemberSpecifications.keywordContains("   ")).isNull()
    }

    @Test
    fun `이름 일부로 검색하면 해당 회원이 나온다`() {
        setUpFixtures()

        val found = memberRepository.findAll(MemberSpecifications.keywordContains("민수")!!)

        assertThat(found.map { it.id }).containsExactly(m1.id)
    }

    @Test
    fun `전화번호 일부 숫자로 검색하면 해당 회원이 나온다`() {
        setUpFixtures()

        val found = memberRepository.findAll(MemberSpecifications.keywordContains("5556")!!)

        assertThat(found.map { it.id }).containsExactly(m4.id)
    }

    @Test
    fun `하이픈을 포함한 전화번호로 검색해도 하이픈 없이 저장된 회원이 나온다`() {
        setUpFixtures()

        val found = memberRepository.findAll(MemberSpecifications.keywordContains("010-5555")!!)

        assertThat(found.map { it.id }).containsExactly(m4.id)
    }

    @Test
    fun `이름이 null인 회원은 이름 검색에 걸리지 않고 예외도 나지 않는다`() {
        setUpFixtures()

        val found = memberRepository.findAll(MemberSpecifications.keywordContains("4445")!!)

        assertThat(found.map { it.id }).containsExactly(m3.id)
    }

    @Test
    fun `이름 검색은 대소문자를 구분하지 않는다`() {
        val branch = songpaBranch()
        val englishNameMember =
            persistMember(branch, kakaoId = 9101L, name = "John Smith", phoneNumber = "01012340000", status = MemberStatus.ACTIVE)

        val found = memberRepository.findAll(MemberSpecifications.keywordContains("john")!!)

        assertThat(found.map { it.id }).containsExactly(englishNameMember.id)
    }

    @Test
    fun `검색어의 와일드카드 문자는 이스케이프되어 전원을 반환하지 않는다`() {
        setUpFixtures()

        val found = memberRepository.findAll(MemberSpecifications.keywordContains("%")!!)

        assertThat(found).isEmpty()
    }

    // ---------- hasStatus ----------

    @Test
    fun `hasStatus에 null을 주면 조건이 없다`() {
        assertThat(MemberSpecifications.hasStatus(null)).isNull()
    }

    @Test
    fun `hasStatus에 PENDING을 주면 PENDING 회원만 나온다`() {
        setUpFixtures()

        val found = memberRepository.findAll(MemberSpecifications.hasStatus(MemberStatus.PENDING)!!)

        assertThat(found.map { it.id }).containsExactlyInAnyOrder(m1.id, m2.id, m3.id, m6.id)
    }

    // ---------- onboardingCompleted ----------

    @Test
    fun `onboardingCompleted에 null을 주면 조건이 없다`() {
        assertThat(MemberSpecifications.onboardingCompleted(null)).isNull()
    }

    @Test
    fun `onboardingCompleted true는 이름과 전화번호가 모두 채워진 회원만 반환한다`() {
        setUpFixtures()

        val found = memberRepository.findAll(MemberSpecifications.onboardingCompleted(true)!!)

        assertThat(found.map { it.id }).containsExactlyInAnyOrder(m1.id, m2.id, m4.id, m5.id)
    }

    @Test
    fun `onboardingCompleted false는 이름 또는 전화번호가 비어 있는 회원만 반환한다`() {
        setUpFixtures()

        val found = memberRepository.findAll(MemberSpecifications.onboardingCompleted(false)!!)

        assertThat(found.map { it.id }).containsExactlyInAnyOrder(m3.id, m6.id)
    }

    @Test
    fun `빈 문자열로 저장된 name은 온보딩 미완료로 취급된다`() {
        setUpFixtures()

        val found = memberRepository.findAll(MemberSpecifications.onboardingCompleted(false)!!)

        assertThat(found.map { it.id }).contains(m6.id)
    }

    @Test
    fun `onboardingCompleted true 결과는 Member isOnboardingCompleted 필터 결과와 같다`() {
        setUpFixtures()

        val specResultIds = memberRepository.findAll(MemberSpecifications.onboardingCompleted(true)!!).map { it.id }.toSet()
        val entityFilterIds =
            memberRepository
                .findAll()
                .filter { it.isOnboardingCompleted() }
                .map { it.id }
                .toSet()

        assertThat(specResultIds).isEqualTo(entityFilterIds)
    }

    // ---------- 조합 (policies §5.1 승인 대기 목록) ----------

    @Test
    fun `status=PENDING과 onboardingCompleted=true 조합은 승인 대기 회원만 반환한다`() {
        setUpFixtures()

        val combined =
            Specification.allOf(
                listOfNotNull(
                    MemberSpecifications.hasStatus(MemberStatus.PENDING),
                    MemberSpecifications.onboardingCompleted(true),
                ),
            )
        val found = memberRepository.findAll(combined)

        assertThat(found.map { it.id }).containsExactlyInAnyOrder(m1.id, m2.id)
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun persistMember(
        branch: Branch,
        kakaoId: Long,
        name: String?,
        phoneNumber: String?,
        status: MemberStatus,
    ): Member =
        memberRepository.saveAndFlush(
            Member(
                branch = branch,
                name = name,
                phoneNumber = phoneNumber,
                status = status,
                kakaoId = kakaoId,
                createdAt = OffsetDateTime.now(clock),
            ),
        )
}
