---
phase: 05-batch
plan: 11
subsystem: batch
tags: [kotlin, spring-boot, jpa, postgresql, flyway, partial-unique-index, concurrency, testcontainers]

# Dependency graph
requires:
  - phase: 05-batch (05-01~05-08)
    provides: "batch_execution 테이블(V9, D-113), InactivityBatchRunner, BatchExecutionResponse, AdminBatchController"
  - phase: 05-batch (05-10)
    provides: "CR-04 휴회 이탈 시각 기록 조건 확장 — V9 주석 정정 대상 사실"
provides:
  - "V10 마이그레이션: batch_execution.finished_at NOT NULL 완화 + status='RUNNING' 부분 유니크 인덱스(uq_batch_execution_running) — 동시에 존재하는 실행 중 행 최대 1건을 DB가 보장"
  - "BatchExecutionStatus.RUNNING·FAILED (WR-02: 전체 실패도 이력에 남는다)"
  - "BatchExecution: 시작 시 삽입 → 종료 시 finish()로 확정하는 모델, triggeredByAdminId 스칼라 매핑(LAZY 연관 0개, WR-04)"
  - "BatchExecutionRepository.findFirstByStatus / markStaleRunningAsFailed / findAllByOrderByStartedAtDesc"
  - "실행 중 행 2건 동시 존재 불가를 실제 PostgreSQL로 실증한 통합테스트 + finish() 엔티티 단위테스트"
  - "docs/decisions.md D-117 신규 + D-113 스키마 갱신, docs/glossary.md 상태값 4종·실행 중 배치·stale RUNNING 용어"
affects: [05-12, 05-13, 05-14, 05-15, 05-16]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "PostgreSQL 부분 유니크 인덱스(CREATE UNIQUE INDEX ... WHERE 조건)로 '특정 상태의 행은 동시에 1건'을 강제하는 첫 사례 — 이 저장소의 실행 직렬화 관례가 된다"
    - "실행 이력 엔티티는 '시작 시 삽입 → 종료 시 엔티티 메서드 finish()로 일괄 확정' — status만 바꾸고 finishedAt을 빠뜨리는 반쪽 확정을 구조적으로 차단"
    - "관리자 존재 검증을 FK 위반에 위임하지 않는다 — 두 실패 원인이 같은 DataIntegrityViolationException으로 뭉개지면 409 변환이 불가능해진다"

key-files:
  created:
    - src/main/resources/db/migration/V10__allow_running_batch_execution.sql
    - src/test/kotlin/com/goldwrestling/batch/BatchExecutionTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/batch/BatchExecutionStatus.kt
    - src/main/kotlin/com/goldwrestling/batch/BatchExecution.kt
    - src/main/kotlin/com/goldwrestling/batch/BatchExecutionRepository.kt
    - src/main/kotlin/com/goldwrestling/batch/dto/BatchExecutionResponse.kt
    - src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt
    - src/test/kotlin/com/goldwrestling/batch/BatchExecutionRepositoryTest.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt
    - docs/decisions.md
    - docs/glossary.md
    - docs/api/openapi.yaml

key-decisions:
  - "D-117 신규: 배치 실행 직렬화는 batch_execution RUNNING 행 + status='RUNNING' 부분 유니크 인덱스로 한다 (05-GAP-CONTEXT §2.1 확정안 이행, advisory lock·원장 주기 유니크 인덱스·회원 행 비관적 락은 기각 사유와 함께 기록)"
  - "D-113에 V10 스키마 갱신 3건 추가: status에 RUNNING·FAILED, finished_at nullable, triggered_by_admin_id 스칼라 매핑(WR-04)"
  - "관리자 존재 검증(resolveTriggeredByAdminId)은 유지 — FK 위반에 맡기면 '관리자 없음'과 '이미 실행 중'이 같은 예외가 되어 05-12의 409 변환이 원인을 구분할 수 없다"
  - "BatchExecutionRepositoryTest에 @AfterEach 삭제 훅을 두지 않고 클래스 레벨 @Transactional 롤백에 맡긴다 — 유니크 위반을 단언하는 테스트는 PostgreSQL 트랜잭션이 abort 상태라 @AfterEach의 DELETE가 그 테스트를 실패시킨다"
  - "API 표면(finishedAt nullable, status enum 2종 추가)이 바뀌었으므로 CLAUDE.md 규칙 4에 따라 docs/api/openapi.yaml을 같은 커밋에서 재생성"

