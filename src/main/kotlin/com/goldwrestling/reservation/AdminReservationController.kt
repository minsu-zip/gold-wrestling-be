package com.goldwrestling.reservation

import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.reservation.dto.AdminReservationResponse
import com.goldwrestling.reservation.dto.ReservationSearchCondition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 전체 예약 조회 API(RESV-07, D-054). `SecurityConfig`에서 `/api/admin` 하위 전체가
 * `hasRole("ADMIN")` 전용이므로(D-040), `AdminMemberController`와 동일하게 이 컨트롤러엔 별도
 * 권한 애노테이션을 붙이지 않는다 — 역할 구분은 URL 인가 규칙이 담당한다.
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
@Tag(name = "admin-reservation", description = "관리자 예약 조회")
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
}
