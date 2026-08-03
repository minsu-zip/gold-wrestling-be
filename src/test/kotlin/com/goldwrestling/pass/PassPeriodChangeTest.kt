package com.goldwrestling.pass

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 기간·유효기간 수정 판정 (PASS-04·PASS-07, D-062) — 순수 Kotlin 단위테스트.
 * 스프링 컨텍스트 없이 `Pass.changePeriod` 엔티티 메서드만 검증한다 (add-domain-test §1).
 *
 * `PassPeriodChange` 이력 행이 실제로 저장되는지는 03-08의 통합테스트가 본다 — 여기서는
 * "무엇을 반영·거부하는가"라는 엔티티 판정만 본다.
 */
class PassPeriodChangeTest {
    @Test
    fun `저녁반 회비는 시작일과 종료일을 모두 수정할 수 있다`() {
        val pass = eveningMembership(startDate = LocalDate.of(2026, 3, 1), endDate = LocalDate.of(2026, 4, 1))

        pass.changePeriod(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 5, 15))

        assertThat(pass.startDate).isEqualTo(LocalDate.of(2026, 3, 15))
        assertThat(pass.endDate).isEqualTo(LocalDate.of(2026, 5, 15))
    }

    @Test
    fun `횟수권은 종료일만 수정할 수 있고 시작일은 유지된다`() {
        val pass = sessionPass(startDate = LocalDate.of(2026, 3, 1), endDate = LocalDate.of(2027, 2, 28))

        pass.changePeriod(null, LocalDate.of(2027, 6, 30))

        assertThat(pass.startDate).isEqualTo(LocalDate.of(2026, 3, 1))
        assertThat(pass.endDate).isEqualTo(LocalDate.of(2027, 6, 30))
    }

    @Test
    fun `횟수권의 시작일을 기존과 다른 값으로 바꾸려 하면 거부된다`() {
        val pass = sessionPass(startDate = LocalDate.of(2026, 3, 1), endDate = LocalDate.of(2027, 2, 28))

        assertThatThrownBy { pass.changePeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2027, 2, 28)) }
            .isInstanceOf(InvalidPassPeriodException::class.java)
    }

    @Test
    fun `횟수권 시작일을 기존과 같은 값으로 재전송하면 허용된다`() {
        val pass = sessionPass(startDate = LocalDate.of(2026, 3, 1), endDate = LocalDate.of(2027, 2, 28))

        assertThatCode { pass.changePeriod(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 6, 30)) }
            .doesNotThrowAnyException()
        assertThat(pass.endDate).isEqualTo(LocalDate.of(2027, 6, 30))
    }

    @Test
    fun `종료일이 시작일보다 앞서는 기간 수정은 거부된다`() {
        val pass = eveningMembership(startDate = LocalDate.of(2026, 3, 1), endDate = LocalDate.of(2026, 4, 1))

        assertThatThrownBy { pass.changePeriod(null, LocalDate.of(2026, 2, 1)) }
            .isInstanceOf(InvalidPassPeriodException::class.java)
    }

    @Test
    fun `만료된 횟수권의 유효기간도 연장할 수 있다`() {
        val pass = sessionPass(startDate = LocalDate.of(2019, 1, 1), endDate = LocalDate.of(2020, 1, 1))

        assertThatCode { pass.changePeriod(null, LocalDate.of(2027, 12, 31)) }
            .doesNotThrowAnyException()
        assertThat(pass.endDate).isEqualTo(LocalDate.of(2027, 12, 31))
    }

    @Test
    fun `취소된 이용권은 기간을 수정할 수 없다`() {
        val pass =
            sessionPass(
                startDate = LocalDate.of(2026, 3, 1),
                endDate = LocalDate.of(2027, 2, 28),
                status = PassStatus.CANCELED,
            )

        assertThatThrownBy { pass.changePeriod(null, LocalDate.of(2027, 6, 30)) }
            .isInstanceOf(PassAlreadyCanceledException::class.java)
    }

    /** `Pass` 픽스처 공통부(회원·지점·등록 관리자·생성 시각)를 모아, 타입별 팩토리가 차이만 넘긴다. */
    private fun pass(
        type: PassType,
        status: PassStatus,
        startDate: LocalDate,
        endDate: LocalDate,
        remainingCount: BigDecimal?,
    ): Pass =
        Pass(
            member = PassFixtures.member(),
            branch = PassFixtures.branch(),
            registeredBy = PassFixtures.admin(),
            type = type,
            status = status,
            startDate = startDate,
            endDate = endDate,
            remainingCount = remainingCount,
            createdAt = PassFixtures.FIXED_TIME,
        )

    private fun eveningMembership(
        startDate: LocalDate,
        endDate: LocalDate,
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass =
        pass(
            type = PassType.EVENING_MEMBERSHIP,
            status = status,
            startDate = startDate,
            endDate = endDate,
            remainingCount = null,
        )

    private fun sessionPass(
        startDate: LocalDate,
        endDate: LocalDate,
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass =
        pass(
            type = PassType.SESSION_PASS,
            status = status,
            startDate = startDate,
            endDate = endDate,
            remainingCount = BigDecimal("3.0"),
        )
}
