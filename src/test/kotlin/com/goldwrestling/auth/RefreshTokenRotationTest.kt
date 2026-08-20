package com.goldwrestling.auth

import com.goldwrestling.TestcontainersConfiguration
import com.goldwrestling.admin.Admin
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.Branch
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.config.JwtProperties
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.support.MutableTestClock
import com.goldwrestling.support.TestClockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * `TokenService`의 발급·회전·재사용 감지·만료·폐기를 실제 DB·Clock 이동으로 증명하는 통합테스트
 * (D-033, D-036, T-02-10).
 *
 * 이 phase 최초로 `TestClockConfiguration`을 `TestcontainersConfiguration`과 함께 `@Import`한다 —
 * 이후 시각 의존 통합테스트는 이 조합을 그대로 재사용해 컨텍스트 캐시가 늘지 않게 한다(conventions §10.1).
 *
 * `MutableTestClock`은 싱글턴 빈이라 이전 테스트가 앞으로 밀어 둔 시각이 다음 테스트에 그대로
 * 이어진다 — [resetClock]으로 각 테스트 시작 시 **실제 벽시계 현재 시각**으로 되돌려 테스트 순서에
 * 결과가 좌우되지 않게 한다. 하드코딩된 과거 고정 시각(예: `TestClockConfiguration`의 기본값)을
 * 쓰지 않는 이유: `JwtDecoder.decode(...)`의 기본 검증기는 토큰의 `iat`/`exp`를 주입받은 [Clock]이
 * 아니라 **실제 시스템 시각**과 비교한다 — 발급 시각이 실제 현재와 30분 이상 어긋나면 access
 * 토큰이 곧바로 "만료됨"으로 거부된다(발급 직후 디코딩 검증이 필요한 테스트에서 실제로 재현·확인함).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)
@Transactional
class RefreshTokenRotationTest {
    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var adminRepository: AdminRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var jwtProperties: JwtProperties

    @Autowired
    private lateinit var clock: Clock

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @BeforeEach
    fun resetClock() {
        (clock as MutableTestClock).setTo(Instant.now())
    }

