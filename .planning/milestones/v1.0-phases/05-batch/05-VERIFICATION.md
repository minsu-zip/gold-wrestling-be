---
phase: 05-batch
verified: 2026-08-16T07:41:32Z
status: passed
score: 4/4 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 1/4
  gaps_closed:
    - "BATCH-01: SESSION_PASS가 기준일 기준 2주 미사용이면 1회 자동 차감되고, 이후 2주마다 반복 차감되며 이력이 INACTIVITY 사유로 남는다"
    - "BATCH-02: ON_LEAVE 기간, 잔여 0, 유효기간 만료된 이용권은 자동 차감 대상에서 제외된다"
    - "BATCH-04: 같은 날 배치를 두 번 이상 실행해도 이중 차감이 발생하지 않는다(멱등)"
  gaps_remaining: []
  regressions: []
deferred:
  - truth: "기준일 후보 ①(마지막 출석일)이 실제 출석 기록으로 채워져, 저녁반 전용 SESSION_PASS 회원이 부당 차감되지 않는다 (CR-03)"
    addressed_in: "Phase 6"
    evidence: >
      Phase 6 Goal: "관리자가 모든 수업의 출석을 체크하고 공지사항을 운영하며…" — Attendance 도입이
      Phase 6 범위다. ROADMAP Phase 5 Note("기준일 후보 ①은 Phase 6까지 자연히 부재로 동작한다 — 의도된
      동작") 및 05-GAP-CONTEXT.md §1 "닫지 않는다"에 사용자 확인을 거쳐 명시된 설계 결정.
      그동안의 대응은 D-116 킬 스위치(BATCH_INACTIVITY_SCHEDULER_ENABLED=false)로 cron을 꺼 둔 채
      운영 배포하는 것이다(D-119).
human_verification:
  - test: "WR-06(이월): 탈퇴·장기미이용(INACTIVE) 회원의 SESSION_PASS도 2주 미사용 차감 대상에 계속 포함되는 현재 동작이 의도인지 확인"
    expected: "policies §4.3 예외 3종(휴회·잔여0·만료)에 INACTIVE가 없으므로 문면상 현재 구현이 틀리지는 않으나, 탈퇴 회원 잔여가 계속 깎여 0이 되면 환불 분쟁 소지가 있다 — 의도된 것인지 결정 필요"
    why_human: "정책 문서가 이 케이스를 의식하고 쓴 문장인지 확인된 바 없다(CLAUDE.md 규칙 8). 이전 검증에서 제기됐고 아직 사용자 판단이 없어 그대로 이월한다 — 이번 갭 클로저 범위 밖이며 truth 판정에는 영향이 없다"
  - test: "신규: `.env.example`의 `BATCH_INACTIVITY_SCHEDULER_ENABLED=true`를 `false`로 바꿀지 결정 (CR-03이 닫힐 때까지)"
    expected: "CR-03이 열려 있는 동안 '운영 배포는 cron을 꺼 둔 채 한다'가 안전 전제인데, 이 전제를 강제하는 장치가 코드·설정 어디에도 없다 — application.yml 기본값이 true, @ConditionalOnProperty가 matchIfMissing=true, .env.example도 true다. 배포자가 환경변수를 잊으면 cron이 켜진 채 뜬다"
    why_human: "기본값을 바꾸는 것은 운영 정책 결정이다(켜져 있어야 하는 시점을 아는 사람은 소유자다). 코드 결함이 아니라 '문서로만 존재하는 안전 전제'라 자동 판정 대상이 아니다"
---

# Phase 5: 배치 검증 보고서 (재검증)

**Phase 목표:** 이용권을 오래 쓰지 않은 회원이 정책대로 자동 차감되고, 유효기간이 지난 이용권은 사용 불가 처리되며, 배치가 며칠씩 중복 실행돼도 이중 차감이 없다.
**검증 시각:** 2026-08-16T07:41:32Z
**상태:** passed (4/4)
**재검증 여부:** **예** — 2026-08-15 판정(gaps_found, 1/4) 이후 갭 클로저 청크 D(05-10~05-16)를 대상으로 다시 판정했다.

## 이전 판정에서 무엇이 바뀌었나

| Truth | 2026-08-15 | 2026-08-16 | 뒤집은 근거 |
|---|---|---|---|
| BATCH-01 | ✗ FAILED | ✓ VERIFIED | 정책 시행일 하한(`coerceAtLeast`)·1회 실행 상한(`minOf`)이 실제 코드에 들어왔고, 두 값을 덮어쓴 `@SpringBootTest(properties=…)` 테스트가 소급 차감 없음을 실증 |
| BATCH-02 | ✗ FAILED | ✓ VERIFIED | `changeStatus`의 기록 조건이 `newStatus != ON_LEAVE`로 확장됐고, `ON_LEAVE→INACTIVE→ACTIVE` 우회 경로를 대조군과 함께 돌리는 통합테스트가 소급 차감 0을 실증 |
| BATCH-03 | ✓ VERIFIED | ✓ VERIFIED (회귀 없음) | `InactivityBatchExpiryVerificationTest` 6건이 이번 실행에서도 전부 통과 |
| BATCH-04 | ✗ FAILED | ✓ VERIFIED | V10 `RUNNING` 부분 유니크 인덱스 + `BatchExecutionRecorder`(REQUIRES_NEW) + 409 거부가 실제로 존재하고, 러너 레벨·HTTP 레벨 동시성 테스트 2종이 실제 PostgreSQL에서 총 차감 1회를 단언 |

이전 판정이 지적한 **근본 원인 3갈래**(동시성 / 소급 차감 무방비 / 기준일 완전성)는 두 갈래가
코드로 닫혔고, 세 번째의 CR-03 부분만 Phase 6으로 이월됐다(아래 "이월 항목").

## 검증 방법 (SUMMARY.md를 근거로 쓰지 않았다)

이 판정의 근거는 세 가지다. SUMMARY.md의 서술은 **어디를 볼지 찾는 색인으로만** 썼다.

1. **코드 직접 열람** — V10 마이그레이션, `batch/` 13개 파일, `InactivityBatchProperties`,
   `BatchExecutorConfig`, `AdminMemberService.changeStatus`, `PassRepository`의 배치 쿼리,
   `application.yml`·`build.gradle.kts`의 프로퍼티 고정.
2. **검증자가 직접 실행한 테스트** — `./gradlew cleanTest test --tests 'com.goldwrestling.batch.*'
   --tests 'com.goldwrestling.member.*'` → **299 tests / 0 failures / 0 errors, BUILD SUCCESSFUL.**
   (`cleanTest`를 붙여 Gradle `UP-TO-DATE` 캐시 재사용을 배제했다 — 05-GAP-CONTEXT §3의 요구.)
   결과는 `build/test-results/test/*.xml`에서 클래스별로 확인했다.
3. **오케스트레이터의 로컬 실기동 관찰**(05-16 Task 2) — 실제 앱·실제 DB. 자동 테스트가 다루지
   못하는 "V10이 기존 데이터 위에서 적용되는가", "동시 POST 20건 뒤 원장이 그대로인가"를 채운다.

## 목표 달성 여부 (Observable Truths)

ROADMAP Phase 5 Success Criteria 4개 = REQUIREMENTS BATCH-01~04. 축소 없이 4개 전부 판정했다.

| # | Truth | 상태 | 근거 |
|---|---|---|---|
| 1 | BATCH-01: 기준일 기준 2주 미사용이면 1회 차감, 이후 2주마다 반복, `INACTIVITY` 이력 | ✓ VERIFIED | 아래 §BATCH-01 |
| 2 | BATCH-02: `ON_LEAVE` 기간·잔여 0·만료 이용권은 차감 대상 제외 | ✓ VERIFIED | 아래 §BATCH-02 |
| 3 | BATCH-03: 등록일+1년 만료 이용권은 예약 불가 + 배치 제외 | ✓ VERIFIED | 아래 §BATCH-03 |
| 4 | BATCH-04: 같은 날 중복 실행돼도 이중 차감 0건(멱등) | ✓ VERIFIED | 아래 §BATCH-04 |

**점수: 4/4 truths verified**

### BATCH-01 — 정확한 주기 차감 ✓ VERIFIED

**이전 실패 근거 (a) 상한·시행일 하한 부재 → 닫혔다.**

- `InactivityDueDateCalculator.resolveDueDate(candidates, policyEffectiveDate)` 72행:
  `.maxOrNull()?.coerceAtLeast(policyEffectiveDate)` — 후보 max가 시행일보다 이르면 시행일로
  끌어올린다. **`maxOrNull()` 뒤에** 하한을 거는 순서까지 맞다(후보 목록에 시행일을 끼워 넣었다면
  "후보 전부 null이면 판정 대상 아님"이 깨져 신규 등록 회원까지 차감 대상이 됐을 것이다).
- `InactivityBatchRunner.runStarted` 177행: `minOf(shortfallCount, properties.maxDeductionsPerRun)`
  — 1회 실행에서 회원 1명당 차감 상한. 178~188행에서 잘린 경우 `skippedCount`를 **올리지 않고**
  로그만 남긴다(집계 오독 방지, D-113 취지 유지).
- 두 값은 `InactivityBatchProperties`(`goldwrestling.batch.inactivity`)의 설정값이다 —
  `policyEffectiveDate` 기본 `2026-09-01`, `maxDeductionsPerRun` 기본 `1`. `application.yml`
  85~91행이 환경변수 오버라이드(`BATCH_INACTIVITY_*`)를 연결하고 `.env.example` 50·52행에 키가 있다.
- **기본 시행일이 오늘(2026-08-16)보다 미래**라, 지금 배포해도 첫 차감이 2026-09-15 이후에나
  가능하다 — 배포 즉시 소급 차감이 구조적으로 불가능하다.

**실증 (검증자가 직접 돌린 결과):**

| 테스트 | 무엇을 고정하는가 |
|---|---|
| `InactivityBatchPolicyLimitTest` (5건, `policy-effective-date=2026-08-01` 오버라이드) | 200일 방치 회원도 1회 실행에서 1회만 차감 / 상한으로 잘린 부족분은 skip으로 세지 않음 / 하루 뒤 재실행 시 또 1회 / 시행일 13일 전이면 0회·15일 전이면 1회 |
| `InactivityBatchDeductionLimitOverrideTest` (2건, `max-deductions-per-run=3`) | 상한이 코드 상수가 아니라 설정값임을 실증(3으로 올리면 3회 차감) |
| `InactivityBatchIdempotencyTest` (7건) | 6주 밀리면 실행마다 1회씩 이어받아 결국 3회 차감 — 상한이 캐치업 **속도**만 늦추고 **총량**은 바꾸지 않음 |
| `InactivityDueDateCalculatorTest` | 주기·부족분 순수 계산 |

**테스트 자체가 무의미해지지 않도록 한 조치까지 확인했다:** `build.gradle.kts` 114행이 테스트
전역 `policy-effective-date=2000-01-01`을 고정한다. 이게 없으면 프로덕션 기본값(2026-09-01)이
배치 테스트의 고정 시각(`BatchFixtures.FIXED_TODAY` = 2026-08-02)보다 미래라 **모든 배치 테스트가
"아무것도 차감하지 않음"을 검증하는 빈 껍데기가 되면서도 초록불로 통과**한다. 하한 자체를
검증하는 두 클래스만 `@SpringBootTest(properties = …)`로 덮어쓴다 — 올바른 구조다.

**CR-03(기준일 후보 ① 부재)은 이번 판정의 실패 근거로 쓰지 않았다** — 이월 항목 참조.

### BATCH-02 — 차감 예외 준수 ✓ VERIFIED

**이전 실패 근거 (휴회 우회 전이에서 복귀 시각 미기록) → 닫혔다.**

- `AdminMemberService.changeStatus` 조건이
  `previousStatus == ON_LEAVE && newStatus != ON_LEAVE`로 확장됐다(이전엔 `newStatus == ACTIVE`).
  `ON_LEAVE→INACTIVE` 시점에 `returnedFromLeaveAt`이 채워지므로 이후 `INACTIVE→ACTIVE`에서도
  기준일이 살아 있다.
- 현재 상태 기준 제외 3종은 여전히 **단일 쿼리 필터**로만 구현된다
  (`PassRepository.findMemberIdsWithDeductibleSessionPass`: `endDate >= :today`,
  `remainingCount > 0`, `member.status <> ON_LEAVE`) — 휴회 일수를 경과일에서 빼는 세 번째
  메커니즘이 추가되지 않았음을 확인했다(RESEARCH Pitfall 2 유지).
- 문서도 함께 정정됐다: policies.md §4.3 기준일 후보 ③이 "`ON_LEAVE`에서 **벗어난 시각**(어떤
  상태로 나가든)"으로 바뀌었고 CR-04를 명시한다. V9 주석은 커밋 후 수정 금지라 V10 (3)번 주석에
  정정 사실을 남겼다 — conventions §9 준수.

