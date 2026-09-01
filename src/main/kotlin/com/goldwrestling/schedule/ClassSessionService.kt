package com.goldwrestling.schedule

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 날짜별 수업(`ClassSession`)의 "필요할 때 생성"(D-094) 진입점.
 *
 * **세션 실체화는 이 서비스에서만 한다.** 04-07(예약 생성)·04-14(휴강 처리)가 각자 `insertIfAbsent`를
 * 직접 부르지 않고 반드시 [getOrCreate]를 거친다 — 두 곳이 각자 upsert를 부르면 "이 서비스만이 세션을
 * 만든다"는 불변식이 깨지고, 나중에 실체화 로직(예: 공휴일 시각 오버라이드)을 바꿀 때 두 곳을 동시에
 * 고쳐야 하는 처지가 된다.
 */
@Service
@Transactional(readOnly = true)
class ClassSessionService(
    private val classSessionRepository: ClassSessionRepository,
) {
    /**
     * `(schedule, classDate)`의 `ClassSession`을 가져오거나, 없으면 만든다(D-094).
     *
     * **[schedule]의 요일과 [classDate]의 요일이 다르면 세션을 만들지 않고
     * [ClassScheduleNotFoundException]으로 거부한다(D-146, policies §2 "정기 시간표 외 임시 세션은
     * v1에서 허용하지 않는다").** 예약·대리변경·휴강 경로는 각자 호출 **전에** 같은 검사를 이미
     * 하고 있었지만(존재하지 않는 조합을 클라이언트가 임의로 만들어 요청하는 것을 막기 위해서다),
     * 출석 경로 두 곳(`AttendanceService.check`·`addEveningAttendance`)에는 그 검사가 없어 요일이
     * 어긋난 조합으로도 세션이 생성됐다(D-136 미해결 항목). 검사를 **세션 실체화의 단일
     * 초크포인트인 여기**로 내려 두면 앞으로 추가되는 쓰기 경로도 자동으로 같은 불변식을 받는다 —
     * 호출부마다 반복하는 방식은 새 호출부가 생길 때마다 빠뜨릴 수 있다.
     *
     * 예외를 `ClassScheduleNotFoundException`으로 통일하는 것은 예약 경로의 선례를 그대로 따른 것이다
     * (403으로 하면 타 지점·타 요일 시간표의 존재 여부가 응답으로 새어 나간다).
     *
     * [ClassSessionRepository.insertIfAbsent]의 **반환값을 보지 않는다** — 반환 0은 실패가 아니라
     * "이미 존재한다"는 상태 정보일 뿐이다(같은 파일 KDoc 참조). 그래서 upsert 호출 직후 항상
     * [ClassSessionRepository.findByClassScheduleIdAndClassDate]로 재조회한다 — 방금 내가 만들었든,
     * 동시에 다른 스레드가 먼저 만들었든 결과는 같다. 재조회가 `null`이면 upsert 직후인데도 행이 없다는
     * 뜻이라 있을 수 없는 상태이므로 [IllegalStateException]을 던진다.
     *
     * 반환된 세션의 [ClassSession.startTime]/[ClassSession.endTime]/[ClassSession.capacity]/
     * [ClassSession.classType]은 [schedule]에서 그대로 복사된 값이다(D-094 "시간표에서 복사해 자기
     * 컬럼으로 보유").
     */
    @Transactional
    fun getOrCreate(
        schedule: ClassSchedule,
        classDate: LocalDate,
    ): ClassSession {
        val scheduleId =
            requireNotNull(schedule.id) { "저장되지 않은 ClassSchedule로는 세션을 생성할 수 없습니다." }
        if (schedule.dayOfWeek != classDate.dayOfWeek) {
            throw ClassScheduleNotFoundException(scheduleId)
        }
        classSessionRepository.insertIfAbsent(
            scheduleId,
            classDate,
            schedule.classType.name,
            schedule.startTime,
            schedule.endTime,
            schedule.capacity,
        )
        return classSessionRepository.findByClassScheduleIdAndClassDate(scheduleId, classDate)
            ?: throw IllegalStateException(
                "insertIfAbsent 직후에도 class_session(schedule=$scheduleId, date=$classDate)을 찾을 수 없습니다 " +
                    "— 있을 수 없는 상태입니다.",
            )
    }

    /** 조회 경로 전용 — 세션을 생성하지 않는다. 회원 시간표 조회([ScheduleService])가 이 메서드를 쓴다. */
    fun findExisting(
        scheduleId: Long,
        classDate: LocalDate,
    ): ClassSession? = classSessionRepository.findByClassScheduleIdAndClassDate(scheduleId, classDate)
}
