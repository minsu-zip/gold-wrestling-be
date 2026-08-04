package com.goldwrestling.pass

import com.goldwrestling.admin.Admin
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * 이용권 ±수량 원장 (glossary.md "차감/복구 이력", D-057 "PassTransaction은 ±수량 원장 역할에 고정").
 *
 * **append-only** — 전 필드를 `val`로 선언해 이력 불변성을 코드로 보장한다(setter 없음).
 * 주체는 항상 관리자([admin])다 — Phase 3의 사유 코드는 전부 관리자 주체이고, "정확히 하나"류
 * CHECK를 미리 넣지 않는다(RESEARCH Pitfall 5). [reason](코드)과 [note](자유 텍스트)는
 * 분리한다(D-061) — `ADMIN_ADJUST`일 때만 `note` 필수를 서비스 계층이 강제한다.
 */
@Entity
@Table(name = "pass_transaction")
class PassTransaction(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pass_id", nullable = false)
    val pass: Pass,
    @Column(nullable = false, precision = 4, scale = 1)
    val amount: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val reason: TransactionReason,
    @Column(length = 500)
    val note: String?,
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
