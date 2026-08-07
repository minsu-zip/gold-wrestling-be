package com.goldwrestling.schedule

import com.goldwrestling.common.time.WeekRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 예약 창(D-095)의 세 가지 판정 — **오픈**(이번 주만) · **마감**(시작 시각 전) · **조회 범위**(2주).
 * 회원 시간표 조회(04-05)·예약 생성(04-07)·예약 변경(04-10)이 이 판정만 호출해 경로마다
 * 경계가 다르게 답하는 것을 막는다.
 *
 * 상태·`Clock` 의존이 없는 순수 함수 집합이다 — 현재 시각은 **호출부(서비스)가 `Clock`에서 뽑아
 * 인자로 넘긴다.** 저장하지 않는 계산용 조합이라 `now`에 `LocalDateTime`을 쓴다(conventions §5 예외).
 *
 * **마감 판정(시각 비교)과 당일 취소 판정(날짜 비교)은 서로 다른 축이다** (04-RESEARCH Pitfall 3).
 * 당일 취소 판정은 여기서 다루지 않는다 — `Reservation.assertCancelableByMember`(04-09)가 담당한다.
 */
object ReservationWindow {
    /** 예약 가능한 주는 [today]가 속한 이번 주 하나뿐이다 (policies §3 "오픈"). */
    fun bookableWeek(today: LocalDate): WeekRange = WeekRange.of(today)

    /** 조회 가능 범위 — 이번 주 월요일부터 다음 주 일요일까지 14일 (policies §3 "예약 창"). */
    fun viewableRange(today: LocalDate): ViewableRange {
        val thisWeek = WeekRange.of(today)
        return ViewableRange(thisWeek.monday, thisWeek.next().sunday)
    }

    /**
     * [classDate]·[startTime] 수업이 [now] 시점에 예약 가능한지 — 예외를 던지지 않는 boolean 버전.
     * [assertBookable]과 판정 로직을 공유한다.
     */
    fun isBookable(
        classDate: LocalDate,
        startTime: LocalTime,
        now: LocalDateTime,
    ): Boolean = bookableWeek(now.toLocalDate()).contains(classDate) && LocalDateTime.of(classDate, startTime).isAfter(now)

    /**
     * [classDate]·[startTime] 수업이 [now] 시점에 예약 불가능하면 [ReservationWindowClosedException]을 던진다.
     * 원인(이번 주가 아님 / 이미 시작됨)별로 메시지가 다르되, 사용자에게 날짜·시각 값을 노출하지 않는다
     * (T-04-16, conventions §8 "내부 정보를 응답에 담지 않는다"의 연장).
     */
    fun assertBookable(
        classDate: LocalDate,
        startTime: LocalTime,
        now: LocalDateTime,
    ) {
        if (!bookableWeek(now.toLocalDate()).contains(classDate)) {
            throw ReservationWindowClosedException("예약 가능한 주(이번 주)가 아닙니다.")
        }
        if (!LocalDateTime.of(classDate, startTime).isAfter(now)) {
            throw ReservationWindowClosedException("이미 시작된 수업은 예약할 수 없습니다.")
        }
    }

    /**
     * 회원이 조회를 요청한 주의 시작일([weekStart])이 월요일이 아니거나 [viewableRange] 밖이면
     * [ReservationWindowClosedException]을 던진다. 임의의 과거·미래 주 탐색을 막는다 (T-04-17).
     */
    fun assertViewable(
        weekStart: LocalDate,
        today: LocalDate,
    ) {
        if (weekStart.dayOfWeek != DayOfWeek.MONDAY || !viewableRange(today).contains(weekStart)) {
            throw ReservationWindowClosedException("조회할 수 없는 주입니다.")
        }
    }

    /** 이번 주 월요일부터 다음 주 일요일까지 14일 범위. [WeekRange](7일 고정)와 별개의 값 객체다. */
    data class ViewableRange(
        val start: LocalDate,
        val end: LocalDate,
    ) {
        fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    }
}
