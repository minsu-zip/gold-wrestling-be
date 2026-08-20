package com.goldwrestling.schedule

import com.goldwrestling.common.time.WeekRange
import com.goldwrestling.member.MemberNotFoundException
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.dto.DayScheduleResponse
import com.goldwrestling.schedule.dto.ScheduleCellResponse
import com.goldwrestling.schedule.dto.WeeklyScheduleResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 회원 주간 시간표 조회(SCHED-01·SCHED-02, 04-CONTEXT "회원 시간표 응답"). 조회 전용이라 클래스
 * 기본 `@Transactional(readOnly = true)`를 그대로 쓰고 별도 애노테이션을 붙이지 않는다(D-020).
 *
 * **시간표(`ClassSchedule`) 기준으로 그리드를 만들고, 세션(`ClassSession`)이 있으면 덮어쓴다** —
 * 반대로 세션 기준으로 조회하면 예약이 아직 없는 타임이 화면에서 통째로 사라진다(D-094 "필요할 때
 * 생성"이 요구하는 조회 방향).
 *
 * **조회 쿼리는 N+1을 만들지 않는다(T-04-21)** — [ClassSessionRepository.findAllByClassDateBetween]과
 * [ReservationRepository.findAllByClassSessionIdInAndStatusAndMemberId]를 각각 정확히 1회만 호출해
 * 맵으로 만들고, 셀 루프 안에서는 그 맵만 조회한다. 예약 조회는 **회원 조건까지 쿼리에 넣는다** —
 * 전체 명단을 가져와 애플리케이션에서 본인 것만 거르면, 정원이 찬 주에 타인 예약 수백 건이
 * 불필요하게 메모리로 올라온다.
 */
@Service
@Transactional(readOnly = true)
class ScheduleService(
    private val memberRepository: MemberRepository,
    private val classScheduleRepository: ClassScheduleRepository,
    private val classSessionRepository: ClassSessionRepository,
    private val reservationRepository: ReservationRepository,
    private val clock: Clock,
) {
    /**
     * [memberId] 본인의 [weekStart] 주(월~일) 시간표를 조회한다. [weekStart]가 `null`이면 이번 주로
     * 간주한다. [weekStart]가 월요일이 아니거나 조회 범위(이번 주+다음 주, D-095) 밖이면
     * [ReservationWindowClosedException]을 던진다(T-04-17·T-04-19).
     *
     * 회원의 지점은 principal이 아니라 [Member.branch]에서 얻는다 — `AuthenticatedPrincipal`은
     * `LazyInitializationException`을 피하려 스칼라 값만 들고 다니는 값 객체라 지점 정보가 없다
     * (`AuthenticatedPrincipal` KDoc 참조).
     */
    fun getWeeklySchedule(
        memberId: Long,
        weekStart: LocalDate?,
    ): WeeklyScheduleResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        val branchId = requireNotNull(member.branch.id) { "저장되지 않은 Branch를 참조하는 Member는 시간표를 조회할 수 없습니다." }

        val today = LocalDate.now(clock)
        val now = LocalDateTime.now(clock)
        val thisWeekStart = ReservationWindow.bookableWeek(today).monday
        val resolvedWeekStart = weekStart ?: thisWeekStart
        ReservationWindow.assertViewable(resolvedWeekStart, today)

        val weekRange = WeekRange.of(resolvedWeekStart)
        // 지역 변수명이 ReservationWindow.bookableWeek(함수, WeekRange 반환)와 겹치지 않게 한다 —
        // 이쪽은 "조회한 주가 예약 가능한 주인가"라는 Boolean이다.
        val isBookableWeek = resolvedWeekStart == thisWeekStart

        val schedules = classScheduleRepository.findAllByBranchId(branchId)
        val sessions = classSessionRepository.findAllByClassDateBetween(weekRange.monday, weekRange.sunday)
        val sessionsByKey = sessions.associateBy { it.classSchedule.id to it.classDate }

        val sessionIds = sessions.mapNotNull { it.id }
        val myReservationIdBySessionId =
            if (sessionIds.isEmpty()) {
                emptyMap()
            } else {
                reservationRepository
                    .findAllByClassSessionIdInAndStatusAndMemberId(sessionIds, ReservationStatus.ACTIVE, memberId)
                    .associate { it.classSession.id to it.id }
            }

        val gridDays = ScheduleGridSkeleton.build(weekRange, schedules, sessionsByKey)
        val days =
            gridDays.map { gridDay ->
                val cellsForDay =
                    gridDay.cells.map { cell -> toCell(cell.schedule, gridDay.date, cell.session, myReservationIdBySessionId, now) }
                DayScheduleResponse(date = gridDay.date, dayOfWeek = gridDay.date.dayOfWeek, cells = cellsForDay)
            }

        return WeeklyScheduleResponse(
            weekStart = weekRange.monday,
            weekEnd = weekRange.sunday,
            bookableWeek = isBookableWeek,
            days = days,
        )
    }

    private fun toCell(
        schedule: ClassSchedule,
        date: LocalDate,
        session: ClassSession?,
        myReservationIdBySessionId: Map<Long?, Long?>,
        now: LocalDateTime,
    ): ScheduleCellResponse {
        val suspended = session != null && session.status == ClassSessionStatus.CANCELED
        val capacity = session?.capacity ?: schedule.capacity
        val reservedCount = session?.reservedCount ?: 0
        val bookable =
            schedule.classType.reservable && !suspended &&
                ReservationWindow.isBookable(date, schedule.startTime, now)

        return ScheduleCellResponse(
            classScheduleId = requireNotNull(schedule.id) { "저장되지 않은 ClassSchedule은 응답으로 변환할 수 없습니다." },
            classSessionId = session?.id,
            classType = schedule.classType,
            startTime = schedule.startTime,
            endTime = schedule.endTime,
            capacity = capacity,
            reservedCount = reservedCount,
            reservable = schedule.classType.reservable,
            suspended = suspended,
            bookable = bookable,
            myReservationId = session?.id?.let { myReservationIdBySessionId[it] },
        )
    }
}
