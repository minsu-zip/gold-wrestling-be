package com.goldwrestling.batch.dto

import com.goldwrestling.batch.BatchExecution
import com.goldwrestling.batch.BatchExecutionStatus
import com.goldwrestling.batch.BatchTrigger
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

/**
 * 배치 실행 결과 응답 DTO(D-108·D-114·D-117). 관리자 수동 실행 API(`AdminBatchController`)가
 * [BatchExecution]을 이 형태로 변환해 반환한다.
 *
 * [BatchExecution]에는 LAZY 연관이 하나도 없다 — `triggeredByAdminId`는 `Admin` 연관이 아니라
 * 스칼라 컬럼이다(05-REVIEW.md WR-04). 따라서 이 변환은 **읽는 필드가 무엇이든** 프록시를
 * 초기화하지 않는다. 엔티티 자체(D-019)는 그대로 노출하지 않는다.
 */
@Schema(description = "배치 실행 결과")
data class BatchExecutionResponse(
    @field:Schema(description = "배치 실행 이력 ID") val batchExecutionId: Long,
    @field:Schema(description = "트리거 종류") val trigger: BatchTrigger,
    @field:Schema(description = "수동 실행한 관리자 ID — SCHEDULED면 null") val triggeredByAdminId: Long?,
    @field:Schema(description = "실행 시작 시각") val startedAt: OffsetDateTime,
    @field:Schema(description = "실행 종료 시각 — 아직 실행 중(RUNNING)이면 null") val finishedAt: OffsetDateTime?,
    @field:Schema(description = "차감 대상 후보로 조회된 회원 수") val processedMemberCount: Int,
    @field:Schema(description = "실제 차감된 건수") val deductedCount: Int,
    @field:Schema(description = "대상 소진 등으로 스킵된 건수 — 다음 실행이 상태 기반으로 이어받는다") val skippedCount: Int,
    @field:Schema(description = "실행 결과 — RUNNING이면 아직 실행 중이며 집계는 확정 전 값(0)이다") val status: BatchExecutionStatus,
    @field:Schema(description = "회원 단위 실패 요약(예외 종류만, 메시지는 담지 않는다) — 실패가 없으면 null") val errorSummary: String?,
) {
    companion object {
        /**
         * **트랜잭션 밖(컨트롤러)에서 호출해도 안전하다** — [BatchExecution]이 스칼라 필드만
         * 갖기 때문이다(위 클래스 KDoc). 예전에는 "무엇을 읽는가에 달려 있다"는 단서가 붙어
         * 있었지만, `triggeredBy` 연관을 없앤 지금은 조건 없이 안전하다.
         *
         * 실행 중(`RUNNING`) 이력도 그대로 변환된다 — [finishedAt]이 null이고 집계는 0이다.
         */
        fun from(execution: BatchExecution): BatchExecutionResponse =
            BatchExecutionResponse(
                batchExecutionId = requireNotNull(execution.id) { "저장되지 않은 BatchExecution은 응답으로 변환할 수 없습니다." },
                trigger = execution.trigger,
                triggeredByAdminId = execution.triggeredByAdminId,
                startedAt = execution.startedAt,
                finishedAt = execution.finishedAt,
                processedMemberCount = execution.processedMemberCount,
                deductedCount = execution.deductedCount,
                skippedCount = execution.skippedCount,
                status = execution.status,
                errorSummary = execution.errorSummary,
            )
    }
}
