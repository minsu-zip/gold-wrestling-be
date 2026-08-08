package com.goldwrestling.schedule

import com.goldwrestling.admin.AdminBranchNotAssignedException
import com.goldwrestling.admin.AdminBranchRepository
import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.branch.BranchRepository
import com.goldwrestling.common.time.WeekRange
import com.goldwrestling.notification.NotificationService
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.reservation.Reservation
import com.goldwrestling.reservation.ReservationLedgerSupport
import com.goldwrestling.reservation.ReservationRepository
import com.goldwrestling.reservation.ReservationStatus
import com.goldwrestling.schedule.dto.AdminBoardCellResponse
import com.goldwrestling.schedule.dto.AdminBoardDayResponse
import com.goldwrestling.schedule.dto.AdminWeeklyBoardResponse
import com.goldwrestling.schedule.dto.BoardReservationResponse
import com.goldwrestling.schedule.dto.ClassSessionResponse
import com.goldwrestling.schedule.dto.SuspendClassSessionRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 관리자 주간 스케줄 보드 조회(SCHED-03) + 휴강 처리·해제(RESV-09, 04-14). 조회 전용 메서드는
 * 클래스 기본 `@Transactional(readOnly = true)`를 그대로 쓰고, 변경 메서드([suspend]·[resume])만
 * `@Transactional`을 오버라이드한다(D-020).
 *
 * 회원용 [ScheduleService]와 "시간표 → 그리드 뼈대 → 세션 덮어쓰기" 조립 로직을
 * [ScheduleGridSkeleton]으로 공유한다 — 복제하면 시간표 정렬·`EVENING` 취급이 두 화면에서
 * 어긋난다. 이 서비스가 그 위에 얹는 두 가지 차이점: ① 셀별 **예약자 명단**을 포함한다(D-096:
 * 명단은 관리자만), ② **조회 주 범위 제한이 없다** — 관리자는 과거 출석 확인·미래 계획을 위해
 * 임의의 주를 조회해야 하므로, 회원용의 [ReservationWindow.assertViewable] 같은 거부 판정을
 * 호출하지 않고 대신 [weekStart]를 그 주의 월요일로 **정규화**만 한다.
 *
 * 휴강 처리([suspend])는 이 phase에서 유일하게 **한 요청이 N건의 예약을 건드리는** 경로다 —
 * 차감 복구·이력 저장은 [ReservationLedgerSupport.restorePassAfterCancellation]을
 * [TransactionReason.CLASS_CANCELED_REFUND]와 함께 재사용해 회원/관리자 취소(`CANCEL_REFUND`)와
 * 원장에서 구분한다(T-04-67). 알림은 세션당 1건 요약형이다(D-097, T-04-69).
 */
