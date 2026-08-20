package com.goldwrestling.auth

import com.goldwrestling.auth.dto.KakaoLoginResponse
import com.goldwrestling.auth.dto.TokenPairResponse
import com.goldwrestling.auth.kakao.KakaoApiClient
import com.goldwrestling.auth.kakao.KakaoUserProfile
import com.goldwrestling.member.MemberRegistrationService
import org.springframework.dao.DataIntegrityViolationException
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
     * 2. 카카오 액세스 토큰 → 카카오 회원번호(kakaoId)와 프로필(닉네임·이미지 URL, D-083, 트랜잭션 없음)
     * 3. kakaoId 기준 회원 find-or-create + 프로필 반영(`MemberRegistrationService`의 짧은 트랜잭션) — 동시 최초
     *    로그인 경쟁(중복 클릭·네트워크 재시도)으로 유니크 제약 위반이 나면 트랜잭션 밖에서 정확히
     *    1회 재시도한다(02-REVIEW.md CR-01)
     * 4. 우리 자체 access/refresh 토큰 쌍 발급(`TokenService`의 짧은 트랜잭션)
     *
     * **왜 재시도가 새 트랜잭션인가:** 이 클래스에는 트랜잭션 애노테이션이 없어(클래스 KDoc 참고)
     * 회원 서비스 메서드 호출은 매번 그 빈의 프록시를 거쳐 독립된 트랜잭션으로 시작한다. 첫 호출이
     * 유니크 제약 위반으로 실패해도 그 트랜잭션은 이미 롤백된 채로 끝나 있으므로, 두 번째 호출은
     * 완전히 새로운 트랜잭션에서 시작해 경쟁 상대가 이미 커밋한 행을 조회로 찾아낸다. 만약 이 메서드
     * 전체를 하나의 트랜잭션 경계로 묶으면 두 호출이 같은 트랜잭션에 참여하게 되어 이 재시도 자체가
     * 무의미해지고(첫 호출의 abort가 두 번째 호출에도 전파됨), conventions §7(트랜잭션 안에서
     * 외부 API 호출 금지)도 위반한다 — 그래서 이 메서드에는 트랜잭션 경계를 열지 않는다.
     */
    fun login(code: String): KakaoLoginResponse {
        val kakaoToken = kakaoApiClient.exchangeToken(code)
        val kakaoUser = kakaoApiClient.fetchUserProfile(kakaoToken.accessToken)

        val memberSummary =
            try {
                findOrCreateMember(kakaoUser)
            } catch (e: DataIntegrityViolationException) {
                findOrCreateMember(kakaoUser)
            }
        val tokenPair = tokenService.issueTokenPair(PrincipalType.MEMBER, memberSummary.memberId)

        return KakaoLoginResponse(
            tokens = TokenPairResponse.from(tokenPair),
            member = memberSummary,
        )
    }

    /**
     * find-or-create 호출을 한 곳에 묶는다 — 위 재시도 경로가 **같은 프로필 값**으로 호출되도록 보장하기
     * 위해서다(두 곳에 인자를 나눠 적으면 한쪽만 고쳐지는 사고가 난다).
     *
     * 닉네임·이미지 URL이 둘 다 `String?`이라 위치 인자로는 조용히 뒤바뀔 수 있으므로 **named argument**로
     * 넘긴다. 이 헬퍼는 다른 빈([MemberRegistrationService])을 호출하므로 self-invocation으로 트랜잭션
     * 프록시를 건너뛰는 문제가 없다 — 호출마다 그 빈의 새 트랜잭션이 시작된다(위 KDoc 참고).
     */
    private fun findOrCreateMember(kakaoUser: KakaoUserProfile) =
        memberRegistrationService.findOrCreateByKakaoId(
            kakaoId = kakaoUser.kakaoId,
            kakaoNickname = kakaoUser.nickname,
            kakaoProfileImageUrl = kakaoUser.profileImageUrl,
        )
}
