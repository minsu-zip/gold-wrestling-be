package com.goldwrestling.batch

import org.springframework.data.jpa.repository.JpaRepository

/**
 * 이번 phase는 조회 API가 없어 커스텀 쿼리·`JpaSpecificationExecutor`를 넣지 않는다.
 */
interface BatchExecutionRepository : JpaRepository<BatchExecution, Long>