    @Test
    fun `토큰 쌍을 발급하면 access는 JWT이고 refresh는 DB에 해시로 1행 저장된다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 1001L))

        val pair = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)

        assertThat(jwtDecoder.decode(pair.accessToken)).isNotNull
        assertThat(refreshTokenRepository.findAllByMemberAndRevokedAtIsNull(member)).hasSize(1)
    }

    @Test
    fun `access 토큰의 sub는 principalId 문자열이고 principalType 클레임이 MEMBER 또는 ADMIN 이다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 1002L))
        val admin = adminRepository.saveAndFlush(newAdmin(loginId = "admin-token-01"))

        val memberPair = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)
        val adminPair = tokenService.issueTokenPair(PrincipalType.ADMIN, admin.id!!)

        val memberDecoded = jwtDecoder.decode(memberPair.accessToken)
        val adminDecoded = jwtDecoder.decode(adminPair.accessToken)
        assertThat(memberDecoded.subject).isEqualTo(member.id.toString())
        assertThat(memberDecoded.getClaimAsString("principalType")).isEqualTo("MEMBER")
        assertThat(adminDecoded.subject).isEqualTo(admin.id.toString())
        assertThat(adminDecoded.getClaimAsString("principalType")).isEqualTo("ADMIN")
    }

    @Test
    fun `access 토큰의 만료는 발급 시각 + accessTokenExpiryMinutes 다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 1003L))
        val issuedAt = OffsetDateTime.now(clock)

        val pair = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)

        assertThat(pair.accessTokenExpiresAt).isEqualTo(issuedAt.plusMinutes(jwtProperties.accessTokenExpiryMinutes))
    }

    @Test
    fun `refresh 토큰의 만료는 발급 시각 + refreshTokenExpiryDays 다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 1004L))
        val issuedAt = OffsetDateTime.now(clock)

        val pair = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)

        assertThat(pair.refreshTokenExpiresAt).isEqualTo(issuedAt.plusDays(jwtProperties.refreshTokenExpiryDays))
    }

    @Test
    fun `유효한 refresh로 회전하면 새 토큰 쌍이 나오고 기존 refresh 행의 revokedAt이 채워진다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 1005L))
        val original = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)

        val rotated = tokenService.rotate(original.refreshToken)

        assertThat(rotated.refreshToken).isNotEqualTo(original.refreshToken)
        val originalRow = refreshTokenRepository.findByTokenHash(sha256(original.refreshToken))!!
        assertThat(originalRow.revokedAt).isNotNull
    }

    @Test
    fun `회전으로 받은 새 refresh는 다시 회전에 쓸 수 있다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 1006L))
        val first = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)
        val second = tokenService.rotate(first.refreshToken)

        val third = tokenService.rotate(second.refreshToken)

        assertThat(third.refreshToken).isNotEqualTo(second.refreshToken)
    }

    @Test
    fun `이미 폐기된 refresh를 다시 제시하면 거부되고 그 주체의 미폐기 refresh가 전부 폐기된다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 1007L))
        val first = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)
        val second = tokenService.rotate(first.refreshToken)

        assertThatThrownBy { tokenService.rotate(first.refreshToken) }
            .isInstanceOf(RefreshTokenInvalidException::class.java)

        val secondRow = refreshTokenRepository.findByTokenHash(sha256(second.refreshToken))!!
        assertThat(secondRow.revokedAt).isNotNull
    }

    @Test
    fun `만료된 refresh를 제시하면 거부된다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 1008L))
        val pair = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)

        (clock as MutableTestClock).advance(Duration.ofDays(jwtProperties.refreshTokenExpiryDays + 1))

        assertThatThrownBy { tokenService.rotate(pair.refreshToken) }
            .isInstanceOf(RefreshTokenInvalidException::class.java)
    }

    @Test
    fun `DB에 없는 refresh 문자열을 제시하면 거부된다`() {
        assertThatThrownBy { tokenService.rotate("존재하지-않는-refresh-토큰-값") }
            .isInstanceOf(RefreshTokenInvalidException::class.java)
    }

    @Test
    fun `revoke를 두 번 호출해도 예외가 나지 않는다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 1009L))
        val pair = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)

        tokenService.revoke(pair.refreshToken)
        tokenService.revoke(pair.refreshToken)
    }

    @Test
    fun `존재하지 않는 refresh로 revoke를 호출해도 예외가 나지 않는다`() {
        tokenService.revoke("존재하지-않는-refresh-토큰-값")
    }

    @Test
    fun `revokeAllForMember 호출 후 해당 회원의 모든 refresh가 폐기되고 다른 회원의 refresh는 살아 있다`() {
        val member1 = memberRepository.saveAndFlush(newMember(kakaoId = 1010L))
        val member2 = memberRepository.saveAndFlush(newMember(kakaoId = 1011L))
        tokenService.issueTokenPair(PrincipalType.MEMBER, member1.id!!)
        tokenService.issueTokenPair(PrincipalType.MEMBER, member1.id!!)
        tokenService.issueTokenPair(PrincipalType.MEMBER, member2.id!!)

        tokenService.revokeAllForMember(member1.id!!)

        assertThat(refreshTokenRepository.findAllByMemberAndRevokedAtIsNull(member1)).isEmpty()
        assertThat(refreshTokenRepository.findAllByMemberAndRevokedAtIsNull(member2)).hasSize(1)
    }

    @Test
    fun `저장된 refresh_token 행의 token_hash가 발급된 원문과 다르다`() {
        val member = memberRepository.saveAndFlush(newMember(kakaoId = 1012L))

        val pair = tokenService.issueTokenPair(PrincipalType.MEMBER, member.id!!)

        val storedHash =
            jdbcClient
                .sql("select token_hash from refresh_token where member_id = :memberId")
                .param("memberId", member.id)
                .query(String::class.java)
                .single()

        assertThat(storedHash).hasSize(64)
        assertThat(storedHash).isNotEqualTo(pair.refreshToken)
    }

    private fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun songpaBranch(): Branch = branchRepository.findByName("송파점")!!

    private fun newMember(kakaoId: Long): Member =
        Member(
            branch = songpaBranch(),
            name = null,
            phoneNumber = null,
            kakaoId = kakaoId,
            createdAt = OffsetDateTime.now(clock),
        )

    private fun newAdmin(loginId: String): Admin =
        Admin(
            name = "관리자",
            loginId = loginId,
            passwordHash = "{bcrypt}\$2a\$10\$abcdefghijklmnopqrstuv",
            createdAt = OffsetDateTime.now(clock),
        )
}
