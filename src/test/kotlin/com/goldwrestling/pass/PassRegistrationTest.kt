package com.goldwrestling.pass

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 이용권 등록 시 기간 계산·입력 규칙 (policies §1, D-055, D-066) — 순수 Kotlin 단위테스트.
 * 스프링 컨텍스트 없이 `Pass.register` 팩토리만 검증한다 (add-domain-test §1).
 */
class PassRegistrationTest {
    @Test
    fun `횟수권 유효기간은 시작일 기준 1년으로 자동 계산된다`() {
        val pass = registerSessionPass(startDate = LocalDate.of(2026, 3, 1), initialCount = BigDecimal("10.0"))

        assertThat(pass.endDate).isEqualTo(LocalDate.of(2027, 2, 28))
        assertThat(pass.remainingCount).isEqualByComparingTo(BigDecimal("10.0"))
        assertThat(pass.status).isEqualTo(PassStatus.ACTIVE)
    }

    @Test
    fun `레슨권 유효기간도 횟수권과 동일하게 시작일 기준 1년으로 계산된다`() {
        val pass =
            Pass.register(
                member = PassFixtures.member(),
                branch = PassFixtures.branch(),
                type = PassType.LESSON_PASS,
                startDate = LocalDate.of(2026, 3, 1),
                term = null,
                initialCount = BigDecimal("5.0"),
                registeredBy = PassFixtures.admin(),
                now = PassFixtures.FIXED_TIME,
            )

        assertThat(pass.endDate).isEqualTo(LocalDate.of(2027, 2, 28))
        assertThat(pass.remainingCount).isEqualByComparingTo(BigDecimal("5.0"))
    }

    @Test
    fun `저녁반 회비 1개월은 시작일 기준 1개월 뒤 하루 전이 종료일이다`() {
        val pass = registerEveningMembership(startDate = LocalDate.of(2026, 3, 1), term = EveningMembershipTerm.ONE_MONTH)

        assertThat(pass.endDate).isEqualTo(LocalDate.of(2026, 3, 31))
        assertThat(pass.remainingCount).isNull()
    }

    @Test
    fun `저녁반 회비 3개월과 6개월은 각각 개월 수만큼 종료일이 계산된다`() {
        val threeMonths =
            registerEveningMembership(startDate = LocalDate.of(2026, 3, 1), term = EveningMembershipTerm.THREE_MONTHS)
        val sixMonths =
            registerEveningMembership(startDate = LocalDate.of(2026, 3, 1), term = EveningMembershipTerm.SIX_MONTHS)

        assertThat(threeMonths.endDate).isEqualTo(LocalDate.of(2026, 5, 31))
        assertThat(sixMonths.endDate).isEqualTo(LocalDate.of(2026, 8, 31))
    }

    @Test
    fun `과거 시작일로도 이용권을 등록할 수 있다`() {
        assertThatCode {
            registerSessionPass(startDate = LocalDate.of(2025, 1, 1), initialCount = BigDecimal("1.0"))
        }.doesNotThrowAnyException()
    }

    @Test
    fun `횟수권 초기 횟수가 0dot5의 배수가 아니면 등록이 거부된다`() {
        assertThatThrownBy {
            registerSessionPass(initialCount = BigDecimal("0.3"))
        }.isInstanceOf(InvalidAdjustmentUnitException::class.java)
    }

    @Test
    fun `횟수권 초기 횟수가 0이거나 음수면 등록이 거부된다`() {
        assertThatThrownBy {
            registerSessionPass(initialCount = BigDecimal.ZERO)
        }.isInstanceOf(InvalidAdjustmentUnitException::class.java)

        assertThatThrownBy {
            registerSessionPass(initialCount = BigDecimal("-1.0"))
        }.isInstanceOf(InvalidAdjustmentUnitException::class.java)
    }

    @Test
    fun `기간제 이용권에 초기 횟수를 지정하면 등록이 거부된다`() {
        assertThatThrownBy {
            Pass.register(
                member = PassFixtures.member(),
                branch = PassFixtures.branch(),
                type = PassType.EVENING_MEMBERSHIP,
                startDate = PassFixtures.FIXED_TODAY,
                term = EveningMembershipTerm.ONE_MONTH,
                initialCount = BigDecimal("5.0"),
                registeredBy = PassFixtures.admin(),
                now = PassFixtures.FIXED_TIME,
            )
        }.isInstanceOf(PassTypeNotAdjustableException::class.java)
    }

    @Test
    fun `기간제 이용권은 기간을 지정하지 않으면 등록이 거부된다`() {
        assertThatThrownBy {
            Pass.register(
                member = PassFixtures.member(),
                branch = PassFixtures.branch(),
                type = PassType.EVENING_MEMBERSHIP,
                startDate = PassFixtures.FIXED_TODAY,
                term = null,
                initialCount = null,
                registeredBy = PassFixtures.admin(),
                now = PassFixtures.FIXED_TIME,
            )
        }.isInstanceOf(InvalidPassPeriodException::class.java)
    }

    @Test
    fun `횟수권에 회비 기간 단위를 지정하면 등록이 거부된다`() {
        assertThatThrownBy {
            Pass.register(
                member = PassFixtures.member(),
                branch = PassFixtures.branch(),
                type = PassType.SESSION_PASS,
                startDate = PassFixtures.FIXED_TODAY,
                term = EveningMembershipTerm.ONE_MONTH,
                initialCount = BigDecimal("5.0"),
                registeredBy = PassFixtures.admin(),
                now = PassFixtures.FIXED_TIME,
            )
        }.isInstanceOf(InvalidPassPeriodException::class.java)
    }

    @Test
    fun `등록 직후에는 사용 가능 상태이고 취소 관련 필드가 모두 비어 있다`() {
        val pass = registerSessionPass(initialCount = BigDecimal("3.0"))

        assertThat(pass.status).isEqualTo(PassStatus.ACTIVE)
        assertThat(pass.canceledAt).isNull()
        assertThat(pass.cancelReason).isNull()
        assertThat(pass.canceledBy).isNull()
    }

    private fun registerSessionPass(
        startDate: LocalDate = PassFixtures.FIXED_TODAY,
        initialCount: BigDecimal?,
    ): Pass =
        Pass.register(
            member = PassFixtures.member(),
            branch = PassFixtures.branch(),
            type = PassType.SESSION_PASS,
            startDate = startDate,
            term = null,
            initialCount = initialCount,
            registeredBy = PassFixtures.admin(),
            now = PassFixtures.FIXED_TIME,
        )

    private fun registerEveningMembership(
        startDate: LocalDate = PassFixtures.FIXED_TODAY,
        term: EveningMembershipTerm?,
    ): Pass =
        Pass.register(
            member = PassFixtures.member(),
            branch = PassFixtures.branch(),
            type = PassType.EVENING_MEMBERSHIP,
            startDate = startDate,
            term = term,
            initialCount = null,
            registeredBy = PassFixtures.admin(),
            now = PassFixtures.FIXED_TIME,
        )
}
