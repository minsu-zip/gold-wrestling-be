---
phase: 06-operations
plan: 08
subsystem: api
tags: [kotlin, spring-boot, jpa, attendance, concurrency, testcontainers, openapi]

# Dependency graph
requires:
  - phase: 06-operations
    provides: "06-06(AttendanceService.getRoster/check, DTO 5종), 06-07(AttendanceService.addEveningAttendance/delete — 저녁반 0.5회 차감·복구)"
provides:
  - "AdminAttendanceController — /api/admin/attendances 4종(GET 명단, PUT 체크, POST /evening 추가, DELETE 삭제)"
  - "AdminAttendanceControllerTest — 성공 4종 + 대표 실패 5종(ATTENDANCE_MEMBER_NOT_RESERVED·ATTENDANCE_CLASS_TYPE_MISMATCH·EVENING_ATTENDANCE_DEDUCTION_UNAVAILABLE·ATTENDANCE_NOT_FOUND·ACCESS_DENIED) HTTP 계약"
  - "AttendanceConcurrencyTest — 같은 회원×같은 저녁반 세션 10스레드 동시 요청에서 출석 1건·이력 1건·차감 0.5회만 반영됨을 실제 PostgreSQL로 실증(T-06-26)"
  - "docs/api/openapi.yaml — 출석 4개 경로 + DTO 5종 스키마 추가(기존 경로 전부 보존)"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "컨트롤러는 principal.requireAdminId() 위임 1줄로 서비스에 그대로 넘긴다(권한/트랜잭션/에러변환을 컨트롤러에 두지 않음, D-040·D-020·D-017) — AdminScheduleController와 동일 관례"
    - "동시성 테스트에서 '실패 9건이 모두 DuplicateAttendanceException'을 보장하는 것은 사전 존재 체크가 아니라 Spring 기본 롤백 규칙이다 — DomainException이 RuntimeException을 상속해, INSERT 유니크 위반으로 던진 예외가 같은 트랜잭션의 선행 0.5회 차감(조건부 UPDATE)까지 통째로 롤백시킨다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/attendance/AdminAttendanceController.kt
    - src/test/kotlin/com/goldwrestling/attendance/AdminAttendanceControllerTest.kt
    - src/test/kotlin/com/goldwrestling/attendance/AttendanceConcurrencyTest.kt
  modified:
    - docs/api/openapi.yaml

key-decisions:
  - "저녁반 회원 검색 전용 API는 만들지 않는다(06-RESEARCH Open Question 2 확정) — FE는 기존 GET /api/admin/members?keyword=로 memberId를 얻어 POST /evening에 넘긴다. POST /evening의 @Operation description에 이 흐름을 명시했다"

patterns-established:
  - "출석 컨트롤러 경로는 /api/admin/attendances 단일 계층 — 스케줄 보드처럼 여러 리소스 계층이 섞이지 않는 단일 리소스는 넓은 클래스 매핑이 필요 없다는 걸 코드로 보여준 두 번째 사례"

requirements-completed: [ATTEND-01, ATTEND-02]

# Metrics
duration: ~25min
completed: 2026-08-18
---

# Phase 06 Plan 08: 출석 관리자 API + 동시성 실증 + openapi.yaml 마감 Summary

**출석 명단 조회·예약제/1:1 체크·저녁반 추가·삭제 4개 REST 엔드포인트를 노출하고, 10스레드 동시 저녁반 출석 추가에서 이중 차감 0건을 실제 PostgreSQL로 실증한 뒤 openapi.yaml을 재생성해 출석 청크(06-06~06-08)를 마감**

## Performance

- **Duration:** ~25min
- **Started:** 2026-08-18T22:00:00+09:00 (파일 조사 시작 기준 역산)
- **Completed:** 2026-08-18T22:25:00+09:00
- **Tasks:** 3
- **Files modified:** 4 (신규 3, 수정 1)

