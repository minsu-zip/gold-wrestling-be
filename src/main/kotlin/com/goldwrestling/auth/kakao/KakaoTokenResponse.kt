package com.goldwrestling.auth.kakao

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * 카카오 `/oauth/token` 응답 매핑 전용 DTO — **우리 API 계약이 아니라 카카오의 외부 계약**이다.
 * 그래서 `auth/dto`(우리 API DTO)가 아니라 `auth/kakao`(카카오 연동 전용) 패키지에 둔다 — 이 구분이
 * 흐려지면 이 타입이 실수로 컨트롤러 응답에 그대로 노출될 위험이 있다.
 *
 * 카카오 응답은 snake_case(`access_token` 등)라 클래스 전체에 [JsonNaming]으로 명명 전략을 걸어
 * `accessToken` ↔ `access_token`처럼 자동 변환한다(필드별 `@JsonProperty`도 대안이지만, 필드 수가
 * 늘어날수록 명명 전략 쪽이 실수(오타로 인한 매핑 누락)에 덜 취약하다).
 *
 * [refreshToken]은 카카오가 발급한 refresh 토큰이다. **우리는 이 값을 어디에도 저장하지 않는다** —
 * 우리 서비스는 카카오 로그인 성공 뒤 우리 자체 refresh 토큰 체계(D-036,
 * [com.goldwrestling.auth.TokenService])를 별도로 발급하므로, 카카오의 refresh는 여기서 매핑되는
 * 순간 버려진다(필드로만 받아 역직렬화 실패를 막고, 저장·전달 코드가 이 값을 참조하지 않게 한다).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val refreshToken: String? = null,
    val scope: String? = null,
)
