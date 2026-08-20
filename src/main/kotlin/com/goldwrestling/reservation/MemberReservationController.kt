package com.goldwrestling.reservation

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.member.MemberStateGate
import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.reservation.dto.ChangeReservationRequest
import com.goldwrestling.reservation.dto.MyReservationSearchCondition
import com.goldwrestling.reservation.dto.ReservationResponse
import com.goldwrestling.reservation.dto.ReserveRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 회원 예약 생성·취소·변경·본인 목록 조회 API(RESV-01~05, 04-08·04-10). `SecurityConfig`에서
 * `/api/members` 하위 전체 경로가 `ROLE_MEMBER` 전용이다(D-040) — 그래서 이 컨트롤러엔 별도
 * 권한 애노테이션이 없다.
 *
 * **네 엔드포인트 모두 첫 줄에서 [memberStateGate]를 호출한다(D-040 유의사항)** — 서비스가 아니라
 * 컨트롤러가 회원 상태 게이트를 호출해야 한다. `MemberReservationService`의 각 메서드는 이미
 * 활성 회원이라고 가정하고 게이트를 호출하지 않는다(04-07 KDoc 참조) — 두 곳이 각자 게이트를
 * 부르면 어느 쪽이 "진짜 방어선"인지 흐려진다.
 *
 * `try-catch`로 에러 응답을 만들지 않는다 → 도메인 예외를 던지고 `GlobalExceptionHandler`가
 * `ProblemDetail`로 변환한다(D-017). 트랜잭션 애노테이션도 붙이지 않는다(D-020).
 */
@RestController
@RequestMapping("/api/members/me")
@Tag(name = "member-reservation", description = "회원 예약 생성·취소·변경·본인 목록 조회")
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

    @PostMapping("/reservations/{reservationId}/cancellation")
    @Operation(
        summary = "예약 취소 (당일 취소 불가, 즉시 잔여 복구)",
        description =
            "취소한 이용권이 등록 취소 상태면 잔여를 복구하지 않고 예약만 취소한다(D-091). " +
                "본인 예약이 아니면 404 RESERVATION_NOT_FOUND(403이 아님, T-04-45). " +
                "당일·과거 예약이거나 이미 취소된 예약이면 409로 응답한다.",
    )
    fun cancel(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @PathVariable reservationId: Long,
    ): ReservationResponse {
        memberStateGate.requireActive(principal)
        return memberReservationService.cancel(principal.requireMemberId(), reservationId)
    }

    @PostMapping("/reservations/{reservationId}/change")
    @Operation(
        summary = "예약 변경 (취소 + 재예약을 하나의 트랜잭션으로 처리)",
        description =
            "새 타임 예약이 실패하면(정원 초과·잔여 부족·창 마감·수업 종류 불일치 등) 기존 예약이 " +
                "그대로 유지된다(D-090). 본인 예약이 아니면 404 RESERVATION_NOT_FOUND(403이 아님).",
    )
    fun change(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @PathVariable reservationId: Long,
        @Valid @RequestBody request: ChangeReservationRequest,
    ): ReservationResponse {
        memberStateGate.requireActive(principal)
        return memberReservationService.change(principal.requireMemberId(), reservationId, request)
    }

    // 아래 springdoc 스펙 생성 힌트: MyReservationSearchCondition을 "condition"이라는 객체 파라미터
    // 1개가 아니라 page/size 개별 쿼리 파라미터로 펼쳐 기술하라는 뜻이다(D-054, MemberPassController와
    // 동일 관례 — 없으면 openapi.yaml이 객체 파라미터 1개로 기술되고 FE 생성기가 바인딩하지 못한다).
    @GetMapping("/reservations")
    @Operation(summary = "본인 예약 목록 조회 (활성 예약만, classDate·startTime 오름차순)")
    fun findMyReservations(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
        @ParameterObject @ModelAttribute @Valid condition: MyReservationSearchCondition,
    ): PageResponse<ReservationResponse> {
        memberStateGate.requireActive(principal)
        return memberReservationService.findMyReservations(principal.requireMemberId(), condition)
    }
}
