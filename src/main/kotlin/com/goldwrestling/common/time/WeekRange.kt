package com.goldwrestling.common.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 월요일 시작 주(週) 범위 (conventions §5 "주의 시작은 월요일"). [monday]·[sunday] 양 끝을 포함한다.
 *
 * `data class`로 둔다 — 엔티티가 아니라 값 객체이고, 테스트가 `equals` 비교(`isEqualTo`)를 쓴다
 * (conventions §3의 엔티티 `data class` 금지 규약은 JPA 엔티티에만 적용된다).
 */
data class WeekRange(
    val monday: LocalDate,
    val sunday: LocalDate,
) {
    /** [date]가 속한 주의 다음 주(월~일)를 반환한다. */
    fun next(): WeekRange = of(monday.plusWeeks(1))

    /** [date]가 이 주(월~일)에 포함되는지, 양 끝을 포함해 판정한다. */
    fun contains(date: LocalDate): Boolean = !date.isBefore(monday) && !date.isAfter(sunday)

    /** 월요일부터 일요일까지 7개 날짜를 오름차순으로 반환한다. */
    fun dates(): List<LocalDate> = (0..6L).map { monday.plusDays(it) }

    companion object {
        /**
         * [date]가 속한 주의 월~일 범위를 계산한다. 일요일=7 처리에서 어긋나는 `dayOfWeek.value`
         * 직접 산술 대신 [TemporalAdjusters.previousOrSame]을 쓴다(04-04 PLAN 지시).
         */
        fun of(date: LocalDate): WeekRange {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return WeekRange(monday, monday.plusDays(6))
        }
    }
}
