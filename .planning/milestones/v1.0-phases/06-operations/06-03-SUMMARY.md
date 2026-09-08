---
phase: 06-operations
plan: 03
subsystem: batch
tags: [jpa, spring-batch-runner, attendance, testcontainers]

# Dependency graph
requires:
  - phase: 06-operations
    provides: "06-02이 만든 attendance.AttendanceRepository.findLastAttendedClassDates 벌크 조회"
provides:
  - "InactivityBatchRunner의 미사용 차감 기준일 후보 ①(마지막 출석일) 실배선 — D-105 5종 max 완결"
  - "출석 반영·불참 무시·소급 불가역 3가지를 고정하는 회귀 테스트(InactivityBatchRunnerTest)"
affects: [06-operations, 05-batch]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "배치 벌크 조회는 MemberDateProjection 같은 스칼라 프로젝션 인터페이스로 받고 associate로 Map화한다(D-115 관례 재사용, 새 패턴 아님)"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt
    - src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt

key-decisions:
  - "InactivityDueDateCalculator·InactivityDueDateCandidates는 계획대로 무변경 — Phase 5가 이미 5종 후보 시그니처를 갖춰 뒀으므로 이 플랜은 배선 한 지점만 바꾼다"
  - "회귀 테스트는 저녁반(EVENING) 전용 SESSION_PASS 회원 시나리오를 기본값으로 쓴다 — CR-03이 실제로 부당 차감을 냈던 시나리오(예약 없이 저녁반만 출석)를 그대로 재현한다. ABSENT 테스트만 명시적으로 SESSION 세션을 쓴다 — 저녁반은 ABSENT 상태가 존재하지 않는다(D-127)"
  - "3개 신규 테스트는 반복 실행(repeat 3)으로 캐치업 결과를 비교한다 — 1회 실행 상한(maxDeductionsPerRun=1)이 한 번의 run()에서는 출석 유무와 무관하게 항상 최대 1회만 차감하므로, 단일 실행 비교로는 기준일 차이가 드러나지 않는다"

requirements-completed: [ATTEND-01]

# Metrics
duration: ~30min
completed: 2026-08-18
---

# Phase 6 Plan 3: 배치 CR-03 클로저 — 미사용 차감 기준일 출석 배선 Summary

**InactivityBatchRunner의 `lastAttendanceDate = null` 하드코딩을 `AttendanceRepository.findLastAttendedClassDates` 벌크 조회로 교체해 미사용 차감 기준일 5종 max(D-105)를 완결하고, 출석 반영·불참 무시·소급 불가역 3가지를 회귀 테스트로 고정했다**

## Performance

- **Duration:** ~30min
- **Completed:** 2026-08-18
- **Tasks:** 2/2 완료
- **Files modified:** 2

## Accomplishments
- `InactivityBatchRunner`가 대상 회원 목록에 대해 `attendanceRepository.findLastAttendedClassDates`를 정확히 1회 호출해 `InactivityDueDateCandidates.lastAttendanceDate`를 실제 값으로 채운다(N+1 없음, `if (memberIds.isNotEmpty())` 블록 내부)
- `InactivityDueDateCalculator`·`InactivityDueDateCandidates`는 계획대로 무변경 — `git diff`로 확인, Phase 5가 선반영한 시그니처를 그대로 재사용
- 저녁반에만 나오는 `SESSION_PASS` 회원이 출석 기록만으로 유예를 갱신받는지, 불참(`ABSENT`)이 기준일을 갱신하지 않는지, 소급 출석이 이미 확정된 `INACTIVITY` 차감을 되돌리지 않는지 3가지를 `InactivityBatchRunnerTest`에 회귀 테스트로 추가(총 18건, 0 failures)
- 이 플랜으로 CR-03(운영 배포가 `BATCH_INACTIVITY_SCHEDULER_ENABLED=false`로 묶여 있던 근거)이 코드 레벨에서 닫혔다 — 스케줄러 활성화 여부(운영 결정)는 이 플랜 범위 밖
- `./gradlew ktlintFormat && ./gradlew build` BUILD SUCCESSFUL — 전체 회귀 통과, `com.goldwrestling.batch.*` 전체(InactivityBatchRunnerTest 18 + InactivityDeductionServiceTest + InactivityDueDateCalculatorTest 26 + InactivityLeaveReturnTest 3 등) 0 failures

## Task Commits

1. **Task 1: InactivityBatchRunner에 AttendanceRepository를 주입하고 후보 ①을 실제 조회로 교체** - `20f0515` (fix)
2. **Task 2: 출석 기반 기준일 회귀 테스트 3종을 기존 배치 테스트에 추가** - `3b5daa8` (test)

