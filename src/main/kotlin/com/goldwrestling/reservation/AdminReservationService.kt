package com.goldwrestling.reservation

import com.goldwrestling.admin.AdminRepository
import com.goldwrestling.member.dto.PageResponse
import com.goldwrestling.notification.NotificationService
import com.goldwrestling.reservation.dto.AdminCancelReservationRequest
import com.goldwrestling.reservation.dto.AdminChangeReservationRequest
import com.goldwrestling.reservation.dto.AdminReservationResponse
import com.goldwrestling.reservation.dto.ReservationSearchCondition
import com.goldwrestling.schedule.ClassScheduleNotFoundException
import com.goldwrestling.schedule.ClassScheduleRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 관리자 전체 예약 조회(RESV-07) + 대리 취소·변경(RESV-08, 04-13). 04-10의
 * [ReservationSpecifications]를 재사용해 `AdminMemberService.search`(회원 검색)와 동일한
 * 형태(`Specification.allOf` + `PageResponse`)로 조립한다 — 목록 API 계약을 Phase 2·3과
 * 일관되게 유지한다.
 *
 * 조회는 대리 취소·변경의 대상 예약을 찾는 진입점이라 취소된 예약도 기본으로 노출한다
 * ([ReservationSearchCondition.status]를 생략하면 조건 없음).
 *
 * **대리 취소·변경은 관리자가 모든 예약에 접근할 수 있어 소유권 조건이 없다** — 회원 경로
 * ([MemberReservationService])가 `findByIdAndMemberId`로 IDOR을 방어하는 것과 다른 점이다.
 * 차감/복구·예약 생성 실행부는 [ReservationLedgerSupport]를 [MemberReservationService]와
 * 공유한다(D-090 확장) — 경로가 갈라지면 D-021 보장이 두 벌로 흩어진다.
 */