**실증:** `InactivityLeaveReturnTest` 3건(검증자 실행, 통과). 단위 테스트가 아니라
`adminMemberService.changeStatus` → 실제 배치 `run()` → 원장 확인의 **end-to-end**다:

- `ON_LEAVE→INACTIVE→ACTIVE` 우회 복귀 후 13일: 잔여 5.0 그대로. **같은 실행의 대조군**(휴회를
  거치지 않고 200일 방치)은 차감됨 — 즉 "아무것도 차감되지 않는 실행이라 통과한 것"이 아니다.
- 우회 복귀 후 15일: `deductedCount == 1`, 잔여 4.0 — 휴회 이전 200일이 소급되지 않았다.

### BATCH-03 — 만료 이용권 사용 불가 ✓ VERIFIED (회귀 없음)

신규 구현물 없이 D-064 조회 시점 계산 + Phase 4 예약 거부 경로로 충족한다는 설계(D-107)를
`InactivityBatchExpiryVerificationTest` **6건이 이번 실행에서도 전부 통과**하며 실증한다
(종료일 경계 2 · 예약 거부 2 · 배치 대상 제외 1 · 기간 수정 후 복귀 1). 갭 클로저가 이 영역을
건드리지 않았고 회귀도 없다.

### BATCH-04 — 멱등(동시 실행 포함) ✓ VERIFIED

