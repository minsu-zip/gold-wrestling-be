package com.goldwrestling.auth

import com.goldwrestling.auth.dto.KakaoLoginResponse
import com.goldwrestling.auth.dto.TokenPairResponse
import com.goldwrestling.auth.kakao.KakaoApiClient
import com.goldwrestling.member.MemberRegistrationService
import org.springframework.stereotype.Service

/**
 * 카카오 인가 코드로 로그인하는 전체 흐름을 조율한다(AUTH-01 본체).
 *
 * **클래스에 트랜잭션 애노테이션을 붙이지 않는다.** 이 서비스의 본체는 카카오 API 호출(외부 I/O)이고,
 * DB 쓰기는 [MemberRegistrationService]와 [TokenService]가 각자의 짧은 트랜잭션 안에서 처리한다
 * (conventions §7 "트랜잭션 안에서 외부 API 호출 금지"). 만약 이 메서드 전체를 하나의 트랜잭션으로
 * 묶으면, 카카오 응답이 늦어지는 동안(최대 5초, T-02-23) DB 커넥션 하나를 계속 붙잡게 된다 — 커넥션
 * 풀이 작은 이 프로젝트 규모에서는 로그인 몇 건만으로 풀이 고갈될 수 있다.
 */
@Service
class KakaoAuthService(
    private val kakaoApiClient: KakaoApiClient,
    private val memberRegistrationService: MemberRegistrationService,
    private val tokenService: TokenService,
) {
    /**
     * 1. 카카오 인가 코드 → 카카오 액세스 토큰(트랜잭션 없음)
     * 2. 카카오 액세스 토큰 → 카카오 회원번호(kakaoId, 트랜잭션 없음)
     * 3. kakaoId 기준 회원 find-or-create(`MemberRegistrationService`의 짧은 트랜잭션)
     * 4. 우리 자체 access/refresh 토큰 쌍 발급(`TokenService`의 짧은 트랜잭션)
     */
    fun login(code: String): KakaoLoginResponse {
        val kakaoToken = kakaoApiClient.exchangeToken(code)
        val kakaoId = kakaoApiClient.fetchKakaoId(kakaoToken.accessToken)

        val session = memberRegistrationService.findOrCreateByKakaoId(kakaoId)
        val tokenPair = tokenService.issueTokenPair(PrincipalType.MEMBER, session.memberId)

        return KakaoLoginResponse(
            tokens = TokenPairResponse.from(tokenPair),
            member = session,
        )
    }
}
