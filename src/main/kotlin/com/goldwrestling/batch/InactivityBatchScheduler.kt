package com.goldwrestling.batch

import com.goldwrestling.SEOUL_ZONE_ID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
 * **분산 락(ShedLock)을 쓰지 않는다** — 단일 EC2 인스턴스 전제다(D-108). 다만 이 근거만으로는
 * 부족하다: 이 cron(스케줄러 스레드)과 `AdminBatchController`(Tomcat 스레드)가 겹치면 인스턴스가
 * 하나여도 같은 주기가 이중 차감될 수 있다 — 조건부 UPDATE가 잔여 음수만 막고 주기 중복은 막지
 * 않기 때문이다(05-REVIEW.md CR-01, D-108 정정 항목). 차감 원자성 보장은 갭 클로저에서 정한다.
 *
 * **[goldwrestling.batch.inactivity-scheduler-enabled]로 끌 수 있다**(D-116). 두 가지 목적이다:
 * ① 배포 킬 스위치 — 이 배치는 사람 개입 없이 회원 잔여를 깎는 유일한 경로라 잘못 돌 때 즉시
 * 멈출 수단이 필요하다. ② 테스트 격리 — 이 게이트가 없으면 모든 `@SpringBootTest` 컨텍스트에서
 * cron이 살아 있어, CI가 04:00 `Asia/Seoul`을 걸치는 순간 실제 배치가 테스트 DB에 끼어들어
 * 재현되지 않는 실패를 만든다(`build.gradle.kts`의 테스트 태스크가 `false`로 고정한다).
 */
@Component
@ConditionalOnProperty(
    name = ["goldwrestling.batch.inactivity-scheduler-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class InactivityBatchScheduler(
    private val runner: InactivityBatchRunner,
) {
    @Scheduled(cron = "0 0 4 * * *", zone = SEOUL_ZONE_ID)
    fun runDaily() {
        runner.run(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
    }
}
