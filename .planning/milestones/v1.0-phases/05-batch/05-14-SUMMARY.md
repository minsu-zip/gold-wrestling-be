---
phase: 05-batch
plan: 14
subsystem: batch
tags: [kotlin, spring-boot, configuration-properties, pure-function, batch, testcontainers, test-isolation]

# Dependency graph
requires:
  - phase: 05-batch (05-12)
    provides: "InactivityBatchProperties(policyEffectiveDate=2026-09-01, maxDeductionsPerRun=1) + application.yml·.env.example 키 3종"
  - phase: 05-batch (05-13)
    provides: "InactivityBatchRunner.runStarted()의 while 루프 — 상한 적용 지점이 shortfallCount 계산 직후 한 곳으로 정리돼 있다"
provides:
  - "InactivityDueDateCalculator.resolveDueDate(candidates, policyEffectiveDate) — 기준일에 정책 시행일 하한 적용(순수 계산 유지, Spring 의존 0)"
  - "InactivityBatchRunner: minOf(부족분, maxDeductionsPerRun) 1회 실행 상한 + 절삭 로그(skippedCount 미오염)"
  - "build.gradle.kts 테스트 전역 시행일 고정(2000-01-01) — 배치 테스트가 '차감 0'을 검증하는 빈 껍데기가 되는 것을 막는다"
  - "InactivityBatchPolicyLimitTest: 200일 방치 1회 차감 / 다음 실행 이어받기 / 시행일 경계 −13일·−15일 실증 5종"
  - "InactivityBatchDeductionLimitOverrideTest: 상한이 코드 상수가 아니라 설정값임을 증명 + 대상 소진 스킵 계약 승계 2종"
  - "docs: policies §4.3 도메인 규칙 2개, D-119 신규, glossary 용어 2종"
affects: [05-15, 05-16]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "정책 값을 소비하는 순수 함수는 설정을 주입받지 않고 파라미터로 받는다 — 호출부(스프링 빈)가 @ConfigurationProperties를 읽어 넘긴다"
    - "설정값의 프로덕션 기본값이 테스트 고정 시각과 어긋나면 build.gradle.kts 테스트 태스크에서 전역 고정하고, 그 값 자체를 검증하는 클래스만 @SpringBootTest(properties=...)로 덮어쓴다"
    - "'상한이 하드코딩돼도 통과하는 테스트'를 막으려면 상한을 기본값이 아닌 값으로 덮어쓴 전용 컨텍스트가 하나 필요하다"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchPolicyLimitTest.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchDeductionLimitOverrideTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt
    - src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityDueDateCalculatorTest.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchIdempotencyTest.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt
    - build.gradle.kts
    - docs/policies.md
    - docs/decisions.md
    - docs/glossary.md

key-decisions:
  - "D-119 신규: 정책 시행일 하한(기본 2026-09-01)과 1회 실행 상한(기본 1). 둘 다 설정값이라 재배포 없이 되돌릴 수 있다"
  - "시행일을 계산기에 주입하지 않고 파라미터로 넘긴다 — InactivityDueDateCalculator는 순수 계산이라 Spring 의존을 들이면 검증에 컨텍스트가 필요해진다(conventions §5)"
  - "coerceAtLeast를 maxOrNull() **뒤에** 안전 호출로 건다 — 후보 목록에 시행일을 끼워 넣으면 '후보 전부 null이면 판정 대상 아님'이 깨진다"
  - "상한으로 잘린 부족분은 skippedCount에 세지 않고 logger.info로만 남긴다(D-113) — 그 필드는 대상 소진·경쟁 패배 전용이고 상한 적용은 정상 예정 동작이다"
  - "테스트 전역 시행일을 2000-01-01로 고정한다 — 고정하지 않으면 배치 테스트 전체가 '차감 0'을 검증하는 빈 껍데기가 되면서 초록불로 통과한다"
  - "상한 1 아래에서 '대상 소진 스킵'은 경쟁 없이는 도달할 수 없다 — 그 계약을 상한 3 전용 컨텍스트로 옮겨 보존했다"

patterns-established:
  - "설정으로 뺀 정책 값은 '기본값이 아닌 값'으로 한 번은 검증한다 — 그러지 않으면 값을 무시하고 상수를 쓰는 구현도 전부 통과한다"

requirements-completed: []

duration: 30min
completed: 2026-08-16
---

# Phase 05 Plan 14: 소급 차감 차단 — 정책 시행일 하한과 1회 실행 상한 Summary

