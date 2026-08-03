package com.goldwrestling.auth

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * 인증 스키마 제약(kakao_id UNIQUE, login_id UNIQUE, token_hash UNIQUE,
 * ck_refresh_token_principal CHECK)이 실제 DB에서 위반을 거부하는지 증명한다 (D-036, D-037).
 * 테스트 간 격리는 `@Transactional` 롤백을 쓴다 — 동시성 테스트가 아니므로 허용된다(add-domain-test §2).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class AuthRepositoryIntegrationTest {
    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Test
    fun `kakaoId로 회원을 조회할 수 있다`() {
        val branch = songpaBranch()
        val member = memberRepository.saveAndFlush(newMember(branch, kakaoId = 111L))

        val found = memberRepository.findByKakaoId(111L)

        assertThat(found?.id).isEqualTo(member.id)
    }

    @Test
    fun `같은 kakaoId로 두 번 저장하면 DB 유니크 제약 위반으로 실패한다`() {
        val branch = songpaBranch()
        memberRepository.saveAndFlush(newMember(branch, kakaoId = 222L))

        assertThatThrownBy {
            memberRepository.saveAndFlush(newMember(branch, kakaoId = 222L))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `loginId로 관리자를 조회할 수 있다`() {
        val admin = adminRepository.saveAndFlush(newAdmin(loginId = "admin01"))

        val found = adminRepository.findByLoginId("admin01")

        assertThat(found?.id).isEqualTo(admin.id)
    }

    @Test
    fun `refresh_token에 member_id와 admin_id를 둘 다 채워 저장하면 CHECK 제약 위반으로 실패한다`() {
        val branch = songpaBranch()
        val member = memberRepository.saveAndFlush(newMember(branch, kakaoId = 333L))
        val admin = adminRepository.saveAndFlush(newAdmin(loginId = "admin02"))

        assertThatThrownBy {
            refreshTokenRepository.saveAndFlush(
                newRefreshToken(member = member, admin = admin, tokenHash = "a".repeat(64)),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `refresh_token에 member_id와 admin_id를 둘 다 비워 저장하면 CHECK 제약 위반으로 실패한다`() {
        assertThatThrownBy {
            refreshTokenRepository.saveAndFlush(
                newRefreshToken(member = null, admin = null, tokenHash = "b".repeat(64)),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `같은 token_hash를 두 번 저장하면 유니크 제약 위반으로 실패한다`() {
        val branch = songpaBranch()
        val member1 = memberRepository.saveAndFlush(newMember(branch, kakaoId = 444L))
        val hash = "c".repeat(64)
        refreshTokenRepository.saveAndFlush(newRefreshToken(member = member1, admin = null, tokenHash = hash))
        val member2 = memberRepository.saveAndFlush(newMember(branch, kakaoId = 445L))

        assertThatThrownBy {
            refreshTokenRepository.saveAndFlush(newRefreshToken(member = member2, admin = null, tokenHash = hash))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `미폐기 refresh 토큰만 회원별로 조회된다`() {
        val branch = songpaBranch()
        val member = memberRepository.saveAndFlush(newMember(branch, kakaoId = 555L))

        val active =
            refreshTokenRepository.saveAndFlush(
                newRefreshToken(member = member, admin = null, tokenHash = "d".repeat(64)),
            )
        val revoked = newRefreshToken(member = member, admin = null, tokenHash = "e".repeat(64))
        revoked.revoke(FIXED_TIME)
        refreshTokenRepository.saveAndFlush(revoked)

        val found = refreshTokenRepository.findAllByMemberAndRevokedAtIsNull(member)

        assertThat(found.map { it.id }).containsExactly(active.id)
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun newMember(
        branch: Branch,
        kakaoId: Long,
    ): Member =
        Member(
            branch = branch,
            name = null,
            phoneNumber = null,
            kakaoId = kakaoId,
            createdAt = FIXED_TIME,
        )

    private fun newAdmin(loginId: String): Admin =
        Admin(
            name = "관리자",
            loginId = loginId,
            passwordHash = "{bcrypt}\$2a\$10\$abcdefghijklmnopqrstuv",
            createdAt = FIXED_TIME,
        )

    private fun newRefreshToken(
        member: Member?,
        admin: Admin?,
        tokenHash: String,
    ): RefreshToken =
        RefreshToken(
            member = member,
            admin = admin,
            tokenHash = tokenHash,
            expiresAt = FIXED_TIME.plusDays(14),
            createdAt = FIXED_TIME,
        )

    companion object {
        private val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-01T00:00:00+09:00")
    }
}
