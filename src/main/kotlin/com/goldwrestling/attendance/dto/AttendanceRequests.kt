package com.goldwrestling.attendance.dto

import com.goldwrestling.attendance.AttendanceStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

/**
 * 예약제/1:1 출석 체크 요청(`AttendanceService.check`, D-132) — 4필드 전부 필수다.
 *
 * `@field:NotNull`을 붙이지 않는다 — 전부 Kotlin non-null 타입이라, 값이 비면 `@Valid` 검증기가
 * 아니라 Jackson 역직렬화가 먼저 실패해 `MALFORMED_REQUEST`가 나온다(04-08 선례,
 * `AdminReservationController` 테스트에서 확인).
 */
@Schema(description = "예약제/1:1 출석 체크 요청 — 예약자 명단에 없는 회원은 거부된다(D-132)")
data class CheckAttendanceRequest(
    @field:Schema(description = "정기 시간표(ClassSchedule) ID") val classScheduleId: Long,
    @field:Schema(description = "날짜별 수업 날짜(ISO, yyyy-MM-dd)") val classDate: LocalDate,
    @field:Schema(description = "출석 체크 대상 회원 ID") val memberId: Long,
    @field:Schema(description = "출석 상태 — ATTENDED 또는 ABSENT") val status: AttendanceStatus,
)

/**
 * 저녁반 출석 추가 요청(06-07 전용, D-132) — 이 파일에 함께 선언한다(conventions §1 "DTO는 관련된
 * 것끼리 한 파일"). 저녁반은 "추가됨 = 출석"이라 [AttendanceStatus]를 받지 않는다(D-127, 불참 상태
 * 없음).
 */
@Schema(description = "저녁반 출석 추가 요청 — 추가 자체가 출석이다(불참 상태 없음, D-127)")
data class AddEveningAttendanceRequest(
    @field:Schema(description = "정기 시간표(ClassSchedule) ID") val classScheduleId: Long,
    @field:Schema(description = "날짜별 수업 날짜(ISO, yyyy-MM-dd)") val classDate: LocalDate,
    @field:Schema(description = "출석 추가 대상 회원 ID") val memberId: Long,
)
