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
 * 취소·변경 가능 여부 판정 메서드 4종([assertCancelableByMember]·[assertCancelableByAdmin]·
 * [assertChangeableByMember]·[assertChangeableByAdmin])은 판정만 하며 필드 대입문을 넣지 않는다
 * (D-072) — 실제 상태 전환은 [ReservationRepository]의 조건부 UPDATE가 담당한다.
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

    /**
     * 회원의 취소 가능 여부 판정(policies §3, D-090). **판정만 하며 상태를 바꾸지 않는다(D-072)** —
     * 실제 상태 전환은 [ReservationRepository]의 조건부 UPDATE가 담당한다. 다음에 이 메서드를
     * 읽는 사람이 여기에 `status =` 같은 대입문을 추가하지 않는다.
     *
     * **당일 판정은 날짜([LocalDate]) 비교만 한다** — 마감 판정(수업 시작 시각 전까지 예약 가능)은
     * `ReservationWindow`가 담당하는 별도의 축이다(Pitfall 3, RESEARCH.md). 이 메서드 시그니처에
     * `LocalDateTime`/`OffsetDateTime`/`Clock`이 등장하면 두 축이 하나로 합쳐진 것이므로 그 자체가
     * 회귀다 — 저녁 수업을 그날 아침에 취소하려는 요청이 "아직 시작 전이니 가능"으로 잘못
     * 통과하는 사고가 여기서 난다.
     *
     * `!classDate.isAfter(today)`는 당일(`classDate == today`)과 과거(`classDate < today`)를
     * 한 조건으로 함께 거부한다 — 지난 수업은 이미 소비된 예약이라 당일보다 더 강하게 막아야 한다는
     * 정책(policies §3)을 반영한다.
     */
    fun assertCancelableByMember(today: LocalDate) {
        if (status == ReservationStatus.CANCELED) throw ReservationAlreadyCanceledException()
        if (!classDate.isAfter(today)) throw SameDayModificationNotAllowedException()
    }

    /**
     * 관리자의 취소 가능 여부 판정(policies §3 "관리자는 당일 포함 제약 없음"). **판정만 하며
     * 상태를 바꾸지 않는다** — [assertCancelableByMember]와 달리 당일·과거 날짜 검사가 없다.
     */
    fun assertCancelableByAdmin() {
        if (status == ReservationStatus.CANCELED) throw ReservationAlreadyCanceledException()
    }

    /**
     * 회원의 변경 가능 여부 판정(D-090). [assertCancelableByMember]와 **동일한 검사를 먼저
     * 수행**한다 — 그렇지 않으면 당일 취소가 금지된 회원이 "변경"으로 우회할 수 있다(T-04-40,
     * policies §3 "변경 허용 시 당일취소 우회 가능하므로"). 그 다음 같은 수업 종류 안에서만
     * 변경을 허용한다([newClassType] != [classType]이면 [ReservationTypeMismatchException]) —
     * 종류가 바뀌면 차감하는 이용권 종류까지 바뀌어 사실상 다른 예약이기 때문이다(D-090).
     */
    fun assertChangeableByMember(
        today: LocalDate,
        newClassType: ClassType,
    ) {
        assertCancelableByMember(today)
        if (newClassType != classType) throw ReservationTypeMismatchException()
    }

    /**
     * 관리자의 변경 가능 여부 판정 — [assertCancelableByAdmin]과 동일하게 당일·과거 제약이
     * 없고, 종류 일치 검사만 [assertChangeableByMember]와 동일하게 적용한다.
     */
    fun assertChangeableByAdmin(newClassType: ClassType) {
        assertCancelableByAdmin()
        if (newClassType != classType) throw ReservationTypeMismatchException()
    }
}
