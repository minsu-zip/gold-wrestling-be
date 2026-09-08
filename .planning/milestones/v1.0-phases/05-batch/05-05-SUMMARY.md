---
phase: 05-batch
plan: 05
subsystem: batch
tags: [kotlin, spring-boot, jpa, postgresql, testcontainers, mockito, tdd]

# Dependency graph
requires:
  - phase: 05-batch (05-02)
    provides: "V9 스키마 — pass_transaction 시스템 주체 CHECK 완화(ck_pass_transaction_subject at-most-one), INACTIVITY 사유가 admin=null·member=null로 저장 가능"
  - phase: 05-batch (05-04)
    provides: "PassRepository.findDeductibleSessionPasses(회당 재선택용 재조회) — 이 플랜이 소비하는 조회, 05-04 KDoc이 명시한 '회당 재호출' 계약"
provides:
  - "InactivityDeductionService.deductOnce(memberId): Boolean — 배치가 회원 잔여를 실제로 바꾸는 유일한 트랜잭션 단위 지점"
affects: [05-06, 05-07]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "회원 1명당 별도 @Transactional 서비스 빈으로 배치 차감 트랜잭션 경계를 분리(05-01 결정 C) — self-invocation 우회·전체 롤백을 피한다"
    - "Mockito 5 + Kotlin non-null 인터페이스 파라미터에는 any()/anyLong() 매처 대신 실제 리터럴 값을 그대로 인자로 넘긴다(전체 인자에 매처를 안 쓰면 equals로 매칭)"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/batch/InactivityDeductionService.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityDeductionServiceTest.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityDeductionRaceTest.kt
  modified: []

key-decisions:
  - "잔여 0인 SESSION_PASS 픽스처는 INITIAL_GRANT 이력을 생성하지 않는다 — ck_pass_transaction_amount_nonzero(V4)가 금액 0인 이력을 거부한다(D-065와 같은 이유)"
  - "취소된 SESSION_PASS 픽스처는 PassRepository.cancelIfNotCanceled 대신 취소 메타데이터를 채운 Pass를 직접 saveAndFlush한다 — 커스텀 @Modifying 쿼리는 명시적 @Transactional 없이 호출하면 기본 readOnly 트랜잭션이 붙어 flush가 실패하는데, 이 테스트 클래스는 deductOnce 자체 트랜잭션 커밋 결과를 검증하려고 클래스 레벨 @Transactional을 의도적으로 배제했다"
  - "Mockito 5는 Kotlin이 non-null로 선언한 인터페이스 파라미터에 any()/anyLong()(null 반환)을 쓰면 'any(...) must not be null' NPE를 던진다 — RaceTest는 매처 없이 실제 값을 그대로 스텁 인자로 전달해 우회했다"

patterns-established:
  - "TDD 플랜의 두 번째 사이클(경쟁 패배 스킵)이 첫 사이클 구현만으로 이미 통과하는 경우, 계획서가 사전에 승인한 대로 코드 변경 없이 회귀 방지 테스트로 커밋한다(사이클 1 GREEN이 사이클 2 요구를 이미 만족)"

requirements-completed: []

# Metrics
duration: ~55min
completed: 2026-08-15
---

# Phase 5 Plan 5: 미사용 차감 1회 반영 InactivityDeductionService Summary

**회당 재선택·부분 차감·부족분 이월 금지를 구현한 `InactivityDeductionService.deductOnce` — 배치가 회원 잔여 횟수를 실제로 바꾸는 유일한 `@Transactional` 지점을 TDD 2사이클(RED→GREEN)로 완성**

## Performance

- **Duration:** ~55분
- **Started:** 2026-08-15T04:23
- **Completed:** 2026-08-15T04:38
- **Tasks:** 1 (TDD 2사이클)
- **Files modified:** 3 (신규 3, 수정 0)

