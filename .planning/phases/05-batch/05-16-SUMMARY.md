---
phase: 05-batch
plan: 16
subsystem: batch
tags: [kotlin, spring-boot, gradle, ktlint, documentation, requirements-tracking]

# Dependency graph
requires:
  - phase: 05-batch (05-10~05-15)
    provides: "CR-01·CR-02·CR-04·WR-02·WR-04·WR-05를 닫은 코드·테스트 전체(청크 D)"
provides:
  - "전체 회귀(캐시 없음) 그린 확인: ./gradlew cleanTest test 763건 0 failures, ./gradlew build BUILD SUCCESSFUL"
  - "코드·문서 안전성 서술 전수 점검 — 사실과 다른 서술 0건"
  - "D-108 해소 항목에 AdminBatchRunConcurrencyTest(HTTP 계층 실증) 근거 보강"
  - "ROADMAP·REQUIREMENTS·VALIDATION·PATTERNS를 갭 클로저 결과로 갱신"
  - "로컬 실기동 검증 환경 준비(docker compose + bootRun, V10 적용 확인, 보존 데이터 무손실 확인)"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - docs/decisions.md
    - .planning/ROADMAP.md
    - .planning/REQUIREMENTS.md
    - .planning/phases/05-batch/05-VALIDATION.md
    - .planning/phases/05-batch/05-PATTERNS.md
    - .planning/STATE.md

key-decisions:
  - "REQUIREMENTS.md BATCH-01·02·04는 '코드 truth가 닫혔다'는 근거(전체 회귀 그린 + 개별 동시성/정책 테스트)로 Complete 전환 — 05-GAP-CONTEXT §4 완료 판정 표와 일치. BATCH-01은 CR-03(출석일 후보 부재, Phase 6 범위)이 열려 있다는 조건을 행에 명시해 미완을 잃어버리지 않게 했다"
  - "05-16 플랜 자체(ROADMAP 체크박스·STATE 진행률)는 완료로 표기하지 않았다 — Task 2(로컬 실기동 확인)를 아직 아무도 승인하지 않았다. 오케스트레이터 지시(checkpoint_handling)에 따라 사용자 승인을 날조하지 않는다"

requirements-completed: []

duration: 진행 중 (Task 1·3 완료, Task 2 사용자 승인 대기)
completed: pending
---

# Phase 05 Plan 16: Phase 5 갭 클로저 마감 검증 Summary (진행 중 — 체크포인트 대기)

**전체 회귀(캐시 없음, 763개 테스트)와 `./gradlew build`가 모두 그린이고, 코드·문서 어디에도 "동시 실행은 안전하지 않다" 계열의 사실과 다른 서술이 남아 있지 않음을 전수 점검으로 확인했다. ROADMAP·REQUIREMENTS·VALIDATION·PATTERNS를 갭 클로저(청크 D) 결과로 갱신했다. 다만 이 문서 작성 시점에 로컬 실기동 확인(Task 2, 사용자 승인 필요)은 아직 완료되지 않았다 — 아래 "사용자 확인 대기" 섹션 참조.**

## Performance

- **Duration:** Task 1·3 실행에 약 25분(전체 회귀 실행 포함)
- **Tasks:** 3개 중 2개 완료(Task 1, Task 3), 1개 대기(Task 2 — checkpoint:human-verify)
- **Files modified:** 6 (docs/decisions.md, .planning/ROADMAP.md, .planning/REQUIREMENTS.md, .planning/phases/05-batch/05-VALIDATION.md, .planning/phases/05-batch/05-PATTERNS.md, .planning/STATE.md)

## Accomplishments

