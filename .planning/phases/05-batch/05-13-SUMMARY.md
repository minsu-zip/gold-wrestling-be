---
phase: 05-batch
plan: 13
subsystem: batch
tags: [kotlin, spring-boot, concurrency, partial-unique-index, transaction-propagation, testcontainers, scheduling]

# Dependency graph
requires:
  - phase: 05-batch (05-11)
    provides: "V10 uq_batch_execution_running 부분 유니크 인덱스, BatchExecution.finish(), RUNNING·FAILED 상태값, findFirstByStatus"
  - phase: 05-batch (05-12)
    provides: "BatchExecutionRecorder.start/finish(REQUIRES_NEW), BatchAlreadyRunningException(409), stale RUNNING 정리(30분)"
provides:
  - "InactivityBatchRunner: start(시작·직렬화 진입) / runStarted(본문+확정) / run(둘을 이은 기존 진입점)으로 3분할 — CR-01이 실행 경로에 실제로 배선됐다"
  - "성공·부분실패·전체실패 모든 종료 경로에서 batch_execution 행이 확정된다(WR-02) — RUNNING 방치 0건"
  - "InactivityBatchScheduler: 중복 실행 거부는 info, 그 밖의 예외는 error로 흡수 — cron 밖으로 예외가 나가지 않는다"
  - "InactivityBatchRunConcurrencyTest: 동시 run() 4개에서 총 INACTIVITY 차감 1회를 실증(BATCH-04)"
  - "InactivityBatchFailureIsolationTest: 벌크 조회 실패 시 FAILED 이력 1건 + 재실행 가능 검증 2종 추가(총 8종)"
  - "docs: D-108 해소 기록(동시 실행 안전해짐, 분산 락 미도입 근거 교체), D-112 보강(REQUIRES_NEW 분리 근거)"
affects: [05-14, 05-15, 05-16]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "실행 오케스트레이터는 '시작(직렬화 진입) / 본문 / 확정' 3구간으로 나눈다 — 시작만 떼어 두면 05-15의 비동기 API가 '동기로 거부 판정 → 202 반환 → 본문은 백그라운드'를 추가 설계 없이 얹을 수 있다"
    - "집계 변수를 try 밖에 선언해 전체 실패 이력에도 진행 상황(처리 인원·차감 횟수)을 남긴다"
    - "복구 기록(FAILED 확정)은 runCatching으로 감싼다 — 복구 실패가 원인 예외를 가리면 로그에서 진짜 원인이 사라진다"
    - "러너 레벨 동시성 테스트는 '예외 건수'가 아니라 '총 차감 횟수'를 단언한다 — 겹치지 않은 실행은 거부 대신 부족분 0을 계산하므로(D-106) 예외 건수는 스케줄링에 따라 흔들리지만 총 차감은 항상 1회다"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunConcurrencyTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt
    - src/main/kotlin/com/goldwrestling/batch/InactivityBatchScheduler.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchFailureIsolationTest.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchSchedulerTest.kt
    - docs/decisions.md

key-decisions:
  - "run()의 시그니처·반환 타입을 유지한 채 내부만 3분할했다 — 기존 호출부 6곳(cron·수동 실행 API·통합테스트 4종)이 한 줄도 바뀌지 않는다"
  - "관리자 해석(resolveTriggeredByAdminId)이 recorder.start()보다 앞이다. 순서 자체가 계약 — MANUAL 거부는 이력을 한 줄도 남기지 않아야 한다"
  - "errorSummary는 전체 실패 경로에서도 예외 종류만 담는다(e.javaClass.simpleName) — 관리자 API 응답으로 그대로 나가므로 메시지를 담으면 제약조건명·SQL이 샌다"
  - "동시성 테스트는 '예외가 정확히 1건'을 단언하지 않는다 — 플레이키 회피이자, 총 차감 1회가 BATCH-04의 실제 불변식이기 때문"
  - "AdminBatchController의 openapi 설명('동시 실행은 아직 안전하지 않다')은 이 플랜에서 고치지 않는다 — 05-15가 같은 청크에서 202 비동기로 바꾸며 다시 쓴다(아래 Deferred 참조)"

