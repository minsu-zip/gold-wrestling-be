package com.goldwrestling.pass

import com.goldwrestling.admin.Admin
import com.goldwrestling.member.Member
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
 * [reason](코드)과 [note](자유 텍스트)는 분리한다(D-061) — `ADMIN_ADJUST`일 때만 `note` 필수를
 * 서비스 계층이 강제한다.
 *
 * **주체는 "이 이용권의 소유자"가 아니라 "이 이력을 발생시킨 행위자"다**(V8 헤더 주석). [admin]/[member]
 * 중 **정확히 하나**만 채워진다 — 회원 셀프 예약/취소/변경은 [member], 관리자 대리 조작과 휴강
 * 캐스케이드(`CLASS_CANCELED_REFUND`)는 [admin]이 주체다. Phase 3의 모든 경로(등록·수동 가감·기간
 * 변경·등록 취소)는 전부 관리자 주체라 [member]가 항상 null이었다 — Phase 4가 회원 셀프 예약/취소
 * 경로에서 처음으로 [member] 주체를 쓴다. "정확히 하나" 불변식의 최종 방어선은
 * `ck_pass_transaction_subject` CHECK(V8)다 — `RefreshToken`의 `ck_refresh_token_principal`과
 * 동일한 사고.
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
    @JoinColumn(name = "admin_id")
    val admin: Admin?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    val member: Member?,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
