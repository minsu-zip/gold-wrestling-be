package com.goldwrestling.reservation

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.member.MemberStateGate
import com.goldwrestling.reservation.dto.ReservationResponse
import com.goldwrestling.reservation.dto.ReserveRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 회원 예약 생성 API(RESV-01·02, 04-08). `SecurityConfig`에서 `/api/members` 하위 전체 경로가
 * `ROLE_MEMBER` 전용이다(D-040) — 그래서 이 컨트롤러엔 별도 권한 애노테이션이 없다.
 *
 * **첫 줄에서 [memberStateGate]를 호출한다(D-040 유의사항)** — 새 회원 엔드포인트라 서비스가 아니라
 * 컨트롤러가 회원 상태 게이트를 호출해야 한다. `MemberReservationService.reserve`는 이미 활성 회원이라고
 * 가정하고 게이트를 호출하지 않는다(04-07 KDoc 참조) — 두 곳이 각자 게이트를 부르면 어느 쪽이
 * "진짜 방어선"인지 흐려진다.
 *
 * `try-catch`로 에러 응답을 만들지 않는다 → 도메인 예외를 던지고 `GlobalExceptionHandler`가
 * `ProblemDetail`로 변환한다(D-017). 트랜잭션 애노테이션도 붙이지 않는다(D-020).
 */
@RestController
@RequestMapping("/api/members/me")
@Tag(name = "member-reservation", description = "회원 예약 생성")
class MemberReservationController(
    private val memberReservationService: MemberReservationService,
    private val memberStateGate: MemberStateGate,
) {
    @PostMapping("/reservations")
    // 실제 응답 상태는 이 애노테이션이 결정한다 — springdoc이 openapi.yaml에 201을 기술하게 하는
    // 문서화 힌트이자 실제 상태 코드 지정(AdminPassController.register와 동일 관례).
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "예약 생성 (예약제 수업 SESSION_PASS / 1:1 레슨 LESSON_PASS 즉시 차감)",
        description =
            "정원·잔여·중복·창(window) 충돌은 409로 응답하고 `code` 필드로 사유를 구분한다. " +
                "가능한 code: RESERVATION_CAPACITY_EXCEEDED(정원 초과) · INSUFFICIENT_PASS_COUNT(잔여 부족) · " +
                "DUPLICATE_RESERVATION(같은 시간 중복 예약) · RESERVATION_WINDOW_CLOSED(예약 창 밖) · " +
                "CLASS_SESSION_CANCELED(휴강된 수업).",
    )
    fun reserve(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @Valid @RequestBody request: ReserveRequest,
    ): ReservationResponse {
        memberStateGate.requireActive(principal)
        return memberReservationService.reserve(principal.requireMemberId(), request)
    }
}
