package com.goldwrestling.common.time

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 월요일 시작 주(週) 범위 계산 (conventions §5 "주의 시작은 월요일") — 순수 Kotlin 단위테스트.
 * `Clock` 없이 `LocalDate` 리터럴을 직접 넘긴다 (04-04 PLAN interfaces 참고).
 */
class WeekRangeTest {
    @Test
    fun `금요일에 계산하면 그 주의 월요일과 일요일을 반환한다`() {
        val range = WeekRange.of(LocalDate.of(2026, 8, 7))

        assertThat(range.monday).isEqualTo(LocalDate.of(2026, 8, 3))
        assertThat(range.sunday).isEqualTo(LocalDate.of(2026, 8, 9))
    }

    @Test
    fun `월요일에 계산하면 자기 자신이 그 주의 월요일이다`() {
        val range = WeekRange.of(LocalDate.of(2026, 8, 3))

        assertThat(range.monday).isEqualTo(LocalDate.of(2026, 8, 3))
        assertThat(range.sunday).isEqualTo(LocalDate.of(2026, 8, 9))
    }

    @Test
    fun `일요일은 그 주의 마지막 날이지 다음 주가 아니다`() {
        val range = WeekRange.of(LocalDate.of(2026, 8, 9))

        assertThat(range.monday).isEqualTo(LocalDate.of(2026, 8, 3))
        assertThat(range.sunday).isEqualTo(LocalDate.of(2026, 8, 9))
    }

    @Test
    fun `next는 다음 주 월요일부터 일요일까지를 반환한다`() {
        val range = WeekRange.of(LocalDate.of(2026, 8, 7)).next()

        assertThat(range.monday).isEqualTo(LocalDate.of(2026, 8, 10))
        assertThat(range.sunday).isEqualTo(LocalDate.of(2026, 8, 16))
    }

    @Test
    fun `contains는 월요일과 일요일 양 끝을 포함하고 다음 주 월요일은 제외한다`() {
        val range = WeekRange.of(LocalDate.of(2026, 8, 7))

        assertThat(range.contains(LocalDate.of(2026, 8, 3))).isTrue()
        assertThat(range.contains(LocalDate.of(2026, 8, 9))).isTrue()
        assertThat(range.contains(LocalDate.of(2026, 8, 10))).isFalse()
    }

    @Test
    fun `dates는 월요일부터 일요일까지 7개를 오름차순으로 반환한다`() {
        val range = WeekRange.of(LocalDate.of(2026, 8, 7))

        assertThat(range.dates()).containsExactly(
            LocalDate.of(2026, 8, 3),
            LocalDate.of(2026, 8, 4),
            LocalDate.of(2026, 8, 5),
            LocalDate.of(2026, 8, 6),
            LocalDate.of(2026, 8, 7),
            LocalDate.of(2026, 8, 8),
            LocalDate.of(2026, 8, 9),
        )
    }
}
