package com.goldwrestling.attendance

import com.goldwrestling.admin.Admin
import com.goldwrestling.branch.Branch
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassType
import com.goldwrestling.schedule.ClassType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 저녁반 0.5회 차감 판정(policies §4.2, D-091) — 순수 Kotlin 단위테스트.
 * 스프링 컨텍스트 없이 `EveningHalfDeductionPolicy` object의 판정만 검증한다(add-domain-test §1).
 * `candidates`는 `PassRepository.findDeductionCandidates`가 이미 만료 임박순으로 정렬해 내려준
 * 결과라고 가정한다 — 이 테스트는 정책이 그 순서를 다시 정렬하지 않는지만 검증한다.
 */
class EveningHalfDeductionPolicyTest {
    @Test
    fun `HALF_SESSION은 0-5다`() {
        assertThat(EveningHalfDeductionPolicy.HALF_SESSION).isEqualByComparingTo(BigDecimal("0.5"))
    }

    @Test
    fun `유효한 저녁반 회비가 있으면 차감하지 않는다`() {
        val anyCandidate = sessionPass(remaining = "1.0", endDate = FIXED_TODAY)

        val result =
            EveningHalfDeductionPolicy.resolveDeduction(
                hasValidMembership = true,
                candidates = listOf(anyCandidate),
            )

        assertThat(result).isNull()
    }

    @Test
    fun `회비가 없고 후보가 비어 있으면 저녁반 출석 추가를 거부한다`() {
        assertThatThrownBy {
            EveningHalfDeductionPolicy.resolveDeduction(hasValidMembership = false, candidates = emptyList())
        }.isInstanceOf(EveningAttendanceDeductionUnavailableException::class.java)
    }

    @Test
    fun `회비가 없으면 만료가 가장 임박한 SESSION_PASS 한 장에서 0-5회가 차감 대상으로 선택된다`() {
        val expiringSoon = sessionPass(remaining = "1.0", endDate = FIXED_TODAY.plusDays(10))
        val expiringLater = sessionPass(remaining = "1.0", endDate = FIXED_TODAY.plusDays(100))

        val result =
            EveningHalfDeductionPolicy.resolveDeduction(
                hasValidMembership = false,
                candidates = listOf(expiringSoon, expiringLater),
            )

        assertThat(result).isSameAs(expiringSoon)
    }

    @Test
    fun `잔여가 정확히 0-5인 장도 후보가 될 수 있다`() {
        val exactlyHalf = sessionPass(remaining = "0.5", endDate = FIXED_TODAY)

        val result =
            EveningHalfDeductionPolicy.resolveDeduction(
                hasValidMembership = false,
                candidates = listOf(exactlyHalf),
            )

        assertThat(result).isSameAs(exactlyHalf)
    }

    @Test
    fun `저녁반 세션이면 저녁반 전용 차감 처리를 통과한다`() {
        assertThatCode { EveningHalfDeductionPolicy.requireEveningSession(ClassType.EVENING) }
            .doesNotThrowAnyException()
    }

    @ParameterizedTest(name = "classType={0}")
    @EnumSource(value = ClassType::class, names = ["SESSION", "LESSON"])
    fun `저녁반이 아닌 수업 종류에는 저녁반 전용 차감 처리를 쓸 수 없다`(classType: ClassType) {
        assertThatThrownBy { EveningHalfDeductionPolicy.requireEveningSession(classType) }
            .isInstanceOf(AttendanceClassTypeMismatchException::class.java)
    }

    private fun sessionPass(
        remaining: String,
        endDate: LocalDate,
    ): Pass =
        Pass(
            member = member(),
            branch = branch(),
            registeredBy = admin(),
            type = PassType.SESSION_PASS,
            status = PassStatus.ACTIVE,
            startDate = FIXED_TODAY,
            endDate = endDate,
            remainingCount = BigDecimal(remaining),
            createdAt = FIXED_TIME,
        )

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

    companion object {
        private val FIXED_TIME: OffsetDateTime = OffsetDateTime.parse("2026-08-01T00:00:00+09:00")
        private val FIXED_TODAY: LocalDate = LocalDate.of(2026, 8, 1)
    }
}
