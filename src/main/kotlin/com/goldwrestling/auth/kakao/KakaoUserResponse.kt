package com.goldwrestling.auth.kakao

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * 카카오 `/v2/user/me` 응답 매핑 전용 DTO — 카카오의 외부 계약이므로 `auth/kakao` 패키지에 둔다
 * ([KakaoTokenResponse] 참고).
 *
 * **실명·전화번호는 카카오에서 가져오지 않는다.** 그 두 값은 온보딩 화면에서 회원이 직접 입력한 것이
 * 정본이고(D-025), 체육관 운영 기준 신원도 그쪽이다 — 카카오 동의항목 심사(비즈앱 전환)에 일정이
 * 묶이지 않도록 한 설계 결정이다. 이 DTO에 실명·연락처 필드를 추가하지 않는다.
 *
 * 반면 **닉네임·프로필 이미지는 표시용 보조 정보로 D-083에 따라 매핑한다.** "내 정보" 화면에
 * 카카오 프로필을 보여주기 위한 것이며, 신원 판단에는 쓰지 않는다.
 *
 * **중간 노드([kakaoAccount]·[KakaoAccount.profile])는 전부 nullable이다.** 카카오 개발자 콘솔에
 * 동의항목(`profile_nickname`·`profile_image`)이 없거나 회원이 동의하지 않으면 해당 객체가 응답에서
 * 통째로 빠진다 — non-null로 선언하면 그 순간 역직렬화가 실패해 로그인 전체가 막힌다(T-083-02).
 *
 * `@JsonNaming`은 상속되지 않으므로 중첩 클래스에도 각각 붙인다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoUserResponse(
    val id: Long,
    val connectedAt: String? = null,
    val kakaoAccount: KakaoAccount? = null,
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class KakaoAccount(
        val profile: Profile? = null,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class Profile(
        val nickname: String? = null,
        val profileImageUrl: String? = null,
        val thumbnailImageUrl: String? = null,
    )
}
