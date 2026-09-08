---
phase: 04-schedule-reservation
plan: 14
subsystem: api
tags: [kotlin, spring-boot, jpa, postgresql, reservation, schedule, notification]

# Dependency graph
requires:
  - phase: 04-schedule-reservation (04-11, 04-13)
    provides: AdminScheduleService/Controller 골격(스케줄 보드), ClassSession 조건부 UPDATE 4종, ReservationLedgerSupport 공유 복구 헬퍼
provides:
  - AdminScheduleService.suspend/resume (RESV-09 휴강 처리·해제)
  - POST /api/admin/class-sessions/suspension, .../{id}/resumption 엔드포인트
  - ReservationLedgerSupport.restorePassAfterCancellation — reason 파라미터화된 잔여 복구 공유 헬퍼
  - ClassSessionRepository.resetReservedCount, ReservationRepository.findAllByClassSessionIdAndStatusWithPass
affects: [04-15]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "N건 일괄 취소 캐스케이드에서 세션 정원은 건별 decrementReservedCount N회 대신 단일 resetReservedCount로 반영 — N번의 UPDATE·영속성 컨텍스트 clear를 피한다"
    - "차감/복구 공유 헬퍼(ReservationLedgerSupport)에 reason: TransactionReason 파라미터를 추가해 회원/관리자 취소(CANCEL_REFUND)와 휴강 복구(CLASS_CANCELED_REFUND)를 같은 실행부로 구분 — 세션 정원 반영이 포함된 restoreAfterCancellation(건당 1회)과, 반영하지 않는 restorePassAfterCancellation(N건 일괄, 호출부가 세션 정원을 별도로 갱신)으로 책임을 분리"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/schedule/dto/SuspendClassSessionRequest.kt
    - src/main/kotlin/com/goldwrestling/schedule/dto/ClassSessionResponse.kt
    - src/test/kotlin/com/goldwrestling/schedule/ClassSessionSuspensionTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/schedule/AdminScheduleService.kt
    - src/main/kotlin/com/goldwrestling/schedule/AdminScheduleController.kt
    - src/main/kotlin/com/goldwrestling/schedule/ClassSessionRepository.kt
    - src/main/kotlin/com/goldwrestling/schedule/ScheduleExceptions.kt
    - src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt
    - src/main/kotlin/com/goldwrestling/reservation/ReservationLedgerSupport.kt
    - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
    - docs/error-codes.md

key-decisions:
  - "ReservationLedgerSupport.restoreAfterCancellation를 restorePassAfterCancellation(세션 정원 미반영 + reason 파라미터)로 감싸는 형태로 리팩터링 — 기존 회원/관리자 취소 호출부는 동작 불변(기본값 CANCEL_REFUND), 휴강 캐스케이드만 CLASS_CANCELED_REFUND로 호출"
  - "AdminScheduleController의 클래스 레벨 @RequestMapping을 /api/admin/schedule에서 /api/admin으로 넓히고 메서드마다 하위 경로(/schedule/board, /class-sessions/suspension, /class-sessions/{id}/resumption)를 붙임 — 플랜이 고정한 두 경로 계층(schedule vs class-sessions)을 한 컨트롤러 파일 안에서 표현하기 위한 최소 변경"
  - "ClassSessionNotFoundException + ErrorCode.CLASS_SESSION_NOT_FOUND 신설(Rule 2) — 휴강 해제 대상 세션 id가 존재하지 않는 경로를 plan이 명시하지 않았으나 404 처리 없이는 방어적이지 않은 API가 된다"

requirements-completed: [RESV-09, NOTIF-01]

# Metrics
duration: 55min
completed: 2026-08-08
---

# Phase 04 Plan 14: 휴강 처리와 휴강 해제 Summary

**AdminScheduleService.suspend/resume 구현 — 휴강 시 활성 예약 N건을 일괄 취소·복구(CLASS_CANCELED_REFUND)하고 세션당 1건 알림만 남기며, 해제해도 취소된 예약은 복원하지 않는다**

## Performance

- **Duration:** ~55 min
- **Tasks:** 2
- **Files modified:** 10 (3 created, 7 modified)

## Accomplishments

- `AdminScheduleService.suspend` — 세션을 먼저 CANCELED로 전환해(T-04-66) 진행 중 새 예약을 차단하고, 활성 예약을 배치 조회(N+1 없음, pass fetch join)한 뒤 예약별로 취소·복구 판정(D-091)을 적용, `reserved_count`는 단일 UPDATE로 초기화, 알림은 세션당 정확히 1건(D-097)만 생성
- 휴강 복구 이력은 `CANCEL_REFUND`가 아니라 `CLASS_CANCELED_REFUND`로 원장에 남아 회원 취소와 구분됨(T-04-67)을 통합테스트로 실증
- `AdminScheduleService.resume` — 휴강 해제 시 취소 메타데이터 3종(취소시각·사유·주체)만 되돌리고, 취소됐던 예약은 절대 복원하지 않음을 명시적 테스트로 고정(CONTEXT.md 락인, T-04-70)
- 엔드포인트 2개(`POST /api/admin/class-sessions/suspension`, `.../{id}/resumption`) 추가, 세션 없는 타임도 (시간표, 날짜)로 휴강 가능(D-094)

