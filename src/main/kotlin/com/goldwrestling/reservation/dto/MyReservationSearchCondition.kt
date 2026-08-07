package com.goldwrestling.reservation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/**
 * 회원 본인 예약 목록 조회(`GET /api/members/me/reservations`, RESV-05) 쿼리 파라미터 바인딩용.
 * `@ParameterObject @ModelAttribute @Valid`로 받는다(D-054) — 본인 목록은 필터가 필요 없어
 * 페이지네이션만 있다. `PassTransactionSearchCondition`과 동일한 형태·기본값 관례를 따른다.
 */
@Schema(description = "회원 본인 예약 목록 조회 조건 — 페이지네이션만 있고 필터는 없다(활성 예약만 반환)")
data class MyReservationSearchCondition(
    @field:Min(0)
    @field:Schema(description = "페이지 번호(0부터 시작)", defaultValue = "0")
    val page: Int = 0,
    @field:Min(1)
    @field:Max(100)
    @field:Schema(description = "페이지 크기(1~100)", defaultValue = "20")
    val size: Int = 20,
)