patterns-established:
  - "부분 유니크 인덱스를 실증하는 통합테스트는 saveAndFlush로 flush 시점 위반을 드러내고, 같은 트랜잭션 안에서 '앞선 행을 종료 상태로 확정 → 새 RUNNING 저장 성공'까지 함께 단언한다"
  - "목록 조회 페이징 테스트는 시작 시각을 먼 미래로 두어 같은 Testcontainers 컨테이너를 쓰는 다른 테스트 클래스의 잔여 행과 섞이지 않게 한다"

requirements-completed: []

duration: 30min
completed: 2026-08-16
---

# Phase 05 Plan 11: 배치 실행 직렬화 스키마·모델 기반(CR-01·WR-02·WR-04) Summary

**`batch_execution`에 `status='RUNNING'` 부분 유니크 인덱스를 걸어 "동시에 실행 중인 배치는 최대 1건"을 DB가 물리적으로 보장하게 하고, 실행 이력을 "시작 시 삽입 → 종료 시 `finish()`로 확정"하는 모델로 바꾸면서 `BatchExecution`의 LAZY 연관을 스칼라 컬럼으로 걷어냈다.**

## Performance

- **Duration:** 약 30분 (2026-08-16 01:19~01:28 커밋 구간 + 사전 조사·검증)
- **Tasks:** 3 (TDD 2건은 RED/GREEN 분리 커밋)
- **Files:** 12 (신규 3, 수정 9)

## Accomplishments

- **V10 마이그레이션** — `finished_at`의 `NOT NULL`을 완화해 실행 중 행을 표현할 수 있게 하고,
  `CREATE UNIQUE INDEX uq_batch_execution_running ON batch_execution (status) WHERE status = 'RUNNING'`으로
  실행 직렬화를 DB로 내렸다. 헤더 주석에 ① 이 인덱스의 보장 내용(D-117), ② `status`가 값 CHECK 없는
  `VARCHAR(20)`이라 `RUNNING`·`FAILED` 추가에 DDL이 더 필요 없다는 것, ③ 두 번째 배치 도입 시
  `(batch_type, status)` 복합 부분 인덱스 확장 경로, ④ 되돌리기 비용(V11 `DROP INDEX` 한 줄),
  ⑤ 커밋된 V9의 `returned_from_leave_at` 주석 정정(05-10, CR-04)을 남겼다.
- **상태값·엔티티·DTO 전환** — `RUNNING`(실행 중, 동시 1건)·`FAILED`(실행 전체 실패)를 추가하고,
  `BatchExecution`을 `RUNNING`으로 시작해 `finish(...)` 한 번으로 확정하는 모델로 바꿨다.
  `trigger`·`startedAt`·`triggeredByAdminId`는 `val`로 잠그고 확정 필드만 `var`로 열었다.
  `triggeredBy: Admin?`(`@ManyToOne(LAZY)`)을 `triggeredByAdminId: Long?` 스칼라로 교체해 엔티티에서
  LAZY 연관을 완전히 없앴다(WR-04) — 05-15의 조회 API가 DB에서 다시 읽은 이력을 트랜잭션 밖에서
  변환해도 `LazyInitializationException`이 날 여지가 사라졌다.
- **리포지토리 쿼리 3종** — `findFirstByStatus`(실행 중 행 관측), `markStaleRunningAsFailed`(임계 시각
  이전 `RUNNING`만 `FAILED`로 정리하는 조건부 벌크 UPDATE), `findAllByOrderByStartedAtDesc(Pageable)`
  (05-15 목록 조회용).
- **실증** — 실제 PostgreSQL(Testcontainers)에서 두 번째 `RUNNING` 저장이
  `DataIntegrityViolationException`으로 거부되고, 앞선 실행을 `SUCCESS`로 확정하면 새 `RUNNING`을
  저장할 수 있음을 단언했다. `finish()`는 스프링 없는 단위테스트로 검증했다.
  로컬 DB(Phase 5 보존 데이터)에도 V10을 실제로 적용해 `ddl-auto=validate` 통과와 보존 데이터 무손실을 확인했다.
