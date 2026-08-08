package com.goldwrestling.schedule.dto

import com.goldwrestling.schedule.ClassType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalTime

/**
 * 관리자 주간 스케줄 보드의 셀 하나(요일×타임) 응답. `AdminBoardDayResponse.cells`의 원소.
 *
 * 회원용 `ScheduleCellResponse`와 데이터 원천은 같지만(같은 시간표·세션을 조회) [reservations](예약자
 * 명단)를 포함한다는 점이 다르다(D-096 "명단은 관리자만"). 회원용 `bookable`·`myReservationId`
 * 필드는 여기 없다 — 관리자는 예약 버튼이 아니라 현황 확인·대리 조작이 목적이라 예약 가능 여부를
 * 별도로 계산해 내려줄 필요가 없다.
 */
@Schema(description = "관리자 스케줄 보드 셀(요일×타임) — 예약자 명단 포함(D-096)")
data class AdminBoardCellResponse(
    @field:Schema(description = "정기 시간표(ClassSchedule) ID") val classScheduleId: Long,
    @field:Schema(description = "날짜별 수업(ClassSession) ID — 아직 실체화되지 않았으면 null(D-094)") val classSessionId: Long?,
    @field:Schema(description = "수업 종류") val classType: ClassType,
    @field:Schema(description = "시작 시각") val startTime: LocalTime,
    @field:Schema(description = "종료 시각") val endTime: LocalTime,
    @field:Schema(description = "정원 — 저녁반(EVENING)은 null") val capacity: Int?,
    @field:Schema(description = "현재 예약 인원(취소 제외)") val reservedCount: Int,
    @field:Schema(description = "예약 대상 수업 종류인지 — 저녁반은 항상 false(D-093)") val reservable: Boolean,
    @field:Schema(description = "휴강 여부") val suspended: Boolean,
    @field:Schema(description = "휴강 사유 — 휴강이 아니면 null") val cancelReason: String?,
    @field:Schema(description = "예약자 명단(활성 예약만) — 관리자 전용(D-096, T-04-52)") val reservations: List<BoardReservationResponse>,
)
