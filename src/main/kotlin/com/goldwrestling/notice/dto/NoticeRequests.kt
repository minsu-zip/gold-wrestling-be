package com.goldwrestling.notice.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 공지 등록 요청(`POST /api/admin/notices`, NOTICE-01). **형식만 검증한다**(conventions §6) —
 * 제목·본문 외에 강제할 도메인 규칙이 없다(D-131 "제목+본문만").
 */
@Schema(description = "공지 등록 요청")
data class CreateNoticeRequest(
    @field:NotBlank
    @field:Size(max = 200)
    @field:Schema(description = "공지 제목(200자 이내)", example = "8월 휴관 안내")
    val title: String,
    @field:NotBlank
    @field:Size(max = 10000)
    @field:Schema(
        description =
            "공지 본문(10000자 이내). 이스케이프되지 않은 원문 텍스트다 — " +
                "렌더링 시 이스케이프는 FE 책임(T-06-11)",
        example = "8월 15일은 휴관합니다.",
    )
    val content: String,
)

/**
 * 공지 수정 요청(`PATCH /api/admin/notices/{noticeId}`, NOTICE-01). [CreateNoticeRequest]와 형식
 * 검증 규칙이 동일하지만, "수정"이라는 별도 의도를 드러내기 위해 DTO를 나눈다(conventions §2 네이밍).
 */
@Schema(description = "공지 수정 요청")
data class UpdateNoticeRequest(
    @field:NotBlank
    @field:Size(max = 200)
    @field:Schema(description = "공지 제목(200자 이내)", example = "8월 휴관 안내(수정)")
    val title: String,
    @field:NotBlank
    @field:Size(max = 10000)
    @field:Schema(
        description =
            "공지 본문(10000자 이내). 이스케이프되지 않은 원문 텍스트다 — " +
                "렌더링 시 이스케이프는 FE 책임(T-06-11)",
        example = "8월 15일은 휴관하며, 16일부터 정상 운영합니다.",
    )
    val content: String,
)
