package com.goldwrestling.attendance

import com.goldwrestling.pass.Pass
import com.goldwrestling.schedule.ClassType
import java.math.BigDecimal

/**
 * 저녁반 0.5회 참여 시 어느 이용권에서 차감할지 고르는 규칙(policies §4.2, D-091, D-128) —
 * 순수 판정만 한다. DB 조회는 `PassRepository.existsActiveEveningMembership`(회비 우선 판정)과
 * `PassRepository.findDeductionCandidates`(`SESSION_PASS`, `requiredAmount = HALF_SESSION`,
 * `classDate` 기준 — 06-07이 배선)가 담당하고, 이 object는 그 결과를 해석만 한다.
 *
 * `ReservationPassPolicy`가 `reservation` 패키지에 있는 것과 같은 이유로 **`attendance` 패키지에
 * 둔다** — `schedule`이나 `pass`가 `attendance`를 참조하지 않게 의존 방향을 `attendance → pass`·
 * `attendance → schedule` 두 갈래로만 유지한다(D-018).
 */
object EveningHalfDeductionPolicy {
    /** 저녁반 0.5회 참여의 차감량(policies §4.2). Double 생성자 대신 문자열 생성자를 쓴다(D-016). */
    val HALF_SESSION: BigDecimal = BigDecimal("0.5")

    /**
     * 저녁반 전용 처리 대상인지 검증한다. `EVENING`이 아니면 저녁반 출석 API를 잘못 호출한
     * 것이므로 [AttendanceClassTypeMismatchException]을 던진다.
     */
    fun requireEveningSession(classType: ClassType) {
        if (classType != ClassType.EVENING) throw AttendanceClassTypeMismatchException()
    }

    /**
     * 차감 대상 이용권 후보 중 하나를 고른다. [candidates]는
     * `PassRepository.findDeductionCandidates`가 이미 만료 임박순(`endDate asc, id asc`)으로
     * 정렬해 내려준다 — **이 메서드는 정렬을 다시 하지 않는다.** 서비스·정책이 다시 정렬하면
     * 만료 임박순(D-091) 규칙이 두 곳으로 갈라진다.
     *
     * `ReservationPassPolicy.selectCandidate`를 그대로 호출하지 않는 이유: 그 함수는 비어 있을 때
     * [com.goldwrestling.pass.InsufficientPassCountException]을 던지는데, 이는 예약 잔여 부족
     * 전용 코드다. 저녁반 차감 불가는 "회비도 없고 잔여도 0.5 미만"이라는 별개 사유이고 FE 안내
     * 문구도 다르므로 D-133이 신설한 [EveningAttendanceDeductionUnavailableException]으로
     * 응답해야 한다 — 재사용 대신 이 object에 같은 모양의 함수를 별도로 둔다.
     */
    fun selectCandidate(candidates: List<Pass>): Pass = candidates.firstOrNull() ?: throw EveningAttendanceDeductionUnavailableException()

    /**
     * 저녁반 0.5회 차감 판정의 진입점(policies §4.2) — 회비 우선, 없으면 [candidates]에서 한 장
     * 선택, 그마저 없으면 거부한다.
     *
     * [hasValidMembership]은 `PassRepository.existsActiveEveningMembership(memberId, classDate)`의
     * 결과다 — **수업날 기준**이다(오늘 기준이 아니다, D-091). true면 [candidates]를 보지 않고
     * `null`(차감 없음)을 반환한다.
     *
     * 반환값이 `null`이면 회비로 커버되어 차감할 이용권이 없다는 뜻이고, non-null이면 그 [Pass]에서
     * [HALF_SESSION]을 차감해야 한다는 뜻이다 — 실제 차감 실행(조건부 UPDATE + `PassTransaction`
     * 기록)은 이 판정 밖(06-07)에서 한다.
     */
    fun resolveDeduction(
        hasValidMembership: Boolean,
        candidates: List<Pass>,
    ): Pass? {
        if (hasValidMembership) return null
        return selectCandidate(candidates)
    }
}
