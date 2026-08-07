package com.goldwrestling.reservation

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

/**
 * 대상 예약이 없을 때(타인 예약 조회 포함). **[reservationId]를 메시지에 보간하지 않는다.**
 *
 * 소유자가 아닌 예약에 접근한 경우도 403(`ACCESS_DENIED`)이 아니라 이 코드(404)로 응답한다 —
 * 403은 "그 id의 예약이 존재한다"는 사실 자체를 노출하기 때문이다(T-04-02, `docs/error-codes.md` 참조).
 */
@Suppress("UNUSED_PARAMETER")
class ReservationNotFoundException(
    reservationId: Long?,
) : DomainException(
        ErrorCode.RESERVATION_NOT_FOUND,
        "예약을 찾을 수 없습니다.",
    )

/**
 * 정원이 초과되어 예약이 실패했을 때(RESV-06). 정원 조건부 UPDATE가 0행을 반환하면 이 예외로 변환한다.
 */
class ReservationCapacityExceededException :
    DomainException(
        ErrorCode.RESERVATION_CAPACITY_EXCEEDED,
        "정원이 초과되었습니다.",
    )

/**
 * 같은 회원이 같은 날짜·같은 시각에 이미 활성 예약을 가지고 있을 때(D-092).
 */
class DuplicateReservationException :
    DomainException(
        ErrorCode.DUPLICATE_RESERVATION,
        "같은 시간에 이미 예약이 있습니다.",
    )

/**
 * 당일에 취소 또는 변경을 시도했을 때(policies §3). 취소·변경 규칙이 동일해 예외 하나로 둔다 —
 * FE 분기가 나뉘지 않는다.
 */
class SameDayModificationNotAllowedException :
    DomainException(
        ErrorCode.SAME_DAY_MODIFICATION_NOT_ALLOWED,
        "당일에는 취소·변경할 수 없습니다.",
    )

/**
 * 이미 취소된 예약을 재취소하거나 변경하려 할 때.
 */
class ReservationAlreadyCanceledException :
    DomainException(
        ErrorCode.RESERVATION_ALREADY_CANCELED,
        "이미 취소된 예약입니다.",
    )

/**
 * 예약 변경 시 기존 예약과 새 타임의 수업 종류가 다를 때(`SESSION`↔`LESSON` 교차, D-090).
 * 종류가 바뀌면 차감하는 이용권 종류까지 바뀌어 사실상 다른 예약이 되기 때문에 변경을 허용하지 않는다.
 */
class ReservationTypeMismatchException :
    DomainException(
        ErrorCode.RESERVATION_TYPE_MISMATCH,
        "같은 종류의 수업으로만 변경할 수 있습니다.",
    )

/**
 * 조건부 갱신 경쟁 패배 등 위 코드로 나뉘지 않는 예약 상태 충돌. 호출부가 상황에 맞는
 * 사용자 대면 문구를 [message]로 넘긴다.
 */
class ReservationStateConflictException(
    message: String,
) : DomainException(ErrorCode.RESERVATION_STATE_CONFLICT, message)
