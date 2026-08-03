package com.goldwrestling.pass

import com.goldwrestling.admin.Admin
import com.goldwrestling.branch.Branch
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberStatus
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 관리자 수동 가감 허용 판정 (policies §4.2a, D-056) — 순수 Kotlin 단위테스트.
 * 스프링 컨텍스트 없이 `Pass.validateAdjustment` 엔티티 메서드만 검증한다 (add-domain-test §1).
 *
 * `validateAdjustment`는 판정만 하고 잔여를 바꾸지 않는다 — 실제 반영은
 * `PassRepository.adjustRemainingCount`(조건부 UPDATE)가 한다.
 */
class PassAdjustmentPolicyTest {
    @Test
    fun `기간제 이용권은 횟수 가감 대상이 아니다`() {
        val pass = eveningMembership()

        assertThatThrownBy { pass.validateAdjustment(BigDecimal("0.5")) }
            .isInstanceOf(PassTypeNotAdjustableException::class.java)
    }

    @Test
    fun `가감 단위는 0dot5가 아니면 거부된다`() {
        val pass = sessionPass(remaining = "2.0")

        assertThatThrownBy { pass.validateAdjustment(BigDecimal("0.3")) }
            .isInstanceOf(InvalidAdjustmentUnitException::class.java)
    }

    @Test
    fun `가감 수량이 0이면 거부된다`() {
        val pass = sessionPass(remaining = "2.0")

        assertThatThrownBy { pass.validateAdjustment(BigDecimal("0")) }
            .isInstanceOf(InvalidAdjustmentUnitException::class.java)
    }

    @Test
    fun `결과 잔여가 음수가 되는 가감은 거부된다`() {
        val pass = sessionPass(remaining = "0.5")

        assertThatThrownBy { pass.validateAdjustment(BigDecimal("-1.0")) }
            .isInstanceOf(InsufficientPassCountException::class.java)
    }

    @Test
    fun `결과 잔여가 정확히 0이 되는 가감은 허용된다`() {
        val pass = sessionPass(remaining = "1.0")

        assertThatCode { pass.validateAdjustment(BigDecimal("-1.0")) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `유효기간이 만료된 횟수권에도 가감할 수 있다`() {
        val pass = sessionPass(remaining = "1.0", endDate = LocalDate.of(2020, 1, 1))

        assertThatCode { pass.validateAdjustment(BigDecimal("0.5")) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `취소된 이용권에는 가감할 수 없다`() {
        val pass = lessonPass(remaining = "1.0", status = PassStatus.CANCELED)

        assertThatThrownBy { pass.validateAdjustment(BigDecimal("0.5")) }
            .isInstanceOf(PassAlreadyCanceledException::class.java)
    }

    @Test
    fun `스케일이 달라도 0dot5의 배수면 허용된다`() {
        val pass = sessionPass(remaining = "2.0")

        assertThatCode { pass.validateAdjustment(BigDecimal("0.50")) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `취소 여부는 이용권 종류·가감 단위보다 먼저 판정된다`() {
        val pass = eveningMembership(status = PassStatus.CANCELED)

        assertThatThrownBy { pass.validateAdjustment(BigDecimal("0.3")) }
            .isInstanceOf(PassAlreadyCanceledException::class.java)
    }

    private fun branch(): Branch = Branch(name = "송파점")

    private fun member(): Member =
        Member(
            branch = branch(),
            name = "홍길동",
            phoneNumber = "01012345678",
            status = MemberStatus.ACTIVE,
            kakaoId = 1L,
            createdAt = FIXED_TIME,
        )

    private fun admin(): Admin =
        Admin(
            name = "관리자",
            loginId = "admin1",
            passwordHash = "hash",
            createdAt = FIXED_TIME,
        )

    private fun eveningMembership(status: PassStatus = PassStatus.ACTIVE): Pass =
        Pass(
            member = member(),
            branch = branch(),
            registeredBy = admin(),
            type = PassType.EVENING_MEMBERSHIP,
            status = status,
            startDate = FIXED_TODAY,
            endDate = FIXED_TODAY.plusMonths(1),
            remainingCount = null,
            createdAt = FIXED_TIME,
        )

    private fun sessionPass(
        remaining: String,
        endDate: LocalDate = FIXED_TODAY.plusYears(1),
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass =
        Pass(
            member = member(),
            branch = branch(),
            registeredBy = admin(),
            type = PassType.SESSION_PASS,
            status = status,
            startDate = FIXED_TODAY,
            endDate = endDate,
            remainingCount = BigDecimal(remaining),
            createdAt = FIXED_TIME,
        )

    private fun lessonPass(
        remaining: String,
        endDate: LocalDate = FIXED_TODAY.plusYears(1),
        status: PassStatus = PassStatus.ACTIVE,
    ): Pass =
        Pass(
            member = member(),
            branch = branch(),
            registeredBy = admin(),
            type = PassType.LESSON_PASS,
            status = status,
            startDate = FIXED_TODAY,
            endDate = endDate,
            remainingCount = BigDecimal(remaining),
            createdAt = FIXED_TIME,
        )

    companion object {
        private val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-01T00:00:00+09:00")
        private val FIXED_TODAY: LocalDate = LocalDate.of(2026, 8, 1)
    }
}
