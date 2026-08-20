package com.goldwrestling.schedule.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

/** `GET /api/members/me/schedule` 응답(SCHED-01·SCHED-02). */
@Schema(description = "주간 시간표 응답")
data class WeeklyScheduleResponse(
    @field:Schema(description = "조회한 주의 월요일") val weekStart: LocalDate,
    @field:Schema(description = "조회한 주의 일요일") val weekEnd: LocalDate,
    @field:Schema(description = "예약 가능한 주(이번 주)인지 — 다음 주 조회면 false") val bookableWeek: Boolean,
    @field:Schema(description = "요일별 시간표(월~일, 7개)") val days: List<DayScheduleResponse>,
)