**기준일이 정책 시행일보다 이를 수 없게 만들고(`coerceAtLeast`) 한 실행이 회원 1명에게서 깎는 횟수를 `minOf(부족분, 상한)`으로 잘라, 배포 후 첫 실행이 "배치가 아예 없었던 과거 전체"를 소급 차감하던 CR-02를 닫았다 — 200일 방치된 잔여 5.0짜리 `SESSION_PASS`가 한 실행에 0이 되던 것이 이제 하루 1회씩이다.**

## Performance

- **Duration:** 약 30분
- **Tasks:** 2 (둘 다 TDD RED/GREEN 분리 커밋)
- **Files:** 11 (신규 2, 수정 9)

## Accomplishments

### Task 1 — 기준일에 정책 시행일 하한 (순수 계산)

`resolveDueDate(candidates, policyEffectiveDate)`로 시그니처를 넓히고 구현은
`listOfNotNull(...).maxOrNull()?.coerceAtLeast(policyEffectiveDate)` 한 줄이다.
**안전 호출 위치가 계약이다** — 후보 목록에 시행일을 끼워 넣는 방식이었다면 "후보가 전부 null이면
여전히 null(판정 대상 자체가 아니다)"이 깨져, 이용권을 막 등록해 후보가 아직 없는 회원까지
시행일 기준으로 차감된다.

KDoc에 하한의 의미("이 배치가 존재하기 시작한 날")와 없을 때 무슨 일이 나는지(D-106 캐치업이
도입 이전 기간까지 몰아서 차감), 그리고 **왜 설정을 주입받지 않고 파라미터로 받는지**
(conventions §5 순수 계산 유지)를 남겼다.

단위테스트 6종 추가 + 기존 6종은 하한이 발동하지 않는 값(`2000-01-01`)을 넘겨 기존 계약을 그대로
유지했다(하한이 기존 규칙을 덮어쓰지 않는지 확인하는 회귀 방어).

### Task 2 — 러너 배선·실증·문서

- **러너**: `minOf(shortfallCount, properties.maxDeductionsPerRun)`로 자르고, 잘렸으면
  `logger.info`로 "부족분 n회 중 m회만 차감 — 나머지는 다음 실행이 이어받는다"를 남긴다.
  **`skippedCount`는 올리지 않는다**(D-113) — 그 필드는 대상 소진·경쟁 패배 전용이고 상한 적용은
  사고가 아니라 정상 예정 동작이라, 올리면 운영자가 집계만 보고 "차감에 실패했다"로 오독한다.
- **`InactivityBatchPolicyLimitTest`(신규 5종)**: 클래스 프로퍼티로 시행일을 `2026-08-01`로 주고
  경계 두 케이스는 **clock을 옮겨서** 만든다(프로퍼티는 클래스 단위라 테스트마다 못 바꾼다).
  200일 방치 1회 차감 / 절삭은 skip이 아님 / 하루 뒤 또 1회(이어받기) / 시행일 −13일 차감 0 /
  −15일 정확히 1회.
- **`InactivityBatchDeductionLimitOverrideTest`(신규 2종, 플랜 밖 — 아래 Deviation 1)**:
  상한을 `3`으로 덮어쓴 컨텍스트. 상한이 **설정값이라는 증명**과 상한 1에서 도달 불가해진
  **대상 소진 스킵 계약**을 담당한다.
- **문서**: policies §4.3에 도메인 규칙 2개, `docs/decisions.md`에 D-119, glossary에 용어 2종.

## Task Commits

| Task | 내용 | 커밋 | 종류 |
|---|---|---|---|
| 1 (RED) | 시행일 하한 순수 함수 계약 6종 (컴파일 실패로 RED 확인) | `e6388f1` | test |
| 1 (GREEN) | `coerceAtLeast` 하한 + 러너 전달 + 테스트 전역 시행일 고정 | `858f381` | feat |
| 2 (RED) | 상한·시행일 경계 통합테스트 2클래스 + 기존 캐치업 계약 갱신 | `5ab0211` | test |
| 2 (GREEN) | 러너 상한 배선 + KDoc 근거 | `a175db9` | feat |
| 2 (docs) | policies §4.3 · D-119 · glossary | `c60ad8b` | docs |

TDD 게이트: 두 태스크 모두 `test(...)`(실제 실패 확인) → `feat(...)`(통과) 순서를 지켰다.
Task 1의 RED는 컴파일 실패(`Too many arguments for 'fun resolveDueDate(...)'` 9건),
Task 2의 RED는 단언 실패 6건이다. REFACTOR 단계는 정리할 중복이 없어 커밋하지 않았다.

## ⚠️ 이 작업의 최대 위험과 그 대응 — "초록불인데 아무것도 증명하지 못하는 테스트"

