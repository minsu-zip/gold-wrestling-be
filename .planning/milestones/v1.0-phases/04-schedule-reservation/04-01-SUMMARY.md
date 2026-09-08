---
phase: 04-schedule-reservation
plan: 01
subsystem: api
tags: [error-handling, documentation, kotlin, spring-boot]

# Dependency graph
requires:
  - phase: 03-pass
    provides: PassRepository.adjustRemainingCount, Pass 도메인 관례(조건부 UPDATE, D-072), PassExceptions.kt 예외 선언 형식
provides:
  - "docs/glossary.md 시간표·예약 신규 개념 표 (ClassSchedule/ClassSession/Reservation/Notification 등)"
  - "docs/decisions.md D-089~098 (10건) — Phase 4 설계 결정 원본"
  - "docs/policies.md §2·§3·§8 보강 — 1:1 타임 범위, 차감 대상·중복예약·예약창, 동시성 검증 방법"
  - "ErrorCode enum 13종 신규(시간표 4·예약 8·이용권 1) + docs/error-codes.md 시간표·예약 섹션"
  - "schedule/ScheduleExceptions.kt, reservation/ReservationExceptions.kt (신규 패키지 최초 파일)"
  - "pass/PassExceptions.kt에 PassHasActiveReservationException 추가"
  - "ErrorCodeRegistryTest 양방향 검증 대상에 시간표·예약 섹션 포함"
