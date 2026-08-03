package com.goldwrestling.pass

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 이용권 표시 상태 계산 (policies §1, D-064, D-066) — 순수 Kotlin 단위테스트.
 * 스프링 컨텍스트 없이 `Pass.displayStatus` 엔티티 메서드만 검증한다 (add-domain-test §1).
 * 우선순위: 취소 → 만료 → 소진 → 사용가능.
 */
class PassDisplayStatusTest {
    @Test
    fun `잔여가 남아 있고 기간 내여도 취소된 이용권은 취소 상태로 표시된다`() {
        val pass =
            sessionPass(
                status = PassStatus.CANCELED,
                remaining = "5.0",
                endDate = PassFixtures.FIXED_TODAY.plusMonths(1),
            )

        assertThat(pass.displayStatus(PassFixtures.FIXED_TODAY)).isEqualTo(PassDisplayStatus.CANCELED)
    }

    @Test
    fun `종료일이 지나면 잔여가 남아 있어도 만료 상태로 표시된다`() {
        val pass = sessionPass(remaining = "5.0", endDate = PassFixtures.FIXED_TODAY.minusDays(1))

        assertThat(pass.displayStatus(PassFixtures.FIXED_TODAY)).isEqualTo(PassDisplayStatus.EXPIRED)
    }

    @Test
    fun `종료일 당일까지는 사용 가능 상태로 표시된다`() {
        val pass = sessionPass(remaining = "5.0", endDate = PassFixtures.FIXED_TODAY)

        assertThat(pass.displayStatus(PassFixtures.FIXED_TODAY)).isEqualTo(PassDisplayStatus.USABLE)
    }

    @Test
    fun `횟수권 잔여가 0이면 기간 내여도 소진 상태로 표시된다`() {
        val pass = sessionPass(remaining = "0", endDate = PassFixtures.FIXED_TODAY.plusMonths(1))

        assertThat(pass.displayStatus(PassFixtures.FIXED_TODAY)).isEqualTo(PassDisplayStatus.EXHAUSTED)
    }

    @Test
    fun `잔여가 0이어도 종료일이 지났으면 만료가 소진보다 우선한다`() {
        val pass = sessionPass(remaining = "0", endDate = PassFixtures.FIXED_TODAY.minusDays(1))

        assertThat(pass.displayStatus(PassFixtures.FIXED_TODAY)).isEqualTo(PassDisplayStatus.EXPIRED)
    }

    @Test
    fun `저녁반 회비는 기간 내면 소진 상태가 될 수 없이 사용 가능으로 표시된다`() {
        val pass = eveningMembership(endDate = PassFixtures.FIXED_TODAY.plusMonths(1))

        assertThat(pass.displayStatus(PassFixtures.FIXED_TODAY)).isEqualTo(PassDisplayStatus.USABLE)
    }

    @Test
    fun `횟수권 잔여가 남아 있고 기간 내면 사용 가능 상태로 표시된다`() {
        val pass = sessionPass(remaining = "2.0", endDate = PassFixtures.FIXED_TODAY.plusMonths(1))

        assertThat(pass.displayStatus(PassFixtures.FIXED_TODAY)).isEqualTo(PassDisplayStatus.USABLE)
    }

    /** `Pass` 픽스처 공통부(회원·지점·등록 관리자·시작일·생성 시각)를 모아, 타입별 팩토리가 차이만 넘긴다. */
    private fun pass(
        type: PassType,
        status: PassStatus,
        endDate: LocalDate,
        remainingCount: BigDecimal?,
    ): Pass =
        Pass(
            member = PassFixtures.member(),
            branch = PassFixtures.branch(),
            registeredBy = PassFixtures.admin(),
            type = type,
            status = status,
            startDate = PassFixtures.FIXED_TODAY,
            endDate = endDate,
            remainingCount = remainingCount,
            createdAt = PassFixtures.FIXED_TIME,
        )

    private fun sessionPass(
        remaining: String,
        endDate: LocalDate,
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass = pass(type = PassType.SESSION_PASS, status = status, endDate = endDate, remainingCount = BigDecimal(remaining))

    private fun eveningMembership(
        endDate: LocalDate,
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass = pass(type = PassType.EVENING_MEMBERSHIP, status = status, endDate = endDate, remainingCount = null)
}
