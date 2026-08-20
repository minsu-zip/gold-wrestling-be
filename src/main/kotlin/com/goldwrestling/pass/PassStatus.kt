package com.goldwrestling.pass

/**
 * 이용권 저장 상태 (D-064). DB에 저장되는 값은 이 2종뿐이다 — 만료·소진은 저장하지 않고
 * 조회 시점에 [PassDisplayStatus]로 계산한다.
 */
enum class PassStatus {
    /** 정상 — 취소되지 않음 */
    ACTIVE,

    /** 등록 취소(오등록 정정, D-059) — 물리 삭제 대신 이 상태로 전환 */
    CANCELED,
}
