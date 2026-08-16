package com.goldwrestling.batch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.core.task.TaskExecutor
import org.springframework.data.domain.PageRequest
import java.util.Optional
import java.util.concurrent.RejectedExecutionException

/**
 * [AdminBatchService]의 **접수 계약**을 고정한다 — "시작은 동기, 본문은 다른 스레드"라는 분리가
 * 이 플랜(WR-05)의 전부이기 때문이다. 협력자는 전부 가짜 객체이고 스프링 컨텍스트를 띄우지
 * 않는다(conventions §10.1) — DB·트랜잭션이 실제로 도는 경로는 `AdminBatchControllerTest`와
 * `AdminBatchRunConcurrencyTest`가 통합으로 검증한다.
 *
 * 여기서 지키는 것은 네 가지다:
 * - **본문이 호출 스레드에서 돌지 않는다.** 돌면 Tomcat 스레드가 배치 내내 묶여 프록시·LB
 *   타임아웃이 나고, 관리자의 재시도가 CR-01의 이중 차감 트리거가 된다(WR-05).
 * - **거부는 실행기에 닿기 전에 끝난다.** 이미 실행 중이면 `RUNNING` 행 INSERT가 막히므로
 *   ([BatchAlreadyRunningException]) 작업을 제출하지 않는다 — 제출하면 없는 실행 id로 본문이 돈다.
 * - **실행기가 작업을 거부하면 방금 만든 `RUNNING` 행을 반드시 정리한다.** 정리하지 않으면 그 행
 *   하나가 이후 모든 배치를 stale 임계(기본 30분)까지 막는다(T-05D-15-04).
 * - **실행기 스레드로 예외가 새어 나가지 않는다.** 새면 스레드 풀 기본 핸들러가 맥락 없는 스택만
 *   찍고, 실패 이력(러너가 남긴 `FAILED`)과 로그가 연결되지 않는다.
 */
class AdminBatchServiceTest {
    private val runner = mock(InactivityBatchRunner::class.java)
    private val recorder = mock(BatchExecutionRecorder::class.java)
    private val batchExecutionRepository = mock(BatchExecutionRepository::class.java)
    private val executor = mock(TaskExecutor::class.java)
    private val service = AdminBatchService(runner, recorder, batchExecutionRepository, executor)

    @Test
    fun `실행 시작은 동기로 하고 본문은 호출 스레드에서 돌지 않는다`() {
        val started = runningExecution(EXECUTION_ID)
        given(runner.start(BatchTrigger.MANUAL, ADMIN_ID)).willReturn(started)

        val returned = service.launchInactivityRun(ADMIN_ID)

        assertThat(returned).isSameAs(started)
        verify(executor).execute(any(Runnable::class.java))
        // 본문은 아직 돌지 않았다 — 실행기에 넘겼을 뿐이다
        verify(runner, never()).runStarted(EXECUTION_ID)
    }

    @Test
    fun `실행기에 넘긴 작업이 돌면 시작된 실행의 본문이 실행된다`() {
        // 모의 객체를 인자 자리에서 만들지 않는다 — 진행 중인 stubbing 안에서 다른 stubbing이
        // 시작돼 Mockito가 UnfinishedStubbingException을 던진다
        val started = runningExecution(EXECUTION_ID)
        given(runner.start(BatchTrigger.MANUAL, ADMIN_ID)).willReturn(started)

        service.launchInactivityRun(ADMIN_ID)

        submittedTask().run()

        verify(runner).runStarted(EXECUTION_ID)
    }

    @Test
    fun `본문이 실패해도 실행기 스레드 밖으로 예외가 새어 나가지 않는다`() {
        val started = runningExecution(EXECUTION_ID)
        given(runner.start(BatchTrigger.MANUAL, ADMIN_ID)).willReturn(started)
        given(runner.runStarted(EXECUTION_ID)).willThrow(IllegalStateException("벌크 조회 실패"))

        service.launchInactivityRun(ADMIN_ID)

        // 러너가 이미 FAILED 이력을 남기고 예외를 재전파한다 — 여기서 잡지 않으면 스레드 풀의
        // 기본 핸들러가 맥락 없는 스택만 찍는다
        assertThatCode { submittedTask().run() }.doesNotThrowAnyException()
    }

