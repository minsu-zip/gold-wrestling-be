package com.goldwrestling.notice.dto

import com.goldwrestling.notice.Notice
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

/**
 * 공지 목록 응답 요소(`GET /api/admin/notices`, `GET /api/members/notices`, NOTICE-01·02).
 * **`createdByAdmin`을 노출하지 않는다** — 회원 응답에 관리자 식별 정보가 섞이지 않게 하고, 관리자
 * 화면도 작성자 표시 요구가 없다(D-131 "제목+본문만").
 */
@Schema(description = "공지 목록 항목")
data class NoticeSummaryResponse(
    @field:Schema(description = "공지 id") val id: Long,
    @field:Schema(description = "공지 제목") val title: String,
    @field:Schema(description = "등록 시각") val createdAt: OffsetDateTime,
    @field:Schema(description = "수정 시각") val updatedAt: OffsetDateTime,
) {
    companion object {
        fun from(notice: Notice): NoticeSummaryResponse =
            NoticeSummaryResponse(
                id = requireNotNull(notice.id) { "저장된 Notice만 응답으로 변환할 수 있다." },
                title = notice.title,
                createdAt = notice.createdAt,
                updatedAt = notice.updatedAt,
            )
    }
}

/**
 * 공지 상세 응답(`GET /api/admin/notices/{noticeId}`, `GET /api/members/notices/{noticeId}`,
 * 등록·수정 응답도 동일). `createdByAdmin`을 노출하지 않는 이유는 [NoticeSummaryResponse]와 같다.
 */
@Schema(description = "공지 상세")
data class NoticeDetailResponse(
    @field:Schema(description = "공지 id") val id: Long,
    @field:Schema(description = "공지 제목") val title: String,
    @field:Schema(description = "공지 본문(원문 텍스트, FE가 이스케이프)") val content: String,
    @field:Schema(description = "등록 시각") val createdAt: OffsetDateTime,
    @field:Schema(description = "수정 시각") val updatedAt: OffsetDateTime,
) {
    companion object {
        fun from(notice: Notice): NoticeDetailResponse =
            NoticeDetailResponse(
                id = requireNotNull(notice.id) { "저장된 Notice만 응답으로 변환할 수 있다." },
                title = notice.title,
                content = notice.content,
                createdAt = notice.createdAt,
                updatedAt = notice.updatedAt,
            )
    }
}
