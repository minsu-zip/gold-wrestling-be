---
phase: 05-batch
plan: 07
subsystem: testing
tags: [kotlin, spring-boot, jpa, postgresql, testcontainers, batch, idempotency]

# Dependency graph
requires:
  - phase: 05-batch (05-06)
    provides: "InactivityBatchRunner.run(trigger, triggeredByAdminId): BatchExecution — 이 플랜이 반복 호출해 멱등·캐치업을 실증"
  - phase: 05-batch (05-04)
    provides: "PassRepository.findMemberIdsWithDeductibleSessionPass — 만료 이용권이 배치 대상에서 실제로 빠지는지 이 플랜이 직접 검증"
provides:
  - "InactivityBatchIdempotencyTest — BATCH-04(멱등)를 같은 날 반복 실행·캐치업·유예 리셋·트리거 혼용 7종으로 실제 PostgreSQL에서 실증"
  - "InactivityBatchExpiryVerificationTest — BATCH-03이 D-107(구현물 없음) 전제대로 기존 메커니즘(D-064 표시 상태·Phase 4 예약 거부·배치 대상 제외)만으로 충족됨을 실증"
affects: [05-08, 05-09]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "멱등성 테스트는 클래스 레벨 트랜잭션 애노테이션을 배제하고 매 run() 호출이 실제 커밋되게 한다 — 두 번째 실행이 첫 실행의 이력을 봐야 캐치업/멱등을 실제로 관측할 수 있다(05-06 InactivityBatchRunnerTest 선례 재사용)"
    - "픽스처가 프로덕션 서비스를 거치지 않고 잔여를 직접 세팅할 때는 INITIAL_GRANT 이력을 함께 만들어 '잔여 = 이력 합계' 불변식의 기준선을 세운다"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchIdempotencyTest.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchExpiryVerificationTest.kt
  modified: []

key-decisions:
  - "ADMIN_ADJUST 픽스처는 호출부가 들고 있던 Pass 참조가 아니라 DB에서 재조회한 값 위에 가감한다 — 배치 차감(조건부 UPDATE)이 준영속 참조를 낡게 만들어, 재조회 없이 가감하면 이미 반영된 배치 차감분이 되살아나 잔여가 부풀려진다"
  - "만료 검증 테스트의 @AfterEach 정리 순서에 pass_period_change 삭제를 pass 삭제보다 앞에 추가 — AdminPassService.changePeriod가 남기는 이력(D-057)이 FK로 남아 있으면 pass 삭제가 실패한다"
  - "BATCH-02 대상 제외 축은 InactivityBatchRunner.run() 전체를 실행해 processedMemberCount·deductedCount로 검증한다 — PassRepository 쿼리만 직접 부르지 않고 실제 배치 경로 전체가 만료 이용권을 걸러내는지까지 실증한다"

patterns-established: []

requirements-completed: [BATCH-03, BATCH-04]

# Metrics
duration: ~40min
completed: 2026-08-15
---

# Phase 5 Plan 7: 배치 멱등·만료 실증 테스트 Summary

**BATCH-04(멱등)를 7종 시나리오로, BATCH-03(만료 사용 불가, D-107 구현물 없음)을 4축(표시 상태·예약 거부·배치 제외·기간 수정 되살아남)으로 실제 PostgreSQL 위에서 실증 — 프로덕션 코드 변경 0줄**

## Performance

- **Duration:** ~40분
- **Tasks:** 2
- **Files modified:** 2 (신규 2, 수정 0)