- **전체 회귀 캐시 없이 실행** — `./gradlew ktlintFormat`(변경 없음, 이미 정렬됨) → `./gradlew cleanTest test`(763개 테스트, 0 failures, 0 errors, 0 skipped) → `./gradlew build`(BUILD SUCCESSFUL, ktlintCheck 포함) 순서로 실행하고 실제 실행 로그(테스트 리포트 XML 집계)로 결과를 확인했다.
- **문서·코드 정합 전수 점검** — `05-VERIFICATION.md`가 지적한 4가지 사실과 다른 서술이 `src/`·`docs/` 어디에도 남아 있지 않음을 grep으로 확인: "동시 실행은 아직 안전하지 않다"(0건), "응답이 오지 않아도 재호출하지 않는다"(0건), "차감 원자성 보장은 갭 클로저에서 정한다"(0건), "ON_LEAVE→ACTIVE 복귀일"류 서술(0건, docs/ 전체).
- **결정 문서 정합 확인** — D-108 해소·D-111 정정·D-113 갱신·D-114 갱신(에러코드+202/조회)·D-117/D-118/D-119(신규) 모두 존재를 확인했다. D-108 해소 항목이 `InactivityBatchRunConcurrencyTest`만 인용하고 있어, HTTP 계층 실증인 `AdminBatchRunConcurrencyTest`(05-15)를 함께 남겼다(Task 1의 명시적 요구사항).
- **요구사항 추적 확인** — `05-1*-PLAN.md`의 frontmatter `requirements` 필드를 모두 모아 BATCH-01·02·03·04가 각각 최소 1회 등장함을 확인했다.
- **ROADMAP·REQUIREMENTS·VALIDATION·PATTERNS 갱신** — 아래 "Files Created/Modified" 참조. 청크 D(05-10~05-15)가 각각 CR-01/02/04·WR-02/04/05 중 무엇을 닫았는지 ROADMAP에 명시하고, CR-03(범위 밖)의 운영 대응(cron 비활성 배포)을 함께 남겼다.
- **로컬 실기동 검증 환경 준비** — `docker compose ps`로 로컬 Postgres가 이미 떠 있고 보존 데이터(member 4·pass 7·pass_transaction 35·batch_execution 3, `RUNNING` 0건)가 그대로임을 확인한 뒤, `./gradlew bootRun`을 백그라운드로 띄웠다. 기동 로그에서 `Successfully validated 10 migrations` + `Schema "public" is up to date`로 V10이 이미 적용돼 있음을 확인했고, 기동 후에도 보존 데이터 카운트가 그대로임을 psql로 재확인했다.

## Task Commits

각 태스크는 원자적으로 커밋했다:

1. **Task 1: 전체 회귀 실행 + 문서·코드 정합 전수 점검** - `c4a0b83` (docs)
3. **Task 3: ROADMAP·REQUIREMENTS·VALIDATION·PATTERNS를 갭 클로저 결과로 갱신** - `030cfcc` (docs)
- **STATE 갱신** - `61e4e51` (docs)

**Task 2(로컬 실기동 확인)는 checkpoint:human-verify — 커밋 없음, 사용자 승인 대기.**

## Files Created/Modified

- `docs/decisions.md` — D-108 해소 항목에 `AdminBatchRunConcurrencyTest`(HTTP 계층 동시성 실증) 근거 보강
- `.planning/ROADMAP.md` — Phase 5 절에 "갭 클로저(2026-08-16, 청크 D)" 블록 신설(CR-01/02/04·WR-02/04/05가 어느 플랜에서 닫혔는지, CR-03 범위 밖 + 운영 cron 비활성 대응), 05-16 항목에 전체 회귀 완료·로컬 실기동 대기 상태 명시, 진행률 표 15/16으로 갱신
- `.planning/REQUIREMENTS.md` — BATCH-01·02·04 체크박스 Complete 전환(BATCH-01은 CR-03/Phase 6 조건 명시), 커버리지 표 `Gaps found` 3행 제거, Traceability 표 갱신
- `.planning/phases/05-batch/05-VALIDATION.md` — wave 10~16(갭 클로저 21개 태스크) 검증 맵을 기존 표에 이어붙임, 러너 레벨·HTTP 레벨 동시성 테스트 2종이 BATCH-04를 실증하는 근거 명시
- `.planning/phases/05-batch/05-PATTERNS.md` — 갭 클로저 신규 파일(`BatchExecutionRecorder.kt`·`InactivityBatchProperties.kt`·`BatchExecutorConfig.kt`·`AdminBatchService.kt`·`BatchExceptions.kt` 등)의 analog를 각 플랜 `<interfaces>`에서 그대로 가져와 추가
- `.planning/STATE.md` — Current Position·Blockers·Decisions·Session Continuity를 05-16 진행 상태(Task 1·3 완료, Task 2 대기)로 갱신

