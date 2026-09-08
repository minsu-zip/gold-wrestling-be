---
phase: 03-pass
plan: 03
subsystem: domain
tags: [kotlin, junit5, assertj, bigdecimal, tdd, domain-policy]

# Dependency graph
requires:
  - phase: 03-pass (03-02)
    provides: "Pass 엔티티(판정 메서드 없음), PassExceptions.kt, V4 스키마·PassRepository 조건부 UPDATE"
provides:
  - "Pass.validateAdjustment(amount) — policies §4.2a 5개 규칙의 순수 판정 메서드"
  - "PassAdjustmentPolicyTest — §4.2a 9개 문장을 고정한 순수 단위테스트"
affects: [03-04-registration, 03-06-adjustment-endpoint, phase-04-reservation, phase-06-evening-membership]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "엔티티 순수 판정 메서드(Boolean 아닌 throw-only) — Member.isOnboardingCompleted/isRejected와 달리 예외로 실패 사유를 구분해 반환"
    - "BigDecimal 0.5 단위 검증: remainder(HALF_SESSION).compareTo(ZERO) != 0"
    - "requireNotNull로 타입-불변식 전제(횟수제만 remainingCount non-null)를 코드에 남김"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/pass/PassAdjustmentPolicyTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/pass/Pass.kt

key-decisions: []

patterns-established:
  - "판정(엔티티 throw) vs 반영(리포지토리 조건부 UPDATE) 역할 분리 — 이후 03-05~03-06, Phase 4가 동일 패턴 재사용"

requirements-completed: [PASS-03]

# Metrics
duration: 3min
completed: 2026-08-03
---

# Phase 3 Plan 3: 이용권 수동 가감 정책 판정 Summary

**`docs/policies.md` §4.2a(관리자 수동 가감, D-056) 5개 규칙을 `Pass.validateAdjustment(amount)` 순수 판정 메서드로 고정 — TDD RED→GREEN→REFACTOR 3커밋**

## Performance

- **Duration:** 약 3분 (커밋 타임스탬프 기준 17:16:50 ~ 17:18:56)
- **Started:** 2026-08-03T17:16:50+09:00
- **Completed:** 2026-08-03T17:18:56+09:00
- **Tasks:** 3/3 (RED, GREEN, REFACTOR)
- **Files modified:** 2 (1 신규, 1 수정)

## Accomplishments

- `docs/policies.md` §4.2a의 5개 문장(취소 이용권 거부, 기간제 거부, 0.5 단위 강제, 음수 잔여 거부, 만료권도 가감 가능)이 9개 실행 가능한 단위테스트로 고정됨 — 정책 문서 문장이 테스트 메서드명 그대로다
- `Pass.validateAdjustment`가 검사 순서(취소 → 기간제 → 단위 → 잔여)를 명시적으로 강제하고, "취소된 기간제" 같은 복합 케이스에서 검사 순서가 실제로 지켜지는지까지 별도 테스트(9번째)로 증명
- 판정(엔티티)과 반영(`PassRepository.adjustRemainingCount` 조건부 UPDATE, 03-02 산출물)의 역할 분리를 KDoc과 테스트 양쪽에 남김 — 이후 플랜이 여기에 잔여 대입 코드를 실수로 추가하는 것을 막는 문서적 방어선

## Task Commits

Each gate was committed atomically:

1. **Task 1 (RED): PassAdjustmentPolicyTest 작성 — 전부 실패 확인** - `0b3b3f5` (test)
   - `./gradlew test --tests "com.goldwrestling.pass.PassAdjustmentPolicyTest"` 컴파일 실패로 RED 확인 (`validateAdjustment` 미해결 참조 9건)
2. **Task 2 (GREEN): Pass.validateAdjustment 구현 — 전부 통과** - `8691d05` (feat)
   - 9개 테스트 전부 PASSED