**이전 실패 근거(`run()`을 직렬화하는 장치가 전혀 없음) → 닫혔다.** 실제 존재를 확인한 장치:

1. **DB 제약** — `V10__allow_running_batch_execution.sql`:
   `ALTER TABLE batch_execution ALTER COLUMN finished_at DROP NOT NULL;` +
   `CREATE UNIQUE INDEX uq_batch_execution_running ON batch_execution (status) WHERE status = 'RUNNING';`
   실행 중 행이 동시에 2건이 될 수 없음을 DB가 물리적으로 보장한다(조회 후 판정 방식에 남는
   경쟁 창이 없다 — D-021).
2. **시작/확정 분리** — `BatchExecutionRecorder`가 **별도 스프링 빈**이고
   `@Transactional(propagation = REQUIRES_NEW)`다. 자기 트랜잭션을 즉시 커밋해야 다른 커넥션이
   `RUNNING` 행을 보고 409를 받는다. `saveAndFlush`를 써서 유니크 위반이 메서드 **안**에서
   터지게 하고, 잡은 뒤 **정상 반환하지 않고 예외를 던진다**(rollback-only 트랜잭션을 커밋해
   500이 되는 사고 회피). 러너는 여전히 `@Transactional` 없음 — 실패 격리(D-112) 유지.
