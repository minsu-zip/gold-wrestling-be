package com.goldwrestling.pass

/**
 * 이용권 표시 상태 (D-064). DB에 저장하지 않는 계산값 — 응답 DTO 변환 시점에 [PassStatus]와
 * 유효기간·잔여 횟수를 조합해 산출한다(03-03~03-05에서 계산 로직 구현).
 */
enum class PassDisplayStatus {
    /** 사용 가능 — 취소되지 않았고, 유효기간 이내이며(D-066), 횟수제면 잔여가 남아 있음 */
    USABLE,

    /** 유효기간 만료 — 취소되지 않았으나 종료일이 지남 */
    EXPIRED,

    /** 소진 — 횟수제이며 잔여가 0 */
    EXHAUSTED,

    /** 취소됨 (D-059) — [PassStatus.CANCELED]를 그대로 반영 */
    CANCELED,
}