## Accomplishments
- `InactivityDeductionService.deductOnce(memberId): Boolean`을 추가 — 회당 재조회(`findDeductibleSessionPasses`) → 만료 임박 한 장 선택 → `min(1회, 잔여)` 부분 차감 → 조건부 UPDATE(D-021) → 재조회 → `INACTIVITY` 이력(시스템 주체, `admin=null`·`member=null`) 저장까지 한 트랜잭션에서 처리
- `@Transactional`이 `deductOnce`에만 있고 클래스는 `readOnly=true`(D-020) — 05-06 배치 루프가 이 빈을 회원별로 호출해도 한 회원의 실패가 앞선 회원 전원을 롤백하지 않는다(05-01 결정 C의 실현부)
- 부분 차감·부족분 이월 금지·만료 임박순 정렬(동률 시 id순)·회당 재선택(연속 호출마다 재조회)·시스템 주체 이력·"잔여 = 이력 합계" 불변식을 Testcontainers 통합테스트 13종으로 실증
- 조건부 UPDATE 0행(경쟁 패배) 시 이력을 남기지 않고 스킵함을 스프링 컨텍스트 없는 Mockito 단위테스트로 별도 검증(밀리초 단위)
- 전체 스위트(기존 테스트 포함) 회귀 없이 `./gradlew ktlintFormat`·`./gradlew build` 그린

## Task Commits

TDD 2사이클로 진행(계획서 지시대로 사이클 1은 RED→GREEN 커밋 분리, 사이클 2는 이미 통과해 코드 변경 없이 테스트만 커밋):

1. **사이클 1 RED** — `eed44ae` (test): `InactivityDeductionServiceTest` 13종 작성, 대상 클래스 없어 컴파일 실패
2. **사이클 1 GREEN** — `c173cac` (feat): `InactivityDeductionService` 구현, 13종 전부 통과
3. **사이클 2** — `26d68e4` (test): `InactivityDeductionRaceTest` 작성, 사이클 1 구현이 이미 0행 분기를 갖고 있어 코드 변경 없이 통과(계획서 사이클 2 GREEN 문구대로)