3. **어떤 종료 경로에서도 확정** — `runStarted`의 `try/catch`가 전체 실패 시 `FAILED`로 확정하고
   원인 예외를 재전파한다(WR-02 동시 해소). 확정 실패 시에도 원인 예외를 가리지 않는다.
4. **HTTP 거부 경로** — `BatchAlreadyRunningException` → `ErrorCode.BATCH_ALREADY_RUNNING`
   (`HttpStatus.CONFLICT`, ErrorCode.kt:141) → `GlobalExceptionHandler` → RFC 9457 `ProblemDetail`.
5. **WR-05 트리거 제거** — `AdminBatchService.launchInactivityRun`이 `RUNNING` INSERT만 동기로
   하고 본문은 전용 실행기(`inactivityBatchExecutor`, core/max 1, queue 1, `defaultCandidate=false`)에
   넘긴 뒤 202를 즉시 반환한다. 타임아웃→재시도로 이중 실행을 유발하던 동기 호출이 사라졌다.

**실증 (검증자가 직접 돌린 결과, 실제 PostgreSQL/Testcontainers):**

| 테스트 | 단언 |
|---|---|
| `InactivityBatchRunConcurrencyTest` (2건) | ① `RUNNING` 행을 미리 심으면 `run()`이 반드시 거부되고 잔여·이력 불변 (결정론 — 직렬화 장치가 사라지면 여기서 먼저 깨진다) ② 네 스레드 동시 `run()` → **`INACTIVITY` 이력 정확히 1건, 잔여 3.0→2.0, 실행 이력들의 `deductedCount` 합 1, `RUNNING` 잔존 0, 실패는 전부 `BatchAlreadyRunningException`** |
| `AdminBatchRunConcurrencyTest` (5건) | 동시 POST 2건 → 202 정확히 1 / 409 정확히 1 · 동시 4건 → 202 1 / 409 3 · 순차 재호출도 409 + `code=BATCH_ALREADY_RUNNING` · 409 본문에 제약조건명·SQL·`Exception` 문자열 없음 · **202 시점에는 아직 잔여가 줄지 않았다**(동기 실행 회귀 감지) |
| `InactivityBatchIdempotencyTest` (7건) | 순차 멱등(2회·5회 실행 시 이력 1건), SCHEDULED·MANUAL 혼재 시 총 1건 |

이전 판정이 "코드베이스 스스로 이중 차감을 증명한다"고 지목한 `InactivityDeductionConcurrencyTest`
(세 스레드 동시 차감 → 정확히 두 번 성공)는 그대로 남아 있고 여전히 통과한다. 그 계약은
**`deductOnce` 한 단계**의 계약이라 바뀌지 않았고, 뒤집힌 것은 **러너 레벨**의 계약이다 —
05-GAP-CONTEXT §3이 요구한 "그 테스트가 여전히 맞는 계약인지 다시 판단"이 실제로 수행됐다.

