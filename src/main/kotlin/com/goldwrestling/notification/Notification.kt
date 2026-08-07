package com.goldwrestling.notification

import com.goldwrestling.reservation.Reservation
import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassType
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
 * 관리자 인앱 알림 (glossary.md "관리자 알림", D-097).
 *
 * **append-only** — 표시·연결 필드는 전부 `val`(setter 없음, `PassTransaction`과 동일 관례).
 * `PassTransaction`의 "주체는 admin/member 중 하나"와 달리, 이 엔티티는 **수신자가 항상 관리자로
 * 고정**이라 주체(수신자) 컬럼 자체가 없다(D-097) — 회원용 알림은 이 phase 요구사항 어디에도
 * 조회 경로가 없어 만들지 않는다.
 *
 * [memberName]·[classType]·[classDate]·[startTime]은 이벤트 발생 시점 값을 담는 비정규화 표시
 * 정보다 — 회원명이 나중에 바뀌어도 "그때 그 알림"이 그대로 남는 것이 알림·활동 피드의 의미에 맞고,
 * 조회 시점 조인 없이 표시할 수 있게 한다. [reservation]은 예약 이벤트(①~④)에서, [classSession]은
 * 휴강 요약 알림(D-097)에서 채워진다 — 어느 쪽도 없는 고아 알림은 `ck_notification_target`(V6)이 막는다.
 *
 * [isRead]/[readAt]만 `var`다 — 이 phase는 저장만 하고, Phase 6(NOTIF-02)이 확인 처리에서 갱신한다.
 */
@Entity
@Table(name = "notification")
class Notification(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val type: NotificationType,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    val reservation: Reservation?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_session_id")
    val classSession: ClassSession?,
    @Column(name = "member_name", length = 50)
    val memberName: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "class_type", length = 20)
    val classType: ClassType?,
    @Column(name = "class_date")
    val classDate: LocalDate?,
    @Column(name = "start_time")
    val startTime: LocalTime?,
    @Column(nullable = false, length = 500)
    val message: String,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: OffsetDateTime,
    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,
    @Column(name = "read_at")
    var readAt: OffsetDateTime? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
