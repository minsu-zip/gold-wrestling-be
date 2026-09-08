---
phase: 05-batch
plan: 15
subsystem: batch
tags: [kotlin, spring-boot, async, task-executor, http-202, openapi, concurrency, testcontainers, mockito-bean]

# Dependency graph
requires:
  - phase: 05-batch (05-11)
    provides: "V10 uq_batch_execution_running, BatchExecution의 LAZY 연관 0개(WR-04) — 트랜잭션 밖 DTO 변환 안전, findAllByOrderByStartedAtDesc(Pageable)"
  - phase: 05-batch (05-12)
    provides: "BatchExecutionRecorder.start/finish(REQUIRES_NEW), BatchAlreadyRunningException(409), stale RUNNING 정리"
  - phase: 05-batch (05-13)
    provides: "InactivityBatchRunner.start / runStarted 분리 — 이 플랜은 러너를 다시 손대지 않고 그 위에 비동기 API만 얹었다"
provides:
  - "BatchExecutorConfig: 배치 본문 전용 단일 스레드 실행기(inactivityBatchExecutor, 큐 1). defaultCandidate = false로 스프링 부트의 applicationTaskExecutor를 밀어내지 않는다"
  - "AdminBatchService: launchInactivityRun(시작 동기 + 본문 비동기) / getExecution / listRecentExecutions(limit 1..100 보정)"
  - "엔드포인트 3종: POST(202 Accepted + Location) · GET 단건(폴링) · GET 목록(최근 실행순)"
  - "ErrorCode.BATCH_EXECUTION_NOT_FOUND(404) + BatchExecutionNotFoundException — 배치 예외 2종을 BatchExceptions.kt로 통합"
  - "AdminBatchRunConcurrencyTest: 동시 POST에서 202 정확히 1건·나머지 409를 HTTP 계층에서 실증(BATCH-04)"
  - "재생성된 docs/api/openapi.yaml — 202·409·GET 2종 반영, 잘못된 안전성 서술 제거"
  - "docs: D-114 갱신(엔드포인트 3종·202·전용 실행기·IN-03 정리), error-codes.md 배치 절 1행"
affects: [05-16]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "장시간 작업은 '시작(직렬화 진입)만 동기 + 본문은 전용 실행기 + 202 Accepted + Location 폴링'으로 접수한다 — 이 저장소의 첫 비동기 API 사례"
    - "전용 TaskExecutor 빈은 @Bean(defaultCandidate = false)로 등록한다 — 그러지 않으면 부트의 applicationTaskExecutor(@ConditionalOnMissingBean(Executor))가 조용히 사라진다"
    - "서비스는 실행기를 구현 타입이 아니라 TaskExecutor 인터페이스 + @Qualifier로 받는다 — 테스트가 무동작 모의로 대체해 '실행 중' 상태를 결정론적으로 붙잡을 수 있다"
    - "@ApiResponses의 에러 응답에는 content = [Content()]를 준다 — 비우면 springdoc이 성공 스키마를 오류 본문으로 잘못 붙인다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/config/BatchExecutorConfig.kt
    - src/main/kotlin/com/goldwrestling/batch/AdminBatchService.kt
    - src/main/kotlin/com/goldwrestling/batch/BatchExceptions.kt
    - src/test/kotlin/com/goldwrestling/batch/AdminBatchServiceTest.kt
    - src/test/kotlin/com/goldwrestling/batch/AdminBatchRunConcurrencyTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/batch/AdminBatchController.kt
    - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
    - src/test/kotlin/com/goldwrestling/batch/AdminBatchControllerTest.kt
    - docs/api/openapi.yaml
    - docs/error-codes.md
    - docs/decisions.md
  deleted:
    - src/main/kotlin/com/goldwrestling/batch/BatchAlreadyRunningException.kt

key-decisions:
  - "D-114 갱신: 수동 실행 API가 엔드포인트 1개 → 3개, 200 동기 → 202 비동기 + 폴링. 동기 호출이 CR-01의 현실적 트리거였다는 것(WR-05)과 전용 단일 스레드 실행기를 근거와 함께 기록"
  - "전용 실행기를 @Bean(defaultCandidate = false)로 등록한다 — 부트의 applicationTaskExecutor는 @ConditionalOnMissingBean(Executor) 조건이라 표시가 없으면 우리 빈 하나 때문에 통째로 사라진다(실제로 남아 있음을 테스트로 고정)"
  - "launchInactivityRun은 Propagation.NOT_SUPPORTED — readOnly 트랜잭션 안에서 레코더의 REQUIRES_NEW가 중첩되는 상황을 아예 만들지 않는다"
  - "동시성 테스트는 실행기 빈을 무동작 @MockitoBean으로 대체해 결정론을 얻는다 — 실제 실행기를 쓰면 첫 실행이 두 번째 요청보다 먼저 끝나 둘 다 202가 될 수 있다"
  - "@ApiResponses 에러 응답에 content = [Content()]를 명시한다 — 생략하면 springdoc이 BatchExecutionResponse를 401·403·409 본문 스키마로 붙여 FE에 거짓 계약을 준다"
  - "픽스처 대역은 플랜이 지정한 9_760_000_000L이 아니라 9_770_000_000L — 9_760은 05-14의 InactivityBatchDeductionLimitOverrideTest가 이미 쓴다"

