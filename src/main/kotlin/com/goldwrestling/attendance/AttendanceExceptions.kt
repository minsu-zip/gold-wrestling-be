package com.goldwrestling.attendance

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

/**
 * 대상 출석 기록이 없을 때(삭제·수정 대상). **[attendanceId]를 메시지에 보간하지 않는다** —
 * 존재 여부를 응답 문구로 탐색할 수 있게 되는 것을 막기 위해서다(conventions §8, `PassNotFoundException`과 동일 선례).
 */
@Suppress("UNUSED_PARAMETER")
class AttendanceNotFoundException(
    attendanceId: Long?,
) : DomainException(
        ErrorCode.ATTENDANCE_NOT_FOUND,
        "출석 기록을 찾을 수 없습니다.",
    )

/**
 * 예약제/1:1 출석 체크 대상이 그 세션의 활성 예약자가 아닐 때(D-127 "예약자 명단에 대해 체크").
 */
class AttendanceMemberNotReservedException :
    DomainException(
        ErrorCode.ATTENDANCE_MEMBER_NOT_RESERVED,
        "해당 수업의 예약자가 아닙니다.",
    )

/**
 * 저녁반 전용 API를 SESSION/LESSON 세션에, 또는 예약자 체크 API를 EVENING 세션에 호출했을 때.
 */
class AttendanceClassTypeMismatchException :
    DomainException(
        ErrorCode.ATTENDANCE_CLASS_TYPE_MISMATCH,
        "이 수업 종류에는 사용할 수 없는 출석 처리입니다.",
    )

/**
 * 같은 회원·같은 세션에 출석 기록이 이미 존재할 때(회원×세션 유니크 위반, D-127).
 */
class DuplicateAttendanceException :
    DomainException(
        ErrorCode.DUPLICATE_ATTENDANCE,
        "이미 출석이 기록된 회원입니다.",
    )

/**
 * 유효한 저녁반 회비도 없고 `SESSION_PASS` 잔여도 0.5 미만이라 저녁반 출석 추가를 거부할 때
 * (policies §4.2, D-128).
 *
 * D-133: 이 실패는 예약 잔여 부족([com.goldwrestling.pass.InsufficientPassCountException])과
 * 사유가 다르다("잔여가 음수가 된다"가 아니라 "회비도 없고 잔여도 0.5 미만"인 복합 사유) — FE
 * 안내 문구도 완전히 달라 신규 코드 `EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`를 쓴다.
 */
class EveningAttendanceDeductionUnavailableException :
    DomainException(
        ErrorCode.EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE,
        "유효한 저녁반 회비가 없고 잔여 횟수도 0.5회 미만이라 저녁반 출석을 기록할 수 없습니다.",
    )
