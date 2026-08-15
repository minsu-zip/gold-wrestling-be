---
phase: 05-batch
plan: 12
subsystem: batch
tags: [kotlin, spring-boot, jpa, transaction-propagation, requires-new, configuration-properties, error-handling, testcontainers]

# Dependency graph
requires:
  - phase: 05-batch (05-11)
    provides: "V10 uq_batch_execution_running 부분 유니크 인덱스, BatchExecution.finish(), BatchExecutionRepository.markStaleRunningAsFailed/findFirstByStatus, RUNNING·FAILED 상태값"
  - phase: 01 (기반)
    provides: "DomainException·ErrorCode·GlobalExceptionHandler(RFC 9457), @ConfigurationPropertiesScan, Clock 빈"
provides:
  - "InactivityBatchProperties: policy-effective-date(2026-09-01) / max-deductions-per-run(1) / stale-run-timeout(30m) 3종을 goldwrestling.batch.inactivity 아래로 묶고 환경변수 오버라이드를 연다"
  - "ErrorCode.BATCH_ALREADY_RUNNING(409) + BatchAlreadyRunningException — 중복 실행 거부를 RFC 9457 ProblemDetail 규약 안에서 표현"
  - "BatchExecutionRecorder.start/finish — 실행 시작·종료를 러너 본문과 별개 트랜잭션(REQUIRES_NEW)으로 기록하고 유니크 위반을 409로 변환"
  - "stale RUNNING 정리(기본 30분)를 실행 시작 경로에 배선 — 앱 급종료가 배치를 영구 차단하지 않는다"
  - "REQUIRES_NEW 커밋 동작을 '호출부 트랜잭션 롤백 후에도 RUNNING 행이 살아남는다'로 실증한 통합테스트 8종"
  - "docs: D-118 신규 + D-114의 '새 에러코드는 추가하지 않는다' 철회 정정, error-codes.md 배치 절, glossary 용어 2종"
affects: [05-13, 05-14, 05-15, 05-16]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "트랜잭션 전파 REQUIRES_NEW를 '호출부와 무관하게 즉시 커밋돼야 하는 기록'에 쓰는 첫 사례 — 이 저장소의 실행 직렬화 관례가 된다"
    - "REQUIRES_NEW 커밋 동작은 '호출부 트랜잭션을 setRollbackOnly로 되돌려도 행이 남는다'로 검증한다 — 커넥션 트릭 없이 REQUIRED와 결정적으로 구분된다"
    - "DataIntegrityViolationException을 도메인 예외로 변환할 때는 반드시 예외를 던진다(정상 반환 금지) — flush 실패로 rollback-only가 표시돼 커밋 시 UnexpectedRollbackException(500)이 된다"
    - "정책 상수는 @ConfigurationProperties + 환경변수 오버라이드로 둔다 — 사람 개입 없이 잔여를 깎는 코드의 값은 재배포 없이 되돌릴 수 있어야 한다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/config/InactivityBatchProperties.kt
    - src/main/kotlin/com/goldwrestling/batch/BatchAlreadyRunningException.kt
    - src/main/kotlin/com/goldwrestling/batch/BatchExecutionRecorder.kt
    - src/test/kotlin/com/goldwrestling/batch/BatchExecutionRecorderTest.kt
  modified:
    - src/main/resources/application.yml
    - .env.example
    - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
    - docs/error-codes.md
    - docs/decisions.md
    - docs/glossary.md

key-decisions:
  - "D-118 신규: 배치 중복 실행은 409 BATCH_ALREADY_RUNNING으로 거부한다. D-114의 '새 에러코드는 추가하지 않는다'를 철회 — 그 근거였던 '거부할 요청이 없다'는 전제가 CR-01(동시 호출)로 깨졌다"
  - "BatchExecutionRecorder는 별도 스프링 빈이다 — 러너에 @Transactional을 붙이면 실패 격리(D-112)가 깨지고, 같은 클래스 내부 호출은 프록시를 우회해 트랜잭션 경계가 생기지 않는다(InactivityDeductionService와 같은 이유, 05-01 결정 C)"
  - "start·finish 모두 REQUIRES_NEW — 호출부 트랜잭션에 참여하면 RUNNING 행이 호출부 종료까지 커밋되지 않아 uq_batch_execution_running이 발동하지 못하고 직렬화가 성립하지 않는다"
  - "D-116 킬 스위치 키(goldwrestling.batch.inactivity-scheduler-enabled)는 새 프로퍼티 클래스로 옮기지 않는다 — @ConditionalOnProperty와 build.gradle.kts 테스트 태스크가 원시 이름으로 읽으므로 옮기면 조용히 깨진다"
  - "파일명은 BatchExceptions.kt가 아니라 BatchAlreadyRunningException.kt — ktlint standard:filename(단일 클래스 파일은 클래스명을 따른다)이 빌드를 막았다. 두 번째 배치 예외가 생기면 그때 합친다"