patterns-established:
  - "예외 경로를 검증하는 통합테스트의 실행 이력 정리는 baseline(max(id)) 기준으로 한다 — run()이 예외를 던지면 호출부가 id를 받지 못해 반환값 기반 정리가 불가능하다"
  - "동시성 테스트 픽스처 대역 정리는 하한이 아니라 범위(>= base and < max)로 좁힌다(WR-07)"

requirements-completed: []

duration: 25min
completed: 2026-08-16
---

# Phase 05 Plan 13: 실행 경로 배선 — 시작·본문·확정 3분할과 동시성 실증 Summary

**`InactivityBatchRunner.run()`을 시작(`RUNNING` 삽입) → 본문 → 확정 세 구간으로 나눠 05-11·05-12가 만들어 둔 인덱스·레코더를 실행 경로에 실제로 배선하고, 동시 `run()` 4개에서 총 `INACTIVITY` 차감이 1회임을 실제 PostgreSQL로 실증해 CR-01·WR-02를 닫았다.**

## Performance

- **Duration:** 약 25분 (01:50~02:15)
- **Tasks:** 3 (Task 1·2는 TDD RED/GREEN 분리 커밋)
- **Files:** 5 (신규 1, 수정 4)

## Accomplishments

### Task 1 — 러너 3분할 (CR-01·WR-02의 본체)

- `start(trigger, adminId)` — 관리자 해석을 **먼저** 한 뒤 `recorder.start(...)`로 `RUNNING` 행을
  즉시 커밋한다. 이 INSERT가 `uq_batch_execution_running`(V10)을 통과해야만 본문이 돈다 —
  **직렬화 진입점**이다. `BatchAlreadyRunningException`은 잡지 않고 전파한다(거부를 어떻게
  표현할지는 호출부가 정한다: cron은 로그, HTTP는 409).
- `runStarted(executionId)` — 기존 본문을 그대로 옮기고 전체를 `try/catch`로 감쌌다.
  집계 변수 4종을 `try` **밖**에 선언해 전체 실패 시에도 그 시점까지의 진행 상황이 이력에 남는다.
  정상 종료면 `SUCCESS`/`PARTIAL_FAILURE`로, 예외면 `logger.error` 후 `FAILED`로 확정하고
  **원래 예외를 다시 던진다.** 확정 기록 자체가 또 실패하는 경우는 `runCatching`으로 감싸
  로그만 남긴다 — 복구 실패가 원인 예외를 가리지 않게 하기 위해서다(T-05D-13-02).
- `run(trigger, adminId)` = `start(...)` → `runStarted(id)`. **시그니처·반환 타입 불변**이라
  기존 호출부(cron·`AdminBatchController`·통합테스트 4종)가 한 줄도 바뀌지 않았다.
- `batchExecutionRepository` 의존성을 러너에서 제거했다 — 삽입·확정은 이제 레코더가 소유한다.
- **IN-01 정리**: 사용하지 않던 루프 인덱스 `attempt`를 `while` + `break` 형태로 바꾸고,
  왜 `repeat`을 쓸 수 없는지(람다 안에서 `break` 불가)를 주석에 남겼다.
- 클래스 KDoc에 ① 3구간 흐름과 직렬화가 DB 제약으로 이뤄진다는 것(D-117), ② 러너에 여전히
  `@Transactional`이 없고 시작·확정만 별도 빈의 `REQUIRES_NEW`라는 것(D-112), ③ `batch_execution`을
  부족분 계산에 쓰지 않는다는 D-106이 **여전히 유효**하다는 것을 명시했다.

### Task 2 — cron이 거부·실패를 삼킨다

- `runDaily()`가 `BatchAlreadyRunningException`은 `logger.info`(수동 실행과 겹친 **정상 경로**,
  건너뛴 주기는 D-106 캐치업이 보정), 그 밖의 `Exception`은 `logger.error`로 남기고 흡수한다.
  이력은 러너가 이미 `FAILED`로 남겼으므로 여기서 추가 기록을 하지 않는다.
- KDoc에서 "차감 원자성 보장은 갭 클로저에서 정한다"는 미완 서술을 제거하고, **분산 락(ShedLock)
  미도입 근거를 교체**했다 — "단일 EC2 인스턴스"가 아니라 "DB 제약이 인스턴스 수와 무관하게
  막는다". 예외 흡수는 로깅이지 로직이 아니라는 원칙(스케줄러는 트리거만 한다)도 함께 적었다.

