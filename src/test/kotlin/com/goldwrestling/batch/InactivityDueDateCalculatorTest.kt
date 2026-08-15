package com.goldwrestling.batch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 2주 미사용 자동 차감(policies §4.3, D-105·D-106)의 판정 순수 함수 — 스프링 컨텍스트 없이
 * `InactivityDueDateCalculator` object의 계산만 검증한다(add-domain-test §1).
 */
class InactivityDueDateCalculatorTest {
    // ── resolveDueDate: 기준일 5종 후보의 max (D-105) ──────────────────────────

    @Test
    fun `후보가 5개 전부 null이면 기준일이 없다`() {
        val dueDate = InactivityDueDateCalculator.resolveDueDate(candidates())

        assertThat(dueDate).isNull()
    }

    @Test
    fun `출석일이 없어도 나머지 4개 후보 중 가장 최근 날짜를 기준일로 삼는다`() {
        val dueDate =
            InactivityDueDateCalculator.resolveDueDate(
                candidates(
                    lastAttendanceDate = null,
                    lastActiveReservationClassDate = LocalDate.of(2026, 7, 1),
                    returnedFromLeaveDate = LocalDate.of(2026, 7, 10),
                    lastSessionPassRegistrationDate = LocalDate.of(2026, 6, 1),
                    lastPositiveAdjustDate = LocalDate.of(2026, 5, 1),
                ),
            )

        assertThat(dueDate).isEqualTo(LocalDate.of(2026, 7, 10))
    }

    @Test
    fun `휴회에서 복귀하면 복귀일이 기준일이 된다`() {
        val dueDate =
            InactivityDueDateCalculator.resolveDueDate(
                candidates(
                    lastActiveReservationClassDate = LocalDate.of(2026, 1, 1),
                    returnedFromLeaveDate = LocalDate.of(2026, 8, 1),
                    lastSessionPassRegistrationDate = LocalDate.of(2026, 2, 1),
                ),
            )

        assertThat(dueDate).isEqualTo(LocalDate.of(2026, 8, 1))
    }

    @Test
    fun `양의 수동 가감이 가장 최근이면 가감일이 기준일이 된다`() {
        val dueDate =
            InactivityDueDateCalculator.resolveDueDate(
                candidates(
                    lastActiveReservationClassDate = LocalDate.of(2026, 1, 1),
                    lastSessionPassRegistrationDate = LocalDate.of(2026, 2, 1),
                    lastPositiveAdjustDate = LocalDate.of(2026, 8, 1),
                ),
            )

        assertThat(dueDate).isEqualTo(LocalDate.of(2026, 8, 1))
    }

    @Test
    fun `예약·복귀·가감 이력이 없는 신규 회원은 등록일이 기준일이 된다`() {
        val dueDate =
            InactivityDueDateCalculator.resolveDueDate(
                candidates(lastSessionPassRegistrationDate = LocalDate.of(2026, 8, 1)),
            )

        assertThat(dueDate).isEqualTo(LocalDate.of(2026, 8, 1))
    }

    @Test
    fun `같은 날짜가 두 후보에 있으면 그 날짜가 기준일이다`() {
        val sameDate = LocalDate.of(2026, 8, 1)
        val dueDate =
            InactivityDueDateCalculator.resolveDueDate(
                candidates(
                    lastActiveReservationClassDate = sameDate,
                    lastSessionPassRegistrationDate = sameDate,
                ),
            )

        assertThat(dueDate).isEqualTo(sameDate)
    }

    private fun candidates(
        lastAttendanceDate: LocalDate? = null,
        lastActiveReservationClassDate: LocalDate? = null,
        returnedFromLeaveDate: LocalDate? = null,
        lastSessionPassRegistrationDate: LocalDate? = null,
        lastPositiveAdjustDate: LocalDate? = null,
    ): InactivityDueDateCandidates =
        InactivityDueDateCandidates(
            lastAttendanceDate = lastAttendanceDate,
            lastActiveReservationClassDate = lastActiveReservationClassDate,
            returnedFromLeaveDate = returnedFromLeaveDate,
            lastSessionPassRegistrationDate = lastSessionPassRegistrationDate,
            lastPositiveAdjustDate = lastPositiveAdjustDate,
        )
}
