package com.goldwrestling.pass

import com.goldwrestling.common.projection.MemberTimestampProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

/**
 * `JpaSpecificationExecutor`를 함께 상속해 둔다 — 03-06(이력 조회, D-058 페이지네이션+필터)이
 * 이 인터페이스를 다시 건드리지 않고 바로 쓸 수 있게 하기 위해서다.
 */
interface PassTransactionRepository :
    JpaRepository<PassTransaction, Long>,
    JpaSpecificationExecutor<PassTransaction> {
    /**
     * 한 이용권의 이력 수량 합계를 한 번의 질의로 얻는다 ("잔여 = 이력 합계" 불변식 검증에 쓴다).
     * `coalesce`가 없으면 이력이 없는 이용권에서 `null`이 돌아와, 그 값을 잔여와 비교하는 호출부가
     * NPE로 죽는다.
     */
    @Query("select coalesce(sum(t.amount), 0) from PassTransaction t where t.pass.id = :passId")
    fun sumAmountByPassId(
        @Param("passId") passId: Long,
    ): BigDecimal

    /**
     * 기준일 후보 ⑤(D-105) 벌크 조회 — 회원별 **양(+)** `ADMIN_ADJUST` 이력의 `occurredAt` 최댓값을
     * 반환한다(Phase 5).
     *
     * 양(+) 가감만 세는 이유: 관리자가 서비스로 충전해 준 횟수가 곧바로 미사용으로 깎이지 않게 하는
     * 것이 이 후보의 목적이다(D-105 "차감 제외 기간에는 부채가 쌓이지 않는다"). 음(−) 가감·다른
     * 사유 코드(`INITIAL_GRANT` 등)는 후보가 아니다. [memberIds]가 빈 컬렉션이면 빈 결과를 반환한다.
     */
    @Query(
        "select pt.pass.member.id as memberId, max(pt.occurredAt) as timestamp from PassTransaction pt " +
            "where pt.pass.member.id in :memberIds " +
            "and pt.reason = com.goldwrestling.pass.TransactionReason.ADMIN_ADJUST and pt.amount > 0 " +
            "group by pt.pass.member.id",
    )
    fun findLastPositiveAdjustTimestamps(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<MemberTimestampProjection>

    /**
     * D-106 멱등·캐치업 계산의 입력 — `INACTIVITY` 이력을 **집계 없이 건별로** 반환한다(Phase 5).
     *
     * **이 건수가 멱등성의 근거다**(D-106) — 배치 실행 이력 테이블이 아니라 이 원장이 진실
     * 원천이다. 부분 차감(0.5)도 1건으로 센다 — 집계 없이 행 하나하나를 그대로 돌려주므로
     * 호출부(`InactivityDueDateCalculator.shortfall`)가 회원별로 `size`만 세면 된다. [memberIds]가
     * 빈 컬렉션이면 빈 결과를 반환한다.
     */
    @Query(
        "select pt.pass.member.id as memberId, pt.occurredAt as timestamp from PassTransaction pt " +
            "where pt.pass.member.id in :memberIds " +
            "and pt.reason = com.goldwrestling.pass.TransactionReason.INACTIVITY " +
            "order by pt.occurredAt asc",
    )
    fun findInactivityEventTimestamps(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<MemberTimestampProjection>
}
