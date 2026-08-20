package com.goldwrestling.schedule

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.member.MemberStateGate
import com.goldwrestling.schedule.dto.WeeklyScheduleResponse
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
 * 회원 본인 주간 시간표 조회(SCHED-01·SCHED-02). `SecurityConfig`에서 `/api/members` 하위 전체 경로가
 * `ROLE_MEMBER` 전용이다(D-040). 이 컨트롤러는 수정하지 않는다.
 *
 * **`memberStateGate.requireActive(principal)`을 반드시 호출한다** — `PENDING`/`ON_LEAVE` 회원이
 * 시간표를 조회하는 것은 허용되지 않는다(T-04-20). 새 회원 엔드포인트마다 이 게이트 호출 여부를
 * 확인해야 하고, 빠뜨려도 컴파일·테스트가 그 자체로는 잡아주지 않는다(D-040 유의사항).
 *
 * `try-catch`로 에러 응답을 만들지 않는다 → 도메인 예외를 던지고 `GlobalExceptionHandler`가
 * `ProblemDetail`로 변환한다(D-017). 트랜잭션 애노테이션도 붙이지 않는다(D-020).
 */
@RestController
@RequestMapping("/api/members/me")
@Tag(name = "member-schedule", description = "회원 본인 주간 시간표 조회")
class MemberScheduleController(
    private val scheduleService: ScheduleService,
    private val memberStateGate: MemberStateGate,
) {
    @GetMapping("/schedule")
    @Operation(summary = "본인 주간 시간표 조회 (weekStart 생략 시 이번 주, 다음 주까지 조회 가능·예약은 불가)")
    fun getWeeklySchedule(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekStart: LocalDate?,
    ): WeeklyScheduleResponse {
        memberStateGate.requireActive(principal)
        return scheduleService.getWeeklySchedule(principal.requireMemberId(), weekStart)
    }
}
