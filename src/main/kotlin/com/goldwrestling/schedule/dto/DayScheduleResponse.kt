package com.goldwrestling.schedule.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.DayOfWeek
import java.time.LocalDate

/** 하루치 시간표 — `WeeklyScheduleResponse.days`의 원소, 월~일 순으로 7개가 내려온다. */
@Schema(description = "하루치 시간표")
data class DayScheduleResponse(
    @field:Schema(description = "날짜") val date: LocalDate,
    @field:Schema(description = "요일") val dayOfWeek: DayOfWeek,
    @field:Schema(description = "그 날의 셀 목록 — 시작 시각 오름차순, 같은 시각이면 EVENING→SESSION→LESSON 순") val cells: List<ScheduleCellResponse>,
)
