package com.goldwrestling.attendance

import com.goldwrestling.attendance.dto.AddEveningAttendanceRequest
import com.goldwrestling.attendance.dto.AttendanceResponse
import com.goldwrestling.attendance.dto.CheckAttendanceRequest
import com.goldwrestling.attendance.dto.ClassSessionAttendanceRosterResponse
import com.goldwrestling.auth.AuthenticatedPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 출석 관리자 API 4종(ATTEND-01·02, D-132) — 명단 조회·예약제/1:1 출석 체크·저녁반 출석
 * 추가(0.5회 차감)·출석 삭제(복구). `SecurityConfig`에서 `/api/admin` 하위 전체가
 * `hasRole("ADMIN")` 전용이므로, **이 컨트롤러에는 별도 권한 애노테이션을 붙이지 않는다**(D-040) —
 * 역할 구분은 URL 인가 규칙이 담당한다.
 *
 * **경로를 `/api/admin/attendances` 한 계층으로 잡는다** — 출석은 `AdminScheduleController`의
 * 스케줄 보드·휴강 처리처럼 서로 다른 리소스 계층으로 나뉘지 않는 단일 리소스라, 넓은 클래스
 * 매핑(`/api/admin`)이 필요 없다.
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020), 에러 응답은
 * `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/admin/attendances")
@Tag(name = "admin-attendance", description = "관리자 출석 체크·저녁반 0.5회 차감")
class AdminAttendanceController(
    private val attendanceService: AttendanceService,
) {
    @GetMapping("")
    @Operation(summary = "타임별 출석 명단 조회 (예약자 프리로드, 미체크는 status=null)")
    fun getRoster(
        @RequestParam classScheduleId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) classDate: LocalDate,
    ): ClassSessionAttendanceRosterResponse = attendanceService.getRoster(classScheduleId, classDate)

    @PutMapping("")
    @Operation(
        summary = "예약제/1:1 출석 체크 (upsert, 소급 정정 가능)",
        description =
            "예약제·1:1 전용이다. 저녁반은 `POST /api/admin/attendances/evening`을 쓴다 — " +
                "저녁반은 출석 추가가 0.5회 차감을 유발하기 때문이다.",
    )
    fun check(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @Valid @RequestBody request: CheckAttendanceRequest,
    ): AttendanceResponse = attendanceService.check(principal.requireAdminId(), request)

    @PostMapping("/evening")
    @Operation(
        summary = "저녁반 출석 추가 (회비 우선 판정, 없으면 0.5회 차감)",
        description =
            "유효한 저녁반 회비가 있으면 차감 없이 출석만 기록하고, 없으면 만료 임박순 횟수권에서 " +
                "0.5회를 차감한다. 둘 다 불가하면 409 EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE로 " +
                "거부한다(관리자가 수동 가감으로 충전 후 재시도). 대상 회원은 " +
                "`GET /api/admin/members?keyword=`로 검색해 얻은 memberId를 넘긴다 — 이 API는 " +
                "별도 회원 검색 기능을 갖지 않는다.",
    )
    fun addEvening(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @Valid @RequestBody request: AddEveningAttendanceRequest,
    ): AttendanceResponse = attendanceService.addEveningAttendance(principal.requireAdminId(), request)

    @DeleteMapping("/{attendanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "출석 삭제",
        description = "저녁반 출석이면 연결된 0.5회 차감을 EVENING_HALF_REFUND로 복구한다.",
    )
    fun delete(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @PathVariable attendanceId: Long,
    ) {
        attendanceService.delete(principal.requireAdminId(), attendanceId)
    }
}
