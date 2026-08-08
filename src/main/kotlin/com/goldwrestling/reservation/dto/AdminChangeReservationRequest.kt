package com.goldwrestling.reservation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

/**
 * 관리자 대리 변경 요청(`POST /api/admin/reservations/{reservationId}/change`, RESV-08). 형태는
 * 회원용 [ChangeReservationRequest]와 같다 — 새 타임(시간표 id + 날짜)만 받고, 변경 대상 예약은
 * 경로 변수(`reservationId`)로 지정한다.
 *
 * **형식만 검증한다**(conventions §6) — 당일·예약 창 제약은 관리자 경로에 없고(policies §3),
 * 수업 종류 일치는 `Reservation.assertChangeableByAdmin`이 서비스 계층에서 검증한다.
 */
@Schema(description = "관리자 대리 변경 요청 — 새 타임(시간표 id + 날짜)만 받는다. 기존 예약은 경로 변수(reservationId)로 지정한다")
data class AdminChangeReservationRequest(
    @field:NotNull
    @field:Schema(description = "변경할 정기 시간표(ClassSchedule) ID", example = "12")
    val classScheduleId: Long,
    @field:NotNull
    @field:Schema(description = "변경할 날짜 — 시간표의 요일과 일치해야 한다. 당일·과거 날짜도 허용된다(관리자 경로)", example = "2026-08-11")
    val classDate: LocalDate,
)
