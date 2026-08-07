package com.goldwrestling.schedule

/**
 * 수업 종류 (glossary.md "수업 종류").
 *
 * [reservable]은 예약 대상 여부를 표현한다(D-096) — `EVENING`(저녁반)은 시간표 조회에는 노출되지만
 * 기간 내 무제한 참여 방식이라 예약 자체가 필요 없다. `ClassSession.assertReservable`이 이 값으로
 * `EVENING` 세션에 대한 예약 시도를 거부한다.
 */
enum class ClassType(
    val reservable: Boolean,
) {
    /** 저녁반(단체수업) — 기간제 무제한 참여, 예약 불필요 (policies §1) */
    EVENING(reservable = false),

    /** 예약제 수업 — 예약 필수, `SESSION_PASS` 차감 대상 */
    SESSION(reservable = true),

    /** 1:1 레슨 — 예약 필수, `LESSON_PASS` 차감 대상, 타임당 1명 */
    LESSON(reservable = true),
}
