package com.goldwrestling.auth.kakao

/**
 * [KakaoApiClient.fetchUserProfile]이 반환하는, 카카오 사용자 정보의 **평탄화된** 형태.
 *
 * 카카오 응답([KakaoUserResponse])은 `kakao_account.profile.nickname`처럼 중간 노드가 모두 nullable인
 * 중첩 구조다. 그 구조를 서비스 계층까지 그대로 끌고 가면 호출부마다 `?.` 체인을 반복하게 되고,
 * 카카오가 응답 구조를 바꾸면 그 영향이 서비스까지 번진다. 외부 계약의 모양은 이 패키지 안에서 끝내고
 * 바깥에는 "카카오 회원번호 + 닉네임 + 프로필 이미지 URL" 세 값만 넘긴다(D-083).
 *
 * [nickname]·[profileImageUrl]은 동의항목 미추가·동의 거부·사후 철회 시 `null`이다 — 값이 없는 것이
 * 정상 상태이므로 호출부는 null을 오류로 다루지 않는다.
 */
data class KakaoUserProfile(
    val kakaoId: Long,
    val nickname: String?,
    val profileImageUrl: String?,
)
