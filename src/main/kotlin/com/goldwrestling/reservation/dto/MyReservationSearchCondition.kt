package com.goldwrestling.reservation.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate

/**
 * 회원 본인 예약 목록 조회(`GET /api/members/me/reservations`, RESV-05) 쿼리 파라미터 바인딩용.
 * `@ParameterObject @ModelAttribute @Valid`로 받는다(D-054).
 * `PassTransactionSearchCondition`과 동일한 형태·기본값 관례를 따른다.
 *
 * ### 기간 필터가 왜 필요한가 (BE-REQ-004, D-150)
 * 이 목록은 **활성 예약만** 반환하는데, 지나간 예약도 계속 `ACTIVE`로 남는다 — 노쇼도 출석도
 * 상태를 바꾸지 않고(policies §3 "노쇼는 별도 처리 없음"), 지난 예약을 종료 처리하는 배치도 없다.
 * 정렬이 `classDate ASC`라 **지난 예약이 목록 앞을 채우고 다가오는 예약이 뒤 페이지로 밀린다.**
 * 주 3회 이용 회원이면 약 8개월 뒤 100건을 넘어, page/size만으로는 "내 다음 수업"을 보여줄 수 없다.
 *
 * [from]/[to]는 **수업 날짜([Reservation.classDate])** 기준이며 **양끝을 포함**한다. 둘 다 없으면
 * 종전과 같이 전체를 반환하므로 기존 호출부의 동작이 바뀌지 않는다(하위 호환).
 *
 * 형식 검증만 여기서 하고 `from <= to` 같은 관계 검증은 서비스가 한다(conventions §6) — 다만
 * 뒤집힌 범위는 결과가 빈 페이지로 나올 뿐 위험하지 않아 별도 예외를 두지 않는다.
 */
@Schema(description = "회원 본인 예약 목록 조회 조건 — 수업 날짜 기간 필터·페이지네이션(활성 예약만 반환)")
data class MyReservationSearchCondition(
    @field:Schema(description = "조회 시작 수업 날짜(포함) — 생략하면 하한 없음", example = "2026-09-01")
    val from: LocalDate? = null,
    @field:Schema(description = "조회 종료 수업 날짜(포함) — 생략하면 상한 없음", example = "2026-09-30")
    val to: LocalDate? = null,
    @field:Min(0)
    @field:Schema(description = "페이지 번호(0부터 시작)", defaultValue = "0")
    val page: Int = 0,
    @field:Min(1)
    @field:Max(100)
    @field:Schema(description = "페이지 크기(1~100)", defaultValue = "20")
    val size: Int = 20,
)
