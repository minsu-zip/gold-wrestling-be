package com.goldwrestling.notification

import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.notification.dto.ActivityFeedItemResponse
import com.goldwrestling.notification.dto.ActivityFeedSearchCondition
import com.goldwrestling.notification.dto.MarkAllReadResponse
import com.goldwrestling.notification.dto.NotificationListResponse
import com.goldwrestling.notification.dto.NotificationResponse
import com.goldwrestling.notification.dto.NotificationSearchCondition
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 관리자 알림 폴링·확인 처리 조회 서비스(NOTIF-02, D-129).
 *
 * **Phase 4의 [NotificationService](생성 전용)는 수정하지 않는다** — 생성 책임(예약·휴강 이벤트가
 * 트리거하는 알림 저장)과 조회 책임(이 서비스)을 분리해, 이 phase의 변경이 Phase 4가 이미 검증한
 * 생성 경로를 건드리지 않게 한다.
 *
 * 클래스 레벨 `@Transactional(readOnly = true)` + 쓰기 메서드([markAllAsRead])만 오버라이드하는
 * 관례(D-020, `AdminPassService`와 동일)를 따른다.
 */
@Service
@Transactional(readOnly = true)
class NotificationQueryService(
    private val notificationRepository: NotificationRepository,
    private val clock: Clock,
) {
    /**
     * 알림 목록을 조회한다. [unreadOnly]가 `true`면 미확인만, `false`면 전체를 최신순으로 반환하고,
     * 두 경우 모두 [NotificationListResponse.unreadCount]를 함께 채운다(D-129 "폴링 1회로 끝난다").
     */
    fun getNotifications(condition: NotificationSearchCondition): NotificationListResponse {
        val pageable = PageRequest.of(condition.page, condition.size)
        val notificationPage =
            if (condition.unreadOnly) {
                notificationRepository.findAllByIsReadFalseOrderByOccurredAtDescIdDesc(pageable)
            } else {
                notificationRepository.findAllByOrderByOccurredAtDescIdDesc(pageable)
            }
        return NotificationListResponse(
            notifications = PageResponse.from(notificationPage, NotificationResponse::from),
            unreadCount = notificationRepository.countByIsReadFalse(),
        )
    }

    /**
     * 미확인 알림을 전부 읽음 처리한다("모두 읽음", D-129 — 개별 읽음은 없다).
     *
     * `markAllAsRead` 벌크 UPDATE 이후 **반드시 [NotificationRepository.countByIsReadFalse]를 다시
     * 호출**해 [MarkAllReadResponse.unreadCount]를 채운다 — 벌크 UPDATE 이전에 로드했거나 계산한
     * 카운트를 재사용하면, 실제로는 전부 읽음 처리됐는데도 응답이 stale한 미확인 건수를 노출해
     * 관리자가 아직 안 읽은 알림이 남아 있다고 착각하게 된다(T-06-31, RESEARCH Pitfall 5).
     */
    @Transactional
    fun markAllAsRead(): MarkAllReadResponse {
        val updatedCount = notificationRepository.markAllAsRead(OffsetDateTime.now(clock))
        val unreadCount = notificationRepository.countByIsReadFalse()
        return MarkAllReadResponse(updatedCount = updatedCount, unreadCount = unreadCount)
    }

    /**
     * 활동 피드(NOTIF-03, D-129)를 조회한다 — `notification` 테이블의 다른 뷰다. 정렬은
     * `occurredAt` 내림차순 + `id` 내림차순 고정(동률 발생 시각의 순서를 안정적으로 만든다,
     * `NotificationRepository.findAllByOrderByOccurredAtDescIdDesc`와 동일 축).
     *
     * **`isRead` 조건을 절대 걸지 않는다** — 피드는 읽음 여부와 무관한 시간순 타임라인이다(D-129).
     */
    fun getActivityFeed(condition: ActivityFeedSearchCondition): PageResponse<ActivityFeedItemResponse> {
        val specification =
            Specification.allOf<Notification>(
                listOfNotNull(
                    NotificationSpecifications.occurredBetween(condition.from, condition.to),
                    NotificationSpecifications.hasType(condition.type),
                ),
            )
        val pageable =
            PageRequest.of(
                condition.page,
                condition.size,
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")),
            )
        val page = notificationRepository.findAll(specification, pageable)
        return PageResponse.from(page, ActivityFeedItemResponse::from)
    }
}
