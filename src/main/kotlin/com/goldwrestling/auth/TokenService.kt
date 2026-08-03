package com.goldwrestling.auth

import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.config.JwtProperties
import com.goldwrestling.member.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.OffsetDateTime
import java.util.Base64

/**
 * 회원·관리자가 공유하는 토큰 발급·회전 엔진 (D-026 "동일한 JWT 체계").
 *
 * access는 자체 서명 JWT(HS256, [com.goldwrestling.config.JwtConfig])이고, refresh는 DB에 해시로만
 * 저장되는 고엔트로피 난수 문자열이다(D-036) — 조회가 항상 DB를 거쳐야 하므로 자기기술적인 JWT일
 * 이유가 없다. refresh는 사용할 때마다 회전하고, 이미 폐기된 토큰의 재사용은 그 주체의 모든 refresh를
 * 폐기하는 신호로 취급한다(D-036, T-02-10).
 */
@Service
@Transactional(readOnly = true)
class TokenService(
    private val jwtEncoder: JwtEncoder,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberRepository: MemberRepository,
    private val adminRepository: AdminRepository,
    private val jwtProperties: JwtProperties,
    private val clock: Clock,
) {
    private val secureRandom = SecureRandom()

    /**
     * 새 access/refresh 토큰 쌍을 발급한다. 주체([principalType]/[principalId])가 실제로
     * 존재하지 않으면(운영 데이터 불일치) [IllegalStateException]을 던진다 — 사용자 대면 예외가
     * 아니라 이 서비스를 호출하는 코드의 전제 조건 위반이다.
     */
    @Transactional
    fun issueTokenPair(
        principalType: PrincipalType,
        principalId: Long,
    ): TokenPair {
        val now = OffsetDateTime.now(clock)
        val (accessToken, accessTokenExpiresAt) = issueAccessToken(principalType, principalId, now)

        val refreshTokenValue = generateRefreshTokenValue()
        val refreshTokenExpiresAt = now.plusDays(jwtProperties.refreshTokenExpiryDays)
        val refreshTokenEntity =
            when (principalType) {
                PrincipalType.MEMBER -> {
                    val member =
                        memberRepository.findById(principalId).orElseThrow {
                            IllegalStateException("토큰을 발급하려는 회원(id=$principalId)을 찾을 수 없습니다.")
                        }
                    RefreshToken(
                        member = member,
                        admin = null,
                        tokenHash = hash(refreshTokenValue),
                        expiresAt = refreshTokenExpiresAt,
                        createdAt = now,
                    )
                }

                PrincipalType.ADMIN -> {
                    val admin =
                        adminRepository.findById(principalId).orElseThrow {
                            IllegalStateException("토큰을 발급하려는 관리자(id=$principalId)를 찾을 수 없습니다.")
                        }
                    RefreshToken(
                        member = null,
                        admin = admin,
                        tokenHash = hash(refreshTokenValue),
                        expiresAt = refreshTokenExpiresAt,
                        createdAt = now,
                    )
                }
            }
        refreshTokenRepository.save(refreshTokenEntity)

        return TokenPair(
            accessToken = accessToken,
            refreshToken = refreshTokenValue,
            accessTokenExpiresAt = accessTokenExpiresAt,
            refreshTokenExpiresAt = refreshTokenExpiresAt,
        )
    }

    /**
     * refresh 토큰을 회전한다. 이미 폐기된 토큰이 다시 제시되면 재사용으로 간주해 그 주체의
     * 미폐기 refresh를 전부 폐기한 뒤 거부한다(T-02-10). 만료된 토큰은 그 행만 폐기하고 거부한다.
     *
     * `noRollbackFor`를 붙인 이유: 재사용 감지·만료 처리는 실패 응답([RefreshTokenInvalidException])을
     * 주면서도 그 과정에서 실행한 폐기는 DB에 남아야 하는 경로다. 이 예외는 [RuntimeException]을
     * 상속하는 [com.goldwrestling.common.error.DomainException]이라, 스프링 기본 규칙(런타임 예외 =
     * 롤백 대상)에 걸리면 방금 실행한 폐기까지 통째로 롤백된다 — D-036이 명시한 재사용 감지가
     * 로그만 남기고 실제로는 아무것도 폐기하지 않는 결과가 된다. `rotate`는 이 메서드 안에서
     * 폐기 외의 다른 상태를 바꾸지 않으므로, 실패 응답 경로에서 커밋해도 절반만 반영되는 위험이 없다.
     */
    @Transactional(noRollbackFor = [RefreshTokenInvalidException::class])
    fun rotate(rawRefreshToken: String): TokenPair {
        val existing =
            refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                ?: throw RefreshTokenInvalidException()
        val now = OffsetDateTime.now(clock)

        // 벌크 UPDATE(clearAutomatically = true)는 실행되는 즉시 영속성 컨텍스트를 비운다.
        // 그 이후 existing은 준영속 상태가 되어 LAZY 연관(member/admin)에 접근하면
        // LazyInitializationException이 난다 — 그 전에 필요한 값을 전부 지역 변수로 옮겨 둔다.
        val id = existing.id!!
        val principalType = existing.principalType()
        val principalId = existing.principalId()
        val alreadyRevoked = existing.isRevoked()
        val expired = existing.isExpired(now)

        if (alreadyRevoked) {
            logger.warn(
                "폐기된 refresh 토큰 재사용 감지 — 해당 주체의 모든 refresh를 폐기합니다. principalType={}, principalId={}",
                principalType,
                principalId,
            )
            revokeAllUsableFor(principalType, principalId, now)
            throw RefreshTokenInvalidException()
        }

        if (expired) {
            refreshTokenRepository.revokeIfUsable(id, now)
            throw RefreshTokenInvalidException()
        }

        // 갱신 행 수 0 = 이 트랜잭션이 경쟁에서 졌다 = 다른 트랜잭션이 방금 이 토큰을 폐기했다.
        // 스냅샷(alreadyRevoked)은 false였지만 그 사이 다른 요청이 같은 토큰으로 먼저 회전에
        // 성공했다는 뜻이므로, 이 요청은 재사용 시도로 취급해 주체의 나머지 refresh까지 폐기한다.
        if (refreshTokenRepository.revokeIfUsable(id, now) == 0) {
            logger.warn(
                "회전 경쟁에서 패배 — 재사용으로 간주해 해당 주체의 모든 refresh를 폐기합니다. principalType={}, principalId={}",
                principalType,
                principalId,
            )
            revokeAllUsableFor(principalType, principalId, now)
            throw RefreshTokenInvalidException()
        }

        // 준영속이 된 existing을 다시 만지지 않고, 미리 담아 둔 값으로 새 토큰 쌍을 발급한다.
        return issueTokenPair(principalType, principalId)
    }

    /** 로그아웃. 존재하지 않는 토큰이어도 조용히 반환한다 — 로그아웃은 항상 성공해야 한다(멱등). */
    @Transactional
    fun revoke(rawRefreshToken: String) {
        val target = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)) ?: return
        target.revoke(OffsetDateTime.now(clock))
    }

    /** 강제 로그아웃(D-044) — 회원 상태 변경이 `ACTIVE`가 아니게 될 때 호출된다. 회원이 없으면 조용히 반환한다. */
    @Transactional
    fun revokeAllForMember(memberId: Long) {
        val member = memberRepository.findById(memberId).orElse(null) ?: return
        val now = OffsetDateTime.now(clock)
        refreshTokenRepository.findAllByMemberAndRevokedAtIsNull(member).forEach { it.revoke(now) }
    }

    /**
     * 주체의 미폐기 refresh를 전부 조건부 벌크 UPDATE로 폐기한다. 엔티티가 아니라
     * principalType/principalId를 받는 이유: 벌크 UPDATE([RefreshTokenRepository.revokeIfUsable])
     * 이후 호출자([rotate])의 `existing`은 이미 준영속 상태라, 여기서 그 엔티티의 LAZY 연관
     * (`member`/`admin`)을 만지면 LazyInitializationException이 난다.
     */
    private fun revokeAllUsableFor(
        principalType: PrincipalType,
        principalId: Long,
        at: OffsetDateTime,
    ) {
        when (principalType) {
            PrincipalType.MEMBER -> refreshTokenRepository.revokeAllUsableByMemberId(principalId, at)
            PrincipalType.ADMIN -> refreshTokenRepository.revokeAllUsableByAdminId(principalId, at)
        }
    }

    private fun issueAccessToken(
        principalType: PrincipalType,
        principalId: Long,
        issuedAt: OffsetDateTime,
    ): Pair<String, OffsetDateTime> {
        val accessTokenExpiresAt = issuedAt.plusMinutes(jwtProperties.accessTokenExpiryMinutes)
        val claims =
            JwtClaimsSet
                .builder()
                .issuedAt(issuedAt.toInstant())
                .expiresAt(accessTokenExpiresAt.toInstant())
                .subject(principalId.toString())
                .claim("principalType", principalType.name)
                // 회원 상태(status)는 여기 담지 않는다(D-033) — 담으면 상태가 바뀐 뒤에도 이 access 토큰이
                // 만료되기 전까지는 최대 accessTokenExpiryMinutes만큼 오래된 상태를 인가 판단에 쓰게 된다.
                // 상태 게이트는 항상 DB의 현재 상태를 재조회해서 검사한다(T-02-13).
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return token to accessTokenExpiresAt
    }

    /** `SecureRandom` 32바이트(256비트) 난수 — `Random`/타임스탬프 기반 생성 금지(T-02-12). */
    private fun generateRefreshTokenValue(): String {
        val randomBytes = ByteArray(REFRESH_TOKEN_BYTES)
        secureRandom.nextBytes(randomBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }

    /**
     * SHA-256 해시(소문자 hex 64자)만 저장한다 — 원문은 DB 어디에도 남지 않는다(D-036).
     * refresh 값은 이미 256비트 고엔트로피 난수라 비밀번호와 달리 느린 적응형 해시(BCrypt 등)가
     * 필요 없다 — 사전 대입 공격 자체가 성립하지 않는다.
     */
    private fun hash(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TokenService::class.java)
        private const val REFRESH_TOKEN_BYTES = 32
    }
}
