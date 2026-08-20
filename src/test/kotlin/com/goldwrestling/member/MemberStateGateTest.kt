package com.goldwrestling.member

import com.goldwrestling.auth.AuthenticatedPrincipal
import com.goldwrestling.auth.PrincipalType
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 회원 상태 게이트(policies §5 상태 표, §5.1 온보딩 규칙, D-040) — 순수 Kotlin 단위테스트.
 * `AuthenticatedPrincipal`을 직접 생성해 넘긴다. 스프링 컨텍스트는 필요 없다(add-domain-test §1).
 */
class MemberStateGateTest {
    private val gate = MemberStateGate()

    @Test
    fun `ACTIVE 회원에게 requireActive는 통과한다`() {
        gate.requireActive(memberPrincipal(status = MemberStatus.ACTIVE, onboardingCompleted = true))
    }

    @Test
    fun `PENDING 회원에게 requireActive는 MemberNotActiveException을 던진다`() {
        assertThatThrownBy { gate.requireActive(memberPrincipal(status = MemberStatus.PENDING, onboardingCompleted = false)) }
            .isInstanceOf(MemberNotActiveException::class.java)
    }

    @Test
    fun `ON_LEAVE 회원에게 requireActive는 MemberNotActiveException을 던진다`() {
        assertThatThrownBy { gate.requireActive(memberPrincipal(status = MemberStatus.ON_LEAVE, onboardingCompleted = true)) }
            .isInstanceOf(MemberNotActiveException::class.java)
    }

    @Test
    fun `INACTIVE 회원에게 requireActive는 MemberNotActiveException을 던진다`() {
        assertThatThrownBy { gate.requireActive(memberPrincipal(status = MemberStatus.INACTIVE, onboardingCompleted = true)) }
            .isInstanceOf(MemberNotActiveException::class.java)
    }

    @Test
    fun `관리자 principal에게 requireActive를 호출하면 IllegalStateException을 던진다`() {
        assertThatThrownBy { gate.requireActive(adminPrincipal()) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `PENDING이고 온보딩 미완료 회원에게 requireOnboardingAllowed는 통과한다`() {
        gate.requireOnboardingAllowed(memberPrincipal(status = MemberStatus.PENDING, onboardingCompleted = false))
    }

    @Test
    fun `온보딩 완료 회원에게 requireOnboardingAllowed는 OnboardingAlreadyCompletedException을 던진다`() {
        assertThatThrownBy {
            gate.requireOnboardingAllowed(memberPrincipal(status = MemberStatus.PENDING, onboardingCompleted = true))
        }.isInstanceOf(OnboardingAlreadyCompletedException::class.java)
    }

    @Test
    fun `PENDING이 아닌 회원에게 requireOnboardingAllowed는 MemberStateConflictException을 던진다`() {
        listOf(MemberStatus.ACTIVE, MemberStatus.ON_LEAVE, MemberStatus.INACTIVE).forEach { status ->
            assertThatThrownBy {
                gate.requireOnboardingAllowed(memberPrincipal(status = status, onboardingCompleted = false))
            }.isInstanceOf(MemberStateConflictException::class.java)
        }
    }

    @Test
    fun `온보딩 완료 검사가 상태 검사보다 먼저 수행된다`() {
        // ACTIVE + 온보딩 완료: 두 조건이 모두 위반이지만 OnboardingAlreadyCompletedException이 나와야 한다
        assertThatThrownBy {
            gate.requireOnboardingAllowed(memberPrincipal(status = MemberStatus.ACTIVE, onboardingCompleted = true))
        }.isInstanceOf(OnboardingAlreadyCompletedException::class.java)
    }

    private fun memberPrincipal(
        status: MemberStatus,
        onboardingCompleted: Boolean,
    ): AuthenticatedPrincipal =
        AuthenticatedPrincipal(
            principalType = PrincipalType.MEMBER,
            principalId = 1L,
            memberStatus = status,
            onboardingCompleted = onboardingCompleted,
        )

    private fun adminPrincipal(): AuthenticatedPrincipal =
        AuthenticatedPrincipal(
            principalType = PrincipalType.ADMIN,
            principalId = 1L,
            memberStatus = null,
            onboardingCompleted = true,
        )
}