## Task Commits

1. **Task 1: 휴강 처리 캐스케이드**
   - `15111b9` test(04-14): 휴강 처리 캐스케이드 통합테스트 추가 (RED)
   - `c5630b7` feat(04-14): 휴강 처리 캐스케이드 구현 (GREEN, RESV-09)
2. **Task 2: 휴강 해제 + 엔드포인트 2개**
   - `5c15d13` test(04-14): 휴강 해제 + 엔드포인트 통합테스트 추가 (RED)
   - `1f7dc5e` feat(04-14): 휴강 해제 + 엔드포인트 구현 (GREEN, RESV-09)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/schedule/dto/SuspendClassSessionRequest.kt` — 휴강 요청(시간표id+날짜+사유, D-094)
- `src/main/kotlin/com/goldwrestling/schedule/dto/ClassSessionResponse.kt` — 휴강 처리·해제 공용 응답, `canceledReservationCount`는 휴강 처리에서만 채워짐
- `src/main/kotlin/com/goldwrestling/schedule/AdminScheduleService.kt` — `suspend`/`resume` 추가
- `src/main/kotlin/com/goldwrestling/schedule/AdminScheduleController.kt` — 클래스 레벨 매핑을 `/api/admin`으로 넓히고 엔드포인트 2개 추가
- `src/main/kotlin/com/goldwrestling/schedule/ClassSessionRepository.kt` — `resetReservedCount`(단일 UPDATE로 0 초기화) 추가
- `src/main/kotlin/com/goldwrestling/schedule/ScheduleExceptions.kt` — `ClassSessionNotFoundException` 추가
- `src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt` — `findAllByClassSessionIdAndStatusWithPass`(배치 조회 + pass fetch join) 추가
- `src/main/kotlin/com/goldwrestling/reservation/ReservationLedgerSupport.kt` — `restorePassAfterCancellation`(reason 파라미터화된 잔여 복구 헬퍼) 추가, 기존 `restoreAfterCancellation`은 이를 위임 호출하도록 리팩터링
- `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt`, `docs/error-codes.md` — `CLASS_SESSION_NOT_FOUND` 추가
- `src/test/kotlin/com/goldwrestling/schedule/ClassSessionSuspensionTest.kt` — 통합테스트 18종(Task1 10종 + Task2 8종)

## Decisions Made

- `ReservationLedgerSupport.restoreAfterCancellation`를 그대로 휴강 캐스케이드에 재사용하지 않고 `restorePassAfterCancellation`(세션 정원 미반영 + `reason` 파라미터)으로 분리했다 — plan의 `<action>`이 명시한 두 요구(① 이력을 `CANCEL_REFUND`가 아니라 `CLASS_CANCELED_REFUND`로 남길 것, ② 세션 정원은 건별 `decrementReservedCount` N회가 아니라 단일 `resetReservedCount`로 반영할 것)가 기존 `restoreAfterCancellation`의 고정 동작(`reason=CANCEL_REFUND` 하드코딩, 호출마다 `decrementReservedCount` 1회 실행)과 직접 충돌해, 그대로 재사용하면 두 acceptance criteria를 동시에 만족할 수 없었다. 대신 잔여 판정·이력 저장 로직만 별도 메서드로 뽑아 회원/관리자 취소(기존 호출부, 기본값 `CANCEL_REFUND`)와 휴강(신규 호출부, `CLASS_CANCELED_REFUND`)이 공유하게 했다 — "복구 처리 공유 헬퍼를 재사용한다"는 interfaces 의도는 유지하면서 두 요구 모두 충족
- `AdminScheduleController`의 `@RequestMapping`을 `/api/admin/schedule`에서 `/api/admin`으로 넓혔다 — plan이 고정한 두 경로(`/api/admin/schedule/board`, `/api/admin/class-sessions/...`)가 서로 다른 리소스 계층이라 하나의 클래스 레벨 prefix 아래 하위 경로로 표현할 수 없었고, plan이 제시한 두 옵션(별도 컨트롤러 분리 vs 기존 컨트롤러에 `@PostMapping("/class-sessions/suspension")` 형태로 붙이기) 중 후자를 문자 그대로 따르려면 클래스 레벨 prefix를 좁혀야 했다
- 휴강 해제 대상 세션이 존재하지 않는 경로에 404(`ClassSessionNotFoundException`)를 추가했다(Rule 2) — plan의 `<action>`은 `assertResumable`/`resumeIfCanceled` 실패(409)만 다뤘고 세션 자체가 없는 경우의 응답을 명시하지 않았으나, `findById`를 그대로 두면 `NoSuchElementException`(500)으로 새 API가 노출되는 것을 막기 위해 필요했다

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - 누락된 방어 로직] `ClassSessionNotFoundException` + `ErrorCode.CLASS_SESSION_NOT_FOUND` 신설**
- **Found during:** Task 2 설계 단계
- **Issue:** `resume(adminId, classSessionId)`가 존재하지 않는 `classSessionId`를 받으면 `findById().orElseThrow`에 예외 타입이 지정되지 않아 500으로 노출될 위험이 있었다 — plan의 `<behavior>`가 409(`ClassSessionNotCanceledException`) 경로만 명시했다
- **Fix:** `schedule` 패키지에 `ClassSessionNotFoundException`(404, `classScheduleId`를 메시지에 보간하지 않는 기존 관례 그대로) 추가, `ErrorCode.CLASS_SESSION_NOT_FOUND` + `docs/error-codes.md` 동기화
- **Files modified:** `ScheduleExceptions.kt`, `ErrorCode.kt`, `docs/error-codes.md`, `AdminScheduleService.kt`
- **Verification:** `./gradlew build` 통과(전체 회귀 포함)
- **Committed in:** `1f7dc5e` (Task 2 GREEN 커밋)

---

**Total deviations:** 2 auto-fixed (1건 Rule 2 — 위 항목, 1건은 위 "Decisions Made"의 리팩터링 판단으로 별도 Rule 분류 없이 plan의 명시적 acceptance criteria 충돌을 해소하기 위한 필수 설계)
**Impact on plan:** 둘 다 plan이 요구한 정확성·방어성을 충족하기 위한 작업. 범위 확장(scope creep) 아님.

## Issues Encountered

- **TDD RED/GREEN 커밋 분리 시 테스트-프로덕션 코드 상호 의존**: 두 task가 같은 테스트 파일(`ClassSessionSuspensionTest.kt`)을 공유하도록 plan이 설계했는데(Task1이 파일 생성, Task2가 이어서 추가), Task1의 `suspend` 구현과 Task2의 `resume`/컨트롤러 엔드포인트가 서로 얽혀 있어(컨트롤러가 `resume`을 호출) RED/GREEN을 정확히 task 경계로 나누려면 컨트롤러·예외·에러코드 변경을 일시적으로 되돌렸다가 Task2 GREEN 커밋 직전에 복원하는 절차가 필요했다. 최종 커밋 히스토리는 04-13의 실제 선례(`test`→`feat` 순서, RED 커밋이 아직 없는 프로덕션 심볼을 참조해 컴파일 실패하는 것을 RED로 인정)와 동일한 패턴을 따른다
- **`ReservationLedgerSupport.createReservation`을 서비스 계층 밖에서 직접 호출하는 테스트**: "휴강 해제 후 새 예약 성공" 테스트가 `MemberReservationService.reserve`(예약 창 제약)를 우회하려고 헬퍼를 직접 호출했는데, 이 헬퍼는 `@Transactional`을 열지 않아(호출부의 트랜잭션에 편승하는 설계) `@Modifying` 조건부 UPDATE가 `TransactionRequiredException`으로 실패했다 — `TransactionTemplate`으로 테스트에서 명시적 트랜잭션을 열어 해결(SUT 코드는 변경 없음)

## User Setup Required

None - no external service configuration required.

**openapi.yaml 재생성 보류**: 이 플랜에서 API 표면이 바뀌었지만(엔드포인트 2개 + DTO 2종), plan의 `<verification>`이 "openapi.yaml 재생성은 다음 플랜(04-15)에서 한다"고 명시했다 — 04-15에서 `docker compose up -d && ./gradlew generateApiDocs`로 한 번에 갱신할 예정이다.

## Next Phase Readiness

- 휴강 한 번이 활성 예약 전부를 정확히 취소·복구하고 관리자 알림함은 세션당 1건만 받는다 — Phase 4의 예약 도메인 핵심 요구(RESV-09) 충족
- Phase 4의 시간표·예약 도메인 코드(schedule/reservation/notification/pass 4개 패키지)가 모두 구현 완료 — 04-15는 openapi.yaml 재생성 + PR 생성(D-084)만 남았다
- `ReservationLedgerSupport.restorePassAfterCancellation`이 이후 다른 일괄 취소 경로(있다면)에도 재사용 가능한 형태로 확보됨

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-08*

## Self-Check: PASSED