patterns-established:
  - "비동기 결과를 검증하는 통합테스트는 awaitFinished(상태가 RUNNING을 벗어날 때까지 폴링) 헬퍼로 기다린다 — 정책상 시각 경과를 기다리는 conventions §10.3의 금지 대상과 다르다"
  - "@AfterEach는 지우기 전에 먼저 비동기 작업 완료를 기다린다 — 돌고 있는 스레드 밑에서 픽스처를 지우면 확정에 실패해 RUNNING 행이 남는다"
  - "단언 실패로 id를 기록하지 못한 경우까지 대비해, 정리 쿼리는 '이 클래스가 만든 관리자를 참조하는 batch_execution'까지 범위로 지운다 — 남으면 FK로 다른 테스트가 연쇄로 깨진다"

requirements-completed: []

duration: 75min
completed: 2026-08-16
---

# Phase 05 Plan 15: 수동 실행을 202 비동기 접수로 바꾸고 실행 상태 조회를 연다 (WR-05) Summary

**관리자 수동 실행 API가 배치가 끝날 때까지 요청 스레드를 붙잡던 동기 호출이었다 — 프록시·LB 타임아웃으로 관리자가 응답을 못 받고 재시도하면 CR-01의 이중 차감이 그대로 재현되는 경로였다. 이제 `RUNNING` 행 삽입(직렬화 진입)만 동기로 하고 본문은 전용 단일 스레드 실행기에 넘겨 **202 Accepted + `Location`**을 즉시 돌려주며, 진행 상태는 새로 연 조회 API 2종으로 폴링한다. 동시 POST에서 **정확히 하나만 202, 나머지는 409**임을 HTTP 계층에서 실증했다.**

## Performance

- **Duration:** 약 75분
- **Tasks:** 3 (Task 1·2는 TDD RED/GREEN 분리 커밋)
- **Files:** 12 (신규 5, 수정 6, 삭제 1)
- **Commits:** 6

## Accomplishments

### Task 1 — 전용 실행기 + 접수·조회 서비스

- **`BatchExecutorConfig`** — `inactivityBatchExecutor`(코어·최대 스레드 1, 큐 1,
  `inactivity-batch-` 스레드명, 종료 시 최대 60초 대기). 스레드 1개로 충분한 이유(어차피
  `uq_batch_execution_running`이 동시 실행을 막는다)와 스프링 기본 실행기에 얹지 않는 이유(요청
  처리용 풀 잠식 + 로그 추적 불가)를 KDoc에 남겼다.
- **`AdminBatchService`** — `launchInactivityRun`(시작 동기 → 본문 실행기 위임 → `RUNNING` 엔티티
  즉시 반환) / `getExecution` / `listRecentExecutions`. 실행기는 **`TaskExecutor` 인터페이스 +
  `@Qualifier`**로 받는다(테스트 대체 가능성이 곧 동시성 테스트의 결정론이다).
  `Propagation.NOT_SUPPORTED`인 이유, 실행기 람다를 `runCatching`으로 감싸는 이유,
  `RejectedExecutionException` 방어가 실질 도달 불가인데도 필요한 이유를 각각 KDoc에 적었다.
- **`BatchExceptions.kt` 통합** — 05-12가 ktlint `standard:filename` 때문에
  `BatchAlreadyRunningException.kt`로 두었던 것을, 예외가 2개가 되어 규칙이 풀린 지금
  `PassExceptions.kt` 선례대로 합쳤다. `BatchExecutionNotFoundException`은 `PassNotFoundException`
  선례를 따라 **id를 메시지에 보간하지 않는다**.
- `ErrorCode.BATCH_EXECUTION_NOT_FOUND(404)` + `docs/error-codes.md` 배치 절 1행.

### Task 2 — 엔드포인트 3종 + API 설명 재작성

| 엔드포인트 | 상태코드 | 비고 |
|---|---|---|
| `POST /api/admin/batch/inactivity-runs` | **202** | `Location: .../{batchExecutionId}`, 본문 `status = RUNNING`·`finishedAt = null` |
| `GET /api/admin/batch/inactivity-runs/{batchExecutionId}` | 200 / 404 | 폴링 대상 |
| `GET /api/admin/batch/inactivity-runs?limit=` | 200 | 기본 20, 1..100 보정, `startedAt` 내림차순 |

`@Operation` description을 전면 재작성했다. 기존 문구("**동시 실행은 아직 안전하지 않다**",
"응답이 오지 않아도 재호출하지 않는다")는 이번 변경으로 **거짓**이 됐고, FE가 이 파일로 동작을
정하기 때문에 반드시 고쳐야 했다. 새 설명은 ① 접수만 하고 202를 준다는 것과 폴링 경로,
② 실행 중 재호출은 409라 **재시도가 안전하다**는 것, ③ 순차 재실행도 안전하다는 것(D-106),
④ 밀린 주기는 1회 실행당 회원 1명 최대 1회씩 이어받는다는 것(D-119)을 담는다.
`@ApiResponses`로 202·200·401·403·404·409를 명시해 IN-03(POST가 200을 반환하고 실패 응답이
명세에 없던 문제)도 함께 닫았다.

`docs/api/openapi.yaml`을 같은 커밋에 재생성했다(CLAUDE.md 규칙 4).

### Task 3 — 동시성 실증 ② + D-114 갱신

