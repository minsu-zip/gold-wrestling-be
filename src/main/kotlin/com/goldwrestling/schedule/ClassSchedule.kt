package com.goldwrestling.schedule

import com.goldwrestling.branch.Branch
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
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 정기 시간표 (glossary.md "정기 시간표") — 요일+시각+수업종류+정원으로 정의되는 주간 반복 시간표.
 *
 * **Flyway 시드로만 채워진다(D-093).** 이 phase는 관리자 CRUD API를 만들지 않는다 — 그래서
 * `Pass.register` 같은 companion factory가 없다. `V7__seed_class_schedule.sql`이 송파점 52행
 * (`EVENING` 10·`SESSION` 16·`LESSON` 26)을 이미 심어 두었다.
 *
 * [dayOfWeek]는 `java.time.DayOfWeek`를 그대로 재사용한다 — 이 프로젝트 전용 enum을 새로 만들지
 * 않는다(04-RESEARCH.md Pattern 1). [capacity]는 `EVENING`이면 `null`, `SESSION`/`LESSON`이면
 * 양수다 — 이 규칙은 엔티티가 아니라 DB `ck_class_schedule_capacity_by_type` CHECK가 강제한다
 * (`Pass`의 `ck_pass_remaining_count_by_type`과 동일한 사고).
 */
@Entity
@Table(name = "class_schedule")
class ClassSchedule(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    val branch: Branch,
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    val dayOfWeek: DayOfWeek,
    @Enumerated(EnumType.STRING)
    @Column(name = "class_type", nullable = false, length = 20)
    val classType: ClassType,
    @Column(name = "start_time", nullable = false)
    val startTime: LocalTime,
    @Column(name = "end_time", nullable = false)
    val endTime: LocalTime,
    @Column(nullable = true)
    val capacity: Int?,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
