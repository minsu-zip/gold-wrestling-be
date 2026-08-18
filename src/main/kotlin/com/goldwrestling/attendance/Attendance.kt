package com.goldwrestling.attendance

import com.goldwrestling.admin.Admin
import com.goldwrestling.member.Member
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.schedule.ClassSession
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
 * 출석 기록 (glossary.md "출석") — `ClassSession`별 참여 기록.
 *
 * 출석은 **차감과 무관한 참고용 데이터**다(policies §6) — 차감은 예약 시점에 이미 확정되어 있고,
 * 예약했지만 불참해도 차감은 유지된다. **예외적으로 저녁반 0.5회 차감(§4.2)만** 출석 확인과 함께
 * 관리자가 수동 처리하며, 그 차감 이력이 [passTransaction]이다 — 예약제/1:1 출석은 이 필드가
 * 항상 `null`이다.
 *
 * [status]만 `var`다(D-127 소급 수정 허용) — 나머지는 "누가 언제 체크했는지"의 이력이라 `val`로
 * 고정한다. **회원×세션 유니크(V11 `uq_attendance_member_session`, 조건 없는 일반 유니크)가 이중
 * 출석·이중 차감의 구조적 방지선이다** — `ABSENT`↔`ATTENDED` 정정은 새 행이 아니라 이 행의
 * [status]를 UPDATE하는 것으로 처리한다.
 */
@Entity
@Table(name = "attendance")
class Attendance(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_session_id", nullable = false)
    val classSession: ClassSession,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: AttendanceStatus,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pass_transaction_id")
    val passTransaction: PassTransaction?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by_admin_id", nullable = false)
    val checkedBy: Admin,
    @Column(name = "checked_at", nullable = false)
    val checkedAt: OffsetDateTime,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