**실기동 관찰(오케스트레이터, 실제 앱·DB)이 자동 테스트를 보완한다:** 동시 POST 10건×2회에서
매번 202 1건/409 9건, 총 20건 요청 이후에도 `pass_transaction` 35건·`reason='INACTIVITY'` 1건
**그대로**(실 DB에서 이중 차감 0건), 종료 후 `status='RUNNING'` 0건·`finished_at IS NULL` 0건.

## 필수 산출물 (Artifacts) — 존재·실질·배선·데이터흐름 4단계

| Artifact | 기대 역할 | 상태 | 근거 |
|---|---|---|---|
| `db/migration/V10__allow_running_batch_execution.sql` | `finished_at` 완화 + `RUNNING` 부분 유니크 인덱스 | ✓ VERIFIED | 33행. V1~V9 미수정 확인. 실기동 로그 `Successfully validated 10 migrations` |
| `batch/BatchExecutionRecorder.kt` | 시작·확정 기록(REQUIRES_NEW) | ✓ VERIFIED | 145행. `saveAndFlush` + `DataIntegrityViolationException`→409 변환. 러너·AdminBatchService 두 곳에서 사용 |
| `batch/InactivityBatchRunner.kt` | 오케스트레이션 + 상한 적용 + FAILED 확정 | ✓ VERIFIED | 303행. `start`/`runStarted`/`run` 3분할, `minOf(...)` 상한, `try/catch` 확정 |
| `batch/InactivityDueDateCalculator.kt` | 기준일 max + 시행일 하한 + 부족분 | ✓ VERIFIED | `resolveDueDate(candidates, policyEffectiveDate)` 시그니처 변경, 호출부(러너 165행)가 프로퍼티 주입 |
| `batch/AdminBatchService.kt` | 202 접수(동기 시작 + 비동기 본문) · 단건/목록 조회 | ✓ VERIFIED | 134행. `NOT_SUPPORTED` 전파, `RejectedExecutionException` 복구 경로 |
| `batch/AdminBatchController.kt` | 엔드포인트 3종 | ✓ VERIFIED | POST 202+`Location`, GET 단건, GET 목록(`@Min(1)@Max(100)`) |
| `batch/BatchExceptions.kt` | 409·404 도메인 예외 | ✓ VERIFIED | `BatchAlreadyRunningException`·`BatchExecutionNotFoundException`, 메시지에 id·제약조건명 미포함 |
| `batch/BatchExecution.kt` | 실행 이력 엔티티 | ✓ VERIFIED | `triggeredByAdminId` **스칼라** 전환(WR-04 해소), `finish()` 원자 확정 |
| `batch/BatchExecutionStatus.kt` | RUNNING/SUCCESS/PARTIAL_FAILURE/FAILED | ✓ VERIFIED | 4값 모두 존재 |
| `batch/BatchExecutionRepository.kt` | stale 정리·목록 조회 | ✓ VERIFIED | `markStaleRunningAsFailed`(`@Modifying(flush/clearAutomatically)`), `findAllByOrderByStartedAtDesc(Pageable)` |
| `batch/InactivityBatchScheduler.kt` | cron 트리거 + 킬 스위치 | ✓ VERIFIED | `@ConditionalOnProperty(...inactivity-scheduler-enabled)`, 409를 info로 흡수 |
| `config/InactivityBatchProperties.kt` | 정책값 3종 | ✓ VERIFIED | 기본 `2026-09-01`/`1`/`30m`. 킬 스위치 키를 이 prefix로 옮기지 않은 이유까지 KDoc에 있음 |
| `config/BatchExecutorConfig.kt` | 전용 단일 스레드 실행기 | ✓ VERIFIED | `defaultCandidate = false`로 부트 기본 실행기 밀어내지 않음 |
| `member/AdminMemberService.kt` | 휴회 이탈 시각 기록 | ✓ VERIFIED | 조건 `newStatus != ON_LEAVE`로 확장 |
| `docs/api/openapi.yaml` | FE 계약 | ✓ VERIFIED | 엔드포인트 3개 등록, 202+`Location`+409 명세, 이전의 "동시 실행은 아직 안전하지 않다"·"재호출하지 않는다" 서술 **삭제·재작성** |
| `docs/policies.md` §4.3 | 도메인 규칙 기록 | ✓ VERIFIED | 시행일 하한·1회 실행 상한·휴회 이탈 기록이 정책 문서에 명시 |
| `docs/decisions.md` | D-117·D-118·D-119 신규, D-108·D-114 정정 | ✓ VERIFIED | D-108에 "해소(2026-08-16)" 및 분산 락 불필요 **근거 교체**, D-114에 "새 에러코드 미추가" 철회 명시 |
| `build.gradle.kts` | 테스트 프로퍼티 고정 | ✓ VERIFIED | 107행 스케줄러 off, 114행 시행일 2000-01-01(빈 껍데기 테스트 방지) |

