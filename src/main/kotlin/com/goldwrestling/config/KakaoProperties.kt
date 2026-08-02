package com.goldwrestling.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 카카오 OAuth 인가코드 연동 설정(D-032).
 *
 * `.env` 키 매핑:
 * - `restApiKey` ← `KAKAO_REST_API_KEY`
 * - `clientSecret` ← `KAKAO_CLIENT_SECRET`
 * - `redirectUri` ← `KAKAO_REDIRECT_URI` — 카카오 개발자 콘솔에 등록한 값과 **정확히 동일**해야 하고,
 *   요청 본문으로 받지 않는다(D-046) — 클라이언트가 임의의 redirect_uri를 보내 검증을 우회하지 못하게 한다.
 * - `tokenUri`/`userInfoUri`는 `.env`로 주입하지 않는다(기본값 사용) — 테스트에서만 목 서버 주소로
 *   교체해 실제 네트워크 호출 없이 카카오 연동을 검증할 수 있도록 프로퍼티로 분리해 두었다.
 */
@ConfigurationProperties(prefix = "goldwrestling.kakao")
data class KakaoProperties(
    val restApiKey: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val tokenUri: String = "https://kauth.kakao.com/oauth/token",
    val userInfoUri: String = "https://kapi.kakao.com/v2/user/me",
)
