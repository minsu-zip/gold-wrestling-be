---
phase: 04-schedule-reservation
plan: 05
subsystem: api
tags: [kotlin, spring-boot, jpa, jdbc-native-upsert, concurrency, openapi]

# Dependency graph
requires:
  - phase: 04-schedule-reservation
    plan: "04-03"
    provides: "schedule/reservation 엔티티·리포지토리(ClassSessionRepository.insertIfAbsent 네이티브 upsert, findAllByClassDateBetween, ReservationRepository.findAllByClassSessionIdInAndStatus)"
  - phase: 04-schedule-reservation
    plan: "04-04"
    provides: "ReservationWindow.bookableWeek/viewableRange/isBookable/assertViewable, WeekRange"
provides:
  - "schedule/ClassSessionService.getOrCreate — 세션 실체화의 유일한 진입점(D-094), 04-07(예약 생성)·04-14(휴강)이 공통으로 쓴다"
  - "GET /api/members/me/schedule — 회원 주간 시간표 조회(SCHED-01·SCHED-02), FE 달력 화면의 데이터 소스"
  - "schedule/dto/{WeeklyScheduleResponse,DayScheduleResponse,ScheduleCellResponse} — 이후 예약 생성(04-07) 등이 참조할 셀 식별자(classScheduleId/classSessionId) 형태의 원본"
