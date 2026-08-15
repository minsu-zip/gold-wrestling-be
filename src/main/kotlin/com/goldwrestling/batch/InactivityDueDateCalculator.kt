package com.goldwrestling.batch

import java.time.LocalDate

/**
 * 2주 미사용 자동 차감(policies §4.3, D-105·D-106)의 기준일 5종 후보. 값 객체이므로 `data class`
 * 허용(엔티티 `data class` 금지 규약은 JPA 엔티티 전용, `WeekRange` 선례).
 *
 * ① [lastAttendanceDate]는 Phase 6이 `Attendance`를 도입하기 전까지 항상 null이며 이는 의도된
 * 동작이다(D-105) — Phase 6은 이 값 객체에 필드를 채우기만 하면 된다.
 * ② [lastActiveReservationClassDate]는 **취소되지 않은** 예약의 수업일(수업 종류 무관).
 * ③ [returnedFromLeaveDate]는 `ON_LEAVE`→`ACTIVE` 복귀일 — 복귀하면 2주 유예가 새로 시작된다.
 * ④ [lastSessionPassRegistrationDate]는 `SESSION_PASS` 등록일(`created_at`, 시작일 아님).
 * ⑤ [lastPositiveAdjustDate]는 마지막 양(+) `ADMIN_ADJUST` 일자 — 충전 시점부터 2주 유예가
 * 새로 시작된다.
 */
data class InactivityDueDateCandidates(
    val lastAttendanceDate: LocalDate?,
    val lastActiveReservationClassDate: LocalDate?,
    val returnedFromLeaveDate: LocalDate?,
    val lastSessionPassRegistrationDate: LocalDate?,
    val lastPositiveAdjustDate: LocalDate?,
)

/**
 * 2주 미사용 자동 차감(policies §4.3)의 판정 전부를 담은 순수 계산. Spring·DB·`Clock`에 의존하지
 * 않는다 — 오늘 날짜는 항상 파라미터로 받는다(conventions §5, 호출부가 `LocalDate.now(clock)`을
 * 넘긴다).
 */
object InactivityDueDateCalculator {
    /** policies §4.3의 "2주"를 표현하는 유일한 상수 — 정책이 바뀌면 여기 하나만 바뀐다. */
    private const val GRACE_PERIOD_DAYS = 14L

    /**
     * 기준일 후보 5종(D-105) 중 non-null의 max를 고른다. 전부 null이면 null(판정 대상 아님 —
     * 호출부가 스킵한다). 후보 순서에는 의미를 주지 않는다.
     */
    fun resolveDueDate(candidates: InactivityDueDateCandidates): LocalDate? =
        listOfNotNull(
            candidates.lastAttendanceDate,
            candidates.lastActiveReservationClassDate,
            candidates.returnedFromLeaveDate,
            candidates.lastSessionPassRegistrationDate,
            candidates.lastPositiveAdjustDate,
        ).maxOrNull()
}