### Task 3 — 동시성 실증 (BATCH-04)

`InactivityBatchRunConcurrencyTest` 2종:

| 테스트 | 무엇을 고정하나 |
|---|---|
| `이미 실행 중이면 run이 거부되고 잔여와 이력이 전혀 변하지 않는다` | **결정론** — `RUNNING` 행을 미리 심어 거부 경로를 반드시 밟는다. 아래 경쟁 테스트가 우연히 겹치지 않아도 직렬화 장치가 사라지면 여기서 먼저 깨진다 |
| `네 스레드가 동시에 run을 호출해도 총 INACTIVITY 차감은 1회다` | 원장 이력 1건 / 잔여 3.0→2.0 / 실행 이력 `deductedCount` 합 1 / 실패는 전부 `BatchAlreadyRunningException` / 종료 후 `RUNNING` 0건 |

**"예외가 정확히 1건"을 단언하지 않는다.** 두 실행이 시간상 겹치면 뒤가 거부되지만, 앞이 아주
빨리 끝나면 뒤는 거부되지 않고 시작해 대신 **부족분 0**을 계산한다(D-106). 스케줄링에 따라 둘 중
어느 쪽이든 나오고 **어느 쪽이든 총 차감은 1회**다 — 그것이 BATCH-04의 불변식이고 예외 건수는
아니다. 예외 건수를 단언하면 CI 부하에 따라 깜빡이는 테스트가 된다. 이 판단을 테스트 KDoc에
남겼고, 반복 실행 3회로 안정성을 확인했다.

`docs/decisions.md`:
- **D-108 해소(2026-08-16)** — 동시 실행 이중 차감이 닫혔다는 것, 실증 근거가
  `InactivityBatchRunConcurrencyTest`라는 것, "분산 락 불필요"라는 **결론은 유지하되 근거가 바뀌었다**는 것
  (인스턴스 수가 아니라 DB 제약이므로 다중 인스턴스가 되어도 같은 보장), stale `RUNNING` 약점과
  회수 수단, 그리고 WR-02(전체 실패 무기록)가 함께 닫혔다는 것.
- **D-112 보강(2026-08-16)** — 러너에 여전히 `@Transactional`이 없고 시작·확정만 `REQUIRES_NEW`라는 것.
  이 분리가 없으면 실패 격리와 직렬화가 **동시에** 깨진다는 것(참여 트랜잭션이면 `RUNNING` 행이
  배치 종료까지 커밋되지 않아 유니크 인덱스가 발동하지 못한다)과, 러너 내부 메서드로 두면
  self-invocation으로 애노테이션이 조용히 무시된다는 것.

## Task Commits

| Task | 내용 | 커밋 | 종류 |
|---|---|---|---|
| 1 (RED) | 전체 실패의 FAILED 이력 확정 테스트 | `49de897` | test |
| 1 (GREEN) | 러너 3분할 + IN-01 정리 | `7acc7c8` | feat |
| 2 (RED) | cron 거부·실패 흡수 테스트 | `6470256` | test |
| 2 (GREEN) | 스케줄러 예외 흡수 + KDoc 근거 교체 | `50d215b` | feat |
| 3 | 동시성 테스트 2종 + D-108 해소·D-112 보강 | `681596f` | test |

TDD 게이트: Task 1·2 모두 `test(...)`(RED — 실제 실패 확인) → `feat(...)`(GREEN) 순서를 지켰다.
Task 3은 **테스트 자체가 산출물**이라 RED/GREEN 분리가 성립하지 않는다 — 검증 대상 구현이 Task 1에서
이미 완성됐고, 이 테스트는 05-VERIFICATION.md의 판정을 반증하는 실증 장치다. REFACTOR 단계는
정리할 중복이 없어 커밋하지 않았다.

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt` — `start`/`runStarted`/`run` 3분할,
  `BatchExecutionRecorder` 주입(`batchExecutionRepository` 제거), 전체 실패 `FAILED` 확정,
  `attempt` 인덱스 제거, KDoc 전면 갱신
- `src/main/kotlin/com/goldwrestling/batch/InactivityBatchScheduler.kt` — `try/catch` 2단(거부는 info,
  그 외는 error), `logger` 추가, 분산 락 근거 교체 및 미완 서술 제거
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunConcurrencyTest.kt` — 신규.
  동시성 테스트 2종, 트랜잭션 애노테이션 0개, 픽스처 대역 `9_740_000_000L` + 범위 정리
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchFailureIsolationTest.kt` — `memberRepository`를
  `@MockitoSpyBean`으로 전환, 전체 실패 검증 2종 추가(기존 6종 계약 그대로), baseline 기반 이력 정리
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchSchedulerTest.kt` — 예외 흡수 검증 2종 추가(총 5종)
- `docs/decisions.md` — D-108 해소 3항목, D-112 보강 1항목

