package com.goldwrestling.common.error

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * `AuthenticationEntryPoint`/`AccessDeniedHandler`가 401·403 응답을 `GlobalExceptionHandler`와
 * 동일한 모양(RFC 9457 `ProblemDetail` + `code`)으로 조립하는 지점을 한 곳에 모은다. 양쪽이 각자
 * 응답을 만들면 형식이 갈라지기 쉽다.
 *
 * **Jackson 3 주의(D-014, conventions.md §11):** Boot 4는 Jackson 3을 쓴다 — `ObjectMapper`는
 * Jackson 2의 `databind` 패키지가 아니라 `tools.jackson.databind` 패키지다. Boot 3 예제를 그대로
 * 옮기면 이 임포트에서 컴파일이 깨진다.
 */
@Component
class ProblemDetailResponseWriter(
    private val objectMapper: ObjectMapper,
) {
    fun write(
        response: HttpServletResponse,
        status: HttpStatus,
        errorCode: ErrorCode,
        detail: String,
    ) {
        // 이미 다른 곳에서 응답이 커밋됐으면(예: 스트리밍 응답 중 실패) 본문을 다시 쓰지 않는다.
        if (response.isCommitted) {
            return
        }

        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.setProperty("code", errorCode.name)

        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = "UTF-8"
        objectMapper.writeValue(response.writer, problem)
    }
}
