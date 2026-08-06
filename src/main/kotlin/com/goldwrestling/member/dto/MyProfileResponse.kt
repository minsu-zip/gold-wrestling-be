package com.goldwrestling.member.dto

import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberStatus
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 본인 프로필 조회(`GET /api/members/me`)·온보딩 제출(`POST /api/members/me/onboarding`) 공통 응답.
 *
 * [MemberLoginSummaryResponse]와의 차이: 저쪽은 카카오 로그인 직후 다음 화면을 결정하기 위한 최소 요약
 * (`memberId`·`status`·`onboardingCompleted`·`rejected`)이고, 이쪽은 이름·전화번호까지 포함하는
 * "내 정보" 화면용 프로필이다.
 *
 * 카카오 프로필([kakaoNickname]·[kakaoProfileImageUrl])은 **이 응답에만** 담는다(D-083). 관리자 화면
 * 응답(`MemberDetailResponse`·`MemberSummaryResponse`)과 로그인 요약([MemberLoginSummaryResponse])에는
 * 넣지 않는다 — 표시 요구가 "내 정보" 화면 하나뿐이고, 필요 없는 곳까지 개인정보를 넓히지 않는다.
 *
 * **`rejectionReason` 원문을 담지 않는다(D-043).** 거절 안내 화면은 [rejected] 불리언만으로
 * 충분하고, 관리자가 내부적으로 남긴 사유 원문은 관리자 화면에서만 노출한다.
 */
@Schema(description = "본인 프로필 — 이름·전화번호·상태와 화면 분기용 플래그를 담는다")
data class MyProfileResponse(
    @field:Schema(description = "회원 ID") val memberId: Long,
    @field:Schema(description = "실명 — 온보딩 미완료면 null") val name: String?,
    @field:Schema(description = "전화번호(하이픈 제거된 숫자) — 온보딩 미완료면 null") val phoneNumber: String?,
    @field:Schema(description = "회원 상태") val status: MemberStatus,
    @field:Schema(description = "온보딩(실명·전화번호 입력) 완료 여부") val onboardingCompleted: Boolean,
    @field:Schema(description = "가입 거절 여부(D-034) — true면 거절 안내 화면. 사유 원문은 담지 않는다(D-043)")
    val rejected: Boolean,
    @field:Schema(
        description =
            "카카오 프로필 닉네임 — 표시용 보조 정보이며 운영 기준 신원은 name이다(D-083). " +
                "카카오 동의항목에 동의하지 않았거나 철회했으면 null",
    )
    val kakaoNickname: String?,
    @field:Schema(
        description =
            "카카오 프로필 이미지 URL(640px) — 카카오 동의항목에 동의하지 않았거나 철회했으면 null(D-083)",
    )
    val kakaoProfileImageUrl: String?,
) {
    companion object {
        fun from(member: Member): MyProfileResponse =
            MyProfileResponse(
                memberId = requireNotNull(member.id) { "저장되지 않은 Member는 프로필 응답으로 변환할 수 없습니다." },
                name = member.name,
                phoneNumber = member.phoneNumber,
                status = member.status,
                onboardingCompleted = member.isOnboardingCompleted(),
                rejected = member.isRejected(),
                kakaoNickname = member.kakaoNickname,
                kakaoProfileImageUrl = member.kakaoProfileImageUrl,
            )
    }
}