## Decisions Made

- **`run()` 시그니처 유지** — 플랜의 요구이자 회귀 방어다. 기존 통합테스트 4종
  (`InactivityBatchRunnerTest`·`InactivityBatchIdempotencyTest`·`InactivityBatchExpiryVerificationTest`·
  `InactivityLeaveReturnTest`)이 러너 흐름 변경의 회귀 게이트 역할을 그대로 해 준다.
- **관리자 해석을 `recorder.start()` 앞에 둔다** — 순서가 곧 계약이다. 뒤집으면 잘못된 `MANUAL`
  요청이 `RUNNING` 행을 만들었다가 확정하는 흔적을 남겨 `InactivityBatchFailureIsolationTest`의
  기존 2건("이력을 남기지 않고 거부한다")이 깨진다.
- **성공 경로의 `recorder.finish()`도 `try` 안에 둔다** — 확정 기록이 실패하면 그 예외가 바깥
  `catch`로 잡혀 `FAILED` 확정을 한 번 더 시도한다. 그마저 실패해도 `runCatching`이 흡수하고 원래
  예외를 던지므로, 최악의 경우에도 원인이 로그에 남고 방치된 `RUNNING` 행은 stale 정리가 회수한다.
- **`@MockitoSpyBean` 추가가 컨텍스트를 늘리지 않는다** — `InactivityBatchFailureIsolationTest`는
  이미 스파이 빈 때문에 전용 컨텍스트를 쓰고 있어(다른 배치 통합테스트와 캐시가 갈려 있음)
  두 번째 스파이를 추가해도 새 컨텍스트가 생기지 않는다(conventions §10.1).
- **동시성 테스트의 예외 주입 매처는 `anyCollection()`** — `findReturnedFromLeaveTimestamps`의
  파라미터가 `Collection<Long>`이고, 실제 `memberIds` 리스트를 그대로 매칭하면 다른 테스트 클래스의
  잔여 데이터에 따라 리스트 내용이 달라져 깨질 수 있다.

## 테스트 판단 (CLAUDE.md 규칙 10 / conventions §10.0)

세 태스크 모두 테스트를 함께 작성했다 — 면제 판단을 적용한 변경은 없다.

- Task 1(러너, 잔여 횟수에 직접 영향) → 통합테스트 2종 추가 + 기존 통합테스트 4종이 회귀 게이트
- Task 2(스케줄러 위임 + 예외 흡수) → 스프링 없는 단위테스트 2종 추가
- Task 3(동시성이 걸린 경로) → conventions §10.4 "동시성 테스트 필수" 이행, 2종 추가