    @Test
    fun `이미 실행 중이면 예외가 그대로 전파되고 작업이 실행기에 제출되지 않는다`() {
        given(runner.start(BatchTrigger.MANUAL, ADMIN_ID)).willThrow(BatchAlreadyRunningException())

        assertThatThrownBy { service.launchInactivityRun(ADMIN_ID) }
            .isInstanceOf(BatchAlreadyRunningException::class.java)

        verifyNoInteractions(executor)
    }

    @Test
    fun `실행기가 작업을 거부하면 방금 만든 실행 중 이력을 FAILED로 정리한 뒤 예외를 던진다`() {
        val started = runningExecution(EXECUTION_ID)
        given(runner.start(BatchTrigger.MANUAL, ADMIN_ID)).willReturn(started)
        doThrow(RejectedExecutionException("pool is full"))
            .`when`(executor)
            .execute(any(Runnable::class.java))

        assertThatThrownBy { service.launchInactivityRun(ADMIN_ID) }
            .isInstanceOf(RejectedExecutionException::class.java)

        verify(recorder).finish(
            executionId = EXECUTION_ID,
            status = BatchExecutionStatus.FAILED,
            processedMemberCount = 0,
            deductedCount = 0,
            skippedCount = 0,
            errorSummary = AdminBatchService.REJECTED_ERROR_SUMMARY,
        )
    }

    @Test
    fun `존재하지 않는 실행 id를 조회하면 BatchExecutionNotFoundException이다`() {
        given(batchExecutionRepository.findById(404L)).willReturn(Optional.empty())

        assertThatThrownBy { service.getExecution(404L) }
            .isInstanceOf(BatchExecutionNotFoundException::class.java)
    }

    @Test
    fun `실행 id로 조회하면 그 이력을 그대로 반환한다`() {
        val execution = runningExecution(EXECUTION_ID)
        given(batchExecutionRepository.findById(EXECUTION_ID)).willReturn(Optional.of(execution))

        assertThat(service.getExecution(EXECUTION_ID)).isSameAs(execution)
    }

    @Test
    fun `최근 실행 목록은 시작 시각 내림차순으로 limit 건을 조회한다`() {
        service.listRecentExecutions(20)

        verify(batchExecutionRepository).findAllByOrderByStartedAtDesc(PageRequest.of(0, 20))
    }

    /**
     * `limit`은 1..[AdminBatchService.MAX_LIMIT]로 보정된다 — 0·음수는 `PageRequest.of`가
     * `IllegalArgumentException`(500)을 던지고, 상한이 없으면 이력이 몇 년치 쌓인 뒤 한 번의
     * 호출이 전부를 메모리에 올린다.
     */
    @Test
    fun `limit이 범위를 벗어나면 1과 100 사이로 보정된다`() {
        service.listRecentExecutions(0)
        service.listRecentExecutions(-5)
        service.listRecentExecutions(10_000)

        verify(batchExecutionRepository, times(2))
            .findAllByOrderByStartedAtDesc(PageRequest.of(0, 1))
        verify(batchExecutionRepository).findAllByOrderByStartedAtDesc(PageRequest.of(0, AdminBatchService.MAX_LIMIT))
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** 실행기에 제출된 작업 하나를 꺼낸다 — 테스트가 그 자리에서 직접 돌려 본문 실행을 관찰한다. */
    private fun submittedTask(): Runnable {
        val captor = ArgumentCaptor.forClass(Runnable::class.java)
        verify(executor).execute(captor.capture())
        return captor.value
    }

    private fun runningExecution(id: Long): BatchExecution {
        val execution = mock(BatchExecution::class.java)
        given(execution.id).willReturn(id)
        return execution
    }

    private companion object {
        const val ADMIN_ID = 7L
        const val EXECUTION_ID = 42L
    }
}
