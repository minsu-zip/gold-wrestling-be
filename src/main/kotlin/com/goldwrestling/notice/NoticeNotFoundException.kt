package com.goldwrestling.notice

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

/**
 * 대상 공지가 없을 때. **[noticeId]를 메시지에 보간하지 않는다** — 존재 여부를 응답 문구로 탐색할 수
 * 있게 되는 것을 막기 위해서다(conventions §8, `PassNotFoundException`과 동일한 선례).
 */
@Suppress("UNUSED_PARAMETER")
class NoticeNotFoundException(
    noticeId: Long?,
) : DomainException(
        ErrorCode.NOTICE_NOT_FOUND,
        "공지를 찾을 수 없습니다.",
    )
