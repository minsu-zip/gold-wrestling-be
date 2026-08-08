package com.goldwrestling.schedule

import com.goldwrestling.common.time.WeekRange
import java.time.LocalDate

/**
 * "시간표(`ClassSchedule`) 기준으로 그리드 뼈대를 만들고 세션(`ClassSession`)이 있으면 덮어쓴다"는
 * 조립 로직 — 회원용 [ScheduleService]와 관리자용 `AdminScheduleService`(04-11)가 공유한다.
 * 반대로 세션 기준으로 조회하면 예약이 아직 없는 타임이 화면에서 통째로 사라진다(D-094 "필요할 때
 * 생성"이 요구하는 조회 방향). 두 화면이 각자 이 로직을 복제하면 시간표 정렬·`EVENING` 취급이
 * 어긋날 수 있어 `schedule` 패키지 내부(package-private) 헬퍼로 뽑아 공유한다.
 */
internal object ScheduleGridSkeleton {
    /** 하루치 그리드 뼈대 — 각 응답 DTO(`DayScheduleResponse`/`AdminBoardDayResponse`)로 변환하는 것은 호출부의 몫이다. */
    data class GridDay(
        val date: LocalDate,
        val cells: List<GridCell>,
    )

    /** 셀 하나 — [session]은 그 (시간표, 날짜) 조합이 아직 실체화되지 않았으면 `null`(D-094). */
    data class GridCell(
        val schedule: ClassSchedule,
        val session: ClassSession?,
    )

    /**
     * [weekRange]의 월~일 7일에 대해, [schedules] 중 그 요일에 해당하는 것만 시작 시각·수업종류
     * 순으로 정렬해 셀을 만들고 [sessionsByKey]에 세션이 있으면 함께 묶는다. 리포지토리를 호출하지
     * 않는다 — 호출부가 세션 배치 조회를 이미 마친 결과(맵)를 넘겨받는다.
     */
    fun build(
        weekRange: WeekRange,
        schedules: List<ClassSchedule>,
        sessionsByKey: Map<Pair<Long?, LocalDate>, ClassSession>,
    ): List<GridDay> =
        weekRange.dates().map { date ->
            val cells =
                schedules
                    .filter { it.dayOfWeek == date.dayOfWeek }
                    .sortedWith(compareBy({ it.startTime }, { it.classType.ordinal }))
                    .map { schedule -> GridCell(schedule, sessionsByKey[schedule.id to date]) }
            GridDay(date, cells)
        }
}
