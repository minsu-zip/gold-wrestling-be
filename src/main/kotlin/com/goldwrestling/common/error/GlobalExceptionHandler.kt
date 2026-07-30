package com.goldwrestling.common.error

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 이 프로젝트의 **모든** 에러 응답을 RFC 9457 `ProblemDetail`(`application/problem+json`)로 통일하는 단일 진입점.
 *
 * `ResponseEntityExceptionHandler`를 상속하면 Boot가 자동 등록하는 `ProblemDetailsExceptionHandler`
 * (`@ConditionalOnMissingBean(ResponseEntityExceptionHandler::class)`)가 비활성화되고, 스프링 내장 예외
 * (검증 실패·404·405·415 등)까지 전부 이 클래스의 [handleExceptionInternal]을 거친다 — 그래서 도메인 예외와
 * 내장 예외가 예외 없이 같은 모양(+`code`)으로 응답한다 (D-06).
 *
 * 개별 `@ExceptionHandler`를 나열해 자동 빈과 공존시키면 응답에 `code`가 있는 것과 없는 것이 섞인다
 * (RESEARCH Pitfall 1) — 그래서 상속 하나로 통일한다.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    /**
     * [DomainException]은 `ResponseEntityExceptionHandler`가 기본으로 처리하는 예외 목록에 없으므로
     * 별도 핸들러를 둔다. 여기서도 [handleExceptionInternal]로 넘겨 code 주입 지점을 하나로 유지한다.
     */
    @ExceptionHandler(DomainException::class)
    fun handleDomainException(
        ex: DomainException,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val problem = ProblemDetail.forStatusAndDetail(ex.status, ex.message ?: "")
        return handleExceptionInternal(ex, problem, HttpHeaders(), ex.status, request) ?: ResponseEntity.of(problem).build()
    }

    /**
     * 예상하지 못한 모든 예외의 최종 방어선. 응답 `detail`은 고정 문구만 채우고 원인 예외 정보
     * (메시지·클래스명·스택트레이스)는 절대 응답 본문에 넣지 않는다 — 원인은 서버 로그에만 남긴다
     * (T-01-01, conventions.md §8).
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        ex: Exception,
        request: WebRequest,
    ): ResponseEntity<Any> {
        logger.error("예상하지 못한 예외가 발생했습니다.", ex)
        val problem = ProblemDetail.forStatusAndDetail(ErrorCode.INTERNAL_ERROR.defaultStatus, "서버 오류가 발생했습니다.")
        return handleExceptionInternal(ex, problem, HttpHeaders(), ErrorCode.INTERNAL_ERROR.defaultStatus, request)
            ?: ResponseEntity.of(problem).build()
    }

    /**
     * 모든 경로(스프링 내장 예외 + [handleDomainException] + [handleUnexpectedException])가
     * 마지막으로 지나는 한 지점. 여기서 `code` 프로퍼티를 단 한 번만 주입한다.
     */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem =
            (body as? ProblemDetail)
                ?: ProblemDetail.forStatusAndDetail(statusCode, "예상하지 못한 오류가 발생했습니다.")
        problem.setProperty("code", resolveErrorCode(ex, statusCode).name)
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request)
    }

    private fun resolveErrorCode(
        ex: Exception,
        statusCode: HttpStatusCode,
    ): ErrorCode =
        when (ex) {
            is DomainException -> {
                ex.errorCode
            }

            is MethodArgumentNotValidException, is HandlerMethodValidationException -> {
                ErrorCode.VALIDATION_FAILED
            }

            is HttpMessageNotReadableException,
            is MethodArgumentTypeMismatchException,
            is MissingServletRequestParameterException,
            is ServletRequestBindingException,
            -> {
                ErrorCode.MALFORMED_REQUEST
            }

            is NoResourceFoundException, is NoHandlerFoundException -> {
                ErrorCode.RESOURCE_NOT_FOUND
            }

            is HttpRequestMethodNotSupportedException -> {
                ErrorCode.METHOD_NOT_ALLOWED
            }

            is HttpMediaTypeNotSupportedException -> {
                ErrorCode.UNSUPPORTED_MEDIA_TYPE
            }

            is HttpMediaTypeNotAcceptableException -> {
                ErrorCode.NOT_ACCEPTABLE
            }

            else -> {
                // 매핑되지 않은 예외에서 상태값으로 코드를 "추측"하지 않는다 — enum 선언 순서에 따라
                // 결과가 조용히 달라지고, 매칭 실패 시 4xx 상태에 INTERNAL_ERROR(계약상 500)가 실려
                // code↔상태가 갈라진다. 4xx는 요청 문제이므로 MALFORMED_REQUEST 로 고정하고,
                // 나머지는 INTERNAL_ERROR 다. 새 예외를 특정 코드로 구분해야 하면 위에 명시 매핑을 추가한다.
                if (statusCode.is4xxClientError) {
                    ErrorCode.MALFORMED_REQUEST
                } else {
                    ErrorCode.INTERNAL_ERROR
                }
            }
        }
}