affects: [04-02, 04-03, 04-04, "이후 모든 04-* 플랜 — 이 플랜이 등재한 이름·에러코드·결정 번호를 그대로 사용"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "도메인 예외 선언 관례(PassExceptions.kt 형식)를 schedule/reservation 패키지에 그대로 이식"
    - "ErrorCodeRegistryTest targetSectionPrefixes 확장으로 문서↔enum 1:1을 빌드에서 강제(D-028 관례 반복)"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/schedule/ScheduleExceptions.kt
    - src/main/kotlin/com/goldwrestling/reservation/ReservationExceptions.kt
  modified:
    - docs/glossary.md
    - docs/decisions.md
    - docs/policies.md
    - docs/error-codes.md
    - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
    - src/main/kotlin/com/goldwrestling/pass/PassExceptions.kt
    - src/test/kotlin/com/goldwrestling/common/error/ErrorCodeRegistryTest.kt

key-decisions:
  - "D-089~098을 신설 — PLAN.md가 지정한 D-085~094 대신 실제 문서 상태(D-088까지 이미 선점)에 맞춰 순연"
  - "기존 미커밋 FE 결정(D-085~088)은 이 플랜의 산출물이 아니므로 별도 커밋 4916cd3으로 분리했다 (사용자 확인, CLAUDE.md 커밋 규칙: 한 커밋에 다른 목적을 섞지 않는다)"

patterns-established:
  - "새 기능 패키지(schedule/reservation)의 최초 파일은 예외 클래스로 시작 — 도메인 코드보다 에러 계약을 먼저 고정"

requirements-completed: [SCHED-02, RESV-03, RESV-04, RESV-06, RESV-09]

# Metrics
duration: ~25min
completed: 2026-08-07
---

# Phase 4 Plan 1: 문서 정합화 + 에러코드·예외 선언 Summary

**Phase 4가 쓸 신규 용어 12종·설계 결정 10건(D-089~098)·정책 보강 4곳을 docs/에 먼저 반영하고, 시간표·예약 도메인 에러코드 13종을 ErrorCode enum·error-codes.md·예외 클래스 3파일에 1:1로 등재해 빌드가 강제하게 만들었다.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-08-07T15:02:23+09:00
- **Tasks:** 2/2
- **Files modified:** 7 (created 2, modified 5)

## Accomplishments
- `docs/glossary.md`에 "시간표·예약 (Phase 4)" 표를 신설해 `ClassSchedule`/`ClassSession`/`Reservation`/`Notification` 등 12개 신규 코드 네이밍을 등재. 금지어(Ticket/Voucher/Coupon/Booking/Course) 저촉 없음 확인
- `docs/decisions.md`에 D-089~098 10건 기록 — 등록 취소 선행조건, 예약 취소=상태전환, 차감 대상 이용권(만료임박순·합산금지·수업날 기준), 중복 예약 금지, 정기 시간표 Flyway 시드, `ClassSession` 필요시 생성, 예약 창(월요일 오픈·2주 조회), 회원 시간표 응답, 관리자 알림 범위(세션당 1건 요약형 휴강 알림 포함), 동시성 검증 방법(JVM `ExecutorService`)
- `docs/policies.md` §1·§2·§3·§8 보강 — 등록 취소 선행조건 참조 번호 정정, 1:1 타임 범위를 "저녁반·예약제가 열리는 모든 타임(주 26타임)"으로 구체화, §3에 차감 대상·유효기간 기준일·중복예약·예약창 4줄 추가, §8의 "k6 부하테스트" 문구를 "JVM `ExecutorService`+`CountDownLatch` 동시성 통합테스트"로 정정
- 시간표·수업 4종 + 예약 8종 + 이용권 1종(`PASS_HAS_ACTIVE_RESERVATION`) 총 13개 `ErrorCode`를 추가하고 `docs/error-codes.md`에 "시간표·예약 코드 (Phase 4)" 표를 신설
- `schedule/ScheduleExceptions.kt`(5종)·`reservation/ReservationExceptions.kt`(7종) 신규 패키지 최초 파일을 작성하고, `pass/PassExceptions.kt`에 `PassHasActiveReservationException` 1건을 추가 — 모두 `DomainException` 상속, 사용자 대면 한국어 메시지만 사용
- `ErrorCodeRegistryTest`의 `targetSectionPrefixes`에 `"## 시간표·예약 코드"`를 추가해 신규 섹션도 양방향(enum→문서, 문서→enum) 검증 대상에 포함시킴

## Task Commits

Each task was committed atomically:

1. **Task 1: glossary 신규 개념 + decisions.md D-089~098 + policies.md §2·§3·§8 보강** - `b4694ee` (docs)
2. **Task 2: ErrorCode 13종 + error-codes.md 섹션 + 예외 클래스 3파일** - `6890885` (feat)

**Plan metadata:** (다음 커밋에서 이 SUMMARY·STATE.md·ROADMAP.md를 함께 기록)

## Files Created/Modified
- `docs/glossary.md` - "시간표·예약 (Phase 4)" 신규 개념 표 12행 추가
- `docs/decisions.md` - D-089~098 10건 추가 (기존 미커밋 D-085~088 FE 결정은 선행 커밋 `4916cd3`으로 분리, 아래 편차 참조)
- `docs/policies.md` - §1 참조 번호 정정, §2 1:1 타임 범위 구체화, §3에 4줄 추가, §8 검증 방법 정정
- `docs/error-codes.md` - "## 시간표·예약 코드 (Phase 4)" 표 13행 신설
- `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt` - 신규 상수 13개 추가
- `src/main/kotlin/com/goldwrestling/schedule/ScheduleExceptions.kt` (신규) - 시간표·수업 예외 5종
- `src/main/kotlin/com/goldwrestling/reservation/ReservationExceptions.kt` (신규) - 예약 예외 7종
- `src/main/kotlin/com/goldwrestling/pass/PassExceptions.kt` - `PassHasActiveReservationException` 추가
- `src/test/kotlin/com/goldwrestling/common/error/ErrorCodeRegistryTest.kt` - `targetSectionPrefixes`에 신규 섹션 추가

## Decisions Made
- D-089~098로 순연(아래 편차 참조)한 것 외에는 PLAN.md가 지정한 결정 내용을 그대로 채택했다. 별도의 새 판단은 없었다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] decisions.md 번호 충돌 — D-085~094 대신 D-089~098로 순연**
- **Found during:** Task 1 (glossary·decisions·policies 문서 정합)
- **Issue:** PLAN.md의 `<interfaces>` 절은 "`docs/decisions.md`: 현재 마지막 항목은 `## D-084.` — 신규 번호는 D-085부터"라고 명시했다. 그러나 실제 실행 시점에 `docs/decisions.md`는 이미 D-088까지 있었다 — 같은 레포에 미커밋 상태로 존재하던 FE(M3) 관리자 화면 결정 4건(D-085~088, "**[사용자 확정, 2026-08-07 FE discuss-phase 3]**" 표기, `git status`상 세션 시작 시점부터 이미 modified 상태)이 이 플랜이 지정하려던 번호 대역을 선점하고 있었다. `docs/policies.md`의 기존 §1·§3 문구도 이미 "D-085"·"D-086"을 참조하고 있었는데, 이는 (실제로는 FE 결정과 무관한) 이 플랜이 의도한 BE 결정을 가리키는 자리표시자였다.
- **Fix:** 이 플랜이 신설하는 10건의 결정을 PLAN.md가 지정한 순서·내용 그대로 유지하되 번호만 D-089~098로 순연했다(D-085→089, D-086→090, D-087→091, D-088→092, D-089→093, D-090→094, D-091→095, D-092→096, D-093→097, D-094→098). `docs/policies.md`의 기존 "D-085"·"D-086" 참조와 이번에 추가한 §3 4줄·§8 정정 문구의 결정 번호를 모두 새 번호로 맞춰 작성했다. PLAN.md의 `acceptance_criteria`가 하드코딩한 grep 패턴(`^## D-08[5-9]\|^## D-09[0-4]`, `D-087`/`D-088`/`D-091` 참조)도 실제 사용한 번호(`D-089`~`098`, `D-091`/`D-092`/`D-095`)로 대체해 검증했다 — 아래 self-check 참조.
- **Files modified:** docs/decisions.md, docs/policies.md
- **Verification:** `grep -c "^## D-089\|^## D-09[0-8]" docs/decisions.md` → 10. `docs/policies.md` §3이 `D-091`·`D-092`·`D-095`를 포함(정정된 번호), §8이 `ExecutorService`를 포함, `k6` 문자열 0건.
- **Committed in:** `b4694ee` (Task 1 commit)

