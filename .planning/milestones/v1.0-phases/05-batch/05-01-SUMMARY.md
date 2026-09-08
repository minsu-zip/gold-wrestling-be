---
phase: 05-batch
plan: 01
subsystem: docs
tags: [glossary, decisions, requirements, roadmap, batch, inactivity]

# Dependency graph
requires:
  - phase: 05-batch (discuss/plan)
    provides: D-105~D-109 원안 + policies.md §4.3 확정 문구, REQUIREMENTS·ROADMAP Phase 5 골격
provides:
  - "glossary.md 배치(Phase 5) 섹션 — BatchExecution·BatchTrigger·BatchExecutionStatus·dueDate·shortfall·returnedFromLeaveAt"
  - "decisions.md D-110~D-114 — CHECK 완화 방식·복귀 시각 컬럼·배치 트랜잭션 경계·실행 이력 스키마·cron/API 경로"
  - "decisions.md D-105·D-108 보강 — 기준일 후보 ④ 다장 범위, 스킵 SUCCESS 집계"
  - "REQUIREMENTS.md·ROADMAP.md BATCH-01/03 문구 정정 (D-105·D-107 확정안 반영)"
affects: [05-02, 05-03, 05-04, 05-05, 05-06, 05-07, 05-08, 05-09]

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - docs/glossary.md
    - docs/decisions.md
    - docs/policies.md
    - .planning/REQUIREMENTS.md
    - .planning/ROADMAP.md

key-decisions:
  - "D-110: pass_transaction 시스템 주체 CHECK를 '정확히 하나'에서 '최대 하나'로 완화 (V9, admin_id·member_id 둘 다 NULL = 시스템 주체)"
  - "D-111: 휴회 복귀 시각은 member.returned_from_leave_at 단일 목적 컬럼 (ON_LEAVE→ACTIVE 전이에서만 갱신)"
  - "D-112: 미사용 차감 배치 트랜잭션 경계는 회원 1명 = 트랜잭션 1개 (루프는 @Transactional 없음)"
  - "D-113: batch_execution 스키마 확정, 경쟁 패배 스킵은 SUCCESS + skipped_count로 집계"
  - "D-114: cron 04:00 Asia/Seoul, 수동 실행 API는 POST /api/admin/batch/inactivity-runs 1개, 신규 에러코드 없음"

patterns-established: []

requirements-completed: [BATCH-01, BATCH-03, BATCH-04]

# Metrics
duration: ~15min
completed: 2026-08-15
---

# Phase 5 Plan 1: 배치 문서 정합 Summary

**Phase 5가 쓸 6개 신규 개념(glossary)과 5건의 Claude 재량 결정(D-110~D-114)을 코드 작성 전에 문서에 고정하고, BATCH-01·BATCH-03 요구사항 문구를 확정 결정(D-105·D-107)에 맞춰 정정**

## Performance

- **Duration:** ~15min
- **Tasks:** 3 완료
- **Files modified:** 5 (docs/glossary.md, docs/decisions.md, docs/policies.md, .planning/REQUIREMENTS.md, .planning/ROADMAP.md)

## Accomplishments
- `docs/glossary.md`에 `## 배치 (Phase 5)` 섹션을 신설해 `BatchExecution`·`BatchTrigger`·`BatchExecutionStatus`·`dueDate`·`shortfall`·`returnedFromLeaveAt` 6개 개념을 등록 — 금지어(`Ticket`/`Voucher`/`Booking`/`Session` 단독)와 충돌 없음을 확인
- `docs/decisions.md`에 Claude 재량 결정 5건(D-110~D-114)을 이유·기각 대안 포함해 기록하고, D-105(기준일 후보 ④ 다장 범위)·D-108(스킵 집계)에 사용자 확정 보강 문구를 추가
- `.planning/REQUIREMENTS.md`·`.planning/ROADMAP.md`의 BATCH-01(기준일 5종 후보 전체 나열)·BATCH-03(구현물 없음, D-107) 문구를 정정해 이후 검증 단계가 존재하지 않는 산출물을 찾지 않도록 함