## Accomplishments
- `InactivityBatchIdempotencyTest` 7종: 같은 날 2회·5회 반복 실행 시 이중 차감 없음, 6주 밀린 첫 실행의 캐치업(3회 몰아서 차감), 14일 뒤 재실행 시 밀린 한 주기만 추가 차감, 캐치업 중 잔여 부분 소진 시 스킵 1건으로 멈추고 `status=SUCCESS`, 관리자 양(+) 가감 후 유예 리셋으로 추가 차감 0건(D-105 원리 실증), `SCHEDULED`/`MANUAL` 트리거가 같은 날 섞여도 총 차감 1건 — 모든 시나리오가 "잔여 = 이력 합계" 불변식(`sumAmountByPassId`)으로 끝난다
- `InactivityBatchExpiryVerificationTest` 6종: `displayStatus`가 종료일 경계(D-066)를 올바르게 판정(어제=EXPIRED, 오늘=USABLE), 만료된 `SESSION_PASS`만 가진 회원의 예약이 `InsufficientPassCountException`으로 거부되고 잔여·예약·이력이 요청 전과 동일, "오늘은 유효하지만 수업날에는 만료"되는 이용권으로도 예약이 거부됨(D-091 수업날 기준 우회 차단), `InactivityBatchRunner.run()`이 만료된 이용권 보유 회원을 `processedMemberCount=0`으로 완전히 걸러냄(BATCH-02 대상 제외 재확인), `AdminPassService.changePeriod`로 유효기간을 미래로 연장하면 `displayStatus`가 `USABLE`로 되돌아오고 `findMemberIdsWithDeductibleSessionPass`가 다시 그 회원을 포함(D-056 "상태를 저장하지 않는" 설계의 이점 — 되돌리기 로직 불필요)
- 두 파일 모두 **프로덕션 코드를 한 줄도 바꾸지 않았다** — `git diff --stat src/main/kotlin/` 두 태스크 모두 무변경으로 확인. `InactivityBatchExpiryVerificationTest` 클래스 KDoc에 "D-107"·"BATCH-03" 문자열이 함께 등장해 이 파일이 BATCH-03의 유일한 산출물임을 코드에도 남겼다
- 전체 스위트(`./gradlew build`) 회귀 없음, `./gradlew ktlintFormat` 변경 없음(포맷 위반 없이 작성)

## Task Commits

Each task was committed atomically:

1. **Task 1: 멱등성·캐치업 실증 테스트 (BATCH-04)** - `027844d` (test)
2. **Task 2: 만료 이용권 사용 불가 실증 테스트 (BATCH-03, 구현물 없음)** - `e28cd65` (test)

**Plan metadata:** (다음 커밋에서 기록)

## Files Created/Modified
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchIdempotencyTest.kt` - BATCH-04 멱등·캐치업 통합테스트 7종(신규)
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchExpiryVerificationTest.kt` - BATCH-03 만료 사용 불가 실증 통합테스트 6종(신규)

## Decisions Made

- **ADMIN_ADJUST 픽스처 재조회**: "차감 후 관리자 충전" 시나리오에서 호출부가 들고 있던 `Pass` Kotlin 객체 참조에 그대로 가감하면, `InactivityDeductionService.deductOnce`의 조건부 UPDATE가 DB를 이미 바꿔 놓았어도 그 변경이 메모리상 객체에는 반영되지 않은 상태라 낡은 값(가감 전 3.0) 위에 더해 버그가 났다(테스트 실행 중 `expected: 2.0 but was: 5.0`으로 즉시 드러났다). `passRepository.findById(pass.id!!).get()`으로 재조회한 뒤 가감하도록 고쳤다.
- **`pass_period_change` 정리 순서 추가**: `AdminPassService.changePeriod`를 호출하는 되살아남 테스트가 FK(`fk_pass_period_change_pass`)로 걸린 이력 행을 남기는데, `@AfterEach`가 `pass_transaction`만 먼저 지우고 `pass_period_change`는 지우지 않은 채 `pass`를 삭제하려다 `DataIntegrityViolationException`이 났다. `pass` 삭제 앞에 `pass_period_change` 삭제를 추가했다.
- **배치 대상 제외는 러너 전체 실행으로 검증**: BATCH-03의 세 번째 축(미사용 차감 대상 제외)을 `PassRepository` 쿼리만 직접 호출해서 확인할 수도 있었지만, `InactivityBatchRunner.run()`을 실제로 실행해 `processedMemberCount`·`deductedCount`까지 확인했다 — 쿼리 하나만 옳고 배치 조립 어딘가에서 그 결과를 무시하는 경우까지 잡기 위해서다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ADMIN_ADJUST 픽스처 헬퍼가 낡은 메모리 참조 위에 가감**
- **Found during:** Task 1 테스트 최초 실행("차감 후 관리자가 양의 가감을 하면...")
- **Issue:** `applyPositiveAdjustment` 헬퍼가 호출부가 들고 있던 `Pass` 참조(배치 차감 이전 값, 3.0)에 그대로 `+2.0`을 더해 저장 — 배치 차감(조건부 UPDATE, 별도 트랜잭션)이 이미 DB를 0으로 바꿔 놓았지만 이 참조는 그 변경을 모른다. 결과가 기대한 2.0이 아니라 5.0으로 나왔다
- **Fix:** `passRepository.findById(pass.id!!).get()`으로 최신 값을 재조회한 뒤 가감하도록 수정
- **Files modified:** `src/test/kotlin/com/goldwrestling/batch/InactivityBatchIdempotencyTest.kt`
- **Verification:** 재실행 시 7종 전부 통과
- **Committed in:** `027844d`

