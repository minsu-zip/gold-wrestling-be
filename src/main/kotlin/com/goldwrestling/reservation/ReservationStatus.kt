package com.goldwrestling.reservation

/**
 * 예약 저장 상태 (glossary.md "예약 상태"). 취소는 물리 삭제가 아니라 이 상태로의 전환이다(D-090) —
 * "누가 언제 어떤 수업을 취소했는지"가 남아야 감사 가능성이 성립한다.
 */
enum class ReservationStatus {
    /** 활성 — 정상 예약 */
    ACTIVE,

    /** 취소됨 — 회원 셀프 취소 또는 관리자 대리 취소(D-090). 취소한 타임의 재예약은 허용한다 */
    CANCELED,
}
