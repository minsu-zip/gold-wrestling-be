---
phase: 05-batch
plan: 03
subsystem: batch
tags: [kotlin, tdd, domain-logic, pure-function]

# Dependency graph
requires:
  - phase: 05-batch (05-02)
    provides: "batch 패키지 골격(BatchExecution 등)·V9 마이그레이션·member.returnedFromLeaveAt — 이 플랜의 호출부가 05-06에서 쓸 실제 데이터 경로"
provides:
  - "InactivityDueDateCandidates 값 객체 — D-105 기준일 5종 후보를 담는 공개 API(05-06이 그대로 호출)"
  - "InactivityDueDateCalculator.resolveDueDate — 후보 중 non-null의 max(기준일 판정)"
  - "InactivityDueDateCalculator.expectedDeductionCount — floor(경과일/14), policies §4.3 2주 반복 규칙"
  - "InactivityDueDateCalculator.shortfall — D-106 상태 기반 부족분(멱등+캐치업의 유일한 근거)"
affects: [05-04, 05-05, 05-06, 05-07, 05-08, 05-09]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "순수 정책 object(reservation/ReservationPassPolicy.kt 관례) — Spring·DB·Clock 의존 없이 today를 파라미터로 받는다"
    - "정책 상수 단일화 — GRACE_PERIOD_DAYS = 14 하나로 '2주' 주기를 표현, 본문에 다른 14 리터럴을 두지 않는다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityDueDateCalculatorTest.kt
  modified: []

key-decisions:
  - "GREEN 직후 ktlintFormat이 expectedDeductionCount 파라미터를 멀티라인으로 재정렬 — 별도 style(05-03) 커밋으로 분리(동작 변경과 포맷 변경을 한 커밋에 섞지 않는다)"
  - "object KDoc이 acceptance criteria의 grep -c \"Clock\" == 0 검사와 충돌해(설명 문장에 'Clock'이라는 단어가 그대로 있었음) '시각 주입 빈'으로 표현을 바꿔 의미는 유지하고 리터럴 문자열만 제거"

patterns-established:
  - "TDD 사이클 2회(기준일 max → 부족분·캐치업)를 한 파일에 이어서 진행 — 테스트 픽스처(candidates() 헬퍼, DUE_DATE 상수)를 사이클 간 재사용해 REFACTOR 커밋 없이도 중복을 피함"

requirements-completed: [BATCH-01, BATCH-04]

# Metrics
duration: ~25min
completed: 2026-08-15
---

# Phase 5 Plan 3: 미사용 판정 기준일·부족분 계산 Summary

**`InactivityDueDateCalculator` object(순수 함수 3개)로 2주 미사용 차감의 판정 전부를 Spring·DB·Clock 없이 TDD로 고정 — 기준일 max(D-105), 존재해야 할 차감 수(policies §4.3 5경계), 상태 기반 부족분(D-106 멱등+캐치업)을 22개 단위테스트로 증명**

## Performance

- **Duration:** ~25분
- **Started:** 2026-08-15T12:20 (컨텍스트 로딩 포함)
- **Completed:** 2026-08-15T12:30
- **Tasks:** TDD 사이클 2회 (RED/GREEN 각 2쌍) + 포맷·문서 보정 2건 = 커밋 6건
- **Files modified:** 2 (신규 2)

## Accomplishments
- `InactivityDueDateCandidates`(5종 후보 값 객체) + `resolveDueDate`(non-null max, `listOfNotNull(...).maxOrNull()` 한 줄) — 후보 5개 전부 null·출석일만 부재·복귀일/양(+)가감일 리셋·같은 날짜 동률 6케이스로 D-105 문장을 그대로 고정
- `expectedDeductionCount`(`floor(경과일/14)`)가 policies §4.3의 13/14/27/28/41/42일 5개 경계와 미래 기준일(음수 경과) 8케이스를 전부 통과 — T-05-07(오프바이원) 위협을 테스트로 봉쇄
- `shortfall`이 D-106 상태 기반 설계를 8케이스로 증명: 같은 날 두 번째 실행 부족분 0(BATCH-04 멱등), 6주 밀린 실행 부족분 3(캐치업), 기준일 당일/이전 경계, 이력 초과 시 `coerceAtLeast(0)` 방어
- 22개 단위테스트 전부 통과, `./gradlew ktlintFormat` → `./gradlew build`(Testcontainers 포함 전체 스위트) 그린

## Task Commits

TDD 사이클로 진행, RED→GREEN 커밋 쌍 2세트 + 포맷·문서 보정:

1. **RED (사이클 1): 기준일 max 실패 테스트** - `ec65b39` (test)
2. **GREEN (사이클 1): InactivityDueDateCalculator·resolveDueDate 구현** - `d1e0eb7` (feat)
3. **RED (사이클 2): 부족분·캐치업 실패 테스트** - `69f09a4` (test)
4. **GREEN (사이클 2): expectedDeductionCount·shortfall 구현** - `d4415c8` (feat)
5. **ktlintFormat 적용(파라미터 개행, 동작 변경 없음)** - `def0e83` (style)
6. **acceptance grep 충돌 해소(KDoc의 'Clock' 문자열 제거)** - `37a8af7` (docs)

_REFACTOR 커밋은 없음 — 사이클 1에서 만든 `candidates()` 헬퍼·사이클 2 도입 시점의 `DUE_DATE` companion 상수로 중복이 이미 최소화돼 별도 정리가 필요하지 않았다._

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt` - `InactivityDueDateCandidates` 값 객체 + `resolveDueDate`/`expectedDeductionCount`/`shortfall` 순수 함수 3개
- `src/test/kotlin/com/goldwrestling/batch/InactivityDueDateCalculatorTest.kt` - 22개 단위테스트(resolveDueDate 6 + expectedDeductionCount 8 + shortfall 8)

## Decisions Made

- **ktlintFormat 결과를 별도 style 커밋으로 분리**: GREEN 커밋 직후 `ktlintFormat`을 돌리면 `expectedDeductionCount`의 파라미터 목록이 자동으로 멀티라인 재정렬됐다. GREEN 커밋에 섞으면 "동작 구현"과 "포맷 자동 수정"이 한 커밋에 섞여 리뷰 시 diff 의도가 흐려지므로 별도 `style(05-03)` 커밋으로 분리했다.
- **KDoc 표현을 "Clock" → "시각 주입 빈"으로 수정(Rule 1 - Bug)**: acceptance criteria가 `grep -c "Clock" == 0`으로 "이 파일이 Clock에 의존하지 않는다"를 자동 검증하는데, object KDoc이 그 사실을 설명하는 문장 안에 "Clock"이라는 단어를 그대로 썼다가 같은 grep에 걸렸다. 의미(순수 함수, 시각은 파라미터로만 받는다)는 그대로 두고 리터럴 문자열만 바꿔 검증 스크립트와 문서 의도를 둘 다 만족시켰다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] object KDoc의 "Clock" 문자열이 자체 acceptance grep과 충돌**
- **Found during:** 최종 acceptance criteria 검증 단계 (`grep -c "Clock" ... == 0` 실행 후)
- **Issue:** GREEN 커밋의 object-level KDoc이 "Spring·DB·`Clock`에 의존하지 않는다"라고 설명하면서 "Clock" 문자열을 그대로 포함해, 같은 파일에 대한 acceptance grep(`Clock` 미사용 검증)이 실패했다
- **Fix:** 문장을 "시각 주입 빈에 의존하지 않는다"로 바꿔 같은 의미를 유지하면서 리터럴 문자열만 제거
- **Files modified:** `src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt`
- **Verification:** `grep -c "Clock" ...` → 0, `./gradlew build` 재실행 그린
- **Committed in:** `37a8af7`

---

**Total deviations:** 1 auto-fixed (Rule 1 — grep 충돌 수정, 로직 변경 없음)
**Impact on plan:** 계산 로직·테스트 케이스는 계획서 `<behavior>`를 그대로 옮겼고 추가·삭제 없음. 유일한 편차는 문서 문구 수정.

## Issues Encountered

None — Docker Desktop이 이미 기동돼 있어 Testcontainers 기반 전체 빌드도 즉시 그린이었다.

## User Setup Required

None - 이 플랜은 Spring 컨텍스트·DB·환경변수와 무관한 순수 Kotlin 파일 2개만 추가했다.

## Next Phase Readiness

- 05-06(`InactivityBatchRunner`)이 이 object의 세 함수만 호출하면 된다 — "몇 번 차감해야 하나"를 다시 계산할 필요가 없다
- BATCH-04(멱등)와 캐치업이 통합테스트 이전에 단위테스트 수준에서 이미 증명돼 있다 — 05-05/05-06의 Testcontainers 테스트는 이 계산을 "신뢰"하고 배선만 검증하면 된다
- 05-04(벌크 조회 리포지토리)가 만들 `InactivityDueDateCandidates`의 각 필드 데이터 출처가 PLAN.md `<interfaces>`에 이미 명시돼 있어 시그니처 불일치 위험이 낮다
- REQUIREMENTS.md는 이 플랜에서 건드리지 않았다 — BATCH-01·BATCH-04의 Complete 전환은 05-09가 일괄 처리한다(project_rules 지시)
- Blocker 없음

---
*Phase: 05-batch*
*Completed: 2026-08-15*

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt
- FOUND: src/test/kotlin/com/goldwrestling/batch/InactivityDueDateCalculatorTest.kt
- FOUND commit: ec65b39, d1e0eb7, 69f09a4, d4415c8, def0e83, 37a8af7 (all present in `git log --oneline`)
