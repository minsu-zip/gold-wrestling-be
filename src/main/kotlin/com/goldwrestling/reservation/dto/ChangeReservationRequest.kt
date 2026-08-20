package com.goldwrestling.reservation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

/**
 * 예약 변경 요청(`POST /api/members/me/reservations/{reservationId}/change`, D-090).
 *
 * 변경 대상 예약은 경로 변수(`reservationId`)로 지정하므로, 이 요청 바디는 **새 타임만** 받는다 —
 * `ReserveRequest`와 같은 형태(시간표 id + 날짜, 세션 id를 받지 않는다, D-094).
 *
 * **형식만 검증한다**(conventions §6) — 당일 여부·수업 종류 일치 같은 도메인 규칙은
 * `MemberReservationService.change`가 검증한다.
 */
@Schema(description = "예약 변경 요청 — 새 타임(시간표 id + 날짜)만 받는다. 기존 예약은 경로 변수(reservationId)로 지정한다")
data class ChangeReservationRequest(
    @field:NotNull
    @field:Schema(description = "변경할 정기 시간표(ClassSchedule) ID", example = "12")
    val classScheduleId: Long,
    @field:NotNull
    @field:Schema(description = "변경할 날짜 — 시간표의 요일과 일치해야 한다", example = "2026-08-11")
    val classDate: LocalDate,
)
