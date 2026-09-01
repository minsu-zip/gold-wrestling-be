package com.goldwrestling.pass

import com.goldwrestling.admin.Admin
import com.goldwrestling.common.projection.MemberTimestampProjection
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

    /**
     * 배치 대상 회원 벌크 조회(BATCH-01·02, Phase 5) — 차감 가능한 `SESSION_PASS`를 가진 회원 id를
     * 중복 없이 오름차순으로 반환한다.
     *
     * **BATCH-02의 예외 4종(휴회·비활성·잔여 0·만료)이 전부 이 한 쿼리의 필터로 구현된다** — 다른
     * 곳에 같은 예외를 다시 구현하지 않는다(RESEARCH Pitfall 2). 과거 제외 기간을 경과일에서 빼는
     * 로직을 추가하지 않는다 — 제외 이탈 시각의 기준일 리셋(D-105 후보 ③)이 이미 그 역할을 한다.
     *
     * - `endDate >= :today` — [Pass.isExpired]가 쓰는 것과 같은 비교축(D-066 종료일 포함 판정)
     * - `remainingCount > 0` — 소진된 이용권은 대상이 아니다
     * - `member.status not in (ON_LEAVE, INACTIVE)` — 차감 제외 상태인 회원은 현재 상태로 즉시
     *   제외한다. `INACTIVE`가 들어간 것은 WR-06 결정이다(D-147) — 탈퇴·장기 미이용 회원의 잔여가
     *   계속 깎여 0이 되면 환불 분쟁의 소지가 된다.
     *
     * **상태 목록을 여기에 문자열로 늘어놓지 말 것** — 판정의 근거는
     * [com.goldwrestling.member.MemberStatus.DEDUCTION_EXCLUDED] 하나이고, 기준일 후보 ③을 기록하는
     * `AdminMemberService.changeStatus`가 같은 집합을 본다. JPQL에는 상수 참조를 넣을 수 없어 값이
     * 중복되므로, 두 곳이 어긋나면 `InactivityBatchQueryTest`의 "차감 제외 상태 목록은 MemberStatus
     * DEDUCTION_EXCLUDED와 일치한다" 케이스가 실패한다.
     */
    @Query(
        "select distinct p.member.id from Pass p " +
            "where p.type = com.goldwrestling.pass.PassType.SESSION_PASS " +
            "and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
            "and p.endDate >= :today and p.remainingCount > 0 " +
            "and p.member.status not in (" +
            "com.goldwrestling.member.MemberStatus.ON_LEAVE, " +
            "com.goldwrestling.member.MemberStatus.INACTIVE" +
            ") " +
            "order by p.member.id asc",
    )
    fun findMemberIdsWithDeductibleSessionPass(
        @Param("today") today: LocalDate,
    ): List<Long>

    /**
     * 기준일 후보 ④(D-105) 벌크 조회 — 회원별로 **차감 가능한 장** 중 가장 최근 등록일을 반환한다.
     *
     * **`startDate`가 아니라 `createdAt`이다**(D-105·RESEARCH Pitfall 4) — 과거 시작일 등록(D-055)이
     * 등록 즉시 소급 차감으로 이어지는 것을 막는다. 범위는 [findMemberIdsWithDeductibleSessionPass]와
     * 같은 필터(차감 가능한 장)로 한정한다(D-105 보강, 2026-08-15 사용자 확정) — 만료·소진·취소된
     * 장의 등록일은 새 장 등록으로 시계를 리셋하는 원리(D-105 "부채가 쌓이지 않는다")와 무관하다.
     *
     * [memberIds]가 빈 컬렉션이면 빈 결과를 반환한다 — 호출부가 빈 목록으로 이 쿼리를 호출해도
     * 안전하다.
     */
    @Query(
        "select p.member.id as memberId, max(p.createdAt) as timestamp from Pass p " +
            "where p.member.id in :memberIds " +
            "and p.type = com.goldwrestling.pass.PassType.SESSION_PASS " +
            "and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
            "and p.endDate >= :today and p.remainingCount > 0 " +
            "group by p.member.id",
    )
    fun findLastDeductibleSessionPassRegistrationDates(
        @Param("memberIds") memberIds: Collection<Long>,
        @Param("today") today: LocalDate,
    ): List<MemberTimestampProjection>

    /**
     * 차감 대상 이용권 재조회(D-109 "회당 재선택") — [findDeductionCandidates](예약용,
     * `remainingCount >= requiredAmount`)와 달리 잔여가 차감량(1.0)보다 적은 장(0.5)도 포함한다 —
     * 부분 차감(D-109)의 실현부이기 때문이다. 05-05가 **차감 1회마다 다시 호출**한다(RESEARCH
     * Pitfall 1) — 한 번 조회한 리스트를 여러 회차에 걸쳐 재사용하면 앞선 차감으로 소진된 장을
     * 계속 대상으로 잡는다.
     */
    @Query(
        "select p from Pass p where p.member.id = :memberId " +
            "and p.type = com.goldwrestling.pass.PassType.SESSION_PASS " +
            "and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
            "and p.endDate >= :today and p.remainingCount > 0 " +
            "order by p.endDate asc, p.id asc",
    )
    fun findDeductibleSessionPasses(
        @Param("memberId") memberId: Long,
        @Param("today") today: LocalDate,
    ): List<Pass>

    /**
     * 저녁반 회비 우선 판정(policies §4.2, D-128)의 데이터 근거 — 회원이 [classDate] 기준으로
     * 유효한 `EVENING_MEMBERSHIP`을 보유하는지 조회한다.
     *
     * **이 쿼리의 `classDate`는 오늘이 아니라 수업날이다**(D-128·Pitfall 1) — 관리자가 지난 수업의
     * 출석을 소급 입력할 때, "오늘" 기준으로 판정하면 그날 실제로 유효했던 회비를 놓치고
     * `SESSION_PASS`를 잘못 차감하게 된다.
     *
     * `startDate <= :classDate and endDate >= :classDate` — `endDate >= :classDate`는
     * [findDeductionCandidates]가 쓰는 것과 **같은 비교축**(D-066 종료일 포함 판정)이다. 다른
     * 비교식(`>`)을 쓰면 종료일 당일 수업이 경계에서 어긋난다.
     */
    @Query(
        "select count(p) > 0 from Pass p where p.member.id = :memberId " +
            "and p.type = com.goldwrestling.pass.PassType.EVENING_MEMBERSHIP " +
            "and p.status = com.goldwrestling.pass.PassStatus.ACTIVE " +
            "and p.startDate <= :classDate and p.endDate >= :classDate",
    )
    fun existsActiveEveningMembership(
        @Param("memberId") memberId: Long,
        @Param("classDate") classDate: LocalDate,
    ): Boolean
}
