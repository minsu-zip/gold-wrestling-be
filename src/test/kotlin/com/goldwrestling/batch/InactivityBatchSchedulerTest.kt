package com.goldwrestling.batch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.springframework.scheduling.annotation.Scheduled

/**
 * [InactivityBatchScheduler]의 **위임 계약**을 고정한다 — cron 발화 자체는 스프링의 기능이라
 * 검증하지 않고, "발화했을 때 무엇을 호출하는가"만 본다.
 *
 * 이 테스트가 지키는 것은 세 가지다:
 * - 스케줄러가 `SCHEDULED` 트리거로, `triggeredByAdminId = null`로 러너를 부른다는 것.
 *   `MANUAL`로 부르면 `ck_batch_execution_trigger`(V9)가 관리자 id를 요구해 새벽에 조용히
 *   실패하고, `triggeredByAdminId`에 값이 들어가면 사람이 실행한 것처럼 이력이 남는다.
 * - 스케줄러가 러너 호출 **외에 아무것도 하지 않는다**는 것(RESEARCH Pattern 1). 조건문·쿼리가
 *   여기 생기면 자동 실행과 수동 실행(`AdminBatchController`)의 차감 규칙이 갈라지기 시작한다.
 * - 러너가 던지는 예외가 **cron 밖으로 나가지 않는다**는 것(WR-02). 예외가 스케줄러 밖으로
 *   빠져나가면 스프링 기본 핸들러의 로그 한 줄만 남고 운영자가 원인을 알 수 없다. 특히
 *   [BatchAlreadyRunningException]은 수동 실행과 겹쳤다는 뜻이라 **정상 경로**다 — 에러로 남기면
 *   매번 오탐이 된다.
 *
 * cron 표현식·`zone`은 문자열이라 리플렉션으로 직접 단언한다 — D-114가 정한 값(`0 0 4 * * *`,
 * `Asia/Seoul`)이 조용히 바뀌면 배치가 엉뚱한 시각에 돈다.
 *
 * 스프링 컨텍스트를 띄우지 않는다(conventions §10.1 — 순수 위임 로직은 단위테스트).
 */
class InactivityBatchSchedulerTest {
    private val runner = mock(InactivityBatchRunner::class.java)
    private val scheduler = InactivityBatchScheduler(runner)

    @Test
    fun `cron이 발화하면 SCHEDULED 트리거로 관리자 없이 러너를 한 번 호출한다`() {
        given(runner.run(BatchTrigger.SCHEDULED, null)).willReturn(mock(BatchExecution::class.java))

        scheduler.runDaily()

        verify(runner).run(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
        // 러너 호출 외의 협력자 상호작용이 없다 — 스케줄러는 트리거만 한다
        verifyNoMoreInteractions(runner)
    }

    /**
     * 04:00 cron과 관리자 수동 실행이 겹치면 뒤에 온 쪽이 거부된다(D-117·D-118). 그것이 안전한
     * 결과이고, 밀린 주기는 상태 기반 캐치업(D-106)이 다음 실행에서 자연 보정한다 — 그래서 이
     * 거부는 실패가 아니며 cron 밖으로 나가서는 안 된다.
     */
    @Test
    fun `이미 실행 중이라 거부되면 예외를 삼키고 cron 밖으로 내보내지 않는다`() {
        given(runner.run(BatchTrigger.SCHEDULED, null)).willThrow(BatchAlreadyRunningException())

        assertThatCode { scheduler.runDaily() }.doesNotThrowAnyException()

        verify(runner).run(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
        verifyNoMoreInteractions(runner)
    }

    @Test
    fun `러너가 그 밖의 예외를 던져도 cron 밖으로 내보내지 않는다`() {
        given(runner.run(BatchTrigger.SCHEDULED, null)).willThrow(IllegalStateException("벌크 조회 실패"))

        assertThatCode { scheduler.runDaily() }.doesNotThrowAnyException()

        verify(runner).run(trigger = BatchTrigger.SCHEDULED, triggeredByAdminId = null)
        // 이력은 러너가 이미 FAILED로 확정했다 — 스케줄러가 추가로 기록하지 않는다
        verifyNoMoreInteractions(runner)
    }

    @Test
    fun `cron 표현식과 시간대가 D-114가 정한 값이다`() {
        val annotation =
            InactivityBatchScheduler::class.java
                .getDeclaredMethod("runDaily")
                .getAnnotation(Scheduled::class.java)

        assertThat(annotation).isNotNull()
        // 6필드(초 분 시 일 월 요일) — 맨 앞 0이 "0초에"다. 5필드 crontab과 다르다
        assertThat(annotation.cron).isEqualTo("0 0 4 * * *")
        assertThat(annotation.zone).isEqualTo("Asia/Seoul")
    }

    @Test
    fun `@Scheduled 메서드는 정확히 하나다`() {
        val scheduledMethods =
            InactivityBatchScheduler::class.java.declaredMethods
                .filter { it.isAnnotationPresent(Scheduled::class.java) }

        // 트리거가 늘면 그만큼 러너 진입점이 늘어난다 — CR-01(동시 이중 차감)이 열리는 지점이라
        // 새 트리거를 추가할 때는 반드시 동시 실행 가드를 함께 본다
        assertThat(scheduledMethods).hasSize(1)
    }
}
