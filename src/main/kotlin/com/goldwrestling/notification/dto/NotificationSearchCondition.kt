package com.goldwrestling.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/**
 * 관리자 알림 목록 조회(`GET /api/admin/notifications`, NOTIF-02) 쿼리 파라미터 바인딩용.
 * `@ParameterObject @ModelAttribute @Valid`로 받는다(D-054, [ActivityFeedSearchCondition]과 동일 관례).
 *
 * **`@RequestParam Int` 3개를 이 객체로 묶은 이유**: 검증 없이 받은 `page`/`size`가 그대로
 * `PageRequest.of`로 들어가면 `size=0`·`page=-1`에서 `IllegalArgumentException`이 나고, 이건
 * `GlobalExceptionHandler`의 포괄 핸들러에 걸려 **400이 아니라 500**으로 나간다(conventions §8 위반 —
 * 잘못된 입력은 4xx여야 한다). 상한이 없으면 `size=100000`도 통과해 폴링 1회가 전체 테이블을 긁는다.
 * 같은 컨트롤러의 활동 피드가 이미 이 방식으로 상·하한을 강제하고 있어 관례도 여기에 맞춘다.
 */
@Schema(description = "관리자 알림 목록 검색 조건 — 미확인 필터 + 페이지네이션")
data class NotificationSearchCondition(
    @field:Schema(description = "미확인 알림만 조회할지 여부", defaultValue = "false")
    val unreadOnly: Boolean = false,
    @field:Min(0)
    @field:Schema(description = "페이지 번호(0부터 시작)", defaultValue = "0")
    val page: Int = 0,
    @field:Min(1)
    @field:Max(100)
    @field:Schema(description = "페이지 크기(1~100)", defaultValue = "20")
    val size: Int = 20,
)
