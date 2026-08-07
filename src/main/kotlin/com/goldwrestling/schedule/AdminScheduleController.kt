package com.goldwrestling.schedule

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.schedule.dto.AdminWeeklyBoardResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 관리자 주간 스케줄 보드 조회(SCHED-03). `SecurityConfig`에서 `/api/admin` 하위 전체가
 * `hasRole("ADMIN")` 전용이므로, **이 컨트롤러에는 별도 권한 애노테이션을 붙이지 않는다**(D-040) —
 * 역할 구분은 URL 인가 규칙이 담당하고, 여기서 또 표현하면 규칙이 두 곳으로 갈라진다.
 *
 * `memberStateGate`는 호출하지 않는다 — 회원 상태 게이트는 회원 전용이고, 관리자 principal이
 * 들어오면 [MemberStateGate.requireActive]가 [IllegalStateException]을 던진다.
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020), 에러 응답은
 * `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/admin/schedule")
@Tag(name = "admin-schedule", description = "관리자 주간 스케줄 보드")
class AdminScheduleController(
    private val adminScheduleService: AdminScheduleService,
) {
    @GetMapping("/board")
    @Operation(
        summary = "관리자 주간 스케줄 보드 조회 (요일×타임 그리드 + 셀별 예약자 명단, 조회 주 범위 제한 없음)",
    )
    fun getWeeklyBoard(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekStart: LocalDate?,
        @RequestParam(required = false) branchId: Long?,
    ): AdminWeeklyBoardResponse {
        val adminId = principal.requireAdminId()
        val resolvedBranchId = adminScheduleService.resolveBranchId(adminId, branchId)
        return adminScheduleService.getWeeklyBoard(resolvedBranchId, weekStart)
    }
}
