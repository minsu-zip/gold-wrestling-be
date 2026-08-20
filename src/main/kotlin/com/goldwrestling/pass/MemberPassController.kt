package com.goldwrestling.pass

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.pass.dto.PassResponse
import com.goldwrestling.pass.dto.PassTransactionResponse
import com.goldwrestling.pass.dto.PassTransactionSearchCondition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 회원 본인 이용권·이력 조회 API(PASS-05, PASS-06). `SecurityConfig`에서 `/api/members` 하위 전체
 * 경로가 `ROLE_MEMBER` 전용이다(D-040).
 *
 * **경로에 `memberId`를 받지 않는다.** 항상 `@AuthenticationPrincipal`로 주입된 토큰 주체 본인의
 * 이용권·이력만 다룬다 — 다른 회원의 이용권을 조회하는 경로를 아예 만들지 않는 것이 IDOR을 막는
 * 방법이다(T-03-41, `MemberProfileController`와 같은 취지). 다른 회원 조회가 필요한 관리자용 화면은
 * `/api/admin/members/{memberId}/passes`(03-09)가 담당한다.
 *
 * `try-catch`로 에러 응답을 만들지 않는다 → 도메인 예외를 던지고 `GlobalExceptionHandler`가
 * `ProblemDetail`로 변환한다(D-017). 트랜잭션 애노테이션도 붙이지 않는다(D-020).
 */
@RestController
@RequestMapping("/api/members/me")
@Tag(name = "member-pass", description = "회원 본인 이용권·이력 조회")
class MemberPassController(
    private val memberPassService: MemberPassService,
) {
    @GetMapping("/passes")
    @Operation(summary = "본인 이용권 목록 조회 (만료·소진 포함, 취소 제외)")
    fun getMyPasses(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
    ): List<PassResponse> = memberPassService.getMyPasses(principal)

    // 아래 springdoc 스펙 생성 힌트: PassTransactionSearchCondition을 "condition"이라는 객체 파라미터
    // 1개가 아니라 passId/page/size 개별 쿼리 파라미터로 펼쳐 기술하라는 뜻이다. 이게 없으면
    // openapi.yaml이 객체 파라미터 1개로 기술되고, 이를 deepObject나 JSON 문자열로 직렬화하는 FE
    // 생성기에서는 서버가 파라미터를 전혀 바인딩하지 못한다(D-054, 실제 재현된 버그 WR-06).
    @GetMapping("/pass-transactions")
    @Operation(summary = "본인 차감·복구 이력 조회 (이용권별 필터, 페이지네이션)")
    fun getMyTransactions(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @ParameterObject @ModelAttribute @Valid condition: PassTransactionSearchCondition,
    ): PageResponse<PassTransactionResponse> = memberPassService.getMyTransactions(principal, condition)
}