**`InactivityDeductionConcurrencyTest`의 기존 계약은 그대로 유효하다**(05-GAP-CONTEXT §3의 재판단
요구 항목). 그 테스트는 `deductOnce` **한 단계**의 조건부 UPDATE 계약("잔여 2회를 세 스레드가
경쟁하면 정확히 두 번 성공")을 고정한다 — 이번 변경은 그 위 계층(러너 진입)에 직렬화를 추가했을
뿐 `deductOnce`의 계약을 바꾸지 않았고, 실제로 두 테스트가 함께 통과한다.

## Deviations from Plan

**1. [상위 플랜 이탈 흡수] `BatchExceptions.kt` → `BatchAlreadyRunningException.kt`**

- 플랜 Task 2의 `<read_first>`가 `src/main/kotlin/com/goldwrestling/batch/BatchExceptions.kt`를
  참조하지만, 05-12가 ktlint `standard:filename` 때문에 파일명을 `BatchAlreadyRunningException.kt`로
  바꿨다(05-12-SUMMARY Deviation 1). 실제 파일명으로 읽고 진행했다 — 새 파일을 만들지 않았다.
- 05-15가 `BatchExecutionNotFoundException`을 추가할 때 두 클래스를 `BatchExceptions.kt`로
  합치면 플랜의 원래 형태로 돌아온다(그 시점엔 단일 클래스가 아니라 규칙이 적용되지 않는다).

**2. [상위 플랜 이탈 흡수] 이력 모델이 이미 "시작 시 삽입 → 종료 시 확정"이었다**

- 05-11이 `BatchExecution.finish()`와 nullable `finishedAt`을, 05-12가 `BatchExecutionRecorder`를
  이미 만들어 두었다. 이 플랜은 **중복 생성 없이 배선만** 했다 — `recorder.start`/`finish`를
  그대로 호출하고, `startedAt`·`finishedAt` 채우기와 stale 정리는 레코더에 맡겼다.
  그 결과 러너에서 `OffsetDateTime.now(clock)` 호출이 사라졌다(`clock`은 `today` 계산에만 쓴다).

**3. [Rule 2 - 회귀 방어 보강] `InactivityBatchFailureIsolationTest`의 이력 정리를 baseline 기반으로 확장**

- **Found during:** Task 1
- **Issue:** 플랜은 새 테스트 2개만 요구했으나, 전체 실패 경로는 `run()`이 예외를 던져 호출부가
  실행 이력 id를 받지 못한다 — 기존의 `createdBatchExecutionIds` 방식으로는 **정리할 수 없다.**
  정리되지 않은 행이 `FAILED`라 당장은 무해하지만, 확정 기록까지 실패하는 미래의 케이스에서
  `RUNNING`이 남으면 같은 컨테이너를 쓰는 다른 테스트 클래스의 배치가 전부 409로 막힌다(T-05D-11-01).
- **Fix:** `BatchExecutionRecorderTest`의 관례대로 `@BeforeEach`에서 `max(id)` baseline을 찍고
  `@AfterEach`에서 `id > baseline` 행을 지운다. 기존 6개 테스트의 본문은 건드리지 않았다.
- **Commit:** `49de897`

## Deferred Issues

**`AdminBatchController`의 `@Operation` 설명이 사실과 달라졌다 — 05-15가 닫는다.**

이 플랜 이후 "동시 실행은 아직 안전하지 않다 … 이전 응답을 받기 전에는 다시 호출하지 않는다"는
서술은 **거짓**이다(이제 겹치면 409로 거부된다). 하지만 05-GAP-CONTEXT §2.4가 이 컨트롤러를
**202 비동기 + 조회 API 2개**로 바꾸는 작업을 05-15에 배정했고, 그 시점에 설명 전문을 다시 쓰면서
`docs/api/openapi.yaml`을 재생성한다. 지금 고치면 같은 파일을 두 번 재생성하게 되므로 미뤘다.

- **같은 청크(`feature/phase-05d-gap-closure`) 안이라 이 서술이 dev에 잘못된 채로 머지되지 않는다.**
- 05-16의 검증 항목에 이 문장이 남아 있지 않은지 확인이 필요하다.
- 이 플랜은 컨트롤러·DTO·`@Operation`을 건드리지 않았으므로 **openapi.yaml 재생성 불필요**
  (05-12와 같은 판단 — `ErrorCode` 추가는 스키마 enum이 아니라 설명 문장으로만 등장한다).

## Issues Encountered

- **Task 1의 RED가 반쪽이었다(기록해 둘 것).** 새 테스트 2개 중 `벌크 조회가 실패하면 FAILED
  이력 1건이 남고…`만 RED에서 실패했고, `전체 실패 뒤에도 RUNNING 행이 남지 않아…`는 **변경 전에도
  통과했다** — 당시 러너는 `RUNNING` 행을 아예 만들지 않았기 때문이다(공허하게 참). 이 테스트는
  변경 **이후**부터 의미를 갖는 회귀 가드다. 처음부터 실패하는 테스트만 남기려 했다면 이 케이스는
  쓸 수 없었지만, "확정 기록이 빠지면 즉시 깨진다"는 보호가 실제로 필요해 유지했다.
- 그 외 이슈 없음. 첫 시도에 컴파일·통과했고 ktlint 위반도 없었다.
- **로컬 DB 무손실:** 테스트는 Testcontainers만 사용했고 로컬 `gold-wrestling` DB에 DDL·DML을
  실행하지 않았다(앱 기동도 하지 않았다). Phase 5 검증 데이터는 그대로다.

## Verification Results

| 항목 | 결과 |
|---|---|
| `./gradlew cleanTest test --tests '…FailureIsolationTest' '…RunnerTest' '…IdempotencyTest'` | BUILD SUCCESSFUL (30/30, 멱등 7종 포함) |
| `./gradlew cleanTest test --tests '…SchedulerTest'` | BUILD SUCCESSFUL (5/5) |
| `./gradlew cleanTest test --tests '…RunConcurrencyTest'` | BUILD SUCCESSFUL (2/2), **반복 3회 모두 통과**(플레이키 아님) |
| `./gradlew ktlintFormat` → `./gradlew cleanTest test` (전체 스위트) | BUILD SUCCESSFUL |
| `./gradlew ktlintFormat` → `./gradlew cleanTest build` | BUILD SUCCESSFUL |
| `grep -rn "차감 원자성 보장은 갭 클로저에서 정한다" src/` | 0건 |
| `grep -c 'fun runStarted(' InactivityBatchRunner.kt` | 1 |
| `grep -c 'recorder.start(\|recorder.finish(' InactivityBatchRunner.kt` | 3 (start 1 + finish 2) |
| `grep -c 'batchExecutionRepository.save' / '@Transactional' / 'for (attempt in' InactivityBatchRunner.kt` | 0 / 0 / 0 |
| `grep -c 'BatchExecutionStatus.FAILED' InactivityBatchRunner.kt` | 1 |
| `grep -c 'BatchAlreadyRunningException' / 'catch' InactivityBatchScheduler.kt` | 1 / 3 |
| `grep -c '동시 실행은 아직 안전하지 않' InactivityBatchScheduler.kt` | 0 |
| `grep -c 'fun \`' InactivityBatchSchedulerTest.kt` | 5 |
| `grep -c 'CountDownLatch' / '@Transactional' / 'kakao_id < :max' / '9_740_000_000L' RunConcurrencyTest.kt` | 3 / 0 / 3 / 1 |
| RunConcurrencyTest 줄 수 | 275 (min_lines 150 충족) |
| `awk '/^## D-108/,/^## D-109/' docs/decisions.md \| grep -c '2026-08-16'` | 3 |
| `awk '/^## D-112/,/^## D-113/' docs/decisions.md \| grep -c 'REQUIRES_NEW'` | 2 |
| 커밋된 V1~V10 마이그레이션 | 이번 플랜에서 수정 0건 (스키마 변경 없음) |

## User Setup Required

None — 새 의존성·환경변수·마이그레이션 없음.

## Next Phase Readiness

- **05-14(캐치업 상한·정책 시행일):** 러너 루프가 `while (remainingShortfall > 0)` 형태로 바뀌었다 —
  `minOf(shortfall, maxDeductionsPerRun)` 적용 지점은 `shortfallCount` 계산 직후 한 곳이다.
  `InactivityBatchProperties`는 05-12가 준비했고 D-119 기록도 05-14 몫이다.
- **05-15(API):** `runner.start(...)`가 **이미 분리돼 있다** — 컨트롤러가 `start`를 동기로 호출해
  409/202를 결정하고, `runStarted(id)`를 전용 실행기에 넘기면 §2.4의 비동기 설계가 그대로 얹힌다.
  러너를 다시 손볼 필요가 없다. `AdminBatchController`의 `@Operation` 설명 전문 재작성 +
  `openapi.yaml` 재생성이 05-15의 필수 작업이다(위 Deferred Issues).
- **BATCH-04:** 실행 경로 배선과 동시성 실증이 끝났다. 다만 **HTTP 계층의 거부(동시 POST 2건 중
  정확히 하나만 성공)는 05-15가 실증**하므로, 요구사항 완료 표시는 05-16에서 한다
  (`requirements-completed: []` 유지).
- 블로커 없음.

## Threat Flags

없음 — 새 네트워크 엔드포인트·인증 경로·파일 접근·스키마 변경이 없다. 플랜의 위협 등록부
4건(T-05D-13-01·02·03·04)은 모두 `mitigate`대로 구현됐고 각각 테스트로 고정됐다.

## 이번에 쓴 기술

**1. DB 유니크 제약으로 "실행"을 직렬화한다 — 애플리케이션 락과 무엇이 다른가 ★**

우리가 막아야 하는 것은 "같은 순간에 미사용 차감 배치가 두 벌 도는 것"이다. 흔한 해법은
**애플리케이션 락**이다 — JVM 안에 `synchronized`나 `ReentrantLock`을 두고 배치 진입을 한 명만
통과시키는 방식.

- *이 코드에서 왜 DB 제약을 골랐는가:* JVM 락은 **그 JVM 안에서만** 유효하다. 지금은 EC2가 한
  대라 우연히 맞지만, 서버를 두 대로 늘리는 순간 각 JVM이 자기 락만 보고 둘 다 통과시킨다 —
  방어가 조용히 사라지고, 코드는 한 줄도 안 바뀌었으므로 아무도 눈치채지 못한다.
  대신 우리는 `batch_execution`에 "지금 도는 배치" 행을 넣고, PostgreSQL에
  `CREATE UNIQUE INDEX ... WHERE status = 'RUNNING'`(부분 유니크 인덱스)을 걸었다. 두 번째 실행이
  같은 행을 넣으려 하면 **DB가 INSERT를 거부한다.** 판정 주체가 애플리케이션이 아니라 DB이므로
  서버가 몇 대든, 어느 스레드든 결과가 같다. 그래서 D-108의 "분산 락이 필요 없다"는 결론은
  유지되지만 **근거가 '인스턴스가 하나여서'에서 'DB가 막아서'로 바뀌었다.**
- *또 하나의 차이 — 관측 가능성:* 락은 눈에 보이지 않는다. `RUNNING` 행은 `select * from
  batch_execution where status='RUNNING'` 한 줄로 "지금 뭐가 돌고 있는지"를 운영자가 직접 볼 수
  있고, 그 행이 그대로 실행 이력이 된다(시작·종료 시각·집계).
- *안 썼으면 뭐가 깨지는가:* 새벽 04:00 cron과 관리자의 수동 실행 버튼이 겹치는 순간, 두 실행이
  각자 "이 회원은 2주 미사용, 부족분 1"을 읽고 **각자 1회씩 깎는다.** 회원 잔여가 근거 없이
  2회 줄고, 이 프로젝트의 Core Value("회원이 보는 잔여 = 실제 사용 가능 횟수")가 무너진다.
- *공짜는 아니다:* 앱이 갑자기 죽으면 `RUNNING` 행이 남아 배치를 **영원히** 막는다(락을 쥔 채
  죽은 것과 같다). 그래서 30분을 넘긴 행은 `FAILED("STALE")`로 회수한다(05-12).

**2. 동시성 테스트에 `@Transactional`을 붙이면 왜 경쟁이 재현되지 않는가 ★**

스프링 테스트에 `@Transactional`을 붙이면 편하다 — 테스트가 끝날 때 자동 롤백돼서 뒷정리가 필요
없다. 그런데 이번 테스트에는 **쓸 수 없다.**

- *이 코드에서 왜 필요했는가:* 우리가 증명하려는 것은 "스레드 A가 넣은 `RUNNING` 행을 **스레드
  B가 본다**"이다. 그런데 커밋되지 않은 행은 **다른 트랜잭션에게 보이지 않는다**(PostgreSQL의
  기본 격리 수준 READ COMMITTED). 테스트 전체를 하나의 트랜잭션으로 감싸면 A의 INSERT가 끝까지
  커밋되지 않고, B는 빈 테이블을 보고 자기 행을 넣는 데 성공한다 — **유니크 인덱스가 발동하지
  않는다.** 그러면 테스트는 "두 실행이 다 통과했다"는 결과를 내고, 우리는 방어가 없다고
  오판하거나(거짓 실패) 더 나쁘게는 잘못된 단언으로 초록불을 본다.
