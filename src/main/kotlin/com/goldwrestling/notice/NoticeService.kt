package com.goldwrestling.notice

import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.notice.dto.CreateNoticeRequest
import com.goldwrestling.notice.dto.NoticeDetailResponse
import com.goldwrestling.notice.dto.NoticeSummaryResponse
import com.goldwrestling.notice.dto.UpdateNoticeRequest
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 공지 CRUD(NOTICE-01·02, D-131). 삭제는 물리 삭제(hard delete)이고 별도 soft delete 컬럼을 두지
 * 않는다. `title`/`content` 외에 강제할 도메인 규칙이 없어 `AdminPassService`류의 사전 판정·조건부
 * 갱신 패턴이 필요 없다 — 조회·CRUD가 전부다.
 */
@Service
@Transactional(readOnly = true)
class NoticeService(
    private val noticeRepository: NoticeRepository,
    private val adminRepository: AdminRepository,
    private val clock: Clock,
) {
    fun getList(
        page: Int,
        size: Int,
    ): PageResponse<NoticeSummaryResponse> {
        val result =
            noticeRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size))
        return PageResponse.from(result, NoticeSummaryResponse::from)
    }

    fun getDetail(noticeId: Long): NoticeDetailResponse {
        val notice = noticeRepository.findById(noticeId).orElseThrow { NoticeNotFoundException(noticeId) }
        return NoticeDetailResponse.from(notice)
    }

    @Transactional
    fun create(
        adminId: Long,
        request: CreateNoticeRequest,
    ): NoticeDetailResponse {
        val admin =
            adminRepository.findById(adminId).orElseThrow {
                IllegalStateException("공지를 등록하려는 관리자(id=$adminId)를 찾을 수 없습니다.")
            }
        val now = OffsetDateTime.now(clock)

        val notice =
            Notice(
                title = request.title,
                content = request.content,
                createdByAdmin = admin,
                createdAt = now,
                updatedAt = now,
            )
        noticeRepository.save(notice)

        return NoticeDetailResponse.from(notice)
    }

    @Transactional
    fun update(
        noticeId: Long,
        request: UpdateNoticeRequest,
    ): NoticeDetailResponse {
        val notice = noticeRepository.findById(noticeId).orElseThrow { NoticeNotFoundException(noticeId) }

        notice.title = request.title
        notice.content = request.content
        notice.updatedAt = OffsetDateTime.now(clock)

        return NoticeDetailResponse.from(notice)
    }

    @Transactional
    fun delete(noticeId: Long) {
        val notice = noticeRepository.findById(noticeId).orElseThrow { NoticeNotFoundException(noticeId) }
        noticeRepository.delete(notice)
    }
}
