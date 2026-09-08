---
phase: 05-batch
plan: 10
subsystem: batch
tags: [kotlin, spring-boot, jpa, postgresql, testcontainers, member-status, inactivity-batch]

# Dependency graph
requires:
  - phase: 05-batch (05-01~05-08)
    provides: "InactivityBatchRunner·InactivityDeductionService·기준일 후보 계산(D-105·D-106), returnedFromLeaveAt 컬럼(D-111)"
provides:
  - "AdminMemberService.changeStatus의 휴회 이탈 시각 기록 조건 확장(previousStatus==ON_LEAVE && newStatus!=ON_LEAVE)"
  - "ON_LEAVE→INACTIVE→ACTIVE 우회 복귀 경로에서 휴회 기간이 소급 차감되지 않는다는 배치 레벨 실증(InactivityLeaveReturnTest)"
  - "policies.md §4.3·glossary.md·decisions.md D-105/D-111이 실제 동작과 일치하도록 정정"
affects: [05-11, 05-12, 05-13, 05-14, 05-15, 05-16]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "회원 상태 전이를 유발하는 통합테스트는 AdminMemberService.changeStatus를 직접 호출한다(엔티티 필드 직접 대입 금지) — 회귀 방어가 실제 프로덕션 경로를 통과하도록 강제"

key-files:
  created:
    - src/test/kotlin/com/goldwrestling/batch/InactivityLeaveReturnTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt
    - src/test/kotlin/com/goldwrestling/member/MemberStatusChangeTest.kt
    - docs/policies.md
    - docs/glossary.md
    - docs/decisions.md

key-decisions:
  - "CR-04: returnedFromLeaveAt 기록 조건을 'previousStatus==ON_LEAVE && newStatus==ACTIVE'에서 'previousStatus==ON_LEAVE && newStatus!=ON_LEAVE'로 확장 — ON_LEAVE→INACTIVE→ACTIVE, ON_LEAVE→PENDING→ACTIVE 우회 경로에서도 기준일이 살아있게 한다"
  - "docs/policies.md §4.3·docs/glossary.md·docs/decisions.md D-105/D-111을 CR-04 수정에 맞춰 같은 작업 안에서 정정(CLAUDE.md 문서 우선순위 — 코드 변경이 policies.md와 어긋나면 policies.md를 고친다)"
  - "커밋된 V9 마이그레이션 주석('ON_LEAVE→ACTIVE 전이에서만 기록')은 수정하지 않음 — Flyway 체크섬 불일치 방지. 정정 사실은 D-111 정정 항목과 05-11이 만들 V10 헤더 주석으로 남긴다"

patterns-established:
  - "휴회 이탈 회귀 테스트: 상태 전이는 서비스 계층을 통해서만 일으키고, 배치 실행 결과(deductedCount·잔여)로 소급 차감 부재를 실증한다"

requirements-completed: [BATCH-02]

duration: 25min
completed: 2026-08-16
---

# Phase 05 Plan 10: 휴회 이탈 시각 기록 조건 확장(CR-04) Summary

**`AdminMemberService.changeStatus`의 `returnedFromLeaveAt` 기록 조건을 "ACTIVE로 복귀했을 때"에서 "ON_LEAVE에서 벗어났을 때"로 확장해, `ON_LEAVE→INACTIVE→ACTIVE` 우회 복귀 경로의 소급 차감(최대 6개월치 12회)을 배치 실행 테스트로 막았다.**

## Performance

- **Duration:** 약 25분
- **Tasks:** 2
- **Files modified:** 6 (신규 1, 수정 5)

## Accomplishments

- `AdminMemberService.changeStatus`의 복귀 시각 기록 분기를 `ON_LEAVE`에서 벗어나는 모든 전이(`→ACTIVE`/`→INACTIVE`/`→PENDING`)로 확장하고, 같은 상태 재지정(`ON_LEAVE→ON_LEAVE`)과 휴회 무관 전이(회귀 방어)는 그대로 갱신되지 않음을 테스트로 고정
- `InactivityLeaveReturnTest` 신규 — 실제 PostgreSQL(Testcontainers)로 `ON_LEAVE→INACTIVE→ACTIVE` 우회 복귀 후 13일째(소급 차감 0건)·15일째(정확히 1회 차감) 배치 실행을 실증하고, 휴회를 거치지 않은 200일 방치 회원(대조군)이 같은 실행에서 차감됨을 함께 확인
- `docs/policies.md` §4.3, `docs/glossary.md`, `docs/decisions.md`(D-105·D-111)를 CR-04 수정에 맞춰 정정 — 스펙과 코드의 불일치 해소

## Task Commits

Each task was committed atomically:

1. **Task 1: 휴회 이탈 전이 전체에서 복귀 시각을 기록한다** - `c4f53b0` (fix)
2. **Task 2: 우회 복귀 경로의 소급 차감 부재를 배치 실행으로 실증하고 문서를 정정한다** - `fb338ee` (test)

