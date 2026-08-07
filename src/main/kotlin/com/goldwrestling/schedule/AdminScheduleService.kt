package com.goldwrestling.schedule

import com.goldwrestling.admin.AdminBranchNotAssignedException
import com.goldwrestling.admin.AdminBranchRepository
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.common.time.WeekRange
import com.goldwrestling.reservation.Reservation
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.dto.AdminBoardCellResponse
import com.goldwrestling.schedule.dto.AdminBoardDayResponse
import com.goldwrestling.schedule.dto.AdminWeeklyBoardResponse
import com.goldwrestling.schedule.dto.BoardReservationResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * 관리자 주간 스케줄 보드 조회(SCHED-03). 조회 전용이라 클래스 기본
 * `@Transactional(readOnly = true)`를 그대로 쓰고 별도 애노테이션을 붙이지 않는다(D-020).
 *
 * 회원용 [ScheduleService]와 "시간표 → 그리드 뼈대 → 세션 덮어쓰기" 조립 로직을
 * [ScheduleGridSkeleton]으로 공유한다 — 복제하면 시간표 정렬·`EVENING` 취급이 두 화면에서
 * 어긋난다. 이 서비스가 그 위에 얹는 두 가지 차이점: ① 셀별 **예약자 명단**을 포함한다(D-096:
 * 명단은 관리자만), ② **조회 주 범위 제한이 없다** — 관리자는 과거 출석 확인·미래 계획을 위해
 * 임의의 주를 조회해야 하므로, 회원용의 [ReservationWindow.assertViewable] 같은 거부 판정을
 * 호출하지 않고 대신 [weekStart]를 그 주의 월요일로 **정규화**만 한다.
 */