- **문서** — `docs/decisions.md`에 **D-117**을 신규 기록(선택 이유 3가지, 기각 대안 3종의 구체적 사유,
  stale `RUNNING` 약점과 담당 플랜, 확장 경로)하고 **D-113**에 V10 스키마 갱신 3건을 붙였다.
  `docs/glossary.md` 배치 절에 상태값 4종의 의미와 "실행 중 배치"·"방치된 실행 중 행" 용어를 추가했다.

## Task Commits

| Task | 내용 | 커밋 | 종류 |
|---|---|---|---|
| 1 | V10 — 실행 중 행 허용 + RUNNING 부분 유니크 인덱스 | `fe8dced` | feat |
| 2 (RED) | 실행 이력 시작·확정 모델 단위테스트 | `6ecc51e` | test |
| 2 (GREEN) | 상태값·엔티티·DTO·러너 전환 + openapi.yaml 재생성 | `c869bfc` | feat |
| 3 (RED) | RUNNING 유일성·stale 정리·목록 조회 통합테스트 | `3524246` | test |
| 3 (GREEN) | 리포지토리 쿼리 3종 + D-117·D-113·glossary | `8fd53ea` | feat |

TDD 게이트: 두 태스크 모두 `test(...)` 커밋(컴파일 실패로 RED 확인) → `feat(...)` 커밋(테스트 통과) 순서를
지켰다. REFACTOR 단계는 정리할 중복이 없어 커밋하지 않았다.

## Files Created/Modified

- `src/main/resources/db/migration/V10__allow_running_batch_execution.sql` — 신규. `finished_at` NOT NULL 완화 + `uq_batch_execution_running` 부분 유니크 인덱스 + V9 주석 정정
- `src/main/kotlin/com/goldwrestling/batch/BatchExecutionStatus.kt` — `RUNNING`·`FAILED` 추가, `PARTIAL_FAILURE`(일부 실패)와 `FAILED`(루프 미완주)의 차이를 KDoc에 명시
- `src/main/kotlin/com/goldwrestling/batch/BatchExecution.kt` — 스칼라 `triggeredByAdminId`, nullable `finishedAt`, 확정 필드 `var` + `finish()`, KDoc의 "append-only / 전 필드 val" 서술을 사실에 맞게 재작성
- `src/main/kotlin/com/goldwrestling/batch/BatchExecutionRepository.kt` — 쿼리 3종 + "직렬화는 인덱스가 보장하고 조회는 관측용"이라는 경계를 KDoc에 명시
- `src/main/kotlin/com/goldwrestling/batch/dto/BatchExecutionResponse.kt` — `finishedAt: OffsetDateTime?`, 스칼라 관리자 id, LAZY 안전성 서술 재작성(조건 없이 안전)
- `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt` — `resolveTriggeredByAdminId`로 교체(관리자 존재 검증 유지), 생성 호출 갱신, `Admin` import 제거
- `src/test/kotlin/com/goldwrestling/batch/BatchExecutionTest.kt` — 신규 단위테스트 5종(상태값 4종, RUNNING 시작 기본값, `finish()` 확정, FAILED 확정, 시작 확정값 불변)
- `src/test/kotlin/com/goldwrestling/batch/BatchExecutionRepositoryTest.kt` — 기존 CHECK 제약 테스트 4종 계약 유지 + 유니크·stale·목록·DTO 변환 테스트 8종 추가(총 13종)
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt` — `triggeredBy` 단언 2건을 스칼라 필드로 갱신(테스트명 포함)
- `docs/decisions.md` — D-117 신규, D-113 갱신
- `docs/glossary.md` — 배치 절 상태값 4종 + 용어 2건
- `docs/api/openapi.yaml` — 재생성(`finishedAt` nullable, `status` enum에 RUNNING·FAILED)

## Decisions Made

- **`@AfterEach` 삭제 훅 대신 클래스 레벨 `@Transactional` 롤백** — 플랜은 `BatchExecutionRepositoryTest`가
  만든 행을 `@AfterEach`에서 지우라고 지시했지만, 유니크 위반을 단언하는 테스트는 위반 직후
  PostgreSQL 트랜잭션이 abort 상태가 되어 이후 어떤 SQL도 실행할 수 없다 — `@AfterEach`의 DELETE가
  `current transaction is aborted`로 실패해 정상 테스트를 깨뜨린다. 이 클래스는 이미 클래스 레벨
  `@Transactional`이라 롤백이 더 강한 보장을 주므로 그쪽에 맡기고, **RUNNING 행을 남기면 다른 테스트
  클래스의 배치 실행이 전부 막힌다는 위험(T-05D-11-01)과 왜 `@AfterEach`가 아닌지**를 클래스 KDoc에 남겼다.
- **`BatchExecutionResponse.from` 검증 위치** — 플랜 Task 2의 behavior였지만 `from()`이 저장된 `id`를
  요구해(`requireNotNull`) 순수 단위테스트로는 검증할 수 없다. DB에서 다시 읽은 `RUNNING` 이력을
  변환하는 통합테스트(Task 3)로 배치했다 — WR-04가 실제로 문제가 되는 경로(DB 재조회)와 같은 형태다.
- **관리자 존재 검증 유지** — 플랜대로 `adminRepository.findById(...).orElseThrow`를 남기고 id만 반환.
  이유는 D-117에도 기록: FK 위반에 맡기면 05-12가 409 변환에서 "관리자 없음"과 "이미 실행 중"을
  구분할 수 없다.

## Deviations from Plan

### Auto-fixed / 규칙 기반 추가

**1. [Rule 2 - CLAUDE.md 규칙 4] `docs/api/openapi.yaml` 재생성을 Task 2에 포함**
- **Found during:** Task 2 (`BatchExecutionResponse` 수정 직후)
- **Issue:** 플랜에는 OpenAPI 재생성 지시가 없었으나, `finishedAt`이 nullable이 되고 `status` enum에
  `RUNNING`·FAILED`가 추가돼 **FE가 타입을 생성하는 계약 파일이 실제로 바뀌었다.** CLAUDE.md 규칙 4는
  API 변경 시 같은 커밋에 재생성을 요구한다(문서 우선순위상 플랜보다 우선).
