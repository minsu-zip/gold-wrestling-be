---
phase: 04-schedule-reservation
plan: 02
subsystem: db
tags: [flyway, migration, schema, concurrency, testcontainers]

# Dependency graph
requires:
  - phase: 04-schedule-reservation
    plan: "04-01"
    provides: "ErrorCode 13종·예외 클래스(schedule/ScheduleExceptions.kt, reservation/ReservationExceptions.kt), D-089~098 결정 원본"
provides:
  - "class_schedule/class_session/reservation/notification 4개 테이블(V6) — 04-03(엔티티)이 그대로 매핑할 스키마"
  - "송파점 정기 시간표 52행 시드(V7) — SCHED-01 충족, 이후 모든 예약 코드가 조회할 데이터"
  - "pass_transaction 회원 주체 확장(V8, member_id + ck_pass_transaction_subject) — 예약/취소의 PassTransaction 기록 경로가 여기 의존"
  - "부분 유니크 인덱스 3종(ux_reservation_session_member_active/ux_reservation_lesson_slot_active/ux_reservation_member_timeslot_active) — RESV-06 초과 예약 0건의 최종 방어선"
affects: ["04-03(엔티티·리포지토리)", "04-04(예약 서비스)", "04-05(관리자 화면)", "pass/AdminPassService(D-089 등록 취소 선행 검사가 참조)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "타입별 필수 컬럼을 DB CHECK로 강제(ck_class_schedule_capacity_by_type) — V4 ck_pass_remaining_count_by_type과 동일 관례"
    - "취소 메타데이터 완전성 CHECK(ck_class_session_cancellation, ck_reservation_cancellation) — V4 ck_pass_cancellation과 동일 관례"
    - "부분 유니크 인덱스(WHERE status = 'ACTIVE')로 '취소 후 재예약 허용 + 활성 중복만 차단'을 표현"
    - "비정규화 컬럼(reservation.class_type/class_date/start_time, class_session의 시각 복사)으로 Postgres 부분 인덱스가 단일 테이블 안에서 성립하도록 설계"
    - "branch_id는 리터럴이 아니라 지점명 조회(INSERT...SELECT)로 시드 — 환경별 id 차이에 안전"

key-files:
  created:
    - src/main/resources/db/migration/V6__create_schedule_reservation_notification.sql
    - src/main/resources/db/migration/V7__seed_class_schedule.sql
    - src/main/resources/db/migration/V8__extend_pass_transaction_subject.sql
    - src/test/kotlin/com/goldwrestling/schedule/ClassScheduleSeedIntegrationTest.kt
  modified:
    - src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt

key-decisions:
  - "V6 한 파일에 4개 테이블을 함께 담았다(계획대로) — class_schedule→class_session→reservation→notification 순으로 FK 의존성이 있어 분리하면 순서 관리만 복잡해진다"
  - "V7 seed 태스크를 tdd=true 지시대로 RED(테스트만, 4개 실패) → GREEN(시드 추가, 전체 통과) 2단계 커밋으로 분리했다"

patterns-established:
  - "get-or-create 대상 테이블(class_session)의 유니크 제약을 마이그레이션에서부터 걸어 두고, 애플리케이션 코드(04-03)는 그 위에서 INSERT...ON CONFLICT DO NOTHING만 구현하면 되게 만들었다"

requirements-completed: [SCHED-01, SCHED-02, RESV-06, NOTIF-01]

# Metrics
duration: ~30min
completed: 2026-08-07
---

# Phase 4 Plan 2: 시간표·예약·알림 스키마 + 시드 + PassTransaction 확장 Summary

**Flyway 마이그레이션 3개(V6·V7·V8)로 시간표·수업·예약·알림 4개 테이블과 부분 유니크 인덱스 3종·CHECK 5종을 만들고, 송파점 정기 시간표 52행을 시드했으며, `pass_transaction`이 회원을 이력 주체로 기록할 수 있게 확장했다 — "초과 예약 0건"을 애플리케이션 코드가 아니라 DB 제약이 보장하는 토대가 이제 실제 PostgreSQL에 존재한다.**

## Performance

- **Duration:** ~30 min
- **Completed:** 2026-08-07T15:48:38+09:00
- **Tasks:** 3/3 (Task 2는 tdd=true 지시대로 RED/GREEN 2커밋으로 분리해 총 5개 태스크 커밋 + 1개 편차 수정 커밋)
- **Files modified:** 5 (created 4, modified 1)

## Accomplishments

- **V6**: `class_schedule`(정기 시간표) · `class_session`(날짜별 수업) · `reservation`(예약) · `notification`(관리자 알림) 4개 테이블을 하나의 마이그레이션으로 생성. 타입별 필수 컬럼(`ck_class_schedule_capacity_by_type`), 취소 메타데이터 완전성(`ck_class_session_cancellation`, `ck_reservation_cancellation`), 알림 대상 필수(`ck_notification_target`) 등 CHECK 5종과, "초과 예약 0건"·"중복 예약 금지"·"1:1 슬롯 1명"을 DB 수준에서 강제하는 부분 유니크 인덱스 3종(`ux_reservation_session_member_active`, `ux_reservation_lesson_slot_active`, `ux_reservation_member_timeslot_active`)을 추가
- **V7**: policies.md §2 확정값 그대로 송파점 정기 시간표 52행(EVENING 10·SESSION 16·LESSON 26)을 시드. `branch_id`는 리터럴이 아니라 지점명("송파점") 조회로 채워 환경별 id 차이에 안전. `ClassScheduleSeedIntegrationTest`(신규, 7개 테스트)가 전체 행 수·타입별 행 수·정원값·90분 간격·branch 귀속·LESSON=EVENING∪SESSION 집합 동일성을 실제 PostgreSQL에서 검증
- **V8**: `pass_transaction.admin_id`를 nullable로 완화하고 `member_id`(nullable FK)를 추가, `ck_pass_transaction_subject`로 "주체는 관리자/회원 중 정확히 하나"를 DB가 강제하게 함(D-030이 예고한 확장). 기존 행은 전부 `admin_id`가 채워져 있어 CHECK를 그대로 통과 — 데이터 백필 불필요
- 세 마이그레이션 모두 `FlywayMigrationIntegrationTest`·전체 스위트로 실제 적용을 확인했고, V1~V5는 이 작업에서 전혀 수정되지 않았다

## Task Commits

Each task was committed atomically:

1. **Task 1: V6 — class_schedule/class_session/reservation/notification DDL** - `4a4da6f` (feat)
2. **Task 2-RED: ClassScheduleSeedIntegrationTest 작성(시드 없이 4개 실패 확인)** - `4b7e512` (test)
2. **Task 2-GREEN: V7 시드 추가(전체 7개 테스트 통과)** - `834fc56` (feat)
3. **Task 3: V8 — pass_transaction 회원 주체 확장** - `e37ad1b` (feat)
4. **편차 수정: KakaoAuthControllerTest의 branch 삭제 실패 수정** - `c1518c1` (fix)

**Plan metadata:** (다음 커밋에서 이 SUMMARY·STATE.md·ROADMAP.md를 함께 기록)

## Files Created/Modified

- `src/main/resources/db/migration/V6__create_schedule_reservation_notification.sql` (신규) - 4테이블 + 인덱스 3종 + CHECK 5종
- `src/main/resources/db/migration/V7__seed_class_schedule.sql` (신규) - 송파점 시간표 52행 시드
- `src/main/resources/db/migration/V8__extend_pass_transaction_subject.sql` (신규) - pass_transaction 회원 주체 확장
- `src/test/kotlin/com/goldwrestling/schedule/ClassScheduleSeedIntegrationTest.kt` (신규) - 시드 검증 통합테스트 7개
- `src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt` - branch 삭제 테스트가 V7 시드의 FK를 먼저 정리하도록 3줄 추가 (편차, 아래 참조)

## Decisions Made

- PLAN.md가 지정한 스키마·시드 구성·V8 CHECK를 그대로 채택했다. 별도의 새 도메인 판단은 없었다 — 04-RESEARCH.md "Code Examples"의 완성된 DDL을 그대로 옮기고 notification 테이블만 PLAN.md 스펙대로 추가했다.
- Task 2(tdd="true")는 CLAUDE.md 규칙 10·conventions §10.0에 따라 RED(테스트만, 시드 없이 4개 실패 확인) → GREEN(V7 추가, 7개 전체 통과) 순서로 커밋을 분리했다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] KakaoAuthControllerTest의 `branchRepository.deleteAll()`이 V6/V7이 만든 FK로 실패**
- **Found during:** Task 3 완료 후 `./gradlew build` 전체 스위트 실행
- **Issue:** `KakaoAuthControllerTest`의 "기본 지점이 DB에 없으면 로그인이 500과 INTERNAL_ERROR로 실패" 테스트가 `branchRepository.deleteAll()`을 호출하는데, V6의 `fk_class_schedule_branch`(기본 RESTRICT)와 V7이 심은 시드 52행이 그 branch를 참조하고 있어 삭제가 FK 위반으로 실패했다. 트랜잭션이 abort 상태가 되면서 이후 `memberRepository.count()` 호출까지 `JpaSystemException`으로 연쇄 실패했다.
- **Fix:** 이 테스트의 관심사는 "기본 지점이 없는 상태"이지 시간표가 아니므로, `branchRepository.deleteAll()` 직전에 `jdbcClient.sql("DELETE FROM class_schedule").update()`로 참조를 먼저 제거했다. `ClassScheduleRepository`가 아직 없어(04-03이 만듦) 이미 테스트에 주입돼 있던 `JdbcClient`를 그대로 재사용했다.
- **Files modified:** src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt
- **Verification:** `./gradlew test --tests "com.goldwrestling.auth.KakaoAuthControllerTest"` 11개 전부 통과. `./gradlew build` 전체 367개 테스트 통과.
- **Committed in:** `c1518c1`