## Task Commits

Each task was committed atomically:

1. **Task 1: glossary에 배치(Phase 5) 용어 등록** - `27cb9c2` (docs)
2. **Task 2: Claude 재량 결정 5건 기록 + D-105·D-108 보강** - `6a78a44` (docs)
3. **Task 3: BATCH-03 문구 정정 + 에러코드 필요 여부 판정** - `62c87cb` (docs)

_Note: 이 플랜은 문서 전용이라 `feat`/`test` 커밋이 없다._

## Files Created/Modified
- `docs/glossary.md` - `## 배치 (Phase 5)` 섹션 신설, 배치 신규 개념 6종 등록
- `docs/decisions.md` - D-110~D-114 신규 5건 + D-105·D-108 보강 2건
- `docs/policies.md` - §4.3 확정 문구(discuss-phase에서 이미 작성, 미커밋 상태였던 것을 decisions.md와 함께 커밋해 정합을 맞춤)
- `.planning/REQUIREMENTS.md` - BATCH-01·BATCH-03 문구 정정
- `.planning/ROADMAP.md` - Phase 5 성공 기준 1·3, Note 정정

## Decisions Made

- **Task 2의 결정 A~E를 D-110~D-114로 번호 부여**: 플랜이 "실행 시점에 마지막 번호 확인 후 순차 부여"를 지시했고, `grep "^## D-" docs/decisions.md | tail`로 확인한 실제 마지막 번호가 D-109였으므로 D-110부터 이어 붙였다 (Phase 4에서 있었던 번호 순연 선례와 달리 이번은 그대로 순연 없이 진행됨).
- **docs/policies.md·docs/decisions.md를 같은 커밋(Task 2)에 묶음**: policies.md §4.3은 discuss-phase에서 이미 D-105~D-109 확정 문구로 갱신됐으나 git에 커밋되지 않은 상태였다. decisions.md의 D-105·D-108 보강과 논리적으로 짝을 이루는 내용이라 별도 커밋으로 쪼개지 않고 함께 커밋했다.
- **.planning/ROADMAP.md의 Plans 목록(discuss-phase 이후 plan-phase가 채운 9개 플랜 목록)도 Task 3 커밋에 포함**: 이 목록 자체는 이 플랜이 만든 게 아니라 이미 미커밋 상태로 존재했다. Task 3이 같은 파일의 성공 기준·Note를 수정하므로 파일 전체를 한 커밋으로 처리했다(플랜의 "Plans 목록 텍스트는 건드리지 말 것" 지시를 지켜 목록 자체는 무수정으로 유지).

## Deviations from Plan

None - plan executed exactly as written. `docs/requirements.md`·`docs/error-codes.md`는 플랜 지시대로 grep 확인 후 변경 없이 남겨두었다(배치 관련 문구 없음 / 신규 에러코드 필요 없음, D-106이 실패 경로를 흡수).

## Issues Encountered

None.

## User Setup Required

None - 문서 전용 플랜, 외부 서비스 설정 없음.

## Next Phase Readiness

- 05-02(V9 스키마·엔티티)가 이 플랜에서 확정한 이름(`BatchExecution` 등)·CHECK 완화 방식(D-110)·복귀 시각 컬럼(D-111)을 그대로 구현할 수 있다
- 05-03~05-09가 참조할 트랜잭션 경계(D-112)·실행 이력 스키마(D-113)·cron/API 경로(D-114)가 문서에 고정됐다
- `/gsd:verify-work`가 BATCH-03에 대해 존재하지 않는 구현물을 찾지 않는다 (REQUIREMENTS·ROADMAP 문구 정정 완료)
- Blocker 없음

---
*Phase: 05-batch*
*Completed: 2026-08-15*

## Self-Check: PASSED

All modified files and task commits (27cb9c2, 6a78a44, 62c87cb) verified present.
