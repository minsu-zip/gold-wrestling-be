package com.goldwrestling.reservation.dto

import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.ClassType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate

/**
 * 관리자 전체 예약 조회(`GET /api/admin/reservations`, RESV-07) 쿼리 파라미터 바인딩용.
 * `@ParameterObject @ModelAttribute @Valid`로 받는다(D-054, `MemberSearchCondition`과 동일 관례).
 *
 * [status]를 생략하면 조건 없음으로 취급되어 **취소된 예약도 함께 반환된다** — 관리자 조회는
 * 회원 본인 목록(D-090, 취소 숨김)과 달리 감사·이력 확인이 목적이라 기본이 "전부 보여준다"이다.
 *
 * `from`/`to` 범위 검증(뒤가 앞보다 빠른 경우)은 이 DTO가 아니라 서비스에서 한다(conventions §6 —
 * 형식 검증만 DTO, 도메인 규칙은 서비스·엔티티) — `AdminPassService.changePeriod`가 기간 역전을
 * 서비스에서 판정하는 것과 같은 관례([com.goldwrestling.reservation.InvalidReservationSearchRangeException]).
 */
@Schema(description = "관리자 예약 검색 조건 — 기간·수업 종류·회원 검색어·상태 필터 + 페이지네이션")
data class ReservationSearchCondition(
    @field:Schema(description = "수업 날짜 범위 시작(포함)") val from: LocalDate? = null,
    @field:Schema(description = "수업 날짜 범위 끝(포함)") val to: LocalDate? = null,
    @field:Schema(description = "수업 종류 필터") val classType: ClassType? = null,
    @field:Schema(description = "회원 이름·전화번호 부분 일치 검색어") val keyword: String? = null,
    @field:Schema(description = "예약 상태 필터 — 생략하면 취소 포함 전체") val status: ReservationStatus? = null,
    @field:Min(0)
    @field:Schema(description = "페이지 번호(0부터 시작)", defaultValue = "0")
    val page: Int = 0,
    @field:Min(1)
    @field:Max(100)
    @field:Schema(description = "페이지 크기(1~100)", defaultValue = "20")
    val size: Int = 20,
)
