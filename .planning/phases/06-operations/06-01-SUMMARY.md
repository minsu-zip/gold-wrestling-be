---
phase: 06-operations
plan: 01
subsystem: docs
tags: [error-handling, domain-glossary, decisions-log, kotlin-enum]

# Dependency graph
requires:
  - phase: 05-batch
    provides: pass_transaction 원장 스키마(V4/V9), TransactionReason 닫힌 enum, ErrorCode 레지스트리 패턴(D-028)
provides:
  - "docs/glossary.md '운영 (Phase 6)' 섹션 — AttendanceStatus, checkedBy/checkedAt, passTransaction 연결, ActivityFeed, unreadCount, markAllAsRead, 공지 title/content"
  - "docs/policies.md §4.2 — 저녁반 출석 추가·0.5회 차감 원자 결합, 회비 우선, 거부, 자동 복구 규칙"
  - "docs/decisions.md D-132~D-135 — 출석 건별 upsert, EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE 신설, 공지 열람 게이트 미적용, 활동 피드 인덱스 V11 예고"
  - "ErrorCode 6종 + docs/error-codes.md '운영 코드 (Phase 6)' 표 1:1 매칭"
  - "TransactionReason.EVENING_HALF_REFUND"
affects: [06-02, 06-03, 06-04, 06-05, 06-06, 06-07, 06-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "닫힌 enum + VARCHAR(N)·DB CHECK 없음 조합은 마이그레이션 없이 enum 상수 확장만으로 안전하게 새 값을 저장할 수 있다(PassTransaction.reason)"
    - "ErrorCode enum ↔ docs/error-codes.md 표 1:1 매칭 계약(D-028) — 신규 예외마다 같은 커밋에서 둘 다 갱신"

key-files:
  created: []
  modified:
    - docs/glossary.md
    - docs/policies.md
    - docs/decisions.md
    - docs/error-codes.md
    - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
    - src/main/kotlin/com/goldwrestling/pass/TransactionReason.kt

key-decisions:
  - "D-132: 출석 체크 API는 타임별 일괄 저장이 아니라 회원 건별 upsert"
  - "D-133: 저녁반 차감 불가는 EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE(409) 신설, INSUFFICIENT_PASS_COUNT 재사용 기각"
  - "D-134: 공지 열람에 MemberStateGate(requireActive) 미적용(D-071 연장)"
  - "D-135: 활동 피드용 idx_notification_occurred_at(occurred_at DESC)를 V11에 함께 추가 예고"

patterns-established:
  - "이후 9개 플랜은 이번에 확정된 이름(AttendanceStatus, ActivityFeed 등)과 6개 에러코드를 그대로 재사용한다 — 새로 짓지 않는다"

requirements-completed: [ATTEND-01, ATTEND-02, NOTICE-01, NOTIF-03]

# Metrics
duration: ~15min
completed: 2026-08-18
---

# Phase 6 Plan 1: 운영 계약 확정 Summary

**Phase 6(출석·공지·알림)이 쓸 이름·정책 문구·에러코드를 코드보다 먼저 docs/에 확정 — 새 로직 0줄, ErrorCode 6종·TransactionReason 1종만 추가**

## Performance

- **Duration:** ~15min
- **Completed:** 2026-08-18
- **Tasks:** 2/2 완료
- **Files modified:** 6

## Accomplishments
- `docs/glossary.md`에 "운영 (Phase 6)" 섹션 추가 — `AttendanceStatus`, `checkedBy`/`checkedAt`, `passTransaction`(출석↔차감 연결), `ActivityFeed`, `unreadCount`, `markAllAsRead`, 공지 `title`/`content` 8개 용어 확정. 차감 사유 표에 `EVENING_HALF_REFUND` 행 추가
- `docs/policies.md` §4.2에 저녁반 출석 추가↔0.5회 차감의 원자 결합, 회비 우선, `SESSION_PASS` 차감 기준일(수업날), 잔여 부족 시 거부, 삭제 시 자동 복구 규칙 명문화 (§6은 변경하지 않음 — D-127 반영 완료 상태 유지 확인)
- `docs/decisions.md`에 D-132(출석 건별 upsert)~D-135(활동 피드 인덱스 V11 예고) 4건 기록
- `ErrorCode` enum에 `ATTENDANCE_NOT_FOUND`·`ATTENDANCE_MEMBER_NOT_RESERVED`·`ATTENDANCE_CLASS_TYPE_MISMATCH`·`DUPLICATE_ATTENDANCE`·`EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`·`NOTICE_NOT_FOUND` 6종 추가, `docs/error-codes.md` "운영 코드 (Phase 6)" 표와 1:1 매칭
- `TransactionReason.EVENING_HALF_REFUND` 추가 — 마이그레이션 없이 컴파일 확인(`pass_transaction.reason`은 VARCHAR(30), DB CHECK 없음)

## Task Commits

1. **Task 1: glossary·policies §4.2·decisions에 Phase 6 용어와 재량 결정 4건 기록** - `859efcc` (docs)
2. **Task 2: ErrorCode 6종 + TransactionReason.EVENING_HALF_REFUND 추가, error-codes.md 1:1 매칭** - `9cd1c0d` (feat)

_두 태스크 모두 순수 문서·enum 상수 추가라 별도 TDD 사이클(RED/GREEN) 없음._

## Files Created/Modified
- `docs/glossary.md` - "운영 (Phase 6)" 섹션 8개 용어 + 차감 사유 표 1행 추가
- `docs/policies.md` - §4.2에 저녁반 0.5회 차감 원자 결합·회비 우선·거부·복구 규칙 6개 불릿 추가 (§6 불변)
- `docs/decisions.md` - D-132~D-135 4건 추가
- `docs/error-codes.md` - "운영 코드 (Phase 6)" 표(6행) 추가
- `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt` - 신규 상수 6종(각 `HttpStatus.NOT_FOUND`/`CONFLICT` 명시)
- `src/main/kotlin/com/goldwrestling/pass/TransactionReason.kt` - `EVENING_HALF_REFUND` 1종

## Decisions Made
- D-132~D-135는 `.planning/phases/06-operations/06-CONTEXT.md`에서 이미 사용자 확정된 내용을 `docs/decisions.md` 형식(제목/날짜·내용/이유/기각 대안)으로 옮겨 기록한 것 — 이번 플랜이 새로 재량 판단을 내리지 않았다
- Task 2는 `conventions.md §10.0` 면제 대상(enum 상수 나열·문서 표)이라 별도 테스트를 작성하지 않았다 — 각 코드가 실제로 던져지는 경로의 테스트는 06-04~06-08이 담당한다(계획서에 명시된 면제 근거를 그대로 따름)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 이후 attendance/notice 스키마·API를 만드는 플랜(06-02~06-08 추정)이 이름을 새로 짓거나 에러코드를 임의로 고르지 않고 이번 계약(용어·정책 문구·에러코드 6종)을 그대로 재사용할 수 있다
- `docs/decisions.md` 마지막 항목은 D-135, `TransactionReason` 마지막 상수는 `EVENING_HALF_REFUND`, `ErrorCode` 마지막 상수는 `NOTICE_NOT_FOUND` — 다음 플랜이 이어붙일 지점이 명확하다
- 마이그레이션 파일은 여전히 10개(V11부터 attendance/notice 스키마가 이어받는다)

---

## 이번에 쓴 기술

1. **닫힌 enum + `VARCHAR(N)` + DB CHECK 없음 = 마이그레이션 없는 값 확장**
   - **이 코드에서 왜 필요했는가**: `TransactionReason`은 `pass_transaction.reason` 컬럼(`VARCHAR(30)`)에 문자열로 저장되는 Kotlin enum이다. 이 컬럼에는 DB `CHECK (reason IN (...))` 같은 값 제약이 걸려 있지 않다 — 그래서 `EVENING_HALF_REFUND`라는 새 사유를 추가할 때, "커밋된 마이그레이션은 수정 금지"(conventions §9) 규칙에도 불구하고 새 마이그레이션 파일을 만들 필요가 없었다. enum에 상수 한 줄만 추가하면 컴파일 시점에 코드가 검증하고, DB는 그 문자열을 그냥 저장할 뿐이다.
   - **안 썼으면 뭐가 깨지는가**: 만약 `reason` 컬럼에 `CHECK (reason IN ('RESERVE', 'CANCEL_REFUND', ...))`처럼 값 목록이 하드코딩된 DB 제약이 있었다면, enum에 새 값을 추가해도 DB가 그 값의 INSERT를 거부해 런타임 에러가 났을 것이고, 이번처럼 문서·enum만 고쳐서 끝낼 수 없었을 것이다(새 버전 마이그레이션으로 CHECK 재정의가 필요).

2. **에러코드 레지스트리 = enum과 문서 표의 1:1 계약 (D-028)**
   - **이 코드에서 왜 필요했는가**: 이 프로젝트는 `ProblemDetail` 표준 응답에 커스텀 `code` 필드(예: `EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE`)를 실어 FE가 그 문자열 하나로만 분기하게 만들었다(D-028). `ErrorCode` enum에 상수를 추가하는 것과 `docs/error-codes.md` 표에 행을 추가하는 것을 **같은 커밋에서 짝지어** 했는데, 이렇게 하지 않으면 "코드에는 있는데 FE 계약 문서에는 없는" 또는 그 반대 상황이 생긴다.
   - **안 썼으면 뭐가 깨지는가**: FE 개발자(FE 레포)가 `docs/error-codes.md`만 보고 어떤 `code` 값이 올 수 있는지 타입을 만드는데, 이 표에 없는 코드가 실제 응답에 나오면 FE가 그 케이스를 처리하지 못하고 기본 처리(알 수 없는 에러)로 떨어진다. 반대로 표에는 있는데 코드에 없으면 죽은 문서가 된다.

## Self-Check: PASSED

- FOUND: `.planning/phases/06-operations/06-01-SUMMARY.md`
- FOUND: commit `859efcc` (Task 1)
- FOUND: commit `9cd1c0d` (Task 2)
- FOUND: `docs/glossary.md`, `docs/policies.md`, `docs/decisions.md`, `docs/error-codes.md`
- FOUND: `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt`, `src/main/kotlin/com/goldwrestling/pass/TransactionReason.kt`
