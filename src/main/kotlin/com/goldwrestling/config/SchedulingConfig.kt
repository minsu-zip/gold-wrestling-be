package com.goldwrestling.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 이 저장소 최초의 스케줄링 도입 지점(D-108). `@EnableScheduling`만 켜고 빈은 두지 않는다 —
 * 실제 트리거는 `batch/InactivityBatchScheduler.kt`에 있다.
 *
 * 기본 스케줄러 풀 크기는 1이고 배치가 하루 1회·수 분 이내이므로 조정하지 않는다 — 스케줄 작업이
 * 늘어나면 `spring.task.scheduling.pool.size`를 검토한다.
 */
@Configuration
@EnableScheduling
class SchedulingConfig
