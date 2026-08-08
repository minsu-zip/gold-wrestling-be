package com.goldwrestling.schedule.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * 휴강 처리 요청(`POST /api/admin/class-sessions/suspension`, RESV-09, policies §7).
 *
 * **세션 id가 아니라 (시간표, 날짜)로 받는다(D-094).** 아직 예약이 없어 세션 행이 없는 타임도
 * 휴강 처리할 수 있어야 하므로 [ReserveRequest][com.goldwrestling.reservation.dto.ReserveRequest]와
 * 같은 이유로 이 형태를 쓴다 — 서버가 `ClassSessionService.getOrCreate`로 세션을 확보한다.
 *
 * **형식만 검증한다**(conventions §6) — 시간표 존재 여부·요일 일치·이미 휴강인지 같은 도메인 규칙은
 * `AdminScheduleService.suspend`가 검증한다.
 */
@Schema(description = "휴강 처리 요청 — 세션 id가 아니라 시간표 id + 날짜로 받는다(D-094)")
data class SuspendClassSessionRequest(
    @field:NotNull
    @field:Schema(description = "휴강할 정기 시간표(ClassSchedule) ID", example = "12")
    val classScheduleId: Long,
    @field:NotNull
    @field:Schema(description = "휴강할 날짜 — 시간표의 요일과 일치해야 한다", example = "2026-08-15")
    val classDate: LocalDate,
    @field:NotBlank
    @field:Size(max = 500)
    @field:Schema(description = "휴강 사유(500자 이내)", example = "공휴일 휴관")
    val reason: String,
)
