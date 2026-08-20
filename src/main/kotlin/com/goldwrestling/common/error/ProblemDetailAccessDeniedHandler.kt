package com.goldwrestling.common.error

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

/**
 * 인증은 됐지만 역할(`ROLE_MEMBER`/`ROLE_ADMIN`)이 부족한 요청에 403 + `application/problem+json` +
 * `code=ACCESS_DENIED`로 응답한다. [ProblemDetailAuthenticationEntryPoint]와 같은 이유로
 * [accessDeniedException]의 메시지는 응답에 노출하지 않는다.
 */
@Component
class ProblemDetailAccessDeniedHandler(
    private val writer: ProblemDetailResponseWriter,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        writer.write(response, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다.")
    }
}
