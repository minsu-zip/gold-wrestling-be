package com.goldwrestling.member.dto

import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberStatus
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 로그인 응답에 담기는 회원 세션 요약 — FE가 이 값만으로 다음에 띄울 화면을 결정한다(AUTH-06, D-034):
 * - [onboardingCompleted]가 `false`면 온보딩 화면
 * - [rejected]가 `true`면 거절 안내 화면
 * - [status]가 `PENDING`이고 [onboardingCompleted]가 `true`면 승인 대기 화면
 * - [status]가 `ACTIVE`면 정상 진입
 *
 * **`rejectionReason` 원문을 필드로 두지 않는다(D-043).** 관리자가 내부적으로 남긴 거절 사유
 * 메모는 회원에게 그대로 노출하지 않기로 결정했다 — [rejected] 불리언만으로 "거절 안내 화면 진입" 분기에
 * 충분하고, 원문 노출은 내부 메모 유출 위험만 키운다.
 */
@Schema(description = "로그인 응답에 포함되는 회원 세션 요약 — FE 화면 분기용")
data class MemberLoginSummaryResponse(
    @field:Schema(description = "회원 ID") val memberId: Long,
    @field:Schema(description = "회원 상태") val status: MemberStatus,
    @field:Schema(description = "온보딩(실명·전화번호 입력) 완료 여부 — false면 온보딩 화면") val onboardingCompleted: Boolean,
    @field:Schema(description = "가입 거절 여부(D-034) — true면 거절 안내 화면. 사유 원문은 담지 않는다(D-043)")
    val rejected: Boolean,
) {
    companion object {
        fun from(member: Member): MemberLoginSummaryResponse =
            MemberLoginSummaryResponse(
                memberId = requireNotNull(member.id) { "저장되지 않은 Member는 세션 응답으로 변환할 수 없습니다." },
                status = member.status,
                onboardingCompleted = member.isOnboardingCompleted(),
                rejected = member.isRejected(),
            )
    }
}