- 같은 이유로 스레드가 도는 도중에 테스트 스레드가 만든 픽스처도 커밋돼 있어야 한다. 그래서
  픽스처를 `saveAndFlush`로 넣고 `@AfterEach`에서 **직접 지운다**(conventions §10.4, add-domain-test §4).
- *안 썼으면 뭐가 깨지는가:* 통과하는데 아무것도 증명하지 못하는 테스트가 남는다 — 방어 장치를
  나중에 누가 지워도 초록불이 유지되는, 가장 위험한 형태의 테스트다.

**3. 예외 안전한 상태 확정 — `try/catch` + 재전파 + `runCatching` ★**

`RUNNING` 행을 먼저 넣는 설계에는 대칭 의무가 생긴다: **무슨 일이 있어도 그 행을 끝내야 한다.**
끝내지 못한 행 하나가 이후 모든 배치를 막기 때문이다.

- *이 코드에서 왜 필요했는가:* 본문 전체를 `try`로 감싸고, 예외가 나면 이력을 `FAILED`로 확정한
  뒤 **원래 예외를 다시 던진다**(`throw e`). 그냥 삼키면 호출부는 성공으로 착각한다.
  그리고 확정 기록 자체가 실패할 수도 있어서(DB가 통째로 죽은 경우) 그 부분만 `runCatching`으로
  감싸 로그만 남긴다 — 여기서 새 예외를 던지면 **원인 예외가 그 예외에 가려져** 로그에 "이력
  확정 실패"만 남고 진짜 원인(벌크 조회 실패 등)은 사라진다.
