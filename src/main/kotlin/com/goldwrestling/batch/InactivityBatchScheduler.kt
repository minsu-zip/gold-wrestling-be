package com.goldwrestling.batch

import com.goldwrestling.SEOUL_ZONE_ID
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 2주 미사용 자동 차감(BATCH-01·04, D-108)의 매일 새벽 cron 트리거. **트리거만 하고 로직을 두지
 * 않는다**(RESEARCH Pattern 1) — 조건문·쿼리·트랜잭션은 [InactivityBatchRunner]와 그 협력자가
 * 담당한다.
 *
 * cron은 6필드(초 분 시 일 월 요일, D-114). JVM 기본 시간대가 이미 `Asia/Seoul`이지만
 * (`GoldWrestlingApplication.main`) `zone`을 명시하는 것이 conventions §5("시간대는 Asia/Seoul
 * 명시")와 일치한다.
 *
 * **분산 락(ShedLock)을 쓰지 않는다** — 단일 EC2 인스턴스 전제이고, 중복 실행은 D-106 상태 기반
 * 계산이 이미 안전하게 만든다(D-108).
 */
@Component
class InactivityBatchScheduler(
    private val runner: InactivityBatchRunner,
) {
    @Scheduled(cron = "0 0 4 * * *", zone = SEOUL_ZONE_ID)
    fun runDaily() {
        runner.run(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
    }
}