@Service
@Transactional(readOnly = true)
class AdminReservationService(
    private val reservationRepository: ReservationRepository,
    private val classScheduleRepository: ClassScheduleRepository,
    private val adminRepository: AdminRepository,
    private val reservationLedgerSupport: ReservationLedgerSupport,
    private val notificationService: NotificationService,
    private val clock: Clock,
) {
    /**
     * 조건을 조합해 예약을 페이지 단위로 조회한다. 정렬은 `reservedAt` 내림차순 고정(최근 예약이 위) —
     * `AdminMemberService.search`가 `createdAt` 내림차순을 쓰는 것과 같은 이유로, 관리자가 방금
     * 들어온 예약·변경을 먼저 보게 한다.
     *
     * `from > to`는 형식 검증(`@Valid`)으로 표현할 수 없는 두 필드 간 관계라 여기서 판정한다
     * (conventions §6 — 도메인 규칙은 서비스에서, `AdminPassService.changePeriod`의 기간 역전
     * 판정과 동일 관례).
     */
    fun search(condition: ReservationSearchCondition): PageResponse<AdminReservationResponse> {
        if (condition.from != null && condition.to != null && condition.from.isAfter(condition.to)) {
            throw InvalidReservationSearchRangeException()
        }
        val specification =
            Specification.allOf<Reservation>(
                listOfNotNull(
                    ReservationSpecifications.classDateBetween(condition.from, condition.to),
                    ReservationSpecifications.hasClassType(condition.classType),
                    ReservationSpecifications.memberKeywordContains(condition.keyword),
                    condition.status?.let { ReservationSpecifications.hasStatus(it) },
                ),
            )
        val pageable = PageRequest.of(condition.page, condition.size, Sort.by(Sort.Direction.DESC, "reservedAt"))
        val page = reservationRepository.findAll(specification, pageable)
        return PageResponse.from(page) { AdminReservationResponse.from(it) }
    }

    /**
     * 관리자 대리 취소(RESV-08, policies §3 "관리자는 당일 포함 제약 없음"). 처리 순서:
     * ① 조회(소유권 조건 없음) → ② [Reservation.assertCancelableByAdmin] → ③ 벌크 UPDATE 전
     * 스칼라 값 확보 → ④ [ReservationRepository.cancelByAdminIfActive] compare-and-swap →
     * ⑤~⑦ [ReservationLedgerSupport.restoreAfterCancellation]로 정원 복구·잔여 복구 판정·이력
     * 저장(회원 경로와 공유) → ⑧ 알림.
     *
     * [AdminCancelReservationRequest.refund]가 `false`면 [ReservationRefundPolicy.shouldRestore]가
     * `false`를 반환해 잔여를 건드리지 않는다 — 당일 불참 연락(policies §3 노쇼 규칙)이나 D-089
     * 2단계 절차(이용권 등록 취소 선행 조건)의 대표 사용처다.
     */
    @Transactional
    fun cancelByAdmin(
        adminId: Long,
        reservationId: Long,
        request: AdminCancelReservationRequest,
    ): AdminReservationResponse {
        // ① 관리자는 모든 예약에 접근 가능하므로 findByIdAndMemberId 같은 소유권 조건이 없다.
        val reservation =
            reservationRepository.findById(reservationId).orElseThrow { ReservationNotFoundException(reservationId) }

        // ② 취소 가능 여부 판정 — 관리자는 당일·과거 제약이 없다.
        reservation.assertCancelableByAdmin()

        // ③ 벌크 UPDATE(clearAutomatically) 전에 이후 필요한 스칼라 값을 지역 변수로 미리 꺼낸다.
        val admin =
            adminRepository.findById(adminId).orElseThrow {
                IllegalStateException("예약을 대리 취소하려는 관리자(id=$adminId)를 찾을 수 없습니다.")
            }
        val sessionId = requireNotNull(reservation.classSession.id) { "저장된 예약은 항상 ClassSession을 참조합니다." }
        val passId = requireNotNull(reservation.pass.id) { "저장된 예약은 항상 Pass를 참조합니다." }
        val passStatus = reservation.pass.status
        val canceledAt = OffsetDateTime.now(clock)

        // ④ 관리자 대리 취소 조건부 UPDATE — 반환 0이면 경쟁에서 졌다(이미 취소됨).
        if (reservationRepository.cancelByAdminIfActive(reservationId, admin, request.refund, canceledAt) == 0) {
            throw ReservationAlreadyCanceledException()
        }

        // ⑤~⑦ 정원 복구 + 복구 여부 판정(D-091) + 잔여 복구·PassTransaction 저장(회원 경로와 공유,
        // D-090 확장) — 이력 주체는 관리자다(member=null, admin=관리자, Pitfall 4).
        reservationLedgerSupport.restoreAfterCancellation(
            sessionId = sessionId,
            passId = passId,
            passStatus = passStatus,
            refundRequested = request.refund,
            canceledAt = canceledAt,
            member = null,
            admin = admin,
        )

        // ⑧ 관리자 알림 생성 — 소유권 조건이 없는 재조회라 bare findById를 그대로 쓴다.
        val refreshedReservation =
            reservationRepository.findById(reservationId).orElseThrow {
                IllegalStateException("방금 취소한 예약(id=$reservationId)을 찾을 수 없습니다.")
            }
        notificationService.createReservationCanceled(refreshedReservation, byAdmin = true)

        return AdminReservationResponse.from(refreshedReservation)
    }

    /**
     * 관리자 대리 변경(RESV-08) — "취소 + 재예약"을 하나의 `@Transactional`로 처리한다
     * (회원 [MemberReservationService.change]와 동일 구조, D-090). 회원 경로와 다른 점은
     * ① 당일·예약 창 검사를 하지 않고(policies §3, `enforceWindow=false`) ② 이력 주체가
     * 관리자이며 ③ 알림이 `RESERVATION_CHANGED_BY_ADMIN`이라는 점이다.
     *
     * **정원 조건부 UPDATE는 그대로 수행한다** — [ReservationLedgerSupport.createReservation]이
     * 회원 경로와 똑같이 `incrementReservedCountIfCapacityAvailable`를 거치므로, 관리자 경로에도
     * 정원 우회 백도어가 없다(CONTEXT.md 락인, T-04-61).
     */
    @Transactional
    fun changeByAdmin(
        adminId: Long,
        reservationId: Long,
        request: AdminChangeReservationRequest,
    ): AdminReservationResponse {
        // ① 기존 예약 조회(소유권 조건 없음) + 새 시간표 조회 + 지점·요일 일치 검증(회원 경로와 동일).
        val reservation =
            reservationRepository.findById(reservationId).orElseThrow { ReservationNotFoundException(reservationId) }
        val admin =
            adminRepository.findById(adminId).orElseThrow {
                IllegalStateException("예약을 대리 변경하려는 관리자(id=$adminId)를 찾을 수 없습니다.")
            }
        val memberBranchId = reservation.member.branch.id
        val newSchedule =
            classScheduleRepository.findById(request.classScheduleId).orElseThrow {
                ClassScheduleNotFoundException(request.classScheduleId)
            }
        if (newSchedule.branch.id != memberBranchId || newSchedule.dayOfWeek != request.classDate.dayOfWeek) {
            throw ClassScheduleNotFoundException(request.classScheduleId)
        }

        // ② 변경 가능 여부 판정 — 관리자는 당일·과거 제약이 없고, 같은 수업 종류 안에서만 변경을 허용한다.
        reservation.assertChangeableByAdmin(newSchedule.classType)

        // ③ 기존 예약 취소 경로 실행 — 이력 주체는 관리자다.
        val memberId = requireNotNull(reservation.member.id) { "저장된 예약은 항상 Member를 참조합니다." }
        val oldSessionId = requireNotNull(reservation.classSession.id) { "저장된 예약은 항상 ClassSession을 참조합니다." }
        val oldPassId = requireNotNull(reservation.pass.id) { "저장된 예약은 항상 Pass를 참조합니다." }
        val oldPassStatus = reservation.pass.status
        val canceledAt = OffsetDateTime.now(clock)
        if (reservationRepository.cancelByAdminIfActive(reservationId, admin, true, canceledAt) == 0) {
            throw ReservationAlreadyCanceledException()
        }
        reservationLedgerSupport.restoreAfterCancellation(
            sessionId = oldSessionId,
            passId = oldPassId,
            passStatus = oldPassStatus,
            refundRequested = true,
            canceledAt = canceledAt,
            member = null,
            admin = admin,
        )

        // ④ 새 예약 생성 경로 — 예약 창 제약 없음(enforceWindow=false), 이력 주체는 관리자.
        val newReservation =
            reservationLedgerSupport.createReservation(
                memberId = memberId,
                schedule = newSchedule,
                classDate = request.classDate,
                enforceWindow = false,
                admin = admin,
            )

        // ⑤ 알림은 변경 1건만 남긴다(취소·예약 각각이 아니라, D-097).
        notificationService.createReservationChanged(newReservation, byAdmin = true)

        return AdminReservationResponse.from(newReservation)
    }
}
