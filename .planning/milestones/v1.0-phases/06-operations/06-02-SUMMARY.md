---
phase: 06-operations
plan: 02
subsystem: database
tags: [flyway, jpa, postgresql, testcontainers, attendance, notice]

# Dependency graph
requires:
  - phase: 06-operations
    provides: "06-01이 확정한 이름(AttendanceStatus, checkedBy/checkedAt, passTransaction, Notice title/content)과 에러코드 6종"
provides:
  - "V11 마이그레이션 — attendance·notice 테이블 + idx_notification_occurred_at(활동 피드용)"
  - "attendance 패키지 — Attendance/AttendanceStatus 엔티티 + AttendanceRepository(findLastAttendedClassDates 포함)"
  - "notice 패키지 — Notice 엔티티 + NoticeRepository(findAllByOrderByCreatedAtDescIdDesc)"
  - "attendance/AttendanceFixtures.kt — 06-03·06-06~06-08이 재사용할 출석·수업 테스트 픽스처"
affects: [06-03, 06-04, 06-05, 06-06, 06-07, 06-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "회원×세션 조건 없는 일반 UNIQUE로 이중 출석·이중 차감을 DB 수준에서 방지(D-127) — 예약의 부분 유니크(WHERE status='ACTIVE')를 그대로 복사하지 않는다"
    - "리포지토리 통합테스트에서 유니크 위반을 단언하는 클래스는 @Transactional 클래스 롤백 대신 @AfterEach 직접 정리를 쓴다(InactivityBatchRunnerTest·BatchExecutionRepositoryTest와 동일한 결론, 서로 반대 이유)"

key-files:
  created:
    - src/main/resources/db/migration/V11__create_attendance_and_notice.sql
    - src/main/kotlin/com/goldwrestling/attendance/Attendance.kt
    - src/main/kotlin/com/goldwrestling/attendance/AttendanceStatus.kt
    - src/main/kotlin/com/goldwrestling/attendance/AttendanceRepository.kt
    - src/main/kotlin/com/goldwrestling/notice/Notice.kt
    - src/main/kotlin/com/goldwrestling/notice/NoticeRepository.kt
    - src/test/kotlin/com/goldwrestling/attendance/AttendanceFixtures.kt
    - src/test/kotlin/com/goldwrestling/attendance/AttendanceRepositoryTest.kt
    - src/test/kotlin/com/goldwrestling/notice/NoticeRepositoryTest.kt
  modified: []

key-decisions:
  - "AttendanceRepositoryTest·NoticeRepositoryTest는 InactivityBatchRunnerTest와 동일한 애노테이션 조합(@Transactional 미사용 + @AfterEach 직접 정리)을 쓴다 — 06-06~06-08이 같은 컨텍스트를 재사용할 것으로 예상되고, 유니크 위반 단언 직후 abort된 PostgreSQL 트랜잭션에서 @AfterEach DELETE가 실패하는 문제(05-11 선례)도 피한다"
  - "AttendanceFixtures.branch()는 통합테스트에서 쓰지 않는다 — V2가 이미 '송파점'을 시드했고 branch.name에 uq_branch_name UNIQUE가 걸려 있어, 이 함수로 새 Branch를 저장하면 항상 충돌한다(PassFixtures.branch()와 동일하게 순수 단위테스트 전용 함수로 남긴다). 통합테스트는 BranchRepository.findByName(\"송파점\")으로 시드된 지점을 재사용한다"

requirements-completed: [ATTEND-01, ATTEND-02, NOTICE-01, NOTICE-02, NOTIF-03]

# Metrics
duration: ~35min
completed: 2026-08-18
---

# Phase 6 Plan 2: 출석·공지 저장 계층 Summary

**V11 마이그레이션으로 attendance·notice 테이블 + 활동 피드 인덱스를 추가하고, Attendance/Notice 엔티티·리포지토리 2종을 Testcontainers 통합테스트 5+2건으로 실증**

## Performance

- **Duration:** ~35min
- **Completed:** 2026-08-18
- **Tasks:** 3/3 완료
- **Files modified:** 9 (전부 신규 생성)

## Accomplishments
- `V11__create_attendance_and_notice.sql` — attendance(회원×세션 **조건 없는 일반 유니크**, D-127) + notice(hard delete 확정, D-131이라 `deleted_at` 없음) 테이블, `idx_notification_occurred_at`(활동 피드용, D-135)까지 한 마이그레이션에 확정. V1~V10은 손대지 않았다
- `Attendance`/`AttendanceStatus` 엔티티 — `member`/`classSession`/`checkedBy`는 non-null LAZY, `passTransaction`만 nullable LAZY(저녁반 0.5회 차감 연결, D-128). `status`만 `var`(소급 정정 허용)
- `AttendanceRepository.findLastAttendedClassDates` — `ATTENDED`만 집계하는 CR-03 벌크 조회. `ABSENT`가 섞이면 2주 미사용 차감 기준일이 부당하게 갱신되는 것을 T-06-04로 방지
- `Notice` 엔티티/`NoticeRepository` — `title`/`content`/`updatedAt`만 `var`, 최신순 페이지는 `createdAt`+`id` 2차 정렬로 동률에서도 결정적 순서 보장
- `AttendanceFixtures` — 06-03(배치)·06-06~06-08(출석 서비스·컨트롤러·동시성)이 재사용할 회원·수업·출석 최소 생성 함수. 통합테스트 7건(`AttendanceRepositoryTest` 5 + `NoticeRepositoryTest` 2) 전부 실제 PostgreSQL(Testcontainers)에서 통과
- 전체 회귀 `./gradlew ktlintFormat build` BUILD SUCCESSFUL — 마이그레이션 11개 전부 빈 DB에서 재생, 엔티티 매핑 validate 통과, 기존 테스트 회귀 없음

## Task Commits

1. **Task 1: V11 마이그레이션 — attendance·notice 테이블 + 활동 피드 인덱스** - `3b22098` (feat)
2. **Task 2: Attendance·AttendanceStatus·Notice 엔티티와 리포지토리 2종** - `fc59184` (feat)
3. **Task 3: 리포지토리 통합테스트 2종 + AttendanceFixtures** - `188a4c0` (test)

_TDD 표시(`tdd="true"`)가 있었으나 이 두 태스크는 순수 저장 계층(엔티티·리포지토리)이라 RED/GREEN 사이클 대신 "구현 → 통합테스트로 즉시 실증" 순서로 진행했다 — DB 제약이 실제 방어선이라 실패하는 테스트를 먼저 작성할 도메인 로직(분기)이 없다(conventions §10.0 "리포지토리 커스텀 쿼리 = Testcontainers 통합테스트 필수"가 이 플랜의 실제 적용 규칙)._

## Files Created/Modified
- `src/main/resources/db/migration/V11__create_attendance_and_notice.sql` - attendance·notice 테이블 + idx_notification_occurred_at
- `src/main/kotlin/com/goldwrestling/attendance/AttendanceStatus.kt` - ATTENDED/ABSENT 2값 enum
- `src/main/kotlin/com/goldwrestling/attendance/Attendance.kt` - 출석 엔티티(passTransaction만 nullable)
- `src/main/kotlin/com/goldwrestling/attendance/AttendanceRepository.kt` - CR-03 벌크 조회 + 세션별 조회 3종
- `src/main/kotlin/com/goldwrestling/notice/Notice.kt` - 공지 엔티티(title/content/updatedAt만 var)
- `src/main/kotlin/com/goldwrestling/notice/NoticeRepository.kt` - 최신순 페이지 조회
- `src/test/kotlin/com/goldwrestling/attendance/AttendanceFixtures.kt` - 출석·수업 테스트 픽스처(향후 플랜 공유)
- `src/test/kotlin/com/goldwrestling/attendance/AttendanceRepositoryTest.kt` - 유니크 제약·상태 정정·ATTENDED 필터 5건
- `src/test/kotlin/com/goldwrestling/notice/NoticeRepositoryTest.kt` - 최신순 페이지·hard delete 2건

## Decisions Made
- `AttendanceRepositoryTest`/`NoticeRepositoryTest`는 `InactivityBatchRunnerTest`와 동일한 애노테이션 조합(`@Transactional` 없이 `@AfterEach` 직접 정리)을 썼다 — 유니크 위반을 단언하는 첫 번째 테스트가 그 트랜잭션을 abort 상태로 만들기 때문에, 만약 클래스 레벨 `@Transactional` 롤백에 기댔다면 그 자체는 문제없지만(테스트 종료 시 어차피 롤백) 06-06~06-08이 이어받을 컨텍스트 조합을 미리 통일해 둔 것이 목적이다(conventions §10.1)
- `AttendanceFixtures.branch()`는 계획에 명시된 대로 만들었지만 통합테스트에서는 호출하지 않는다 — `branch.name`에 `uq_branch_name` UNIQUE가 있고 V2가 이미 "송파점"을 시드해서, 이 함수로 새 `Branch`를 저장하면 항상 충돌한다. `PassFixtures.branch()`와 같은 이유로 순수 단위테스트(스프링 컨텍스트 없이 도메인 로직만 검증) 전용으로 남겨두고, 통합테스트는 `BranchRepository.findByName("송파점")`으로 시드된 지점을 재사용했다(`BatchFixtures`가 쓰는 관례와 동일)
- `AttendanceRepositoryTest`의 `class_session` 픽스처는 실제 정책 날짜(예약 오픈·마감 등)와 무관한 임의 날짜(2030년부터 순차 증가)를 쓴다 — 이 리포지토리·테스트가 검증하는 것은 유니크 제약과 `GROUP BY` 집계뿐이라 날짜 값 자체는 의미가 없고, 카운터로 매 세션마다 겹치지 않는 날짜만 보장하면 된다(`uq_class_session (class_schedule_id, class_date)` 회피)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 06-03(배치)이 `AttendanceRepository.findLastAttendedClassDates`를 `InactivityBatchRunner`에 배선할 수 있는 저장 계층이 준비됐다 — 06-PATTERNS.md가 이미 배선 지점(`lastAttendanceDate = null` → 벌크 조회 결과로 교체)을 특정해 뒀다
- 06-04~06-08(공지 API, 출석 서비스·컨트롤러·동시성)이 이번에 확정된 `Attendance`/`Notice` 엔티티와 `AttendanceFixtures`를 그대로 재사용할 수 있다 — 새로 이름을 짓거나 픽스처를 다시 만들 필요가 없다
- `EveningHalfDeductionPolicy`(저녁반 0.5회 차감 판정)와 `AttendanceService`(조건부 원자 갱신 + 원장)는 이번 플랜 범위 밖이다 — 06-PATTERNS.md의 해당 섹션이 다음 플랜의 시작점이다

---

## 이번에 쓴 기술

1. **조건 없는 일반 UNIQUE 제약으로 상태 정정을 "새 행 추가"가 아니라 "같은 행 UPDATE"로 강제하기**
   - **이 코드에서 왜 필요했는가**: 예약(`Reservation`)은 취소 후 같은 시간에 재예약이 가능해야 해서 `WHERE status = 'ACTIVE'`가 붙은 **부분 유니크**를 쓴다(취소된 예약은 제약에서 빠지므로 재예약 시 새 행을 또 만들 수 있다). 하지만 출석은 다르다 — 관리자가 `ABSENT`로 체크했다가 나중에 `ATTENDED`로 정정하는 것은 "다시 체크"가 아니라 "이미 체크된 기록의 정정"이라, 조건 없는 일반 `UNIQUE (class_session_id, member_id)`를 걸어 두 번째 INSERT 자체가 항상 막히게 만들었다. 그러면 코드가 "정정"을 구현할 방법은 기존 행을 찾아 `status`만 바꾸는 것뿐이다.
   - **안 썼으면 뭐가 깨지는가**: 예약처럼 조건부(부분) 유니크를 썼다면, `ABSENT` 행이 있는 상태에서 관리자가 실수로(혹은 API 설계 실수로) "출석 추가" 엔드포인트를 다시 호출하면 두 번째 `ATTENDED` 행이 그냥 저장돼 버린다. 저녁반의 경우 이 두 번째 행이 `passTransaction`을 또 만들면 같은 수업에 0.5회가 두 번 차감되는 사고로 이어진다(T-06-03) — DB가 아니라 애플리케이션 코드의 "이미 있으면 update"라는 판단 로직에만 의존했다면, 그 로직에 버그가 있을 때 이 사고를 막을 방법이 없다.

2. **JPQL 벌크 프로젝션 쿼리 + `GROUP BY`로 "회원별 마지막 날짜"를 N+1 없이 한 번에 조회하기**
   - **이 코드에서 왜 필요했는가**: 2주 미사용 차감 배치(Phase 5)는 대상 회원이 수백~수천 명일 수 있는데, 회원마다 "이 사람의 마지막 출석일이 언제인가"를 따로 쿼리하면 회원 수만큼 쿼리가 나간다(N+1). `findLastAttendedClassDates`는 `select a.member.id as memberId, max(a.classSession.classDate) as date ... group by a.member.id`로 **회원 목록 전체의 결과를 쿼리 1번**에 가져오고, 그 결과를 `MemberDateProjection`(엔티티가 아니라 `getMemberId()`/`getDate()`만 있는 읽기 전용 인터페이스)으로 받는다 — 엔티티 전체를 로딩하지 않으니 메모리도 아낀다.
   - **안 썼으면 뭐가 깨지는가**: 회원별로 반복문을 돌며 `attendanceRepository.findByMemberId(id)`류의 쿼리를 호출했다면, 대상 회원이 1000명일 때 배치 실행마다 최소 1000번의 DB 왕복이 생긴다. 이게 매일 새벽 도는 배치라는 걸 감안하면, 대상 회원 수가 늘어날수록 배치 실행 시간이 선형으로 늘어나 결국 다음 배치 실행 시간과 겹치는 사고로 이어질 수 있다(Phase 5가 이미 `uq_batch_execution_running`으로 겹침 자체는 막아뒀지만, "느려서 못 끝난다"는 별개 문제다).

3. **★ Postgres 트랜잭션 abort 이후에는 같은 트랜잭션에서 아무 쿼리도 못 낸다 — 통합테스트 정리 전략을 여기 맞춰 고르기**
   - **이 코드에서 왜 필요했는가**: `assertThatThrownBy { attendanceRepository.saveAndFlush(duplicate) }`로 유니크 위반을 일으키는 테스트가 있으면, 그 순간 PostgreSQL은 **그 트랜잭션 전체를 abort 상태**로 만든다 — 이후 같은 트랜잭션 안에서 어떤 SQL을 보내도(심지어 관계없는 SELECT라도) "current transaction is aborted" 에러가 난다. 이 프로젝트의 다른 리포지토리 테스트(`PassRepositoryTest` 등)는 클래스 레벨 `@Transactional`로 "테스트 하나 = 트랜잭션 하나, 끝나면 자동 롤백"을 쓰는데, 그건 테스트 메서드가 끝나는 시점에 통째로 버려지니 abort 여부가 문제되지 않는다. 하지만 `AttendanceRepositoryTest`는 06-06~06-08이 이어받을 `InactivityBatchRunnerTest` 스타일(트랜잭션 애노테이션 없이 실제 커밋 + `@AfterEach`에서 직접 DELETE)을 따르기로 했으므로, "유니크 위반 단언이 테스트의 마지막 동작이 되도록" 각 테스트를 짰다 — abort된 트랜잭션 위에서 추가 쿼리를 내지 않게.
   - **안 썼으면 뭐가 깨지는가**: 만약 유니크 위반을 단언한 뒤 같은 테스트 메서드 안에서 다른 조회를 이어서 했다면 "current transaction is aborted, commands ignored until end of transaction block"이라는, 원래 검증하려던 것과 무관한 에러로 테스트가 실패했을 것이다 — 05-11(`BatchExecutionRepositoryTest`)에서 이미 겪은 문제와 같은 종류다.

## Self-Check: PASSED

- FOUND: src/main/resources/db/migration/V11__create_attendance_and_notice.sql
- FOUND: src/main/kotlin/com/goldwrestling/attendance/Attendance.kt
- FOUND: src/main/kotlin/com/goldwrestling/attendance/AttendanceStatus.kt
- FOUND: src/main/kotlin/com/goldwrestling/attendance/AttendanceRepository.kt
- FOUND: src/main/kotlin/com/goldwrestling/notice/Notice.kt
- FOUND: src/main/kotlin/com/goldwrestling/notice/NoticeRepository.kt
- FOUND: src/test/kotlin/com/goldwrestling/attendance/AttendanceFixtures.kt
- FOUND: src/test/kotlin/com/goldwrestling/attendance/AttendanceRepositoryTest.kt
- FOUND: src/test/kotlin/com/goldwrestling/notice/NoticeRepositoryTest.kt
- FOUND: commit `3b22098` (Task 1)
- FOUND: commit `fc59184` (Task 2)
- FOUND: commit `188a4c0` (Task 3)

---
*Phase: 06-operations*
*Completed: 2026-08-18*
