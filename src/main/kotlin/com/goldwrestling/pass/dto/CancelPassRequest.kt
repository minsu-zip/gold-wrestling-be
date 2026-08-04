package com.goldwrestling.pass.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 등록 취소 요청(`POST /api/admin/passes/{passId}/cancellation`, PASS-08, D-059). 이 사유는
 * `pass.cancel_reason`과 상쇄 이력의 `note`에 함께 저장되며 관리자 전용 정보다(D-043의 연장) —
 * 회원 본인 조회(03-10)는 취소된 이용권 자체를 응답에서 제외하므로 이 값이 회원에게 노출될 경로가 없다.
 */
@Schema(description = "취소 사유. 관리자 전용 정보이며 pass.cancel_reason과 상쇄 이력 note에 함께 저장된다")
data class CancelPassRequest(
    @field:NotBlank
    @field:Size(max = 500)
    @field:Schema(description = "취소 사유(관리자 전용, 500자 이내)", example = "관리자 오등록으로 인한 취소")
    val reason: String,
)
