package com.goldwrestling.schedule.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

/**
 * `GET /api/admin/schedule/board` 응답(SCHED-03). 회원용 `WeeklyScheduleResponse`와 달리
 * `bookableWeek` 필드가 없다 — 관리자는 조회 주 범위 제한이 없어(과거·미래 모두 조회 가능) 이번 주
 * 여부를 판정할 이유가 없다.
 */
@Schema(description = "관리자 주간 스케줄 보드 응답 — 조회 주 범위 제한이 없다(관리자 전용)")
data class AdminWeeklyBoardResponse(
    @field:Schema(description = "조회한 주의 월요일") val weekStart: LocalDate,
    @field:Schema(description = "조회한 주의 일요일") val weekEnd: LocalDate,
    @field:Schema(description = "요일별 보드(월~일, 7개)") val days: List<AdminBoardDayResponse>,
)
