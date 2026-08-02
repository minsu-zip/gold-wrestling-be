package com.goldwrestling.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * [MutableTestClock] 자체가 틀리면 이후 모든 시각 의존 테스트가 조용히 거짓 통과하므로
 * 이 테스트는 생략하지 않는다.
 */
class MutableTestClockTest {
    @Test
    fun `초기 시각을 그대로 반환한다`() {
        // given
        val initial = Instant.parse("2026-08-02T01:00:00Z")
        val clock = MutableTestClock.ofSeoul("2026-08-02T10:00:00+09:00")

        // when / then
        assertThat(clock.instant()).isEqualTo(initial)
    }

    @Test
    fun `advance 후 instant가 정확히 그만큼 앞으로 이동한다`() {
        // given
        val clock = MutableTestClock.ofSeoul("2026-08-02T10:00:00+09:00")
        val before = clock.instant()

        // when
        clock.advance(Duration.ofDays(14))

        // then
        assertThat(clock.instant()).isEqualTo(before.plus(Duration.ofDays(14)))
    }

    @Test
    fun `zone은 Asia_Seoul 이다`() {
        // given
        val clock = MutableTestClock.ofSeoul("2026-08-02T10:00:00+09:00")

        // when / then
        assertThat(clock.zone.id).isEqualTo("Asia/Seoul")
    }
}