**Plan metadata:** (다음 커밋에서 기록)

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/batch/InactivityDeductionService.kt` - 미사용 차감 1회 반영 트랜잭션 단위 서비스(신규)
- `src/test/kotlin/com/goldwrestling/batch/InactivityDeductionServiceTest.kt` - 부분 차감·회당 재선택·시스템 주체 이력·불변식 통합테스트 13종(신규)
- `src/test/kotlin/com/goldwrestling/batch/InactivityDeductionRaceTest.kt` - 조건부 UPDATE 경쟁 패배 시 이력 미기록 단위테스트(신규)

## Decisions Made

- **잔여 0 픽스처는 INITIAL_GRANT 이력을 생성하지 않는다**: `ck_pass_transaction_amount_nonzero`(V4)가 금액 0인 `PassTransaction`을 거부한다(D-065 "잔여가 이미 0이면 상쇄 이력을 남기지 않는다"와 같은 원리). "잔여 0인 SESSION_PASS만 보유" 테스트는 이력 없이 픽스처를 만들도록 헬퍼에 분기를 추가했다.
- **취소된 SESSION_PASS 픽스처는 `cancelIfNotCanceled`를 쓰지 않는다**: 이 커스텀 `@Modifying` 쿼리는 명시적 `@Transactional`이 없으면 Spring Data가 기본 `readOnly=true` 트랜잭션을 붙여 `flush`가 `TransactionRequiredException`으로 실패한다. `deductOnce`의 실제 커밋 결과를 검증하려고 테스트 클래스에 `@Transactional`을 의도적으로 배제했기 때문에(계획서 지시, `MemberReservationServiceTest` 선례), 취소 메타데이터(`canceledBy`·`canceledAt`·`cancelReason`, `ck_pass_cancellation` V4)를 채운 `Pass`를 직접 `saveAndFlush`하는 방식으로 우회했다. (참고: `saveAndFlush`는 `SimpleJpaRepository`의 상속 CRUD 메서드라 클래스 레벨 `@Transactional(readOnly=false)`가 이미 붙어 있어 문제가 없다 — 커스텀 `@Query` 메서드만 이 문제가 있다.)
- **Mockito 5 + Kotlin non-null 매처 우회**: `PassRepository`는 Kotlin 인터페이스라 `memberId: Long`·`today: LocalDate` 등이 non-null로 선언돼 있다. Mockito 5는 Kotlin 메타데이터를 읽어 `any()`/`anyLong()`(내부적으로 null을 반환) 사용을 감지하면 "any(...) must not be null" NPE를 던진다(구버전엔 없던 안전장치). 이 저장소 첫 순수 Mockito 단위테스트라 마주친 문제 — 매처를 아예 안 쓰고 실제 리터럴 값을 인자로 그대로 넘기는 방식(Mockito가 매처 없는 인자는 `equals`로 매칭)으로 해결했다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] "잔여 0" 픽스처 헬퍼가 V4 CHECK(`ck_pass_transaction_amount_nonzero`)를 위반**
- **Found during:** GREEN 검증(테스트 최초 실행)
- **Issue:** `persistSessionPass` 헬퍼가 잔여 금액과 동일한 `INITIAL_GRANT` 이력을 항상 생성했는데, 잔여 "0.0" 케이스에서 금액 0인 `PassTransaction` INSERT가 `DataIntegrityViolationException`으로 거부됐다
- **Fix:** 금액이 0이면(`compareTo(BigDecimal.ZERO) == 0`) 이력 생성을 건너뛰도록 헬퍼에 조건 추가
- **Files modified:** `src/test/kotlin/com/goldwrestling/batch/InactivityDeductionServiceTest.kt`
- **Verification:** 재실행 시 통과
- **Committed in:** `eed44ae` (사이클 1 RED 커밋에 포함 — 최종 파일 기준으로 커밋했다)

**2. [Rule 1 - Bug] 취소 픽스처가 트랜잭션 없는 컨텍스트에서 `TransactionRequiredException`**
- **Found during:** GREEN 검증(테스트 최초 실행)
- **Issue:** `passRepository.cancelIfNotCanceled(...)`를 클래스 레벨 `@Transactional`이 없는 테스트에서 직접 호출하자 Spring Data의 커스텀 `@Modifying` 쿼리 기본 `readOnly` 트랜잭션 때문에 flush가 실패했다
- **Fix:** 취소 메타데이터를 채운 `Pass`를 `saveAndFlush`로 직접 저장하는 `persistCanceledSessionPass` 헬퍼로 교체
- **Files modified:** `src/test/kotlin/com/goldwrestling/batch/InactivityDeductionServiceTest.kt`
- **Verification:** 재실행 시 통과
- **Committed in:** `eed44ae` (사이클 1 RED 커밋에 포함)

**3. [Rule 1 - Bug] Mockito 5의 Kotlin non-null 매처 NPE**
- **Found during:** 사이클 2 GREEN 검증(RaceTest 최초 실행)
- **Issue:** `any()`/`anyLong()`을 Kotlin non-null 파라미터(`PassRepository` 메서드)에 쓰자 "any(...) must not be null" NPE가 났다
- **Fix:** 매처를 제거하고 실제 리터럴 값을 스텁 인자로 그대로 전달
- **Files modified:** `src/test/kotlin/com/goldwrestling/batch/InactivityDeductionRaceTest.kt`
- **Verification:** 재실행 시 통과
- **Committed in:** `26d68e4` (사이클 2 test 커밋)

---

**Total deviations:** 3 auto-fixed (전부 Rule 1 — 테스트 코드 버그 수정, 프로덕션 도메인 로직 변경 없음)
**Impact on plan:** 계획서의 `deductOnce` 흐름·KDoc 4항목·behavior 13종·경쟁 패배 단언은 그대로 구현했다. 편차는 전부 테스트 픽스처·매처 사용법 수정이며 acceptance criteria(grep 카운트, 커밋 3건, 14개 이상 테스트)를 그대로 충족한다.

## Issues Encountered

None — 위 3건은 모두 Deviations 섹션에서 자동 수정으로 처리했다.

## User Setup Required

None - 이 플랜은 서비스 클래스와 테스트만 추가했다. 환경변수·외부 서비스 설정이 필요 없다.

## Next Phase Readiness

- 05-06(`InactivityBatchRunner`)이 `InactivityDeductionService.deductOnce(memberId)`를 부족분만큼 반복 호출해 배치 루프를 구성할 수 있다 — "1회 호출 = 1회 재조회" 계약과 `false` 반환(대상 소진·경쟁 패배)이 정상 경로임이 KDoc·테스트로 확정됐다
- "몇 번 차감할지"는 05-06(05-03의 `InactivityDueDateCalculator`)이 결정하고, "어느 장에서 얼마나"는 이 서비스가 전부 책임진다 — 관심사 분리가 완료됐다
- REQUIREMENTS.md는 이 플랜에서 건드리지 않았다 — BATCH-01·02의 Complete 전환은 05-09가 일괄 처리한다(project_rules 지시, 05-04 선례와 동일)
- Blocker 없음

---
*Phase: 05-batch*
*Completed: 2026-08-15*

## Self-Check: PASSED

All created files and task commits (eed44ae, c173cac, 26d68e4) verified present.