patterns-established:
  - "커밋 여부를 관찰해야 하는 통합테스트는 클래스 레벨 트랜잭션 대신 @BeforeEach의 max(id) baseline + @AfterEach의 'id > baseline' 삭제로 격리한다 — 다른 테스트 클래스의 이력을 건드리지 않으면서 RUNNING 행 누출도 막는다"

requirements-completed: []

duration: 15min
completed: 2026-08-16
---

# Phase 05 Plan 12: 실행 직렬화의 실행 지점 — 레코더·409 에러코드·배치 설정 Summary

**`RUNNING` 행 삽입을 러너 본문과 **별개 트랜잭션**(`REQUIRES_NEW`)으로 즉시 커밋하는 `BatchExecutionRecorder`를 만들어, 두 번째 실행이 그 행을 보고 409 `BATCH_ALREADY_RUNNING`으로 거부되게 하고, 앱이 죽어 남은 `RUNNING` 행은 30분 뒤 `FAILED(STALE)`로 정리되게 했다.**

## Performance

- **Duration:** 약 15분 (01:31~01:45 커밋 구간 + 사전 조사·검증)
- **Tasks:** 3 (Task 3은 TDD RED/GREEN 분리 커밋)
- **Files:** 10 (신규 4, 수정 6) / +522줄

## Accomplishments

- **배치 설정 3종 분리(Task 1)** — `InactivityBatchProperties`(`goldwrestling.batch.inactivity`)로
  `policyEffectiveDate`(기본 `2026-09-01`) · `maxDeductionsPerRun`(기본 `1`) ·
  `staleRunTimeout`(기본 `30m`)을 묶고 `application.yml`에 환경변수 오버라이드 3종, `.env.example`에
  키 3종을 열었다. 셋 다 기본값이 있어 로컬·테스트가 설정 없이 돈다. **D-116 킬 스위치 키는
  그대로 두었다** — `@ConditionalOnProperty`가 원시 이름으로 읽고 `build.gradle.kts` 테스트
  태스크가 같은 이름의 시스템 프로퍼티로 `false`를 고정하기 때문이며, 그 사실과 이유를 KDoc·yml
  주석에 남겼다.
