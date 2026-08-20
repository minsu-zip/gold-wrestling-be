package com.goldwrestling.pass.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * 기간·유효기간 통합 수정 요청(`PATCH /api/admin/passes/{passId}/period`, PASS-04·PASS-07, D-062).
 *
 * **형식만 검증한다**(conventions §6) — "횟수권은 시작일을 바꿀 수 없다", "종료일은 시작일보다
 * 앞설 수 없다" 같은 도메인 규칙은 여기 넣지 않는다. `Pass.resolvePeriodChange`가 그 전부를
 * 판정하므로, DTO에 같은 규칙을 중복해서 넣으면 두 곳이 어긋날 때 어느 쪽이 맞는지 알 수 없게 된다.
 */
@Schema(description = "기간·유효기간 수정 요청 — 저녁반은 시작·종료 모두, 횟수권은 종료일만 반영된다(D-062)")
data class ChangePassPeriodRequest(
    @field:Schema(
        description = "새 시작일. 저녁반 회비만 변경 가능하다. 횟수권은 생략하거나 기존 시작일과 같은 값이어야 한다(D-062)",
        example = "2026-03-15",
        nullable = true,
    )
    val newStartDate: LocalDate? = null,
    @field:NotNull
    @field:Schema(description = "새 종료일(유효기간)", example = "2026-05-15")
    val newEndDate: LocalDate,
    @field:NotBlank
    @field:Size(max = 500)
    @field:Schema(description = "변경 사유(필수, 500자 이내)", example = "휴회 보상 연장")
    val reason: String,
)
