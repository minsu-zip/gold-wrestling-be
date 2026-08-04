package com.goldwrestling.pass

/**
 * 차감/복구 사유 코드 (glossary.md "차감 사유"). `PassTransaction.reason`에 저장되는 닫힌 집합이다.
 *
 * **이번 phase(03-02)가 실제로 쓰는 건 [INITIAL_GRANT]·[ADMIN_ADJUST]·[REGISTRATION_CANCELED] 3종뿐이다.**
 * 나머지 5종은 Phase 4(예약 차감/복구)·Phase 5(배치)·Phase 6(저녁반 수동 차감)에서 사용된다 —
 * 이번 phase가 미리 코드만 선언해 두는 이유는 `TransactionReason`이 이 프로젝트 전체가 공유하는
 * 닫힌 enum이라 나중에 상수를 추가하면 저장된 값과의 매핑이 흩어지기 때문이다.
 */
enum class TransactionReason {
    /** 예약 성공 시 차감 (Phase 4) */
    RESERVE,

    /** 예약 취소로 복구 (Phase 4) */
    CANCEL_REFUND,

    /** 관리자 수동 가감 (policies §4.2a, 이번 phase 사용) */
    ADMIN_ADJUST,

    /** 저녁반 0.5회 수동 차감 (Phase 6) */
    EVENING_HALF,

    /** 2주 미사용 배치 차감 (Phase 5) */
    INACTIVITY,

    /** 휴강으로 인한 복구 (Phase 4) */
    CLASS_CANCELED_REFUND,

    /** 이용권 등록 시 초기 횟수 부여 (D-055, 이번 phase 사용) */
    INITIAL_GRANT,

    /** 등록 취소(오등록 정정) 시 잔여를 0으로 상쇄 (D-059, 이번 phase 사용) */
    REGISTRATION_CANCELED,
}
