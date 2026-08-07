package com.goldwrestling.reservation

import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberNotFoundException
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.notification.NotificationService
import com.goldwrestling.pass.InsufficientPassCountException
import com.goldwrestling.pass.PassNotFoundException
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassTransaction
import com.goldwrestling.pass.PassTransactionRepository
import com.goldwrestling.pass.TransactionReason
import com.goldwrestling.reservation.dto.ChangeReservationRequest
import com.goldwrestling.reservation.dto.MyReservationSearchCondition
import com.goldwrestling.reservation.dto.ReservationResponse
import com.goldwrestling.reservation.dto.ReserveRequest
import com.goldwrestling.schedule.ClassSchedule
import com.goldwrestling.schedule.ClassScheduleNotFoundException
import com.goldwrestling.schedule.ClassScheduleRepository
import com.goldwrestling.schedule.ClassSessionRepository
import com.goldwrestling.schedule.ClassSessionService
import com.goldwrestling.schedule.ReservationWindow
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * 회원 예약 생성·취소·변경(RESV-01~05) — 이 phase에서 가장 중요한 트랜잭션 조립.
 *
 * 예약 1건은 5개 테이블을 원자적으로 갱신한다 — `class_session`(정원 +1) · `pass`(잔여 −1) ·
 * `reservation`(INSERT) · `pass_transaction`(RESERVE) · `notification`(INSERT). 각 단계의 원자성은
 * 04-03의 조건부 UPDATE가 이미 보장하므로, 이 서비스의 일은 **순서와 실패 처리**를 정확히 짜는
 * 것이다 — 순서를 바꾸면 롤백 범위나 에러코드가 달라진다(각 단계는 메서드 내부에 번호 주석으로
 * 남겨 뒀다).
 *
 * **회원 상태 게이트(`MemberStateGate.requireActive`)는 이 서비스가 호출하지 않는다** — 컨트롤러가
 * 호출한다(D-040 관례). 이 서비스는 [memberId]만 받아 이미 활성 회원이라고 가정한다.
 *
 * **차감/복구 경로는 [reserve]·[cancel]·[change] 세 메서드가 각자 만들지 않고 [createReservationInternal]·
 * [performMemberCancellation] 두 private 헬퍼를 공유한다(D-090)** — 경로가 갈라지면 D-021
 * (모든 잔여 변경이 이력을 남긴다) 보장이 두 벌로 흩어진다. [change]는 "취소 + 재예약"을 하나의
 * `@Transactional` 안에서 이 두 헬퍼를 순서대로 호출해 구현한다 — 재예약이 실패하면 트랜잭션
 * 전체가 롤백되어 취소했던 기존 예약도 그대로 ACTIVE로 되돌아간다.
 */
