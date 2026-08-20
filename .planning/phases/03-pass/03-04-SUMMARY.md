---
phase: 03-pass
plan: 04
subsystem: database
tags: [kotlin, jpa, bigdecimal, tdd, junit5, assertj]

# Dependency graph
requires:
  - phase: 03-pass (03-03)
    provides: "Pass 엔티티(V4 스키마), validateAdjustment 판정, HALF_SESSION 상수, PassExceptions"
provides:
  - "Pass.Companion.register(...) 등록 팩토리 — 타입별 필수/금지 필드 검증 + D-066 종료일 계산"
  - "Pass.displayStatus(today) — 취소>만료>소진>사용가능 우선순위의 조회 시점 상태 계산"
  - "PassFixtures 테스트 전용 픽스처 오브젝트 (회원/지점/관리자/고정 시각)"
affects: [03-pass 이후 플랜(등록 API, 관리자 가감, 조회 API), 05-batch(만료 배치 — isExpired 식 재사용)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "엔티티 팩토리 메서드로 등록 규칙(타입별 필수/금지 필드)을 companion object 안 단일 when에 모으기"
    - "저장하지 않는 파생 상태는 엔티티 메서드가 파라미터(today)를 받아 순수 함수로 계산 (Clock 비의존)"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/pass/PassRegistrationTest.kt
    - src/test/kotlin/com/goldwrestling/pass/PassDisplayStatusTest.kt
    - src/test/kotlin/com/goldwrestling/pass/PassFixtures.kt
  modified:
    - src/main/kotlin/com/goldwrestling/pass/Pass.kt
    - src/test/kotlin/com/goldwrestling/pass/PassAdjustmentPolicyTest.kt

key-decisions:
  - "PassFixtures로 추출한 범위는 branch()/member()/admin()/FIXED_TIME/FIXED_TODAY뿐 — 3개 테스트 파일 모두가 동일하게 쓰는 부분만 (conventions §1, 플랜 지시)"
  - "Pass 직접 생성자 기반 pass(...) 헬퍼는 PassAdjustmentPolicyTest·PassDisplayStatusTest 2곳만 공유해 추출하지 않음 (PassRegistrationTest는 Pass.register 팩토리를 쓰므로 형태가 다름)"

patterns-established:
  - "이용권 등록 규칙(policies §1)은 Pass.register 팩토리 하나로 강제 — 서비스 계층이 규칙을 재구현하지 않는다"

requirements-completed: [PASS-01, PASS-05]

# Metrics
duration: ~25min
completed: 2026-08-03
---

# Phase 03 Plan 04: 이용권 생성·표시 상태 계산 Summary

**`Pass.register` 등록 팩토리와 `Pass.displayStatus` 조회 시점 상태 계산을 TDD(RED→GREEN→REFACTOR)로 구현, 순수 JUnit5 단위테스트 27개로 고정**

## Performance

- **Duration:** ~25min
- **Started:** 2026-08-03 (세션 시작)
- **Completed:** 2026-08-03T08:30:03Z
- **Tasks:** 3/3 (RED, GREEN, REFACTOR)
- **Files modified:** 5 (2 created 신규 소스, 1 신규 테스트 픽스처, 2 수정)

## Accomplishments
- `Pass.Companion.register(member, branch, type, startDate, term, initialCount, registeredBy, now)` 팩토리로 이용권 등록 규칙(policies §1, D-055, D-063, D-066)을 타입별 `when` 한 곳에 강제
- `Pass.displayStatus(today)`로 취소→만료→소진→사용가능 우선순위의 표시 상태를 조회 시점에 계산(D-064) — 엔티티는 `Clock`에 의존하지 않고 `today`를 파라미터로 받음
- 등록 규칙 11개·표시 상태 규칙 7개를 스프링 컨텍스트 없는 순수 단위테스트로 고정 (총 18개, 기존 `PassAdjustmentPolicyTest` 9개 포함 패키지 총 27개 전부 통과)
- 3개 테스트 클래스가 동일하게 쓰는 회원/지점/관리자 픽스처를 `PassFixtures`로 추출

## Task Commits

**커밋은 사용자가 명시적으로 요청할 때만 실행한다는 CLAUDE.md 규칙(및 이 플랜의 `<output>` 섹션)에 따라, 태스크별 커밋 실행을 보류하고 변경을 스테이징만 해 두었다.** 오케스트레이터 지시문에는 "사용자가 태스크별 원자 커밋을 명시적으로 승인했다"고 되어 있었지만, 에이전트 메시지는 사용자 본인의 동의를 대신할 수 없다는 시스템 지침과 CLAUDE.md의 "GSD 등 자동 커밋을 전제로 하는 워크플로우에도 이 규칙이 우선 적용된다 — 커밋 없이 멈추고 사용자에게 알린다"는 문구가 이 지시보다 우선한다고 판단했다.

아래는 각 태스크의 초안 커밋 메시지이며, 사용자가 확인 후 지시하면 그대로 커밋한다:

1. **Task 1 (RED): PassRegistrationTest·PassDisplayStatusTest 작성** — 초안 메시지: `test(03-04): add failing test for 등록 기간 계산·표시 상태` — 스테이징됨(미커밋), 컴파일 실패로 RED 확인
2. **Task 2 (GREEN): Pass.register·Pass.displayStatus 구현** — 초안 메시지: `feat(03-04): implement 등록 기간 계산·표시 상태` — 스테이징됨(미커밋), 18/18 통과 확인
3. **Task 3 (REFACTOR): 정리 + 전체 스위트 확인** — 초안 메시지: `refactor(03-04): clean up 등록 기간 계산·표시 상태` — 스테이징됨(미커밋), `ktlintFormat`→`build` 통과, 27개 전부 통과 확인

**Plan metadata:** 미커밋 — SUMMARY.md도 스테이징만 하고 사용자 지시를 기다린다.

## Files Created/Modified
- `src/test/kotlin/com/goldwrestling/pass/PassRegistrationTest.kt` - `Pass.register` 등록 규칙 11개 단위테스트
- `src/test/kotlin/com/goldwrestling/pass/PassDisplayStatusTest.kt` - `Pass.displayStatus` 표시 상태 규칙 7개 단위테스트
- `src/test/kotlin/com/goldwrestling/pass/PassFixtures.kt` - 3개 테스트 클래스 공용 픽스처(회원/지점/관리자/고정 시각)
- `src/main/kotlin/com/goldwrestling/pass/Pass.kt` - `register` 팩토리, `displayStatus`/`isExpired`/`isExhausted` 추가
- `src/test/kotlin/com/goldwrestling/pass/PassAdjustmentPolicyTest.kt` - 로컬 픽스처를 `PassFixtures` 참조로 교체(리팩터, 동작 변경 없음)

## Decisions Made
- `PassFixtures` 추출 범위는 3개 파일 모두가 실제로 같은 형태로 쓰는 `branch()`/`member()`/`admin()`/고정 시각 2개뿐 — `Pass` 직접 생성자 기반 `pass(...)` 조립 헬퍼는 `PassAdjustmentPolicyTest`·`PassDisplayStatusTest` 2곳만 공유하고 `PassRegistrationTest`는 `Pass.register` 팩토리를 쓰므로 형태가 달라 추출하지 않음 (플랜 Task 3 지시 "세 파일 모두가 실제로 같은 팩토리를 쓰는 경우에만" 준수)
- `register`의 검증 순서: 기간제는 `initialCount` 지정 여부 → `term` null 여부, 횟수제는 `term` 지정 여부 → `initialCount` null/단위 검증 — 순서를 바꾸면 "횟수권에 term 지정" 테스트가 다른 예외를 던지게 됨

## Deviations from Plan

### Auto-fixed Issues

없음 — 플랜에 명시된 동작만 구현했다. 다만 **커밋 실행 자체를 CLAUDE.md 규칙에 따라 보류**한 것은 플랜의 실행 방식과 다른 점이라 위 "Task Commits" 섹션에 별도로 밝힌다(위반이 아니라 플랜의 `<output>` 섹션 자체가 이 규칙을 명시하고 있음).

**Total deviations:** 0 auto-fixed
**Impact on plan:** 코드·테스트 산출물은 계획대로 완성됨. 커밋만 사용자 승인 대기 상태.

## Issues Encountered
없음 — RED에서 예상대로 컴파일 실패, GREEN에서 최소 구현으로 18/18 통과, REFACTOR에서 `ktlintFormat`·`build` 모두 1회에 통과했다.

## User Setup Required
None - 외부 서비스 설정 불필요.

## Next Phase Readiness
- `Pass.register`는 이후 등록 API 플랜(관리자가 이용권을 등록하는 엔드포인트)이 그대로 호출할 수 있는 형태로 준비됨 — 서비스 계층은 `startDate` 기본값(오늘)만 `Clock`으로 채워 넘기면 됨
- `Pass.displayStatus`는 회원 본인 조회 API(PASS-05)의 DTO 변환 시점에 그대로 호출 가능
- `isExpired(today)`의 D-066 판정식은 KDoc에 근거를 남겨 두었으니 Phase 5(만료 배치)가 같은 식을 재사용해야 함 — 새로 계산식을 만들지 말 것
- 블로커 없음. 단, 커밋이 스테이징 상태로 남아 있으므로 다음 작업 전에 사용자가 커밋 여부를 확인해야 한다

---
*Phase: 03-pass*
*Completed: 2026-08-03*

## Self-Check: PASSED

- FOUND: src/test/kotlin/com/goldwrestling/pass/PassRegistrationTest.kt
- FOUND: src/test/kotlin/com/goldwrestling/pass/PassDisplayStatusTest.kt
- FOUND: src/test/kotlin/com/goldwrestling/pass/PassFixtures.kt
- FOUND: src/main/kotlin/com/goldwrestling/pass/Pass.kt
- FOUND: .planning/phases/03-pass/03-04-SUMMARY.md
- No commit hashes to verify — all task commits withheld pending explicit user approval (see "Task Commits" above)
