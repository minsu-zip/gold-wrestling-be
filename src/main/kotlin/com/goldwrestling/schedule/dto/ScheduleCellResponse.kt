package com.goldwrestling.schedule.dto

import com.goldwrestling.schedule.ClassType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalTime

/**
 * 주간 시간표의 셀 하나(요일×타임) 응답. `DayScheduleResponse.cells`의 원소.
 *
 * **예약자 명단·회원명은 이 DTO에 절대 넣지 않는다(D-096, T-04-18).** 셀에는 예약 인원 숫자만
 * 내려준다 — 회원 간 개인정보 노출이고, 명단은 관리자 스케줄 보드(04-11 이후)에서만 본다.
 *
 * [bookable]이 이 셀의 예약 가능 여부를 나타내는 유일한 필드다 — FE는 [reservable]·[suspended]·
 * 주(week) 판정을 각자 조합하지 않고 이 값 하나로 예약 버튼 활성화를 결정한다.
 */
@Schema(description = "시간표 셀(요일×타임) — 예약 인원 숫자만 내려주고 예약자 명단은 포함하지 않는다(D-096)")
data class ScheduleCellResponse(
    @field:Schema(description = "정기 시간표(ClassSchedule) ID") val classScheduleId: Long,
    @field:Schema(description = "날짜별 수업(ClassSession) ID — 아직 실체화되지 않았으면 null(D-094)") val classSessionId: Long?,
    @field:Schema(description = "수업 종류") val classType: ClassType,
    @field:Schema(description = "시작 시각") val startTime: LocalTime,
    @field:Schema(description = "종료 시각") val endTime: LocalTime,
    @field:Schema(description = "정원 — 저녁반(EVENING)은 null") val capacity: Int?,
    @field:Schema(description = "현재 예약 인원") val reservedCount: Int,
    @field:Schema(description = "예약 대상 수업 종류인지 — 저녁반은 항상 false(D-093)") val reservable: Boolean,
    @field:Schema(description = "휴강 여부") val suspended: Boolean,
    @field:Schema(description = "지금 예약 가능한지 — FE는 이 필드 하나로 예약 버튼 활성화를 결정한다") val bookable: Boolean,
    @field:Schema(description = "본인이 이 셀을 예약했다면 그 예약 ID, 아니면 null") val myReservationId: Long?,
)
