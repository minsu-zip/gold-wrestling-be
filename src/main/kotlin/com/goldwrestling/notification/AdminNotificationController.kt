package com.goldwrestling.notification

import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.notification.dto.ActivityFeedItemResponse
import com.goldwrestling.notification.dto.ActivityFeedSearchCondition
import com.goldwrestling.notification.dto.MarkAllReadResponse
import com.goldwrestling.notification.dto.NotificationListResponse
import com.goldwrestling.notification.dto.NotificationSearchCondition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 알림 폴링·확인 처리 API 2종(NOTIF-02, D-129) + 활동 피드 API 1종(NOTIF-03, D-129).
 * `SecurityConfig`에서 `/api/admin` 하위 전체가 `hasRole("ADMIN")` 전용이므로, **이 컨트롤러에는
 * 별도 권한 애노테이션을 붙이지 않는다**(D-040).
 *
 * **클래스 매핑이 `/api/admin/notifications`가 아니라 `/api/admin`이다** — 알림과 피드는 같은
 * `notification` 테이블의 다른 뷰지만 FE 화면이 서로 다르고, 피드를 알림 하위 경로
 * (`/api/admin/notifications/activity-feed`)에 두면 "알림의 부분 리소스"로 오해된다. 그래서
 * `AdminScheduleController`가 여러 리소스 계층을 한 컨트롤러에 담을 때 쓰는 관례(클래스는
 * `/api/admin`으로 넓게, 메서드마다 하위 경로)를 따른다. 알림 두 경로의 문자열은 06-09가 노출한
 * 값과 동일하게 유지된다(FE 계약 불변).
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020), 에러 응답은
 * `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "admin-notification", description = "관리자 알림 폴링·확인 처리·활동 피드")
class AdminNotificationController(
    private val notificationQueryService: NotificationQueryService,
) {
    @GetMapping("/notifications")
    @Operation(
        summary = "알림 목록 조회(미확인 카운트 포함)",
        description =
            "FE는 30초 주기로 이 엔드포인트를 폴링한다. 미확인 건수가 응답에 포함되므로 별도 " +
                "카운트 호출이 필요 없다(D-129).",
    )
    fun list(
        @ParameterObject @ModelAttribute @Valid condition: NotificationSearchCondition,
    ): NotificationListResponse = notificationQueryService.getNotifications(condition)

    @PostMapping("/notifications/read-all")
    @Operation(
        summary = "알림 모두 읽음 처리",
        description = "개별 읽음 처리는 제공하지 않는다(D-129).",
    )
    fun markAllAsRead(): MarkAllReadResponse = notificationQueryService.markAllAsRead()

    @GetMapping("/activity-feed")
    @Operation(
        summary = "활동 피드 조회(읽음 여부 무관 시간순, 기간·종류 필터)",
        description =
            "알림과 같은 notification 테이블을 읽음 여부와 무관하게 시간순으로 보여주는 다른 " +
                "뷰다(NOTIF-03, D-129). 별도 저장 경로가 없다.",
    )
    fun activityFeed(
        @ParameterObject @ModelAttribute @Valid condition: ActivityFeedSearchCondition,
    ): PageResponse<ActivityFeedItemResponse> = notificationQueryService.getActivityFeed(condition)
}
