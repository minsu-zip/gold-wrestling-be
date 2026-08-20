package com.goldwrestling.notice.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

/**
 * 공지 목록 조회(`GET /api/admin/notices`, `GET /api/members/notices`, NOTICE-01·02) 쿼리
 * 파라미터 바인딩용. `@ParameterObject @ModelAttribute @Valid`로 받는다(D-054,
 * [com.goldwrestling.notification.dto.NotificationSearchCondition]과 동일 관례) — 관리자·회원
 * 두 컨트롤러가 이 조건 객체를 공유한다, 두 목록은 정렬·페이지 계약이 동일하다.
 *
 * **`@RequestParam Int` 2개를 이 객체로 묶은 이유**: 검증 없이 받은 `page`/`size`가 그대로
 * `PageRequest.of`로 들어가면 `size=0`·`page=-1`에서 `IllegalArgumentException`이 나고, 이건
 * `GlobalExceptionHandler`의 포괄 핸들러에 걸려 **400이 아니라 500**으로 나간다(conventions §8 위반 —
 * 잘못된 입력은 4xx여야 한다). 상한이 없으면 `size=100000`도 통과해 한 번에 전체 테이블을 긁는다.
 * `NotificationSearchCondition` 등 이 프로젝트의 다른 모든 목록 API가 이미 이 방식이라 관례도
 * 여기에 맞춘다.
 */
@Schema(description = "공지 목록 검색 조건 — 페이지네이션")
data class NoticeSearchCondition(
    @field:Min(0)
    @field:Schema(description = "페이지 번호(0부터 시작)", defaultValue = "0")
    val page: Int = 0,
    @field:Min(1)
    @field:Max(100)
    @field:Schema(description = "페이지 크기(1~100)", defaultValue = "20")
    val size: Int = 20,
)
