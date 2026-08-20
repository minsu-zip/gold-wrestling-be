package com.goldwrestling.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * 미사용 차감 배치 **본문 전용** 실행기(WR-05, 05-GAP-CONTEXT §2.4). 관리자 수동 실행
 * (`POST /api/admin/batch/inactivity-runs`)이 실행 시작만 동기로 하고 본문을 이 실행기에 넘긴다 —
 * 그래야 요청 스레드가 배치 내내 묶이지 않고 즉시 202를 돌려줄 수 있다.
 *
 * ### 왜 스레드 1개로 충분한가
 * 배치는 어차피 **동시에 하나만** 돌 수 있다 — 실행 시작이 `RUNNING` 행을 넣고, V10의 부분 유니크
 * 인덱스 `uq_batch_execution_running`이 두 번째 행을 거부하기 때문이다(D-117). 스레드를 늘려도
 * 두 번째 작업은 시작 단계에서 409로 끝나므로 늘릴 이유가 없다. 큐도 1이면 충분하다 — 큐가 깊으면
 * "언젠가 돌 예정인 작업"이 쌓이는데, 그 작업들은 각자 유니크 인덱스에 막혀 전부 실패한다.
 *
 * ### 왜 스프링 기본 실행기(`applicationTaskExecutor`)에 얹지 않는가
 * 두 가지 때문이다.
 * - **요청 처리용 자원을 잠식한다.** 기본 실행기는 서블릿 비동기 처리 등 요청 경로가 함께 쓰는
 *   풀이다. 수 분짜리 배치가 그 풀의 스레드를 차지하면 배치와 무관한 API 응답이 함께 느려진다.
 * - **추적이 안 된다.** 어느 풀에서 무엇이 도는지 로그로 구분할 수 없다. 전용 빈이면
 *   `inactivity-batch-1` 같은 스레드명이 그대로 로그에 찍혀 "지금 배치가 도는 중인가"를 로그만
 *   보고 판단할 수 있다.
 *
 * 그래서 [defaultCandidate = false][Bean.defaultCandidate]로 등록한다 — 이 빈은 이름을 명시한
 * 주입점(`@Qualifier("inactivityBatchExecutor")`)에서만 쓰이고, 타입만 보고 자동 주입되는 후보에서는
 * 빠진다. 스프링 부트의 `applicationTaskExecutor`는 `@ConditionalOnMissingBean(Executor::class)`
 * 조건이라, 이 표시가 없으면 우리 빈 하나 때문에 기본 실행기가 통째로 사라진다.
 *
 * 종료 처리: 애플리케이션이 내려갈 때 [ThreadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown]으로
 * 돌고 있는 배치를 최대 [AWAIT_TERMINATION_SECONDS]초 기다린다. 중간에 끊으면 `RUNNING` 행이
 * 확정되지 못한 채 남아 다음 실행을 stale 임계(기본 30분)까지 막는다.
 */
@Configuration
class BatchExecutorConfig {
    @Bean(name = ["inactivityBatchExecutor"], defaultCandidate = false)
    fun inactivityBatchExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 1
        executor.queueCapacity = 1
        executor.setThreadNamePrefix("inactivity-batch-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS)
        return executor
    }

    private companion object {
        /** 종료 시 실행 중인 배치를 기다리는 시간. 이보다 오래 걸리면 stale 정리(기본 30분)가 회수한다. */
        const val AWAIT_TERMINATION_SECONDS = 60
    }
}
