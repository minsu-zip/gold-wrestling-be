package com.goldwrestling.batch

import com.goldwrestling.SEOUL_ZONE_ID
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 2주 미사용 자동 차감(BATCH-01·04, D-108)의 매일 새벽 cron 트리거. **트리거만 하고 로직을 두지
 * 않는다**(RESEARCH Pattern 1) — 조건문·쿼리·트랜잭션은 [InactivityBatchRunner]와 그 협력자가
 * 담당한다. 아래 `try-catch`는 **로깅이지 로직이 아니다** — 여기에 조건문·쿼리·이력 기록을
 * 추가하면 자동 실행과 수동 실행(`AdminBatchController`)의 차감 규칙이 갈라지기 시작한다.
 *
 * cron은 6필드(초 분 시 일 월 요일, D-114). JVM 기본 시간대가 이미 `Asia/Seoul`이지만
 * (`GoldWrestlingApplication.main`) `zone`을 명시하는 것이 conventions §5("시간대는 Asia/Seoul
 * 명시")와 일치한다.
 *
 * **분산 락(ShedLock)을 쓰지 않는다.** 근거가 05-13에서 **바뀌었다** — 예전 근거는 "단일 EC2
 * 인스턴스 전제"(D-108)였는데, 그것만으로는 부족했다. 이 cron(스케줄러 스레드)과
 * `AdminBatchController`(Tomcat 스레드)의 경쟁은 인스턴스 *간*이 아니라 한 JVM 안에서 일어나므로
 * 인스턴스가 하나여도 이중 차감이 났다(05-REVIEW.md CR-01). 지금은 `batch_execution`의
 * `RUNNING` 부분 유니크 인덱스(V10 `uq_batch_execution_running`, D-117)가 **DB에서** 두 실행을
 * 직렬화한다 — 인스턴스 수와 무관하게 성립하므로 다중 인스턴스가 되어도 같은 보장이 유지되고,
 * 그래서 분산 락이 여전히 필요 없다.
 *
 * **기본은 꺼져 있고 [goldwrestling.batch.inactivity-scheduler-enabled]로 켠다**(D-116, D-121로 정정).
 * `matchIfMissing = false` + `application.yml` 기본값 `false` — **설정을 빠뜨리면 "돌지 않는" 쪽으로
 * 실패한다**(fail-safe). 반대(fail-open)였다면 배포자가 환경변수를 잊는 순간 CR-03(기준일 후보 ①
 * 부재)이 열린 채 cron이 돌아 저녁반 전용 회원의 잔여가 2주마다 부당하게 깎인다 — 잊어서 안 도는
 * 것은 아무 일도 일어나지 않지만, 잊어서 도는 것은 회원 횟수가 사라진다. Phase 6이 출석 기록을
 * 채우면 그때 `true`로 켠다.
 *
 * 두 가지 목적이다:
 * ① 배포 킬 스위치 — 이 배치는 사람 개입 없이 회원 잔여를 깎는 유일한 경로라 잘못 돌 때 즉시
 * 멈출 수단이 필요하다. ② 테스트 격리 — 이 게이트가 없으면 모든 `@SpringBootTest` 컨텍스트에서
 * cron이 살아 있어, CI가 04:00 `Asia/Seoul`을 걸치는 순간 실제 배치가 테스트 DB에 끼어들어
 * 재현되지 않는 실패를 만든다(`build.gradle.kts`의 테스트 태스크가 `false`로 고정한다).
 */
@Component
@ConditionalOnProperty(
    name = ["goldwrestling.batch.inactivity-scheduler-enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class InactivityBatchScheduler(
    private val runner: InactivityBatchRunner,
) {
    /**
     * **예외를 밖으로 내보내지 않는다**(WR-02). 스케줄러 메서드에서 예외가 빠져나가면 스프링 기본
     * 핸들러가 스택 한 덩어리를 찍고 끝나서, 운영자는 "그날 배치가 왜 안 됐는지"를 로그에서
     * 되짚기 어렵다. 여기서 원인별로 분류해 남기고, **실행 이력은 러너가 이미 `FAILED`로
     * 확정했으므로 여기서 추가로 기록하지 않는다.**
     */
    @Scheduled(cron = "0 0 4 * * *", zone = SEOUL_ZONE_ID)
    fun runDaily() {
        try {
            runner.run(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
        } catch (e: BatchAlreadyRunningException) {
            // 에러가 아니다 — 관리자 수동 실행과 겹쳤을 뿐이고, 건너뛴 주기는 상태 기반 캐치업
            // (D-106)이 다음 실행에서 자연 보정한다.
            logger.info("이미 실행 중인 배치가 있어 이번 cron 실행을 건너뜁니다.", e)
        } catch (e: Exception) {
            logger.error("미사용 차감 배치의 cron 실행이 실패했습니다. 실행 이력(batch_execution)에서 원인을 확인하세요.", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InactivityBatchScheduler::class.java)
    }
}
