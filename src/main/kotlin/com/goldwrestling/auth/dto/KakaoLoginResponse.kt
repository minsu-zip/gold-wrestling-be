package com.goldwrestling.auth.dto

import com.goldwrestling.member.dto.MemberLoginSummaryResponse
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "카카오 로그인 성공 응답 — 토큰과 회원 세션 요약을 함께 담는다")
data class KakaoLoginResponse(
    @field:Schema(description = "발급된 access/refresh 토큰 쌍")
    val tokens: TokenPairResponse,
    @field:Schema(description = "회원 세션 요약 — FE 화면 분기용(AUTH-06, D-034)")
    val member: MemberLoginSummaryResponse,
)