전 항목이 존재(L1)·실질(L2)·배선(L3)을 통과했고, 데이터 흐름(L4)은 아래에서 별도로 본다.

## 핵심 연결(Key Link) 검증

| From | To | Via | 상태 |
|---|---|---|---|
| `InactivityBatchScheduler.runDaily` | `InactivityBatchRunner.run(SCHEDULED, null)` | cron 6필드 `0 0 4 * * *` + `@ConditionalOnProperty` | ✓ WIRED |
| `AdminBatchController.runInactivityBatch` | `AdminBatchService.launchInactivityRun` | `principal.requireAdminId()` | ✓ WIRED |
| `AdminBatchService.launchInactivityRun` | `BatchExecutionRecorder.start` (동기) | `runner.start()` | ✓ WIRED — 동시 요청 즉시 409 |
| `AdminBatchService.launchInactivityRun` | `InactivityBatchRunner.runStarted` (비동기) | `@Qualifier("inactivityBatchExecutor") TaskExecutor.execute` | ✓ WIRED — 실기동에서 SUCCESS·`processedMemberCount=3` 관찰 |
| `BatchExecutionRecorder.start` | `uq_batch_execution_running` (V10) | `saveAndFlush` → `DataIntegrityViolationException` → 409 | ✓ WIRED |
| `InactivityBatchRunner` | `InactivityDueDateCalculator.resolveDueDate` | `properties.policyEffectiveDate` 전달 | ✓ WIRED — 하한이 실제로 계산에 도달 |
| `InactivityBatchRunner` | `InactivityDeductionService.deductOnce` | `minOf(shortfall, maxDeductionsPerRun)` 루프 | ✓ WIRED — 상한이 실제로 루프를 제한 |
| `AdminMemberService.changeStatus` | `Member.returnedFromLeaveAt` | `previousStatus==ON_LEAVE && newStatus!=ON_LEAVE` | ✓ WIRED — 우회 경로 커버 |
| `AdminBatchController` GET 2종 | `BatchExecutionRepository` | `findById` / `findAllByOrderByStartedAtDesc(PageRequest)` | ✓ WIRED |

## 데이터 흐름 추적 (Level 4)

이 phase의 최종 산출물은 화면 렌더링이 아니라 **DB 상태 변경**이다.

| 대상 | 데이터 원천 | 실제 데이터가 흐르는가 | 상태 |
|---|---|---|---|
| 차감(잔여 횟수) | `PassRepository.adjustRemainingCount` 조건부 UPDATE | 통합테스트에서 3.0→2.0, 5.0→4.0 실측. 실기동에서 이전 실행 차감 1건 존재 | ✓ FLOWING |
| 이력(`PassTransaction` `INACTIVITY`) | `InactivityDeductionService.deductOnce` | 동시성 테스트가 원장 건수를 직접 카운트(성공 반환값이 아니라 DB를 본다) | ✓ FLOWING |
| 실행 이력(`batch_execution`) | `BatchExecutionRecorder` start/finish | 실기동에서 SUCCESS 7건·RUNNING 0건·`finished_at IS NULL` 0건 | ✓ FLOWING |
| 조회 API 응답 | `BatchExecutionResponse.from(엔티티)` | 스칼라 전용 엔티티라 LAZY 프록시 없음. 실기동 폴링에서 SUCCESS·`finishedAt` 채워짐 확인 | ✓ FLOWING |

정적 값·하드코딩 응답(`return json([])` 류)은 배치 경로 어디에도 없다.

## 행동 스팟체크 / 프로브

이 저장소에는 `scripts/*/tests/probe-*.sh` 규약이 없다(`find scripts -path '*/tests/probe-*.sh'`
결과 없음). PLAN·SUMMARY도 프로브를 선언하지 않는다 — 프로브 실행은 **SKIPPED(해당 없음)**이며,
그 자리를 아래 두 가지가 대신한다.

