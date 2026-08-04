package com.goldwrestling.pass.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/**
 * 회원 본인 이력 조회(`GET /api/members/me/pass-transactions`, PASS-06) 쿼리 파라미터 바인딩용.
 * `@ParameterObject @ModelAttribute @Valid`로 받는다 — 형식 검증(`@Min`/`@Max`)만 여기서 하고,
 * 소유권 검증은 이 DTO의 일이 아니다(`PassTransactionSpecifications.ownedByMember`가 항상 함께 붙는다).
 */
@Schema(description = "회원 본인 차감/복구 이력 조회 조건 — 이용권 필터·페이지네이션")
data class PassTransactionSearchCondition(
    @field:Schema(description = "이용권 ID 필터 — 본인 이용권이 아니면 결과가 비어 있다") val passId: Long? = null,
    @field:Min(0)
    @field:Schema(description = "페이지 번호(0부터 시작)", defaultValue = "0")
    val page: Int = 0,
    @field:Min(1)
    @field:Max(100)
    @field:Schema(description = "페이지 크기(1~100)", defaultValue = "20")
    val size: Int = 20,
)
