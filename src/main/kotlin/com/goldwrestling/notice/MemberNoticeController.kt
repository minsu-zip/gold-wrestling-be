package com.goldwrestling.notice

import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.notice.dto.NoticeDetailResponse
import com.goldwrestling.notice.dto.NoticeSummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 회원 공지 열람 API(NOTICE-02). 경로가 `/api/members/me`가 아니라 `/api/members/notices`인
 * 이유는 공지가 **본인 소유 리소스가 아니기 때문**이다 — `MemberPassController`(본인 이용권)와
 * 달리 공지는 전 회원 공용 데이터라 principal로 범위를 좁힐 필요가 없다.
 *
 * **`@AuthenticationPrincipal`을 받지 않고 회원 상태 게이트(`ACTIVE` 강제)도 거치지 않는다**(D-134) —
 * `SecurityConfig`의 `/api/members` 하위 전체 = `hasRole("MEMBER")` 규칙만으로 인증된 회원임은
 * 이미 보장되고, 공지는 회원 소유 데이터가 아니라 휴회(`ON_LEAVE`) 회원의 열람을 막을 이유가 없다
 * (D-071 "상태 게이트가 필요 없는 공용 조회" 연장).
 *
 * 트랜잭션 애노테이션·`try-catch`를 붙이지 않는다 — 트랜잭션 경계는 서비스(D-020), 에러 응답은
 * `GlobalExceptionHandler`가 담당한다(D-017).
 */
@RestController
@RequestMapping("/api/members/notices")
@Tag(name = "member-notice", description = "회원 공지 열람")
class MemberNoticeController(
    private val noticeService: NoticeService,
) {
    @GetMapping
    @Operation(summary = "공지 목록 조회 (최신순 페이지)")
    fun getList(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<NoticeSummaryResponse> = noticeService.getList(page, size)

    @GetMapping("/{noticeId}")
    @Operation(summary = "공지 상세 조회")
    fun getDetail(
        @PathVariable noticeId: Long,
    ): NoticeDetailResponse = noticeService.getDetail(noticeId)
}
