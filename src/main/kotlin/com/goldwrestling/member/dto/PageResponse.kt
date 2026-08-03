package com.goldwrestling.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

/**
 * 페이지네이션 응답 계약. 컨트롤러에서 Spring Data의 `Page`를 **그대로 반환하지 않는다** — `Page`의
 * 직렬화 형태는 스프링 버전에 따라 바뀔 수 있고(예: `pageable`·`sort` 등 내부 구현 세부가 그대로
 * 노출된다) 필드가 과도하게 많아 FE 계약이 불안정해진다(D-019의 연장).
 *
 * 지금은 `member/dto`에 둔다 — 두 번째 기능 패키지가 실제로 이 응답 형태를 쓰게 되면
 * `common`으로 승격한다(conventions §1 "두 기능 이상이 실제로 쓰기 전에는 넣지 않는다").
 */
@Schema(description = "페이지네이션 응답")
data class PageResponse<T>(
    @field:Schema(description = "현재 페이지의 항목 목록") val content: List<T>,
    @field:Schema(description = "현재 페이지 번호(0부터 시작)") val page: Int,
    @field:Schema(description = "페이지 크기") val size: Int,
    @field:Schema(description = "전체 항목 수") val totalElements: Long,
    @field:Schema(description = "전체 페이지 수") val totalPages: Int,
) {
    companion object {
        fun <E : Any, T> from(
            page: Page<E>,
            mapper: (E) -> T,
        ): PageResponse<T> =
            PageResponse(
                content = page.content.map(mapper),
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
    }
}
