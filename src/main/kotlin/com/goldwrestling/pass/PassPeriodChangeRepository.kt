package com.goldwrestling.pass

import org.springframework.data.jpa.repository.JpaRepository

interface PassPeriodChangeRepository : JpaRepository<PassPeriodChange, Long> {
    fun findAllByPassIdOrderByOccurredAtDesc(passId: Long): List<PassPeriodChange>
}
