package com.goldwrestling.batch.dto

import com.goldwrestling.batch.BatchExecution
import com.goldwrestling.batch.BatchExecutionStatus
import com.goldwrestling.batch.BatchTrigger
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

/**
 * 배치 실행 결과 응답 DTO(D-108·D-114). 관리자 수동 실행 API(`AdminBatchController`)가
 * [BatchExecution]을 이 형태로 변환해 반환한다.
 *
 * [triggeredByAdminId]만 담고 [BatchExecution.triggeredBy] 자체(엔티티)는 담지 않는다 — LAZY
 * 연관 객체를 응답에 그대로 실으면 컨트롤러(트랜잭션 밖)에서 필드에 접근하는 순간
 * `LazyInitializationException`이 난다. `id`는 프록시가 DB 접근 없이 이미 들고 있는 값이라
 * `triggeredBy?.id`로 읽어도 프록시를 초기화하지 않는다(`PassResponse.from`의 `pass.member.id`와
 * 같은 패턴, 04·03에서 실제로 확인된 관례).
 */
@Schema(description = "배치 실행 결과")
data class BatchExecutionResponse(
    @field:Schema(description = "배치 실행 이력 ID") val batchExecutionId: Long,
    @field:Schema(description = "트리거 종류") val trigger: BatchTrigger,
    @field:Schema(description = "수동 실행한 관리자 ID — SCHEDULED면 null") val triggeredByAdminId: Long?,
    @field:Schema(description = "실행 시작 시각") val startedAt: OffsetDateTime,
    @field:Schema(description = "실행 종료 시각") val finishedAt: OffsetDateTime,
    @field:Schema(description = "차감 대상 후보로 조회된 회원 수") val processedMemberCount: Int,
    @field:Schema(description = "실제 차감된 건수") val deductedCount: Int,
    @field:Schema(description = "대상 소진 등으로 스킵된 건수 — 다음 실행이 상태 기반으로 이어받는다") val skippedCount: Int,
    @field:Schema(description = "실행 결과") val status: BatchExecutionStatus,
    @field:Schema(description = "회원 단위 실패 요약(예외 종류만, 메시지는 담지 않는다) — 실패가 없으면 null") val errorSummary: String?,
) {
    companion object {
        /** **트랜잭션이 열려 있는 서비스 계층 안에서만 호출한다** — 위 클래스 KDoc 참고. */
        fun from(execution: BatchExecution): BatchExecutionResponse =
            BatchExecutionResponse(
                batchExecutionId = requireNotNull(execution.id) { "저장되지 않은 BatchExecution은 응답으로 변환할 수 없습니다." },
                trigger = execution.trigger,
                triggeredByAdminId = execution.triggeredBy?.id,
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
