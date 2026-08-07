package com.goldwrestling.schedule

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 예약 창(오픈·마감·조회범위) 판정 (policies §3, D-095) — 순수 Kotlin 단위테스트.
 * `now`는 `LocalDateTime.of(...)` 리터럴로 고정한다 — 실제 시계를 읽으면 특정 요일에만 통과한다
 * (04-04 PLAN interfaces 참고, add-domain-test §3).
 *
 * 마감 판정(시각 비교)과 당일 취소 판정(날짜 비교)은 서로 다른 축이다(04-RESEARCH Pitfall 3) —
 * 당일 취소 판정은 `Reservation.assertCancelableByMember`가 별도로 담당하며 여기서는 다루지 않는다.
 */
class ReservationWindowTest {
    @Test
    fun `월요일 00시 정각에는 그 주 전체가 예약 가능하다`() {
        val monday = LocalDate.of(2026, 8, 3)
        val now = LocalDateTime.of(monday, LocalTime.of(0, 0))

        assertThatCode {
            ReservationWindow.assertBookable(monday, LocalTime.of(19, 0), now)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `일요일 23시 59분에도 그 주는 여전히 이번 주이지만 이미 시작된 수업은 마감으로 거부된다`() {
        val sunday = LocalDate.of(2026, 8, 9)
        val now = LocalDateTime.of(sunday, LocalTime.of(23, 59))

        // 그 주 자체는 여전히 예약 가능한 주다
        assertThat(ReservationWindow.bookableWeek(now.toLocalDate()).contains(sunday)).isTrue()

        // 그러나 이미 시작(21:00) 수업은 마감으로 거부된다
        assertThatThrownBy {
            ReservationWindow.assertBookable(sunday, LocalTime.of(21, 0), now)
        }.isInstanceOf(ReservationWindowClosedException::class.java)
    }

    @Test
    fun `수업 시작 1분 전에는 예약이 가능하다`() {
        val classDate = LocalDate.of(2026, 8, 7)
        val now = LocalDateTime.of(classDate, LocalTime.of(12, 59))

        assertThatCode {
            ReservationWindow.assertBookable(classDate, LocalTime.of(13, 0), now)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `수업 시작 정각에는 예약이 거부된다`() {
        val classDate = LocalDate.of(2026, 8, 7)
        val now = LocalDateTime.of(classDate, LocalTime.of(13, 0))

        assertThatThrownBy {
            ReservationWindow.assertBookable(classDate, LocalTime.of(13, 0), now)
        }.isInstanceOf(ReservationWindowClosedException::class.java)
    }

    @Test
    fun `다음 주 월요일 수업은 이번 주 금요일에 예약이 거부되지만 조회는 가능하다`() {
        val today = LocalDate.of(2026, 8, 7) // 금요일
        val now = LocalDateTime.of(today, LocalTime.of(10, 0))
        val nextMonday = LocalDate.of(2026, 8, 10)

        assertThatThrownBy {
            ReservationWindow.assertBookable(nextMonday, LocalTime.of(19, 0), now)
        }.isInstanceOf(ReservationWindowClosedException::class.java)

        assertThatCode {
            ReservationWindow.assertViewable(nextMonday, today)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `지난 주 수업은 오픈된 적 없는 과거 주이므로 예약이 거부된다`() {
        val today = LocalDate.of(2026, 8, 7) // 금요일
        val now = LocalDateTime.of(today, LocalTime.of(10, 0))
        val lastWeekTuesday = LocalDate.of(2026, 7, 28)

        assertThatThrownBy {
            ReservationWindow.assertBookable(lastWeekTuesday, LocalTime.of(19, 0), now)
        }.isInstanceOf(ReservationWindowClosedException::class.java)
    }

    @Test
    fun `bookableWeek은 오늘이 속한 주의 WeekRange를 반환한다`() {
        val today = LocalDate.of(2026, 8, 7)

        val week = ReservationWindow.bookableWeek(today)

        assertThat(week).isEqualTo(
            com.goldwrestling.common.time.WeekRange
                .of(today),
        )
    }

    @Test
    fun `viewableRange는 이번 주 월요일부터 다음 주 일요일까지 14일이다`() {
        val today = LocalDate.of(2026, 8, 7)

        val range = ReservationWindow.viewableRange(today)

        assertThat(range.start).isEqualTo(LocalDate.of(2026, 8, 3))
        assertThat(range.end).isEqualTo(LocalDate.of(2026, 8, 16))
    }

    @Test
    fun `isBookable은 assertBookable과 동일한 판정을 예외 없이 boolean으로 반환한다`() {
        val classDate = LocalDate.of(2026, 8, 7)
        val now = LocalDateTime.of(classDate, LocalTime.of(12, 59))

        assertThat(ReservationWindow.isBookable(classDate, LocalTime.of(13, 0), now)).isTrue()
        assertThat(ReservationWindow.isBookable(classDate, LocalTime.of(12, 0), now)).isFalse()
    }

    @Test
    fun `assertBookable 예외 메시지는 이번 주가 아닌 경우와 이미 시작된 경우가 서로 다르고 날짜 시각 값을 노출하지 않는다`() {
        val today = LocalDate.of(2026, 8, 7)
        val now = LocalDateTime.of(today, LocalTime.of(10, 0))

        val notThisWeekException =
            catchThrowableOfType(ReservationWindowClosedException::class.java) {
                ReservationWindow.assertBookable(LocalDate.of(2026, 8, 10), LocalTime.of(19, 0), now)
            }
        val alreadyStartedException =
            catchThrowableOfType(ReservationWindowClosedException::class.java) {
                ReservationWindow.assertBookable(today, LocalTime.of(9, 0), now)
            }

        val notThisWeekMessage = notThisWeekException.message
        val alreadyStartedMessage = alreadyStartedException.message

        assertThat(notThisWeekMessage).isNotEqualTo(alreadyStartedMessage)
        assertThat(notThisWeekMessage).doesNotContain("2026", "08", "10", "19")
        assertThat(alreadyStartedMessage).doesNotContain("2026", "08", "07", "09")
    }

    @Test
    fun `assertViewable은 월요일이 아니거나 조회범위 밖이면 거부한다`() {
        val today = LocalDate.of(2026, 8, 7)

        assertThatThrownBy {
            ReservationWindow.assertViewable(LocalDate.of(2026, 8, 4), today) // 화요일
        }.isInstanceOf(ReservationWindowClosedException::class.java)

        assertThatThrownBy {
            ReservationWindow.assertViewable(LocalDate.of(2026, 8, 17), today) // 3주 뒤 월요일
        }.isInstanceOf(ReservationWindowClosedException::class.java)

        assertThatCode {
            ReservationWindow.assertViewable(LocalDate.of(2026, 8, 3), today)
        }.doesNotThrowAnyException()
    }
}