## Decisions Made

- **REQUIREMENTS.md BATCH-01·02·04를 Complete로 전환한 근거** — 05-GAP-CONTEXT.md §4 완료 판정 표가 이 세 truth를 "코드·테스트로 닫혔다"고 명시했고(BATCH-01: 시행일 하한+상한 1회, BATCH-02: 휴회 이탈 전이 전체 기록, BATCH-04: RUNNING 유니크+409 거부, 각각 동시성 테스트로 실증), 이번 전체 회귀(763건 그린)가 그 상태를 재확인했다. 로컬 실기동(Task 2)은 이 코드 truth에 대한 추가 신뢰 구축 단계이지, 코드 자체의 정합성을 좌우하는 게이트가 아니라고 판단했다.
- **05-16 플랜 자체는 완료로 표기하지 않았다** — ROADMAP의 05-16 체크박스는 `[ ]`로 남겼고, STATE.md 진행률도 100%로 올리지 않았다. Task 2(로컬 실기동 202 접수·조회·잔여 변화 확인)를 아직 아무도 승인하지 않았고, 오케스트레이터 지시(checkpoint_handling)가 "사용자가 확인했다"고 문서에 쓰지 말라고 명시했기 때문이다. 이 판단은 REQUIREMENTS.md의 개별 요구사항 상태(코드 truth 기준)와 05-16 플랜의 완료 여부(체크포인트 승인 포함)를 분리한 것이다.

## Deviations from Plan

### 계획과 다르게 판단한 것 (오케스트레이터 지시 우선 적용)

**1. [체크포인트 제외 지시에 따른 판단] 05-16 acceptance_criteria `grep -c '\[x\] 05-1[0-6]-PLAN.md' == 7` 미충족**

- **원인:** 이 플랜의 acceptance_criteria는 Task 2(체크포인트)까지 포함해 완전히 끝난 상태를 전제로
  작성됐다(7개 = 05-10~05-16 전부 `[x]`). 그러나 이번 실행은 오케스트레이터 지시(checkpoint_handling,
  priority="critical")에 따라 Task 2를 직접 수행·통과 처리하지 않고 사용자 승인을 기다려야 한다.
- **판단:** ROADMAP의 05-16 항목을 `[x]`로 표기하지 않았다 — 현재 `[x] 05-1[0-6]-PLAN.md` 카운트는
  6이다(05-10~05-15). 사용자가 Task 2를 승인하면 이어지는 세션에서 05-16을 `[x]`로 바꾸고 이 미충족
  항목이 자연히 해소된다.
- **영향 범위:** ROADMAP.md 체크박스 1개, STATE.md 진행률 표기. 코드·테스트·다른 문서 갱신에는
  영향이 없다.

그 외 배포·검증 로직 자체에는 계획과의 편차가 없다 — Task 1·3은 계획대로 실행했다.

## 사용자 확인 대기 (Task 2 — checkpoint:human-verify)

**아직 아무도 이 항목을 확인하지 않았다.** 아래 절차를 그대로 실행해 결과를 알려주면, 기대와
일치하는지 대조한 뒤 다음 단계(ROADMAP 05-16 `[x]` 전환 + PR 생성)로 넘어간다.

### 0. 준비 상태 (이미 완료 — 그대로 쓰면 된다)

- 로컬 Postgres는 이미 떠 있고 **Phase 5 검증 데이터가 보존돼 있다**: `member` 4건, `pass` 7건,
  `pass_transaction` 35건, `batch_execution` 3건(전부 종료 상태, `RUNNING` 0건).
  **`docker compose down -v`를 실행하지 않았다 — 절대 실행하지 말 것.**
- `./gradlew bootRun`을 이미 백그라운드로 띄워 뒀다. 기동 로그에서 Flyway가
  `Successfully validated 10 migrations` / `Schema "public" is up to date. No migration necessary.`를
  출력했다 — **V10이 이미 이 DB에 적용돼 있다는 뜻**이다(새로 적용되는 로그가 아니라 "이미 있다"는
  로그가 정상이다).
