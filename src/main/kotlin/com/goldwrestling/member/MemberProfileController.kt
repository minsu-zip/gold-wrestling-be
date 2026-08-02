package com.goldwrestling.member

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.member.dto.MyProfileResponse
import com.goldwrestling.member.dto.OnboardingRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 회원 본인 정보 API(AUTH-05, MEMBER-04). `SecurityConfig`에서 `/api/members` 하위 전체 경로가
 * `ROLE_MEMBER` 전용이다(D-040).
 *
 * **경로에 `memberId`를 받지 않는다.** 항상 `@AuthenticationPrincipal`로 주입된 토큰 주체 본인의
 * 정보만 다룬다 — 다른 회원의 프로필을 조회·수정하는 경로를 아예 만들지 않는 것이 IDOR을 막는 방법이다
 * (T-02-29). 다른 회원 조회가 필요한 관리자용 화면은 별도 API(`02-09`의 `/api/admin/members`)가
 * 담당한다.
 *
 * `try-catch`로 에러 응답을 만들지 않는다 → 도메인 예외를 던지고 `GlobalExceptionHandler`가
 * `ProblemDetail`로 변환한다(D-017). 트랜잭션 애노테이션도 붙이지 않는다(D-020, 컨트롤러는
 * 트랜잭션 경계가 아니다).
 */
@RestController
@RequestMapping("/api/members")
@Tag(name = "member", description = "회원 본인 정보")
class MemberProfileController(
    private val memberProfileService: MemberProfileService,
) {
    @GetMapping("/me")
    @Operation(summary = "본인 프로필 조회 (승인 대기·거절 안내 화면 포함 모든 상태에서 접근 가능)")
    fun getMyProfile(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
    ): MyProfileResponse = memberProfileService.getMyProfile(principal)

    @PostMapping("/me/onboarding")
    @Operation(summary = "온보딩 — 실명·전화번호 등록 (최초 1회, 재제출은 409로 거부된다)")
    fun submitOnboarding(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @Valid @RequestBody request: OnboardingRequest,
    ): MyProfileResponse = memberProfileService.submitOnboarding(principal, request)
}
