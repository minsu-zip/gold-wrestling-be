package com.goldwrestling.common.error

import org.springframework.http.HttpStatus

/**
 * 이후 모든 phase의 도메인 예외(잔여 부족, 정원 초과, 당일 취소 불가 …)가 상속하는 기반 클래스다.
 *
 * [errorCode]는 응답의 `code` 필드로, [message]는 그대로 `ProblemDetail.detail`로 나간다 —
 * 즉 **[message]는 사용자 대면 문구**여야 한다. 내부 원인·SQL·식별자 같은 정보를 담지 않는다 (conventions.md §8).
 *
 * HTTP 상태는 기본적으로 [errorCode]가 갖고 있는 [ErrorCode.defaultStatus]를 따르되,
 * 같은 에러코드라도 상태를 다르게 줘야 하는 예외를 위해 [status]를 오버라이드할 수 있다.
 */
abstract class DomainException(
    val errorCode: ErrorCode,
    message: String,
    val status: HttpStatus = errorCode.defaultStatus,
) : RuntimeException(message)