- 앱은 `http://localhost:8080`에서 응답 중이다.

### 1. 관리자 토큰 발급

로컬 `.env`의 `ADMIN_SEED_LOGIN_ID`·`ADMIN_SEED_PASSWORD` 값으로 로그인한다(실값은 이 문서에
쓰지 않는다 — 본인 `.env` 파일에서 확인):

```bash
curl -s -X POST http://localhost:8080/api/auth/admin/login \
  -H "Content-Type: application/json" \
  -d '{"loginId":"<ADMIN_SEED_LOGIN_ID 값>","password":"<ADMIN_SEED_PASSWORD 값>"}' | jq .
```

응답의 `tokens.accessToken`을 복사해 아래에서 `$TOKEN`으로 쓴다.

```bash
TOKEN="<위에서 복사한 accessToken>"
```

### 2. 202 접수 확인

```bash
curl -si -X POST http://localhost:8080/api/admin/batch/inactivity-runs \
  -H "Authorization: Bearer $TOKEN"
```

**기대:** HTTP `202`, 응답 본문의 `status`가 `RUNNING`, `finishedAt`이 `null`, `Location` 헤더가
`/api/admin/batch/inactivity-runs/{id}`를 가리킨다.

### 3. 단건 조회(폴링)로 결과 확인

```bash
curl -s http://localhost:8080/api/admin/batch/inactivity-runs/<위 Location의 id> \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**기대:** 잠시 뒤 `status`가 `SUCCESS`(또는 `PARTIAL_FAILURE`)로 바뀌고 `processedMemberCount`·
`deductedCount`·`skippedCount` 집계가 채워진다.

### 4. 목록 조회

```bash
curl -s "http://localhost:8080/api/admin/batch/inactivity-runs?limit=5" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**기대:** 최근 실행이 `startedAt` 내림차순으로 오고, 기존 3건 + 이번 실행이 함께 보인다.

### 5. 연속 두 번 빠르게 호출(동시 실행 거부 확인)

```bash
curl -si -X POST http://localhost:8080/api/admin/batch/inactivity-runs -H "Authorization: Bearer $TOKEN" &
curl -si -X POST http://localhost:8080/api/admin/batch/inactivity-runs -H "Authorization: Bearer $TOKEN" &
wait
```

**기대:** 하나는 `202`, 다른 하나는 `409` + 본문 `code`가 `BATCH_ALREADY_RUNNING` +
`Content-Type: application/problem+json`.
※ 대상 회원이 적어 배치가 즉시 끝나면 두 번째도 202가 나올 수 있다 — 그 경우 그 응답의
`deductedCount`가 0인지만 확인하면 된다(순차 재실행은 원래 안전하다). 동시 호출의 409는
`AdminBatchRunConcurrencyTest`가 이미 결정론적으로 증명했다.

### 6. DB 상태 확인

```bash
docker compose exec -T postgres psql -U gold -d gold_wrestling -c \
  "select count(*) from batch_execution where status='RUNNING';"
docker compose exec -T postgres psql -U gold -d gold_wrestling -c \
  "select id, status, finished_at, deducted_count from batch_execution order by id desc limit 5;"
```

**기대:** 첫 쿼리 결과 `0`. (`$DB_USERNAME`/`$DB_NAME`이 다르면 `.env`의 실제 값으로 바꿔서 실행)

### 7. 응답 본문 개인정보 미노출 확인

2~4번 응답 어디에도 회원 이름·전화번호·회원 id 목록이 없는지 눈으로 확인한다.

### 8. 승인 또는 이의 제기

- 1~7번이 모두 기대대로면 **"승인"**이라고 답한다 — 다음 세션이 ROADMAP 05-16을 `[x]`로 바꾸고
  `.claude/skills/deliver-phase-chunk/SKILL.md` 절차로 PR을 생성한다.
- 기대와 다른 항목이 있으면 **그 번호와 실제로 받은 값**을 알려준다 — 원인을 찾아 고친 뒤 다시
  확인을 요청한다.

