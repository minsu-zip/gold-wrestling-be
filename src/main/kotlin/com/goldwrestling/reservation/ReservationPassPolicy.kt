package com.goldwrestling.reservation

import com.goldwrestling.pass.InsufficientPassCountException
import com.goldwrestling.pass.Pass
import com.goldwrestling.pass.PassType
import com.goldwrestling.schedule.ClassSessionNotReservableException
import com.goldwrestling.schedule.ClassType
import java.math.BigDecimal

/**
 * 예약이 어느 이용권에서 차감할지 고르는 규칙(D-091) — 순수 판정만 한다. DB 조회는
 * `PassRepository.findDeductionCandidates`가 담당하고, 이 object는 그 결과를 해석만 한다.
 *
 * 이 매핑을 `pass`가 아니라 `reservation` 패키지에 둔 이유(RESEARCH·PLAN.md 명시): `schedule` 패키지가
 * `pass`를 참조하지 않게 하기 위해서다 — 의존 방향은 `reservation → schedule` · `reservation → pass`
 * 두 갈래만 생긴다(D-018).
 */
object ReservationPassPolicy {
    /** 예약 1건의 차감량 — 이번 phase 요구량은 항상 이 값으로 고정된다(D-091 "예약 1건 ↔ 이용권 1장"). */
    val DEDUCTION_AMOUNT: BigDecimal = BigDecimal("1.0")

    /**
     * 수업 종류 → 필요 이용권 종류 매핑(D-093). `EVENING`은 예약 대상이 아니므로(policies §1,
     * `ClassType.reservable = false`) [ClassSessionNotReservableException]으로 거부한다 — 저녁반은
     * 애초에 이 정책을 거칠 일이 없어야 하고, 잘못 호출됐다면 그 자체가 버그다.
     */
    fun requiredPassType(classType: ClassType): PassType =
        when (classType) {
            ClassType.SESSION -> PassType.SESSION_PASS
            ClassType.LESSON -> PassType.LESSON_PASS
            ClassType.EVENING -> throw ClassSessionNotReservableException()
        }

    /**
     * 차감 대상 이용권 후보 중 하나를 고른다. [candidates]는 `PassRepository.findDeductionCandidates`가
     * 이미 만료 임박순(`endDate asc, id asc`)으로 정렬해 내려준다 — **이 메서드는 다시 정렬하지 않는다.**
     * 비어 있으면 여러 장을 합산해도 충분한 단일 이용권이 없다는 뜻이므로 잔여 부족으로 처리한다(D-091
     * "여러 장을 합산해 한 건을 예약할 수 없다", RESV-03).
     */
    fun selectCandidate(candidates: List<Pass>): Pass = candidates.firstOrNull() ?: throw InsufficientPassCountException()
}