@Service
@Transactional(readOnly = true)
class AdminScheduleService(
    private val classScheduleRepository: ClassScheduleRepository,
    private val classSessionRepository: ClassSessionRepository,
    private val reservationRepository: ReservationRepository,
    private val adminBranchRepository: AdminBranchRepository,
    private val branchRepository: BranchRepository,
    private val clock: Clock,
) {
    /**
     * [branchId] 지점의 [weekStart] 주(월~일) 스케줄 보드를 조립한다. [weekStart]가 `null`이면
     * 오늘이 속한 주, 월요일이 아니면 그 날짜가 속한 주의 월요일로 **정규화**한다 — 회원용과 달리
     * 거부하지 않는다(관리자는 조회 범위 제한이 없다).
     *
     * 조회 쿼리는 정확히 3회다(셀 수에 비례하지 않는다, T-04-54) — [ClassScheduleRepository.findAllByBranchId]
     * (시간표 전량), [ClassSessionRepository.findAllByClassDateBetween](세션 배치 조회 1회),
     * [ReservationRepository.findAllByClassSessionIdInAndStatusWithMember](명단 배치 조회 1회,
     * `member`를 fetch join해 N+1을 없앤다). 셀 루프 안에서는 이 결과로 만든 맵만 조회한다.
     */
    fun getWeeklyBoard(
        branchId: Long,
        weekStart: LocalDate?,
    ): AdminWeeklyBoardResponse {
        val today = LocalDate.now(clock)
        val resolvedWeekStart = WeekRange.of(weekStart ?: today).monday
        val weekRange = WeekRange.of(resolvedWeekStart)

        val schedules = classScheduleRepository.findAllByBranchId(branchId)
        val sessions = classSessionRepository.findAllByClassDateBetween(weekRange.monday, weekRange.sunday)
        val sessionsByKey = sessions.associateBy { it.classSchedule.id to it.classDate }

        val sessionIds = sessions.mapNotNull { it.id }
        val reservationsBySessionId =
            if (sessionIds.isEmpty()) {
                emptyMap()
            } else {
                reservationRepository
                    .findAllByClassSessionIdInAndStatusWithMember(sessionIds, ReservationStatus.ACTIVE)
                    .groupBy { it.classSession.id }
            }

        val gridDays = ScheduleGridSkeleton.build(weekRange, schedules, sessionsByKey)
        val days =
            gridDays.map { gridDay ->
                AdminBoardDayResponse(
                    date = gridDay.date,
                    dayOfWeek = gridDay.date.dayOfWeek,
                    cells = gridDay.cells.map { cell -> toCell(cell.schedule, cell.session, reservationsBySessionId) },
                )
            }

        return AdminWeeklyBoardResponse(
            weekStart = weekRange.monday,
            weekEnd = weekRange.sunday,
            days = days,
        )
    }

    /**
     * 요청된 [branchId]를 실제 조회에 쓸 지점 id로 해석한다(T-04-53, `AdminScheduleController`가 호출).
     *
     * [branchId]가 주어지면 `admin_branch` 매핑으로 [adminId]가 그 지점 소속인지 검증하고, 아니면
     * [AdminBranchNotAssignedException]을 던진다. 생략되면 관리자의 첫 지점을 쓰되, **`admin_branch`
     * 매핑이 비어 있으면**(v1은 지점이 하나뿐이라 이 매핑 없이도 운영에 지장이 없었다 — `AdminSeeder`
     * KDoc 참고, 시드 관리자를 포함한 기존 관리자 전부가 이 상태다) 시스템에 지점이 정확히 하나일 때만
     * 그 지점으로 대체한다. 지점이 둘 이상인데 매핑이 없으면 어느 지점인지 결정할 수 없으므로
     * [IllegalStateException]을 던진다 — URL 인가는 이미 관리자 역할만 확인했으므로, 이 경로에
     * 도달했다는 것은 다지점으로 확장하면서 매핑 시딩을 빠뜨렸다는 배포 설정 오류 신호다.
     */
    fun resolveBranchId(
        adminId: Long,
        branchId: Long?,
    ): Long {
        if (branchId != null) {
            if (!adminBranchRepository.existsByAdminIdAndBranchId(adminId, branchId)) {
                throw AdminBranchNotAssignedException()
            }
            return branchId
        }
        val assignedBranchId = adminBranchRepository.findFirstByAdminId(adminId)?.branch?.id
        if (assignedBranchId != null) return assignedBranchId
        return branchRepository.findAll().singleOrNull()?.id
            ?: throw IllegalStateException(
                "관리자(id=$adminId)의 기본 지점을 결정할 수 없습니다 — admin_branch 매핑이 없고 지점이 여러 개입니다.",
            )
    }

    private fun toCell(
        schedule: ClassSchedule,
        session: ClassSession?,
        reservationsBySessionId: Map<Long?, List<Reservation>>,
    ): AdminBoardCellResponse {
        val suspended = session != null && session.status == ClassSessionStatus.CANCELED
        val capacity = session?.capacity ?: schedule.capacity
        val reservations = session?.id?.let { reservationsBySessionId[it] }.orEmpty()

        return AdminBoardCellResponse(
            classScheduleId = requireNotNull(schedule.id) { "저장되지 않은 ClassSchedule은 응답으로 변환할 수 없습니다." },
            classSessionId = session?.id,
            classType = schedule.classType,
            startTime = schedule.startTime,
            endTime = schedule.endTime,
            capacity = capacity,
            reservedCount = session?.reservedCount ?: 0,
            reservable = schedule.classType.reservable,
            suspended = suspended,
            cancelReason = session?.cancelReason,
            reservations =
                reservations.map { reservation ->
                    BoardReservationResponse(
                        reservationId = requireNotNull(reservation.id) { "저장되지 않은 Reservation은 응답으로 변환할 수 없습니다." },
                        memberId = requireNotNull(reservation.member.id) { "저장되지 않은 Member를 참조하는 Reservation은 응답으로 변환할 수 없습니다." },
                        memberName =
                            requireNotNull(reservation.member.name) {
                                "예약(id=${reservation.id})의 회원이 아직 온보딩을 완료하지 않았습니다 — " +
                                    "예약 가능한 회원은 항상 실명이 있어야 합니다."
                            },
                    )
                },
        )
    }
}