## Accomplishments
- `AdminAttendanceController` — `GET /api/admin/attendances`(명단), `PUT /api/admin/attendances`(예약제/1:1 체크), `POST /api/admin/attendances/evening`(저녁반 추가), `DELETE /api/admin/attendances/{id}`(삭제) 4개 엔드포인트. `/api/admin/**` 전역 `hasRole("ADMIN")`에 의존해 컨트롤러에 별도 권한·트랜잭션·try-catch를 두지 않음(D-040·D-020·D-017)
- `AdminAttendanceControllerTest` 9건 — 성공 4종(명단 미체크 status=null, 체크 ATTENDED, 저녁반 추가 deducted=true, 삭제 204)과 대표 실패 5종(예약자 아님·저녁반/예약제 오용·잔여 부족·없는 id·회원 토큰 403)을 `AdminBatchControllerTest`와 동일한 애노테이션 조합으로 검증, `application/problem+json` 계약 확인
- `AttendanceConcurrencyTest` — 잔여 5.0인 `SESSION_PASS` 회원 1명에게 10스레드가 동시에 저녁반 출석 추가를 요청해도 성공 1건·`attendance` 행 1건·`EVENING_HALF` 이력 1건·잔여 정확히 4.5만 반영됨을 실제 PostgreSQL 동시 트랜잭션으로 실증(T-06-26). 실패 9건은 전부 `DuplicateAttendanceException`이고, Spring 기본 롤백 규칙 덕에 이들의 선행 차감도 함께 롤백돼 잔여가 이중으로 깎이지 않음을 확인
- `docs/api/openapi.yaml` 재생성 — 출석 4개 경로 + `CheckAttendanceRequest`·`AddEveningAttendanceRequest`·`AttendanceResponse`·`AttendanceRosterEntryResponse`·`ClassSessionAttendanceRosterResponse` 5개 스키마 추가, 기존 공지·배치·예약 등 경로는 그대로 보존(`servers: /` 유지)

## Task Commits

Each task was committed atomically:

1. **Task 1: AdminAttendanceController — 명단 조회·출석 체크·저녁반 추가·삭제 4 엔드포인트** - `24c07ae` (feat)
2. **Task 2: AdminAttendanceControllerTest — 성공 경로 4종 + 대표 실패 5종(HTTP 계약)** - `2f2c61d` (test)
3. **Task 3: AttendanceConcurrencyTest(이중 차감 0건) + openapi.yaml 재생성** - `431d635` (test)

