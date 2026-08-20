package com.goldwrestling.notification

/**
 * 관리자 알림 종류 (glossary.md "알림 종류", D-097). 알림을 만드는 이벤트는 4종
 * (① 회원 예약 ② 회원 취소·변경 ③ 휴강 처리 ④ 관리자 대리 취소·변경)이지만, 주체(회원/관리자)별로
 * 갈라 6개로 표현한다 — FE가 알림 목록에서 "누가 한 행동인지"를 종류만 보고 구분할 수 있게 하기 위해서다.
 */
enum class NotificationType {
    /** 회원이 예약을 생성했다 */
    RESERVATION_CREATED,

    /** 회원이 스스로 예약을 취소했다 */
    RESERVATION_CANCELED_BY_MEMBER,

    /** 회원이 스스로 예약을 변경했다 */
    RESERVATION_CHANGED_BY_MEMBER,

    /** 관리자가 예약을 대리 취소했다(RESV-08) */
    RESERVATION_CANCELED_BY_ADMIN,

    /** 관리자가 예약을 대리 변경했다(RESV-08) */
    RESERVATION_CHANGED_BY_ADMIN,

    /** 관리자가 수업을 휴강 처리했다 — 세션당 1건 요약형(D-097, 정원만큼 개별 알림이 쏟아지는 것을 막는다) */
    CLASS_SESSION_SUSPENDED,
}