affects: ["04-07(예약 생성이 ClassSessionService.getOrCreate와 셀 식별자를 그대로 씀)", "04-11(관리자 스케줄 보드가 같은 그리드 계산 방향을 재사용)", "04-14(휴강 처리가 getOrCreate를 거쳐 세션을 실체화)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "get-or-create 서비스 진입점 단일화 — insertIfAbsent(네이티브 upsert)의 반환값을 보지 않고 항상 재조회하는 패턴을 서비스 계층 1곳에만 두어, 이후 플랜들이 각자 upsert를 부르지 않게 강제"
    - "시간표(존재 항상 보장) 기준 그리드 + 세션 맵 덮어쓰기 — 세션 유무와 무관하게 모든 타임이 셀로 나오는 조회 방향(D-094 역방향 설계 함정 회피)"
    - "조회 API의 N+1 방지 — 세션·본인 예약 조회를 각각 정확히 1회 배치 쿼리로 고정하고 Map으로 셀 루프에 공급"
    - "통합테스트에서 토큰은 실제 시각(Instant.now())으로 먼저 발급하고, 비즈니스 Clock은 그 이후에만 이동시킨다 — JwtDecoder는 주입된 Clock 빈이 아니라 실제 시스템 시각으로 만료를 검증하기 때문"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/schedule/ClassSessionService.kt
    - src/main/kotlin/com/goldwrestling/schedule/ScheduleService.kt
    - src/main/kotlin/com/goldwrestling/schedule/MemberScheduleController.kt
    - src/main/kotlin/com/goldwrestling/schedule/dto/WeeklyScheduleResponse.kt
    - src/main/kotlin/com/goldwrestling/schedule/dto/DayScheduleResponse.kt
    - src/main/kotlin/com/goldwrestling/schedule/dto/ScheduleCellResponse.kt
    - src/test/kotlin/com/goldwrestling/schedule/ClassSessionConcurrencyTest.kt
    - src/test/kotlin/com/goldwrestling/schedule/MemberScheduleControllerTest.kt
  modified:
    - docs/api/openapi.yaml

key-decisions:
  - "ScheduleCellResponse의 capacity는 세션이 있으면 session.capacity, 없으면 schedule.capacity를 쓴다 — D-094가 세션이 시간표 값을 복사해 보유하도록 정했으므로 두 값이 항상 같지만, '세션이 생기고 난 뒤의 실제 정원'을 우선한다는 의도를 코드로 명시"
  - "bookable 계산은 ReservationWindow.isBookable(date, startTime, now)를 그대로 재사용 — 예약 생성(04-07)이 쓸 assertBookable과 판정 로직을 공유해, 조회 화면의 버튼 상태와 실제 예약 가능 여부가 어긋나는 사고를 원천 차단"

requirements-completed: [SCHED-01, SCHED-02]

# Metrics
duration: ~30min
completed: 2026-08-07
---

# Phase 04 Plan 05: 세션 get-or-create + 회원 주간 시간표 조회 API Summary

**ClassSessionService.getOrCreate(D-094 세션 실체화 단일 진입점)와 GET /api/members/me/schedule(정기 시간표 기준 그리드 + 세션 덮어쓰기, N+1 없는 배치 조회)을 TDD로 구현하고 청크 A를 openapi.yaml 재생성으로 마감했다.**

## Performance

- **Duration:** 약 30분
- **Started:** 2026-08-07T16:51Z경 (컨텍스트 로딩 시작)
- **Completed:** 2026-08-07T17:05:56+09:00 (마지막 커밋)
- **Tasks:** 3/3
- **Files modified:** 9개 (신규 8 + openapi.yaml 재생성 1)

## Accomplishments

- `ClassSessionService.getOrCreate` — `insertIfAbsent`(네이티브 `ON CONFLICT DO NOTHING`)의 반환값을 보지 않고 항상 재조회하는 get-or-create를 서비스 계층 한 곳에 고정했다. 이후 04-07(예약 생성)·04-14(휴강)가 각자 upsert를 부르지 않고 이 서비스만 거치도록 KDoc으로 명문화 — 실체화 로직을 나중에 바꿀 때(공휴일 시각 오버라이드 등) 고칠 지점이 하나로 유지된다
- `ClassSessionConcurrencyTest` — 스레드 20개가 동시에 같은 `(schedule, date)`로 `getOrCreate`를 호출해도 예외 0건·반환 세션 id 전부 동일·DB 행 정확히 1개임을 실제 Postgres(Testcontainers)로 증명(T-04-22)
- `GET /api/members/me/schedule` — 정기 시간표(52행) 기준으로 요일×타임 그리드를 만들고, 그 주에 실체화된 세션이 있으면 값을 덮어쓰는 방향으로 구현. 예약이 없는 타임도 세션 행 없이 "0/정원"으로 표시되고(D-094), 저녁반은 노출되지만 `reservable=false`로 예약 대상이 아님을 표현(D-096)
- 조회 성능: `findAllByClassDateBetween`·`findAllByClassSessionIdInAndStatus`를 각각 정확히 1회만 호출해 Map으로 만들고 셀 루프 안에서는 그 Map만 참조 — 7일×26타임(182칸)에 대해 리포지토리 호출이 셀당 발생하는 걸 막았다(T-04-21)
- `bookable` 단일 필드로 FE의 예약 버튼 활성화 판단을 통합했다 — `reservable && !suspended && ReservationWindow.isBookable(...)`. `isBookable`은 04-04가 만든 판정 함수를 그대로 재사용해 예약 생성(04-07)의 마감 판정과 경계가 어긋나지 않는다
- `MemberScheduleControllerTest` 10개 테스트로 `<behavior>` 절 전체(이번 주/다음 주 조회, weekStart 검증 2종, 미예약 셀, 저녁반 셀, 휴강 셀, 본인/타인 예약 표시, 정보 노출 범위, PENDING 회원 403)를 실제 Postgres+MockMvc로 검증
- 청크 A(wave 1~5) 마감: `openapi.yaml`을 재생성해 `GET /api/members/me/schedule`과 3개 DTO 스키마를 반영. V6~V8 마이그레이션, schedule/reservation/notification 3개 패키지, 예약 창 판정, 회원 시간표 조회 API, 세션 동시성 테스트가 모두 존재함을 확인

## Task Commits

Each task was committed atomically:

1. **Task 1: ClassSessionService.getOrCreate + 동시 생성 경쟁 테스트** - `4cf22fc` (feat)
2. **Task 2: 회원 주간 시간표 조회 API (GET /api/members/me/schedule)** - `beea671` (feat)
3. **Task 3: openapi.yaml 재생성 + 청크 A 마감 점검** - `8d89ed4` (docs)

**Plan metadata:** (본 커밋에 이어 별도 docs 커밋)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/schedule/ClassSessionService.kt` — get-or-create 진입점(D-094)
- `src/test/kotlin/com/goldwrestling/schedule/ClassSessionConcurrencyTest.kt` — 스레드 20개 get-or-create 경쟁 테스트
- `src/main/kotlin/com/goldwrestling/schedule/ScheduleService.kt` — 정기 시간표 기준 그리드 + 세션/본인 예약 배치 조회
- `src/main/kotlin/com/goldwrestling/schedule/MemberScheduleController.kt` — `GET /api/members/me/schedule`, `memberStateGate.requireActive` 호출
- `src/main/kotlin/com/goldwrestling/schedule/dto/{WeeklyScheduleResponse,DayScheduleResponse,ScheduleCellResponse}.kt` — 응답 DTO 3종(D-096 — 예약자 명단·회원명 없음)
- `src/test/kotlin/com/goldwrestling/schedule/MemberScheduleControllerTest.kt` — 컨트롤러 통합테스트 10개
- `docs/api/openapi.yaml` — `generateApiDocs`로 재생성, 신규 경로·스키마 반영

## Decisions Made

- `ScheduleCellResponse.capacity`는 세션이 있으면 세션의 값, 없으면 시간표의 값을 쓴다 — 계획서에 명시된 선택이며, D-094(세션이 시간표 값을 복사)에 따라 지금은 항상 같은 값이지만 "세션 실체화 이후에는 세션이 정답"이라는 의도를 코드로 남긴다.
- `bookable` 판정에 04-04가 만든 `ReservationWindow.isBookable`을 그대로 재사용했다 — PLAN.md가 `bookable = reservable && !suspended && 이번 주 && 아직 시작 전`으로 조건을 나열했지만, `isBookable`이 이미 "이번 주 + 아직 시작 전" 두 조건을 정확히 그 형태로 구현해 두었으므로 같은 로직을 다시 쓰지 않고 재사용했다(단순 구현 세부 사항, Rule 4 대상 아님).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] 컨트롤러 통합테스트의 시각 고정 방식이 최초 시도에서 인증 실패(401)를 유발**
- **Found during:** Task 2, `MemberScheduleControllerTest` 최초 실행
- **Issue:** PLAN.md는 "시각 고정은 `TestClockConfiguration`/`MutableTestClock`을 쓴다"고만 지시했다. 처음에는 다른 시각 의존 테스트들처럼 임의로 고른 특정 달력 날짜(예: 2026-08-03)로 `Clock`을 고정한 뒤 토큰을 발급했는데, `JwtDecoder`가 주입된 `Clock` 빈이 아니라 **실제 시스템 시각**으로 토큰 만료를 검증한다는 사실이 확인되어(`JwtAuthenticationFilterTest`의 기존 테스트가 이미 이 동작을 전제) 실제 "오늘"과 먼 날짜로 발급된 토큰이 즉시 만료 취급되어 10개 테스트 전부 401로 실패했다.
- **Fix:** 토큰을 먼저 실제 시각(`Instant.now()`)으로 발급한 뒤, 그 이후에만 비즈니스 `Clock`을 "이번 주 월요일 08:00"(실제 오늘이 속한 주를 `WeekRange.of`로 계산)으로 이동시키는 순서로 바꿨다. 이미 발급된 토큰은 실제 시스템 시각 기준으로 그대로 유효하게 남는다.
- **Files modified:** src/test/kotlin/com/goldwrestling/schedule/MemberScheduleControllerTest.kt
- **Verification:** `./gradlew test --tests "com.goldwrestling.schedule.MemberScheduleControllerTest"` 10개 전부 통과
- **Committed in:** `beea671` (Task 2 commit)

**2. [Rule 1 - Bug] 셀 조회 헬퍼가 `LocalTime` 직렬화 형식 불일치로 셀을 찾지 못함**
- **Found during:** Task 2, `MemberScheduleControllerTest` 최초 실행
- **Issue:** 테스트 헬퍼 `cellAt`이 `startTime.toString()`(`"11:00"`, 초 없음)과 JSON의 `startTime` 문자열을 그대로 비교했는데, Jackson(JSR-310)이 `LocalTime`을 `"11:00:00"`(초 포함)으로 직렬화해 모든 시각 매칭이 실패했다.
- **Fix:** 문자열 비교 대신 JSON 값을 `LocalTime.parse`로 역직렬화해 `LocalTime` 값끼리 비교하도록 수정했다.
- **Files modified:** src/test/kotlin/com/goldwrestling/schedule/MemberScheduleControllerTest.kt
- **Verification:** 동일 테스트 재실행 통과
- **Committed in:** `beea671` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (Rule 1 — 둘 다 테스트 코드 버그, 프로덕션 로직·스키마 변경 없음)
**Impact on plan:** 스코프 확장 없음. 두 편차 모두 테스트 인프라 사용 방식의 실수를 바로잡은 것으로, PLAN.md가 의도한 동작·범위는 그대로 유지된다.

## Issues Encountered

없음 — Boot 4 API·신규 의존성 확인이 필요한 작업이 없었다(기존 스택만 재사용).

## Testing Note (CLAUDE.md 규칙 10)

이 플랜의 프로덕션 코드(`ClassSessionService`, `ScheduleService`, `MemberScheduleController`, DTO 3종)는
전부 conventions §10.0 표의 "서비스 로직"·"엔드포인트" 행에 해당해 단위/통합테스트 필수 대상이다.
계획대로 두 사이클(Task 1: 동시성 테스트, Task 2: 컨트롤러 통합테스트) 모두 RED(실패 확인) → GREEN(구현)
순서로 테스트를 먼저 작성했다. DTO 3종은 필드만 있는 데이터 클래스이지만 응답 조립 로직(`ScheduleService`)의
통합테스트가 이미 이 DTO들의 직렬화 형태를 검증하므로 별도 단위테스트를 추가하지 않았다.

## User Setup Required

None - no external service configuration required. 로컬 검증은 `docker compose up -d` 후
`./gradlew ktlintFormat` → `./gradlew build` → `./gradlew generateApiDocs`로 전부 확인했다.

## Next Phase Readiness

- `ClassSessionService.getOrCreate`는 04-07(예약 생성)·04-14(휴강 처리)이 그대로 호출하면 된다 — 두 곳
  모두 각자 `insertIfAbsent`를 직접 부르지 않아야 한다는 것이 KDoc으로 명문화되어 있다
- `ScheduleCellResponse.classScheduleId`/`classSessionId`가 04-07의 예약 생성 요청이 받을 식별자
  형태의 기준이 된다 — 세션이 아직 없는 셀은 `classScheduleId`로 예약을 시도하게 될 것이다(04-07이
  이 식별자를 어떻게 받을지는 그 플랜에서 확정)
- 청크 A(wave 1~5)가 여기서 닫힌다 — `deliver-phase-chunk` 절차에 따른 브랜치 커밋·푸시·PR 생성은
  오케스트레이터가 담당한다(이 실행 에이전트는 PR을 생성하지 않았다)
- 블로커 없음

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-07*
