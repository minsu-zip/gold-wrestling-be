package com.goldwrestling.notice

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.notice.dto.CreateNoticeRequest
import com.goldwrestling.notice.dto.NoticeDetailResponse
import com.goldwrestling.notice.dto.NoticeSearchCondition
import com.goldwrestling.notice.dto.NoticeSummaryResponse
import com.goldwrestling.notice.dto.UpdateNoticeRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 공지 관리 API(NOTICE-01). `SecurityConfig`에서 `/api/admin` 하위 전체가
 * `hasRole("ADMIN")` 전용이므로, **이 컨트롤러에는 별도 권한 애노테이션을 붙이지 않는다**(D-040) —
 * 역할 구분은 URL 인가 규칙이 담당하고, 여기서 또 표현하면 규칙이 두 곳으로 갈라진다.
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020), 에러 응답은
 * `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/admin/notices")
@Tag(name = "admin-notice", description = "관리자 공지 관리")
class AdminNoticeController(
    private val noticeService: NoticeService,
) {
    @GetMapping
    @Operation(summary = "공지 목록 조회 (최신순 페이지)")
    fun getList(
        @ParameterObject @ModelAttribute @Valid condition: NoticeSearchCondition,
    ): PageResponse<NoticeSummaryResponse> = noticeService.getList(condition)

    @GetMapping("/{noticeId}")
    @Operation(summary = "공지 상세 조회")
    fun getDetail(
        @PathVariable noticeId: Long,
    ): NoticeDetailResponse = noticeService.getDetail(noticeId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "공지 등록 (제목+본문만, 첨부·고정 없음, D-131)")
    fun create(
        @Valid @RequestBody request: CreateNoticeRequest,
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
    ): NoticeDetailResponse = noticeService.create(principal.requireAdminId(), request)

    @PatchMapping("/{noticeId}")
    @Operation(summary = "공지 수정")
    fun update(
        @PathVariable noticeId: Long,
        @Valid @RequestBody request: UpdateNoticeRequest,
    ): NoticeDetailResponse = noticeService.update(noticeId, request)

    @DeleteMapping("/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "공지 삭제 (물리 삭제, D-131)")
    fun delete(
        @PathVariable noticeId: Long,
    ) {
        noticeService.delete(noticeId)
    }
}