| 체크 | 명령 | 결과 | 상태 |
|---|---|---|---|
| 배치·회원 도메인 회귀 | `./gradlew cleanTest test --tests 'com.goldwrestling.batch.*' --tests 'com.goldwrestling.member.*'` | **299 tests / 0 failures / 0 errors**, BUILD SUCCESSFUL (exit 0) | ✓ PASS |
| 동시성 계약 2종 | 위 실행 중 `InactivityBatchRunConcurrencyTest`(2) · `AdminBatchRunConcurrencyTest`(5) | 7건 전부 `failures="0" errors="0"` | ✓ PASS |
| 정책 상한·하한 | `InactivityBatchPolicyLimitTest`(5) · `InactivityBatchDeductionLimitOverrideTest`(2) | 7건 전부 통과 | ✓ PASS |
| 휴회 우회 경로 | `InactivityLeaveReturnTest`(3) | 3건 전부 통과 | ✓ PASS |
| 만료 처리 회귀 | `InactivityBatchExpiryVerificationTest`(6) | 6건 전부 통과 | ✓ PASS |
| 실기동(앱+DB) | 오케스트레이터 05-16 Task 2 | 202/409·V10 적용·이중 차감 0건·400 검증 | ✓ PASS (관찰) |

전체 스위트(767건)·`./gradlew build`는 오케스트레이터가 보고한 값이며, 검증자는 그 중
배치·회원 범위 299건을 **직접 재실행해** 무결함을 확인했다.

## 요구사항 커버리지

| 요구사항 | 근거 플랜 | 상태 | 근거 |
|---|---|---|---|
| BATCH-01 | 05-01,03,04,05,06,08,09,**14** | ✓ SATISFIED | 시행일 하한 + 1회 실행 상한(코드 + 7건 테스트). CR-03은 Phase 6 이월 |
| BATCH-02 | 05-04,05,06,07,**10** | ✓ SATISFIED | 쿼리 필터 3종 + 휴회 이탈 전이 전체 기록(3건 end-to-end 테스트) |
| BATCH-03 | 05-01,07 | ✓ SATISFIED | `InactivityBatchExpiryVerificationTest` 6건, 회귀 없음 |
| BATCH-04 | 05-02,03,06,07,08,**11,12,13,15** | ✓ SATISFIED | V10 유니크 인덱스 + REQUIRES_NEW 기록자 + 409 + 202 비동기, 동시성 테스트 7건 + 실기동 |

REQUIREMENTS.md가 Phase 5에 매핑한 ID는 BATCH-01~04뿐이다 — **orphaned requirement 없음.**
REQUIREMENTS.md 140~143행의 `Complete` 표기는 이번 판정과 일치한다(이전 검증이 지적한 불일치 해소).
ROADMAP 21행의 Phase 5 체크박스는 아직 `[ ]`, 05-16-PLAN도 `[ ]`다 — 이 마감 처리는 오케스트레이터
몫이며 검증 대상이 아니다.

## 안티패턴 스캔

이번 phase가 만지는 파일 전체(`batch/` 13개, `config/` 2개, `AdminMemberService.kt`, V10)에
`TODO`·`FIXME`·`XXX`·`TBD`·`HACK`·`PLACEHOLDER` **0건** — debt-marker 게이트 미발동.

이전 판정의 안티패턴 3건은 전부 해소됐다:

| 이전 지적 | 현재 |
|---|---|
| D-108·D-114가 "중복 실행 안전"을 사실과 다르게 단언 (🛑 Blocker) | ✓ 해소 — D-108에 "해소(2026-08-16)" 절 추가, 분산 락 불필요의 **근거 자체를 교체**("인스턴스가 하나여서"→"DB 제약이 인스턴스 수와 무관하게 막아서"). D-114는 "새 에러코드 미추가"를 명시적으로 **철회** |
| openapi.yaml의 잘못된 안전성 서술 (⚠️ Warning) | ✓ 해소 — 재생성된 명세에 "동시 실행은 아직 안전하지 않다"·"재호출하지 않는다" 문구 없음, 409 응답이 명세됨 |
| `lastAttendanceDate = null` 하드코딩 + cron 활성 (⚠️ Warning) | ⚠️ **부분 해소** — 하드코딩 null은 여전하나 D-105/CR-03의 의도된 동작으로 문서화됐고, 킬 스위치(D-116)와 시행일 하한(2026-09-01)이 완충한다. 아래 잔여 위험 참조 |

### 잔여 위험 (신규 발견 — 차단은 아니나 기록한다)