- **거부 경로의 계약(Task 2)** — `ErrorCode.BATCH_ALREADY_RUNNING(409)` +
  `BatchAlreadyRunningException`. 사용자 대면 문구에 제약조건명·SQL을 담지 않는다(conventions §8).
  `docs/error-codes.md`에 "배치 코드 (Phase 5)" 절을 신설하고, **D-118을 기록하면서 D-114의
  "새 에러코드는 추가하지 않는다"를 철회 정정**했다 — 그 문장의 근거("상태 기반 설계가 중복 실행을
  흡수해 거부할 요청이 없다")는 *순차* 재호출에만 참이고, 동시 호출은 흡수되지 않는다.
- **실행 지점(Task 3)** — `BatchExecutionRecorder.start`는 ① stale `RUNNING` 정리(임계 초과분만,
  `errorSummary = "STALE"`, 정리 시 `logger.warn`) → ② `RUNNING` 행 `saveAndFlush` →
  ③ `DataIntegrityViolationException` → `BatchAlreadyRunningException` 변환 순으로 동작한다.
  `finish`는 `BatchExecution.finish(...)`로 확정하고 **확정된 엔티티를 반환**해 05-13·05-15가
  재조회하지 않게 한다. 두 메서드 모두 `Propagation.REQUIRES_NEW`다.
- **실증** — 실제 PostgreSQL(Testcontainers)에서 8종을 단언했다. 특히
  **"호출부 트랜잭션을 `setRollbackOnly`로 되돌려도 `RUNNING` 행이 살아남는다"**로 `REQUIRES_NEW`를
  결정적으로 증명했다(`REQUIRED`였다면 행이 함께 사라진다). stale 임계는 **양쪽 경계**를 모두
  단언했다 — 31분 경과분은 `FAILED(STALE)`로 정리되고 새 실행이 시작되며, 29분 경과분은 정리되지
  않아 `start`가 거부된다(정상 실행 중인 배치를 죽었다고 오판해 끊지 않는다).

## Task Commits

| Task | 내용 | 커밋 | 종류 |
|---|---|---|---|
| 1 | 배치 정책 값 3종을 `@ConfigurationProperties`로 분리 | `e7b3cb0` | feat |
| 2 | 중복 실행 거부 예외 + 409 에러코드 + D-118·D-114 정정 | `2ca48ef` | feat |
| 3 (RED) | 실행 시작·종료의 트랜잭션 경계 통합테스트 8종 | `19e8883` | test |
| 3 (GREEN) | `BatchExecutionRecorder` + glossary 용어 2종 | `d1a9ece` | feat |

TDD 게이트: Task 3은 `test(...)` 커밋(컴파일 실패로 RED 확인 — `Unresolved reference
'BatchExecutionRecorder'`) → `feat(...)` 커밋(8종 전부 통과) 순서를 지켰다. REFACTOR 단계는 정리할
중복이 없어 커밋하지 않았다.

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/config/InactivityBatchProperties.kt` — 신규. 정책 값 3종 +
  각 값이 없으면 무엇이 깨지는지(소급 차감·사고 피해 무제한·영구 차단)를 필드 KDoc에 명시,
  D-116 키를 옮기지 않는 이유를 클래스 KDoc에 명시
- `src/main/kotlin/com/goldwrestling/batch/BatchAlreadyRunningException.kt` — 신규. 발생 조건과
  **정상 경로**라는 사실(더블클릭·재시도·cron 겹침은 예상된 상황, 거부가 안전한 결과)을 KDoc에 명시
- `src/main/kotlin/com/goldwrestling/batch/BatchExecutionRecorder.kt` — 신규. 왜 별도 빈인가 /
  왜 `REQUIRES_NEW`인가 / FK·CHECK 위반과 유니크 위반이 같은 예외라는 사실과 그 전제 /
  "이 테이블은 부족분 계산의 근거가 아니다"(D-106) 네 가지를 클래스 KDoc에 명시
- `src/test/kotlin/com/goldwrestling/batch/BatchExecutionRecorderTest.kt` — 신규. 백틱 한국어
  테스트 8종, 트랜잭션 애노테이션 없음, `id > baseline` 삭제 훅
- `src/main/resources/application.yml` — `goldwrestling.batch.inactivity` 블록 3키(+D-116 키 유지 주석)
- `.env.example` — `BATCH_INACTIVITY_POLICY_EFFECTIVE_DATE` / `_MAX_DEDUCTIONS_PER_RUN` /
  `_STALE_RUN_TIMEOUT` (실값 없음, 기본값은 주석으로만)
- `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt` — `BATCH_ALREADY_RUNNING(CONFLICT)` 추가
- `docs/error-codes.md` — "배치 코드 (Phase 5)" 절 신규 1행
- `docs/decisions.md` — **D-118 신규**, **D-114 정정**(에러코드 금지 철회)
- `docs/glossary.md` — "정체된 실행 `stale run`", "배치 실행 기록자 `BatchExecutionRecorder`"

## Decisions Made

- **`BatchExceptions.kt` → `BatchAlreadyRunningException.kt`** — 아래 Deviations 1번 참조.
- **`start`가 `DataIntegrityViolationException`을 잡은 뒤 반드시 던진다** — 잡아서 기존 실행을
  조회해 반환하는 "친절한" 처리를 하지 않는다. flush 실패로 트랜잭션이 rollback-only로 표시돼
  있어 정상 반환하면 커밋 시점에 `UnexpectedRollbackException`(500)이 나고, 관리자는 409 대신
  원인 불명의 서버 오류를 본다.
- **stale 정리와 INSERT를 같은 트랜잭션에 둔다** — 05-GAP-CONTEXT §2.1·위협 T-05D-12-02의 확정안
  그대로. 경쟁에서 진 쪽은 정리까지 함께 롤백되고 409를 받는데, 그것이 안전한 결과다.
- **`finish`의 "행 없음"은 `IllegalStateException`이다** — 사용자 입력 오류가 아니라 프로그래밍
  오류(존재하지 않는 id로 확정 시도)이므로 도메인 예외·에러코드를 만들지 않는다.
- **테스트 격리를 `max(id)` baseline으로 한다** — 이 클래스는 커밋 여부를 관찰해야 해서 클래스
  레벨 트랜잭션 롤백(05-11 `BatchExecutionRepositoryTest`의 방식)을 쓸 수 없다. 대신
  `@BeforeEach`가 `max(id)`를 찍고 `@AfterEach`가 그 이후 행만 지운다 — 다른 테스트 클래스의
  이력을 건드리지 않으면서 `RUNNING` 행 누출(T-05D-11-01)도 막는다.

## 테스트 판단 (CLAUDE.md 규칙 10 / conventions §10.0)

- **Task 1은 테스트를 만들지 않았다** — `@ConfigurationProperties` 데이터 클래스 + `application.yml`
  + `.env.example`은 conventions §10.0의 **면제 목록**(“`config/`의 설정 클래스,
  `@ConfigurationProperties` 데이터 클래스 / `application.yml` / 문서”)에 정확히 해당한다.
  다만 **바인딩이 실제로 되는지는 검증했다** — Task 3의 `@SpringBootTest`가 이 프로퍼티를
  주입받는 `BatchExecutionRecorder`를 띄우고, `staleRunTimeout`이 30분으로 바인딩됐음을
  31분/29분 경계 테스트가 간접 실증한다(`LocalDate`·`Duration` 바인딩 모두 정상 동작 확인).
- **Task 2는 HTTP 계약 테스트를 만들지 않았다** — conventions §10.0은 "에러 응답 변경 → 통합
  테스트로 상태코드·본문 형태 확인"을 요구하지만, **이 시점에는 409를 반환할 HTTP 경로가 아직
  없다**(컨트롤러는 05-15에서 바뀐다). 예외·에러코드 선언까지만 하고, **HTTP 409 +
  `ProblemDetail` 본문 검증은 05-15의 `AdminBatchControllerTest`·`AdminBatchRunConcurrencyTest`가
  담당한다.** 이 시차를 D-118 본문에도 남겼다.
- Task 3은 리포지토리 쿼리 배선 + 트랜잭션 경계라 **Testcontainers 통합테스트 필수** 항목이며,
  8종을 작성해 통과시켰다.

## Deviations from Plan

### 규칙 기반 수정

**1. [Rule 3 - 차단 이슈] `BatchExceptions.kt` → `BatchAlreadyRunningException.kt` 파일명 변경**

- **Found during:** Task 2 (`./gradlew ktlintFormat` 실행)
- **Issue:** 플랜은 `PassExceptions.kt` 선례를 따라 `batch/BatchExceptions.kt`를 만들라고 지시했으나,
  ktlint `standard:filename` 규칙이 **단일 클래스만 있는 파일은 클래스명을 따라야 한다**며 빌드를
  실패시켰다(`File 'BatchExceptions.kt' contains a single class ... should be named
  'BatchAlreadyRunningException.kt'`). `PassExceptions.kt`·`MemberExceptions.kt` 등 선례는 모두 클래스가
  2개 이상이라 이 규칙에 걸리지 않는다.
- **Fix:** 파일명을 클래스명에 맞췄다. 억제 애노테이션(`@file:Suppress("ktlint:...")`)이나
  `.editorconfig` 규칙 완화는 쓰지 않았다 — 이 저장소에는 ktlint 억제 선례가 **한 건도 없고**,
  CLAUDE.md가 "코드 포맷은 ktlint가 기준"이라고 못박고 있으며 문서 우선순위상 `.planning/`의 파일
  경로 지시보다 우위다. 그 판단과 "두 번째 배치 예외가 생기면 그때 `BatchExceptions.kt`로 합치면
  된다(그 시점엔 규칙이 적용되지 않는다)"는 사실을 예외 클래스 KDoc에 남겼다.
- **05-13·05-15에 대한 영향:** 두 플랜이 `src/main/kotlin/com/goldwrestling/batch/BatchExceptions.kt`를
  참조한다. **05-15가 `BatchExecutionNotFoundException`을 추가하는 시점**에 두 클래스를
  `BatchExceptions.kt`로 합치면 ktlint 규칙에서 벗어나 플랜의 원래 형태로 돌아온다.
- **Commit:** `2ca48ef`

**2. [보강] `<behavior>` 6가지 외에 테스트 2종 추가**

- `start가 만든 실행 중 이력은 호출부 트랜잭션이 롤백돼도 살아남는다` — `REQUIRES_NEW`가 이
  플랜의 핵심인데, behavior 목록의 "즉시 커밋"만으로는 `REQUIRED`와 구분되지 않는다(호출부가
  트랜잭션을 열지 않으면 둘 다 통과한다). 전파 속성이 잘못 바뀌면 반드시 실패하는 테스트를 두었다.
- `MANUAL 트리거로 시작하면 실행을 지시한 관리자 id가 함께 기록된다` — 플랜 `<action>`이 요구한
  `ck_batch_execution_trigger` + 실제 `Admin` 행 경로 검증.

### 플랜 지시대로 두되 기록해 둘 것

**3. yml·`.env.example` 주석의 `D-119`는 아직 존재하지 않는 전방 참조다**

플랜 Task 1이 "각 키 위에 한 줄 주석으로 의미와 결정 번호(D-118·D-119)를 적는다"고 지시했고,
**D-119("미사용 차감에 정책 시행일 하한과 1회 실행 상한을 둔다")는 05-14가 기록하도록 배정돼
있다**(05-14-PLAN.md, 05-16이 D-117·D-118·D-119 3건 존재를 검증한다). 이 플랜이 앞당겨 쓰면
번호가 충돌하므로 쓰지 않았다 — 같은 청크(`feature/phase-05d-gap-closure`) 안에서 05-14가 닫는다.

## Issues Encountered

- ktlint 파일명 규칙(위 Deviation 1) 외에 없다. RED는 의도한 컴파일 실패로 확인됐고, GREEN 이후
  `BatchExecutionRecorderTest` 8종, `com.goldwrestling.batch.*` 전체, `cleanTest build`가 모두
  한 번에 통과했다.
- **OpenAPI 재생성 불필요 확인:** 이 플랜은 컨트롤러·DTO·`@Operation`을 건드리지 않았다.
  `docs/api/openapi.yaml`에 에러코드는 스키마 enum이 아니라 `@Operation` description 문장으로만
  등장하므로(예약 생성 설명의 `INSUFFICIENT_PASS_COUNT`), `ErrorCode` 추가만으로는 계약 파일이
  바뀌지 않는다. 배치 API 표면이 바뀌는 05-15에서 재생성한다.
- **로컬 DB 무손실:** 테스트는 Testcontainers만 사용했고 로컬 `gold-wrestling` DB에 DDL·DML을
  실행하지 않았다(앱 기동도 하지 않았다). Phase 5 검증 데이터는 그대로다.

## Verification Results

| 항목 | 결과 |
|---|---|
| `./gradlew cleanTest test --tests 'com.goldwrestling.batch.BatchExecutionRecorderTest'` | BUILD SUCCESSFUL (8/8 PASSED) |
| `./gradlew cleanTest test --tests 'com.goldwrestling.batch.*'` | BUILD SUCCESSFUL |
| `./gradlew ktlintFormat` → `./gradlew cleanTest build` | BUILD SUCCESSFUL |
| `grep -c 'Propagation.REQUIRES_NEW' BatchExecutionRecorder.kt` | 2 |
| `grep -c '@Transactional' BatchExecutionRecorderTest.kt` | 0 |
| `grep -c 'inactivity-scheduler-enabled' application.yml` | 1 (D-116 킬 스위치 생존) |
| `grep -c '^## D-118' docs/decisions.md` / D-114 정정 | 1 / 있음(2026-08-16) |
| `grep -rn "새 에러코드는 추가하지 않는다" docs/decisions.md` | 3건 — 원문(D-114) + 그 바로 아래 철회 정정 + D-118의 인용. 정정 없이 홀로 남은 서술 없음 |
| 커밋된 V1~V10 마이그레이션 | 이번 플랜에서 수정 0건 (스키마 변경 없음) |

## User Setup Required

새 환경변수 3종이 생겼지만 **전부 기본값이 있어 설정 없이도 동작한다.** 값을 바꾸려면 `.env`
(로컬) 또는 배포 환경변수에 아래를 넣는다 — `.env.example`에 키 이름이 이미 추가돼 있다.

| 키 | 기본값 | 언제 바꾸나 |
|---|---|---|
| `BATCH_INACTIVITY_POLICY_EFFECTIVE_DATE` | `2026-09-01` | 정책 시행일이 미뤄지면 |
| `BATCH_INACTIVITY_MAX_DEDUCTIONS_PER_RUN` | `1` | 밀린 주기를 빠르게 따라잡아야 할 때(권장하지 않음) |
| `BATCH_INACTIVITY_STALE_RUN_TIMEOUT` | `30m` | 배치 실행 시간이 30분을 넘길 만큼 회원이 늘면 |

## Next Phase Readiness

- **05-13(러너 재편):** `recorder.start(...)` → 본문 → `try/finally`로 `recorder.finish(...)`
  형태로 바꾸면 된다. 러너에는 여전히 트랜잭션 애노테이션을 붙이지 않는다(D-112).
  `finish`가 확정 엔티티를 반환하므로 재조회가 필요 없다. **주의:** 현재 러너는 아직 종료 시점에
  이력 1건을 `save`하므로 `RUNNING` 행을 만들지 않는다 — **CR-01은 05-13이 러너를 배선하고
  05-15가 API를 바꿔야 실제로 닫힌다.** 이 플랜은 거부 장치를 만들었을 뿐 아직 배선하지 않았다.
- **05-14(캐치업 상한·시행일):** `InactivityBatchProperties.policyEffectiveDate`·
  `maxDeductionsPerRun`이 준비됐다. D-119 기록도 05-14 몫이다(위 Deviation 3).
- **05-15(API):** `BatchAlreadyRunningException` → 409 `ProblemDetail` 변환은 `GlobalExceptionHandler`가
  이미 `DomainException` 공통 경로로 처리하므로 **핸들러를 새로 만들 필요가 없다.**
  HTTP 계약(409 본문·동시 호출 시 정확히 하나만 202) 검증은 05-15가 담당한다.
  `BatchExecutionNotFoundException`을 추가할 때 파일 합치기(위 Deviation 1)를 함께 처리한다.
- **BATCH-04:** 아직 완료 표시하지 않았다(`requirements-completed: []`). 실행 경로 배선(05-13)과
  API 거부(05-15)가 끝나고 동시성 테스트로 실증된 뒤에 표시한다.
- 블로커 없음.

## 이번에 쓴 기술

**1. 트랜잭션 전파 `REQUIRES_NEW` ★**
새 트랜잭션을 열어 **호출부와 무관하게 자기 것만 먼저 커밋**하는 전파 방식이다.
- *이 코드에서 왜 필요했는가:* 배치 시작 기록은 "지금 도는 배치가 있다"는 **신호**다. 이 신호가
  다른 요청에게 보이려면 **커밋돼야** 한다. 그런데 기본값 `REQUIRED`는 호출부가 이미 트랜잭션을
  열고 있으면 거기 얹혀서, 배치 본문이 다 끝날 때까지(수 분) 커밋되지 않는다. 그동안 관리자가
  버튼을 한 번 더 누르면 두 번째 요청은 `RUNNING` 행을 보지 못해 유니크 인덱스가 발동하지 않고
  그대로 실행에 들어간다 — 같은 회원의 같은 주기가 두 번 차감된다.
- *안 썼으면 뭐가 깨지는가:* 유니크 인덱스(05-11)를 만들어 놓고도 동시 실행이 그대로 통과한다.
  방어 장치가 있는데 발동하지 않는, 가장 나쁜 형태의 실패다.

**2. 자기 호출(self-invocation)과 프록시 우회 ★**
스프링의 `@Transactional`은 빈을 **프록시로 감싸서** 메서드 진입 시 트랜잭션을 여는 방식으로
동작한다. 그래서 **같은 클래스 안에서 `this.method()`로 부르면 프록시를 거치지 않아** 애노테이션이
있어도 트랜잭션이 아예 열리지 않는다.
- *이 코드에서 왜 필요했는가:* "그냥 러너 안에 `startRecord()` 메서드를 만들고 `REQUIRES_NEW`를
  붙이면 되지 않나?"가 자연스러운 첫 발상인데, 그렇게 하면 애노테이션이 **조용히 무시된다** —
  오류도 경고도 없다. 그래서 기록자를 별도 스프링 빈으로 꺼냈다. 이 저장소에는 같은 이유로
  분리된 선례가 이미 있다(`InactivityDeductionService`, 05-01).
- *안 썼으면 뭐가 깨지는가:* 코드에는 `REQUIRES_NEW`가 적혀 있는데 실제로는 전파가 일어나지 않는,
  **읽어서는 찾을 수 없는 버그**가 된다.

**3. `save` vs `saveAndFlush` — 예외가 터지는 시점 ★**
JPA는 변경을 모아 뒀다가 트랜잭션 커밋 직전에 한꺼번에 SQL로 내보낸다(flush). `saveAndFlush`는
그 시점을 **지금**으로 당긴다.
- *이 코드에서 왜 필요했는가:* 우리는 INSERT가 유니크 인덱스를 위반하는 것을 잡아서 409로
  바꿔야 한다. `save`만 쓰면 INSERT가 메서드가 끝난 **뒤**에 실행돼, 예외가 `try-catch` 바깥에서
  터진다 — 잡을 기회 자체가 없다.
- *안 썼으면 뭐가 깨지는가:* 관리자가 409("이미 실행 중") 대신 500(원인 불명 서버 오류)을 본다.

**4. rollback-only 표시 — "잡았으니 괜찮다"가 아닌 경우 ★**
DB 제약 위반으로 flush가 실패하면 그 트랜잭션은 **더 이상 커밋될 수 없는 상태**로 표시된다.
예외를 `catch`했다고 해서 트랜잭션이 되살아나지 않는다.
- *이 코드에서 왜 필요했는가:* `catch (e: DataIntegrityViolationException)` 안에서 기존 실행을
  조회해 "이미 이게 돌고 있습니다"라며 정상 반환하고 싶어지는데, 그러면 메서드는 성공으로 끝나고
  스프링이 커밋을 시도했다가 `UnexpectedRollbackException`을 던진다. 그래서 반드시 예외를 던져
  이 트랜잭션이 롤백되게 했고, 그 이유를 KDoc에 남겼다.
- *안 썼으면 뭐가 깨지는가:* 의도한 409 대신 정체불명의 500이 나가고, 스택만 보고는 원인을
  추적하기 어렵다.

**5. 부분 유니크 인덱스의 "죽은 잠금" 문제와 타임아웃 회수**
`RUNNING` 행 유일성으로 실행을 직렬화하면, 앱이 갑자기 죽었을 때 그 행이 **영원히** 남아 배치를
막는다(락을 쥔 채 죽은 것과 같다).
- *이 코드에서 왜 필요했는가:* 그래서 `start`가 먼저 "임계 시간(30분)보다 이르게 시작된 `RUNNING`
  행"만 골라 `FAILED(STALE)`로 정리한 뒤 새 행을 넣는다. 조건을 "이른 것만"으로 좁히는 게 핵심이다 —
  조건 없이 정리하면 **정상 실행 중인 배치를 죽었다고 오판해 끊는다.** 테스트에서 31분/29분
  양쪽 경계를 모두 단언한 이유가 이것이다.
- *안 썼으면 뭐가 깨지는가:* EC2가 한 번 재시작되면 그날 이후 미사용 차감이 영구히 멈추고,
  아무도 오류를 보지 못한 채 며칠 뒤에야 "잔여가 안 깎인다"로 발견된다.

**6. 일부러 쓰지 않은 것 — 낙관적 락(`@Version`)**
동시성 방어에 흔히 쓰는 방식이지만 여기엔 맞지 않는다. 낙관적 락은 "**같은 행**을 둘이 고쳐서
나중 것이 앞 것을 덮어쓰는" 상황(lost update)을 막는 장치인데, 우리 문제는 **서로 다른 새 행을
각자 INSERT하는** 상황이다 — 겹치는 행이 없으니 버전 비교가 발동할 대상 자체가 없다.
DB 제약(부분 유니크 인덱스)이 이 형태에 맞는 유일한 장치다(D-021 "동시성은 DB 제약으로 막는다").

---
*Phase: 05-batch*
*Completed: 2026-08-16*

## Self-Check: PASSED

- FOUND: `src/main/kotlin/com/goldwrestling/config/InactivityBatchProperties.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/batch/BatchAlreadyRunningException.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/batch/BatchExecutionRecorder.kt`
- FOUND: `src/test/kotlin/com/goldwrestling/batch/BatchExecutionRecorderTest.kt`
- FOUND: `.planning/phases/05-batch/05-12-SUMMARY.md`
- FOUND: commits `e7b3cb0`, `2ca48ef`, `19e8883`, `d1a9ece`
