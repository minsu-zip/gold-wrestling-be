package com.goldwrestling.pass

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

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
}