**위험:** 정책 시행일 기본값 `2026-09-01`은 배치 테스트의 고정 시각
(`BatchFixtures.FIXED_TODAY` = `2026-08-02`)보다 **미래**다. 하한을 적용하면 모든 테스트 회원의
기준일이 시행일로 끌어올려져 `expectedDeductionCount = 0`이 되고, 멱등·캐치업·차감 테스트가
**"아무것도 차감하지 않음"을 검증하는 빈 껍데기가 되면서도 전부 통과한다.**

**대응 ①(예방):** `build.gradle.kts`의 `tasks.withType<Test>`에
`goldwrestling.batch.inactivity.policy-effective-date=2000-01-01`을 추가하고, 왜 필요한지를 주석에
남겼다(D-116 킬 스위치 고정과 같은 자리·같은 방식). 기존
`goldwrestling.batch.inactivity-scheduler-enabled` 줄은 그대로 두었다.

**대응 ②(능동 검증 — 통과 여부만 보지 않았다):**

1. **기존 테스트가 실제 차감을 단언하고 있는지 코드로 확인했다.**
   `InactivityBatchIdempotencyTest`는 `deductedCount == 1`, `remaining 2.0`, `inactivityCount == 1`처럼
   **차감이 일어났다는 사실**을 단언한다. "0이 아님"이 아니라 구체적 값이다.
2. **그 고정값이 없으면 실제로 깨지는지 실험했다.** `build.gradle.kts`의 값을 프로덕션 기본값
   `2026-09-01`로 일시 변경하고 돌린 결과 — **멱등 테스트 7종이 전부 `FAILED`**:

   ```
   InactivityBatchIdempotencyTest > 같은 날 배치를 두 번 실행해도 이중 차감이 없다() FAILED
   InactivityBatchIdempotencyTest > 배치가 6주 밀리면 … 차감한다() FAILED
   … (7/7 FAILED)
   ```

   즉 이 고정값은 **load-bearing**이고, 기존 테스트는 하한 때문에 조용히 무력화되지 않았다.
   (실험 후 값을 `2000-01-01`로 되돌렸고, 최종 상태는 `grep`으로 확인했다.)
3. **시행일 하한 자체는 전역 값에 의존하지 않고 명시 주입으로 검증한다.**
   - 순수 함수: `InactivityDueDateCalculatorTest`가 시행일을 **인자로** 넘긴다.
   - 통합: `InactivityBatchPolicyLimitTest`가 `@SpringBootTest(properties = ...)`로 클래스마다
     덮어쓴다. 이 클래스는 전역 값(`2000-01-01`)이 유효하면 오히려 **실패**한다 — 시행일 경계
     테스트가 성립하지 않기 때문이다. 그래서 이 클래스의 통과 자체가 "덮어쓰기가 실제로 먹혔다"는
     증거다.

