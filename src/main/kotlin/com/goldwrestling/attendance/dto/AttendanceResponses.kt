package com.goldwrestling.attendance.dto

import com.goldwrestling.attendance.Attendance
import com.goldwrestling.attendance.AttendanceStatus
import com.goldwrestling.schedule.ClassType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 출석 단건 응답(`AttendanceService.check`/저녁반 추가, D-132). [deducted]는
 * `attendance.passTransaction != null`로 계산한다 — **`passTransaction`의 다른 필드(금액·사유)는
 * 응답에 노출하지 않는다.** 차감 상세는 이용권 이력 API(`GET /api/passes/{passId}/transactions`
 * 계열)가 담당한다.
 */
@Schema(description = "출석 단건 응답 — 차감 상세(금액·사유)는 노출하지 않는다(deducted 여부만)")
data class AttendanceResponse(
    @field:Schema(description = "출석 기록 ID") val id: Long,
    @field:Schema(description = "회원 ID") val memberId: Long,
    @field:Schema(description = "회원 실명") val memberName: String,
    @field:Schema(description = "날짜별 수업(ClassSession) ID") val classSessionId: Long,
    @field:Schema(description = "수업 날짜") val classDate: LocalDate,
    @field:Schema(description = "수업 시작 시각") val startTime: LocalTime,
    @field:Schema(description = "수업 종류") val classType: ClassType,
    @field:Schema(description = "출석 상태") val status: AttendanceStatus,
    @field:Schema(description = "차감 발생 여부 — 저녁반 0.5회 차감이 있었으면 true(D-128), 예약제/1:1은 항상 false") val deducted: Boolean,
    @field:Schema(description = "출석 확인 시각") val checkedAt: OffsetDateTime,
) {
    companion object {
        /**
         * **트랜잭션이 열려 있는 서비스 계층 안에서만 호출한다** — `member`·`classSession`이 `LAZY`
         * 연관이라 트랜잭션 밖에서 접근하면 `LazyInitializationException`이 난다(`PassResponse.from`과
         * 동일 관례).
         */
        fun from(attendance: Attendance): AttendanceResponse =
            AttendanceResponse(
                id = requireNotNull(attendance.id) { "저장되지 않은 Attendance는 응답으로 변환할 수 없습니다." },
                memberId = requireNotNull(attendance.member.id) { "저장되지 않은 Member를 참조하는 Attendance는 응답으로 변환할 수 없습니다." },
                memberName =
                    requireNotNull(attendance.member.name) {
                        "이름이 없는 회원(id=${attendance.member.id})은 출석 대상일 수 없습니다."
                    },
                classSessionId = requireNotNull(attendance.classSession.id) { "저장되지 않은 ClassSession을 참조하는 Attendance는 응답으로 변환할 수 없습니다." },
                classDate = attendance.classSession.classDate,
                startTime = attendance.classSession.startTime,
                classType = attendance.classSession.classType,
                status = attendance.status,
                deducted = attendance.passTransaction != null,
                checkedAt = attendance.checkedAt,
            )
    }
}

/**
 * 명단 항목(`ClassSessionAttendanceRosterResponse.entries`의 원소, D-132). [status]가 `null`이면
 * 아직 그 회원을 체크하지 않았다는 뜻이다(D-127 "레코드 부재 = 미체크"). [reservationId]·
 * [attendanceId]는 예약제/1:1 명단에서는 각각 채워지고, 저녁반 명단(예약이 없다)에서는
 * [reservationId]가 항상 `null`이다.
 */
@Schema(description = "출석 명단 항목 — status가 null이면 미체크(D-127)")
data class AttendanceRosterEntryResponse(
    @field:Schema(description = "회원 ID") val memberId: Long,
    @field:Schema(description = "회원 실명") val memberName: String,
    // 동명이인 식별용 보조 정보(BE-REQ-005, D-148). 회원 식별의 기준이 "이름 + 전화번호"이므로
    // (policies §5.1) 이름만으로는 명단에서 같은 이름의 두 회원을 구분할 수 없다 — 저녁반 출석
    // 삭제가 0.5회 복구를 유발하는 만큼(D-128) 잘못 고르면 엉뚱한 회원의 잔여가 바뀐다.
    //
    // nullable인 이유: `Member.phoneNumber`가 온보딩 전에는 null이다(policies §5.1). 정상 운영에서
    // 명단에 오르는 회원은 모두 온보딩을 마쳤지만, 값이 없다고 명단 조회 전체를 500으로 떨어뜨리는
    // 것은 과하다 — 이름(`memberName`)은 표시의 필수 요소라 여전히 non-null을 강제한다.
    @field:Schema(description = "회원 전화번호 — 동명이인 구분용. 온보딩 전 회원은 null") val phoneNumber: String?,
    @field:Schema(description = "예약 ID — 저녁반 명단에서는 항상 null(예약이 없는 수업)") val reservationId: Long?,
    @field:Schema(description = "출석 기록 ID — 미체크면 null") val attendanceId: Long?,
    @field:Schema(description = "출석 상태 — null이면 미체크(D-127 레코드 부재 = 미체크)") val status: AttendanceStatus?,
    @field:Schema(description = "차감 발생 여부 — 저녁반 0.5회 차감이 있었으면 true, 미체크·예약제/1:1은 항상 false") val deducted: Boolean,
)

/**
 * 특정 시간표·날짜의 출석 명단 응답(`AttendanceService.getRoster`, D-132) — 관리자가 타임별 참여자
 * 명단을 출석 상태와 함께 한 번에 조회한다(policies §6).
 *
 * [classSessionId]가 `null`이면 아직 그 날짜의 세션 행이 없다는 뜻이다(예약도 출석도 없음, D-094
 * "필요할 때 생성"). 이때 [classType]/[startTime]/[endTime]/[capacity]는 [ClassSchedule]에서 그대로
 * 복사한 표시 정보이고 [entries]는 빈 목록이다.
 */
@Schema(description = "시간표·날짜별 출석 명단 응답 — classSessionId가 null이면 세션이 아직 없다(예약·출석 없음)")
data class ClassSessionAttendanceRosterResponse(
    @field:Schema(description = "정기 시간표(ClassSchedule) ID") val classScheduleId: Long,
    @field:Schema(description = "수업 날짜") val classDate: LocalDate,
    @field:Schema(description = "날짜별 수업(ClassSession) ID — 세션이 아직 없으면 null") val classSessionId: Long?,
    @field:Schema(description = "수업 종류") val classType: ClassType,
    @field:Schema(description = "수업 시작 시각") val startTime: LocalTime,
    @field:Schema(description = "수업 종료 시각") val endTime: LocalTime,
    @field:Schema(description = "정원 — 저녁반(EVENING)은 null") val capacity: Int?,
    @field:Schema(description = "명단 — 예약제/1:1은 활성 예약자 전원, 저녁반은 출석 레코드가 있는 회원만") val entries: List<AttendanceRosterEntryResponse>,
)
