package com.goldwrestling.config

import com.goldwrestling.SEOUL_ZONE_ID
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/**
 * 현재 시각은 이 빈에서만 얻는다 — `OffsetDateTime.now()` 직접 호출 금지 (conventions §5).
 * 테스트는 [com.goldwrestling.support.TestClockConfiguration]으로 이 빈을 대체해 시각을 고정한다.
 */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of(SEOUL_ZONE_ID))
}
