package com.goldwrestling.reservation

import com.goldwrestling.pass.PassStatus

/**
 * 취소 시 이용권 복구를 수행할지 결정하는 순수 판정(D-091, Pitfall 2, RESEARCH.md).
 *
 * **`PassRepository.adjustRemainingCount`가 0행을 반환하는 두 의미를 혼동하지 않기 위해 존재한다**
 * — 예약 생성 경로에서 0행은 "경쟁 패배"(예외로 변환)지만, 취소 복구 경로에서 등록 취소
 * (`PassStatus.CANCELED`)된 이용권에 대한 0행은 "복구하지 않는 게 정책"(정상)이다. 이 두 의미를
 * 호출부가 반환값을 보고 즉흥적으로 나누게 하면 그 분기 자체가 버그의 원인이 된다 — 그래서
 * **호출 전에** 복구 여부를 이 object로 먼저 결정하고, [shouldRestore]가 `false`면
 * `adjustRemainingCount`를 아예 호출하지 않는다. 호출한 뒤 반환 0을 무시하는 방식으로 구현하면
 * "경쟁 패배로 인한 0"과 "정책상 복구 안 함으로 인한 0"을 코드에서 구분할 수 없게 된다.
 *
 * [shouldRestore]가 `false`를 반환한 경우에도 **예약은 정상적으로 취소 상태로 전환된다** — 이
 * 값은 `adjustRemainingCount` 호출 여부만 가른다. `Reservation.status`를 `CANCELED`로 바꾸는
 * 것은 `ReservationRepository`의 조건부 UPDATE가 별도로 담당하며 이 판정과 무관하게 항상 일어난다.
 */
object ReservationRefundPolicy {
    /**
     * 복구 여부를 판정한다.
     *
     * - [refundRequested]가 `false`면 무조건 복구하지 않는다 — 관리자 대리 취소의 "복구 안 함"
     *   선택(policies §3, 당일 불참 연락이 대표 사용처)을 그대로 반영한다
     * - 이용권 [passStatus]가 [PassStatus.CANCELED](등록 취소)면 복구하지 않는다 — 이미
     *   `REGISTRATION_CANCELED` 이력으로 잔여를 0으로 상쇄했으므로, 여기서 복구하면 D-059의
     *   취소 의미가 깨진다
     * - 그 외에는 복구한다. **유효기간이 지난 이용권도 복구 대상이다** — `endDate`를 이 판정이
     *   보지 않는다는 것 자체가 그 증거다("잔여 = 이력 합계" 원장 불변식 유지, policies §4.2a와
     *   일관)
     */
    fun shouldRestore(
        passStatus: PassStatus,
        refundRequested: Boolean,
    ): Boolean {
        if (!refundRequested) return false
        if (passStatus == PassStatus.CANCELED) return false
        return true
    }
}
