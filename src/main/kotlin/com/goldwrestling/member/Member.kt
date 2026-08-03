package com.goldwrestling.member

import com.goldwrestling.branch.Branch
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
import java.time.OffsetDateTime

/**
 * 회원 (카카오 로그인 사용자).
 *
 * [name]·[phoneNumber]는 온보딩(Phase 2, policies §5.1) 전에는 값이 없다 — 온보딩 완료 판정은
 * 이 두 필드의 입력 여부로 한다(D-025). 컬럼 nullable과 Kotlin 타입 nullable을 함께 맞춘다 —
 * 불일치는 `ddl-auto=validate`가 잡지 못하는 종류의 버그다(conventions §3).
 *
 * `created_at`은 서비스가 주입받은 `Clock`으로 명시적으로 채운다 — JPA Auditing은 도입하지 않는다
 * (D-039). DB의 `DEFAULT now()`는 방어선으로 남긴다.
 */
@Entity
@Table(name = "member")
class Member(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    val branch: Branch,
    @Column(length = 50)
    var name: String?,
    @Column(name = "phone_number", length = 20)
    var phoneNumber: String?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MemberStatus = MemberStatus.PENDING,
    @Column(name = "kakao_id", nullable = false, updatable = false)
    val kakaoId: Long,
    @Column(name = "rejection_reason", length = 500)
    var rejectionReason: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /**
     * 온보딩 완료 여부 (policies §5.1) — 별도 상태 컬럼 없이 이름·전화번호 입력 여부로 판정한다(D-025).
     */
    fun isOnboardingCompleted(): Boolean = !name.isNullOrBlank() && !phoneNumber.isNullOrBlank()

    /**
     * 거절된 회원인지 (policies §5.2, D-034) — 거절은 별도 상태가 아니라 `INACTIVE` + 거절 사유 존재로 식별한다.
     * `INACTIVE`이지만 사유가 없으면 탈퇴·장기 미이용 유래이지 거절이 아니다.
     */
    fun isRejected(): Boolean = status == MemberStatus.INACTIVE && !rejectionReason.isNullOrBlank()
}
