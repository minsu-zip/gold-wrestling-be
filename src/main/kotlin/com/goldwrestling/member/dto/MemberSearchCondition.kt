package com.goldwrestling.member.dto

import com.goldwrestling.member.MemberStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/**
 * 관리자 회원 목록(`GET /api/admin/members`, D-035) 쿼리 파라미터 바인딩용. `@ModelAttribute @Valid`로
 * 받는다 — 형식 검증(`@Min`/`@Max`)만 여기서 하고, 도메인 규칙 검증은 이 DTO의 일이 아니다(conventions §6).
 */
@Schema(description = "회원 목록 조회 조건 — 검색어·상태·온보딩완료·페이지네이션")
data class MemberSearchCondition(
    @field:Schema(description = "이름·전화번호 통합 검색어 (부분 일치, D-035)") val keyword: String? = null,
    @field:Schema(description = "회원 상태 필터") val status: MemberStatus? = null,
    @field:Schema(
        description =
            "온보딩(실명·전화번호 입력) 완료 여부 필터. " +
                "status=PENDING과 함께 true로 주면 관리자 승인 대기 목록이 된다(D-035, policies §5.1)",
    )
    val onboardingCompleted: Boolean? = null,
    @field:Min(0)
    @field:Schema(description = "페이지 번호(0부터 시작)", defaultValue = "0")
    val page: Int = 0,
    @field:Min(1)
    @field:Max(100)
    @field:Schema(description = "페이지 크기(1~100)", defaultValue = "20")
    val size: Int = 20,
)
