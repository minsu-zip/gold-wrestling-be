package com.goldwrestling.reservation

import com.goldwrestling.admin.Admin
import com.goldwrestling.member.Member
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * `JpaSpecificationExecutor`를 함께 상속한다 — 04-07 이후의 관리자 예약 조회(RESV-07, 필터+페이지네이션)가
 * 이 인터페이스를 다시 건드리지 않고 바로 쓸 수 있게 하기 위해서다(`PassRepository`와 동일 관례).
 */
interface ReservationRepository :
    JpaRepository<Reservation, Long>,
    JpaSpecificationExecutor<Reservation> {
    /**
     * 관리자 예약 검색(RESV-07, `AdminReservationService.search`)이 쓰는 페이지 조회를 재선언해
     * `@EntityGraph`를 붙인다(T-04-58) — `AdminReservationResponse.from`이 `member`·`classSession`·
     * `canceledByMember`·`canceledByAdmin`(전부 `ManyToOne`, 단일 연관) `LAZY` 필드를 읽으므로,
     * 미리 로딩해 두지 않으면 페이지 크기만큼 N+1이 난다. `Specification`에 `root.fetch(...)`를
     * 넣는 대신 이 방식을 쓰는 이유: fetch join은 페이지네이션의 count 쿼리에도 함께 적용되면
     * JPA 스펙 위반이 되지만, `@EntityGraph` 힌트는 count 쿼리에는 적용되지 않고 본문 조회에만
     * 적용된다(verify-boot4-api 절차로 spring-data-jpa 4.1.0 테스트 코드
     * `RepositoryMethodsWithEntityGraphConfigRepository`에서 이 재선언 패턴을 확인).
     */
    @EntityGraph(attributePaths = ["member", "classSession", "canceledByMember", "canceledByAdmin"])
    override fun findAll(
        spec: Specification<Reservation>,
        pageable: Pageable,
    ): Page<Reservation>

    /** 등록 취소 선행 검사(D-089, `AdminPassService.cancel`이 호출) — 활성 예약 유무만 확인한다. */
    fun existsByPassIdAndStatus(
        passId: Long,
        status: ReservationStatus,
    ): Boolean

    /**
     * 중복 예약 사전 검사(D-092, `MemberReservationService.reserve` ⑥단계) — 같은 회원이 같은
     * 날짜·시각에 이미 활성 예약을 갖고 있는지 확인한다. **이 검사는 사용자에게 정확한 에러를
     * 주기 위한 것일 뿐, 실제 방어는 부분 유니크 인덱스(`ux_reservation_member_timeslot_active`,
     * V6, D-021)다** — 사전 검사와 커밋 사이에 다른 트랜잭션이 끼어들 수 있기 때문이다.
     */
    fun existsByMemberIdAndClassDateAndStartTimeAndStatus(
        memberId: Long,
        classDate: LocalDate,
        startTime: LocalTime,
        status: ReservationStatus,
    ): Boolean

    /** 스케줄보드 셀 명단 등 단일 세션 기준 조회. */
    fun findAllByClassSessionIdAndStatus(
        classSessionId: Long,
        status: ReservationStatus,
    ): List<Reservation>

    /**
     * 관리자 주간 보드가 셀별 **전체 명단**을 N+1 없이 한 번에 가져오는 경로.
     *
     * 회원 시간표 조회는 이 메서드를 쓰지 않는다 — 본인 예약 표시에만 쓰면서 그 주 모든 회원의
     * 예약 행을 메모리에 올리게 되기 때문이다. 회원 경로는
     * [findAllByClassSessionIdInAndStatusAndMemberId]를 쓴다.
     */
    fun findAllByClassSessionIdInAndStatus(
        classSessionIds: Collection<Long>,
        status: ReservationStatus,
    ): List<Reservation>

    /**
     * 관리자 주간 보드의 셀별 예약자 명단(D-096)이 `member.name`을 N+1 없이 가져오는 경로
     * (T-04-54, 관리자 스케줄 보드 최초 도입) — [findAllByClassSessionIdInAndStatus]와 조건은
     * 같지만 `join fetch`로 `member`를 함께 로딩한다. 이 저장소에서 `join fetch`를 쓰는 첫
     * 사례다 — 명단에 `memberName`을 담는 순간 `member`(LAZY) 프록시를 건드리게 되므로, 미리
     * 가져오지 않으면 명단 크기만큼(셀당 최대 정원 수) 추가 쿼리가 나간다.
     */
    @Query(
        "select r from Reservation r join fetch r.member " +
            "where r.classSession.id in :classSessionIds and r.status = :status",
    )
    fun findAllByClassSessionIdInAndStatusWithMember(
        @Param("classSessionIds") classSessionIds: Collection<Long>,
        @Param("status") status: ReservationStatus,
    ): List<Reservation>

    /**
     * 회원 시간표 조회 전용 — 세션 목록 안에서 **[memberId] 본인의 예약만** 가져온다.
     *
     * 회원 조건을 애플리케이션 `filter`가 아니라 쿼리에 두는 이유: 정원이 찬 수업이 여러 개 열린
     * 주에는 셀당 수십 건씩, 한 주 전체로는 수백 건의 타인 예약 행이 로드된다. 응답에 노출되지는
     * 않으므로(D-096) 정보 유출은 아니지만, 회원 한 명의 시간표 조회가 지불할 이유가 없는 비용이다.
     */
    fun findAllByClassSessionIdInAndStatusAndMemberId(
        classSessionIds: Collection<Long>,
        status: ReservationStatus,
        memberId: Long,
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