**2. [Rule 1 - Bug] 만료 검증 테스트 `@AfterEach`가 `pass_period_change`를 지우지 않아 FK 위반**
- **Found during:** Task 2 테스트 최초 실행("만료된 이용권도 유효기간을 미래로 수정하면..." 및 뒤이은 테스트)
- **Issue:** `AdminPassService.changePeriod` 호출이 남기는 `pass_period_change` 이력을 `@AfterEach`가 정리하지 않아 `pass` 삭제 시 `DataIntegrityViolationException`이 났다. 첫 실패로 정리가 중간에 멈추면서 다음 테스트의 정리까지 연쇄로 실패했다
- **Fix:** `pass` 삭제보다 앞서 `pass_period_change`를 지우는 삭제문 추가
- **Files modified:** `src/test/kotlin/com/goldwrestling/batch/InactivityBatchExpiryVerificationTest.kt`
- **Verification:** 재실행 시 6종 전부 통과
- **Committed in:** `e28cd65`

---

**Total deviations:** 2 auto-fixed (전부 Rule 1 — 테스트 코드 버그 수정, 프로덕션 도메인 로직 변경 없음)
**Impact on plan:** 계획서가 지시한 시나리오·검증 축·acceptance criteria는 그대로 구현했다. 편차는 둘 다 테스트 픽스처의 재조회 누락과 정리 순서 문제이며, D-106·D-107이 확정한 설계 자체는 손대지 않았다.

## Issues Encountered

None — 위 2건은 모두 Deviations 섹션에서 자동 수정으로 처리했다. Docker Desktop이 이미 기동돼 있어 Testcontainers 기반 통합테스트가 즉시 실행됐다.

## User Setup Required

None - 이 플랜은 테스트 파일만 추가했다. 환경변수·외부 서비스 설정이 필요 없다.

## Known Stubs

None — 스텁 패턴(하드코딩된 빈 값, 플레이스홀더 문자열, 데이터 소스 미배선) 없음. 이 플랜은 테스트 전용이며 UI 렌더링과 무관하다.

## Threat Flags

None — 이 플랜이 추가한 코드는 PLAN.md `<threat_model>`이 이미 다룬 표면(T-05-24~26)만 검증하며, 새 네트워크 엔드포인트·인증 경로·스키마 변경은 없다.

## Next Phase Readiness

- BATCH-04(멱등)와 BATCH-03(만료 사용 불가)이 이제 각각 자동 검증으로 뒷받침된다 — 이후 phase가 배치 로직을 건드릴 때 이 테스트들이 멱등성·만료 처리 회귀를 잡는다
- REQUIREMENTS.md는 이 플랜에서 건드리지 않았다 — BATCH-01~04의 Complete 전환은 05-09가 일괄 처리한다(project_rules 지시, 05-04~06 선례와 동일). 이 SUMMARY frontmatter의 `requirements-completed`는 이 플랜이 실제로 실증을 완료한 요구사항(BATCH-03·04)만 기록했다 — BATCH-02는 05-04에서 이미 구현·검증됐고 이 플랜의 배치 제외 테스트는 그 재확인이다
- 05-08(스케줄러·수동 실행 API)이 이 플랜의 결과를 그대로 신뢰하고 `InactivityBatchRunner.run`을 트리거만 하면 된다 — 멱등성이 이미 상태 기반(D-106)으로 보장되고 이 테스트로 실증됐으므로 05-08은 별도 중복 실행 방지 로직을 만들 필요가 없다
- Blocker 없음

---
*Phase: 05-batch*
*Completed: 2026-08-15*

## Self-Check: PASSED

- FOUND: src/test/kotlin/com/goldwrestling/batch/InactivityBatchIdempotencyTest.kt
- FOUND: src/test/kotlin/com/goldwrestling/batch/InactivityBatchExpiryVerificationTest.kt
- FOUND: .planning/phases/05-batch/05-07-SUMMARY.md
- FOUND commit: 027844d, e28cd65 (verified in `git log --oneline`)
