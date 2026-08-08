package com.goldwrestling.schedule

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.schedule.dto.AdminWeeklyBoardResponse
import com.goldwrestling.schedule.dto.ClassSessionResponse
import com.goldwrestling.schedule.dto.SuspendClassSessionRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 관리자 주간 스케줄 보드 조회(SCHED-03) + 휴강 처리·해제(RESV-09, 04-14). `SecurityConfig`에서
 * `/api/admin` 하위 전체가 `hasRole("ADMIN")` 전용이므로, **이 컨트롤러에는 별도 권한 애노테이션을
 * 붙이지 않는다**(D-040) — 역할 구분은 URL 인가 규칙이 담당하고, 여기서 또 표현하면 규칙이 두
 * 곳으로 갈라진다.
 *
 * **클래스 레벨 매핑을 `/api/admin`으로 넓게 잡고, 메서드마다 하위 경로를 붙인다** — 스케줄
 * 보드(`/schedule/board`)와 휴강 처리(`/class-sessions/suspension`·`/class-sessions/{id}/resumption`)가
 * 서로 다른 리소스 계층(`schedule` vs `class-sessions`)이라 한 컨트롤러 안에서 공통 하위 경로로
 * 묶이지 않는다(04-14 PLAN.md "경로 문자열은 아래로 고정한다").
 *
 * `memberStateGate`는 호출하지 않는다 — 회원 상태 게이트는 회원 전용이고, 관리자 principal이
 * 들어오면 [MemberStateGate.requireActive]가 [IllegalStateException]을 던진다.
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020), 에러 응답은
 * `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "admin-schedule", description = "관리자 주간 스케줄 보드 + 휴강 처리·해제")
class AdminScheduleController(
    private val adminScheduleService: AdminScheduleService,
) {
    @GetMapping("/schedule/board")
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

    @PostMapping("/class-sessions/suspension")
    @Operation(
        summary = "휴강 처리 (활성 예약 일괄 취소 + 차감 복구 + 세션당 1건 요약 알림, RESV-09, policies §7)",
        description =
            "세션 id가 아니라 시간표 id + 날짜로 받는다(D-094) — 아직 예약이 없어 세션 행이 없는 " +
                "타임도 휴강할 수 있다. 이미 휴강된 수업이면 409 CLASS_SESSION_CANCELED로 응답한다.",
    )
    fun suspend(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @Valid @RequestBody request: SuspendClassSessionRequest,
    ): ClassSessionResponse = adminScheduleService.suspend(principal.requireAdminId(), request)

    @PostMapping("/class-sessions/{classSessionId}/resumption")
    @Operation(
        summary = "휴강 해제 (그 타임이 다시 예약 가능해진다 — 취소됐던 예약은 복원하지 않는다)",
        description =
            "휴강으로 자동 취소됐던 예약은 이 API로 복원되지 않는다 — 회원이 직접 다시 예약한다 " +
                "(잔여 음수 방지, CONTEXT.md 락인). 휴강 상태가 아닌 세션이면 409 CLASS_SESSION_NOT_CANCELED로 응답한다.",
    )
    fun resume(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @PathVariable classSessionId: Long,
    ): ClassSessionResponse = adminScheduleService.resume(principal.requireAdminId(), classSessionId)
}
