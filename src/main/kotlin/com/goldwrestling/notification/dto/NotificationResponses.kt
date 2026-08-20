package com.goldwrestling.notification.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.notification.Notification
import com.goldwrestling.notification.NotificationType
import com.goldwrestling.schedule.ClassType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 관리자 알림 단건 응답(NOTIF-02, D-129).
 *
 * **[Notification.reservation]·[Notification.classSession] 연관을 건드리지 않는다** — 둘 다
 * `LAZY`이고, 표시에 필요한 정보(회원명·수업 종류·날짜·시각)는 이벤트 발생 시점에 이미 비정규화
 * 필드로 담겨 있다(D-097). 그래서 이 응답에는 `reservationId`·`classSessionId`도 넣지 않는다 —
 * FE가 알림에서 예약 상세로 이동하는 요구가 요구사항(NOTIF-02)에 없다.
 *
 * **[isRead]에 `@get:JsonProperty`가 붙는 이유**: Kotlin의 `is` 접두 Boolean 프로퍼티는 getter가
 * `isRead()`로 생성되는데, Jackson은 이를 `isRead`로 직렬화하는 반면 springdoc은 자바 빈 규약대로
 * 접두사를 떼어 `read`로 스키마를 만든다. 그대로 두면 **실제 응답(`isRead`)과 openapi.yaml 계약
 * (`read`)이 어긋나** FE가 생성한 타입으로 읽으면 항상 `undefined`가 된다(PR #20 리뷰 Warning에서
 * 실제 응답과 재생성된 스키마를 대조해 확인). getter에 이름을 고정해 둘을 일치시킨다.
 * `@field:Schema`가 아니라 `@get:Schema`인 것도 같은 이유다 — 스키마가 getter에서 유도되므로
 * 필드에 붙은 설명은 유실된다.
 */
@Schema(description = "관리자 알림 단건 응답 — 예약/세션 상세로의 이동 링크(id)는 제공하지 않는다")
data class NotificationResponse(
    @field:Schema(description = "알림 ID") val id: Long,
    @field:Schema(description = "알림 종류") val type: NotificationType,
    @field:Schema(description = "알림 문구") val message: String,
    @field:Schema(description = "관련 회원명 — 휴강 요약 알림은 특정 회원이 없어 null") val memberName: String?,
    @field:Schema(description = "관련 수업 종류 — 없으면 null") val classType: ClassType?,
    @field:Schema(description = "관련 수업 날짜 — 없으면 null") val classDate: LocalDate?,
    @field:Schema(description = "관련 수업 시작 시각 — 없으면 null") val startTime: LocalTime?,
    @field:Schema(description = "이벤트 발생 시각") val occurredAt: OffsetDateTime,
    @get:JsonProperty("isRead")
    @get:Schema(description = "확인 여부")
    val isRead: Boolean,
    @field:Schema(description = "확인(모두 읽음) 처리 시각 — 미확인이면 null") val readAt: OffsetDateTime?,
) {
    companion object {
        fun from(notification: Notification): NotificationResponse =
            NotificationResponse(
                id = requireNotNull(notification.id) { "저장되지 않은 Notification은 응답으로 변환할 수 없습니다." },
                type = notification.type,
                message = notification.message,
                memberName = notification.memberName,
                classType = notification.classType,
                classDate = notification.classDate,
                startTime = notification.startTime,
                occurredAt = notification.occurredAt,
                isRead = notification.isRead,
                readAt = notification.readAt,
            )
    }
}

/**
 * 알림 목록 응답 — [unreadCount]를 같은 응답에 함께 실어 FE의 30초 폴링이 API 1회 호출로 끝나게
 * 한다(D-129 "미확인 카운트는 별도 엔드포인트 없이 목록 응답에 포함").
 */
@Schema(description = "알림 목록 응답 — 미확인 건수를 함께 담아 폴링 1회로 끝난다(D-129)")
data class NotificationListResponse(
    @field:Schema(description = "알림 목록(페이지)") val notifications: PageResponse<NotificationResponse>,
    @field:Schema(description = "미확인 건수") val unreadCount: Long,
)

/**
 * "모두 읽음" 처리 응답 — [unreadCount]는 벌크 UPDATE **이후 재조회**한 값이다(항상 0이 기대값,
 * RESEARCH Pitfall 5의 회귀 방지 계약).
 */
@Schema(description = "모두 읽음 처리 응답")
data class MarkAllReadResponse(
    @field:Schema(description = "이번 호출로 새로 읽음 처리된 건수") val updatedCount: Int,
    @field:Schema(description = "처리 후 재조회한 미확인 건수 — 정상 처리라면 0") val unreadCount: Long,
)

/**
 * 활동 피드 항목 응답(NOTIF-03, D-129) — `notification` 테이블의 다른 뷰다.
 *
 * **[NotificationResponse]와 달리 `isRead`·`readAt`을 담지 않는다** — 피드는 읽음 여부와 무관한
 * 시간순 타임라인이라는 의미 자체가 이 두 필드를 노출하지 않는 것으로 표현된다.
 */
@Schema(description = "활동 피드 항목 응답 — 읽음 여부와 무관한 시간순 타임라인(D-129)")
data class ActivityFeedItemResponse(
    @field:Schema(description = "알림 ID") val id: Long,
    @field:Schema(description = "알림 종류") val type: NotificationType,
    @field:Schema(description = "알림 문구") val message: String,
    @field:Schema(description = "관련 회원명 — 휴강 요약 알림은 특정 회원이 없어 null") val memberName: String?,
    @field:Schema(description = "관련 수업 종류 — 없으면 null") val classType: ClassType?,
    @field:Schema(description = "관련 수업 날짜 — 없으면 null") val classDate: LocalDate?,
    @field:Schema(description = "관련 수업 시작 시각 — 없으면 null") val startTime: LocalTime?,
    @field:Schema(description = "이벤트 발생 시각") val occurredAt: OffsetDateTime,
) {
    companion object {
        fun from(notification: Notification): ActivityFeedItemResponse =
            ActivityFeedItemResponse(
                id = requireNotNull(notification.id) { "저장되지 않은 Notification은 응답으로 변환할 수 없습니다." },
                type = notification.type,
                message = notification.message,
                memberName = notification.memberName,
                classType = notification.classType,
                classDate = notification.classDate,
                startTime = notification.startTime,
                occurredAt = notification.occurredAt,
            )
    }
}
