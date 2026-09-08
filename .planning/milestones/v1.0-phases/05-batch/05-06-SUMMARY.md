---
phase: 05-batch
plan: 06
subsystem: batch
tags: [kotlin, spring-boot, jpa, postgresql, testcontainers, tdd, batch-orchestration]

# Dependency graph
requires:
  - phase: 05-batch (05-03)
    provides: "InactivityDueDateCalculator.resolveDueDate/expectedDeductionCount/shortfall — 순수 계산, 이 플랜이 그대로 호출"
  - phase: 05-batch (05-04)
    provides: "벌크 조회 6종(대상 회원·기준일 후보 4종·INACTIVITY 이력) — 이 플랜이 memberId 기준 Map으로 조합"
  - phase: 05-batch (05-05)
    provides: "InactivityDeductionService.deductOnce(memberId): Boolean — 별도 빈의 회원 1명 트랜잭션 단위, 이 플랜이 부족분만큼 반복 호출"
provides:
  - "InactivityBatchRunner.run(trigger, triggeredByAdminId): BatchExecution — 조회→계산→차감→이력을 엮는 배치 오케스트레이션(트랜잭션 없는 루프)"
affects: [05-07, 05-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "배치 루프 상위 컴포넌트는 트랜잭션 애노테이션을 붙이지 않고, 회원 1명 반영은 별도 빈의 트랜잭션 메서드로 위임(D-112) — self-invocation 우회·전체 롤백을 피한다"
    - "실행 이력 저장은 계산 결과의 부산물로만(멱등성 판단에 쓰지 않는다, D-106) — 러너는 batchExecutionRepository.save만 호출하고 find/existsBy는 호출하지 않는다(acceptance grep으로 고정)"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt
  modified: []

key-decisions:
  - "KDoc의 '@Transactional' 리터럴이 acceptance grep(0건 기대)과 충돌해 '트랜잭션 애노테이션'으로 표현 변경 — 05-03·05-04의 동일 유형 충돌과 같은 해결(의미 유지, 리터럴만 회피)"
  - "부족분 루프는 RESEARCH의 repeat+return@repeat 예시 대신 for+break로 구현 — return@repeat은 repeat 람다의 현재 호출만 건너뛰고 전체 반복을 멈추지 않아 '대상 소진 시 즉시 중단, skippedCount 1 증가'라는 명시된 behavior를 만족하지 못한다"
  - "취소된 예약 픽스처는 ReservationRepository.cancelByMemberIfActive(커스텀 @Modifying 쿼리) 대신 엔티티 필드를 직접 대입한 뒤 saveAndFlush — 05-05의 동일 사유(명시적 트랜잭션 애노테이션 없는 클래스에서 커스텀 @Modifying 호출 시 flush 실패)"

patterns-established: []

requirements-completed: []

# Metrics
duration: ~35min
completed: 2026-08-15
---

# Phase 5 Plan 6: 배치 오케스트레이션 InactivityBatchRunner Summary

**벌크 조회(05-04) → 기준일·부족분 순수 계산(05-03) → 회원별 차감(05-05)을 엮는 `InactivityBatchRunner.run`을 TDD(RED→GREEN)로 구현 — 트랜잭션 없는 루프가 회원 300명 규모에서도 부분 실패를 흡수하며 `batch_execution` 이력 1건을 남긴다**

## Performance

- **Duration:** ~35분
- **Tasks:** 1 (TDD 1사이클)
- **Files modified:** 2 (신규 2, 수정 0)

## Accomplishments
- `InactivityBatchRunner.run(trigger, triggeredByAdminId): BatchExecution` 구현 — 대상 회원 벌크 조회(비어 있으면 벌크 조회 4종을 건너뛰고 0집계 이력 저장) → 기준일 후보 4종·`INACTIVITY` 이력을 `memberId` 기준 `Map`으로 조합 → 회원별 `InactivityDueDateCalculator`로 기준일·부족분 계산 → 부족분만큼 `InactivityDeductionService.deductOnce`(별도 빈)를 반복 호출
- 클래스·메서드 어디에도 트랜잭션 애노테이션을 붙이지 않음(05-01 결정 C, D-112) — `grep -c "@Transactional"` 0건으로 acceptance criteria 고정, 회원 1명 = 트랜잭션 1개는 `deductOnce`가 전담
- `batch_execution`을 저장(`save`)만 하고 조회하지 않음(D-106) — `grep -c "batchExecutionRepository.find|existsBy"` 0건으로 "실행 이력을 멱등성 판단에 쓰지 않는다"를 코드 수준에서 고정
- 대상 소진(`deductOnce` false 반환) 시 그 회원의 남은 부족분 루프를 `for`+`break`로 즉시 중단하고 `skippedCount`만 1 증가 — RESEARCH의 `repeat`+`return@repeat` 예시는 반복을 멈추지 못해 명시된 behavior(스킵 1건만 집계)를 만족하지 못하므로 다른 구현 선택
- 회원 단위 `try-catch`로 예외를 흡수해 `PARTIAL_FAILURE`·`errorSummary`(1000자 절단)에 집계 — 한 회원의 실패가 나머지 회원 처리를 막지 않음
- `OffsetDateTime → LocalDate` 변환은 `SEOUL_ZONE_ID` 기준(`GoldWrestlingApplication.kt` 상수 재사용, 새 상수 생성 없음)
- `MANUAL` 트리거는 관리자를 조회해 `triggeredBy`를 채우고 `SCHEDULED`는 항상 `null`(`ck_batch_execution_trigger`, D-113)
- 통합테스트 15종(BATCH-01 캐치업·경계, BATCH-02 예외 3종, D-105 기준일 후보 조합 4종, 집계·회당 재선택·소진, 대상 0명·트리거 2종, 시각 정합성) 전부 통과, `./gradlew ktlintFormat` → `./gradlew build`(전체 스위트) 그린, 회귀 없음

## Task Commits

TDD 1사이클(계획서 지시대로 RED→GREEN 커밋 분리):

1. **RED** — `291d0d4` (test): `InactivityBatchRunnerTest` 15종 작성, 대상 클래스(`InactivityBatchRunner`) 없어 컴파일 실패 확인
2. **GREEN** — `cb518df` (feat): `InactivityBatchRunner` 구현, 15종 전부 통과

**Plan metadata:** (다음 커밋에서 기록)

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt` - 배치 오케스트레이션(조회→계산→차감→이력), 트랜잭션 애노테이션 없는 회원별 루프(신규)
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt` - BATCH-01/02 도메인 15종 통합테스트(신규)

## Decisions Made

- **`for`+`break` vs `repeat`+`return@repeat`**: PATTERNS.md가 인용한 RESEARCH 코드 예시(`repeat(shortfall) { ... return@repeat }`)는 Kotlin에서 `return@repeat`이 람다의 현재 호출만 건너뛰고(continue와 동일) 전체 `repeat`을 멈추지 않는다. PLAN.md의 명시적 behavior("부족분이 2지만 1회차 후 false를 반환하면 루프를 멈추고 skippedCount를 1 증가시킨다")는 실제 중단(break)을 요구하므로 `for (attempt in 1..shortfallCount) { ... break }`로 구현했다 — behavior 문서가 코드 예시보다 우선하는 근거다.
- **KDoc "@Transactional" 리터럴 → "트랜잭션 애노테이션"**: acceptance criteria가 `grep -c "@Transactional" == 0`으로 "이 파일이 트랜잭션 경계를 열지 않는다"를 검증하는데, 클래스 KDoc이 그 사실을 설명하며 리터럴 "@Transactional"을 그대로 썼다가 같은 grep에 걸렸다(05-03 "Clock", 05-04 "group by"와 동일 유형). 의미는 유지하고 리터럴만 바꿨다.
- **취소된 예약 픽스처는 커스텀 `@Modifying` 쿼리를 우회**: `ReservationRepository.cancelByMemberIfActive`를 트랜잭션 애노테이션 없는 이 테스트 클래스에서 직접 호출하면 기본 `readOnly` 트랜잭션 때문에 flush가 `TransactionRequiredException`으로 실패한다(05-05 `InactivityDeductionServiceTest`의 동일 문제). `Reservation`의 `var` 필드(`status`·`canceledAt`·`canceledByMember`·`refunded`)를 직접 대입한 뒤 `saveAndFlush`로 우회했다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `repeat`+`return@repeat` 예시를 그대로 쓰면 "대상 소진 시 루프 중단" behavior가 깨짐**
- **Found during:** 구현 설계 단계(코드 작성 전 PATTERNS.md 예시와 PLAN.md behavior 문구 대조)
- **Issue:** PATTERNS.md가 인용한 RESEARCH 코드(`repeat(shortfall) { if (!deducted) return@repeat }`)는 Kotlin 람다 반환 의미상 continue와 동일해, 대상이 소진된 이후에도 `deductOnce`가 남은 횟수만큼 계속 호출된다 — PLAN.md가 요구하는 "1회차 후 소진 시 skippedCount를 정확히 1만 증가시키고 중단"과 어긋난다
- **Fix:** `for (attempt in 1..shortfallCount) { ...; break }`로 실제 중단 로직을 구현
- **Files modified:** `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt`
- **Verification:** "부족분이 2인데 잔여 1.0인 장 한 장뿐이면 1회만 차감되고 스킵 1건으로 루프가 멈춘다" 테스트로 실증(GREEN)
- **Committed in:** `cb518df`

**2. [Rule 1 - Bug] KDoc의 "@Transactional" 문자열이 자체 acceptance grep과 충돌**
- **Found during:** 최종 acceptance criteria 검증 단계
- **Issue:** 클래스 KDoc이 "트랜잭션 애노테이션을 붙이지 않는다"는 사실을 설명하며 리터럴 "@Transactional"을 포함해 `grep -c "@Transactional" == 0` 기준이 실패했다
- **Fix:** "트랜잭션 애노테이션"으로 표현을 바꿔 의미는 유지하고 리터럴만 제거
- **Files modified:** `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt`
- **Verification:** `grep -c "@Transactional" ...` → 0, `./gradlew build` 재실행 그린
- **Committed in:** `cb518df`

**3. [Rule 1 - Bug] 취소 픽스처가 트랜잭션 없는 컨텍스트에서 `TransactionRequiredException`**
- **Found during:** GREEN 검증(테스트 최초 실행)
- **Issue:** `reservationRepository.cancelByMemberIfActive(...)`를 클래스 레벨 트랜잭션 애노테이션이 없는 테스트에서 직접 호출하자 커스텀 `@Modifying` 쿼리의 기본 `readOnly` 트랜잭션 때문에 flush가 실패했다
- **Fix:** `Reservation`의 취소 관련 `var` 필드를 직접 대입한 뒤 `saveAndFlush`로 우회하는 방식으로 교체
- **Files modified:** `src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt`
- **Verification:** 재실행 시 통과
- **Committed in:** `291d0d4`(RED 커밋에 포함 — 최종 파일 기준으로 커밋)

---

**Total deviations:** 3 auto-fixed (전부 Rule 1 — 버그 수정, PLAN.md가 명시한 behavior·D-112·D-106 자체는 변경 없음)
**Impact on plan:** 계획서의 흐름(9단계 action)·KDoc 3항목·interfaces 목록은 그대로 구현했다. 편차는 (a) 인용된 코드 예시의 Kotlin 의미론 오류를 behavior 문서 기준으로 수정, (b) acceptance grep과 KDoc 문구 충돌 해소, (c) 테스트 픽스처의 트랜잭션 문제 우회— 셋 다 로직·정책 변경이 아니다.

## Issues Encountered

None — 위 3건은 모두 Deviations 섹션에서 자동 수정으로 처리했다. Docker Desktop이 이미 기동돼 있어 Testcontainers 기반 통합테스트가 즉시 실행됐다.

## User Setup Required

None - 이 플랜은 서비스 클래스와 테스트만 추가했다. 환경변수·외부 서비스 설정이 필요 없다.

## Known Stubs

None — 스텁 패턴(하드코딩된 빈 값, 플레이스홀더 문자열, 데이터 소스 미배선) 없음.

## Threat Flags

None — 이 플랜이 추가한 코드는 PLAN.md `<threat_model>`이 이미 다룬 표면(T-05-19~23)만 건드리며, 새 네트워크 엔드포인트·인증 경로·스키마 변경은 없다.

## Next Phase Readiness

- 05-07(멱등성 플랜)이 이 러너의 `run`을 반복 호출해 "같은 날 중복 실행·며칠 밀린 실행"을 검증할 수 있다 — `run`이 순수하게 상태 기반(D-106)이라 추가 배선 없이 그대로 재사용 가능
- 05-08(스케줄러·수동 실행 API)이 `InactivityBatchScheduler`(`@Scheduled` 트리거)와 `AdminBatchController`(`POST /api/admin/batch/inactivity-runs`)에서 각각 `run(SCHEDULED, null)`/`run(MANUAL, adminId)`만 호출하면 된다 — 트리거 분기와 관리자 조회가 이미 이 플랜에서 완성됐다
- 회원 단위 예외 격리 케이스(한 회원의 예외가 나머지 회원 처리를 막지 않는다)는 이 플랜에서 실행 이력 하나의 트랜잭션 밖에서 예외를 안정적으로 유발하기 어려워 다루지 않았다 — PLAN.md가 명시적으로 허용한 대로 05-07 멱등성 테스트에서 다룬다
- REQUIREMENTS.md는 이 플랜에서 건드리지 않았다 — BATCH-01·02·04의 Complete 전환은 05-09가 일괄 처리한다(project_rules 지시, 05-03~05 선례와 동일)
- Blocker 없음

---
*Phase: 05-batch*
*Completed: 2026-08-15*

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt
- FOUND: src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt
- FOUND commit: 291d0d4, cb518df (verified in `git log --oneline`)
