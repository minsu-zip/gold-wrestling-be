package com.goldwrestling.schedule

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

/**
 * 요청한 정기 시간표(`ClassSchedule`) 행이 없을 때. **[classScheduleId]를 메시지에 보간하지 않는다** —
 * 존재 여부를 응답 문구로 탐색할 수 있게 되는 것을 막기 위해서다(conventions §8, `PassNotFoundException`과 동일한 선례).
 */
@Suppress("UNUSED_PARAMETER")
class ClassScheduleNotFoundException(
    classScheduleId: Long?,
) : DomainException(
        ErrorCode.CLASS_SCHEDULE_NOT_FOUND,
        "시간표를 찾을 수 없습니다.",
    )

/**
 * 휴강된 수업(`ClassSession`)에 예약·변경을 시도했을 때(policies §7).
 */
class ClassSessionCanceledException :
    DomainException(
        ErrorCode.CLASS_SESSION_CANCELED,
        "휴강된 수업입니다.",
    )

/**
 * 휴강 상태가 아닌 수업에 휴강 해제(`resume`)를 시도했을 때.
 */
class ClassSessionNotCanceledException :
    DomainException(
        ErrorCode.CLASS_SESSION_NOT_CANCELED,
        "휴강 상태가 아닌 수업입니다.",
    )

/**
 * 예약 대상이 아닌 수업 종류(`EVENING`)에 예약을 시도했을 때(D-093).
 */
class ClassSessionNotReservableException :
    DomainException(
        ErrorCode.CLASS_SESSION_NOT_RESERVABLE,
        "예약 대상이 아닌 수업입니다.",
    )

/**
 * 예약 창(오픈 전/마감 후) 밖의 요청일 때(D-095). 이 예외는 [ReservationWindow](04-04에서 구현 예정)가
 * `schedule` 패키지에 있어 여기 함께 둔다 — `schedule` → `reservation` 역참조를 만들지 않기 위해서다.
 * 의존 방향은 `reservation → schedule` 한 갈래만 유지한다 (D-018).
 */
class ReservationWindowClosedException(
    message: String,
) : DomainException(ErrorCode.RESERVATION_WINDOW_CLOSED, message)
