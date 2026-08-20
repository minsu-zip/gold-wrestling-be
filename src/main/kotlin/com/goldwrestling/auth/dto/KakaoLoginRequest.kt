package com.goldwrestling.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/**
 * `redirectUri`를 받지 않는다(D-046) — 서버가 `KAKAO_REDIRECT_URI` 환경변수 값을 그대로 카카오 토큰
 * 교환에 사용한다. 클라이언트가 임의의 redirect_uri를 보내 검증을 우회하지 못하게 하는 설계다.
 */
@Schema(description = "카카오 인가 코드 로그인 요청")
data class KakaoLoginRequest(
    @field:NotBlank
    @field:Schema(description = "카카오 인가 코드(FE가 카카오 리다이렉트에서 전달받은 값)")
    val code: String,
)
