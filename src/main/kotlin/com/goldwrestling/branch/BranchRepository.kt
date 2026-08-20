package com.goldwrestling.branch

import org.springframework.data.jpa.repository.JpaRepository

interface BranchRepository : JpaRepository<Branch, Long> {
    fun findByName(name: String): Branch?
}