_TDD 표시(`tdd="true"`)가 있었으나 Task 1은 러너의 배선 지점 한 곳을 하드코딩(`null`)에서 실제 조회로 교체하는 것이고, 계산기 자체(`InactivityDueDateCalculator`)는 Phase 5에서 이미 5종 후보를 다루도록 완성돼 있어 실패하는 새 단위테스트를 먼저 쓸 새 분기가 없다 — 대신 Task 2에서 통합테스트 3종으로 즉시 실증하는 순서로 진행했다(06-02와 동일한 판단, conventions §10.0 "리포지토리 커스텀 쿼리 = Testcontainers 통합테스트 필수"가 실질 적용 규칙)._

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt` - `attendanceRepository` 생성자 주입 + 후보 ① 실배선(`lastAttendedClassDates` 벌크 조회 결과 반영)
- `src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt` - 출석 반영·불참 무시·소급 불가역 3종 추가, `persistAttendance`/`persistClassSession(classType 파라미터화)` 헬퍼 추가, `@AfterEach` attendance 정리 순서 추가

## Decisions Made
- `InactivityDueDateCalculator`·`InactivityDueDateCandidates`는 계획대로 손대지 않았다 — Phase 5가 이미 5종 후보 필드를 갖춘 값 객체·순수 계산을 만들어 뒀으므로 이 플랜은 러너의 배선 한 지점만 바꾼다
- 신규 회귀 테스트는 CR-03이 실제로 문제가 됐던 시나리오(저녁반에만 출석하는 `SESSION_PASS` 회원)를 기본값으로 재현한다 — `persistAttendance` 헬퍼의 기본 `classType`을 `EVENING`으로 뒀다. `ABSENT` 테스트만 `SESSION`을 명시했다 — 저녁반은 `ABSENT` 상태가 존재하지 않는다는 D-127 제약을 테스트 픽스처도 지킨다
- 3개 테스트 모두 `repeat(3)`로 배치를 여러 번 실행해 캐치업 결과를 비교한다 — 1회 실행 상한(`maxDeductionsPerRun = 1`)이 한 번의 `run()`에서는 출석 유무와 무관하게 항상 최대 1회만 차감하므로, 단일 실행 비교로는 기준일 반영 여부가 드러나지 않는다(3회 반복으로 "등록일 기준 2회" vs "출석일 기준 1회"라는 차이가 실제로 나타난다)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- CR-03이 코드 레벨에서 닫혔다 — 미사용 차감 기준일 5종 max(D-105)가 완결됐고, 계산기·값 객체는 무변경으로 유지됐다
- `BATCH_INACTIVITY_SCHEDULER_ENABLED` 킬 스위치를 켜는 것은 여전히 별도의 운영 배포 결정이다(이 플랜은 그 조건을 코드로 해제할 수 있게 했을 뿐, 플래그 자체는 건드리지 않았다) — 사용자가 명시적으로 지시할 때 `.env`/배포 환경변수를 갱신한다
- 06-04~06-08(공지 API, 출석 서비스·컨트롤러·동시성)이 이번에 확정된 `AttendanceRepository`·`InactivityBatchRunner` 배선과 무관하게 독립적으로 진행 가능하다

---

## 이번에 쓴 기술

1. **배치 "1회 실행 상한"이 있는 시스템에서 반복 실행으로 캐치업 차이를 드러내기**
   - **이 코드에서 왜 필요했는가**: 이 배치는 하루 04:00에 한 번 돌고, 사고 확산을 막으려고 "한 번에 최대 1회만 차감"이라는 상한이 걸려 있다(D-119). 그래서 "출석 기록이 있으면 기준일이 최근으로 당겨져 차감이 준다"는 걸 확인하려고 배치를 **딱 한 번**만 돌리면, 출석이 있든 없든 어차피 1회로 잘려서 결과가 똑같이 보인다 — 상한이 차이를 가려버린다. 그래서 실제 운영처럼 배치를 여러 날(3번) 돌려서 "밀린 주기를 다음 실행이 이어받는" 캐치업 과정을 재현해야, 출석 있는 회원(총 1회)과 없는 회원(총 2회)의 차이가 눈에 보인다.
   - **안 썼으면 뭐가 깨지는가**: 단일 실행만 비교했다면 두 회원 모두 "1회 차감"으로 나와서 테스트는 통과하지만 사실은 아무것도 검증하지 못한 것이 된다 — 나중에 기준일 계산 로직이 완전히 고장 나도(예: 출석 데이터를 아예 안 읽어도) 이 테스트는 계속 초록불이었을 것이다.

2. **★ 벌크 프로젝션 결과를 회원 id로 미리 `Map`(associate)해 두고 반복문 안에서는 조회를 다시 하지 않기**
   - **이 코드에서 왜 필요했는가**: `runStarted`는 대상 회원 수백 명을 `for` 반복문으로 순회하며 회원별 기준일을 계산한다. 이 반복문 **안에서** `attendanceRepository.findLastAttendedClassDates(memberId)`처럼 회원 1명씩 다시 조회하면 회원 수만큼 쿼리가 나가는 N+1이 된다. 그래서 반복문 진입 **전에** 대상 회원 전체에 대해 쿼리를 1번만 날려 `Map<memberId, LocalDate?>`로 만들어 두고, 반복문 안에서는 이 맵에서 값만 꺼내 쓴다(`lastAttendedClassDates[memberId]`) — DB 왕복이 없는 순수 메모리 조회다.
   - **안 썼으면 뭐가 깨지는가**: 이미 기존 코드(예약·복귀·등록·가감 4종)가 이 패턴을 쓰고 있었는데, 이번에 출석만 예외로 반복문 안에서 개별 조회하도록 짰다면 대상 회원이 늘어날수록 배치 실행 시간이 선형으로 늘어나고, 이 저장소가 이미 겪은 "N+1로 매일 새벽 배치가 느려져 다음 실행과 겹치는" 유형의 사고로 이어질 수 있었다(06-02 SUMMARY가 같은 이유를 설명한 바로 그 위험).

*이번 작업은 이미 확립된 패턴(벌크 프로젝션 배선, 회귀 테스트 골격)을 한 지점에 적용한 것이라 새로 등장한 개념은 2번(★)뿐이고 나머지는 06-02·05-batch에서 이미 다룬 것의 재적용이다.*

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt (attendanceRepository 배선 확인)
- FOUND: src/test/kotlin/com/goldwrestling/batch/InactivityBatchRunnerTest.kt (신규 테스트 3종 확인)
- FOUND: commit `20f0515` (Task 1)
- FOUND: commit `3b5daa8` (Task 2)

---
*Phase: 06-operations*
*Completed: 2026-08-18*
