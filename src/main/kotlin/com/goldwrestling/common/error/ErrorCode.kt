package com.goldwrestling.common.error

import org.springframework.http.HttpStatus

/**
 * 이 프로젝트의 모든 에러 응답에 붙는 `code` 필드 값이다 (D-06).
 *
 * **FE 분기는 이 값(enum `name`)으로만 한다.** `ProblemDetail.type` URI는 형식만 갖춘 값이며 분기 키가 아니다.
 *
 * 여기 정의된 7개는 이번 phase에 실제로 발생 가능한 **공통** 코드만 담는다 — 스프링 내장 예외(404/405/400/406/415)와
 * 예상하지 못한 서버 오류(500)에 대응한다. 도메인 코드(예: `RESERVATION_FULL`, `INSUFFICIENT_PASS_COUNT`)는
 * 해당 도메인이 생기는 이후 phase에서 이 enum에 추가하고, 같은 PR에서 `docs/error-codes.md` 표도 함께 갱신한다.
 *
 * [defaultStatus]로 코드↔HTTP 상태 대응을 코드 안에 고정해, 문서(`docs/error-codes.md`)와 코드가 갈라지는 것을 막는다.
 */
enum class ErrorCode(
    val defaultStatus: HttpStatus,
) {
    /** 요청 값 형식 검증 실패 (`@Valid`, 파라미터 제약) */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    /** 본문 파싱 실패, 타입 불일치, 필수 파라미터 누락 */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    /** 매핑되지 않은 경로 또는 대상 리소스 없음 */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** 해당 경로가 지원하지 않는 HTTP 메서드 */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    /** 지원하지 않는 Content-Type */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    /** Accept 헤더가 요구하는 미디어 타입으로 응답을 만들 수 없음 */
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),

    /** 예상하지 못한 서버 오류 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
}
