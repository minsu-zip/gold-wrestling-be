package com.goldwrestling.auth

import com.goldwrestling.member.MemberStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * [AuthenticatedPrincipal.requireAdminId]·[AuthenticatedPrincipal.requireMemberId]의 대칭 판정을
 * 순수 단위테스트로 검증한다(03-06 Task 1). 스프링 컨텍스트가 필요 없는 값 객체 로직이다.
 */
class AuthenticatedPrincipalTest {
    @Test
    fun `ADMIN 주체의 requireAdminId는 principalId를 반환한다`() {
        val principal = AuthenticatedPrincipal(PrincipalType.ADMIN, 7L, memberStatus = null, onboardingCompleted = true)

        assertThat(principal.requireAdminId()).isEqualTo(7L)
    }

    @Test
    fun `MEMBER 주체의 requireAdminId는 IllegalStateException을 던진다`() {
        val principal =
            AuthenticatedPrincipal(
                PrincipalType.MEMBER,
                7L,
                memberStatus = MemberStatus.ACTIVE,
                onboardingCompleted = true,
            )

        assertThatThrownBy { principal.requireAdminId() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `MEMBER 주체의 requireMemberId는 principalId를 반환한다`() {
        val principal =
            AuthenticatedPrincipal(
                PrincipalType.MEMBER,
                9L,
                memberStatus = MemberStatus.ACTIVE,
                onboardingCompleted = true,
            )

        assertThat(principal.requireMemberId()).isEqualTo(9L)
    }

    @Test
    fun `ADMIN 주체의 requireMemberId는 IllegalStateException을 던진다`() {
        val principal = AuthenticatedPrincipal(PrincipalType.ADMIN, 9L, memberStatus = null, onboardingCompleted = true)

        assertThatThrownBy { principal.requireMemberId() }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
