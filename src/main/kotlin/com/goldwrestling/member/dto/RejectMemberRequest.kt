package com.goldwrestling.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 가입 거절 요청(`POST /api/admin/members/{memberId}/rejection`, D-034). 형식 검증만 여기서
 * 하고(공백 불가, 컬럼 길이 500 이내), "PENDING이 아닌 회원 거절" 같은 도메인 규칙은
 * `AdminMemberService.reject`가 검사한다(conventions §6).
 */
@Schema(description = "거절 사유. 관리자 전용 정보이며 회원에게 원문이 노출되지 않는다 (D-043)")
data class RejectMemberRequest(
    @field:NotBlank
    @field:Size(max = 500)
    @field:Schema(description = "거절 사유(관리자 전용, 500자 이내)", example = "제출한 정보와 실제 신원이 일치하지 않습니다.")
    val reason: String,
)
