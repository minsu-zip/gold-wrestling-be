package com.goldwrestling.reservation.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 관리자 대리 취소 요청(`POST /api/admin/reservations/{reservationId}/cancellation`, RESV-08).
 *
 * [refund] 기본값은 `true`(policies §3 "관리자의 취소/변경: 취소 시 차감 복구 여부 선택, 기본:
 * 복구") — 요청 바디를 생략하거나 `{}`만 보내도 복구가 기본으로 동작한다. `false`는 당일 불참
 * 연락 등 차감을 유지해야 하는 경우(policies §3 노쇼 규칙)나, D-089 "이용권 등록 취소 선행
 * 조건"의 2단계 절차(대리 취소로 예약을 정리한 뒤 등록을 취소)에 쓴다.
 *
 * **형식만 검증한다**(conventions §6) — 복구 판정 자체(등록 취소된 이용권은 항상 복구하지
 * 않는다, D-091)는 `ReservationRefundPolicy.shouldRestore`가 서비스 계층에서 수행한다.
 */
@Schema(
    description =
        "관리자 대리 취소 요청 — refund 생략 시 기본값 true(복구). " +
            "false는 당일 불참 연락 등 차감을 유지해야 하는 경우에 쓴다",
)
data class AdminCancelReservationRequest(
    @field:Schema(description = "취소 시 이용권 잔여를 복구할지 여부. 기본값 true", example = "true", defaultValue = "true")
    val refund: Boolean = true,
)
