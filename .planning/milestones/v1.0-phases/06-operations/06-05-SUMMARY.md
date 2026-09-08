---
phase: 06-operations
plan: 05
subsystem: database
tags: [tdd, domain-policy, jpa-query, attendance, pass, bigdecimal]

# Dependency graph
requires:
  - phase: 06-operations
    provides: "06-01이 확정한 출석 도메인 예외 5종의 ErrorCode(ATTENDANCE_*, EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE), 06-02의 attendance 패키지·Attendance 엔티티"
provides:
  - "attendance/AttendanceExceptions.kt — 출석 도메인 예외 5종(DomainException 상속, id 미보간)"
  - "attendance/EveningHalfDeductionPolicy.kt — 저녁반 0.5회 차감 판정(회비 우선·만료 임박순 한 장·거부), 순수 object"
  - "pass/PassRepository.existsActiveEveningMembership(memberId, classDate) — 수업날 기준 회비 유효성 조회"
affects: [06-06, 06-07, 06-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "판정 object(attendance→pass 단방향 의존)와 실행부(조건부 UPDATE)를 분리 — ReservationPassPolicy와 동일 구조를 저녁반 차감에도 반복 적용"
    - "회비 우선 판정처럼 '차감 없음'을 표현해야 하는 판정 함수는 Pass?(nullable)를 반환값으로 써서 별도 sealed 결과 타입 없이 호출부가 null 분기만으로 처리하게 한다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/attendance/AttendanceExceptions.kt
    - src/main/kotlin/com/goldwrestling/attendance/EveningHalfDeductionPolicy.kt
    - src/test/kotlin/com/goldwrestling/attendance/EveningHalfDeductionPolicyTest.kt
    - src/test/kotlin/com/goldwrestling/pass/PassRepositoryEveningMembershipTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/pass/PassRepository.kt

key-decisions:
  - "EveningHalfDeductionPolicy.selectCandidate는 ReservationPassPolicy.selectCandidate를 재사용하지 않고 별도로 둔다 — 던지는 예외가 InsufficientPassCountException(예약 잔여 부족 전용)이라 D-133이 요구한 EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE이 나오지 않는다"
  - "resolveDeduction(hasValidMembership, candidates)는 회비로 커버되는 경우 Pass? 중 null을 반환한다 — 별도 sealed 결과 타입을 만들지 않고 호출부(06-07)가 null 분기만으로 '차감 없음'을 처리하게 한다"
  - "existsActiveEveningMembership을 새 리포지토리 쿼리로 추가하되 findDeductionCandidates가 이미 확립한 D-066 종료일 포함 비교축(endDate >= :classDate)을 그대로 재사용한다 — 두 쿼리가 다른 비교식을 쓰면 종료일 당일 경계가 갈라진다"

requirements-completed: [ATTEND-02]

# Metrics
duration: ~25min
completed: 2026-08-18
---

# Phase 6 Plan 5: 저녁반 0.5회 차감 판정 Summary

**저녁반 0.5회 차감(회비 우선·만료 임박순 한 장·거부)을 TDD로 순수 Kotlin object에 고정하고, 수업날 기준 회비 유효성 조회를 실제 PostgreSQL로 실증**

## Performance

- **Duration:** ~25min
- **Completed:** 2026-08-18
- **Tasks:** 3/3 완료
- **Files modified:** 5 (전부 신규 생성, PassRepository.kt만 추가 메서드)

## Accomplishments
- `AttendanceExceptions.kt` — `AttendanceNotFoundException`·`AttendanceMemberNotReservedException`·`AttendanceClassTypeMismatchException`·`DuplicateAttendanceException`·`EveningAttendanceDeductionUnavailableException` 5종을 `PassExceptions.kt` 관례로 선언(id 미보간, D-133 근거 KDoc)
- `EveningHalfDeductionPolicy` — `HALF_SESSION`(`BigDecimal("0.5")`), `requireEveningSession`, `selectCandidate`, `resolveDeduction` 4개 함수로 회비 우선·만료 임박순 한 장 선택·거부 3규칙을 순수 Kotlin 단위테스트 5건으로 고정. `attendance` 패키지에 둬 `attendance→pass`/`attendance→schedule` 단방향 의존 유지(D-018)
- `PassRepository.existsActiveEveningMembership` — 회원의 `EVENING_MEMBERSHIP`이 수업날(`classDate`, 오늘 아님 — D-128) 기준으로 유효한지 조회. `endDate >= :classDate`는 `findDeductionCandidates`와 같은 비교축(D-066 종료일 포함)을 재사용해 통합테스트 7건(종료일 당일·다음날·시작일 이전·취소·타입 불일치·소급 입력)으로 실증
- RED→GREEN 2사이클 모두 컴파일 실패로 RED를 확인(`ReservationPassPolicyTest` 선례와 동일 관례), 이후 최소 구현으로 GREEN — 별도 REFACTOR 커밋 없음(중복 없음)
- 전체 회귀 `./gradlew ktlintFormat build` BUILD SUCCESSFUL — 805 tests, 0 failures

## Task Commits

1. **Task 1: AttendanceExceptions 5종을 DomainException 관례로 만든다** - `624af09` (feat)
2. **Task 2-RED: EveningHalfDeductionPolicy 실패 테스트** - `7d65740` (test)
3. **Task 2-GREEN: EveningHalfDeductionPolicy 구현** - `f6c13ec` (feat)
4. **Task 3-RED: existsActiveEveningMembership 실패 테스트** - `c995760` (test)
5. **Task 3-GREEN: existsActiveEveningMembership 구현** - `8d434b9` (feat)

_Task 1은 분기 없는 예외 클래스 선언이라 계획서에 명시된 대로 별도 RED/GREEN 없이 단일 커밋으로 처리했다 — 예외가 실제로 던져지는 경로의 테스트는 Task 2·3과 06-06~06-08이 담당한다(conventions §10.0 면제 판단, plan Task 1 action 섹션에 명시된 근거)._

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/attendance/AttendanceExceptions.kt` - 출석 도메인 예외 5종
- `src/main/kotlin/com/goldwrestling/attendance/EveningHalfDeductionPolicy.kt` - 저녁반 0.5회 차감 판정(순수 object)
- `src/test/kotlin/com/goldwrestling/attendance/EveningHalfDeductionPolicyTest.kt` - 단위테스트 5건(회비 우선·만료 임박순·거부·경계값)
- `src/main/kotlin/com/goldwrestling/pass/PassRepository.kt` - `existsActiveEveningMembership` 추가
- `src/test/kotlin/com/goldwrestling/pass/PassRepositoryEveningMembershipTest.kt` - 통합테스트 7건(신규 파일, 기존 `PassRepositoryTest` 무변경)

## Decisions Made
- `EveningHalfDeductionPolicy.selectCandidate`는 `ReservationPassPolicy.selectCandidate`를 호출하지 않고 별도로 구현했다 — 두 함수의 모양(정렬된 리스트에서 `firstOrNull`)은 같지만 비어 있을 때 던지는 예외 타입이 달라야 한다(D-133). 재사용 대신 KDoc에 그 이유를 명시해 다음 사람이 "왜 안 합치나" 궁금해하지 않게 했다
- `resolveDeduction`의 반환 타입을 `Pass?`로 정했다 — `hasValidMembership = true`일 때 "차감할 이용권이 없다"를 표현할 별도 결과 타입(sealed class 등)을 만들 수도 있었지만, 호출부(06-07)가 만들 로직이 "null이면 그냥 출석만 기록, non-null이면 그 Pass에서 0.5 차감"이라는 단순 분기라 과설계를 피했다
- `existsActiveEveningMembership`의 KDoc에 "이 쿼리의 classDate는 오늘이 아니라 수업날"이라는 D-128 경고를 `findDeductionCandidates`와 같은 문구로 반복해 남겼다 — 06-07이 이 쿼리를 배선할 때 실수로 오늘 날짜를 넘기는 것을 막기 위해서다

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 06-07(저녁반 출석 서비스·저장 실행부)이 이번에 만든 `EveningHalfDeductionPolicy.resolveDeduction`과 `PassRepository.existsActiveEveningMembership`·`findDeductionCandidates`(재사용, `requiredAmount = HALF_SESSION`)를 그대로 배선할 수 있다 — 새 판정 로직을 다시 만들 필요가 없다
- 실제 차감 실행(조건부 UPDATE + `PassTransaction` 기록, `Attendance.passTransaction` 연결)은 여전히 이 플랜 범위 밖이며 06-07이 담당한다
- `AttendanceExceptions.kt`의 5종 중 `AttendanceNotFoundException`·`AttendanceMemberNotReservedException`·`DuplicateAttendanceException`은 아직 던져지는 호출부가 없다 — 06-06~06-08이 실제 서비스 로직에서 사용한다

## TDD Gate Compliance

- Task 2: `test(06-05)` `7d65740` → `feat(06-05)` `f6c13ec` — RED/GREEN 순서 확인
- Task 3: `test(06-05)` `c995760` → `feat(06-05)` `8d434b9` — RED/GREEN 순서 확인
- REFACTOR 커밋 없음 — 두 구현 모두 최소 구현 이후 중복이 없어 별도 정리가 필요하지 않았다(ktlintFormat은 두 GREEN 커밋 전에 이미 무변경 확인)

---

## 이번에 쓴 기술

1. **판정(순수 함수)과 실행(조건부 UPDATE)의 분리**
   - **이 코드에서 왜 필요했는가**: `EveningHalfDeductionPolicy.resolveDeduction`은 "회비가 있는가, 없으면 어느 `Pass`에서 0.5를 뺄 것인가"만 계산하고 실제로 DB의 `remainingCount`를 바꾸지 않는다. `PassRepository`(회비 존재 조회·차감 후보 조회)와의 경계를 분명히 갈라둔 이유는, 04-06(`ReservationPassPolicy`)에서 이미 검증된 구조를 그대로 반복하기 위해서다 — 판정 로직에 스프링 컨텍스트나 DB가 필요 없으면 밀리초 단위 단위테스트로 회비 우선·만료 임박순·거부 3규칙을 전부 고정할 수 있다. 실제 차감(원자적 조건부 UPDATE + `PassTransaction` 이력)은 06-07이 담당한다.
   - **안 썼으면 뭐가 깨지는가**: 판정과 실행이 한 서비스 메서드에 섞여 있었다면, "회비 우선"·"만료 임박순 한 장"·"거부" 세 규칙을 검증하려면 매번 Testcontainers로 실제 `Pass`를 DB에 저장하고 서비스를 호출해야 했을 것이다 — 테스트가 느려지고, 회귀가 생겼을 때 "판정이 틀렸는지 DB 반영이 틀렸는지"를 구분하기 어려워진다.

2. **★ nullable 반환 타입으로 "해당 없음"을 표현하기 (`Pass?`)**
   - **이 코드에서 왜 필요했는가**: `resolveDeduction`은 두 가지 결과가 가능하다 — "차감할 이용권이 있다"(어떤 `Pass`를 반환) 또는 "회비로 이미 커버돼 차감할 필요가 없다"(아무것도 반환하지 않음). Kotlin은 `null`을 타입 시스템이 강제하는 값으로 다룬다 — 함수 시그니처가 `Pass?`라고 선언하면, 이 함수를 호출하는 쪽(06-07)은 컴파일러가 `null` 처리를 빼먹으면 컴파일이 안 되게 만들어준다(`?.`나 `if (result == null)` 분기를 강제). Java였다면 이 자리에 `null`을 그냥 반환해도 컴파일러가 아무 말도 안 해서, 호출부가 null 체크를 잊으면 런타임에 `NullPointerException`이 난다.
   - **안 썼으면 뭐가 깨지는가**: 별도의 "결과 없음" 신호(예외를 던지거나 매직 값)를 썼다면, "회비가 있어서 차감 안 함"이라는 정상 흐름을 예외로 표현하는 오남용이 되거나(예외는 예외적 상황에만), 호출부가 그 신호를 놓쳐 회비가 있는데도 `SESSION_PASS`를 잘못 차감하는 버그로 이어질 수 있었다.

3. **비교축을 여러 쿼리에서 반복 재사용해 경계값 버그를 막기**
   - **이 코드에서 왜 필요했는가**: "유효기간의 종료일 당일까지는 사용 가능하다"(D-066)는 규칙을 SQL로 표현하는 방법은 `endDate >= :date`와 `endDate > :date` 두 가지가 있고, 어느 것을 고르느냐에 따라 종료일 당일 결과가 정반대로 뒤집힌다. `existsActiveEveningMembership`은 이미 `findDeductionCandidates`가 확립해 둔 `endDate >= :classDate`를 그대로 복사해 썼다 — 같은 프로젝트 안에 이 규칙을 표현하는 방식이 두 가지가 생기지 않도록.
   - **안 썼으면 뭐가 깨지는가**: 만약 이번에 `endDate > :classDate`로 다르게 썼다면, "회비 종료일 당일 저녁반에 참여한 회원"이 이 쿼리에서는 회비가 없는 것으로 오판되고, 그 결과 `SESSION_PASS`에서 잘못 0.5회가 차감되는 실제 금전적 손해로 이어졌을 것이다 — 테스트("수업날이 종료일 당일이면 true")가 없었다면 배포 후에나 발견됐을 버그다.

## Self-Check: PASSED

- FOUND: `src/main/kotlin/com/goldwrestling/attendance/AttendanceExceptions.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/attendance/EveningHalfDeductionPolicy.kt`
- FOUND: `src/test/kotlin/com/goldwrestling/attendance/EveningHalfDeductionPolicyTest.kt`
- FOUND: `src/main/kotlin/com/goldwrestling/pass/PassRepository.kt`
- FOUND: `src/test/kotlin/com/goldwrestling/pass/PassRepositoryEveningMembershipTest.kt`
- FOUND: commit `624af09` (Task 1)
- FOUND: commit `7d65740` (Task 2-RED)
- FOUND: commit `f6c13ec` (Task 2-GREEN)
- FOUND: commit `c995760` (Task 3-RED)
- FOUND: commit `8d434b9` (Task 3-GREEN)

---
*Phase: 06-operations*
*Completed: 2026-08-18*
