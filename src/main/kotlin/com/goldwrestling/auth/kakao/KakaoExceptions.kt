package com.goldwrestling.auth.kakao

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

/**
 * 카카오가 인가 코드 교환 또는 사용자 조회를 4xx로 거부했을 때 — 잘못되거나 만료된 인가 코드,
 * `redirect_uri` 불일치 등. [message]는 사용자 대면 문구만 담는다 — 원인(HTTP 상태 등)은
 * [com.goldwrestling.auth.kakao.KakaoApiClient]가 서버 로그에만 남긴다.
 */
class KakaoAuthFailedException :
    DomainException(
        ErrorCode.KAKAO_AUTH_FAILED,
        "카카오 로그인에 실패했습니다. 다시 시도해 주세요.",
    )

/**
 * 카카오 API가 5xx를 반환하거나 응답하지 않을 때(연결 실패·타임아웃 포함, T-02-23).
 */
class KakaoUnavailableException :
    DomainException(
        ErrorCode.KAKAO_UNAVAILABLE,
        "카카오 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    )
