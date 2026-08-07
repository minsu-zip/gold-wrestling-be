---
phase: 04-schedule-reservation
plan: 08
subsystem: api
tags: [kotlin, spring-boot, jpa, postgresql, concurrency, reservation]

# Dependency graph
requires:
  - phase: 04-schedule-reservation (plan 04-07)
    provides: "MemberReservationService.reserve 예약 생성 트랜잭션 조립(정원·잔여 조건부 UPDATE + 이력 + 알림)"
provides:
  - "POST /api/members/me/reservations — 회원 예약 생성 API"
  - "RESV-06(정원·1:1 슬롯·중복 예약 3종 동시성)이 실제 PostgreSQL로 실증됨(D-098)"
affects: [04-09, 04-10, 04-11]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "회원 엔드포인트 첫 줄에서 MemberStateGate.requireActive 호출(D-040) — 서비스가 아니라 컨트롤러가 호출"
    - "동시성 테스트: ExecutorService+CountDownLatch, @Transactional 미부착, @AfterEach에서 JdbcClient로 FK 역순 정리(D-098)"
    - "컨트롤러 통합테스트에서 실제시각 토큰 발급 후 비즈니스 Clock을 이번 주 월요일 08:00으로 이동(JwtDecoder는 실제 시스템 시각으로 만료 검증)"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/reservation/MemberReservationController.kt
    - src/test/kotlin/com/goldwrestling/reservation/MemberReservationControllerTest.kt
    - src/test/kotlin/com/goldwrestling/reservation/ReservationCapacityConcurrencyTest.kt
  modified: []

key-decisions:
  - "409 5종 코드 구성에 CLASS_SESSION_CANCELED(휴강)를 추가 — acceptance_criteria가 요구한 '5종'을 behavior 목록(4종)만으로는 채울 수 없어 휴강 케이스를 추가 테스트로 채택"
  - "classScheduleId 누락 시 기대 코드를 VALIDATION_FAILED에서 MALFORMED_REQUEST로 정정 — Kotlin non-null 생성자 파라미터라 Jackson 역직렬화가 @Valid보다 먼저 실패(AdminPassControllerTest 선례와 동일)"

patterns-established:
  - "409 응답의 가능한 code 목록을 @Operation(description=...)에 나열 — 이 저장소는 @ApiResponse 애노테이션 전례가 없어 기존 관례(@Operation summary/description)를 그대로 확장"

requirements-completed: [RESV-01, RESV-02, RESV-06]

duration: ~20min
completed: 2026-08-08
---

# Phase 4 Plan 08: 예약 생성 API + 동시성 3종 실증 Summary

**POST /api/members/me/reservations 엔드포인트를 열고, 정원·1:1 슬롯·같은 회원 중복 3종 동시성을 ExecutorService+CountDownLatch로 실제 PostgreSQL 위에서 증명했다(RESV-06, D-098).**

## Performance

- **Duration:** ~20min
- **Tasks:** 2 completed
- **Files modified:** 3 (전부 신규 생성)

## Accomplishments

- 회원이 실제로 `POST /api/members/me/reservations`를 호출해 예약을 만들 수 있다 — 컨트롤러 첫 줄에서 `memberStateGate.requireActive(principal)`를 호출해 `PENDING`/`ON_LEAVE`/`INACTIVE` 회원을 차단한다(D-040)
- 5가지 실패 사유(정원초과·잔여부족·중복예약·예약창마감·휴강)가 각각 다른 `code`로 FE에 구분돼 내려간다
- "초과 예약 0건"(Core Value)이 세 시나리오(정원 10명 수업에 20명 동시 예약, 1:1 슬롯에 10명 동시 예약, 같은 회원의 같은 타임 10건 동시 요청)로 실제 동시 트랜잭션 하에 증명됐고, 매 시나리오마다 "성공 건수 = 활성 예약 행 수 = `RESERVE` 이력 건수 = 세션 `reservedCount`" 4자 일치(원장 불변식)를 단언했다

## Task Commits

Each task was committed atomically (TDD RED → GREEN):

1. **Task 1: POST /api/members/me/reservations 엔드포인트**
   - `3f13178` test(04-08): POST /api/members/me/reservations 실패 테스트 추가 (RED)
   - `5d5b83d` feat(04-08): POST /api/members/me/reservations 예약 생성 엔드포인트 구현 (GREEN)
2. **Task 2: 동시성 테스트 3종 — 초과 예약 0건 실증**
   - `72f2e45` test(04-08): 예약 정원·1대1 슬롯·중복 예약 동시성 3종 실증(RESV-06, D-098)
   - `97325da` style(04-08): ktlintFormat 적용 — 동시성 테스트 줄바꿈 정리

_Task 2는 04-07이 이미 구현한 `MemberReservationService.reserve`를 대상으로 하는 테스트 전용 태스크라 실패하는 RED 커밋 없이 test 커밋 하나로 완결했다 — 대상 프로덕션 코드가 이미 존재해 "실패하는 테스트"를 먼저 만들 대상 자체가 없다._

