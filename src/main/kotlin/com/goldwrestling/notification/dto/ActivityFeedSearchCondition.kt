package com.goldwrestling.notification.dto

import com.goldwrestling.notification.NotificationType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.OffsetDateTime

/**
 * 관리자 활동 피드 조회(`GET /api/admin/activity-feed`, NOTIF-03) 쿼리 파라미터 바인딩용.
 * `@ParameterObject @ModelAttribute @Valid`로 받는다(D-054, `ReservationSearchCondition`과 동일
 * 관례).
 *
 * **`from`이 `to`보다 뒤인 경우**: 예약 조회(`ReservationSearchCondition`)처럼 전용 400 에러코드
 * (`INVALID_RESERVATION_SEARCH_RANGE`)를 재사용하지 않는다 — 그 코드는 예약 조회 전용이라 다른
 * 도메인에 재사용하면 에러코드 의미가 흐려진다. 그렇다고 활동 피드만을 위한 새 에러코드를 추가할
 * 만큼 중요한 경우도 아니라고 판단했다(피드 조회는 파괴적 동작이 아니라 잘못된 범위를 넣어도 빈
 * 목록이 돌아올 뿐 데이터가 잘못되지 않는다) — 그래서 Specification의 AND 조합이 자연히 빈 결과를
 * 반환하도록 두고 별도 검증을 하지 않는다.
 */
@Schema(description = "관리자 활동 피드 검색 조건 — 기간·종류 필터 + 페이지네이션(읽음 여부 무관, D-129)")
data class ActivityFeedSearchCondition(
    @field:Schema(description = "이벤트 발생 시각 범위 시작(포함)") val from: OffsetDateTime? = null,
    @field:Schema(description = "이벤트 발생 시각 범위 끝(포함)") val to: OffsetDateTime? = null,
    @field:Schema(description = "알림 종류 필터") val type: NotificationType? = null,
    @field:Min(0)
    @field:Schema(description = "페이지 번호(0부터 시작)", defaultValue = "0")
    val page: Int = 0,
    @field:Min(1)
    @field:Max(100)
    @field:Schema(description = "페이지 크기(1~100)", defaultValue = "20")
    val size: Int = 20,
)
