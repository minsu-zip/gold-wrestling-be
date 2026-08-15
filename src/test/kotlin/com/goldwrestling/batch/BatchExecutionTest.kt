package com.goldwrestling.batch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 실행 이력의 "시작 시 삽입 → 종료 시 확정" 모델(D-117, 05-REVIEW.md WR-02)을 스프링 컨텍스트
 * 없이 검증하는 단위테스트 — [BatchExecution.finish]는 엔티티 메서드이므로 conventions §10.0에
 * 따라 단위테스트가 필수다.
 *
 * DB 제약(실행 중 행은 최대 1건)은 여기서 증명할 수 없다 —
 * [BatchExecutionRepositoryTest]가 실제 PostgreSQL로 맡는다.
 */
class BatchExecutionTest {
    private val startedAt = OffsetDateTime.of(2026, 9, 1, 4, 0, 0, 0, SEOUL_OFFSET)

    @Test
    fun `배치 실행 결과 상태값은 RUNNING·SUCCESS·PARTIAL_FAILURE·FAILED 4종이다`() {
        assertThat(BatchExecutionStatus.entries).containsExactlyInAnyOrder(
            BatchExecutionStatus.RUNNING,
            BatchExecutionStatus.SUCCESS,
            BatchExecutionStatus.PARTIAL_FAILURE,
            BatchExecutionStatus.FAILED,
        )
    }

    @Test
    fun `실행을 시작한 이력은 RUNNING 상태에 종료 시각이 없고 집계가 모두 0이다`() {
        val execution = startedExecution()

        assertThat(execution.status).isEqualTo(BatchExecutionStatus.RUNNING)
        assertThat(execution.finishedAt).isNull()
        assertThat(execution.processedMemberCount).isZero()
        assertThat(execution.deductedCount).isZero()
        assertThat(execution.skippedCount).isZero()
        assertThat(execution.errorSummary).isNull()
    }

    @Test
    fun `finish를 호출하면 상태·종료 시각·집계 3종·오류 요약이 인자 값으로 확정된다`() {
        val execution = startedExecution()
        val finishedAt = startedAt.plusMinutes(3)

        execution.finish(
            status = BatchExecutionStatus.PARTIAL_FAILURE,
            finishedAt = finishedAt,
            processedMemberCount = 10,
            deductedCount = 7,
            skippedCount = 2,
            errorSummary = "memberId=1: IllegalStateException",
        )

        assertThat(execution.status).isEqualTo(BatchExecutionStatus.PARTIAL_FAILURE)
        assertThat(execution.finishedAt).isEqualTo(finishedAt)
        assertThat(execution.processedMemberCount).isEqualTo(10)
        assertThat(execution.deductedCount).isEqualTo(7)
        assertThat(execution.skippedCount).isEqualTo(2)
        assertThat(execution.errorSummary).isEqualTo("memberId=1: IllegalStateException")
    }

    @Test
    fun `실행 전체가 실패해도 FAILED로 확정할 수 있어 이력이 RUNNING으로 남지 않는다`() {
        val execution = startedExecution()

        execution.finish(
            status = BatchExecutionStatus.FAILED,
            finishedAt = startedAt.plusSeconds(1),
            processedMemberCount = 0,
            deductedCount = 0,
            skippedCount = 0,
            errorSummary = "DataAccessResourceFailureException",
        )

        assertThat(execution.status).isEqualTo(BatchExecutionStatus.FAILED)
        assertThat(execution.finishedAt).isNotNull()
    }

    @Test
    fun `finish는 시작 시점에 확정된 트리거·시작 시각·실행 관리자 id를 바꾸지 않는다`() {
        val execution = startedExecution(triggeredByAdminId = 42L)

        execution.finish(
            status = BatchExecutionStatus.SUCCESS,
            finishedAt = startedAt.plusMinutes(1),
            processedMemberCount = 1,
            deductedCount = 1,
            skippedCount = 0,
            errorSummary = null,
        )

        assertThat(execution.trigger).isEqualTo(BatchTrigger.MANUAL)
        assertThat(execution.startedAt).isEqualTo(startedAt)
        assertThat(execution.triggeredByAdminId).isEqualTo(42L)
    }

    private fun startedExecution(triggeredByAdminId: Long? = null): BatchExecution =
        BatchExecution(
            trigger = if (triggeredByAdminId == null) BatchTrigger.SCHEDULED else BatchTrigger.MANUAL,
            triggeredByAdminId = triggeredByAdminId,
            startedAt = startedAt,
        )

    companion object {
        private val SEOUL_OFFSET: ZoneOffset = ZoneOffset.ofHours(9)
    }
}
