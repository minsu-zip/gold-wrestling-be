package com.goldwrestling.schedule.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 관리자 스케줄 보드 셀의 예약자 명단 항목(D-096 "명단은 관리자만"). **전화번호·회원 상태·이용권
 * 정보는 절대 넣지 않는다(T-04-52)** — 명단의 목적은 "누가 오는가" 확인이고, 더 필요한 정보는
 * 기존 회원 상세 API(`GET /api/admin/members/{memberId}`)로 간다. 이 3필드가 이 DTO의 전부여야
 * 한다 — 필드를 추가하고 싶다면 그 전에 왜 회원 상세 API로 충분하지 않은지부터 답한다.
 */
@Schema(description = "예약자 명단 항목 — reservationId·memberId·memberName 3필드로 고정(T-04-52)")
data class BoardReservationResponse(
    @field:Schema(description = "예약 ID") val reservationId: Long,
    @field:Schema(description = "회원 ID") val memberId: Long,
    @field:Schema(description = "회원 실명") val memberName: String,
)
