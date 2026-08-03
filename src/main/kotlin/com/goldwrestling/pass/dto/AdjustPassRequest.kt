package com.goldwrestling.pass.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * 관리자 수동 가감 요청(`POST /api/admin/passes/{passId}/adjustments`, PASS-02·PASS-03, D-056).
 *
 * **형식만 검증한다**(conventions §6) — "가감 단위 배수", "결과 잔여 음수 금지" 같은 도메인 규칙은
 * 여기 넣지 않는다. `Pass.validateAdjustment`가 그 전부를 판정하므로, DTO에 같은 규칙을 중복해서
 * 넣으면 두 곳이 어긋날 때 어느 쪽이 맞는지 알 수 없게 된다. [amount]의 `@field:Digits`만 예외 —
 * 이건 DB `DECIMAL(4,1)` 컬럼 자체의 형식 한계라 도메인 규칙이 아니라 "숫자를 이 컬럼에 담을 수
 * 있는 형식인가"의 문제다.
 *
 * [note]의 `@field:NotBlank`는 D-061 "`ADMIN_ADJUST`일 때만 `note` 필수"를 강제하는 지점이다 —
 * 이 DTO는 `ADMIN_ADJUST` 경로에서만 쓰이므로, 여기의 `@NotBlank`가 곧 그 규칙이다. 다른
 * `TransactionReason`(예약 차감 등)은 이 DTO를 쓰지 않으므로 이 강제가 잘못 번지지 않는다.
 */
@Schema(description = "관리자 수동 가감 요청 — 사유(note) 필수")
data class AdjustPassRequest(
    @field:NotNull
    @field:Digits(integer = 3, fraction = 1)
    @field:Schema(description = "가감 수량(음수면 차감). 형식만 검증 — 가감 단위·음수 잔여 금지는 도메인 규칙", example = "1.0")
    val amount: BigDecimal,
    @field:NotBlank
    @field:Size(max = 500)
    @field:Schema(description = "가감 사유(필수, 500자 이내)", example = "이벤트 보상")
    val note: String,
)
