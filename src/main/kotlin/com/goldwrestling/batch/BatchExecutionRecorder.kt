package com.goldwrestling.batch

import com.goldwrestling.config.InactivityBatchProperties
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 배치 실행의 **시작과 종료만** 기록하는 서비스(D-117·D-118). 차감 로직은 여기 없다 —
 * 실행 본문은 [InactivityBatchRunner]가, 실제 차감은 [InactivityDeductionService]가 담당한다.
 *
 * ### 왜 별도 스프링 빈인가
 * 러너([InactivityBatchRunner])에는 `@Transactional`을 붙일 수 없다 — 붙이면 한 회원의 실패가
 * 앞서 처리된 회원 전원을 롤백시켜 **실패 격리**(D-112)가 깨진다. 그렇다고 러너 안에 시작·종료
 * 기록 메서드를 두고 `@Transactional`을 붙여도 소용없다: **같은 클래스 내부 호출은 스프링 프록시를
 * 거치지 않아 트랜잭션 경계가 아예 생기지 않는다**(self-invocation). 그래서 기록자를 별도 빈으로
 * 분리한다 — [InactivityDeductionService]가 이미 같은 이유로 분리돼 있다(05-01 결정 C).
 *
 * ### 왜 `REQUIRES_NEW`인가
 * 호출부가 (읽기 전용이라도) 트랜잭션을 열고 있으면 `REQUIRED`로 참여한 INSERT는 **호출부가 끝날
 * 때까지 커밋되지 않는다.** 그러면 그 사이에 들어온 두 번째 실행이 `RUNNING` 행을 보지 못해
 * `uq_batch_execution_running`이 발동하지 않고, 결국 실행 직렬화(CR-01)가 성립하지 않는다.
 * `REQUIRES_NEW`는 호출부 트랜잭션을 잠시 중단하고 **자기 트랜잭션을 즉시 커밋**하므로, 다른
 * 커넥션이 곧바로 그 행을 보고 409를 받는다. 종료 기록도 같은 이유로 별개 트랜잭션이어야
 * 한다 — 본문이 어떻게 끝나든(예외 포함) 확정이 남아야 행이 `RUNNING`으로 방치되지 않는다(WR-02).
 *
 * ### FK 위반과 유니크 위반의 구분
 * [start]는 `DataIntegrityViolationException`을 **전부** [BatchAlreadyRunningException]으로 바꾼다.
 * 그런데 `triggeredByAdminId`가 존재하지 않는 관리자면 FK(`fk_batch_execution_admin`)가, `MANUAL`인데
 * id가 null이면 CHECK(`ck_batch_execution_trigger`)가 **같은 예외**를 던진다. 그래서 관리자 존재
 * 검증은 이 메서드에 오기 전(`InactivityBatchRunner.resolveTriggeredByAdminId`)에 끝난다는 것이
 * 전제다(D-117) — 그 전제가 깨지면 "관리자 없음"이 "이미 실행 중"으로 잘못 보고된다.
 *
 * ### 이 테이블은 여전히 부족분 계산의 근거가 아니다 (D-106·D-108)
 * `batch_execution`은 "지금 도는 배치가 있나"(중복 실행 거부)에만 쓴다. "오늘 이미 실행했나"의
 * 근거는 원장(`pass_transaction`)의 `INACTIVITY` 건수다. 둘을 섞으면 상태 기반 캐치업이 무너진다.
 */
