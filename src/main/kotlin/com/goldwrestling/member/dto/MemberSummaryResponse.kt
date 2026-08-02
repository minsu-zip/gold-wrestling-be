package com.goldwrestling.member.dto

import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

/**
 * 관리자 회원 목록(`GET /api/admin/members`)의 항목 하나. **`rejectionReason`을 담지 않는다** — 목록은
 * 스캔용이고 사유 원문은 상세(`MemberDetailResponse`)에서만 노출한다(D-043, T-02-36).
 */
@Schema(description = "관리자 회원 목록 항목")
data class MemberSummaryResponse(
    @field:Schema(description = "회원 ID") val memberId: Long,
    @field:Schema(description = "실명 — 온보딩 미완료면 null") val name: String?,
    @field:Schema(description = "전화번호(하이픈 제거된 숫자) — 온보딩 미완료면 null") val phoneNumber: String?,
    @field:Schema(description = "회원 상태") val status: MemberStatus,
    @field:Schema(description = "온보딩(실명·전화번호 입력) 완료 여부") val onboardingCompleted: Boolean,
    @field:Schema(description = "가입 신청 시각") val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(member: Member): MemberSummaryResponse =
            MemberSummaryResponse(
                memberId = requireNotNull(member.id) { "저장되지 않은 Member는 응답으로 변환할 수 없습니다." },
                name = member.name,
                phoneNumber = member.phoneNumber,
                status = member.status,
                onboardingCompleted = member.isOnboardingCompleted(),
                createdAt = member.createdAt,
            )
    }
}