**Plan metadata:** (이 커밋, docs: complete plan)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/reservation/MemberReservationController.kt` — 회원 예약 생성 엔드포인트. `@Transactional` 미부착(D-020), 회원 상태 게이트 호출
- `src/test/kotlin/com/goldwrestling/reservation/MemberReservationControllerTest.kt` — 성공 1 + 형식검증 1 + 409 5종 + 403 2종(PENDING·관리자) + 401 1종, 총 10개 테스트
- `src/test/kotlin/com/goldwrestling/reservation/ReservationCapacityConcurrencyTest.kt` — 정원 경쟁(20→10 성공)·1:1 슬롯(10→1 성공)·같은 회원 중복(10→1 성공, 잔여 정확히 1회 차감) 3개 시나리오

## Decisions Made

- **409 코드 5종 구성**: 플랜의 `<behavior>` 목록은 4종(정원초과·잔여부족·중복예약·창마감)만 명시했지만 `<acceptance_criteria>`는 "409 응답 5종"을 요구했다. 컨트롤러 `<action>` 섹션이 "가능한 code가 5종"이라고 이미 언급하고 있어, 서비스가 던지는 다섯 번째 409(`ClassSessionCanceledException` → `CLASS_SESSION_CANCELED`, 휴강된 수업 예약 시도)를 테스트에 추가해 5종을 채웠다.
- **`classScheduleId` 누락 시 기대 코드 정정**: 플랜은 "400 ProblemDetail (jakarta validation)"이라고만 적었으나, `ReserveRequest.classScheduleId`가 Kotlin non-null `Long` 생성자 파라미터라 JSON에 그 키가 아예 없으면 Jackson 역직렬화 자체가 `@Valid` 검증 이전에 실패한다 — 이 저장소의 기존 확립된 관례(`AdminPassControllerTest`의 `note` 케이스)와 동일하게 `VALIDATION_FAILED`가 아니라 `MALFORMED_REQUEST`를 기대하도록 테스트를 정정했다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] 컨트롤러 통합테스트 RED 확인 중 발견한 테스트 자체의 시각 동기화 누락**
- **Found during:** Task 1 GREEN 검증 (컨트롤러 복원 후에도 9/10 테스트가 401로 실패)
- **Issue:** `MutableTestClock`이 싱글턴 빈이라 이전 테스트 클래스가 남긴 시각이 이어지는데, `@BeforeEach resetClock()`을 빠뜨려 `tokenAtThisWeekMonday`가 실제 이번 주가 아닌 엉뚱한 주를 계산했고, 발급된 JWT의 `issuedAt`/`expiresAt`도 실제 시스템 시각과 어긋나 전부 401(만료)로 실패했다
- **Fix:** `MemberScheduleControllerTest`·`AdminPassControllerTest`와 동일하게 `@BeforeEach fun resetClock() { (clock as MutableTestClock).setTo(Instant.now()) }`를 추가
- **Files modified:** `src/test/kotlin/com/goldwrestling/reservation/MemberReservationControllerTest.kt`
- **Verification:** 추가 후 10/10 테스트 통과
- **Committed in:** `5d5b83d` (Task 1 GREEN 커밋에 포함)

---

**Total deviations:** 1 auto-fixed (Rule 1 — 테스트 버그)
**Impact on plan:** 프로덕션 코드에는 영향 없음. 테스트 인프라 관례(이 저장소에 이미 확립된 패턴)를 놓쳤던 것을 발견 즉시 수정. 스코프 확장 없음.

## Issues Encountered

없음 — 계획대로 진행됐고, 위 1건은 작업 중 자체 발견·즉시 수정했다.

## Known Stubs

없음.

## Threat Flags

없음 — 이 플랜이 만든 새 표면(`POST /api/members/me/reservations`)은 이미 플랜의 `<threat_model>`(T-04-34~39)이 다루고 있고, 세 위협 모두 `mitigate`로 테스트가 실증했다(회원 상태 게이트, 역할 인가, 동시성 3종, 회원 주체 이력, 4자 일치 원장 불변식). `T-04-39`(409가 정원 정보를 노출)는 계획대로 `accept`로 남겨 뒀다.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `POST /api/members/me/reservations`가 실제로 동작하고, 이 phase의 Core Value("초과 예약 0건")가 실제 PostgreSQL·실제 동시 트랜잭션으로 증명됐다
- `openapi.yaml` 재생성은 이 플랜에서 하지 않았다 — 플랜의 `<verification>`이 명시한 대로 청크 B(feature/phase-04b-reservation)의 마지막 플랜인 04-10에서 이 phase의 API 변경분(04-08 포함)을 한 번에 재생성한다. 그전까지 FE는 이 엔드포인트의 계약을 openapi.yaml로 확인할 수 없다는 점을 인지해야 한다
- 다음 플랜(04-09는 이미 완료, 04-10 이후)이 취소·변경·관리자 조회 등을 이어서 만들 때 이 플랜의 컨트롤러·동시성 테스트 골격을 그대로 재사용할 수 있다

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-08*
