package com.goldwrestling.support

import com.goldwrestling.SEOUL_ZONE_ID
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 시각을 앞으로 밀 수 있는 테스트 전용 Clock.
 *
 * 2주 미사용 차감(Phase 5)·refresh 만료(Phase 2)처럼 "시간이 흘렀다"를 재현해야 하는 테스트가
 * 공용으로 쓴다. `Thread.sleep`으로 실제 시간을 기다리지 않고 [advance]/[setTo]로 즉시 이동한다.
 */
class MutableTestClock(
    initial: Instant,
    private val zone: ZoneId,
) : Clock() {
    @Volatile
    private var current: Instant = initial

    override fun instant(): Instant = current

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableTestClock(current, zone)

    /** 현재 시각을 [duration]만큼 앞으로 이동시킨다. */
    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    /** 현재 시각을 [instant]로 교체한다. */
    fun setTo(instant: Instant) {
        current = instant
    }

    companion object {
        /** `"2026-08-02T10:00:00+09:00"` 처럼 오프셋이 포함된 ISO 문자열로 Asia/Seoul 기준 Clock을 만든다. */
        fun ofSeoul(iso: String): MutableTestClock = MutableTestClock(OffsetDateTime.parse(iso).toInstant(), ZoneId.of(SEOUL_ZONE_ID))
    }
}
