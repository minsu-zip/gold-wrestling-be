package com.goldwrestling.pass

import com.goldwrestling.admin.Admin
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 기간·유효기간 변경 이력 (glossary.md "기간 변경 이력", D-057). 저녁반 기간 수정(PASS-04)과
 * 횟수권 유효기간 수정(PASS-07)을 하나의 이력 테이블로 통합한다(D-062 통합 엔드포인트와 짝).
 *
 * **append-only** — 전 필드를 `val`로 선언해 이력 불변성을 코드로 보장한다(setter 없음).
 */
@Entity
@Table(name = "pass_period_change")
class PassPeriodChange(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pass_id", nullable = false)
    val pass: Pass,
    @Column(name = "previous_start_date", nullable = false)
    val previousStartDate: LocalDate,
    @Column(name = "previous_end_date", nullable = false)
    val previousEndDate: LocalDate,
    @Column(name = "new_start_date", nullable = false)
    val newStartDate: LocalDate,
    @Column(name = "new_end_date", nullable = false)
    val newEndDate: LocalDate,
    @Column(nullable = false, length = 500)
    val reason: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    val admin: Admin,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