3. **Task 3 (REFACTOR): 정리 + 전체 스위트 확인** - `2a57ef2` (refactor)
   - 테스트 픽스처 중복(Member/Branch/Admin 조립)을 공통 `pass()` 팩토리로 통합, 동작 변경 없음
   - `./gradlew ktlintFormat` → `./gradlew build` 전부 통과 (전체 스위트 그린, `PassAdjustmentPolicyTest` 9개 그대로)

## Files Created/Modified

- `src/test/kotlin/com/goldwrestling/pass/PassAdjustmentPolicyTest.kt` (신규) - 순수 JUnit5+AssertJ 단위테스트 9개, 스프링 컨텍스트 없음. `eveningMembership`/`sessionPass`/`lessonPass` 픽스처 팩토리가 공통 `pass()` 하나로 위임
- `src/main/kotlin/com/goldwrestling/pass/Pass.kt` (수정) - `fun validateAdjustment(amount: BigDecimal)` 추가, companion object에 `HALF_SESSION = BigDecimal("0.5")`, 클래스 KDoc의 "도메인 판정 메서드는 이 태스크에 넣지 않는다" 안내문을 03-03 완료 반영으로 갱신

## Decisions Made

없음 — plan의 `<implementation>` 지시(companion object 상수, `remainder` 기반 단위 판정, `compareTo` 비교, `requireNotNull`)를 그대로 따랐다. 새로운 설계 판단이 필요한 지점이 없어 `docs/decisions.md`에 추가할 항목 없음.

## Deviations from Plan

None - plan에 명시된 검사 순서·시그니처·9개 테스트 케이스를 그대로 구현했다. 추가로 Pass.kt 클래스 KDoc의 "도메인 판정 메서드는 이 태스크에 넣지 않는다"는 03-02 시점 안내 문구가 이번 플랜 완료로 stale해져, 사실을 반영하도록 한 줄 갱신했다 — plan에 명시된 작업은 아니었으나 코드 밖 문서(주석) 정정이라 별도 deviation 규칙 분류 없이 GREEN 커밋에 포함했다.

## Issues Encountered

None.

## TDD Gate Compliance

- RED (`0b3b3f5`): 테스트 파일 생성 직후 `./gradlew test`가 컴파일 실패로 종료 — `validateAdjustment` 미해결 참조 9건. 커밋 시점에는 아직 구현이 없어 명확한 RED 상태였다.
- GREEN (`8691d05`): `Pass.validateAdjustment` 구현 후 9개 테스트 전부 PASSED.
- REFACTOR (`2a57ef2`): 테스트 픽스처 중복 제거, 전체 `./gradlew build` 그린. `@Test` 개수 9개 그대로(리팩터가 테스트를 지우지 않음).

게이트 순서·시퀀스 전부 준수됨. 경고 없음.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `Pass.validateAdjustment`가 이후 03-06(관리자 수동 가감 엔드포인트)과 Phase 4(예약 차감)가 재사용할 수 있는 형태로 완성됨 — 서비스 계층은 `pass.validateAdjustment(amount)` 호출 후 `PassRepository.adjustRemainingCount`를 호출하는 2단계 패턴을 그대로 쓰면 된다
- 이 판정 메서드는 잔여를 바꾸지 않으므로, 다음 플랜이 실제 반영(조건부 UPDATE + `PassTransaction` 이력 기록)을 별도로 구현해야 한다는 전제가 여전히 유효하다

---
*Phase: 03-pass*
*Completed: 2026-08-03*

## Self-Check: PASSED

- `src/test/kotlin/com/goldwrestling/pass/PassAdjustmentPolicyTest.kt` 존재 확인
- `src/main/kotlin/com/goldwrestling/pass/Pass.kt`에 `fun validateAdjustment(` 포함 확인
- 커밋 3건(`0b3b3f5`, `8691d05`, `2a57ef2`) 전부 `git log`로 확인
- `./gradlew build` 최종 그린 확인 (ktlintCheck + compileKotlin + 전체 테스트)