- *집계 변수를 `try` 밖에 선언한 이유도 같은 맥락이다:* 100명 중 40명을 처리한 뒤 터졌다면
  `FAILED` 이력에 "40명 처리, 12회 차감"이 남아야 운영자가 복구 범위를 안다. `try` 안에
  선언하면 `catch`에서 그 값에 접근할 수 없다.
- *안 썼으면 뭐가 깨지는가:* 배치가 한 번 터진 뒤로 모든 실행이 409로 막히고, 아무도 이유를
  모른 채 며칠 뒤 "잔여가 안 깎인다"로 발견된다.

**4. 스케줄러에서 예외를 삼키는 것이 왜 "숨기기"가 아닌가**

보통 예외를 `catch`하고 넘어가는 것은 나쁜 습관이지만, `@Scheduled` 메서드는 예외적이다.

- *이 코드에서 왜 필요했는가:* `@Scheduled` 메서드에서 예외가 밖으로 나가면 **받아 주는 호출부가
  없다** — 스프링 기본 핸들러가 스택 한 덩어리를 찍고 끝난다. 그래서 여기서 원인별로 분류해
  남긴다. 특히 `BatchAlreadyRunningException`은 **에러가 아니라 정상 경로**라 `info`로 남긴다 —
  관리자 수동 실행과 겹쳤을 뿐이고, 건너뛴 주기는 다음 실행이 상태 기반으로 보정한다(D-106).
  이걸 `error`로 남기면 운영자가 매번 오탐을 보게 되고, 결국 로그를 안 보게 된다.
