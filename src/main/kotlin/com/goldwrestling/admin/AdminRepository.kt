package com.goldwrestling.admin

import org.springframework.data.jpa.repository.JpaRepository

interface AdminRepository : JpaRepository<Admin, Long> {
    fun findByLoginId(loginId: String): Admin?

    fun existsByLoginId(loginId: String): Boolean
}
