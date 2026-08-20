package com.goldwrestling.pass

/**
 * 이용권 종류 (glossary.md "이용권" — `Pass`의 판별 컬럼, D-060).
 */
enum class PassType {
    /** 저녁반 회비 — 기간제. 횟수 가감 대상이 아니다(policies §4.2a) */
    EVENING_MEMBERSHIP,

    /** 예약제 횟수권 — 횟수제 */
    SESSION_PASS,

    /** 1:1 레슨권 — 횟수제 */
    LESSON_PASS,
}
