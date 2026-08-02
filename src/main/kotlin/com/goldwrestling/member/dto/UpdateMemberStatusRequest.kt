package com.goldwrestling.member.dto

import com.goldwrestling.member.MemberStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

/**
 * 회원 상태 변경 요청(`PATCH /api/admin/members/{memberId}/status`, MEMBER-03).
 *
 * MEMBER-03 요구사항 문구는 `ACTIVE`/`ON_LEAVE`/`INACTIVE` 3종이지만, `PENDING`도 허용한다 —
 * D-034의 재신청 처리(관리자가 거절된 회원을 다시 심사 대상으로 되돌리는 것)가 이 API로만
 * 가능하기 때문이다. `AdminMemberService.changeStatus`가 `PENDING`으로 되돌아갈 때
 * `rejectionReason`을 초기화한다.
 */
@Schema(description = "변경할 회원 상태. PENDING으로 되돌리면 거절 사유가 초기화되어 재신청 상태가 된다 (D-034)")
data class UpdateMemberStatusRequest(
    @field:NotNull
    @field:Schema(description = "변경할 상태(PENDING/ACTIVE/ON_LEAVE/INACTIVE)", example = "ON_LEAVE")
    val status: MemberStatus,
)