- **Fix:** `docker compose up -d` 상태에서 `./gradlew generateApiDocs`(D-029) 실행, diff가 의도한 2곳뿐임을
  확인하고 Task 2 커밋에 포함.
- **Commit:** `c869bfc`

**2. [Rule 2 - CLAUDE.md 규칙 10 / conventions §10.0] `BatchExecutionTest.kt` 신규 단위테스트**
- **Found during:** Task 2
- **Issue:** 플랜의 `files_modified`에는 없지만 `finish()`는 **엔티티 메서드**이고, conventions §10.0 표는
  엔티티 메서드에 단위테스트를 필수로 요구한다. 통합테스트만으로는 "시작 확정값이 finish 이후에도
  불변"·"FAILED로도 확정 가능" 같은 계약이 커버되지 않는다.
- **Fix:** 스프링 없는 단위테스트 5종을 추가하고 RED→GREEN 순서로 커밋.
- **Commit:** `6ecc51e`(RED) / `c869bfc`(GREEN)

**3. [Rule 3 - 차단 이슈] `BatchExecutionRepositoryTest` 생성자 호출 5곳 수정을 Task 3 → Task 2로 이동**
- **Found during:** Task 2
- **Issue:** 플랜은 이 파일 수정을 Task 3에 배치했으나, 엔티티 시그니처를 바꾸면 **테스트 소스셋 전체가
  컴파일되지 않아** Task 2의 검증 명령(`InactivityBatchRunnerTest` 실행)이 아예 불가능하다.
- **Fix:** Task 2에서 인자명만 기계적으로 갱신(`triggeredBy = admin` → `triggeredByAdminId = admin.id`)하고,
  새 테스트 추가·테스트명 정정은 계획대로 Task 3에서 처리.
- **Commit:** `c869bfc`

### 플랜 지시와 다르게 판단한 것

