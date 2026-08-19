package com.goldwrestling.notification

import com.goldwrestling.notification.dto.MarkAllReadResponse
import com.goldwrestling.notification.dto.NotificationListResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 알림 폴링·확인 처리 API 2종(NOTIF-02, D-129). `SecurityConfig`에서 `/api/admin` 하위
 * 전체가 `hasRole("ADMIN")` 전용이므로, **이 컨트롤러에는 별도 권한 애노테이션을 붙이지 않는다**
 * (D-040).
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020), 에러 응답은
 * `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/admin/notifications")
@Tag(name = "admin-notification", description = "관리자 알림 폴링·확인 처리")
class AdminNotificationController(
    private val notificationQueryService: NotificationQueryService,
) {
    @GetMapping("")
    @Operation(
        summary = "알림 목록 조회(미확인 카운트 포함)",
        description =
            "FE는 30초 주기로 이 엔드포인트를 폴링한다. 미확인 건수가 응답에 포함되므로 별도 " +
                "카운트 호출이 필요 없다(D-129).",
    )
    fun list(
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): NotificationListResponse = notificationQueryService.getNotifications(unreadOnly, page, size)

    @PostMapping("/read-all")
    @Operation(
        summary = "알림 모두 읽음 처리",
        description = "개별 읽음 처리는 제공하지 않는다(D-129).",
    )
    fun markAllAsRead(): MarkAllReadResponse = notificationQueryService.markAllAsRead()
}