`AdminBatchRunConcurrencyTest` 5종. 핵심 장치는 **실행기 빈을 무동작
`@MockitoBean(name = "inactivityBatchExecutor")`로 대체**하는 것이다 — 실제 실행기를 쓰면 테스트
픽스처가 작아 첫 실행이 밀리초에 끝나 버려 두 요청이 **둘 다 202**를 받을 수 있다. 모의 실행기는
본문을 돌리지 않으므로 `RUNNING`이 테스트 내내 유지되고, 뒤에 온 요청은 반드시 409를 받는다.
운영에서 배치는 수 초~수 분 돌기 때문에 이 상태가 오히려 **정상 상황을 모사**한다.

| 테스트 | 고정하는 것 |
|---|---|
| `두 스레드가 동시에 …정확히 하나만 202를 받고 나머지는 409다` | 202 1건 / 409 1건 / 새 이력 1건(거부는 이력 무기록) / `RUNNING` 1건 |
| `네 스레드가 동시에 …실행 중 행은 정확히 1건이다` | 202 1건 / 409 3건 |
| `실행 중 상태에서 순차로 다시 호출해도 409다` | 409 + `application/problem+json` + `BATCH_ALREADY_RUNNING` |
| `409 응답 본문에 제약조건명이나 SQL 같은 내부 정보가 담기지 않는다` | T-05D-15-02 |
| `202를 받은 시점에는 아직 잔여가 줄지 않았다` | **동기 실행 회귀 가드** — 컨트롤러가 본문을 동기로 돌리면 여기서 잔여가 줄어 실패한다 |

**반복 3회 모두 통과**(플레이키 아님).

`docs/decisions.md` **D-114 갱신** — 엔드포인트 3종·상태코드, 동기 호출이 CR-01의 현실적
트리거였다는 근거(WR-05), 전용 단일 스레드 실행기와 `defaultCandidate = false`, openapi 안전성
서술 재작성, IN-03 정리를 기록했다.

## Task Commits

| Task | 내용 | 커밋 | 종류 |
|---|---|---|---|
| 1 (RED) | 접수 서비스의 동기 시작·비동기 본문 계약 테스트 9종 | `af66bd6` | test |
| 1 (GREEN) | 전용 실행기 + `AdminBatchService` + 예외 통합 + 404 에러코드 | `2ed4d23` | feat |
| 2 (RED) | 202·Location·조회 2종의 HTTP 계약 테스트 12종 | `e2e6d63` | test |
| 2 (GREEN) | 엔드포인트 3종 + description 재작성 + openapi 재생성 | `6a9bd2e` | feat |
| 3 | 동시 POST 202/409 실증 5종 + D-114 갱신 | `4cffaee` | test |
| — | 수용 기준 정합(서비스 KDoc의 구현 클래스명 제거) | `aebfc34` | docs |