**4. `@AfterEach` 삭제 훅 미도입** — 위 "Decisions Made" 첫 항목 참조. 계획의 목적(RUNNING 행 누출 방지)은
클래스 레벨 트랜잭션 롤백으로 더 강하게 달성되며, 지시대로 구현하면 유니크 위반 테스트가 깨진다.

## Issues Encountered

- 없음. RED 두 번 모두 의도한 컴파일 실패로 확인됐고, GREEN 후 `./gradlew cleanTest test --tests
  'com.goldwrestling.batch.*'`와 전체 `cleanTest build`가 모두 통과했다.
- **로컬 보존 데이터 확인 메모:** V10 적용 **전에** `batch_execution` 3건이 모두 종료 상태
  (`SUCCESS`)·`finished_at` non-null이고 `RUNNING` 행이 0건임을 psql로 먼저 확인한 뒤 앱을 기동했다.
  적용 후 `flyway_schema_history`에 V10 성공 기록, `uq_batch_execution_running` 인덱스 1건,
  `batch_execution` 3건·`member` 4건(id 4·5 포함)·`pass` 7건(id 6·7 포함)이 그대로 남아 있음을 확인했다.
  `pass_transaction`은 현재 35건이며 최대 id가 38이다 — 05-GAP-CONTEXT의 "38"은 id 기준 표기이고
  이번 작업은 로컬 DB에 DELETE를 실행하지 않았다.

## Verification Results

| 항목 | 결과 |
|---|---|
| `./gradlew cleanTest test --tests 'com.goldwrestling.batch.*'` | BUILD SUCCESSFUL (배치 테스트 전체, 신규 13종 포함) |
| `./gradlew ktlintFormat` → `./gradlew cleanTest build` | BUILD SUCCESSFUL |
| V10 로컬 DB 적용 | `flyway_schema_history` version 10 success=t, `ddl-auto=validate` 통과(앱 기동 성공) |
| `uq_batch_execution_running` 인덱스 | psql `\di`로 1건 확인 |
| 보존 데이터 | `batch_execution` 3건 / `member` 4건 / `pass` 7건 유지, `RUNNING` 행 0건 |
| 커밋된 V1~V9 | 이번 플랜 커밋 5개에서 수정 0건 |

## User Setup Required

None — 새 의존성·환경변수 없음.

## Next Phase Readiness

- **05-12**: `markStaleRunningAsFailed`와 `findFirstByStatus`가 준비됐다. 남은 일은 stale 임계 시간
  프로퍼티(기본 30분)와 `BatchAlreadyRunningException` → 409 `ErrorCode` 추가, 그리고 시작 경로 배선이다.
  `DataIntegrityViolationException`을 409로 바꿀 때 **관리자 없음은 이미 `IllegalStateException`으로
  분리돼 있으므로** 유니크 위반만 409로 매핑하면 된다.
- **05-13**: 실행 흐름 재편(시작 INSERT / 본문 / 종료 확정)에 필요한 엔티티 API(`finish()`)가 준비됐다.
  05-GAP-CONTEXT §2.1대로 시작·종료 UPDATE는 러너 본문과 별개 트랜잭션이어야 하므로 **별도 스프링 빈**이
  필요하다(러너에 트랜잭션 애노테이션 금지, D-112).
- **05-15**: `findAllByOrderByStartedAtDesc(Pageable)`과 LAZY 없는 응답 변환이 준비됐다.
- **주의:** 이 플랜은 아직 러너를 `RUNNING` 행 방식으로 바꾸지 않았다 — 현재 러너는 여전히 종료 시점에
  이력 1건을 저장하므로 `RUNNING` 행을 만들지 않는다. 따라서 CR-01은 **아직 닫히지 않았다**(05-12·05-13이 닫는다).
- 블로커 없음.

---
*Phase: 05-batch*
*Completed: 2026-08-16*

## Self-Check: PASSED

- FOUND: `src/main/resources/db/migration/V10__allow_running_batch_execution.sql`
- FOUND: `src/test/kotlin/com/goldwrestling/batch/BatchExecutionTest.kt`
- FOUND: `.planning/phases/05-batch/05-11-SUMMARY.md`
- FOUND: commits `fe8dced`, `6ecc51e`, `c869bfc`, `3524246`, `8fd53ea`
