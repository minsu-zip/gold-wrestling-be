package com.goldwrestling.pass

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.pass.dto.AdjustPassRequest
import com.goldwrestling.pass.dto.CancelPassRequest
import com.goldwrestling.pass.dto.ChangePassPeriodRequest
import com.goldwrestling.pass.dto.PassResponse
import com.goldwrestling.pass.dto.RegisterPassRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 관리자 이용권 등록 API(PASS-01, PASS-02, D-055). `SecurityConfig`에서 `/api/admin` 하위 전체가
 * `hasRole("ADMIN")` 전용이므로, **이 컨트롤러에는 별도 권한 애노테이션을 붙이지 않는다**(D-040) —
 * 역할 구분은 URL 인가 규칙이 담당하고, 여기서 또 표현하면 규칙이 두 곳으로 갈라진다.
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020), 에러 응답은
 * `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "admin-pass", description = "관리자 이용권 관리")
class AdminPassController(
    private val adminPassService: AdminPassService,
) {
    @PostMapping("/members/{memberId}/passes")
    // 실제 응답 상태는 ResponseEntity.created(...)가 결정한다(Spring MVC가 ResponseEntity 상태를
    // 항상 우선한다) — 이 애노테이션은 springdoc이 openapi.yaml에 201을 기술하게 하는 문서화 전용
    // 힌트다. 없으면 springdoc이 반환 타입 바이트코드에서 실제 상태를 추론하지 못해 기본값 200으로
    // 문서화한다.
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "이용권 등록 (저녁반 회비 / 예약제 횟수권 / 1:1 레슨권)")
    fun register(
        @PathVariable memberId: Long,
        @Valid @RequestBody request: RegisterPassRequest,
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
    ): ResponseEntity<PassResponse> {
        val response = adminPassService.register(memberId, request, principal.requireAdminId())
        return ResponseEntity.created(URI.create("/api/admin/passes/${response.passId}")).body(response)
    }

    // POST + 복수형(`/adjustments`)을 쓴다 — 호출할 때마다 새 PassTransaction 이력이 append되는
    // 컬렉션이기 때문이다. 일회성 상태 전이인 `/approval`·`/cancellation`은 단수형을 쓰지만,
    // 이 엔드포인트는 매 호출이 독립된 원장 항목을 만든다는 점에서 다르다.
    @PostMapping("/passes/{passId}/adjustments")
    @Operation(summary = "이용권 잔여 횟수 수동 가감 (사유 필수)")
    fun adjust(
        @PathVariable passId: Long,
        @Valid @RequestBody request: AdjustPassRequest,
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
    ): PassResponse = adminPassService.adjust(passId, request, principal.requireAdminId())

    // 저녁반 회비 기간 수정(PASS-04)과 횟수권 유효기간 수정(PASS-07)을 이 하나의 엔드포인트가 모두
    // 담당한다(D-062) — 타입별 차이(횟수권은 종료일만)는 서버(`Pass.changePeriod`)가 강제한다.
    @PatchMapping("/passes/{passId}/period")
    @Operation(summary = "이용권 기간·유효기간 수정 (저녁반은 시작·종료, 횟수권은 종료일만)")
    fun changePeriod(
        @PathVariable passId: Long,
        @Valid @RequestBody request: ChangePassPeriodRequest,
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
    ): PassResponse = adminPassService.changePeriod(passId, request, principal.requireAdminId())

    // 일회성 상태 전이라 단수형 POST(`/approval`·`/rejection` 관례, PASS-08).
    @PostMapping("/passes/{passId}/cancellation")
    @Operation(summary = "이용권 등록 취소 (물리 삭제 없이 취소 상태 전환 + 상쇄 이력)")
    fun cancel(
        @PathVariable passId: Long,
        @Valid @RequestBody request: CancelPassRequest,
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
    ): PassResponse = adminPassService.cancel(passId, request, principal.requireAdminId())

    // 조회 전용이라 주체(관리자 ID)가 필요 없다 — `/api/admin` 프리픽스로 인가만 강제된다(D-040).
    @GetMapping("/members/{memberId}/passes")
    @Operation(summary = "회원 이용권 목록 조회 (취소 포함, 상태 구분)")
    fun getMemberPasses(
        @PathVariable memberId: Long,
    ): List<PassResponse> = adminPassService.getMemberPasses(memberId)
}
