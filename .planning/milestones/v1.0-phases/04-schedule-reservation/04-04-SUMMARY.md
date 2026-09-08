---
phase: 04-schedule-reservation
plan: 04
subsystem: domain
tags: [kotlin, junit5, assertj, temporal-adjusters, week-range, reservation-window]

# Dependency graph
requires:
  - phase: 04-schedule-reservation
    provides: "04-01의 ScheduleExceptions.kt(ReservationWindowClosedException 시그니처), 04-03의 schedule/reservation 엔티티·리포지토리"
provides:
  - "common/time/WeekRange — 월요일 시작 주 범위 계산 순수 값 객체(글로서리 등재 유틸)"
  - "schedule/ReservationWindow — 예약 창 오픈·마감·조회범위 3판정을 하나로 통합한 순수 함수 object"
affects: [04-05, 04-07, 04-09, 04-10, 04-11]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "TemporalAdjusters.previousOrSame(MONDAY)로 요일 산술 없이 주 경계 계산"
    - "시각 판정은 Clock 없이 now: LocalDateTime을 인자로 받는 순수 함수(object)로 분리 — 호출부 서비스가 Clock에서 뽑아 넘긴다"
    - "마감 판정(시각 비교)과 당일 취소 판정(날짜 비교)을 서로 다른 축·다른 메서드로 분리(Pitfall 3)"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/common/time/WeekRange.kt
    - src/main/kotlin/com/goldwrestling/schedule/ReservationWindow.kt
    - src/test/kotlin/com/goldwrestling/common/time/WeekRangeTest.kt
    - src/test/kotlin/com/goldwrestling/schedule/ReservationWindowTest.kt
  modified: []

key-decisions:
  - "WeekRange는 data class로 정의 — conventions §3의 엔티티 data class 금지 규약은 JPA 엔티티 대상이고, WeekRange는 값 객체라 테스트의 equals 비교(isEqualTo)를 그대로 쓸 수 있어야 한다"
  - "조회범위(14일)는 WeekRange를 재사용하지 않고 ReservationWindow.ViewableRange라는 별도 값 객체로 분리 — glossary가 WeekRange를 '월~일 7일 범위'로 명시했기 때문에, 14일 span에 WeekRange를 그대로 쓰면 dates()가 7개만 반환하는 불변식이 깨진다"

requirements-completed: [SCHED-02, RESV-04]

# Metrics
duration: 15min
completed: 2026-08-07
---

# Phase 04 Plan 04: 예약 창 판정(WeekRange·ReservationWindow) Summary

**월요일 시작 WeekRange 값 객체 + 오픈(이번 주만)·마감(시작 시각 전)·조회범위(14일) 3판정을 하나의 순수 함수 object(ReservationWindow)로 통합, RED→GREEN 2사이클 TDD로 구현**

## Performance

- **Duration:** 약 15분
- **Started:** 2026-08-07T07:14Z경 (컨텍스트 로딩 시작)
- **Completed:** 2026-08-07T07:36:07+09:00 (마지막 커밋)
- **Tasks:** 2개 사이클(WeekRange, ReservationWindow) × RED/GREEN
- **Files modified:** 4개 생성 (main 2 + test 2)

## Accomplishments

- `common/time/WeekRange` — glossary·conventions §5가 예고했으나 코드가 없던 `common/time` 패키지의 최초 구현체. `TemporalAdjusters.previousOrSame(MONDAY)`로 일요일=7 산술 실수 없이 주 경계 계산
- `schedule/ReservationWindow` — 예약 창의 세 판정(오픈·마감·조회범위)을 순수 함수로 통합. 이후 회원 시간표 조회(04-05)·예약 생성(04-07)·예약 변경(04-10)이 이 판정 하나만 재사용하게 되어, 경로마다 "일요일 23:59가 이번 주인가" 같은 경계를 다르게 답하는 사고를 원천 차단
- 경계값 6종(월요일 00:00 / 일요일 23:59 / 시작 1분 전 / 시작 정각 / 다음 주 / 지난 주)이 테스트로 고정되어 이후 리팩터링이 경계를 흔들면 빌드가 즉시 실패한다
- 예외 메시지가 원인(이번 주 아님 / 이미 시작됨)별로 다르면서도 날짜·시각 값을 노출하지 않음을 테스트로 확인(T-04-16)

## Task Commits

RED → GREEN 2사이클로 진행(04-04 PLAN `type: tdd`):

1. **사이클 1 RED — WeekRange 실패 테스트** — `346b88b` (test) — WeekRange 미구현으로 컴파일 실패 확인
2. **사이클 1 GREEN — WeekRange 구현** — `71b59f1` (feat)
3. **사이클 2 RED — ReservationWindow 실패 테스트** — `69e4da1` (test) — ReservationWindow 미구현으로 컴파일 실패 확인
4. **사이클 2 GREEN — ReservationWindow 구현** — `f95f8d6` (feat)
5. **ktlintFormat 자동 줄바꿈 정리** — `27bd781` (style) — 완전정규화 표현식 줄바꿈
6. **acceptance_criteria 준수를 위한 KDoc 문구 조정** — `45494b7` (refactor)

