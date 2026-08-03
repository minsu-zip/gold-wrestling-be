---
phase: 03-pass
plan: 05
subsystem: database
tags: [kotlin, jpa, tdd, domain-logic, pass]

# Dependency graph
requires:
  - phase: 03-pass (03-03, 03-04)
    provides: Pass 엔티티 골격(validateAdjustment, register, displayStatus), PassFixtures 공용 픽스처, V4 스키마
provides:
  - "Pass.changePeriod(newStartDate, newEndDate) — 기간·유효기간 수정 판정 (PASS-04·PASS-07, D-062)"
  - "Pass.cancel(reason, admin, now): BigDecimal — 등록 취소 처리·상쇄 수량 산출 (PASS-08, D-059·D-065)"
  - "Pass.requireNotCanceled() private 헬퍼 — 3개 도메인 메서드가 공유하는 취소 재조작 거부"
affects: [03-pass 서비스 계층 플랜(기간 수정·취소 엔드포인트), 03-08 통합테스트(PassPeriodChange 이력 저장)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "판정과 반영의 역할 분리: 엔티티는 무엇을 바꿀지·얼마를 상쇄할지만 산출하고, 실제 잔여 반영은 PassRepository의 조건부 UPDATE가 담당 (validateAdjustment/adjustRemainingCount와 동일 패턴을 changePeriod/cancel에도 적용)"
    - "반복되는 가드 조건(3곳 이상)은 REFACTOR 단계에서 private 헬퍼로 추출 — GREEN 단계는 최소 구현만"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/pass/PassPeriodChangeTest.kt
    - src/test/kotlin/com/goldwrestling/pass/PassCancellationTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/pass/Pass.kt

key-decisions:
  - "changePeriod/cancel 모두 반환값·인자 시그니처는 계획서 <behavior> 표를 그대로 따름 — 별도 결정 없음"
  - "requireNotCanceled() 추출 시점을 REFACTOR(Task 3)로 미룸 — GREEN(Task 2)에서 미리 추출하면 '최소 구현'이라는 TDD 게이트 취지에 어긋나 raw 가드 3곳이 쌓인 뒤 한 번에 정리"

patterns-established:
  - "Pattern: 취소된 이용권 재조작 3중 방어 — validateAdjustment/changePeriod/cancel 진입부 requireNotCanceled() + adjustRemainingCount의 status=ACTIVE 조건(DB) + zeroRemainingCount 조건부 UPDATE(DB)"

requirements-completed: [PASS-04, PASS-07, PASS-08]

# Metrics
duration: ~20min
completed: 2026-08-03
---

# Phase 3 Plan 05: 기간 변경 판정 + 등록 취소 처리 Summary

**Pass 엔티티에 `changePeriod`(기간·유효기간 수정 판정, D-062)와 `cancel`(등록 취소·상쇄 수량 산출, D-059·D-065)을 RED→GREEN→REFACTOR로 구현, 취소 재조작 방어를 `requireNotCanceled()` 헬퍼로 3곳 통합**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-08-03
- **Tasks:** 3 (RED, GREEN, REFACTOR)
- **Files modified:** 3 (신규 2, 수정 1)

## Accomplishments

- `Pass.changePeriod(newStartDate: LocalDate?, newEndDate: LocalDate)` — 저녁반은 시작·종료일 모두 수정 가능, 횟수권은 종료일만 수정 가능(시작일 고정, D-062), 역전된 기간·취소된 이용권을 거부
- `Pass.cancel(reason: String, admin: Admin, now: OffsetDateTime): BigDecimal` — 상태·취소 시각·사유·주체를 전환하고, 잔여를 직접 바꾸지 않은 채 원장에 남길 상쇄 수량만 반환(D-021·D-065)
- 두 메서드와 기존 `validateAdjustment`가 공유하는 "취소된 이용권이면 거부" 가드를 `requireNotCanceled()` private 헬퍼로 통합
- `pass` 패키지 단위테스트 41개 전부 green (9+11+7+7+7), `./gradlew build` 전체 성공

## Task Commits

Each task was committed atomically (TDD gate sequence: test → feat → refactor):

1. **Task 1 (RED): PassPeriodChangeTest·PassCancellationTest 작성** - `1280ca9` (test) — `changePeriod`/`cancel` 미해결 참조로 컴파일 실패 확인
2. **Task 2 (GREEN): Pass.changePeriod·Pass.cancel 구현** - `1564e7c` (feat) — 14개 테스트 전부 통과
3. **Task 3 (REFACTOR): requireNotCanceled 추출 + 전체 빌드 확인** - `1b1c53e` (refactor) — ktlintFormat → build 전부 green

## Files Created/Modified

- `src/test/kotlin/com/goldwrestling/pass/PassPeriodChangeTest.kt` - 기간·유효기간 수정 판정 7규칙 단위테스트 (스프링 없음)
- `src/test/kotlin/com/goldwrestling/pass/PassCancellationTest.kt` - 등록 취소·상쇄 수량 산출 7규칙 단위테스트 (스프링 없음, `isEqualByComparingTo` 사용)
- `src/main/kotlin/com/goldwrestling/pass/Pass.kt` - `changePeriod`·`cancel`·`requireNotCanceled` 추가, 클래스 KDoc에 도메인 메서드 5개 목록·근거 조항 정리

## Decisions Made

None - 계획서 `<behavior>`·`<implementation>` 명세를 그대로 구현. 별도 설계 결정 없음 (docs/decisions.md 추가 기록 없음).

## Deviations from Plan

None - plan executed exactly as written. GREEN 단계에서 `requireNotCanceled()`를 미리 추출하려다, TDD 최소 구현 원칙과 계획서의 Task 3 담당 범위를 지키기 위해 raw 가드로 되돌리고 REFACTOR 단계에서 정리했다 — 이는 계획 준수를 위한 자체 수정이며 배포 코드에 남은 편차는 없다.

## Issues Encountered

None.

## User Setup Required

None - 외부 서비스 설정 불필요.

## TDD Gate Compliance

- RED: `1280ca9` (`test(03-05): ...`) — `./gradlew test`가 `Unresolved reference 'cancel'/'changePeriod'/'isEqualByComparingTo'`로 컴파일 실패, 14개 테스트 모두 미실행 상태로 고정
- GREEN: `1564e7c` (`feat(03-05): ...`) — 같은 14개 테스트 전부 PASSED
- REFACTOR: `1b1c53e` (`refactor(03-05): ...`) — 동작 변경 없이 헬퍼 추출, `./gradlew build` 성공(전체 `pass` 패키지 단위 41개 + 통합 7개 포함)

게이트 순서가 커밋 로그에 그대로 남아 있어 별도 경고 없음.

## Next Phase Readiness

- `Pass.changePeriod`·`Pass.cancel`은 순수 도메인 판정만 하므로, 다음 서비스 계층 플랜(관리자 기간 수정·취소 엔드포인트)이 이 메서드를 호출한 뒤 변경 전값을 지역 변수로 보관해 `PassPeriodChange`/`PassTransaction` 이력을 같은 트랜잭션에서 남기면 된다.
- `PassPeriodChange` 이력 테이블 실제 저장 검증은 03-08 통합테스트가 담당 — 이 플랜은 의도적으로 스프링 컨텍스트를 띄우지 않았다.
- 특별한 블로커 없음.

---
*Phase: 03-pass*
*Completed: 2026-08-03*