TDD 게이트: Task 1은 `test(...)`(컴파일 실패로 RED — `Unresolved reference 'AdminBatchService'`)
→ `feat(...)`, Task 2는 `test(...)`(실제 단언 실패 9/12 — `Status expected:<202> but was:<200>`)
→ `feat(...)` 순서를 지켰다. Task 3은 **테스트 자체가 산출물**이라 RED/GREEN 분리가 성립하지
않는다(검증 대상 구현이 Task 1·2에서 완성됐다) — 05-13 Task 3과 같은 판단이다. REFACTOR 단계는
정리할 중복이 없어 커밋하지 않았다.

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/config/BatchExecutorConfig.kt` — 신규. 전용 실행기 + 스레드 1개
  근거 · 기본 실행기에 얹지 않는 이유 · `defaultCandidate = false`의 이유를 KDoc에
- `src/main/kotlin/com/goldwrestling/batch/AdminBatchService.kt` — 신규. 접수 1 + 조회 2,
  `NOT_SUPPORTED`·`runCatching`·`RejectedExecutionException` 방어의 근거를 KDoc에
- `src/main/kotlin/com/goldwrestling/batch/BatchExceptions.kt` — 신규(예외 2종 통합).
  `BatchAlreadyRunningException.kt`는 삭제
- `src/main/kotlin/com/goldwrestling/batch/AdminBatchController.kt` — 엔드포인트 3종,
  `@ResponseStatus(ACCEPTED)`(springdoc 문서화 힌트) + `ResponseEntity.accepted().location(...)`,
  `@ApiResponses` 6종, KDoc에 "왜 201이 아니라 202인가"
- `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt` — `BATCH_EXECUTION_NOT_FOUND(404)`
- `src/test/kotlin/com/goldwrestling/batch/AdminBatchServiceTest.kt` — 신규 단위테스트 9종(스프링 없음)
- `src/test/kotlin/com/goldwrestling/batch/AdminBatchControllerTest.kt` — 12종으로 재작성.
  `awaitFinished` 헬퍼, 관측 지점 이동(POST 응답 → 단건 조회), 조회 2종의 401/403,
  기본 실행기 생존 단언, `@AfterEach`의 완료 대기 + FK 안전 정리
- `src/test/kotlin/com/goldwrestling/batch/AdminBatchRunConcurrencyTest.kt` — 신규 357줄, 5종
- `docs/api/openapi.yaml` — 재생성(202·409·404·GET 2종, `Location` 헤더, 잘못된 서술 제거)
- `docs/error-codes.md` — 배치 절 1행
- `docs/decisions.md` — D-114 갱신

## Decisions Made

- **`@Bean(defaultCandidate = false)`** — 이 플랜에서 가장 조용한 함정이었다. 부트의
  `applicationTaskExecutor`는 `@ConditionalOnMissingBean(Executor::class)` 조건이고
  (`spring-boot-autoconfigure-4.1.0.jar`의 `TaskExecutorConfigurations$OnExecutorCondition$ExecutorBeanCondition`
  바이트코드로 직접 확인), `TaskExecutor`는 `java.util.concurrent.Executor`를 상속한다. 표시 없이
  등록했다면 **우리 빈 하나 때문에 부트 기본 실행기가 통째로 사라지고** 서블릿 비동기 처리와
  `spring.task.execution.*`가 조용히 무력화된다. `defaultCandidate = false`가 `@ConditionalOnMissingBean`
  판정에서 제외되는지도 실제 컨텍스트를 띄워 확인했고(빈 목록에 셋 다 존재), 그 사실을 테스트로 고정했다.
- **`@ApiResponses`의 에러 응답에 `content = [Content()]`** — 처음엔 description만 줬는데,
  재생성된 openapi.yaml에서 springdoc이 **401·403·409 응답 본문 스키마로 `BatchExecutionResponse`를
  붙였다.** 실제 오류 본문은 RFC 9457 `ProblemDetail`이므로 FE에게 거짓 계약이 된다. 빈 `Content()`로
  스키마를 제거했다(이 저장소는 에러 스키마를 명세하지 않고 `docs/error-codes.md`가 계약이다).
- **관측 지점 이동** — `AdminBatchControllerTest`의 "차감이 실제로 일어난다"·"두 번째는 0"은
  계약을 유지하되 POST 응답이 아니라 단건 조회에서 확인한다. POST는 이제 결과를 모른다.
- **`@AfterEach`가 지우기 전에 기다린다** — 본문이 다른 스레드에서 도는 중에 회원·이용권을 지우면
  그 스레드가 사라진 행을 건드리고, 최악의 경우 확정에 실패해 `RUNNING`이 남는다. 남으면 같은
  컨테이너의 다른 테스트 클래스 배치가 전부 409로 막힌다(T-05D-11-01).
- **`java.util.concurrent.RejectedExecutionException`을 잡는다** — 스프링의
  `TaskRejectedException`이 이 예외의 하위 클래스라(바이트코드로 확인) 부모를 잡는 쪽이 넓고 안전하다.

## 테스트 판단 (CLAUDE.md 규칙 10 / conventions §10.0)

세 태스크 모두 테스트를 함께 작성했다. **면제 판단을 적용한 프로덕션 파일은
`BatchExecutorConfig.kt` 하나**다 — `config/`의 설정 클래스는 conventions §10.0 면제 목록이다.
다만 이 빈은 "등록 자체가 다른 빈을 밀어낼 수 있다"는 위험이 있어 **부작용만은 단언했다**
(`배치 전용 실행기를 등록해도 스프링 기본 실행기가 사라지지 않는다`).

- Task 1(서비스 메서드, 분기·예외 경로) → 스프링 없는 단위테스트 9종
- Task 2(새 엔드포인트·상태코드 변경) → 통합테스트 12종(성공 + 401·403·404 실패 경로)
- Task 3(동시성이 걸린 경로) → conventions §10.4 "동시성 테스트 필수" 이행, 5종

## Deviations from Plan

### 규칙 기반 보강

**1. [Rule 2 - CLAUDE.md 규칙 10] `AdminBatchServiceTest` 신규 (플랜 `<files>`에 없던 파일)**

- **Found during:** Task 1
- **Issue:** 플랜 Task 1의 `<files>`에는 테스트가 없지만 `AdminBatchService`는 분기·예외 경로가 있는
  **서비스 메서드**다(conventions §10.0 표: 단위테스트 + 필요하면 통합). 특히
  `RejectedExecutionException` 경로와 "본문이 호출 스레드에서 돌지 않는다"는 계약은 통합테스트로
  재현할 수 없다 — 전자는 큐를 일부러 채워야 하고, 후자는 결과만 봐서는 동기·비동기를 구분할 수 없다.
- **Fix:** Mockito 기반 단위테스트 9종을 RED→GREEN 순서로 추가.
- **Commit:** `af66bd6`(RED) / `2ed4d23`(GREEN)

**2. [Rule 2 - 조용한 부작용 방어] `@Bean(defaultCandidate = false)` + 기본 실행기 생존 테스트**

- **Found during:** Task 1
- **Issue:** 플랜은 실행기 등록 방법만 지시했고 부트 기본 실행기와의 충돌은 다루지 않았다. 실제로
  `TaskExecutionAutoConfiguration`이 `@ConditionalOnMissingBean(Executor)` 조건이라 전용 실행기를
  평범하게 등록하면 `applicationTaskExecutor`가 사라진다 — **아무 오류도 나지 않아 알아채기 어렵다.**
- **Fix:** `defaultCandidate = false`로 등록하고(우리 빈은 `@Qualifier` 명시 주입점에서만 쓰인다),
  실제 컨텍스트에서 셋 다 존재함을 확인한 뒤 `AdminBatchControllerTest`에 단언으로 고정했다.
- **Commit:** `2ed4d23`(설정) / `e2e6d63`(테스트)

**3. [Rule 1 - 잘못된 API 계약] `@ApiResponses` 에러 응답의 `content = [Content()]`**

- **Found during:** Task 2 (재생성한 openapi.yaml diff 확인)
- **Issue:** description만 준 401·403·409 응답에 springdoc이 성공 스키마
  (`BatchExecutionResponse`)를 붙였다. FE가 오류 본문을 그 타입으로 파싱하게 만드는 **거짓 계약**이다.
- **Fix:** 빈 `Content()`로 스키마를 제거하고 openapi를 다시 생성해 확인.
- **Commit:** `6a9bd2e`

**4. [Rule 1 - 픽스처 대역 충돌] 동시성 테스트 대역을 `9_760_000_000L` → `9_770_000_000L`**

- **Found during:** Task 3
- **Issue:** 플랜이 지정한 `9_760_000_000L`은 **05-14가 만든
  `InactivityBatchDeductionLimitOverrideTest`가 이미 쓰고 있다**(05-14-SUMMARY 확인). 겹치면 한
  클래스의 `@AfterEach` 범위 삭제가 다른 클래스의 픽스처를 지운다 — WR-07이 범위 정리를 도입한 이유가
  정확히 이것이다.
- **Fix:** 다음 대역으로 올리고 그 이유를 테스트 companion object KDoc에 남겼다.
- **Commit:** `4cffaee`

**5. [Rule 3 - 차단 이슈] KDoc 안의 `/api/admin/**` 표기 제거**

- **Kotlin 블록 주석은 중첩된다.** KDoc 본문에 `/api/admin/**`를 쓰면 `/*`가 **중첩 주석을 열어**
  파일 끝까지 주석이 되고 `Unclosed comment`로 컴파일이 실패한다(기존 컨트롤러 KDoc이 `/api/admin`
  까지만 쓴 이유). 표기를 `/api/admin 하위`로 바꿨다.
- **Commit:** `e2e6d63`

### 플랜 수용 기준과 실제 grep 결과가 다른 항목 (의도된 차이)

| 기준 | 기대 | 실제 | 사유 |
|---|---|---|---|
| `Propagation.NOT_SUPPORTED` in `AdminBatchService.kt` | 1 | **2** | import 1줄 + 애노테이션 1줄. FQN으로 쓰지 않는 한 2가 정상이다 |
| `RejectedExecutionException` in `AdminBatchService.kt` | 1 | **3** | import + `catch` + KDoc의 `[...]` 링크 |
| `9_760_000_000L` in `AdminBatchRunConcurrencyTest.kt` | 1 | **0** | 위 Deviation 4(대역 충돌) — `9_770_000_000L`을 쓴다 |

나머지 수용 기준은 모두 충족했다(아래 Verification Results).

### 플랜 지시와 다르게 배치한 것

**`openapi.yaml` 재생성을 Task 3이 아니라 Task 2 커밋에 넣었다.** 플랜은 Task 3 (b)에 배정했지만
CLAUDE.md 규칙 4·conventions §12가 **"API 변경 커밋에 재생성한 openapi.yaml이 포함되어야 한다"**고
못박고 있고(문서 우선순위상 `.planning/`보다 위), API를 바꾼 커밋은 Task 2다. Task 3은 API 표면을
건드리지 않으므로 재생성이 두 번 필요하지도 않다.

## Issues Encountered

- **Task 1 RED에서 Mockito `UnfinishedStubbingException` 3건.**
  `given(runner.start(...)).willReturn(runningExecution(...))`처럼 **인자 자리에서 다른 모의 객체를
  stubbing**하면, 진행 중인 stubbing 안에서 새 stubbing이 시작돼 Mockito가 거부한다. 모의 객체를
  지역 변수로 먼저 만들도록 고치고 그 이유를 주석에 남겼다.
- **Task 2 RED의 연쇄 실패.** 첫 단언(202 vs 200)이 실패하면서 실행 이력 id가 기록되지 않아
  `@AfterEach`의 관리자 삭제가 `fk_batch_execution_admin`에 걸렸고, 원인과 무관한 테스트 8개가 함께
  깨졌다. 정리 쿼리에 "이 클래스 관리자를 참조하는 `batch_execution`"을 추가해 자기 치유되게 했다.
- **로컬 DB 무손실:** `./gradlew generateApiDocs`가 로컬 Postgres에 앱을 띄우지만 이 플랜에는 새
  마이그레이션이 없고 DML도 실행하지 않았다. Phase 5 검증 데이터는 그대로다.

## Verification Results

| 항목 | 결과 |
|---|---|
| `./gradlew cleanTest test` (전체 스위트) | **BUILD SUCCESSFUL** |
| `./gradlew ktlintFormat` → `./gradlew cleanTest build` | **BUILD SUCCESSFUL** |
| `AdminBatchServiceTest` | 9/9 PASSED |
| `AdminBatchControllerTest` | 12/12 PASSED |
| `AdminBatchRunConcurrencyTest` | 5/5 PASSED, **반복 3회 모두 통과**(플레이키 아님) |
| `grep -c 'inactivityBatchExecutor' BatchExecutorConfig.kt` | 3 (≥1 충족) |
| `grep -c 'queueCapacity' BatchExecutorConfig.kt` | 1 |
| `grep -c 'TaskExecutor' AdminBatchService.kt` / `'ThreadPoolTaskExecutor'` | 3 / **0** |
| `grep -c 'ResponseEntity.accepted()' AdminBatchController.kt` | 1 |
| `grep -c '@GetMapping' AdminBatchController.kt` | 2 |
| `grep -c 'InactivityBatchRunner' AdminBatchController.kt` | **0** (서비스 경유) |
| `grep -c '동시 실행은 아직 안전하지 않다' AdminBatchController.kt` | 0 |
| `grep -c '응답이 오지 않아도 재호출하지 않는다' AdminBatchController.kt` | 0 |
| `grep -c 'BATCH_EXECUTION_NOT_FOUND' ErrorCode.kt` / `docs/error-codes.md` | 1 / 1 |
| `grep -c 'isAccepted\|202' AdminBatchControllerTest.kt` | 7 (≥1 충족) |
| `grep -c 'awaitFinished' AdminBatchControllerTest.kt` | 7 (≥2 충족) |
| `grep -c 'MockitoBean(name = "inactivityBatchExecutor")' AdminBatchRunConcurrencyTest.kt` | 1 |
| `grep -c 'CountDownLatch' AdminBatchRunConcurrencyTest.kt` | 3 (≥1 충족) |
| `AdminBatchRunConcurrencyTest` 줄 수 | 357 (min_lines 130 충족) |
| `grep -c '"202"' / '"409"' / 'inactivity-runs/{batchExecutionId}' openapi.yaml` | 1 / 1 / 2 |
| `openapi.yaml` `servers:` | `- url: /` 유지 |
| `git diff docs/api/openapi.yaml` 범위 | 배치 경로 3곳 + tag 설명 1줄 (springdoc의 tag 순서 재배열 포함). 다른 엔드포인트 변경 0건 |
| `grep -rn "동시 실행은 아직 안전하지 않다" src/ docs/` | **0건** |
| `awk '/^## D-114/,/^## D-115/' docs/decisions.md \| grep -c '202'` | 10 (≥1 충족) |
| 커밋된 V1~V10 마이그레이션 | 이번 플랜에서 수정 0건 (스키마 변경 없음) |

## User Setup Required

None — 새 의존성·환경변수·마이그레이션 없음.

**FE에 알릴 것:** `POST /api/admin/batch/inactivity-runs`의 성공 상태코드가 **200 → 202**로 바뀌었고,
응답 본문이 더 이상 최종 결과가 아니다(`status = RUNNING`, `finishedAt = null`). 결과는 `Location`이
가리키는 `GET .../{batchExecutionId}`를 폴링해서 얻는다. 실행 중 재호출은 409
`BATCH_ALREADY_RUNNING`이다. 계약은 재생성된 `docs/api/openapi.yaml`에 그대로 있다.

## Next Phase Readiness

- **05-16(마감 검증):** 이 플랜으로 WR-05·IN-03이 닫혔고 BATCH-04의 HTTP 계층 실증
  (`AdminBatchRunConcurrencyTest`)이 확보됐다. 05-13의 Deferred Issue였던 "`AdminBatchController`의
  `@Operation` 설명이 사실과 달라졌다"도 함께 해소됐다 —
  `grep -rn "동시 실행은 아직 안전하지 않다" src/ docs/`가 0건이다.
- **BATCH-04:** 요구사항 완료 표시는 하지 않았다(`requirements-completed: []`) — 05-11~15가 각각
  일부를 담당했고 **최종 판정은 05-16 몫**이다(05-12·05-13의 판단을 승계).
- **CR-03은 여전히 열려 있다.** 운영 배포는 `BATCH_INACTIVITY_SCHEDULER_ENABLED=false`로 cron을 꺼 둔
  채 올린다(D-116·D-119). **수동 실행 API는 이 값과 무관하게 동작하므로 그때까지 호출하지 않는다** —
  이번 변경으로 실행이 더 쉬워졌기 때문에 이 주의가 오히려 중요해졌다.
- 블로커 없음.

## Threat Flags

새 네트워크 엔드포인트 2종(GET)이 생겼지만 **플랜의 위협 등록부에 이미 등록돼 있고**
(T-05D-15-01) `mitigate`대로 처리했다 — 경로가 `/api/admin` 하위라 기존 `hasRole("ADMIN")` 규칙을
상속하고, 그 사실을 테스트가 두 엔드포인트 각각에 대해 401/403으로 단언한다. 나머지 4건도
등록된 disposition대로다: T-05D-15-02(응답에 집계 수치만 + 409 본문 내부정보 미노출, 테스트로 고정),
T-05D-15-03(실행 중 요청은 409로 즉시 종료, 실행기 스레드 1·큐 1), T-05D-15-04
(`RejectedExecutionException` 시 `FAILED("REJECTED")` 확정), T-05D-15-05(`triggered_by_admin_id`를
요청 스레드에서 확정해 저장 — 실행 스레드에는 인증 컨텍스트가 없다).

**등록부 밖에서 새로 발견한 표면은 없다.** 다만 위 "User Setup Required"의 운영 주의 하나가
성격상 보안이 아니라 운영 안전에 해당해 함께 적었다.

## 이번에 쓴 기술

**1. `202 Accepted`는 `200 OK`와 무엇이 다른가 — "작업 접수" vs "결과 반환" ★**

`200 OK`는 **"요청을 처리했고, 여기 결과가 있다"**는 뜻이다. `202 Accepted`는
**"요청은 접수했다. 아직 안 끝났고, 결과는 나중에 생긴다"**는 뜻이다.

- *이 코드에서 왜 필요했는가:* 미사용 차감 배치는 회원이 늘수록 오래 걸린다. 예전 API는 배치가 다
  끝난 뒤 최종 집계(`deductedCount` 등)를 200으로 돌려줬는데, 그러려면 **관리자를 그동안 기다리게
  해야 한다.** 202로 바꾸면 서버는 "접수했다 + 이 실행의 id는 42다"만 즉시 답하고, 관리자 화면은
  `Location`이 가리키는 주소(`GET .../42`)를 몇 초 간격으로 확인해 진행 상황을 보여줄 수 있다.
- *`201 Created`가 아닌 이유:* 201은 **리소스를 만들었을 때** 쓴다(이 저장소에서는 이용권 등록·예약
  생성 2곳). 우리가 만든 것은 조회·수정할 도메인 리소스가 아니라 **아직 끝나지 않은 작업**이다.
  `batch_execution` 행이 생기긴 하지만 그건 "작업의 진행 상태를 보는 창"이지 관리자가 다룰 자원이 아니다.
- *`Location` 헤더의 역할:* 202 응답에 "진행 상태를 어디서 보라"를 담는 표준 자리다. 이게 없으면
  FE가 응답 본문에서 id를 꺼내 경로를 **직접 조립**해야 하고, 나중에 경로가 바뀌면 FE도 함께 고쳐야 한다.
- *안 썼으면 뭐가 깨지는가:* 아래 2번 그대로다.

**2. 요청 스레드를 오래 붙잡으면 왜 위험한가 — 타임아웃이 데이터 손상으로 번지는 경로 ★**

Tomcat은 요청 하나당 스레드 하나를 배정하고, 응답을 다 보낼 때까지 그 스레드는 다른 요청을 받지
못한다. 여기에 **프록시·로드밸런서의 응답 타임아웃**(흔히 60초)이 겹치면 문제가 생긴다.

- *이 코드에서 왜 필요했는가:* 배치가 60초를 넘기면 프록시가 먼저 연결을 끊고 관리자는 **오류
  화면**을 본다. 그런데 **서버에서는 배치가 계속 돌고 있다** — 끊긴 것은 응답 경로일 뿐이다.
  아무것도 안 된 줄 안 관리자가 버튼을 다시 누르면 두 실행이 겹치고, 그게 CR-01(같은 주기 이중
  차감)이다. 즉 "타임아웃"이라는 네트워크 문제가 **회원 잔여 횟수라는 데이터 문제로 번진다.**
  05-11~13이 만든 가드(`RUNNING` 부분 유니크 인덱스)가 이 재시도를 409로 막아 주지만, 그건
  **사고를 막는 것이지 사고가 나는 경로를 없애는 것은 아니다.** 이 플랜이 경로 자체를 없앴다.
- *"시작만 동기"가 핵심이다:* 전부 비동기로 던지면 중복 요청도 전부 202를 받고, 거부는 나중에
  로그로만 남아 관리자는 두 번 접수된 줄 안다. 그래서 **`RUNNING` 행 INSERT(= 직렬화 진입)까지는
  동기**로 하고 그 결과로 202/409를 가른 뒤, 오래 걸리는 본문만 넘긴다.
- *전용 실행기를 쓴 이유:* 스프링이 기본으로 주는 실행기(`applicationTaskExecutor`)에 얹으면 요청
  처리가 함께 쓰는 풀을 수 분짜리 배치가 차지해 **무관한 API 응답까지 느려진다.** 게다가 로그만
  봐서는 어느 풀에서 도는지 알 수 없다. 전용 빈이면 스레드명이 `inactivity-batch-1`로 찍힌다.
  스레드를 1개만 둔 이유는 어차피 DB 제약이 동시 실행을 막아 2번째 스레드가 할 일이 없어서다.
- *안 썼으면 뭐가 깨지는가:* 관리자가 "안 됐네" → 재시도 → 가드가 409 → "왜 안 되지" → 반복.
  운영이 불편해지고, 가드가 하나라도 빠지는 날 잔여가 근거 없이 두 번 깎인다.

**3. 테스트에서 빈을 모의로 바꿔 경쟁 상황을 결정론으로 만든다 ★**

동시성 테스트의 가장 흔한 실패는 **"통과했는데 아무것도 증명하지 못하는 것"**이다. 여기서도
그랬다: 두 스레드가 동시에 POST를 보내도, 테스트 데이터가 작아 첫 실행이 **밀리초 만에** 끝나면
두 번째 요청이 도착할 때는 이미 `RUNNING` 행이 종료 상태다 → **둘 다 202**를 받고, 우리가 증명하려던
"하나는 409" 계약은 검증되지 않는다. 더 나쁜 것은 이게 **CI 부하에 따라 어떤 날은 통과하고 어떤
날은 실패하는** 깜빡이는 테스트가 된다는 점이다.

- *이 코드에서 왜 필요했는가:* 그래서 실행기 빈 하나만
  `@MockitoBean(name = "inactivityBatchExecutor")`으로 **무동작 대역**으로 바꿨다. 모의 실행기는
  `execute`를 받아도 아무 일도 하지 않으므로 첫 실행이 `RUNNING`에 **멈춰 있고**, 뒤에 온 요청은
  **반드시** 유니크 인덱스에 막혀 409를 받는다. 타이밍에 의존하던 것이 구조에 의존하게 바뀐다.
- *이게 현실을 왜곡하지 않는 이유:* 운영에서 배치는 수 초~수 분 돈다. 즉 **"앞 실행이 아직 안
  끝난 상태"가 오히려 정상 상황**이고, 모의 실행기는 그 상황을 고정할 뿐이다. 반대로 "밀리초 만에
  끝나는 배치"가 오히려 테스트에서만 나타나는 비현실적 조건이었다.
- *이게 가능하려면 설계가 받쳐 줘야 한다:* 서비스가 실행기를 **구현 클래스가 아니라 `TaskExecutor`
  인터페이스로** 받기 때문에 대체가 성립한다. 구현 타입으로 받았다면 모의로 바꿀 수 없었다.
  "테스트하기 쉬운 설계"가 추상적인 미덕이 아니라 **이 테스트를 쓸 수 있느냐 없느냐**를 갈랐다.
- *안 썼으면 뭐가 깨지는가:* 깜빡이는 테스트가 하나 생기고, 사람들이 "가끔 실패하니 다시 돌려"라고
  대응하기 시작하는 순간 그 테스트는 방어 기능을 잃는다.

**4. `@ConditionalOnMissingBean`과 `defaultCandidate = false` — 빈 하나가 다른 빈을 지우는 일 ★**

스프링 부트의 자동 설정은 대부분 **"사용자가 직접 만들지 않았으면 내가 만들어 준다"**는 조건
(`@ConditionalOnMissingBean`)으로 동작한다. 편리하지만 반대 방향의 함정이 있다.

- *이 코드에서 왜 필요했는가:* 부트의 `applicationTaskExecutor`는
  `@ConditionalOnMissingBean(Executor::class)` 조건이다. 그런데 우리가 만든 `TaskExecutor`도
  `Executor`의 하위 타입이라, 그냥 등록하면 부트는 "아, 사용자가 실행기를 직접 만들었구나" 하고
  **기본 실행기를 아예 만들지 않는다.** 오류도 경고도 없다 — 서블릿 비동기 처리와
  `spring.task.execution.*` 설정이 조용히 죽는다. 그래서 `@Bean(defaultCandidate = false)`로
  "이 빈은 **이름을 지정한 주입점**(`@Qualifier`)에서만 쓰이고, 타입만 보고 고르는 후보에서는
  빼 달라"고 표시했다. 부트는 이 표시가 붙은 빈을 조건 판정에서 무시한다.
- *추측하지 않고 확인한 방법:* 자동 설정 jar를 풀어 `javap -v`로 조건 애노테이션을 직접 읽었고
  (`@ConditionalOnMissingBean(value=[Executor])`), 실제 컨텍스트를 띄워 빈 목록에
  `inactivityBatchExecutor`·`applicationTaskExecutor`가 **둘 다** 있는지 확인한 뒤 그 확인을
  테스트로 고정했다(CLAUDE.md 규칙 9 — 모르는 API는 추측하지 않는다).
- *안 썼으면 뭐가 깨지는가:* 지금 당장은 아무 일도 안 일어난다. 나중에 누가 서블릿 비동기나
  `@Async`를 쓰기 시작했을 때, **"왜 우리 앱만 기본 실행기가 없지"**를 아무도 이 커밋과 연결하지 못한다.

**5. 일부러 하지 않은 것 — 스레드 풀을 키우거나 큐를 깊게 두기**

"배치를 비동기로 돌린다"고 하면 자연스럽게 풀 크기를 고민하게 되는데, 여기서는 **스레드 1개·큐 1**이
맞다.

- 동시에 돌 수 있는 배치는 어차피 **최대 1건**이다 — `uq_batch_execution_running`이 물리적으로
  보장한다(D-117). 스레드를 4개 둬도 2번째 이후 작업은 시작 단계에서 409로 끝난다.
- 큐를 깊게 두면 **"언젠가 돌 예정인 작업"이 쌓이는데**, 그 작업들은 각자 유니크 인덱스에 막혀
  전부 실패한다 — 실패를 예약해 두는 셈이다.
- 대신 큐가 넘칠 때(`RejectedExecutionException`)를 방어했다. 이 경로는 실질적으로 도달 불가지만,
  **방금 만든 `RUNNING` 행을 정리하지 않으면 그 행 하나가 이후 모든 배치를 30분간 막는다.**
  도달 불가한 경로라도 실패 시 피해가 크면 방어한다 — 이게 이 배치 전체를 관통하는 원칙이다.

---
*Phase: 05-batch*
*Completed: 2026-08-16*

## Self-Check: PASSED

- FOUND: `src/main/kotlin/com/goldwrestling/config/BatchExecutorConfig.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/batch/AdminBatchService.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/batch/BatchExceptions.kt`
- FOUND: `src/test/kotlin/com/goldwrestling/batch/AdminBatchServiceTest.kt`
- FOUND: `src/test/kotlin/com/goldwrestling/batch/AdminBatchRunConcurrencyTest.kt`
- FOUND: `.planning/phases/05-batch/05-15-SUMMARY.md`
- DELETED as intended: `src/main/kotlin/com/goldwrestling/batch/BatchAlreadyRunningException.kt`
- FOUND: commits `af66bd6`, `2ed4d23`, `e2e6d63`, `6a9bd2e`, `4cffaee`, `aebfc34`
