package com.goldwrestling.pass

import com.goldwrestling.admin.Admin
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * `JpaSpecificationExecutor`를 함께 상속해 둔다 — 03-09/03-10의 이용권 목록·필터 조회가 이
 * 인터페이스를 다시 건드리지 않고 바로 쓸 수 있게 하기 위해서다(`MemberRepository`와 동일 관례).
 */
interface PassRepository :
    JpaRepository<Pass, Long>,
    JpaSpecificationExecutor<Pass> {
    /** 관리자 목록(회원별 이용권 전체, 취소 포함) — 최신 등록순. */
    fun findAllByMemberIdOrderByStartDateDescIdDesc(memberId: Long): List<Pass>

    /** 회원 본인 조회(취소된 이용권 제외, D-058) — 최신 등록순. */
    fun findAllByMemberIdAndStatusNotOrderByStartDateDescIdDesc(
        memberId: Long,
        status: PassStatus,
    ): List<Pass>

    /**
     * 잔여 횟수를 원자적으로 가감한다(D-021 — Phase 4의 예약 차감이 그대로 재사용할 경로).
     * `status = ACTIVE` 조건으로 취소된 이용권이 이 경로로 되살아나는 것을 DB에서 막고,
     * `remainingCount + :amount >= 0` 조건으로 결과가 음수가 되는 갱신을 DB가 거부한다 —
     * 애플리케이션 사전 판정은 사용자 안내용일 뿐 방어선이 아니다.
     *
     * 반환값은 갱신된 행 수 — **0이면 경쟁에서 졌거나(동시 가감) 사전 판정 이후 상태(취소·잔여)가
     * 바뀐 것**이다. 호출부는 재조회 후 정확한 도메인 예외로 변환한다(`RefreshTokenRepository.revokeIfUsable`와
     * 동일 관례). `flushAutomatically`/`clearAutomatically` 이유도 그 KDoc과 동일 — 벌크 UPDATE는
     * 영속성 컨텍스트를 거치지 않고 DB에 직접 SQL을 보내므로, 이전 더티 상태를 먼저 flush해야
     * 최신 상태 위에서 정확히 실행되고(`flushAutomatically`), 실행 후 컨텍스트를 비워야
     * 1차 캐시의 갱신 전 스냅샷을 다시 읽는 사고를 막는다(`clearAutomatically`). 호출부는 이 쿼리
     * 실행 전에 이후 로직에 필요한 스칼라 값을 지역 변수로 미리 꺼내둬야 한다 — 실행 직후 준영속
     * 상태가 된 엔티티의 LAZY 연관에 접근하면 `LazyInitializationException`이 난다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update Pass p set p.remainingCount = p.remainingCount + :amount " +
            "where p.id = :id and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
            "and p.remainingCount + :amount >= 0",
    )
    fun adjustRemainingCount(
        @Param("id") id: Long,
        @Param("amount") amount: BigDecimal,
    ): Int

    /**
     * 등록 취소(D-059)의 잔여 0 상쇄에 쓴다 — 조회 시점 잔여가 [expectedRemaining]과 일치할 때만
     * 잔여를 0으로 만든다. 반환 0의 의미와 flush/clear 이유는 [adjustRemainingCount]와 같다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Pass p set p.remainingCount = 0 where p.id = :id and p.remainingCount = :expectedRemaining")
    fun zeroRemainingCount(
        @Param("id") id: Long,
        @Param("expectedRemaining") expectedRemaining: BigDecimal,
    ): Int

    /**
     * 등록 취소(PASS-08, D-059, D-072)의 상태 전환을 원자적으로 반영한다. `status <> CANCELED`
     * 조건으로 이미 취소된 이용권의 재취소·경쟁 중복 반영을 DB에서 막는다 — 두 관리자가 동시에
     * 취소를 요청해도 이 조건부 UPDATE는 정확히 한 트랜잭션만 반영한다(D-021).
     *
     * 반환값은 갱신된 행 수 — **0이면 사전 판정(`Pass.resolveCancellationOffset`) 이후 다른
     * 트랜잭션이 먼저 취소를 확정한 것**이다. 호출부는 [PassAlreadyCanceledException]으로
     * 변환한다(`RefreshTokenRepository.revokeIfUsable`와 동일 관례). `flushAutomatically`/
     * `clearAutomatically` 이유도 그 KDoc과 동일. 호출부는 이 쿼리 실행 전에 이후 로직에 필요한
     * 스칼라 값을 지역 변수로 미리 꺼내둬야 한다 — 실행 직후 준영속 상태가 된 엔티티의 LAZY
     * 연관에 접근하면 `LazyInitializationException`이 난다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update Pass p set p.status = com.goldwrestling.pass.PassStatus.CANCELED, " +
            "p.cancelReason = :reason, p.canceledBy = :admin, p.canceledAt = :now " +
            "where p.id = :id and p.status <> com.goldwrestling.pass.PassStatus.CANCELED",
    )
    fun cancelIfNotCanceled(
        @Param("id") id: Long,
        @Param("reason") reason: String,
        @Param("admin") admin: Admin,
        @Param("now") now: OffsetDateTime,
    ): Int

    /**
     * 기간·유효기간 수정(PASS-04·PASS-07, D-062, D-072)을 compare-and-swap으로 반영한다. 조회
     * 시점에 호출부가 읽은 전값(`expectedStartDate`/`expectedEndDate`)과 DB의 현재 값이 같을
     * 때만 반영한다 — 두 관리자가 동시에 기간을 수정하면 먼저 커밋한 쪽만 반영되고, 나중 쪽은
     * 전값이 이미 달라져 있어 갱신 행 수 0을 받는다. `status <> CANCELED` 조건으로 취소된
     * 이용권의 기간 수정도 함께 막는다.
     *
     * 반환값은 갱신된 행 수 — **0이면 취소됐거나(경쟁) 그 사이 다른 트랜잭션이 기간을 먼저
     * 바꾼 것**이다. 호출부는 재조회해 상태로 원인을 구분하고 정확한 도메인 예외로 변환한다.
     * flush/clear 이유는 [cancelIfNotCanceled]와 같다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update Pass p set p.startDate = :newStartDate, p.endDate = :newEndDate " +
            "where p.id = :id and p.status <> com.goldwrestling.pass.PassStatus.CANCELED " +
            "and p.startDate = :expectedStartDate and p.endDate = :expectedEndDate",
    )
    fun changePeriodIfUnchanged(
        @Param("id") id: Long,
        @Param("newStartDate") newStartDate: LocalDate,
        @Param("newEndDate") newEndDate: LocalDate,
        @Param("expectedStartDate") expectedStartDate: LocalDate,
        @Param("expectedEndDate") expectedEndDate: LocalDate,
    ): Int

    /**
     * 예약 차감 대상 이용권 후보를 만료 임박순으로 조회한다(D-091 — 만료 임박순·합산 금지).
     *
     * **이 쿼리의 `classDate`는 예약일이 아니라 수업 날짜다.** 만료 직전에 다음 달 수업을 전부
     * 예약해 유효기간을 사실상 연장하는 우회를 막기 위해서다 — 잘못 넘기면 유효기간 우회가 열린다.
     *
     * - `status = ACTIVE` — 등록 취소된 이용권은 후보에서 제외한다(D-089/D-091)
     * - `endDate >= classDate` — [Pass.isExpired]가 쓰는 것과 **같은 비교축**(D-066 종료일 포함
     *   판정)이다. 다른 비교식(`>`)을 쓰면 종료일 당일 수업이 경계에서 어긋난다
     * - `remainingCount >= requiredAmount` — **단일 이용권 기준**이다. 여러 장의 잔여를 SQL이
     *   합산해 비교하지 않는다 — 이것이 "0.5회 두 장으로 1회 예약 불가"(RESV-03)의 실현부다
     * - `order by endDate asc, id asc` — 만료 임박순, 동률이면 `id`로 결정적 순서를 보장한다.
     *   `ReservationPassPolicy.selectCandidate`가 이 정렬을 신뢰하고 `first()`만 취한다 — 서비스가
     *   다시 정렬하지 않는다
     */
    @Query(
        "select p from Pass p where p.member.id = :memberId and p.type = :type " +
            "and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
            "and p.endDate >= :classDate and p.remainingCount >= :requiredAmount " +
            "order by p.endDate asc, p.id asc",
    )
    fun findDeductionCandidates(
        @Param("memberId") memberId: Long,
        @Param("type") type: PassType,
        @Param("classDate") classDate: LocalDate,
        @Param("requiredAmount") requiredAmount: BigDecimal,
    ): List<Pass>
}
