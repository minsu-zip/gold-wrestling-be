package com.goldwrestling.pass

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 등록 취소 판정·상쇄 수량 산출 (PASS-08, D-059·D-065·D-072) — 순수 Kotlin 단위테스트.
 * 스프링 컨텍스트 없이 `Pass.resolveCancellationOffset` 엔티티 메서드만 검증한다 (add-domain-test §1).
 *
 * `resolveCancellationOffset`은 상태를 바꾸지 않는다(D-072) — 실제 상태 전환은
 * `PassRepository.cancelIfNotCanceled`의 조건부 UPDATE가 한다. 이 메서드는 판정과 원장에 남길
 * 상쇄 수량 산출만 한다. `isEqualTo` 대신 `isEqualByComparingTo`를 쓴다 — `-3.0 != -3.00`(D-016).
 */
class PassCancellationTest {
    @Test
    fun `잔여가 있는 횟수권을 취소하면 잔여만큼의 음수 상쇄 수량이 산출된다`() {
        val pass = sessionPass(remaining = "3.0")

        val offsetAmount = pass.resolveCancellationOffset()

        assertThat(offsetAmount).isEqualByComparingTo(BigDecimal("-3.0"))
    }

    @Test
    fun `잔여가 0인 이용권을 취소하면 상쇄 수량이 0이다`() {
        val pass = sessionPass(remaining = "0")

        val offsetAmount = pass.resolveCancellationOffset()

        assertThat(offsetAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `레슨권을 취소하면 잔여만큼의 음수 상쇄 수량이 산출된다`() {
        val pass = lessonPass(remaining = "0.5")

        val offsetAmount = pass.resolveCancellationOffset()

        assertThat(offsetAmount).isEqualByComparingTo(BigDecimal("-0.5"))
    }

    @Test
    fun `기간제 이용권을 취소하면 상쇄 수량이 0이다`() {
        val pass = eveningMembership()

        val offsetAmount = pass.resolveCancellationOffset()

        assertThat(offsetAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `취소 판정은 잔여 횟수를 바꾸지 않는다`() {
        val pass = sessionPass(remaining = "3.0")

        pass.resolveCancellationOffset()

        assertThat(pass.remainingCount).isEqualByComparingTo(BigDecimal("3.0"))
    }

    @Test
    fun `취소 판정은 상태를 바꾸지 않는다`() {
        val pass = sessionPass(remaining = "3.0")

        pass.resolveCancellationOffset()

        assertThat(pass.status).isEqualTo(PassStatus.ACTIVE)
    }

    @Test
    fun `이미 취소된 이용권은 재취소할 수 없다`() {
        val pass = sessionPass(remaining = "1.0", status = PassStatus.CANCELED)

        assertThatThrownBy { pass.resolveCancellationOffset() }
            .isInstanceOf(PassAlreadyCanceledException::class.java)
    }

    /** `Pass` 픽스처 공통부(회원·지점·등록 관리자·기간·생성 시각)를 모아, 타입별 팩토리가 차이만 넘긴다. */
    private fun pass(
        type: PassType,
        status: PassStatus,
        remainingCount: BigDecimal?,
    ): Pass =
        Pass(
            member = PassFixtures.member(),
            branch = PassFixtures.branch(),
            registeredBy = PassFixtures.admin(),
            type = type,
            status = status,
            startDate = FIXED_START,
            endDate = FIXED_START.plusYears(1),
            remainingCount = remainingCount,
            createdAt = PassFixtures.FIXED_TIME,
        )

    private fun eveningMembership(status: PassStatus = PassStatus.ACTIVE): Pass =
        pass(type = PassType.EVENING_MEMBERSHIP, status = status, remainingCount = null)

    private fun sessionPass(
        remaining: String,
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass = pass(type = PassType.SESSION_PASS, status = status, remainingCount = BigDecimal(remaining))

    private fun lessonPass(
        remaining: String,
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass = pass(type = PassType.LESSON_PASS, status = status, remainingCount = BigDecimal(remaining))

    private companion object {
        val FIXED_START: LocalDate = LocalDate.of(2026, 3, 1)
    }
}
