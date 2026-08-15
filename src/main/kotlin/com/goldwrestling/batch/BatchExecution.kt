package com.goldwrestling.batch

import com.goldwrestling.admin.Admin
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * 배치 실행 이력 (glossary.md "배치 실행 이력", D-108·D-113).
 *
 * **append-only** — 전 필드를 `val`로 선언해 이력 불변성을 코드로 보장한다(setter 없음).
 *
 * **관측·복구 판단용이며 멱등성의 근거가 아니다(D-108)** — 이 테이블을 "오늘 이미 실행했나"
 * 판단에 쓰면 D-106 설계가 무너진다. 멱등성은 `InactivityDueDateCalculator`의 상태 기반
 * 부족분 계산이 담당한다.
 */
@Entity
@Table(name = "batch_execution")
class BatchExecution(
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    val trigger: BatchTrigger,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by_admin_id")
    val triggeredBy: Admin?,
    @Column(name = "started_at", nullable = false)
    val startedAt: OffsetDateTime,
    @Column(name = "finished_at", nullable = false)
    val finishedAt: OffsetDateTime,
    @Column(name = "processed_member_count", nullable = false)
    val processedMemberCount: Int,
    @Column(name = "deducted_count", nullable = false)
    val deductedCount: Int,
    @Column(name = "skipped_count", nullable = false)
    val skippedCount: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: BatchExecutionStatus,
    @Column(name = "error_summary", length = 1000)
    val errorSummary: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
