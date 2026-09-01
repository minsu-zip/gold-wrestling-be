package com.goldwrestling.pass.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/**
 * 차감/복구 이력 조회의 쿼리 파라미터 바인딩용 — **두 경로가 공유한다**:
 * 회원 본인(`GET /api/members/me/pass-transactions`, PASS-06)과
 * 관리자(`GET /api/admin/members/{memberId}/pass-transactions`, PASS-05·BE-REQ-003, D-149).
 *
 * `@ParameterObject @ModelAttribute @Valid`로 받는다(D-054) — 형식 검증(`@Min`/`@Max`)만 여기서
 * 하고, **스코프 검증은 이 DTO의 일이 아니다**. 회원 경로는 인증 주체에서
 * (`PassTransactionSpecifications.ownedByMember`가 항상 함께 붙는다), 관리자 경로는 경로 변수
 * `memberId`에서 스코프가 온다.
 *
 * 두 경로가 필터·페이지네이션 모양이 같아 조건 DTO를 하나로 둔다 — 응답 DTO는 노출 범위가 달라
 * 갈라져 있다(`PassTransactionResponse` / `AdminPassTransactionResponse`).
 */
@Schema(description = "차감/복구 이력 조회 조건 — 이용권 필터·페이지네이션")
data class PassTransactionSearchCondition(
    @field:Schema(description = "이용권 ID 필터 — 조회 대상 회원의 이용권이 아니면 결과가 비어 있다") val passId: Long? = null,
    @field:Min(0)
    @field:Schema(description = "페이지 번호(0부터 시작)", defaultValue = "0")
    val page: Int = 0,
    @field:Min(1)
    @field:Max(100)
    @field:Schema(description = "페이지 크기(1~100)", defaultValue = "20")
    val size: Int = 20,
)
