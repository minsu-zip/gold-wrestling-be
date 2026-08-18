package com.goldwrestling.attendance

/**
 * 출석 상태(glossary.md "출석 상태", policies §6, D-127). **레코드 부재 = 미체크**이며, 이 enum이
 * 갖는 두 값은 그 다음 단계(체크됨)만 표현한다.
 */
enum class AttendanceStatus {
    /** 출석 — 예약제/1:1은 관리자 체크, 저녁반은 "명단에 추가됨" 자체가 곧 출석이다(불참 상태 없음). */
    ATTENDED,

    /** 불참 — 예약제/1:1 전용이다. 저녁반은 예약이 없어 불참을 남길 대상이 없다(D-127). */
    ABSENT,
}
