package com.goldwrestling.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.LocalDate

/**
 * 2주 미사용 자동 차감 배치의 **정책 값**(D-118·D-119, 05-GAP-CONTEXT §2.2).
 *
 * 세 값 모두 코드 상수가 아니라 설정으로 둔다 — 이 배치는 사람 개입 없이 회원 잔여를 깎는 유일한
 * 경로이므로(D-116), 값이 잘못됐을 때 **재배포 없이 환경변수로 되돌릴 수 있어야** 한다.
 * 셋 다 기본값을 갖는 이유는 로컬·테스트가 설정 파일 없이 그대로 돌아야 하기 때문이다.
 *
 * `.env` 키 매핑:
 * - [policyEffectiveDate] ← `BATCH_INACTIVITY_POLICY_EFFECTIVE_DATE`
 * - [maxDeductionsPerRun] ← `BATCH_INACTIVITY_MAX_DEDUCTIONS_PER_RUN`
 * - [staleRunTimeout] ← `BATCH_INACTIVITY_STALE_RUN_TIMEOUT`
 *
 * **`goldwrestling.batch.inactivity-scheduler-enabled`는 이 클래스로 옮기지 않는다.**
 * cron 킬 스위치(D-116)는 `InactivityBatchScheduler`의
 * `@ConditionalOnProperty(name = ["goldwrestling.batch.inactivity-scheduler-enabled"])`가 **원시
 * 프로퍼티 이름**으로 직접 읽고, `build.gradle.kts`의 테스트 태스크도 같은 이름의 시스템
 * 프로퍼티로 `false`를 고정한다. 이 클래스의 `goldwrestling.batch.inactivity` prefix 아래로
 * 옮기면 키 이름이 `...inactivity.scheduler-enabled`로 바뀌어 **조건 애노테이션과 테스트 격리가
 * 조용히 깨진다**(빈이 등록돼도 아무 오류가 나지 않는다). 그래서 새 키는 그 형제로 둔다.
 */
@ConfigurationProperties(prefix = "goldwrestling.batch.inactivity")
data class InactivityBatchProperties(
    /**
     * 정책 시행일 하한 — 이 날짜보다 이른 미사용 기간은 차감 부채로 치지 않는다(CR-02).
     *
     * 없으면 "배치가 아예 없었던 기간"과 "배치가 며칠 죽었던 기간"을 코드가 구분할 수 없어,
     * 최초 배포 즉시 기존 회원 전원의 과거 미사용이 소급 차감된다. 05-14가 소비한다.
     */
    val policyEffectiveDate: LocalDate = LocalDate.of(2026, 9, 1),
    /**
     * 배치 1회 실행에서 회원 1명당 최대 차감 횟수 — 사고 피해 상한(CR-02).
     *
     * 매일 04:00에 도는 배치에서 정상 부족분은 0 또는 1이다. 2 이상은 배치가 2주 넘게 죽었거나
     * 계산이 틀린 상황이므로 하루 1회로 묶는다 — 그러면 사고가 나도 피해가 하루 1회씩만 누적돼
     * 관리자가 킬 스위치를 켤 시간이 생긴다. 밀린 주기는 다음 날들이 이어받는다(D-106 상태 기반
     * 캐치업). 05-14가 소비한다.
     */
    val maxDeductionsPerRun: Int = 1,
    /**
     * 이 시간을 넘긴 `RUNNING` 행은 죽은 실행(stale run)으로 보고 정리한다(D-117 알려진 약점).
     *
     * 앱이 비정상 종료하면 확정되지 못한 `RUNNING` 행이 남고, 그 한 건이
     * `uq_batch_execution_running` 때문에 이후 모든 배치 실행을 **영구히** 막는다. 임계를 너무
     * 짧게 잡으면 정상 실행 중인 배치를 죽었다고 오판하므로, 실제 실행 시간(수초~수분)보다 넉넉한
     * 30분을 기본값으로 둔다. `BatchExecutionRecorder.start`가 소비한다.
     */
    val staleRunTimeout: Duration = Duration.ofMinutes(30),
)
