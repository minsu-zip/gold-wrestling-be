package com.goldwrestling.member.dto

import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

/**
 * 관리자 회원 상세(`GET /api/admin/members/{memberId}`) 응답.
 *
 * **[MyProfileResponse]와 달리 [rejectionReason] 원문을 포함한다(D-043).** 회원 본인에게는
 * `rejected` 불리언만 노출하고 사유 원문은 관리자 화면에서만 노출하기로 한 결정의 반대편이다 —
 * 이 응답은 관리자 전용 경로(`/api/admin` 하위 전체, `hasRole("ADMIN")`)에서만 도달 가능하다.
 *
 * [branchName]은 `member.branch.name`으로 얻는다. `branch`는 `LAZY` 연관이므로 **트랜잭션이 열려 있는
 * 서비스 계층 안에서 이 DTO로 변환해야 한다** — 트랜잭션 밖(예: 컨트롤러)에서 접근하면
 * `LazyInitializationException`이 난다.
 */
@Schema(description = "관리자 회원 상세 — 거절 사유 원문 포함")
data class MemberDetailResponse(
    @field:Schema(description = "회원 ID") val memberId: Long,
    @field:Schema(description = "실명 — 온보딩 미완료면 null") val name: String?,
    @field:Schema(description = "전화번호(하이픈 제거된 숫자) — 온보딩 미완료면 null") val phoneNumber: String?,
    @field:Schema(description = "회원 상태") val status: MemberStatus,
    @field:Schema(description = "온보딩(실명·전화번호 입력) 완료 여부") val onboardingCompleted: Boolean,
    @field:Schema(description = "가입 거절 여부(D-034)") val rejected: Boolean,
    @field:Schema(description = "거절 사유 원문 — 관리자 전용, 거절되지 않았으면 null(D-043)") val rejectionReason: String?,
    @field:Schema(description = "소속 지점명") val branchName: String,
    @field:Schema(description = "카카오 사용자 식별자") val kakaoId: Long,
    @field:Schema(description = "가입 신청 시각") val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(member: Member): MemberDetailResponse =
            MemberDetailResponse(
                memberId = requireNotNull(member.id) { "저장되지 않은 Member는 응답으로 변환할 수 없습니다." },
                name = member.name,
                phoneNumber = member.phoneNumber,
                status = member.status,
                onboardingCompleted = member.isOnboardingCompleted(),
                rejected = member.isRejected(),
                rejectionReason = member.rejectionReason,
                branchName = member.branch.name,
                kakaoId = member.kakaoId,
                createdAt = member.createdAt,
            )
    }
}
