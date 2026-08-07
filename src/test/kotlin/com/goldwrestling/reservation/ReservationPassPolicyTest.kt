package com.goldwrestling.reservation

import com.goldwrestling.admin.Admin
import com.goldwrestling.branch.Branch
import com.goldwrestling.member.Member
import com.goldwrestling.member.MemberStatus
import com.goldwrestling.pass.InsufficientPassCountException
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassStatus
import com.goldwrestling.pass.PassType
import com.goldwrestling.schedule.ClassSessionNotReservableException
import com.goldwrestling.schedule.ClassType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 예약이 어느 이용권에서 차감할지 고르는 규칙(D-091) — 순수 Kotlin 단위테스트.
 * 스프링 컨텍스트 없이 `ReservationPassPolicy` object의 판정만 검증한다(add-domain-test §1).
 */
class ReservationPassPolicyTest {
    @Test
    fun `SESSION 예약은 SESSION_PASS를 요구한다`() {
        assertThat(ReservationPassPolicy.requiredPassType(ClassType.SESSION)).isEqualTo(PassType.SESSION_PASS)
    }

    @Test
    fun `LESSON 예약은 LESSON_PASS를 요구한다`() {
        assertThat(ReservationPassPolicy.requiredPassType(ClassType.LESSON)).isEqualTo(PassType.LESSON_PASS)
    }

    @Test
    fun `EVENING은 예약 대상이 아니므로 이용권 매핑을 거부한다`() {
        assertThatThrownBy { ReservationPassPolicy.requiredPassType(ClassType.EVENING) }
            .isInstanceOf(ClassSessionNotReservableException::class.java)
    }

    @Test
    fun `후보가 비어 있으면 잔여 부족 예외를 던진다`() {
        assertThatThrownBy { ReservationPassPolicy.selectCandidate(emptyList()) }
            .isInstanceOf(InsufficientPassCountException::class.java)
    }

    @Test
    fun `후보가 있으면 쿼리가 이미 정렬한 첫 번째를 그대로 고른다 — 서비스가 다시 정렬하지 않는다`() {
        val first = sessionPass(remaining = "1.0")
        val second = sessionPass(remaining = "2.0")

        val selected = ReservationPassPolicy.selectCandidate(listOf(first, second))

        assertThat(selected).isSameAs(first)
    }

    private fun sessionPass(remaining: String): Pass =
        Pass(
            member = member(),
            branch = branch(),
            registeredBy = admin(),
            type = PassType.SESSION_PASS,
            status = PassStatus.ACTIVE,
            startDate = FIXED_TODAY,
            endDate = FIXED_TODAY.plusYears(1).minusDays(1),
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
