package com.goldwrestling.reservation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

/**
 * 예약 생성 요청(`POST /api/members/me/reservations`, RESV-01·02, 04-08에서 노출).
 *
 * **`classSessionId`를 받지 않는다(D-094, T-04-27).** 세션은 아직 실체화되지 않았을 수 있고,
 * 클라이언트가 임의 세션 id를 지정하면 다른 지점·다른 시간표의 세션에 예약이 붙을 수 있다.
 * 정기 시간표 id + 날짜만 받고, 서버가 `ClassSessionService.getOrCreate`로 세션을 확보한다.
 *
 * **형식만 검증한다**(conventions §6) — 시간표 존재 여부·지점 일치·요일 일치 같은 도메인 규칙은
 * `MemberReservationService.reserve`가 검증한다.
 */
@Schema(description = "예약 생성 요청 — 세션 id가 아니라 시간표 id + 날짜로 받는다(D-094)")
data class ReserveRequest(
    @field:NotNull
    @field:Schema(description = "예약할 정기 시간표(ClassSchedule) ID", example = "12")
    val classScheduleId: Long,
    @field:NotNull
    @field:Schema(description = "예약할 날짜 — 시간표의 요일과 일치해야 한다", example = "2026-08-10")
    val classDate: LocalDate,
)
