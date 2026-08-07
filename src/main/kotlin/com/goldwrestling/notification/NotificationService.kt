package com.goldwrestling.notification

import com.goldwrestling.reservation.Reservation
import com.goldwrestling.schedule.ClassSession
import org.springframework.stereotype.Service

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
 * TODO(04-07 GREEN): 아래 4개 메서드를 실제로 구현한다. 지금은 RED 확보를 위한 스텁이다.
 */
@Service
class NotificationService {
    fun createReservationCreated(reservation: Reservation): Notification = TODO("04-07 GREEN에서 구현")

    fun createReservationCanceled(
        reservation: Reservation,
        byAdmin: Boolean,
    ): Notification = TODO("04-07 GREEN에서 구현")

    fun createReservationChanged(
        newReservation: Reservation,
        byAdmin: Boolean,
    ): Notification = TODO("04-07 GREEN에서 구현")

    fun createClassSessionSuspended(
        classSession: ClassSession,
        canceledCount: Int,
    ): Notification = TODO("04-07 GREEN에서 구현")
}