@Service
@Transactional(readOnly = true)
class AdminScheduleService(
    private val classScheduleRepository: ClassScheduleRepository,
    private val classSessionRepository: ClassSessionRepository,
    private val classSessionService: ClassSessionService,
    private val reservationRepository: ReservationRepository,
    private val reservationLedgerSupport: ReservationLedgerSupport,
    private val notificationService: NotificationService,
    private val adminBranchRepository: AdminBranchRepository,
    private val adminRepository: AdminRepository,
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

    /**
     * 휴강 처리(RESV-09, policies §7) — 활성 예약 일괄 취소 + 차감 복구(`CLASS_CANCELED_REFUND`) +
     * 세션당 1건 요약 알림(D-097). 처리 순서:
     * ① 시간표 조회 + 요일 일치 검증 → ② 세션 확보(get-or-create, D-094 "세션 없는 타임도 휴강
     * 가능") → ③ 세션을 **먼저** `CANCELED`로 전환한다(T-04-66, 경쟁 중인 새 예약을
     * `incrementReservedCountIfCapacityAvailable`의 `status = SCHEDULED` 조건이 거부하게 하기
     * 위해서다) → ④ 활성 예약을 배치로 조회한다(건별 조회 금지 — N+1) → ⑤ 예약별로 취소 +
     * 복구 판정(D-091) → ⑥ `reserved_count`를 일괄 초기화한다 → ⑦ 알림을 정확히 1회 생성한다.
     *
     * **③~⑤에서 반복되는 벌크 UPDATE(`clearAutomatically`) 때문에, ④에서 필요한 스칼라 값
     * (reservationId·passId·passStatus)을 [ReservationCancellationSnapshot]으로 미리 리스트에
     * 뽑아둔다** — 그렇지 않으면 루프 도중 이전 반복의 벌크 UPDATE가 준영속화한 엔티티의 LAZY
     * 연관에 접근해 `LazyInitializationException`이 난다(04-RESEARCH.md Pitfall 2 확장).
     *
     * ⑥은 건별 [ClassSessionRepository.decrementReservedCount]를 N번 부르는 대신
     * [ClassSessionRepository.resetReservedCount] 단일 호출로 반영한다 — 건별 호출은 N번의 UPDATE와
     * N번의 영속성 컨텍스트 clear를 유발한다. 그래서 [ReservationLedgerSupport.restoreAfterCancellation]
     * (세션 정원까지 함께 반영)이 아니라 [ReservationLedgerSupport.restorePassAfterCancellation]
     * (잔여 판정 + 이력 저장만)을 예약마다 호출한다.
     */
    @Transactional
    fun suspend(
        adminId: Long,
        request: SuspendClassSessionRequest,
    ): ClassSessionResponse {
        val schedule =
            classScheduleRepository.findById(request.classScheduleId).orElseThrow {
                ClassScheduleNotFoundException(request.classScheduleId)
            }
        if (schedule.dayOfWeek != request.classDate.dayOfWeek) {
            throw ClassScheduleNotFoundException(request.classScheduleId)
        }
        val admin =
            adminRepository.findById(adminId).orElseThrow {
                IllegalStateException("휴강 처리를 하려는 관리자(id=$adminId)를 찾을 수 없습니다.")
            }

        // ② 세션 확보 — 아직 예약이 없어 세션 행이 없는 타임도 휴강할 수 있어야 한다(D-094).
        val session = classSessionService.getOrCreate(schedule, request.classDate)
        session.assertSuspendable()
        val sessionId = requireNotNull(session.id) { "getOrCreate가 반환한 세션은 항상 저장돼 있어야 합니다." }

        // ③ 세션을 먼저 CANCELED로 전환한다(T-04-66) — 반환 0이면 경쟁 패배(이미 휴강 처리됨).
        val now = OffsetDateTime.now(clock)
        if (classSessionRepository.suspendIfScheduled(sessionId, request.reason, admin, now) == 0) {
            throw ClassSessionCanceledException()
        }

        // ④ 활성 예약을 배치로 조회하고, 반복 중 안전하게 쓸 스칼라 값을 미리 뽑아둔다.
        val activeReservations =
            reservationRepository.findAllByClassSessionIdAndStatusWithPass(sessionId, ReservationStatus.ACTIVE)
        val snapshots =
            activeReservations.map { reservation ->
                ReservationCancellationSnapshot(
                    reservationId = requireNotNull(reservation.id) { "저장되지 않은 예약이 조회될 수 없습니다." },
                    passId = requireNotNull(reservation.pass.id) { "저장된 예약은 항상 Pass를 참조합니다." },
                    passStatus = reservation.pass.status,
                )
            }

        // ⑤ 예약별 취소 + 복구 판정 — CANCEL_REFUND가 아니라 CLASS_CANCELED_REFUND를 이력에 남긴다.
        snapshots.forEach { snapshot ->
            if (reservationRepository.cancelByAdminIfActive(snapshot.reservationId, admin, true, now) == 0) {
                // ④ 조회와 이 취소 사이에 회원이 스스로 취소했다면(드문 경쟁) 이미 CANCELED다 —
                // 이중 복구를 막기 위해 이 건은 건너뛴다.
                return@forEach
            }
            reservationLedgerSupport.restorePassAfterCancellation(
                passId = snapshot.passId,
                passStatus = snapshot.passStatus,
                refundRequested = true,
                canceledAt = now,
                member = null,
                admin = admin,
                reason = TransactionReason.CLASS_CANCELED_REFUND,
            )
        }

        // ⑥ reserved_count 일괄 초기화 — 건별 decrementReservedCount N번 대신 단일 호출.
        if (classSessionRepository.resetReservedCount(sessionId) == 0) {
            throw IllegalStateException("휴강 처리 중 세션(id=$sessionId)의 reserved_count를 초기화하지 못했습니다.")
        }

        val refreshedSession =
            classSessionRepository.findById(sessionId).orElseThrow {
                IllegalStateException("방금 휴강 처리한 세션(id=$sessionId)을 찾을 수 없습니다.")
            }

        // ⑦ 알림은 정확히 1회 — 반복문 밖에서 부른다(D-097 요약형).
        notificationService.createClassSessionSuspended(refreshedSession, snapshots.size)

        return ClassSessionResponse.from(refreshedSession, canceledReservationCount = snapshots.size)
    }

    /**
     * 휴강 해제 — [ClassSession.assertResumable] 판정 후 [ClassSessionRepository.resumeIfCanceled]로
     * 반영한다.
     *
     * **예약 복원 로직을 만들지 않는다.** 휴강으로 취소됐던 예약을 자동으로 되돌리면, 그 사이 잔여를
     * 다른 데 써버린 회원의 잔여가 음수가 될 수 있다 — 이는 Core Value("잔여 = 실제 사용 가능
     * 횟수")를 정면으로 깬다(CONTEXT.md "휴강 (RESV-09)" 락인, T-04-70). 회원이 직접 다시 예약한다.
     *
     * `reserved_count`는 0 그대로 둔다 — 취소된 예약을 복원하지 않으므로 카운트도 되돌리지 않는다
     * ([ClassSessionRepository.resumeIfCanceled]는 취소 메타데이터 3개만 되돌리고 카운트는 건드리지
     * 않는다).
     *
     * 휴강 해제 알림은 만들지 않는다 — `NotificationType`에 해당 값이 없다(D-097 이벤트 6종 미포함).
     */
    @Transactional
    fun resume(
        adminId: Long,
        classSessionId: Long,
    ): ClassSessionResponse {
        val session =
            classSessionRepository.findById(classSessionId).orElseThrow {
                ClassSessionNotFoundException(classSessionId)
            }
        session.assertResumable()
        if (classSessionRepository.resumeIfCanceled(classSessionId) == 0) {
            throw ClassSessionNotCanceledException()
        }
        val refreshedSession =
            classSessionRepository.findById(classSessionId).orElseThrow {
                IllegalStateException("방금 휴강 해제한 세션(id=$classSessionId)을 찾을 수 없습니다.")
            }
        return ClassSessionResponse.from(refreshedSession)
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

/**
 * 휴강 캐스케이드([AdminScheduleService.suspend])가 활성 예약 목록을 조회한 직후에만 만드는
 * 스칼라 스냅샷 — 반복 중 벌크 UPDATE(`clearAutomatically`)로 준영속화되는 [Reservation]·[Pass]
 * 엔티티의 LAZY 연관을 다시 건드리지 않기 위해서다.
 */
private data class ReservationCancellationSnapshot(
    val reservationId: Long,
    val passId: Long,
    val passStatus: PassStatus,
)