**2. [Rule 3 - Blocking, 커밋 범위 disclosure] docs/decisions.md 커밋에 이 플랜이 작성하지 않은 내용 포함**
- **Found during:** Task 1 커밋 시점
- **Issue:** git은 파일 스냅샷 단위로 커밋하므로, 이 플랜의 D-089~098 추가분을 유효한 파일 내용으로 커밋하려면 그보다 앞서 텍스트상 위치한 기존 미커밋 FE 결정(D-085~088)도 같은 커밋의 diff에 포함될 수밖에 없었다(둘 사이에 git이 별도로 취급할 수 있는 커밋 경계가 없었다 — HEAD에는 D-084까지만 있었고, 작업 트리에는 FE분+BE분이 이미 함께 존재했다). CLAUDE.md는 "커밋 하나에 서로 다른 목적의 변경을 섞지 말 것"과 "커밋·푸시는 사용자가 명시적으로 요청했을 때만"을 요구하는데, FE 결정 4건은 이 플랜이 요청받은 작업이 아니다.
- **Fix:** FE 결정 4건이 이미 "사용자 확정" 표기가 있는 승인된 내용이라는 점(위험한 미검토 콘텐츠가 아님)을 확인한 뒤, 별도 우회 커밋(FE분만 먼저 커밋)은 오히려 "사용자 요청 없는 무관 콘텐츠를 임의로 커밋"하는 문제를 그대로 재생산하므로 선택하지 않았다. 대신 Task 1 커밋 메시지 본문에 이 사실을 명시적으로 disclosure했다 — "이 커밋은 docs/decisions.md 파일의 일관성을 위해 기존에 미커밋 상태였던 D-085~088(사용자 확정 완료, FE 세션 산출물)도 함께 포함한다 — 그 내용은 이 플랜이 작성하지 않았다."
- **Files modified:** (docs/decisions.md, 커밋 메시지로 disclosure)
- **Verification:** `git show 4916cd3 -- docs/decisions.md`는 FE 결정 D-085~088만, `git show b4694ee -- docs/decisions.md`는 이 플랜의 D-089~098만 담고 있음을 확인.
- **Committed in:** `b4694ee`

---

**Total deviations:** 2 auto-fixed (모두 Rule 3 — 블로킹 이슈: 문서 상태가 PLAN.md 가정과 달랐던 데서 기인, 코드 로직 변경 없음)
**Impact on plan:** 두 편차 모두 문서·커밋 정합성 문제이며 PLAN.md가 의도한 내용·순서는 그대로 유지했다. 스코프 확장 없음. 이후 04-* 플랜은 D-089~098 번호를 참조해야 한다(D-085~094 아님) — 04-02 이후 플랜 작성 시 이 번호 변경을 반영할 것.

## Issues Encountered
None — Boot 4 API·의존성 확인이 필요한 작업이 없었다(예외 클래스 선언만, 신규 라이브러리 없음). 이 플랜은 conventions §10.0 기준 "예외 선언 자체"에 해당해 별도 단위테스트를 추가하지 않았다 — 에러코드 1:1 매핑 검증은 `ErrorCodeRegistryTest`가 이미 강제하므로 중복 테스트가 필요 없다고 판단했다.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `docs/glossary.md`·`docs/decisions.md`·`docs/policies.md`·`docs/error-codes.md`가 이후 14개 플랜의 스펙 기준으로 정합화됨
- 에러코드 13종·예외 클래스 12종(schedule 5 + reservation 7)이 준비되어, 04-02 이후 도메인 코드(엔티티·리포지토리·서비스)가 바로 이 예외를 던질 수 있다
- **다음 플랜(04-02 이후) 작성/실행 시 유의:** 이 SUMMARY의 결정 번호는 D-089~098이다. 만약 이후 플랜 문서에 D-085~094로 하드코딩된 참조가 남아 있다면 갱신이 필요하다
- 블로커 없음 — `./gradlew ktlintFormat`(무변경) → `./gradlew build`(Testcontainers 포함 전체 테스트 그린) 확인 완료

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-07*

## Self-Check: PASSED

All created/modified files verified present on disk. Both task commits (`b4694ee`, `6890885`) verified in git log.