---

**Total deviations:** 1 auto-fixed (Rule 1 — 버그: 이번 플랜이 추가한 FK 제약이 기존 테스트의 전제를 깼다)
**Impact on plan:** 스코프 확장 없음. 스키마·시드 자체는 변경하지 않았고, 기존 테스트 한 곳의 정리 순서만 보정했다.

## Issues Encountered

None — Boot 4 API·신규 의존성 확인이 필요한 작업이 없었다(순수 SQL 마이그레이션 + JdbcClient 기반 통합테스트, 기존 Testcontainers 배선 재사용).

## User Setup Required

None - no external service configuration required. 로컬 검증은 `docker compose up -d` 후 `./gradlew build`로 전부 확인했다.

## Next Phase Readiness

- 04-03(엔티티·리포지토리)이 이 스키마를 정확히 매핑할 수 있다 — `ddl-auto=validate`이므로 컬럼명·타입·nullable 여부가 어긋나면 부팅 시점에 즉시 드러난다
- `PassRepository.adjustRemainingCount`를 예약 차감/복구에 재사용할 준비가 됐다(V8이 `pass_transaction`에 회원 주체 기록 경로를 열었으므로, Phase 3 코드 변경 없이 그대로 호출 가능)
- 부분 유니크 인덱스 3종·`ck_class_session_reserved_count` 조건부 UPDATE 대상 컬럼이 모두 준비돼, 04-04의 동시성 테스트(RESV-06, `ExecutorService`+`CountDownLatch`)가 이 스키마 위에서 바로 실행 가능하다
- 블로커 없음 — `./gradlew ktlintFormat`(무변경) → `./gradlew build`(367개 테스트, Testcontainers 포함 전체 그린) 확인 완료

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-07*

## Self-Check: PASSED

All created/modified files verified present on disk (V6/V7/V8 마이그레이션, `ClassScheduleSeedIntegrationTest.kt`, 이 SUMMARY.md). All 5 task/deviation commits (`4a4da6f`, `4b7e512`, `834fc56`, `e37ad1b`, `c1518c1`) verified in git log.
