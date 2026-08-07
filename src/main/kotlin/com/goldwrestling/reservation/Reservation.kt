package com.goldwrestling.reservation

import com.goldwrestling.admin.Admin
import com.goldwrestling.member.Member
import com.goldwrestling.pass.Pass
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
 * 예약 (glossary.md "예약") — 회원 ↔ `ClassSession`.
 *
 * [classType]·[classDate]·[startTime]은 `ClassSession`에서 복사한 **비정규화 컬럼**이다 —
 * Postgres 부분 유니크 인덱스 3종(V6 `ux_reservation_*`)의 `WHERE` 절과 인덱스 대상 컬럼은
 * 반드시 같은 테이블 안에서 나와야 성립하기 때문에 존재한다(조인 기반 방어는 Postgres 부분
 * 인덱스로 표현할 수 없다) — `ClassSession`이 `ClassSchedule`의 시각을 복사한 것과 같은 이유다(D-094).
 *
 * [pass]는 이 예약이 차감한 **그 이용권**을 추적한다(D-091) — 취소 시 복구는 항상 이 이용권으로
 * 되돌린다(여러 장을 합산해 한 건을 예약하지 않으므로 예약 1건 ↔ 이용권 1장이 항상 대응한다).
 *
 * [canceledByMember]/[canceledByAdmin]은 `RefreshToken.member`/`admin`과 동일한 nullable 쌍
 * 관례다 — 취소 시 둘 중 정확히 하나만 채워지고, DB `ck_reservation_cancellation`(V6) CHECK가
 * 최종 방어선이다. [refunded]는 `CANCELED`일 때만 값이 있다 — 관리자 대리 취소의 "복구 안 함"
 * 선택(policies §3)을 표현한다.
 *
 * **취소·변경 가능 여부 판정은 이 플랜에서 만들지 않는다** — 04-09가 TDD로 `assertCancelableByMember`
 * 등을 이 클래스에 추가한다(테스트가 먼저). 판정 메서드에는 필드 대입문을 넣지 않는다(D-072) —
 * 실제 상태 전환은 [ReservationRepository]의 조건부 UPDATE가 담당한다.
 */
@Entity
@Table(name = "reservation")
class Reservation(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_session_id", nullable = false)
    val classSession: ClassSession,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pass_id", nullable = false)
    val pass: Pass,
    @Enumerated(EnumType.STRING)
    @Column(name = "class_type", nullable = false, length = 20)
    val classType: ClassType,
    @Column(name = "class_date", nullable = false)
    val classDate: LocalDate,
    @Column(name = "start_time", nullable = false)
    val startTime: LocalTime,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReservationStatus,
    @Column(name = "reserved_at", nullable = false)
    val reservedAt: OffsetDateTime,
    @Column(name = "canceled_at")
    var canceledAt: OffsetDateTime? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canceled_by_member_id")
    var canceledByMember: Member? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canceled_by_admin_id")
    var canceledByAdmin: Admin? = null,
    @Column(nullable = true)
    var refunded: Boolean? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
