package com.goldwrestling.pass

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
}