**같은 함정의 두 번째 얼굴(플랜에 없던 것):** 상한도 똑같다. 모든 테스트가 상한 기본값 `1`
아래에서 돌면 러너가 `minOf(shortfall, properties.maxDeductionsPerRun)`이 아니라
`minOf(shortfall, 1)`로 **하드코딩돼 있어도 전부 초록불**이다. 그래서 상한을 `3`으로 덮어쓴
`InactivityBatchDeductionLimitOverrideTest`를 추가했다(Deviation 1).

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/batch/InactivityDueDateCalculator.kt` — `resolveDueDate`에
  `policyEffectiveDate` 파라미터 + `?.coerceAtLeast(...)`. KDoc에 하한의 의미·안전 호출 위치의
  이유·파라미터로 받는 이유. **`import org.springframework` 0건**(순수 계산 유지)
- `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt` — `InactivityBatchProperties`
  주입, `resolveDueDate(candidates, properties.policyEffectiveDate)`,
  `deductionTarget = minOf(...)` + 절삭 로그, 클래스 KDoc에 정책 값 2개의 의미·근거
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchPolicyLimitTest.kt` — 신규 267줄.
  픽스처 대역 `9_750_000_000L` + **범위 정리**(WR-07), `batch_execution`은 baseline(max(id)) 기준 정리
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchDeductionLimitOverrideTest.kt` — 신규.
  픽스처 대역 `9_760_000_000L` + 범위 정리
- `src/test/kotlin/com/goldwrestling/batch/InactivityDueDateCalculatorTest.kt` — 기존 6종 시그니처
  갱신(`ANCIENT_EFFECTIVE_DATE`), 시행일 하한 6종 추가
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchIdempotencyTest.kt` — 캐치업 계약 3종 갱신
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt` — 캐치업·스킵 계약 2종 갱신
- `build.gradle.kts` — 테스트 전역 시행일 고정 + 이유 주석 6줄
- `docs/policies.md` §4.3 — 도메인 규칙 2개 추가(**스펙 변경 — 아래 별도 보고**)
- `docs/decisions.md` — D-119 신규
- `docs/glossary.md` — "정책 시행일", "1회 실행 상한" 2종 + 기준일 정의에 하한 반영

## `docs/policies.md`를 고쳤다 (스펙 변경 보고)

CLAUDE.md 문서 우선순위상 스펙의 근거는 `.planning/`이 아니라 `docs/`다. 이번에 §4.3에 **도메인
규칙 두 문장을 추가**했다:

1. **"정책 시행일 이전의 미사용은 차감하지 않는다"** — 기준일이 시행일보다 이르면 시행일을
   기준일로 본다. 시행일은 설정값이며 기본 `2026-09-01`.
2. **"한 번의 배치 실행에서 회원 1명당 최대 1회만 차감한다"** — 밀린 주기는 다음 실행들이
   이어받는다(캐치업 유지). 상한은 설정값이며 기본 `1`.

기존 캐치업 서술("배치가 며칠 밀리면 다음 실행이 **몰아서** 차감한다")도 **"다음 실행들이 밀린
주기를 이어받아 차감한다"**로 정정했다 — 상한 도입으로 그 문장이 사실과 달라졌기 때문이다.
`docs/glossary.md`에도 새 용어 2종을 추가했다(CLAUDE.md 규칙 3).

## Decisions Made

- **`coerceAtLeast`를 `maxOrNull()` 뒤에 안전 호출로 건다** — 후보 목록에 시행일을 넣는 구현은
  "후보 전부 null이면 판정 대상 아님"을 깨뜨린다. 이 순서를 KDoc과 테스트
  (`후보가 전부 null이면 정책 시행일이 있어도 기준일은 여전히 null이다`)로 고정했다.
- **상한 절삭은 `skippedCount`가 아니라 로그다** — 05-GAP-CONTEXT의 위협 T-05D-14-03(`accept`)
  그대로. 원장(`PassTransaction`)과 기준일로 언제든 재계산 가능하므로 정보가 사라지지 않는다.
- **경계 두 케이스를 프로퍼티가 아니라 clock 이동으로 만든다** — `@SpringBootTest(properties)`는
  클래스 단위라 테스트마다 바꿀 수 없다. 클래스는 시행일 하나를 고정하고 "오늘"을 옮긴다.
- **테스트 클래스가 `POLICY_EFFECTIVE_DATE` 상수와 애노테이션 문자열을 이중으로 갖는다** —
  Kotlin 애노테이션 인자는 컴파일 타임 상수만 받아 `LocalDate` 상수를 참조할 수 없다. 두 값이
  같아야 한다는 사실을 상수 KDoc에 남겼다.

## 테스트 판단 (CLAUDE.md 규칙 10 / conventions §10.0)

두 태스크 모두 테스트를 함께 작성했다 — 면제 판단을 적용한 프로덕션 변경은 없다.

- Task 1(도메인 규칙, 잔여 횟수에 직접 영향) → **단위테스트 필수** 항목. 6종 추가 + 기존 6종 갱신
- Task 2(배치) → **멱등성 테스트** 항목. 통합테스트 7종 추가 + 기존 계약 5종 갱신
- `build.gradle.kts`는 conventions §10.0의 **면제 목록**이지만, 이 변경은 그 자체가 테스트
  인프라이므로 "값을 되돌리면 7종이 깨진다"는 실험으로 검증했다(위 대응 ②-2)

## Deviations from Plan

### 규칙 기반 보강

**1. [Rule 2 - 누락된 핵심 검증] `InactivityBatchDeductionLimitOverrideTest` 신규 (플랜에 없던 파일)**

- **Found during:** Task 2 (테스트 설계)
- **Issue:** 플랜의 테스트는 전부 상한 기본값 `1` 아래에서 돈다. 그러면 러너가
  `minOf(shortfall, properties.maxDeductionsPerRun)`이 아니라 **`minOf(shortfall, 1)`로 하드코딩돼
  있어도 100% 통과한다** — 플랜의 `key_links`(러너 → `properties.maxDeductionsPerRun`)가 검증되지
  않는다. 이 플랜이 경고하는 "초록불인데 아무것도 증명하지 못하는 테스트"의 두 번째 얼굴이다.
  더불어 상한 `1`은 **기존 계약 하나를 도달 불가로 만든다**: 회원당 `deductOnce` 호출이 최대
  1회인데 그 첫 호출은 대상 회원 벌크 조회(`findMemberIdsWithDeductibleSessionPass`)와 필터가
  동일해 단일 스레드에서는 항상 성공한다 — 즉 "대상 소진 → `skippedCount`++ → `break`" 분기가
  경쟁 없이는 실행되지 않는다.
- **Fix:** `@SpringBootTest(properties = ["…max-deductions-per-run=3"])` 전용 클래스를 만들어
  ① 상한 3에서 한 실행 3회 차감(= 상한이 설정값이라는 증명), ② 기존
  `InactivityBatchRunnerTest`의 대상 소진 스킵 계약(원래 이름·단언 그대로)을 이관했다.
  컨텍스트가 하나 늘지만(conventions §10.1), 프로퍼티 오버라이드는 본질적으로 새 컨텍스트를
  요구하고 이 두 계약은 다른 곳에서 검증할 방법이 없다.
- **Commit:** `5ab0211`

**2. [Rule 3 - 차단 이슈] `build.gradle.kts` 테스트 프로퍼티를 Task 2가 아니라 Task 1 GREEN에 넣었다**

- 플랜은 이 줄을 Task 2 (b)에 배정했지만, Task 1 GREEN에서 계산기가 하한을 적용하는 순간 러너
  호출부가 바뀌고 **모든 배치 통합테스트가 즉시 무력화**된다. Task 1을 정직하게 GREEN으로
  만들려면 같은 커밋에 있어야 했다. 러너의 `InactivityBatchProperties` 주입과 시행일 전달도 같은
  이유로 Task 1 GREEN에 포함했다(상한 적용은 Task 2에 그대로 남겼다).
- **Commit:** `858f381`

**3. [Rule 1 - 기존 계약이 새 규칙과 모순] 기존 테스트 5종 갱신 (플랜은 "갱신됐거나 여전히 통과한다"만 요구)**

상한 도입으로 계약이 실제로 바뀐 5종을 고쳤다. **총 차감량은 어디서도 줄지 않았다 — 회수만 늘었다.**

| 파일 | 기존 | 갱신 |
|---|---|---|
| `InactivityBatchIdempotencyTest` | `배치가 6주 밀리면 다음 실행이 밀린 3회를 몰아서 차감한다` (첫 실행 3회) | `…실행마다 1회씩 이어받아 밀린 3회를 결국 다 차감한다` (1,1,1,0 → 총 3회) |
| `InactivityBatchIdempotencyTest` | `캐치업 중 잔여가 부분 소진되면 스킵 1건으로 멈추고…` (한 실행 2회 + 스킵) | `…잔여만큼만 차감되고 소진 후에는 대상에서 빠진다` (1.0 → 0.5 두 실행, 3번째는 처리 인원 0) |
| `InactivityBatchIdempotencyTest` | `차감 후 관리자가 양의 가감을…` (실행 1회로 캐치업 완료) | 캐치업 완료까지 `repeat(3)` — 단언은 그대로 |
| `InactivityBatchRunnerTest` | `기준일 42일 전인 회원은 3회 차감된다(캐치업)` | `…실행 3번에 걸쳐 3회 차감된다(캐치업 + 1회 실행 상한)` — 단언 그대로 |
| `InactivityBatchRunnerTest` | `부족분이 2인데 잔여 1.0인 장 한 장뿐이면 …스킵 1건으로 루프가 멈춘다` | `부족분이 2여도 한 실행에서는 1회만 차감하고 상한으로 잘린 주기는 스킵으로 세지 않는다`(`skippedCount == 0`). **원래 계약은 Deviation 1의 상한 3 컨텍스트가 이름·단언 그대로 승계** |

## Deferred Issues

**CR-03(출석일 후보 부재)은 이번 범위 밖이다 — 운영 대응만 문서화했다.**

기준일 후보 ①(마지막 출석일)은 Phase 6이 `Attendance`를 도입하기 전까지 항상 null이라, 저녁반만
다니는 회원은 출석해도 기준일이 갱신되지 않아 부당 차감될 수 있다(ROADMAP Phase 5 Note의 설계
결정). **시행일 하한은 이 문제를 줄이지만 없애지 않는다** — 시행일 이후 2주가 지나면 같은 문제가
다시 생긴다. 그래서 **운영 배포는 `BATCH_INACTIVITY_SCHEDULER_ENABLED=false`로 cron을 꺼 둔 채**
올리고 Phase 6에서 켠다는 사실을 D-119에 남겼다. 수동 실행 API는 이 값과 무관하게 동작하므로
그때까지 실행하지 않는다.

## Issues Encountered

- **RED가 예상보다 넓게 번졌다(기록해 둘 것).** Task 2 RED에서 플랜이 예고하지 않은
  `InactivityBatchRunnerTest`의 `기준일 42일 전인 회원은 3회 차감된다(캐치업)`가 함께 깨졌다.
  플랜의 `<read_first>`에 그 파일이 없어 사전에 발견하지 못했다 — 상한처럼 **전역 기본값을 바꾸는
  변경은 grep으로 영향 범위를 먼저 훑는 편이 낫다**는 교훈.
- **OpenAPI 재생성 불필요 확인:** 컨트롤러·DTO·`@Operation`을 건드리지 않았다(05-12·05-13과 같은
  판단). 배치 API 표면이 바뀌는 05-15에서 재생성한다.
- **로컬 DB 무손실:** Testcontainers만 사용했고 로컬 `gold-wrestling` DB에 DDL·DML을 실행하지
  않았다(앱 기동도 하지 않았다). Phase 5 검증 데이터는 그대로다.
- **새 환경변수 없음:** `.env.example`의 `BATCH_INACTIVITY_POLICY_EFFECTIVE_DATE`·
  `_MAX_DEDUCTIONS_PER_RUN`은 05-12가 이미 추가했다. 이 플랜은 그 값을 **소비**했을 뿐이다.

## Verification Results

| 항목 | 결과 |
|---|---|
| `./gradlew cleanTest test` (전체 스위트) | **BUILD SUCCESSFUL** |
| `./gradlew cleanTest test --tests 'com.goldwrestling.batch.*'` | BUILD SUCCESSFUL |
| `./gradlew ktlintFormat` → `./gradlew build` | BUILD SUCCESSFUL |
| 시행일 고정을 `2026-09-01`로 되돌린 실험 | 멱등 테스트 **7/7 FAILED** (고정값이 load-bearing임을 실증) |
| `grep -c 'policyEffectiveDate: LocalDate' InactivityDueDateCalculator.kt` | 1 (≥1 충족) |
| `grep -c 'coerceAtLeast(policyEffectiveDate)' InactivityDueDateCalculator.kt` | 1 |
| `grep -c 'import org.springframework' InactivityDueDateCalculator.kt` | **0** (순수 계산 유지) |
| `grep -c '시행일' InactivityDueDateCalculatorTest.kt` | 11 (≥4 충족) |
| `grep -c 'maxDeductionsPerRun' InactivityBatchRunner.kt` | 2 (≥1 충족) |
| `grep -c 'policyEffectiveDate' InactivityBatchRunner.kt` | 2 (≥1 충족) |
| `grep -c 'policy-effective-date' build.gradle.kts` | 1 |
| `grep -c 'inactivity-scheduler-enabled' build.gradle.kts` | 1 (D-116 고정 생존) |
| `grep -v '^#' docs/policies.md \| grep -c '정책 시행일'` | 1 (≥1 충족) |
| `grep -c '^## D-119' docs/decisions.md` | 1 |
| `awk '/^## D-119/,0' docs/decisions.md \| grep -c 'CR-03'` | 1 (≥1 충족) |
| `InactivityBatchPolicyLimitTest` 줄 수 | 267 (min_lines 130 충족) |
| `grep -rn "상한 없이" docs/decisions.md` | 0건 (D-119 이전 서술과 모순 없음) |
| 커밋된 V1~V10 마이그레이션 | 이번 플랜에서 수정 0건 (스키마 변경 없음) |

## User Setup Required

None — 새 의존성·환경변수·마이그레이션 없음. 다만 **운영 배포 시점의 주의 하나**:
CR-03이 열려 있는 동안에는 `BATCH_INACTIVITY_SCHEDULER_ENABLED=false`로 cron을 꺼 둔 채 배포한다
(D-116·D-119). 시행일 기본값 `2026-09-01`이 그 사이의 2차 안전판이다.

## Next Phase Readiness

- **05-15(API):** 이 플랜은 컨트롤러를 건드리지 않았다. `runner.start` / `runStarted` 분리(05-13)와
  `InactivityBatchProperties` 주입은 그대로이므로 202 비동기 설계에 영향이 없다.
- **05-16(검증):** D-117·D-118·D-119 3건이 모두 `docs/decisions.md`에 존재한다.
  BATCH-01의 두 실패 경로 중 **소급 차감 경로는 닫혔고**, CR-03은 명시적으로 범위 밖 + 킬 스위치
  대응이라는 사실이 D-119에 기록돼 있다.
- **BATCH-01:** 완료 표시는 하지 않았다(`requirements-completed: []`) — CR-03이 열려 있는 동안
  "정책대로 차감된다"를 무조건 참이라고 말할 수 없다. 판정은 05-16이 한다.
- 블로커 없음.

## Threat Flags

없음 — 새 네트워크 엔드포인트·인증 경로·파일 접근·스키마 변경이 없다. 플랜의 위협 등록부 3건은
등록된 disposition대로 처리했다: T-05D-14-01(`mitigate`) 하한+상한으로 방어하고 테스트로 고정,
T-05D-14-02(`accept`) `.env.example`에 키 이름만, T-05D-14-03(`accept`) 절삭은 로그로만.

## 이번에 쓴 기술

**1. 외부 설정(`@ConfigurationProperties`)으로 도메인 정책 값을 빼는 것이 왜 상수보다 안전한가 ★**

`policyEffectiveDate`·`maxDeductionsPerRun`을 코드에
`private const val MAX_DEDUCTIONS = 1`처럼 두는 편이 훨씬 간단하다. 그런데 이 두 값은 **사람 개입
없이 회원 잔여를 깎는 코드가 읽는 값**이다.

- *이 코드에서 왜 필요했는가:* 값이 잘못됐다는 걸 아는 순간은 대개 **사고가 난 뒤**다. 상수라면
  고치는 절차가 "코드 수정 → PR → 머지 → 빌드 → 배포"다. 그 사이에도 매일 04:00 배치는 돈다.
  설정값이면 서버 환경변수 한 줄(`BATCH_INACTIVITY_MAX_DEDUCTIONS_PER_RUN=0`)과 재시작으로 **즉시**
  되돌릴 수 있다. 킬 스위치(D-116)가 "배치를 통째로 멈추는" 큰 스위치라면, 이 값들은 "얼마나 깎을지"를
  조절하는 다이얼이다.
- *두 번째 이점 — 환경마다 다른 값:* 프로덕션 시행일은 `2026-09-01`이지만, 테스트는 고정 시각이
  2026-08-02라 그 값으로는 아무것도 차감되지 않는다. 설정값이라 `build.gradle.kts`가 테스트
  전역에서 `2000-01-01`로 덮어쓸 수 있었다. 상수였다면 테스트를 위해 프로덕션 코드에 분기를
  넣어야 했을 것이다.
- *공짜는 아니다 — 새로 생긴 함정:* "설정에서 읽는다"고 **써 놓고** 실제로는 상수를 쓰는 구현도
  테스트를 통과할 수 있다. 모든 테스트가 기본값 아래에서 돌기 때문이다. 그래서 값을 기본값이
  **아닌 것**으로 덮어쓴 테스트를 하나 두었다(`InactivityBatchDeductionLimitOverrideTest`).
  설정으로 뺀 값에는 이 테스트가 세트로 따라와야 한다.
- *안 썼으면 뭐가 깨지는가:* 배포 첫날 잔여가 잘못 깎이는 걸 발견해도, 고치는 데 걸리는 시간
  동안 피해가 계속 누적된다.

**2. 순수 함수가 시각·설정을 파라미터로 받아야 하는 이유 ★**

`InactivityDueDateCalculator`는 스프링 빈이 아니라 Kotlin `object`다. `today`도, 이번에 추가한
`policyEffectiveDate`도 **전부 인자로 받는다.** 자연스러운 첫 발상은 여기에
`@Value("\${...policy-effective-date}")`를 붙이거나 `LocalDate.now()`를 직접 부르는 것이다.

- *이 코드에서 왜 필요했는가:* 이 object가 설정이나 현재 시각을 **스스로 가져오는** 순간
  "같은 입력이면 같은 출력"이 깨진다. 그러면 ① 이 계산을 검증하는 데 스프링 컨텍스트를 띄워야 하고
  (지금은 컨텍스트 없이 밀리초 만에 돈다), ② 어제 통과한 테스트가 오늘 실패할 수 있고,
  ③ "시행일이 미래일 때" 같은 케이스를 만들려면 시스템 시계를 조작해야 한다.
  실제로 이번에 추가한 6종 중 `정책 시행일이 미래면 …차감 수는 0이다`,
  `200일 전 등록 회원도 시행일이 30일 전이면 …14가 아니라 2다`는 **인자를 바꾸는 것만으로**
  만들었다 — DB도, 컨텍스트도, 시계 조작도 없다.
- *경계는 어디인가:* 값을 **가져오는** 책임은 스프링 빈(`InactivityBatchRunner`)에 있고,
  값으로 **판단하는** 책임은 순수 함수에 있다. 이 프로젝트에서는 이 분리를 `conventions.md §5`가
  규칙으로 못박고 있다(현재 시각은 `Clock` 빈을 주입받아 쓰고, 계산기에는 넘긴다).
- *안 썼으면 뭐가 깨지는가:* 정책 판정(=제품 그 자체)의 테스트가 느려지고 불안정해진다.
  느리고 불안정한 테스트는 결국 아무도 안 돌린다.

**3. `?.coerceAtLeast(...)` — 안전 호출의 위치가 곧 계약이다 ★**

`coerceAtLeast(x)`는 "x보다 작으면 x로 올린다"는 Kotlin 표준 함수다(하한). 코드는
`listOfNotNull(...).maxOrNull()?.coerceAtLeast(policyEffectiveDate)` 한 줄인데, **`?.`가 어디
붙느냐가 도메인 규칙을 결정한다.**

- *이 코드에서 왜 필요했는가:* 후보 5종이 전부 null인 회원은 "판정 대상 자체가 아니다"(이용권을
  막 등록해 기준일 후보가 아직 안 잡힌 경우 등). `maxOrNull()`이 null을 주면 안전 호출이 하한을
  건너뛰고 **null을 그대로 유지**한다 → 호출부가 `?: continue`로 그 회원을 건너뛴다.
  만약 후보 목록 안에 시행일을 끼워 넣었다면(`listOfNotNull(…, policyEffectiveDate)`) max가 절대
  null이 될 수 없어 **모든 회원이 판정 대상**이 되고, 후보가 없는 회원까지 시행일 기준으로 차감된다.
- *안 썼으면 뭐가 깨지는가:* "기준일이 없으면 차감하지 않는다"는 규칙이 조용히 사라진다. 코드 한 줄
  차이고 컴파일도 되고 대부분의 테스트도 통과한다 — 그래서
  `후보가 전부 null이면 정책 시행일이 있어도 기준일은 여전히 null이다` 테스트를 명시적으로 두었다.

**4. 방어 장치를 넣을 때 생기는 "테스트가 조용히 무력화되는" 실패 유형 ★**

이번 작업에서 가장 위험했던 것은 버그가 아니라 **테스트가 의미를 잃는 것**이었다.
시행일 하한(2026-09-01)을 넣는 순간, 고정 시각이 2026-08-02인 배치 테스트들의 기준일이 전부
미래로 밀려 기대 차감 수가 0이 된다 — **모든 단언이 "0회 차감"을 확인하는 참이 되어 전부 통과한다.**

- *이 코드에서 왜 필요했는가:* 통과했다는 사실만으로는 아무것도 알 수 없어서, 값을 프로덕션
  기본값으로 되돌려 **일부러 깨 봤다.** 7종이 전부 `FAILED`가 나왔고, 그제서야 "이 테스트들은
  실제 차감을 검증하고 있다"가 확인됐다. 방어 로직·필터·가드를 추가할 때는 항상 이 질문을
  던져야 한다 — **"내가 넣은 이 조건 때문에, 검증 대상 코드가 아예 실행되지 않게 된 테스트가
  있는가?"**
- *일반화:* 조건을 좁히는 변경(필터 추가, 가드 절 추가, 기본값 변경)은 테스트를 **깨뜨리기보다
  비워 버린다.** 깨지는 건 눈에 보이지만 비는 건 안 보인다.
- *안 썼으면 뭐가 깨지는가:* 멱등·캐치업이라는 이 배치의 핵심 계약이 아무 경고 없이 무방비가
  된다. 나중에 누가 차감 로직을 지워도 초록불이 유지된다.

**5. 일부러 하지 않은 것 — "배치 도입일"을 데이터에서 자동으로 알아내기**

시행일을 사람이 설정하지 않고 `batch_execution`의 **최초 행 날짜**로 자동 판정하는 방법이 있다.
매력적으로 들리지만 쓰지 않았다(D-119 기각 대안).

- 실행 이력을 지우거나 테이블을 옮기는 순간 시행일이 사라져 **소급 차감이 되살아난다.** 정책의
  근거가 "관리자가 실수로 지울 수 있는 데이터"에 매달리게 된다.
- 더 근본적으로는 D-106이 **"실행 이력을 부족분 계산의 근거로 쓰지 않는다"**를 명시적 원칙으로
  두고 있다(멱등의 유일한 근거는 원장 `PassTransaction`이다). 자동 감지는 그 원칙과 정면으로
  충돌한다. 설정값은 "사람이 정한 정책"이라는 사실을 코드에 그대로 남긴다.

---
*Phase: 05-batch*
*Completed: 2026-08-16*

## Self-Check: PASSED

- FOUND: `src/test/kotlin/com/goldwrestling/batch/InactivityBatchPolicyLimitTest.kt`
- FOUND: `src/test/kotlin/com/goldwrestling/batch/InactivityBatchDeductionLimitOverrideTest.kt`
- FOUND: `.planning/phases/05-batch/05-14-SUMMARY.md`
- FOUND: commits `e6388f1`, `858f381`, `5ab0211`, `a175db9`, `c60ad8b`
