package com.goldwrestling.batch

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.RejectedExecutionException

/**
 * 관리자 배치 API의 서비스 계층(WR-05, 05-GAP-CONTEXT §2.4) — 실행 **접수**와 실행 **이력 조회**를
 * 담당한다. 차감 로직은 여기 없다: 본문은 [InactivityBatchRunner], 시작·확정 기록은
 * [BatchExecutionRecorder]가 소유한다.
 *
 * ### 시작은 동기, 본문은 비동기 — 이 분리가 이 클래스의 존재 이유다
 * 예전 수동 실행 API는 배치가 끝날 때까지 요청 스레드를 붙잡는 **동기 호출**이었다. 배치가 프록시·
 * 로드밸런서의 응답 타임아웃(흔히 60초)을 넘기면 관리자는 응답을 못 받고 다시 누르는데, 서버에서는
 * 첫 실행이 계속 돌고 있어 두 실행이 겹친다 — CR-01(동시 실행 이중 차감)의 가장 현실적인
 * 트리거였다(WR-05). 그래서 [launchInactivityRun]은
 * - `RUNNING` 행 INSERT(= 직렬화 진입)만 **동기**로 해서 중복 요청이 그 자리에서 409를 받게 하고,
 * - 본문은 전용 실행기(`inactivityBatchExecutor`)에 넘긴 뒤 즉시 반환한다.
 *
 * ### 실행기를 인터페이스 타입으로 받는다
 * 스레드 풀 구현 클래스가 아니라 [TaskExecutor](인터페이스) + `@Qualifier`로 받는다 —
 * 그래야 테스트가 이 빈을 무동작 모의로 대체해 "실행 중" 상태를 붙잡아 둘 수 있다
 * (`AdminBatchRunConcurrencyTest`). 실제 실행기를 쓰면 첫 실행이 두 번째 요청보다 먼저 끝나 버려
 * 동시 호출 경쟁이 재현되지 않는다.
 */