- *안 썼으면 뭐가 깨지는가:* "그날 배치가 왜 안 됐는지"를 로그에서 되짚을 수 없다.

**5. 일부러 하지 않은 것 — 실행 이력으로 "오늘 이미 돌았나"를 판단하기**

`batch_execution`에 실행 기록이 생겼으니 "오늘 이미 SUCCESS 행이 있으면 건너뛰자"가 자연스러운
발상이다. **하지 않았다**(D-106·D-108).

- 부족분의 근거는 오직 **원장(`pass_transaction`)의 `INACTIVITY` 건수**다. 실행 이력으로 판단하면
  배치가 3일 죽었다가 살아났을 때 "오늘은 돌았으니 끝"이 되어 밀린 주기를 영영 따라잡지 못한다.
  지금 구조에서는 밀린 만큼 자동으로 캐치업된다.
- 이 원칙이 동시성 테스트의 단언 방식까지 설명한다 — 두 번째 실행이 거부되지 **않고** 시작해도
  원장을 다시 읽어 부족분 0을 계산하므로 총 차감은 여전히 1회다. 두 방어선이 같은 답으로 수렴한다.

---
*Phase: 05-batch*
*Completed: 2026-08-16*

## Self-Check: PASSED

- FOUND: `src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunConcurrencyTest.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/batch/InactivityBatchScheduler.kt`
- FOUND: `.planning/phases/05-batch/05-13-SUMMARY.md`
- FOUND: commits `49de897`, `7acc7c8`, `6470256`, `50d215b`, `681596f`
