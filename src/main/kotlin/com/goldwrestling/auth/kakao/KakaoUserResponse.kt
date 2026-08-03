package com.goldwrestling.auth.kakao

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * 카카오 `/v2/user/me` 응답 매핑 전용 DTO — 카카오의 외부 계약이므로 `auth/kakao` 패키지에 둔다
 * ([KakaoTokenResponse] 참고).
 *
 * **카카오 계정·프로필 관련 나머지 필드(닉네임·프로필 사진 등)는 매핑하지 않는다.** 카카오는 로그인(인증 수단)
 * 으로만 쓰고, 실명·전화번호는 온보딩 화면에서 회원이 직접 입력한다(D-025) — 카카오 동의항목 심사
 * (비즈앱 전환)에 이 phase의 일정이 묶이지 않도록 하기 위한 설계 결정이다. 이 DTO에 그 필드들을
 * 추가하면 "카카오에서 실명·연락처를 가져온다"는 잘못된 인상을 준다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoUserResponse(
    val id: Long,
    val connectedAt: String? = null,
)