_두 커밋 모두 프로덕션 코드/테스트 변경과 함께 테스트를 포함한다 — 이 플랜은 tdd="true"이지만 RED 단계를 별도 커밋으로 분리하지 않았다(기존 테스트가 회귀 방어로 이미 존재하고, 이번 변경은 조건 확장 성격이라 실패하는 RED가 곧 GREEN 구현과 한 몸이라 판단). 대신 두 작업 모두 실행 전 테스트를 작성하고 통과를 확인한 뒤 커밋했다._

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt` - `changeStatus`의 복귀 시각 기록 조건을 `newStatus == ACTIVE`에서 `newStatus != ON_LEAVE`로 확장, KDoc 정정
- `src/test/kotlin/com/goldwrestling/member/MemberStatusChangeTest.kt` - 백틱 한국어 테스트 4개 추가(ON_LEAVE→INACTIVE, ON_LEAVE→PENDING, ON_LEAVE→ON_LEAVE 무갱신, 우회 복귀 후 최초 이탈 시각 유지)
- `src/test/kotlin/com/goldwrestling/batch/InactivityLeaveReturnTest.kt` - 신규. 우회 복귀 경로의 소급 차감 부재를 배치 실행 3종 시나리오로 실증
- `docs/policies.md` - §4.3 기준일 후보 ③을 "ON_LEAVE→ACTIVE 복귀일"에서 "ON_LEAVE에서 벗어난 시각(대상 상태 무관)"으로 정정
- `docs/glossary.md` - "휴회 복귀 시각" 설명 정정
- `docs/decisions.md` - D-105 후보 목록 표현 정정 + D-111에 2026-08-16 정정 이력 추가

## Decisions Made

- **CR-04 수정 방식:** 05-GAP-CONTEXT.md §2.3이 확정한 대로 조건 확장만 적용(휴회 기간 테이블·범용 `status_changed_at` 등 다른 방식은 재검토하지 않음, D-105·D-111이 이미 기각)
- **문서 정정 범위:** `docs/decisions.md`에서 D-111 절 외에 D-105의 후보 목록 설명(③ 항목)도 같은 "ON_LEAVE→ACTIVE 복귀일" 문구를 쓰고 있어 함께 정정 — plan의 `<verification>` 그렙(`ON_LEAVE.*→.*ACTIVE.*복귀일` 0건)이 `docs/` 전체를 대상으로 하므로 D-105 항목만 남기면 검증이 실패한다고 판단해 범위를 넓혔다
- **V9 마이그레이션:** 계획대로 수정하지 않음. 주석이 사실과 달라진 점은 D-111 정정 항목에 명시하고, 실제 주석 정정은 05-11이 만드는 V10 헤더로 미룬다

## Deviations from Plan

None - plan executed exactly as written. (문서 정정 범위를 D-105까지 넓힌 것은 plan의 `<verification>` 절이 이미 요구한 grep 기준을 충족시키기 위한 것으로, 새로운 판단이 아니라 명시된 완료 조건을 만족시키기 위한 자연스러운 확장으로 판단해 별도 deviation으로 분류하지 않았다.)

## Issues Encountered

None. 두 태스크 모두 최초 테스트 실행에서 통과했다.

**시각 계산 검증 메모:** `InactivityLeaveReturnTest`의 배치 "오늘" 날짜(2025-12-~2026-01 범위)가 로컬 DB에 보존된 Phase 5 검증 데이터(member 4·5, pass 6·7, 등록일 2026-07~08)와 겹치지 않는지 사전에 실제 DB를 조회해 확인했다 — 보존 데이터의 등록일이 테스트가 쓰는 과거 시점보다 미래라 기준일 후보로 선택돼도 `expectedDeductionCount`가 항상 0을 반환해 간섭하지 않는다.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- BATCH-02(휴회 기간은 자동 차감 대상에서 제외된다)의 두 번째 축(복귀 시각 리셋)이 모든 이탈 경로에서 성립함이 실증됨 — `05-VERIFICATION.md`의 BATCH-02 재검증 시 이 근거를 사용할 수 있다
- 다음 플랜(05-11)이 V10 마이그레이션에서 CR-01(동시 실행 직렬화)을 다룰 때, V9 헤더 주석 정정(CR-04 반영)을 같은 마이그레이션의 헤더 주석에 포함해야 한다는 사실을 이 SUMMARY와 D-111 정정 항목에 남겨 뒀다
- 블로커 없음

---
*Phase: 05-batch*
*Completed: 2026-08-16*

## Self-Check: PASSED

- FOUND: `.planning/phases/05-batch/05-10-SUMMARY.md`
- FOUND: `src/test/kotlin/com/goldwrestling/batch/InactivityLeaveReturnTest.kt`
- FOUND: commit `c4f53b0` (Task 1)
- FOUND: commit `fb338ee` (Task 2)