1. **⚠️ "운영 배포 시 cron off"라는 안전 전제를 강제하는 장치가 없다.**
   `application.yml:83`이 `${BATCH_INACTIVITY_SCHEDULER_ENABLED:true}`, `@ConditionalOnProperty`가
   `matchIfMissing = true`, **`.env.example:48`도 `=true`**다. CR-03이 열려 있는 동안의 대응책이
   `docs/`·`.planning/`의 산문에만 존재하고, 배포 워크플로(`.github/workflows/`)에는 배포 잡 자체가
   아직 없다. 배포자가 환경변수를 잊으면 2026-09-15부터 저녁반 전용 회원이 2주마다 부당 차감된다.
   → 사람 결정 항목으로 올렸다(frontmatter `human_verification` 2번). 권고: CR-03이 닫힐 때까지
   `.env.example` 값을 `false`로 두는 것이 문서와 설정의 어긋남을 없앤다.
2. **ℹ️ stale 회수 임계(기본 30분)는 이론적으로 CR-01 창을 되연다.** 배치가 30분을 넘겨 도는데
   다른 실행이 들어오면, 뒤 실행이 앞 실행의 `RUNNING` 행을 `FAILED(STALE)`로 회수하고 새로
   시작해 두 본문이 겹칠 수 있다. 실제 실행 시간이 수초~수분이라 현실적 위험은 낮고,
   D-117·`InactivityBatchProperties` KDoc이 "이 설계의 알려진 약점"으로 이미 명시하고 있으며
   임계는 설정값이라 조정 가능하다. **차단 사유로 보지 않는다.**
3. **ℹ️ `InactivityBatchProperties`에 값 검증(`@Min` 등)이 없다.** `max-deductions-per-run`에
   음수·0을 넣으면 조용히 차감이 0이 된다(안전한 방향의 실패라 위험도는 낮다).

## 이월 항목 (Deferred — 이번 phase의 갭이 아니다)

**CR-03: 기준일 후보 ①(마지막 출석일) 부재 → Phase 6이 닫는다.**

- ROADMAP Phase 5 Note와 05-GAP-CONTEXT.md §1이 "Phase 6까지 자연히 부재로 동작한다(의도된 동작)"로
  문서화한 설계 결정이며, 사용자가 이번 갭 클로저 범위에서 제외하기로 확인했다. 따라서
  **BATCH-01 실패의 근거로 쓰지 않았다.**
- Phase 6 Goal("관리자가 모든 수업의 출석을 체크하고…")이 `Attendance` 도입을 포함하므로,
  후보 ①을 채우는 작업은 그 phase의 범위에 명시적으로 들어 있다.
- **그동안 운영 배포는 `BATCH_INACTIVITY_SCHEDULER_ENABLED=false`로 cron을 꺼 둔 채 한다(D-116·D-119).**
- **이 조건이 지켜지지 않으면 저녁반 전용 회원이 2주마다 부당 차감된다** — 저녁반 참여는
  `EVENING_HALF` 수동 차감으로만 남고 기준일 후보 어디에도 반영되지 않아, 활동 중인 회원이
  "미사용"으로 판정된다(저녁반 0.5 + 미사용 1.0 = 2주에 1.5회). 위 "잔여 위험 1"이 바로 이
  조건을 강제하는 장치가 없다는 지적이다.
- `InactivityDueDateCandidates.lastAttendanceDate` 필드가 이미 값 객체에 자리를 잡고 있어,
  Phase 6은 러너 156행의 `null`을 실제 조회로 바꾸기만 하면 된다.

**WR-06(INACTIVE 회원 지속 차감):** 이전 검증의 `human_verification` 항목이었고 아직 사용자
판단이 없다. 상태를 **그대로 이월**하며 새로 실패 판정하지 않는다.

## 판정

**passed — 4/4.** 이전 판정이 실패로 본 truth 3개가 코드·테스트·실기동 세 층위 모두에서 뒤집혔고,
통과했던 BATCH-03에 회귀가 없다. 이전 판정의 blocker(문서-구현 불일치)도 해소됐다.

> **status 값에 관한 주석:** GSD 기본 규칙은 `human_verification` 항목이 하나라도 있으면
> `human_needed`로 분류한다. 이번에는 오케스트레이터가 `passed | gaps_found` 두 값으로 제한했고,
> 남은 두 항목이 **truth 판정을 좌우하지 않는 후속 운영 결정**(하나는 이전 판정에서 그대로 이월된
> 것)이므로 `passed`로 두되 항목은 frontmatter에 그대로 남겼다. 다음 작업자가 이 두 결정을
> 놓치지 않도록 하기 위함이다.

**cron 활성 배포(dev→main 병합)에 대한 권고는 유지된다** — CR-03이 열려 있는 한 cron을 켠 채
프로덕션에 올리지 않는다. 이것은 Phase 5의 결함이 아니라 Phase 6과의 순서 문제다.

---

_검증: 2026-08-16T07:41:32Z_
_검증자: Claude (gsd-verifier) — 재검증_
