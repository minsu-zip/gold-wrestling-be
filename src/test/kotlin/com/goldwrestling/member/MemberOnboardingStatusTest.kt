package com.goldwrestling.member

import com.goldwrestling.branch.Branch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

/**
 * 온보딩 완료·거절 판정 (policies §5.1, §5.2, D-025, D-034) — 순수 Kotlin 단위테스트.
 * 스프링 컨텍스트 없이 엔티티 메서드만 검증한다 (add-domain-test §1).
 */
class MemberOnboardingStatusTest {
    @Test
    fun `이름과 전화번호가 둘 다 있으면 온보딩 완료로 판정한다`() {
        val member = member(name = "홍길동", phoneNumber = "01012345678", status = MemberStatus.PENDING)

        assertThat(member.isOnboardingCompleted()).isTrue()
    }

    @Test
    fun `이름만 있고 전화번호가 null이면 온보딩 미완료다`() {
        val member = member(name = "홍길동", phoneNumber = null, status = MemberStatus.PENDING)

        assertThat(member.isOnboardingCompleted()).isFalse()
    }

    @Test
    fun `이름이 빈 문자열이면 온보딩 미완료다`() {
        val member = member(name = "", phoneNumber = "01012345678", status = MemberStatus.PENDING)

        assertThat(member.isOnboardingCompleted()).isFalse()
    }

    @Test
    fun `상태가 INACTIVE이고 거절 사유가 있으면 거절된 회원이다`() {
        val member =
            member(
                name = "홍길동",
                phoneNumber = "01012345678",
                status = MemberStatus.INACTIVE,
                rejectionReason = "신청 정보 불일치",
            )

        assertThat(member.isRejected()).isTrue()
    }

    @Test
    fun `상태가 INACTIVE지만 거절 사유가 없으면 거절이 아니다`() {
        val member =
            member(
                name = "홍길동",
                phoneNumber = "01012345678",
                status = MemberStatus.INACTIVE,
                rejectionReason = null,
            )

        assertThat(member.isRejected()).isFalse()
    }

    @Test
    fun `상태가 PENDING이면 거절 사유가 있어도 거절이 아니다`() {
        val member =
            member(
                name = "홍길동",
                phoneNumber = "01012345678",
                status = MemberStatus.PENDING,
                rejectionReason = "신청 정보 불일치",
            )

        assertThat(member.isRejected()).isFalse()
    }

    private fun member(
        name: String?,
        phoneNumber: String?,
        status: MemberStatus,
        rejectionReason: String? = null,
    ): Member =
        Member(
            branch = branch(),
            name = name,
            phoneNumber = phoneNumber,
            status = status,
            kakaoId = 1L,
            rejectionReason = rejectionReason,
            createdAt = FIXED_TIME,
        )

    private fun branch(): Branch = Branch(name = "송파점")

    companion object {
        private val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-01T00:00:00+09:00")
    }
}
