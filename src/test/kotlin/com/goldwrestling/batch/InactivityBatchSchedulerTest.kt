package com.goldwrestling.batch

import org.assertj.core.api.Assertions.assertThat
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
 * 이 테스트가 지키는 것은 두 가지다:
 * - 스케줄러가 `SCHEDULED` 트리거로, `triggeredByAdminId = null`로 러너를 부른다는 것.
 *   `MANUAL`로 부르면 `ck_batch_execution_trigger`(V9)가 관리자 id를 요구해 새벽에 조용히
 *   실패하고, `triggeredByAdminId`에 값이 들어가면 사람이 실행한 것처럼 이력이 남는다.
 * - 스케줄러가 러너 호출 **외에 아무것도 하지 않는다**는 것(RESEARCH Pattern 1). 조건문·쿼리가
 *   여기 생기면 자동 실행과 수동 실행(`AdminBatchController`)의 차감 규칙이 갈라지기 시작한다.
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
