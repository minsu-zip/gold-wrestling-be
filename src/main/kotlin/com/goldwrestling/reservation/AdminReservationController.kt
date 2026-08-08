package com.goldwrestling.reservation

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.reservation.dto.AdminCancelReservationRequest
import com.goldwrestling.reservation.dto.AdminChangeReservationRequest
import com.goldwrestling.reservation.dto.AdminReservationResponse
import com.goldwrestling.reservation.dto.ReservationSearchCondition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 전체 예약 조회(RESV-07, D-054) + 대리 취소·변경(RESV-08, 04-13) API. `SecurityConfig`에서
 * `/api/admin` 하위 전체가 `hasRole("ADMIN")` 전용이므로(D-040), `AdminMemberController`와
 * 동일하게 이 컨트롤러엔 별도 권한 애노테이션을 붙이지 않는다 — 역할 구분은 URL 인가 규칙이 담당한다.
 *
 * 이 API는 다지점 확장(branchId 스코프)을 아직 요구하지 않는다 — 04-11(관리자 스케줄 보드)과 달리
 * `04-CONTEXT.md`의 RESV-07 결정이 필터를 기간·수업 종류·회원 검색어로만 못박았고, `AdminBranch`
 * 매핑이 v1에서 아직 채워지지 않는다(D-101). 다지점 운영이 시작되면 그때 `AdminScheduleService.
 * resolveBranchId`와 같은 방식으로 확장한다.
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020), 에러 응답은
 * `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/admin/reservations")
@Tag(name = "admin-reservation", description = "관리자 예약 조회·대리 취소·변경")
class AdminReservationController(
    private val adminReservationService: AdminReservationService,
) {
    // 아래 springdoc 스펙 생성 힌트: ReservationSearchCondition을 "condition" 객체 파라미터 1개가
    // 아니라 from/to/classType/keyword/status/page/size 개별 쿼리 파라미터로 펼쳐 기술하라는 뜻이다
    // (D-054, AdminMemberController와 동일 관례) — 없으면 openapi.yaml이 객체 파라미터 1개로
    // 기술되고 FE 생성기가 바인딩하지 못한다.
    @GetMapping
    @Operation(
        summary = "예약 목록 조회 (기간·수업 종류·회원 검색어 필터 + 페이지네이션, 취소 예약 포함)",
    )
    fun search(
        @ParameterObject @ModelAttribute @Valid condition: ReservationSearchCondition,
    ): PageResponse<AdminReservationResponse> = adminReservationService.search(condition)

    @PostMapping("/{reservationId}/cancellation")
    @Operation(
        summary = "예약 대리 취소 (당일·과거 포함 제약 없음, 복구 여부 선택 — 기본 복구)",
        description =
            "회원 경로와 달리 당일·과거 예약도 취소할 수 있다. refund=false면 잔여를 복구하지 " +
                "않고 예약만 취소한다(당일 불참 연락, D-089 등록 취소 선행 조건 2단계 절차). " +
                "이미 취소된 예약이면 409로 응답한다.",
    )
    fun cancel(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @PathVariable reservationId: Long,
        @Valid @RequestBody request: AdminCancelReservationRequest,
    ): AdminReservationResponse = adminReservationService.cancelByAdmin(principal.requireAdminId(), reservationId, request)

    @PostMapping("/{reservationId}/change")
    @Operation(
        summary = "예약 대리 변경 (당일·예약 창 제약 없음, 취소 + 재예약을 하나의 트랜잭션으로 처리)",
        description =
            "새 타임 예약이 실패하면(정원 초과·수업 종류 불일치 등) 기존 예약이 그대로 유지된다. " +
                "관리자 경로도 정원 조건부 UPDATE를 그대로 거친다 — 오버부킹 백도어가 없다.",
    )
    fun change(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @PathVariable reservationId: Long,
        @Valid @RequestBody request: AdminChangeReservationRequest,
    ): AdminReservationResponse = adminReservationService.changeByAdmin(principal.requireAdminId(), reservationId, request)
}
