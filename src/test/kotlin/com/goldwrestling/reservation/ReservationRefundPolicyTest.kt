package com.goldwrestling.reservation

import com.goldwrestling.pass.PassStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 취소 시 이용권 복구 수행 여부 판정(D-091, Pitfall 2) — 순수 Kotlin 단위테스트.
 * 스프링 컨텍스트 없이 `ReservationRefundPolicy` object의 판정만 검증한다(add-domain-test §1).
 *
 * `shouldRestore`는 호출 **전에** 복구 여부를 결정하는 순수 판정이다 — 호출부가
 * `PassRepository.adjustRemainingCount` 반환값 0을 사후에 "경쟁 패배"와 "복구 안 함 정책"으로
 * 구분하려 하지 않도록, 그 분기를 여기서 미리 끝내 둔다.
 */
class ReservationRefundPolicyTest {
    @Test
    fun `복구 요청이 없으면 이용권 상태와 무관하게 복구하지 않는다`() {
        val result = ReservationRefundPolicy.shouldRestore(PassStatus.ACTIVE, refundRequested = false)

        assertThat(result).isFalse()
    }

    @Test
    fun `등록 취소된 이용권은 복구 요청이 있어도 복구하지 않는다`() {
        val result = ReservationRefundPolicy.shouldRestore(PassStatus.CANCELED, refundRequested = true)

        assertThat(result).isFalse()
    }

    @Test
    fun `정상 이용권은 복구 요청이 있으면 복구한다`() {
        val result = ReservationRefundPolicy.shouldRestore(PassStatus.ACTIVE, refundRequested = true)

        assertThat(result).isTrue()
    }

    @Test
    fun `유효기간이 지난 이용권도 ACTIVE 상태면 복구 대상이다 — endDate를 보지 않는다`() {
        // shouldRestore는 만료 여부를 판정 대상으로 삼지 않는다 — 파라미터에 날짜 타입이 없다는
        // 것 자체가 구조적 증거다. "빌려간 것을 돌려놓는 것"(D-091)이라 만료된 이용권도 정상
        // 이용권과 동일하게 true여야 한다는 회귀 방지 테스트다.
        val result = ReservationRefundPolicy.shouldRestore(PassStatus.ACTIVE, refundRequested = true)

        assertThat(result).isTrue()
    }
}