**확인이 끝나면 로컬 DB의 Phase 5 검증 데이터를 임의로 지우지 말 것** — 다음 단계에서도 이
데이터를 기준으로 재확인할 수 있다.

## Verification Results (Task 1)

| 항목 | 결과 |
|---|---|
| `./gradlew ktlintFormat` | BUILD SUCCESSFUL (변경 없음 — 이미 정렬됨) |
| `./gradlew cleanTest test` | BUILD SUCCESSFUL — **763 tests, 0 failures, 0 errors, 0 skipped**(테스트 리포트 XML 집계, 캐시 재사용 아님 — cleanTest로 강제 재실행) |
| `./gradlew build` | BUILD SUCCESSFUL (ktlintCheck 포함) |
| `grep -rn "동시 실행은 아직 안전하지 않다" src/ docs/` | 0건 |
| `grep -rn "응답이 오지 않아도 재호출하지 않는다" src/ docs/` | 0건 |
| `grep -rn "차감 원자성 보장은 갭 클로저에서 정한다" src/ docs/` | 0건 |
| `grep -rn "ON_LEAVE.*→.*ACTIVE.*복귀일" docs/` | 0건 |
| `grep -c '^## D-117\|^## D-118\|^## D-119' docs/decisions.md` | 3 |
| `grep -h 'requirements:' .planning/phases/05-batch/05-1*-PLAN.md` | BATCH-01·02·03·04 모두 등장 확인 |
| `git status --porcelain src/main/resources/db/migration/` | 빈 결과(V1~V10 어느 것도 변경 없음) |
| `grep -c 'AdminBatchRunConcurrencyTest' docs/decisions.md` | 1 (D-108 해소 항목에 보강) |

## Issues Encountered

없음. Task 1·3 모두 계획대로 진행됐고 첫 실행에서 통과했다.

## User Setup Required

- **로컬 실기동 확인(Task 2)** — 위 "사용자 확인 대기" 섹션의 1~8단계 수행 필요. 새 환경변수·
  마이그레이션·의존성은 없다(05-12~05-15가 이미 도입한 배치 정책 값 3종은 전부 기본값이 있다).

## Next Phase Readiness

- Task 2 승인 후 남은 작업: ROADMAP.md의 05-16 체크박스를 `[x]`로 전환하고, STATE.md 진행률을
  16/16으로 갱신한 뒤, `.claude/skills/deliver-phase-chunk/SKILL.md` 절차에 따라
  `feature/phase-05d-gap-closure` 브랜치를 커밋·푸시하고 dev 대상 PR 1개를 생성한다(PR 머지는
  사용자가 한다). PR 본문에는 청크 D(4/4)가 닫은 결함(CR-01·CR-02·CR-04·WR-02·WR-04·WR-05)과
  범위 밖인 CR-03·운영 대응(cron 비활성 배포)을 적는다.
- **CR-03(출석일 후보 부재)은 여전히 열려 있다** — Phase 6이 `Attendance`를 도입하기 전까지 운영
  배포는 `BATCH_INACTIVITY_SCHEDULER_ENABLED=false`로 cron을 꺼 둔 채 한다(D-116·D-119). 이 사실은
  ROADMAP·REQUIREMENTS 양쪽에 조건으로 명시했다.
- 블로커 없음 — 진행을 막는 것은 오직 사용자의 Task 2 확인뿐이다.

---
*Phase: 05-batch*
*Status: 진행 중 — Task 2(로컬 실기동 확인) 사용자 승인 대기*

## Self-Check: PASSED

- FOUND: `.planning/phases/05-batch/05-16-SUMMARY.md`
- FOUND: commit `c4a0b83` (Task 1)
- FOUND: commit `030cfcc` (Task 3)
- FOUND: commit `61e4e51` (STATE 갱신)
- CONFIRMED: 로컬 앱이 `http://localhost:8080`에서 응답 중, V10 적용 확인, 보존 데이터(member 4·pass 7·pass_transaction 35·batch_execution 3·RUNNING 0) 무손실
