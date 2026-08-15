package com.goldwrestling.batch

import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassRepository
import com.goldwrestling.pass.PassTransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * `InactivityDeductionService.deductOnce`가 조건부 UPDATE 경쟁에서 졌을 때(D-021, T-05-15)
 * 이력을 저장하지 않고 스킵함을 스프링 컨텍스트 없이 검증한다 — 순수 Mockito 단위테스트라
 * 밀리초 단위로 끝난다(add-domain-test §2 종류 구분).
 */
class InactivityDeductionRaceTest {
    @Test
    fun `조건부 UPDATE가 0행이면 false를 반환하고 PassTransaction을 저장하지 않는다`() {
        val passRepository = mock(PassRepository::class.java)
        val passTransactionRepository = mock(PassTransactionRepository::class.java)
        // 2026-08-02T01:00:00Z == 2026-08-02T10:00:00+09:00(Asia/Seoul) → LocalDate.now(clock) == 2026-08-02.
        val clock = Clock.fixed(Instant.parse("2026-08-02T01:00:00Z"), ZoneId.of("Asia/Seoul"))
        val service = InactivityDeductionService(passRepository, passTransactionRepository, clock)
        val memberId = 1L
        val passId = 1L
        val today = LocalDate.of(2026, 8, 2)

        val candidate = mock(Pass::class.java)
        given(candidate.id).willReturn(passId)
        given(candidate.remainingCount).willReturn(BigDecimal("1.0"))
        // Kotlin이 non-null로 선언한 PassRepository 파라미터에는 null을 반환하는 any()/eq() 매처를
        // 쓸 수 없다(Mockito 5가 Kotlin 메타데이터로 이를 검출해 NPE를 던진다) — 매처 없이 실제 값을
        // 그대로 넘긴다(인자 전체에 매처를 안 쓰면 Mockito가 각 인자를 equals로 그대로 매칭한다).
        given(passRepository.findDeductibleSessionPasses(memberId, today)).willReturn(listOf(candidate))
        // 사전 후보 조회 이후 다른 트랜잭션이 먼저 잔여를 바꿔 조건부 UPDATE가 0행을 반환하는 경쟁 상황.
        given(passRepository.adjustRemainingCount(passId, BigDecimal("-1.0"))).willReturn(0)

        val result = service.deductOnce(memberId = memberId)

        assertThat(result).isFalse()
        verify(passTransactionRepository, never()).save(any())
    }
}
