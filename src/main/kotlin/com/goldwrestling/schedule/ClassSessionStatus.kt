package com.goldwrestling.schedule

/**
 * 날짜별 수업(`ClassSession`) 저장 상태.
 *
 * `CANCELED` = 휴강(policies §7) — 관리자가 특정 날짜의 수업을 휴강 처리했을 때의 상태다.
 * `Pass`의 `PassStatus`와 마찬가지로 저장되는 값은 이 2종뿐이다.
 */
enum class ClassSessionStatus {
    /** 정상 진행 */
    SCHEDULED,

    /** 휴강 (policies §7) */
    CANCELED,
}
