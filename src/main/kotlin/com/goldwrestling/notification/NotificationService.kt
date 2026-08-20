package com.goldwrestling.notification

import com.goldwrestling.reservation.Reservation
import com.goldwrestling.schedule.ClassSession
import com.goldwrestling.schedule.ClassType
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 관리자 알림 생성 전용 헬퍼(NOTIF-01, D-097).
 *
 * **이 클래스·메서드에는 `@Transactional`을 붙이지 않는다.** 이 서비스는 항상 호출부
 * (예약 생성/취소/변경 서비스, 휴강 처리 서비스)의 트랜잭션 안에서 실행되는 헬퍼다 — 자체
 * 트랜잭션 경계를 열면 "예약은 롤백됐는데 알림은 남는" 상태가 생긴다(D-020 트랜잭션 경계 원칙의 연장).
 *
 * **수신자 컬럼이 없는 이유(D-097)**: 이 알림의 수신자는 항상 관리자 전체로 고정이다 — 회원별로
 * 수신 대상을 가를 필요가 없어 주체/수신자 컬럼 자체를 두지 않았다. 회원용 알림은 요구사항
 * (NOTIF-01~03) 어디에도 조회 경로가 없어 만들지 않는다.
 *
 * **이 phase는 생성만 한다.** 조회·확인 처리·미확인 카운트·폴링 API는 Phase 6(NOTIF-02·03)이 만든다.
 *
 * **호출부 경고**: 이 메서드들은 [reservation]·[classSession]의 [Reservation.member] 같은 값을
 * 그대로 읽는다 — 벌크 UPDATE(`clearAutomatically = true`) 직후 준영속 상태가 된 엔티티를 그대로
 * 넘기면 `LazyInitializationException`이 날 수 있다. 호출부는 정원·잔여 조건부 UPDATE 이후
 * **재조회한 영속 엔티티**로 이 메서드를 호출해야 한다(RESEARCH Pitfall 4, `AdminPassService` 관례).
 */