@Service
@Transactional(readOnly = true)
class BatchExecutionRecorder(
    private val batchExecutionRepository: BatchExecutionRepository,
    private val properties: InactivityBatchProperties,
    private val clock: Clock,
) {
    /**
     * 실행 시작을 기록하고 저장된 `RUNNING` 이력을 반환한다.
     *
     * 흐름: ① 임계 시간([InactivityBatchProperties.staleRunTimeout], 기본 30분)을 넘긴 `RUNNING`
     * 행을 `FAILED`(`errorSummary = "STALE"`)로 정리 → ② 새 `RUNNING` 행 삽입.
     *
     * 저장은 **flush를 강제하는 쪽**을 쓴다 — 평범한 `save`는 트랜잭션 커밋 시점까지 INSERT를 미룰
     * 수 있어, 유니크 위반이 이 메서드 **바깥**(커밋 시점)에서 터지면 [BatchAlreadyRunningException]
     * 으로 바꿀 기회를 놓친다.
     *
     * 위반을 잡은 뒤 **정상 반환하면 안 된다.** flush 실패로 트랜잭션이 이미 rollback-only로
     * 표시돼 있어, 값을 반환하면 커밋 시도 시 `UnexpectedRollbackException`이 난다 — 호출부는
     * 409 대신 500을 보게 된다. 반드시 예외를 던져 이 트랜잭션이 롤백되게 한다.
     *
     * stale 정리와 INSERT가 같은 트랜잭션이라, 두 실행이 동시에 정리를 시도하면 진 쪽은 정리까지
     * 함께 롤백되고 409를 받는다 — 그것이 안전한 결과다(T-05D-12-02).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun start(
        trigger: BatchTrigger,
        triggeredByAdminId: Long?,
    ): BatchExecution {
        val now = OffsetDateTime.now(clock)

        val cleaned =
            batchExecutionRepository.markStaleRunningAsFailed(
                threshold = now.minus(properties.staleRunTimeout),
                finishedAt = now,
                errorSummary = STALE_ERROR_SUMMARY,
            )
        if (cleaned > 0) {
            // 앱이 비정상 종료했다는 뜻이다 — 운영자가 알아채야 한다(정상 운영에서는 나오지 않는다).
            logger.warn(
                "임계 시간({})을 넘긴 배치 실행 이력 {}건을 FAILED(STALE)로 정리했습니다. 이전 실행이 비정상 종료된 것으로 보입니다.",
                properties.staleRunTimeout,
                cleaned,
            )
        }

        return try {
            batchExecutionRepository.saveAndFlush(
                BatchExecution(
                    trigger = trigger,
                    triggeredByAdminId = triggeredByAdminId,
                    startedAt = now,
                    status = BatchExecutionStatus.RUNNING,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            logger.info("이미 실행 중인 배치가 있어 새 실행을 거부했습니다. (trigger={})", trigger, e)
            throw BatchAlreadyRunningException()
        }
    }

    /**
     * 실행 종료를 확정하고 **확정된 엔티티를 반환한다** — 05-13의 러너와 05-15의 수동 실행·조회
     * API가 이 반환값을 그대로 응답·집계에 쓰므로 호출부가 재조회를 한 번 더 하지 않게 한다.
     *
     * 값 대입은 [BatchExecution.finish] 한 번으로 묶여 있어(반쪽 확정 방지) 여기서는 더티 체킹으로
     * 반영된다 — 별도 `save` 호출이 필요 없다.
     *
     * 대상 행이 없으면 [IllegalStateException]이다. 이것은 사용자 입력 오류가 아니라 **프로그래밍
     * 오류**(존재하지 않는 실행 id로 확정 시도)이므로 도메인 예외·에러코드를 만들지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finish(
        executionId: Long,
        status: BatchExecutionStatus,
        processedMemberCount: Int,
        deductedCount: Int,
        skippedCount: Int,
        errorSummary: String?,
    ): BatchExecution {
        val execution =
            batchExecutionRepository.findById(executionId).orElseThrow {
                IllegalStateException("확정할 배치 실행 이력(id=$executionId)을 찾을 수 없습니다.")
            }

        execution.finish(
            status = status,
            finishedAt = OffsetDateTime.now(clock),
            processedMemberCount = processedMemberCount,
            deductedCount = deductedCount,
            skippedCount = skippedCount,
            errorSummary = errorSummary,
        )

        return execution
    }

    companion object {
        /** 방치된 `RUNNING` 행을 정리할 때 남기는 고정 문구(D-117, glossary "정체된 실행"). */
        const val STALE_ERROR_SUMMARY = "STALE"

        private val logger = LoggerFactory.getLogger(BatchExecutionRecorder::class.java)
    }
}
