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

    // ── expectedDeductionCount: 2주 경과마다 1회 반복 (policies §4.3) ──────────

    @Test
    fun `경과 0일이면 존재해야 할 차감 수는 0이다`() {
        assertThat(InactivityDueDateCalculator.expectedDeductionCount(DUE_DATE, DUE_DATE)).isEqualTo(0)
    }

    @Test
    fun `경과 13일이면 존재해야 할 차감 수는 0이다`() {
        assertThat(InactivityDueDateCalculator.expectedDeductionCount(DUE_DATE, DUE_DATE.plusDays(13))).isEqualTo(0)
    }

    @Test
    fun `경과 14일이면 1회 차감이 존재해야 한다`() {
        assertThat(InactivityDueDateCalculator.expectedDeductionCount(DUE_DATE, DUE_DATE.plusDays(14))).isEqualTo(1)
    }

    @Test
    fun `경과 27일이면 존재해야 할 차감 수는 여전히 1이다`() {
        assertThat(InactivityDueDateCalculator.expectedDeductionCount(DUE_DATE, DUE_DATE.plusDays(27))).isEqualTo(1)
    }

    @Test
    fun `경과 28일이면 2회 차감이 존재해야 한다`() {
        assertThat(InactivityDueDateCalculator.expectedDeductionCount(DUE_DATE, DUE_DATE.plusDays(28))).isEqualTo(2)
    }

    @Test
    fun `경과 41일이면 존재해야 할 차감 수는 여전히 2다`() {
        assertThat(InactivityDueDateCalculator.expectedDeductionCount(DUE_DATE, DUE_DATE.plusDays(41))).isEqualTo(2)
    }

    @Test
    fun `경과 42일이면 3회 차감이 존재해야 한다`() {
        assertThat(InactivityDueDateCalculator.expectedDeductionCount(DUE_DATE, DUE_DATE.plusDays(42))).isEqualTo(3)
    }

    @Test
    fun `기준일이 오늘보다 미래면 존재해야 할 차감 수는 0이다 — 미래 수업일 예약이 음수 차감을 만들지 않는다`() {
        assertThat(InactivityDueDateCalculator.expectedDeductionCount(DUE_DATE, DUE_DATE.minusDays(1))).isEqualTo(0)
    }

    // ── shortfall: 상태 기반 부족분 = 멱등 + 캐치업 (D-106) ────────────────────

    @Test
    fun `경과 14일에 이력이 없으면 부족분은 1이다`() {
        val shortfall =
            InactivityDueDateCalculator.shortfall(DUE_DATE, DUE_DATE.plusDays(14), inactivityEventDates = emptyList())

        assertThat(shortfall).isEqualTo(1)
    }

    @Test
    fun `경과 14일에 기준일 이후 이력이 1건 있으면 부족분은 0이다 — 같은 날 두 번째 실행이 이중 차감하지 않는다`() {
        val shortfall =
            InactivityDueDateCalculator.shortfall(
                DUE_DATE,
                DUE_DATE.plusDays(14),
                inactivityEventDates = listOf(DUE_DATE.plusDays(14)),
            )

        assertThat(shortfall).isEqualTo(0)
    }

    @Test
    fun `경과 42일에 이력이 없으면 부족분은 3이다 — 배치가 6주 밀려도 밀린 주기를 몰아서 차감한다`() {
        val shortfall =
            InactivityDueDateCalculator.shortfall(DUE_DATE, DUE_DATE.plusDays(42), inactivityEventDates = emptyList())

        assertThat(shortfall).isEqualTo(3)
    }

    @Test
    fun `경과 42일에 이력이 1건 있으면 부족분은 2다`() {
        val shortfall =
            InactivityDueDateCalculator.shortfall(
                DUE_DATE,
                DUE_DATE.plusDays(42),
                inactivityEventDates = listOf(DUE_DATE.plusDays(14)),
            )

        assertThat(shortfall).isEqualTo(2)
    }

    @Test
    fun `이력 날짜가 기준일보다 이전이면 세지 않는다 — 기준일이 리셋되면 그 전 이력은 무관하다`() {
        val shortfall =
            InactivityDueDateCalculator.shortfall(
                DUE_DATE,
                DUE_DATE.plusDays(14),
                inactivityEventDates = listOf(DUE_DATE.minusDays(1)),
            )

        assertThat(shortfall).isEqualTo(1)
    }

    @Test
    fun `이력 날짜가 기준일 당일이면 센다`() {
        val shortfall =
            InactivityDueDateCalculator.shortfall(
                DUE_DATE,
                DUE_DATE.plusDays(14),
                inactivityEventDates = listOf(DUE_DATE),
            )

        assertThat(shortfall).isEqualTo(0)
    }

    @Test
    fun `이력이 기대 횟수보다 많으면 부족분은 음수가 아니라 0을 반환한다`() {
        val shortfall =
            InactivityDueDateCalculator.shortfall(
                DUE_DATE,
                DUE_DATE.plusDays(14),
                inactivityEventDates = listOf(DUE_DATE.plusDays(14), DUE_DATE.plusDays(20)),
            )

        assertThat(shortfall).isEqualTo(0)
    }

    @Test
    fun `이력 리스트가 비어 있고 경과 13일이면 부족분은 0이다`() {
        val shortfall =
            InactivityDueDateCalculator.shortfall(DUE_DATE, DUE_DATE.plusDays(13), inactivityEventDates = emptyList())

        assertThat(shortfall).isEqualTo(0)
    }

    companion object {
        private val DUE_DATE: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
