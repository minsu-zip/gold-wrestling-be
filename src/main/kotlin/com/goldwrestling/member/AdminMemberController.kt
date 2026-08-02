package com.goldwrestling.member

import com.goldwrestling.member.dto.MemberDetailResponse
import com.goldwrestling.member.dto.MemberSearchCondition
import com.goldwrestling.member.dto.MemberSummaryResponse
import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.member.dto.RejectMemberRequest
import com.goldwrestling.member.dto.UpdateMemberStatusRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 회원 조회 API(MEMBER-01 목록 부분, MEMBER-02, D-035). `SecurityConfig`에서
 * `/api/admin` 하위 전체가 `hasRole("ADMIN")` 전용이므로, **이 컨트롤러에는 별도 권한 애노테이션을
 * 붙이지 않는다**(D-040) — 역할 구분은 URL 인가 규칙이 담당하고, 여기서 또 표현하면 규칙이
 * 두 곳으로 갈라진다.
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020), 에러 응답은
 * `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/admin/members")
@Tag(name = "admin-member", description = "관리자 회원 관리")
class AdminMemberController(
    private val adminMemberService: AdminMemberService,
) {
    @GetMapping
    @Operation(
        summary = "회원 목록 조회 (검색·상태 필터·페이지네이션. status=PENDING+onboardingCompleted=true 가 승인 대기 목록)",
    )
    fun search(
        @ModelAttribute @Valid condition: MemberSearchCondition,
    ): PageResponse<MemberSummaryResponse> = adminMemberService.search(condition)

    @GetMapping("/{memberId}")
    @Operation(summary = "회원 상세 조회 (거절 사유 포함 — 관리자 전용)")
    fun getDetail(
        @PathVariable memberId: Long,
    ): MemberDetailResponse = adminMemberService.getDetail(memberId)

    @PostMapping("/{memberId}/approval")
    @Operation(summary = "가입 승인 (온보딩 완료 PENDING 회원만)")
    fun approve(
        @PathVariable memberId: Long,
    ): MemberDetailResponse = adminMemberService.approve(memberId)

    @PostMapping("/{memberId}/rejection")
    @Operation(summary = "가입 거절 (INACTIVE 전환 + 사유 기록)")
    fun reject(
        @PathVariable memberId: Long,
        @Valid @RequestBody request: RejectMemberRequest,
    ): MemberDetailResponse = adminMemberService.reject(memberId, request.reason)

    @PatchMapping("/{memberId}/status")
    @Operation(summary = "회원 상태 변경 (ACTIVE가 아니게 되면 해당 회원 강제 로그아웃)")
    fun changeStatus(
        @PathVariable memberId: Long,
        @Valid @RequestBody request: UpdateMemberStatusRequest,
    ): MemberDetailResponse = adminMemberService.changeStatus(memberId, request.status)
}
