package com.goldwrestling.batch

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * 배치 실행 이력 (glossary.md "배치 실행 이력", D-108·D-113·D-117).
 *
 * **실행 1회 = 행 1건이며, 시작 시점에 `RUNNING`으로 삽입한 뒤 종료 시점에 같은 행을 [finish]로
 * 확정한다.** 확정 이후에는 다시 바꾸지 않는다. 이 구조가 두 가지를 동시에 해결한다:
 * - 전체 실패 시 이력이 한 줄도 안 남던 문제(05-REVIEW.md WR-02) — 시작 기록이 먼저 남는다
 * - 동시 실행 이중 차감(CR-01) — `RUNNING` 행의 유일성(V10 `uq_batch_execution_running`)이
 *   실행 직렬화 장치다(D-117). 두 번째 실행의 INSERT를 DB가 유니크 위반으로 거부한다
 *
 * 시작 시점에 확정되는 [trigger]·[triggeredByAdminId]·[startedAt]은 `val`로 잠그고, 종료 시점에
 * 확정되는 값만 `var`로 연다.
 *
 * [triggeredByAdminId]는 `Admin` 연관이 아니라 **스칼라 컬럼**이다(05-REVIEW.md WR-04) — 실행
 * 이력을 트랜잭션 밖에서 응답 DTO로 변환하는 경로(조회 API)에서 LAZY 프록시가 개입할 여지를
 * 아예 없앤다. FK(`fk_batch_execution_admin`)와 CHECK(`ck_batch_execution_trigger`)는 그대로
 * 살아 있으므로 무결성은 DB가 계속 보장한다.
 *
 * **멱등성의 근거가 아니다(D-108).** 이 테이블은 이제 "지금 도는 배치가 있나"(동시 실행 거부)에는
 * 쓰이지만, "오늘 이미 실행했나"(부족분 계산)에는 여전히 쓰지 않는다 — 부족분의 유일한 근거는
 * D-106의 상태 기반 계산(원장의 `INACTIVITY` 건수)이다. 둘을 섞으면 D-106 설계가 무너진다.
 */
@Entity
@Table(name = "batch_execution")
class BatchExecution(
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    val trigger: BatchTrigger,
    @Column(name = "triggered_by_admin_id")
    val triggeredByAdminId: Long?,
    @Column(name = "started_at", nullable = false)
    val startedAt: OffsetDateTime,
    @Column(name = "finished_at")
    var finishedAt: OffsetDateTime? = null,
    @Column(name = "processed_member_count", nullable = false)
    var processedMemberCount: Int = 0,
    @Column(name = "deducted_count", nullable = false)
    var deductedCount: Int = 0,
    @Column(name = "skipped_count", nullable = false)
    var skippedCount: Int = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: BatchExecutionStatus = BatchExecutionStatus.RUNNING,
    @Column(name = "error_summary", length = 1000)
    var errorSummary: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /**
     * 실행 종료를 한 번에 반영한다(conventions §7 — 비즈니스 규칙은 가능한 한 엔티티 메서드로).
     *
     * 확정 필드를 호출부가 하나씩 대입하지 않고 이 메서드 하나로 묶는 이유는, `status`만 바꾸고
     * `finishedAt`을 빠뜨리는 식의 **반쪽 확정**을 구조적으로 막기 위해서다 — 그런 행은 종료
     * 상태인데 종료 시각이 없어 운영 판단을 흐린다.
     */
    fun finish(
        status: BatchExecutionStatus,
        finishedAt: OffsetDateTime,
        processedMemberCount: Int,
        deductedCount: Int,
        skippedCount: Int,
        errorSummary: String?,
    ) {
        this.status = status
        this.finishedAt = finishedAt
        this.processedMemberCount = processedMemberCount
        this.deductedCount = deductedCount
        this.skippedCount = skippedCount
        this.errorSummary = errorSummary
    }
}