@Service
@Transactional(readOnly = true)
class MemberReservationService(
    private val memberRepository: MemberRepository,
    private val classScheduleRepository: ClassScheduleRepository,
    private val classSessionRepository: ClassSessionRepository,
    private val classSessionService: ClassSessionService,
    private val reservationRepository: ReservationRepository,
    private val passRepository: PassRepository,
    private val passTransactionRepository: PassTransactionRepository,
    private val notificationService: NotificationService,
    private val clock: Clock,
) {
    /**
     * 예약을 생성한다. ③~⑪은 [createReservationInternal]이 담당한다([change]와 공유).
     */
    @Transactional
    fun reserve(
        memberId: Long,
        request: ReserveRequest,
    ): ReservationResponse {
        // ① 회원 조회 — branchId 확보. 회원 상태 게이트는 컨트롤러가 호출한다.
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        val memberBranchId = member.branch.id

        // ② 시간표 조회 — 없거나 타 지점 시간표거나 요일이 classDate와 다르면 거부한다(T-04-27·28).
        // "요일이 어긋난 조합"까지 같은 예외로 거부하는 이유: 존재하지 않는 (시간표, 날짜) 조합을
        // 클라이언트가 임의로 만들어 요청하는 것을 막기 위해서다.
        val schedule =
            classScheduleRepository.findById(request.classScheduleId).orElseThrow {
                ClassScheduleNotFoundException(request.classScheduleId)
            }
        if (schedule.branch.id != memberBranchId || schedule.dayOfWeek != request.classDate.dayOfWeek) {
            throw ClassScheduleNotFoundException(request.classScheduleId)
        }

        // ③~⑪ 예약 생성 실행부(change와 공유)
        val reservation = createReservationInternal(memberId, schedule, request.classDate)

        // ⑫ 관리자 알림 생성 — 같은 트랜잭션에 편승한다(NotificationService는 자체 경계를 열지 않는다).
        notificationService.createReservationCreated(reservation)

        return ReservationResponse.from(reservation, reservation.classSession.endTime)
    }

    /**
     * 예약을 취소한다(policies §3, D-090·D-091). 처리 순서는 메서드 내부 번호 주석 참조.
     *
     * **당일이 아닌 예약만 취소할 수 있다** — [Reservation.assertCancelableByMember]가 당일·과거
     * 날짜를 함께 거부한다. 복구 여부는 [ReservationRefundPolicy.shouldRestore]가 판정하며,
     * 차감했던 이용권이 등록 취소(`CANCELED`) 상태면 예약은 취소되지만 잔여는 복구되지 않는다
     * (D-091 Pitfall 2 — `PassRepository.adjustRemainingCount`의 반환 0을 사후 해석하지 않는다).
     */
    @Transactional
    fun cancel(
        memberId: Long,
        reservationId: Long,
    ): ReservationResponse {
        // ① 소유권 포함 조회 — findById가 아니라 findByIdAndMemberId를 쓴다(IDOR 방어, T-04-45).
        // 남의 예약 id면 null이 오고, 404(RESERVATION_NOT_FOUND)로만 응답한다 — 403은 "그 id가
        // 존재한다"는 사실 자체를 노출하므로 쓰지 않는다.
        val reservation =
            reservationRepository.findByIdAndMemberId(reservationId, memberId)
                ?: throw ReservationNotFoundException(reservationId)

        // ② 취소 가능 여부 판정 — 당일·과거·이미 취소된 예약을 여기서 거부한다.
        reservation.assertCancelableByMember(LocalDate.now(clock))

        // ③ 벌크 UPDATE(clearAutomatically) 전에 이후 필요한 스칼라 값·엔티티를 미리 꺼낸다.
        // member는 reservation.member(LAZY 프록시)를 그대로 쓰지 않고 memberRepository로 직접
        // 조회한다 — clearAutomatically 이후에도 안전하게 재사용하기 위해서다(AdminPassService.adjust의
        // admin 관례와 동일).
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        val sessionId = requireNotNull(reservation.classSession.id) { "저장된 예약은 항상 ClassSession을 참조합니다." }
        val sessionEndTime = reservation.classSession.endTime
        val passId = requireNotNull(reservation.pass.id) { "저장된 예약은 항상 Pass를 참조합니다." }
        val passStatus = reservation.pass.status
        val canceledAt = OffsetDateTime.now(clock)

        // ④~⑦ 취소 반영 공통 경로(change와 공유, D-090)
        performMemberCancellation(reservationId, sessionId, passId, passStatus, member, canceledAt)

        // ⑧ 관리자 알림 생성 — clearAutomatically로 준영속화된 예약을 findByIdAndMemberId로 다시
        // 조회한다(bare findById를 쓰지 않는다 — IDOR 방어 관례를 재조회 경로에도 유지한다).
        val refreshedReservation =
            reservationRepository.findByIdAndMemberId(reservationId, memberId)
                ?: throw IllegalStateException("방금 취소한 예약(id=$reservationId)을 찾을 수 없습니다.")
        notificationService.createReservationCanceled(refreshedReservation, byAdmin = false)

        return ReservationResponse.from(refreshedReservation, sessionEndTime)
    }

    /**
     * 예약을 변경한다 — "취소 + 재예약"을 하나의 `@Transactional`로 처리한다(D-090). 새 타임
     * 예약이 실패하면(정원 초과·잔여 부족·창 마감·종류 불일치·당일 등) **이 메서드 전체가
     * 롤백되어 기존 예약이 그대로 ACTIVE로 남는다** — ③에서 이미 반영한 취소·복구도 함께
     * 되돌아간다. 처리 순서는 메서드 내부 번호 주석 참조.
     */
    @Transactional
    fun change(
        memberId: Long,
        reservationId: Long,
        request: ChangeReservationRequest,
    ): ReservationResponse {
        // ① 기존 예약 조회(소유권 포함) + 새 시간표 조회 + 지점·요일 일치 검증(reserve ②와 동일).
        val reservation =
            reservationRepository.findByIdAndMemberId(reservationId, memberId)
                ?: throw ReservationNotFoundException(reservationId)
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        val memberBranchId = member.branch.id
        val newSchedule =
            classScheduleRepository.findById(request.classScheduleId).orElseThrow {
                ClassScheduleNotFoundException(request.classScheduleId)
            }
        if (newSchedule.branch.id != memberBranchId || newSchedule.dayOfWeek != request.classDate.dayOfWeek) {
            throw ClassScheduleNotFoundException(request.classScheduleId)
        }

        // ② 변경 가능 여부 판정 — assertCancelableByMember와 동일한 당일·과거 검사를 먼저 수행해
        // 당일 취소 금지를 "변경"으로 우회하지 못하게 막고(T-04-47), 그 다음 같은 수업 종류 안에서만
        // 변경을 허용한다.
        reservation.assertChangeableByMember(LocalDate.now(clock), newSchedule.classType)

        // ③ 기존 예약 취소 경로 실행(cancel의 ④~⑦과 동일 헬퍼 공유) — CANCEL_REFUND 이력이 남는다.
        // 벌크 UPDATE 전에 필요한 스칼라 값을 미리 꺼낸다.
        val oldSessionId = requireNotNull(reservation.classSession.id) { "저장된 예약은 항상 ClassSession을 참조합니다." }
        val oldPassId = requireNotNull(reservation.pass.id) { "저장된 예약은 항상 Pass를 참조합니다." }
        val oldPassStatus = reservation.pass.status
        val canceledAt = OffsetDateTime.now(clock)
        performMemberCancellation(reservationId, oldSessionId, oldPassId, oldPassStatus, member, canceledAt)

        // ④ 새 예약 생성 경로(reserve와 동일 헬퍼 공유) — RESERVE 이력이 남는다.
        val newReservation = createReservationInternal(memberId, newSchedule, request.classDate)

        // ⑤ 알림은 변경 1건만 남긴다(취소·예약 각각이 아니라, D-097).
        notificationService.createReservationChanged(newReservation, byAdmin = false)

        return ReservationResponse.from(newReservation, newReservation.classSession.endTime)
    }

    /**
     * 본인 예약 목록을 조회한다(RESV-05, D-090). **활성 예약만 반환하고 취소된 예약은 제외한다**
     * — [ReservationSpecifications.ownedByMember]·[ReservationSpecifications.hasStatus]`(ACTIVE)`
     * 조합으로만 구성한다. `ownedByMember`가 non-null 필수 조건이라 이 조합에서 본인 스코프를
     * 빼먹을 수 없다(T-04-46).
     *
     * 정렬은 `classDate` 오름차순, 같은 날이면 `startTime` 오름차순 — 회원이 달력을 보는 순서와
     * 같다.
     */
    fun findMyReservations(
        memberId: Long,
        condition: MyReservationSearchCondition,
    ): PageResponse<ReservationResponse> {
        val specification =
            Specification.allOf<Reservation>(
                listOf(
                    ReservationSpecifications.ownedByMember(memberId),
                    ReservationSpecifications.hasStatus(ReservationStatus.ACTIVE),
                ),
            )
        val pageable =
            PageRequest.of(
                condition.page,
                condition.size,
                Sort.by(Sort.Direction.ASC, "classDate", "startTime"),
            )
        val page = reservationRepository.findAll(specification, pageable)
        return PageResponse.from(page) { ReservationResponse.from(it, it.classSession.endTime) }
    }

    /**
     * 예약 생성 실행부 — [reserve]가 원래 갖고 있던 스텝 ③~⑪을 [reserve]·[change]가 공유하도록
     * 뽑아낸 것이다(D-090 "차감/복구 경로 단일화"). 호출 전에 회원 존재·시간표 조회+검증(지점·요일
     * 일치)은 호출부가 이미 마쳤다고 가정한다 — [change]가 이미 한 검증을 이 헬퍼가 다시 하면
     * 중복이고, `reserve`의 에러코드(`ClassScheduleNotFoundException`)와 `change`의 에러코드가
     * 갈릴 이유가 없다.
     *
     * ⑦·⑨의 조건부 UPDATE는 `clearAutomatically = true`라 그 시점 이후 이전에 로드한 [schedule]·
     * `session`·`candidate`는 준영속 상태가 된다 — ⑩·⑪에서 쓸 영속 엔티티는 마지막 조건부
     * UPDATE(⑨) 직후 다시 조회한다(`AdminPassService` 관례, RESEARCH Pitfall 4).
     */
    private fun createReservationInternal(
        memberId: Long,
        schedule: ClassSchedule,
        classDate: LocalDate,
    ): Reservation {
        val scheduleStartTime = schedule.startTime
        val scheduleClassType = schedule.classType

        // ③ 예약 창 검사 — 이번 주가 아니거나 이미 시작된 수업이면 거부한다(D-095).
        val now = LocalDateTime.now(clock)
        ReservationWindow.assertBookable(classDate, scheduleStartTime, now)

        // ④ 필요 이용권 종류 판정 — 저녁반이면 여기서 ClassSessionNotReservableException으로 거부한다.
        val passType = ReservationPassPolicy.requiredPassType(scheduleClassType)

        // ⑤ 세션 확보(get-or-create, D-094)·휴강 검사.
        val session = classSessionService.getOrCreate(schedule, classDate)
        session.assertReservable()
        val sessionId = requireNotNull(session.id) { "getOrCreate가 반환한 세션은 항상 저장돼 있어야 합니다." }
        val sessionClassType = session.classType
        val sessionStartTime = session.startTime

        // ⑥ 중복 예약 사전 검사 — 사용자에게 정확한 에러를 주기 위한 것일 뿐, 실제 방어는 부분
        // 유니크 인덱스(ux_reservation_member_timeslot_active, D-021)다. change()가 방금 취소한
        // 원래 예약은 이미 CANCELED로 반영돼 있어(같은 트랜잭션 내 flushAutomatically) 여기 걸리지 않는다.
        if (reservationRepository.existsByMemberIdAndClassDateAndStartTimeAndStatus(
                memberId,
                classDate,
                sessionStartTime,
                ReservationStatus.ACTIVE,
            )
        ) {
            throw DuplicateReservationException()
        }

        // ⑦ 정원 조건부 UPDATE — 반환 0이면 정원 초과 또는 그 사이 휴강 처리됨.
        if (classSessionRepository.incrementReservedCountIfCapacityAvailable(sessionId) == 0) {
            throw ReservationCapacityExceededException()
        }

        // ⑧ 차감 후보 선정(D-091, 만료 임박순·단일 이용권 기준) — 없으면 잔여 부족.
        val candidates =
            passRepository.findDeductionCandidates(
                memberId,
                passType,
                classDate,
                ReservationPassPolicy.DEDUCTION_AMOUNT,
            )
        val candidate = ReservationPassPolicy.selectCandidate(candidates)
        val passId = requireNotNull(candidate.id) { "차감 후보 조회는 항상 저장된 Pass만 반환합니다." }

        // ⑨ 이용권 차감(조건부 UPDATE) — 반환 0이면 경쟁에서 진 것이다(같은 회원이 자신의 이용권을
        // 동시에 두 곳에서 쓰는 경우뿐). 재시도 없이 즉시 실패시킨다 — 빈도가 극히 낮아 재시도
        // 로직의 복잡성을 들일 실익이 없다(CONTEXT.md Claude's Discretion 확정).
        if (passRepository.adjustRemainingCount(passId, ReservationPassPolicy.DEDUCTION_AMOUNT.negate()) == 0) {
            throw InsufficientPassCountException()
        }

        // ⑦·⑨의 clearAutomatically로 이전에 로드한 schedule·session·candidate는 전부 준영속
        // 상태다 — ⑩·⑪에 쓸 영속 엔티티를 재조회한다(AdminPassService 관례).
        val refreshedMember = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        val refreshedSession =
            classSessionRepository.findById(sessionId).orElseThrow {
                IllegalStateException("방금 정원을 갱신한 ClassSession(id=$sessionId)을 찾을 수 없습니다.")
            }
        val refreshedPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }

        val nowOffset = OffsetDateTime.now(clock)

        // ⑩ Reservation INSERT — 부분 유니크 인덱스 위반 시 DataIntegrityViolationException을
        // DuplicateReservationException으로 변환한다. INSERT 실패이므로 트랜잭션이 여기서 끝나도
        // 안전하다(⑦·⑨의 UPDATE는 같은 트랜잭션이라 함께 롤백된다).
        val reservation =
            try {
                reservationRepository.saveAndFlush(
                    Reservation(
                        member = refreshedMember,
                        classSession = refreshedSession,
                        pass = refreshedPass,
                        classType = sessionClassType,
                        classDate = classDate,
                        startTime = sessionStartTime,
                        status = ReservationStatus.ACTIVE,
                        reservedAt = nowOffset,
                        createdAt = nowOffset,
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                throw DuplicateReservationException()
            }

        // ⑪ PassTransaction INSERT — 행위자 기준(회원 셀프 예약이므로 member 주체, admin은 null).
        passTransactionRepository.save(
            PassTransaction(
                pass = refreshedPass,
                amount = ReservationPassPolicy.DEDUCTION_AMOUNT.negate(),
                reason = TransactionReason.RESERVE,
                note = null,
                admin = null,
                member = refreshedMember,
                occurredAt = nowOffset,
            ),
        )

        return reservation
    }

    /**
     * 취소 반영 공통 경로 — [cancel](④~⑦)·[change](③)가 공유한다(D-090 "차감/복구 경로 단일화").
     * 호출 전에 벌크 UPDATE(clearAutomatically)로 준영속화되기 전 스칼라 값과 [member](미리 직접
     * 조회한 영속 엔티티, `AdminPassService.adjust`의 `admin` 관례)를 이미 확보했다고 가정한다.
     */
    private fun performMemberCancellation(
        reservationId: Long,
        sessionId: Long,
        passId: Long,
        passStatus: PassStatus,
        member: Member,
        canceledAt: OffsetDateTime,
    ) {
        // ④ 회원 셀프 취소 조건부 UPDATE — 반환 0이면 경쟁에서 졌다(이미 취소됨).
        if (reservationRepository.cancelByMemberIfActive(reservationId, member, canceledAt) == 0) {
            throw ReservationAlreadyCanceledException()
        }

        // ⑤ 세션 정원 복구 — 반환 0은 이미 0이라는 뜻이라 무시하지 않고 드러낸다(활성 예약이
        // 있었다면 카운트가 0일 수 없다).
        if (classSessionRepository.decrementReservedCount(sessionId) == 0) {
            throw IllegalStateException("활성 예약이 있었던 세션(id=$sessionId)의 reservedCount가 이미 0입니다.")
        }

        // ⑥ 복구 여부 판정(D-091, Pitfall 2) — 판정 결과로 ⑦의 실행 여부를 가른다. 판정이 false면
        // adjustRemainingCount 자체를 호출하지 않는다(반환 0을 사후 해석하지 않는다).
        if (!ReservationRefundPolicy.shouldRestore(passStatus, refundRequested = true)) {
            return
        }

        // ⑦ 판정이 true일 때만 잔여 복구 + 이력 저장. 여기서 반환 0이 나온다면 판정 이후 다른
        // 트랜잭션이 이용권 상태를 바꾼 것(정상 운영에서는 D-089 선행 검사로 발생하지 않아야 한다)
        // 이므로 방어적으로 드러낸다.
        if (passRepository.adjustRemainingCount(passId, ReservationPassPolicy.DEDUCTION_AMOUNT) == 0) {
            throw IllegalStateException(
                "복구 대상 이용권(id=$passId)이 판정 이후 상태가 바뀌어 복구를 반영하지 못했습니다.",
            )
        }
        val refreshedPass = passRepository.findById(passId).orElseThrow { PassNotFoundException(passId) }
        passTransactionRepository.save(
            PassTransaction(
                pass = refreshedPass,
                amount = ReservationPassPolicy.DEDUCTION_AMOUNT,
                reason = TransactionReason.CANCEL_REFUND,
                note = null,
                admin = null,
                member = member,
                occurredAt = canceledAt,
            ),
        )
    }
}
