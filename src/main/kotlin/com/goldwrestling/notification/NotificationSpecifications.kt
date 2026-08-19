package com.goldwrestling.notification

import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.time.OffsetDateTime

/**
 * 활동 피드(NOTIF-03, D-129) 동적 조건. `ReservationSpecifications`와 동일 관례를 따른다 — 조건이
 * 없으면 `null`을 반환하고, 호출부가 `Specification.allOf(listOfNotNull(...))`로 합성한다.
 *
 * 피드는 **읽음 여부(`isRead`)를 조건으로 걸지 않는다** — 알림과 같은 `notification` 테이블을
 * 읽지만 "시간순 전체 타임라인"이라는 다른 뷰이기 때문이다(D-129). 이 오브젝트에 `isRead` 조건을
 * 추가하지 않는다.
 */
object NotificationSpecifications {
    /**
     * 이벤트 발생 시각([Notification.occurredAt]) 범위 필터 — `createdAt`이 아니다. 활동 피드의
     * 시간축은 "언제 벌어진 일인가"이지 "언제 저장됐는가"가 아니다.
     */
    fun occurredBetween(
        from: OffsetDateTime?,
        to: OffsetDateTime?,
    ): Specification<Notification>? {
        if (from == null && to == null) return null
        return Specification { root, _, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>()
            if (from != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), from))
            if (to != null) predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), to))
            criteriaBuilder.and(*predicates.toTypedArray())
        }
    }

    /** 알림 종류 필터. `null`이면 조건 없음. */
    fun hasType(type: NotificationType?): Specification<Notification>? {
        if (type == null) return null
        return Specification { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<NotificationType>("type"), type)
        }
    }
}
