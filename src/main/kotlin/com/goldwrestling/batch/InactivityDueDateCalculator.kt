package com.goldwrestling.batch

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 2주 미사용 자동 차감(policies §4.3, D-105·D-106)의 기준일 5종 후보. 값 객체이므로 `data class`
 * 허용(엔티티 `data class` 금지 규약은 JPA 엔티티 전용, `WeekRange` 선례).
 *
 * ① [lastAttendanceDate]는 Phase 6이 `Attendance`를 도입하기 전까지 항상 null이며 이는 의도된
 * 동작이다(D-105) — Phase 6은 이 값 객체에 필드를 채우기만 하면 된다.
 * ② [lastActiveReservationClassDate]는 **취소되지 않은** 예약의 수업일(수업 종류 무관).
 * ③ [deductionExclusionExitedDate]는 **차감 제외 상태(`ON_LEAVE`·`INACTIVE`)에서 벗어난 날**
 * (D-147) — 제외가 끝나면 2주 유예가 새로 시작된다. 제외 상태 목록의 근거는
 * [com.goldwrestling.member.MemberStatus.DEDUCTION_EXCLUDED] 하나다.
 * ④ [lastSessionPassRegistrationDate]는 `SESSION_PASS` 등록일(`created_at`, 시작일 아님).
 * ⑤ [lastPositiveAdjustDate]는 마지막 양(+) `ADMIN_ADJUST` 일자 — 충전 시점부터 2주 유예가
 * 새로 시작된다.
 */
data class InactivityDueDateCandidates(
    val lastAttendanceDate: LocalDate?,
    val lastActiveReservationClassDate: LocalDate?,
    val deductionExclusionExitedDate: LocalDate?,
    val lastSessionPassRegistrationDate: LocalDate?,
    val lastPositiveAdjustDate: LocalDate?,
)

/**
 * 2주 미사용 자동 차감(policies §4.3)의 판정 전부를 담은 순수 계산. Spring·DB·시각 주입 빈에
 * 의존하지 않는다 — 오늘 날짜는 항상 파라미터로 받는다(conventions §5, 호출부가
 * `LocalDate.now(주입받은 시각 소스)`를 넘긴다).
 */
object InactivityDueDateCalculator {
    /** policies §4.3의 "2주"를 표현하는 유일한 상수 — 정책이 바뀌면 여기 하나만 바뀐다. */
    private const val GRACE_PERIOD_DAYS = 14L

    /**
     * 기준일 후보 5종(D-105·D-147) 중 non-null의 max를 고르되, **[policyEffectiveDate]보다 이르면 시행일로
     * 끌어올린다**(D-119). 전부 null이면 null(판정 대상 아님 — 호출부가 스킵한다). 후보 순서에는
     * 의미를 주지 않는다.
     *
     * ### [policyEffectiveDate]는 "이 배치가 존재하기 시작한 날"이다 (CR-02)
     * 그 이전의 미사용은 차감 **부채로 치지 않는다.** 이 하한이 없으면 D-106의 상태 기반 캐치업
     * ("배치가 며칠 밀리면 다음 실행이 밀린 주기를 몰아서 차감한다")이 **"배치가 아예 없었던
     * 과거 전체"**까지 몰아서 차감하는 결과가 된다 — 200일 전에 등록하고 한 번도 쓰지 않은
     * `SESSION_PASS`는 배포 후 첫 실행에서 기대 차감 수 14회를 계산한다. 캐치업 설계는 "배치가
     * 이미 돌고 있었다"는 전제 위에 서 있고, 그 전제를 코드에 명시하는 유일한 장치가 이 하한이다.
     *
     * 시행일이 **미래**면 기준일도 미래가 되어 [expectedDeductionCount]가 0이므로, 배포 전에
     * 쌓여 있던 데이터가 소급 차감되지 않는다(기본값 `2026-09-01`이 그 안전판이다).
     *
     * ### 왜 파라미터인가
     * 이 object는 **순수 계산**이라 Spring·DB·시각 주입 빈에 의존하지 않는다(conventions §5).
     * 설정값을 여기서 직접 읽으면(`@Value`·`@ConfigurationProperties` 주입) 이 계산을 검증하려고
     * 스프링 컨텍스트를 띄워야 하고, "같은 입력이면 같은 출력"이 깨진다. `today`를 파라미터로
     * 받는 것과 같은 이유다 — 호출부(`InactivityBatchRunner`)가
     * `InactivityBatchProperties.policyEffectiveDate`를 넘긴다.
     *
     * `maxOrNull()` **뒤에** 안전 호출로 하한을 건다 — 순서를 바꿔 후보 목록에 시행일을 끼워
     * 넣으면 "후보가 전부 null이면 판정 대상이 아니다"가 깨져, 이용권을 막 등록해 후보가 아직
     * 없는 회원까지 시행일 기준으로 차감된다.
     */
    fun resolveDueDate(
        candidates: InactivityDueDateCandidates,
        policyEffectiveDate: LocalDate,
    ): LocalDate? =
        listOfNotNull(
            candidates.lastAttendanceDate,
            candidates.lastActiveReservationClassDate,
            candidates.deductionExclusionExitedDate,
            candidates.lastSessionPassRegistrationDate,
            candidates.lastPositiveAdjustDate,
        ).maxOrNull()
            ?.coerceAtLeast(policyEffectiveDate)

    /**
     * 기준일로부터 오늘까지 "존재해야 할" `INACTIVITY` 차감 횟수 — `floor(경과일 / 14)`
     * (policies §4.3 "2주 경과 시 1회, 이후 2주마다 반복"). 기준일이 오늘보다 미래(경과일 음수)면
     * 0을 반환한다 — 미래 수업일을 예약해 둔 회원이 음수 차감을 받지 않는다.
     */
    fun expectedDeductionCount(
        dueDate: LocalDate,
        today: LocalDate,
    ): Int {
        val elapsedDays = ChronoUnit.DAYS.between(dueDate, today)
        if (elapsedDays < GRACE_PERIOD_DAYS) return 0
        return (elapsedDays / GRACE_PERIOD_DAYS).toInt()
    }

    /**
     * 부족분 = 존재해야 할 차감 수 − 기준일 이후(당일 포함) 실제 발생한 `INACTIVITY` 이력
     * 건수(D-106). 이력 날짜가 [dueDate]보다 이전이면 세지 않는다 — 기준일이 리셋되면(차감 제외
     * 상태 이탈·+가감) 그 이전 이력은 지금의 유예 주기와 무관하다.
     *
     * **이 계산이 멱등성·캐치업의 유일한 근거다** — "오늘 이미 실행했는가" 같은 실행 이력
     * (`BatchExecution`) 기반 가드를 여기에 추가하면 안 된다(D-106). `BatchExecution`은
     * 관측·복구 판단용일 뿐 멱등성의 근거가 아니다(D-108) — 원장(`PassTransaction`) 건수만이
     * 진실이다.
     *
     * 하한 0은 방어적이다 — 정상 흐름에서는 매 실행이 부족분만 채우므로 이력이 기대 횟수를
     * 넘어설 수 없지만, 수동 데이터 조작·마이그레이션 오류로 넘어섰을 때 음수 부족분이
     * "차감 취소"로 오해되지 않게 막는다.
     */
    fun shortfall(
        dueDate: LocalDate,
        today: LocalDate,
        inactivityEventDates: List<LocalDate>,
    ): Int {
        val expected = expectedDeductionCount(dueDate, today)
        val actualSinceDueDate = inactivityEventDates.count { !it.isBefore(dueDate) }
        return (expected - actualSinceDueDate).coerceAtLeast(0)
    }
}
