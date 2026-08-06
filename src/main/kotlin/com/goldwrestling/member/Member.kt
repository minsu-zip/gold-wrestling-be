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
 *
 * [kakaoNickname]·[kakaoProfileImageUrl]은 카카오가 준 **표시용 보조 정보**이지 운영 기준 신원이
 * 아니다(D-083). 매 로그인마다 [applyKakaoProfile]로 갱신되며, 동의가 없으면 값이 없는 것이 정상이다.
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
    @Column(name = "kakao_nickname", length = 100)
    var kakaoNickname: String? = null,
    @Column(name = "kakao_profile_image_url", length = 500)
    var kakaoProfileImageUrl: String? = null,
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

    /**
     * 카카오가 이번 로그인에서 준 프로필(닉네임·프로필 이미지 URL)을 그대로 반영한다(D-083).
     *
     * **값이 없으면 `null`로 덮어쓰는 것이 의도된 동작이다.** 동의항목 미추가·동의 거부·사후 철회로
     * 카카오가 값을 주지 않으면 우리 DB도 값을 갖지 않아야 한다 — 회원이 동의를 철회했는데 우리가
     * 계속 보관하는 상태를 만들지 않는다. 빈 문자열·공백만 문자열은 "값이 없음"과 같은 뜻이므로
     * `null`로 정규화한다(DB에 빈 문자열이 들어가면 FE가 null 분기와 빈 문자열 분기를 둘 다 다뤄야 한다).
     *
     * "값이 바뀌었는지" 비교 분기를 두지 않는다. JPA 변경 감지는 로딩 시점 스냅샷과 필드를 비교하므로
     * 같은 값을 다시 대입하면 UPDATE 자체가 나가지 않는다 — 단순 대입만으로 "실제로 바뀐 경우에만
     * UPDATE"가 성립한다.
     */
    fun applyKakaoProfile(
        nickname: String?,
        profileImageUrl: String?,
    ) {
        kakaoNickname = nickname?.takeIf { it.isNotBlank() }
        kakaoProfileImageUrl = profileImageUrl?.takeIf { it.isNotBlank() }
    }
}
