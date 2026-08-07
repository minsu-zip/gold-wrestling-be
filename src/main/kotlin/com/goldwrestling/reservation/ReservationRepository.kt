package com.goldwrestling.reservation

import com.goldwrestling.admin.Admin
import com.goldwrestling.member.Member
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

/**
 * `JpaSpecificationExecutor`를 함께 상속한다 — 04-07 이후의 관리자 예약 조회(RESV-07, 필터+페이지네이션)가
 * 이 인터페이스를 다시 건드리지 않고 바로 쓸 수 있게 하기 위해서다(`PassRepository`와 동일 관례).
 */
interface ReservationRepository :
    JpaRepository<Reservation, Long>,
    JpaSpecificationExecutor<Reservation> {
    /** 등록 취소 선행 검사(D-089, `AdminPassService.cancel`이 호출) — 활성 예약 유무만 확인한다. */
    fun existsByPassIdAndStatus(
        passId: Long,
        status: ReservationStatus,
    ): Boolean

    /** 스케줄보드 셀 명단 등 단일 세션 기준 조회. */
    fun findAllByClassSessionIdAndStatus(
        classSessionId: Long,
        status: ReservationStatus,
    ): List<Reservation>

    /** 주간 보드가 셀별 명단을 N+1 없이 한 번에 가져오는 경로. */
    fun findAllByClassSessionIdInAndStatus(
        classSessionIds: Collection<Long>,
        status: ReservationStatus,
    ): List<Reservation>

    /**
     * 회원 경로 조회 전용 — 소유자 조건이 포함돼 있어 IDOR을 방어한다(T-04-12). 회원 서비스는
     * 소유자 없는 `findById`를 쓰지 않는다 — 남의 예약 id로 조회를 시도하면 이 메서드는 `null`을
     * 반환하고 호출부는 [ReservationNotFoundException](404)으로 변환한다(403이 "그 id가 존재한다"는
     * 사실 자체를 노출하는 것을 막기 위해).
     */
    fun findByIdAndMemberId(
        id: Long,
        memberId: Long,
    ): Reservation?

    /**
     * 회원 셀프 취소를 compare-and-swap으로 반영한다(`PassRepository.cancelIfNotCanceled`와
     * 동일 관례). `status = ACTIVE` 조건으로 이미 취소된 예약의 재취소·경쟁 중복 반영을 DB에서
     * 막는다(D-021).
     *
     * **Pitfall 2**: 이 UPDATE의 반환 0은 "경쟁에서 졌다"는 뜻이지만, 뒤따르는
     * `PassRepository.adjustRemainingCount`의 반환 0은 "등록 취소된 이용권이라 복구하지 않는다"는
     * **정상 경로**다(D-091) — 두 0을 같은 의미로 다루지 않는다.
     *
     * 반환값은 갱신된 행 수 — 0이면 호출부가 [ReservationAlreadyCanceledException]으로 변환한다.
     * `flushAutomatically`/`clearAutomatically` 이유는 `PassRepository.adjustRemainingCount`와 같다 —
     * 벌크 UPDATE는 영속성 컨텍스트를 거치지 않고 DB에 직접 SQL을 보내므로, 이전 더티 상태를 먼저
     * flush해야 최신 상태 위에서 정확히 실행되고(`flushAutomatically`), 실행 후 컨텍스트를 비워야
     * 1차 캐시의 갱신 전 스냅샷을 다시 읽는 사고를 막는다(`clearAutomatically`). 호출부는 이 쿼리
     * 실행 전에 이후 로직(이용권 복구 등)에 필요한 스칼라 값을 지역 변수로 미리 꺼내둬야 한다 —
     * 실행 직후 준영속 상태가 된 엔티티의 LAZY 연관에 접근하면 `LazyInitializationException`이 난다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update Reservation r set r.status = com.goldwrestling.reservation.ReservationStatus.CANCELED, " +
            "r.canceledAt = :canceledAt, r.canceledByMember = :canceledBy, r.refunded = true " +
            "where r.id = :id and r.status = com.goldwrestling.reservation.ReservationStatus.ACTIVE",
    )
    fun cancelByMemberIfActive(
        @Param("id") id: Long,
        @Param("canceledBy") canceledBy: Member,
        @Param("canceledAt") canceledAt: OffsetDateTime,
    ): Int

    /**
     * 관리자 대리 취소를 compare-and-swap으로 반영한다(RESV-08). [refunded]는 요청에서 받은 값을
     * 그대로 저장한다 — 기본은 복구(`true`)지만 당일 불참 연락 등 "복구 안 함"(`false`) 선택을
     * 지원한다(policies §3 "관리자의 취소/변경"). 반환값·Pitfall 2·flush/clear 이유는
     * [cancelByMemberIfActive]와 같다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update Reservation r set r.status = com.goldwrestling.reservation.ReservationStatus.CANCELED, " +
            "r.canceledAt = :canceledAt, r.canceledByAdmin = :canceledBy, r.refunded = :refunded " +
            "where r.id = :id and r.status = com.goldwrestling.reservation.ReservationStatus.ACTIVE",
    )
    fun cancelByAdminIfActive(
        @Param("id") id: Long,
        @Param("canceledBy") canceledBy: Admin,
        @Param("refunded") refunded: Boolean,
        @Param("canceledAt") canceledAt: OffsetDateTime,
    ): Int
}
