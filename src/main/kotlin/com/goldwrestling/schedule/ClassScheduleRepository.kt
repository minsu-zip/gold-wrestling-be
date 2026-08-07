package com.goldwrestling.schedule

import org.springframework.data.jpa.repository.JpaRepository
import java.time.DayOfWeek

/**
 * `ClassSchedule`은 Flyway 시드로만 채워지므로(D-093) 이 저장소는 조회 메서드만 갖는다.
 */
interface ClassScheduleRepository : JpaRepository<ClassSchedule, Long> {
    /** 지점의 정기 시간표 전체 — 회원 시간표 조회(SCHED-01)·관리자 스케줄 보드의 기준 데이터. */
    fun findAllByBranchId(branchId: Long): List<ClassSchedule>

    /** 조회 범위(이번 주 + 다음 주, D-095)에 해당하는 요일만 좁혀 가져올 때 쓴다. */
    fun findAllByBranchIdAndDayOfWeekIn(
        branchId: Long,
        days: Collection<DayOfWeek>,
    ): List<ClassSchedule>
}
