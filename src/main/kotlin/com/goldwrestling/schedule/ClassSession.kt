package com.goldwrestling.schedule

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
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 날짜별 수업 (glossary.md "타임슬롯") — `ClassSchedule`이 특정 날짜에 실체화된 것. 예약/출석/휴강의 대상.
 *
 * **"필요할 때 생성"한다(D-094)** — 첫 예약 또는 관리자 휴강 처리 시점에만 행이 만들어진다
 * (`ClassSessionRepository.insertIfAbsent`). [startTime]·[endTime]·[capacity]는 [ClassSchedule]에서
 * 복사해 자기 컬럼으로 보유한다 — 나중에 "그날만 시간 옮기기"를 마이그레이션 없이 붙이기 위함이고,
 * 시간표를 참조만 하면 시간표 수정이 과거 수업까지 소급 변경해 지난 기록이 틀어진다.
 *
 * **판정만 하는 도메인 메서드**([assertReservable]·[assertSuspendable]·[assertResumable])는
 * 예외만 던지고 필드를 대입하지 않는다(D-072, `Pass.kt`와 동일 관례). **다음에 이 클래스를 읽는
 * 사람이 여기에 `status =`/`reservedCount =` 같은 대입문을 추가하지 않는다** — 실제 상태 전환은
 * [ClassSessionRepository]의 조건부 UPDATE가 담당한다. 이 원칙 위반이 Phase 3의 실제 회귀 버그
 * 원인이었다(D-072).
 */
@Entity
@Table(name = "class_session")
class ClassSession(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_schedule_id", nullable = false)
    val classSchedule: ClassSchedule,
    @Column(name = "class_date", nullable = false)
    val classDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "class_type", nullable = false, length = 20)
    val classType: ClassType,
    @Column(name = "start_time", nullable = false)
    val startTime: LocalTime,
    @Column(name = "end_time", nullable = false)
    val endTime: LocalTime,
    @Column(nullable = true)
    val capacity: Int?,
    @Column(name = "reserved_count", nullable = false)
    var reservedCount: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ClassSessionStatus,
    @Column(name = "canceled_at")
    var canceledAt: OffsetDateTime? = null,
    @Column(name = "cancel_reason", length = 500)
    var cancelReason: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canceled_by_admin_id")
    var canceledByAdmin: Admin? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /**
     * 예약 가능 여부 판정(RESV-01·02) — 휴강이면 [ClassSessionCanceledException], 예약 대상이
     * 아닌 수업 종류(`EVENING`)면 [ClassSessionNotReservableException]을 던진다(D-093).
     * **판정만 하며 대입문은 없다.**
     */
    fun assertReservable() {
        if (status == ClassSessionStatus.CANCELED) throw ClassSessionCanceledException()
        if (!classType.reservable) throw ClassSessionNotReservableException()
    }

    /**
     * 휴강 처리 가능 여부 판정(RESV-09) — 이미 휴강이면 [ClassSessionCanceledException]을 던진다.
     * **판정만 하며 대입문은 없다.** 실제 전환은 [ClassSessionRepository.suspendIfScheduled]가 한다.
     */
    fun assertSuspendable() {
        if (status == ClassSessionStatus.CANCELED) throw ClassSessionCanceledException()
    }

    /**
     * 휴강 해제 가능 여부 판정 — 휴강 상태가 아니면 [ClassSessionNotCanceledException]을 던진다.
     * **판정만 하며 대입문은 없다.** 실제 전환은 [ClassSessionRepository.resumeIfCanceled]가 한다.
     */
    fun assertResumable() {
        if (status != ClassSessionStatus.CANCELED) throw ClassSessionNotCanceledException()
    }
}
