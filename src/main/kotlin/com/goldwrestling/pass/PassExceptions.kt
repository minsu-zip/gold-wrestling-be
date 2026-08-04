package com.goldwrestling.pass

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

/**
 * 대상 이용권이 없을 때. **[passId]를 메시지에 보간하지 않는다** — 존재 여부를 응답 문구로 탐색할 수
 * 있게 되는 것을 막기 위해서다(conventions §8, `MemberNotFoundException`과 동일한 선례).
 */
@Suppress("UNUSED_PARAMETER")
class PassNotFoundException(
    passId: Long?,
) : DomainException(
        ErrorCode.PASS_NOT_FOUND,
        "이용권을 찾을 수 없습니다.",
    )

/**
 * 관리자 수동 가감(`ADMIN_ADJUST`) 요청 수량이 0.5 단위가 아니거나 0일 때(policies §4.2a).
 */
class InvalidAdjustmentUnitException :
    DomainException(
        ErrorCode.INVALID_ADJUSTMENT_UNIT,
        "가감 수량은 0.5 단위여야 하며 0이 될 수 없습니다.",
    )

/**
 * 횟수권·레슨권 등록에 초기 횟수가 누락됐을 때(D-055, 리뷰 WR-02).
 *
 * DTO의 `@Valid`로는 "타입이 횟수제일 때만 필수"라는 조건부 필수를 표현할 수 없어 도메인에서
 * 검증한다. 의미상 요청 형식 문제이므로 새 코드를 만들지 않고 [ErrorCode.VALIDATION_FAILED]를
 * 재사용한다 — 단위 위반([InvalidAdjustmentUnitException])과 원인이 다르므로 예외를 분리해
 * 사용자 대면 문구가 실제 원인("누락")을 말하게 한다.
 */
class MissingInitialCountException :
    DomainException(
        ErrorCode.VALIDATION_FAILED,
        "횟수권·레슨권은 초기 횟수를 지정해야 합니다.",
    )

/**
 * 가감 결과 잔여가 음수가 될 때(policies §4.2a "결과 잔여가 음수가 되는 가감은 거부").
 */
class InsufficientPassCountException :
    DomainException(
        ErrorCode.INSUFFICIENT_PASS_COUNT,
        "잔여 횟수가 부족합니다.",
    )

/**
 * 기간제(`EVENING_MEMBERSHIP`)에 횟수 가감을 시도했을 때(policies §4.2a "기간제는 횟수 가감 대상이 아니다").
 */
class PassTypeNotAdjustableException :
    DomainException(
        ErrorCode.PASS_TYPE_NOT_ADJUSTABLE,
        "이 이용권은 횟수 가감 대상이 아닙니다.",
    )

/**
 * 이미 취소된 이용권을 가감·기간수정·재취소하려 할 때(D-059).
 */
class PassAlreadyCanceledException :
    DomainException(
        ErrorCode.PASS_ALREADY_CANCELED,
        "이미 취소된 이용권입니다.",
    )

/**
 * 종료일이 시작일보다 앞서거나, 횟수권의 시작일을 수정하려 할 때(D-062 — 횟수권은 종료일만 수정 가능).
 * 호출부가 상황에 맞는 사용자 대면 문구를 [message]로 넘긴다.
 */
class InvalidPassPeriodException(
    message: String,
) : DomainException(ErrorCode.INVALID_PASS_PERIOD, message)

/**
 * 조건부 갱신 경쟁 패배 등 위 코드로 나뉘지 않는 이용권 상태 충돌. 호출부가 상황에 맞는
 * 사용자 대면 문구를 [message]로 넘긴다.
 */
class PassStateConflictException(
    message: String,
) : DomainException(ErrorCode.PASS_STATE_CONFLICT, message)
