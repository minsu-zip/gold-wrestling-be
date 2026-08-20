package com.goldwrestling.reservation

import com.goldwrestling.member.MemberNotFoundException
import com.goldwrestling.member.MemberRepository
import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.notification.NotificationService
import com.goldwrestling.reservation.dto.ChangeReservationRequest
import com.goldwrestling.reservation.dto.MyReservationSearchCondition
import com.goldwrestling.reservation.dto.ReservationResponse
import com.goldwrestling.reservation.dto.ReserveRequest
import com.goldwrestling.schedule.ClassScheduleNotFoundException
import com.goldwrestling.schedule.ClassScheduleRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
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
 * **차감/복구 경로는 [reserve]·[cancel]·[change] 세 메서드가 각자 만들지 않고 [ReservationLedgerSupport]
 * (`createReservation`·`restoreAfterCancellation`)를 공유한다(D-090, 04-13에서 `AdminReservationService`와도
 * 공유하도록 확장)** — 경로가 갈라지면 D-021(모든 잔여 변경이 이력을 남긴다) 보장이 두 벌로 흩어진다.
 * [change]는 "취소 + 재예약"을 하나의 `@Transactional` 안에서 이 컴포넌트의 두 메서드를 순서대로
 * 호출해 구현한다 — 재예약이 실패하면 트랜잭션 전체가 롤백되어 취소했던 기존 예약도 그대로 ACTIVE로
 * 되돌아간다.
 */
@Service
@Transactional(readOnly = true)
class MemberReservationService(
    private val memberRepository: MemberRepository,
    private val classScheduleRepository: ClassScheduleRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationLedgerSupport: ReservationLedgerSupport,
    private val notificationService: NotificationService,
    private val clock: Clock,
) {
    /**
     * 예약을 생성한다. 실행부는 [ReservationLedgerSupport.createReservation]이 담당한다([change]와 공유).
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

        // ③~⑪ 예약 생성 실행부(change와 공유) — 회원 셀프 경로라 예약 창을 강제하고(enforceWindow=true)
        // 주체는 회원 본인이다(admin=null).
        val reservation =
            reservationLedgerSupport.createReservation(
                memberId = memberId,
                schedule = schedule,
                classDate = request.classDate,
                enforceWindow = true,
                admin = null,
            )

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

        // ④ 회원 셀프 취소 조건부 UPDATE — 반환 0이면 경쟁에서 졌다(이미 취소됨).
        if (reservationRepository.cancelByMemberIfActive(reservationId, member, canceledAt) == 0) {
            throw ReservationAlreadyCanceledException()
        }

        // ⑤~⑦ 취소 반영 공통 경로(change와 공유, D-090·04-13에서 AdminReservationService와도 공유)
        reservationLedgerSupport.restoreAfterCancellation(
            sessionId = sessionId,
            passId = passId,
            passStatus = passStatus,
            refundRequested = true,
            canceledAt = canceledAt,
            member = member,
            admin = null,
        )

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
        if (reservationRepository.cancelByMemberIfActive(reservationId, member, canceledAt) == 0) {
            throw ReservationAlreadyCanceledException()
        }
        reservationLedgerSupport.restoreAfterCancellation(
            sessionId = oldSessionId,
            passId = oldPassId,
            passStatus = oldPassStatus,
            refundRequested = true,
            canceledAt = canceledAt,
            member = member,
            admin = null,
        )

        // ④ 새 예약 생성 경로(reserve와 동일 헬퍼 공유) — RESERVE 이력이 남는다.
        val newReservation =
            reservationLedgerSupport.createReservation(
                memberId = memberId,
                schedule = newSchedule,
                classDate = request.classDate,
                enforceWindow = true,
                admin = null,
            )

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
}