@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val clock: Clock,
) {
    /** 회원이 예약을 생성했다(RESV-01·02) — 예약 시점 회원명·수업 정보를 비정규화해 담는다. */
    fun createReservationCreated(reservation: Reservation): Notification {
        val memberName = reservation.member.name
        val message =
            "${memberName}님이 ${formatDateTime(reservation.classDate, reservation.startTime)} " +
                "${classTypeLabel(reservation.classType)}을 예약했습니다."
        return save(
            type = NotificationType.RESERVATION_CREATED,
            reservation = reservation,
            classSession = null,
            memberName = memberName,
            classType = reservation.classType,
            classDate = reservation.classDate,
            startTime = reservation.startTime,
            message = message,
        )
    }

    /** 예약이 취소됐다 — [byAdmin]에 따라 `RESERVATION_CANCELED_BY_MEMBER`/`_BY_ADMIN`으로 나뉜다. */
    fun createReservationCanceled(
        reservation: Reservation,
        byAdmin: Boolean,
    ): Notification {
        val memberName = reservation.member.name
        val message =
            "${actorPrefix(byAdmin)}${memberName}님${subjectParticle(byAdmin)} " +
                "${formatDateTime(reservation.classDate, reservation.startTime)} " +
                "${classTypeLabel(reservation.classType)} 예약을 취소했습니다."
        return save(
            type = if (byAdmin) NotificationType.RESERVATION_CANCELED_BY_ADMIN else NotificationType.RESERVATION_CANCELED_BY_MEMBER,
            reservation = reservation,
            classSession = null,
            memberName = memberName,
            classType = reservation.classType,
            classDate = reservation.classDate,
            startTime = reservation.startTime,
            message = message,
        )
    }

    /**
     * 예약이 변경됐다(취소+재예약, D-090) — [newReservation]은 변경 후 새로 생성된 예약이다.
     * [byAdmin]에 따라 `RESERVATION_CHANGED_BY_MEMBER`/`_BY_ADMIN`으로 나뉜다.
     */
    fun createReservationChanged(
        newReservation: Reservation,
        byAdmin: Boolean,
    ): Notification {
        val memberName = newReservation.member.name
        val message =
            "${actorPrefix(byAdmin)}${memberName}님${subjectParticle(byAdmin)} " +
                "${formatDateTime(newReservation.classDate, newReservation.startTime)} " +
                "${classTypeLabel(newReservation.classType)}으로 예약을 변경했습니다."
        return save(
            type = if (byAdmin) NotificationType.RESERVATION_CHANGED_BY_ADMIN else NotificationType.RESERVATION_CHANGED_BY_MEMBER,
            reservation = newReservation,
            classSession = null,
            memberName = memberName,
            classType = newReservation.classType,
            classDate = newReservation.classDate,
            startTime = newReservation.startTime,
            message = message,
        )
    }

    /**
     * 수업이 휴강 처리됐다(policies §7) — **세션당 1건 요약형**이다(D-097). 정원 10명 수업을
     * 휴강해도 취소 건별 10건이 아니라 이 1건만 만든다. [reservation]은 항상 null, [classSession]만
     * 채운다 — 회원명이 특정되지 않는 이벤트라 [Notification.memberName]도 null이다.
     */
    fun createClassSessionSuspended(
        classSession: ClassSession,
        canceledCount: Int,
    ): Notification {
        val message =
            "${formatDateTime(classSession.classDate, classSession.startTime)} " +
                "${classTypeLabel(classSession.classType)}이 휴강 처리되어 예약 ${canceledCount}건이 자동 취소되었습니다."
        return save(
            type = NotificationType.CLASS_SESSION_SUSPENDED,
            reservation = null,
            classSession = classSession,
            memberName = null,
            classType = classSession.classType,
            classDate = classSession.classDate,
            startTime = classSession.startTime,
            message = message,
        )
    }

    private fun save(
        type: NotificationType,
        reservation: Reservation?,
        classSession: ClassSession?,
        memberName: String?,
        classType: ClassType?,
        classDate: LocalDate?,
        startTime: LocalTime?,
        message: String,
    ): Notification {
        val now = OffsetDateTime.now(clock)
        return notificationRepository.save(
            Notification(
                type = type,
                reservation = reservation,
                classSession = classSession,
                memberName = memberName,
                classType = classType,
                classDate = classDate,
                startTime = startTime,
                message = message,
                occurredAt = now,
                createdAt = now,
            ),
        )
    }

    /** 관리자 대리 처리면 "관리자가 ", 회원 셀프 처리면 빈 문자열 — 문장 맨 앞 주어를 만든다. */
    private fun actorPrefix(byAdmin: Boolean): String = if (byAdmin) "관리자가 " else ""

    /** 관리자 대리 처리("~님의")와 회원 셀프 처리("~님이")는 조사가 다르다. */
    private fun subjectParticle(byAdmin: Boolean): String = if (byAdmin) "의" else "이"

    private fun formatDateTime(
        classDate: LocalDate,
        startTime: LocalTime,
    ): String = "${classDate.format(DATE_FORMATTER)} ${startTime.format(TIME_FORMATTER)}"

    /**
     * 수업 종류 한글 라벨(docs/glossary.md "수업 종류" 표 그대로).
     *
     * **라벨 뒤에 "수업"을 덧붙이지 말 것** — `SESSION`의 라벨이 이미 "예약제 수업"이라
     * "예약제 수업 수업을 예약했습니다."처럼 중복된다(04-UAT 테스트 12에서 발견).
     * 세 라벨 모두 받침으로 끝나므로 조사는 `을`/`으로`/`이`로 고정해도 안전하다.
     */
    private fun classTypeLabel(classType: ClassType): String =
        when (classType) {
            ClassType.EVENING -> "저녁반"
            ClassType.SESSION -> "예약제 수업"
            ClassType.LESSON -> "1:1 레슨"
        }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