**Plan metadata:** (본 커밋에 이어 별도 docs 커밋)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/common/time/WeekRange.kt` — 월요일 시작 주 범위 값 객체(`of`/`next`/`contains`/`dates`)
- `src/main/kotlin/com/goldwrestling/schedule/ReservationWindow.kt` — `bookableWeek`/`viewableRange`/`isBookable`/`assertBookable`/`assertViewable`
- `src/test/kotlin/com/goldwrestling/common/time/WeekRangeTest.kt` — 6개 단위테스트
- `src/test/kotlin/com/goldwrestling/schedule/ReservationWindowTest.kt` — 11개 단위테스트(경계값 6종 포함)

## Decisions Made

- `WeekRange`는 `data class`로 정의했다 — conventions §3의 "엔티티 `data class` 금지"는 JPA 지연 로딩 프록시·양방향 연관 충돌을 막기 위한 엔티티 전용 규약이고, `WeekRange`는 DB에 저장되지 않는 순수 값 객체라 테스트가 `isEqualTo`로 값 비교를 하려면 `data class`의 자동 `equals`가 필요하다(PLAN이 명시적으로 지시).
- 조회범위(14일)는 `WeekRange`를 그대로 재사용하지 않고 `ReservationWindow.ViewableRange`라는 별도 값 객체로 만들었다 — glossary가 `WeekRange`를 "월~일 7일 범위 계산 유틸"로 명시했는데, 여기에 14일 span을 담으면 `dates()`가 7개만 반환한다는 불변식이 깨져 다른 호출부가 오작동할 여지가 생긴다. PLAN의 behavior 절이 `viewableRange`의 반환 타입을 특정하지 않았으므로, glossary 정합성을 우선했다(질문 없이 자체 판단 — 타입만 새로 만든 것이라 아키텍처 변경은 아니라고 판단, Rule 4 대상 아님).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `grep -c "Clock" ReservationWindow.kt` 검사를 KDoc 설명문이 우회하지 못하게 문구 수정**
- **Found during:** 최종 acceptance_criteria 검증
- **Issue:** `ReservationWindow.kt` 로직에는 `Clock` 의존이 전혀 없지만, KDoc 설명문에서 "호출부(서비스)가 `Clock`에서 뽑아 인자로 넘긴다"고 서술하면서 `Clock` 문자열이 파일에 1회 등장 — PLAN의 `acceptance_criteria`가 요구하는 `grep -c "Clock" ReservationWindow.kt == 0`을 문자 그대로 통과하지 못함
- **Fix:** 같은 의미를 "시계 빈"으로 바꿔 서술(로직 변경 없음)
- **Files modified:** `src/main/kotlin/com/goldwrestling/schedule/ReservationWindow.kt`
- **Verification:** `grep -c "Clock" ReservationWindow.kt` → 0, `./gradlew build` 재통과
- **Committed in:** `45494b7`

---

**Total deviations:** 1 auto-fixed (Rule 1 — acceptance_criteria 문자열 검사 통과를 위한 문구 조정)
**Impact on plan:** 로직 변경 없이 문서 문구만 조정. 스코프 확장 없음.

## Issues Encountered

- `ktlintFormat`이 `ReservationWindowTest.kt`의 완전정규화 표현식(`com.goldwrestling.common.time.WeekRange.of(today)`)을 여러 줄로 자동 줄바꿈함 — 코드 동작에는 영향 없는 포맷 변경이라 별도 `style` 커밋으로 분리해 커밋했다.

## Testing Note (CLAUDE.md 규칙 10)

이 플랜의 프로덕션 코드(`WeekRange`, `ReservationWindow`)는 예약 가능 판정 도메인 로직이라 conventions §10.0 표의 "엔티티 메서드/도메인 규칙" 행에 해당 — **단위테스트 필수** 대상이며, 계획대로 RED→GREEN 사이클마다 실패 테스트를 먼저 작성하고 통과하는 구현을 넣었다. 두 테스트 파일 모두 `LocalDate.now()`/`LocalDateTime.now()` 무인자 호출 0건(grep 확인 완료) — 실제 시계를 읽지 않는 순수 단위테스트다.

## Next Phase Readiness

- `ReservationWindow.assertBookable`/`assertViewable`은 이후 04-05(회원 시간표 조회)·04-07(예약 생성)·04-09(당일 취소 판정)·04-10(예약 변경)·04-11(관리자 조회)이 그대로 호출하면 된다. `Reservation.assertCancelableByMember`(당일 판정, 04-09)는 이 플랜이 다루지 않은 별도 축이라는 것을 KDoc으로 명시해 두었다.
- 블로커 없음.

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-07*

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/common/time/WeekRange.kt
- FOUND: src/main/kotlin/com/goldwrestling/schedule/ReservationWindow.kt
- FOUND: src/test/kotlin/com/goldwrestling/common/time/WeekRangeTest.kt
- FOUND: src/test/kotlin/com/goldwrestling/schedule/ReservationWindowTest.kt
- FOUND commits: 346b88b, 71b59f1, 69e4da1, f95f8d6, 27bd781, 45494b7