@Service
@Transactional(readOnly = true)
class AdminBatchService(
    private val runner: InactivityBatchRunner,
    private val recorder: BatchExecutionRecorder,
    private val batchExecutionRepository: BatchExecutionRepository,
    @param:Qualifier("inactivityBatchExecutor")
    private val executor: TaskExecutor,
) {
    /**
     * 미사용 차감 배치를 **시작만** 하고 `RUNNING` 상태의 실행 이력을 즉시 반환한다. 본문은 이
     * 메서드가 반환된 뒤 전용 실행기 스레드에서 돈다.
     *
     * 이미 실행 중이면 [BatchAlreadyRunningException]이 그대로 전파된다 — 잡지 않는다. 컨트롤러에
     * `try-catch`가 없으므로 `GlobalExceptionHandler`가 409 `ProblemDetail`로 바꾼다(D-017·D-118).
     * 이때 **작업은 실행기에 제출되지 않는다**: 시작에 실패했으니 넘길 실행 id 자체가 없다.
     *
     * **왜 [Propagation.NOT_SUPPORTED]인가.** 이 클래스의 기본값은 `readOnly = true` 트랜잭션인데,
     * 그 안에서 레코더의 `REQUIRES_NEW`가 중첩되는 상황을 아예 만들지 않기 위해서다 — 바깥
     * 트랜잭션은 이 메서드에서 아무것도 읽지 않으면서 커넥션만 붙잡고, 중첩 구간에서는 커넥션을
     * 두 개 쓰게 된다. 접수 경로는 짧고 잦으므로 트랜잭션을 아예 열지 않는 편이 맞다.
     *
     * 실행기에 넘기는 작업은 [runCatching]으로 감싼다. 러너가 이미 `FAILED` 이력을 남기고 예외를
     * 재전파하므로, 여기서 잡지 않으면 스레드 풀의 기본 핸들러가 **맥락 없는 스택만** 찍는다 —
     * 어느 실행이 실패했는지(`executionId`)가 로그에 남지 않는다.
     *
     * [RejectedExecutionException] 경로는 실질적으로 도달 불가하다(유니크 인덱스가 동시 실행을
     * 막으므로 큐가 찰 일이 없다). 그럼에도 방어하는 이유는 **잔존 `RUNNING` 행 하나가 이후 모든
     * 배치를 stale 임계(기본 30분)까지 영구 차단**하기 때문이다(T-05D-15-04). 확정 기록마저 실패하면
     * 로그만 남기고 원래 예외를 던진다 — 복구 실패가 원인 예외를 가리면 안 된다(러너와 같은 원칙).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun launchInactivityRun(adminId: Long): BatchExecution {
        val started = runner.start(trigger = BatchTrigger.MANUAL, triggeredByAdminId = adminId)
        val executionId = requireNotNull(started.id) { "시작된 배치 실행 이력에 id가 없습니다." }

        try {
            executor.execute {
                runCatching { runner.runStarted(executionId) }
                    .onFailure { logger.error("비동기 미사용 차감 배치가 실패했습니다. (executionId={})", executionId, it) }
            }
        } catch (e: RejectedExecutionException) {
            logger.error("배치 실행기가 작업을 거부했습니다 — 방금 시작한 실행을 정리합니다. (executionId={})", executionId, e)
            runCatching {
                recorder.finish(
                    executionId = executionId,
                    status = BatchExecutionStatus.FAILED,
                    processedMemberCount = 0,
                    deductedCount = 0,
                    skippedCount = 0,
                    errorSummary = REJECTED_ERROR_SUMMARY,
                )
            }.onFailure {
                logger.error("거부된 배치 실행 이력을 FAILED로 확정하지 못했습니다. (executionId={})", executionId, it)
            }
            throw e
        }

        return started
    }

    /**
     * 실행 이력 1건을 조회한다 — 관리자가 202 응답의 `Location`으로 진행 상태를 폴링하는 경로다.
     *
     * 없으면 [BatchExecutionNotFoundException](404)이다.
     *
     * **찾지 못한 id는 응답이 아니라 로그에만 남긴다.** 예외 메시지에 id를 보간하면 응답 문구로
     * "그 id의 실행이 존재하는가"를 훑을 수 있게 되므로 담지 않는다(conventions §8). 그렇다고
     * 아무 데도 남기지 않으면 운영 중 "어떤 id가 404였나"를 코드로 되짚을 수 없어, 여기서 한 줄
     * 남긴다 — 배치 실행 id는 회원 개인정보가 아니므로 로그에 남아도 안전하다.
     */
    fun getExecution(batchExecutionId: Long): BatchExecution =
        batchExecutionRepository.findById(batchExecutionId).orElseThrow {
            logger.info("배치 실행 이력 조회 실패 — 존재하지 않는 id (batchExecutionId={})", batchExecutionId)
            BatchExecutionNotFoundException(batchExecutionId)
        }

    /**
     * 최근 실행 이력을 시작 시각 내림차순으로 최대 [limit]건 반환한다 — D-108의 "배치가 안 돌았는지는
     * 실행 이력으로 확인한다"를 실제로 가능하게 만드는 조회다(WR-02의 운영자 질문).
     *
     * **HTTP 경로의 범위 검증은 여기가 아니라 컨트롤러가 한다** — `AdminBatchController`의
     * `limit` 파라미터에 `@Min(1)`·`@Max`가 붙어 있어 범위를 벗어난 요청은 이 메서드에 닿기 전에
     * `HandlerMethodValidationException` → **400 `VALIDATION_FAILED`**(`ProblemDetail`)로 거부된다
     * (`MemberSearchCondition`·`PassTransactionSearchCondition`의 페이징 파라미터와 같은 관례).
     * 잘못된 입력을 조용히 고쳐 200을 돌려주면 호출자는 자기 요청이 무시된 줄 모른다.
     *
     * 아래 `coerceIn`은 그 검증을 대신하는 것이 아니라 **HTTP를 거치지 않는 직접 호출**(테스트·
     * 내부 코드)에 대한 최후 방어다 — 0·음수는 `PageRequest.of`가 `IllegalArgumentException`(500)을
     * 던지고, 상한이 없으면 이력이 몇 년치 쌓인 뒤 한 번의 호출이 전부를 메모리에 올린다.
     */
    fun listRecentExecutions(limit: Int): List<BatchExecution> =
        batchExecutionRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit.coerceIn(1, MAX_LIMIT)))

    companion object {
        /** 실행 이력 목록 1회 조회 상한. */
        const val MAX_LIMIT = 100

        /** 실행기 포화로 본문을 시작조차 못한 실행에 남기는 고정 문구(`BatchExecutionRecorder.STALE_ERROR_SUMMARY` 관례). */
        const val REJECTED_ERROR_SUMMARY = "REJECTED"

        private val logger = LoggerFactory.getLogger(AdminBatchService::class.java)
    }
}