**Plan metadata:** (다음 커밋에서 STATE.md·ROADMAP.md와 함께 기록)

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/attendance/AdminAttendanceController.kt` - 출석 관리자 API 4종, `principal.requireAdminId()` 위임 1줄씩
- `src/test/kotlin/com/goldwrestling/attendance/AdminAttendanceControllerTest.kt` - 성공 4종 + 실패 5종 HTTP 계약 테스트 9건
- `src/test/kotlin/com/goldwrestling/attendance/AttendanceConcurrencyTest.kt` - 저녁반 이중 차감 0건 동시성 테스트 1건(10스레드)
- `docs/api/openapi.yaml` - 출석 경로 4개 + DTO 스키마 5개 재생성

## Decisions Made
- 저녁반 회원 검색 전용 API 미생성(위 key-decisions 참조) — 기존 회원 검색 API 재사용
- 컨트롤러 경로를 `/api/admin/attendances` 단일 계층으로 고정(위 patterns-established 참조)

## Deviations from Plan

None - 계획대로 실행됐다.

## Issues Encountered

None - 계획대로 실행됐다. 동시성 테스트 설계 시 "사전 존재 체크(existsByClassSessionIdAndMemberId)가 병렬 요청 다수를 막지 못하는 것 아닌가"라는 의문이 있었으나, `DomainException`이 `RuntimeException`을 상속해 INSERT 유니크 위반 시 Spring이 트랜잭션 전체(선행 0.5회 차감 포함)를 롤백한다는 점을 `DomainException.kt` 확인으로 검증해 해소했다 — 이 기제가 이중 차감을 막는 실제 방어선이다(사전 체크는 빠른 실패용 최적화일 뿐).

## User Setup Required

None - 외부 서비스 설정 불필요.

## Next Phase Readiness

- 출석 청크(06-06~06-08)가 완결됨 — 명단 조회·체크·저녁반 차감·복구·동시성 실증·API 계약(openapi.yaml)이 모두 갖춰짐
- `./gradlew ktlintFormat && ./gradlew build`가 BUILD SUCCESSFUL로 끝남(전체 회귀 포함, 0 failures)
- FE는 `docs/api/openapi.yaml`로 출석 API 타입을 생성할 수 있음
- 블로커 없음. 다음 청크(06-09 이후, 알림/대시보드 등)로 진행 가능

---

## 이번에 쓴 기술

1. **RFC 9457 `ProblemDetail` + 전역 예외 핸들러(D-017)** — 도메인 예외를 컨트롤러가 아니라 한 곳(`GlobalExceptionHandler`)에서 표준 에러 응답 형태로 변환하는 방식
   - **이 코드에서 왜 필요했는가**: 컨트롤러 4개 메서드 모두 `try-catch` 없이 서비스 예외(`AttendanceMemberNotReservedException` 등)를 그대로 던지기만 한다. FE는 어떤 엔드포인트든 실패하면 항상 같은 모양(`code`·`detail`·`status`)의 JSON을 받는다.
   - **안 썼으면 뭐가 깨지는가**: 컨트롤러마다 각자 에러 응답을 만들면 어떤 API는 `{error: "..."}`, 어떤 API는 `{message: "..."}`처럼 형태가 갈라져 FE가 API마다 다른 파싱 로직을 짜야 한다.

2. **정렬된 애노테이션 조합 재사용으로 스프링 컨텍스트 캐시 유지** — `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)` 조합을 기존 `AdminBatchControllerTest`와 정확히 동일하게 맞추는 관례
   - **이 코드에서 왜 필요했는가**: 스프링은 테스트 클래스의 애노테이션 조합(컨텍스트 설정)이 다르면 새 애플리케이션 컨텍스트를 처음부터 다시 띄운다. 조합을 통일하면 이미 뜬 컨텍스트를 재사용해 `AdminAttendanceControllerTest`·`AttendanceConcurrencyTest`가 다른 통합테스트들과 함께 돌 때 기동 시간이 누적되지 않는다.
   - **안 썼으면 뭐가 깨지는가**: 테스트마다 컨텍스트가 새로 뜨면 전체 `./gradlew test` 시간이 급격히 늘어난다 — 지금도 이미 수백 건의 통합테스트가 있는 저장소라 체감이 크다.

3. **★ Spring `@Transactional`의 기본 롤백 규칙과 unchecked 예외 전파** — 트랜잭션 메서드 안에서 unchecked 예외(`RuntimeException` 계열)가 던져지면, 그 메서드 안에서 이미 실행된 DB 변경(조건부 UPDATE 포함)까지 전부 되돌리는 스프링 AOP의 기본 동작
   - **이 코드에서 왜 필요했는가**: `addEveningAttendance`는 "0.5회 차감(UPDATE) → 출석 INSERT" 순서로 실행된다. 10개 스레드가 동시에 같은 회원·같은 세션에 요청하면, 사전 존재 체크만으로는 여러 스레드가 동시에 차감까지 통과할 수 있다. 실제로 이중 차감을 막는 건 INSERT 단계의 DB 유니크 제약 위반이 `DataIntegrityViolationException`→`DuplicateAttendanceException`(RuntimeException)으로 이어지고, 스프링이 이 메서드의 트랜잭션 전체(그 안의 차감 UPDATE 포함)를 롤백한다는 사실이다.
   - **안 썼으면 뭐가 깨지는가**: 만약 `check()`나 `delete()`가 예외를 checked exception으로 던지거나, 서비스가 트랜잭션 경계 없이 각 단계를 개별 커밋했다면, 실패한 스레드의 차감(-0.5)만 DB에 남고 출석 기록은 없는 "유령 차감"이 생겼을 것이다. 회원 잔여가 실제 사용 가능 횟수와 어긋나는 것(Core Value 위반)이다.

4. **CountDownLatch 기반 동시 시작 신호로 경쟁 창을 강제로 좁히는 부하 테스트 기법** — `startLatch.await()`로 모든 스레드를 대기시켰다가 `startLatch.countDown()` 한 번으로 동시에 풀어주는 패턴
   - **이 코드에서 왜 필요했는가**: 스레드를 그냥 순서대로 `submit`만 하면 실행 타이밍이 자연히 벌어져 "동시에 같은 행을 두고 경쟁"하는 상황이 재현되지 않을 수 있다(운이 좋으면 순차 실행처럼 통과해 버그를 놓친다). `CountDownLatch(1)`로 모든 스레드가 같은 순간에 출발하게 만들어 DB 행 잠금 경쟁이 실제로 발생하도록 강제한다.
   - **안 썼으면 뭐가 깨지는가**: 타이밍이 우연히 벌어지면 테스트가 매번 통과해 "동시성 안전하다"는 잘못된 확신을 준다 — 실제 운영에서 관리자 두 명이 거의 동시에 같은 회원을 처리할 때만 터지는 버그를 못 잡는 테스트가 된다.

---
*Phase: 06-operations*
*Completed: 2026-08-18*

## Self-Check: PASSED

All created/modified files verified to exist on disk (`AdminAttendanceController.kt`, `AdminAttendanceControllerTest.kt`, `AttendanceConcurrencyTest.kt`, `docs/api/openapi.yaml`, this SUMMARY.md); all task commit hashes (`24c07ae`, `2f2c61d`, `431d635`) verified present in git log.
