---
phase: 04-schedule-reservation
plan: 03
subsystem: db
tags: [jpa, entity, repository, concurrency, testcontainers, native-query]

# Dependency graph
requires:
  - phase: 04-schedule-reservation
    plan: "04-02"
    provides: "V6/V7/V8 스키마(class_schedule/class_session/reservation/notification, 부분 유니크 인덱스 3종, ck_pass_transaction_subject) — 이 플랜이 그대로 매핑"
  - phase: 04-schedule-reservation
    plan: "04-01"
    provides: "schedule/reservation 예외 클래스 12종 — ClassSession·Reservation 판정 메서드가 그대로 재사용"
provides:
  - "schedule 패키지: ClassType/ClassSessionStatus enum, ClassSchedule/ClassSession 엔티티, ClassScheduleRepository/ClassSessionRepository(get-or-create 네이티브 upsert + 정원·휴강 조건부 UPDATE 4종)"
  - "reservation 패키지: ReservationStatus enum, Reservation 엔티티(비정규화 컬럼 + 주체 nullable 쌍), ReservationRepository(취소 compare-and-swap 2종)"
  - "notification 패키지: NotificationType(6종)·Notification(append-only)·NotificationRepository(저장 전용)"
  - "PassTransaction 회원 주체 매핑(admin nullable + member 추가) — Phase 4 서비스 코드가 회원 셀프 예약/취소 이력을 남길 수 있는 경로"
affects: ["04-04(예약 서비스가 이 리포지토리들을 조립)", "04-05~04-09(관리자 화면·휴강·예약 변경 TDD 플랜)", "pass/AdminPassService(D-089 선행 검사가 이 플랜의 reservationRepository.existsByPassIdAndStatus를 참조할 예정)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "이 저장소 최초의 nativeQuery(INSERT...ON CONFLICT DO NOTHING) — get-or-create 경쟁을 예외 없이 upsert로 해결"
    - "조건부 UPDATE(compare-and-swap) 관례를 schedule/reservation에 그대로 이식 — PassRepository.adjustRemainingCount와 동일 KDoc 형식(반환 0 의미·flush/clear 이유·LAZY 함정 경고)"
    - "판정만 하는 도메인 메서드 + 반영은 조건부 UPDATE(D-072) — ClassSession.assertReservable/assertSuspendable/assertResumable"
    - "부분 유니크 인덱스 3종 방어를 위한 비정규화 컬럼(Reservation.classType/classDate/startTime)"
    - "주체 nullable 쌍 + CHECK(RefreshToken.member/admin과 동일 관례) — Reservation.canceledByMember/canceledByAdmin, PassTransaction.admin/member"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/schedule/ClassType.kt
    - src/main/kotlin/com/goldwrestling/schedule/ClassSessionStatus.kt
    - src/main/kotlin/com/goldwrestling/schedule/ClassSchedule.kt
    - src/main/kotlin/com/goldwrestling/schedule/ClassScheduleRepository.kt
    - src/main/kotlin/com/goldwrestling/schedule/ClassSession.kt
    - src/main/kotlin/com/goldwrestling/schedule/ClassSessionRepository.kt
    - src/test/kotlin/com/goldwrestling/schedule/ClassSessionRepositoryTest.kt
    - src/main/kotlin/com/goldwrestling/reservation/ReservationStatus.kt
    - src/main/kotlin/com/goldwrestling/reservation/Reservation.kt
    - src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt
    - src/test/kotlin/com/goldwrestling/reservation/ReservationRepositoryTest.kt
    - src/main/kotlin/com/goldwrestling/notification/NotificationType.kt
    - src/main/kotlin/com/goldwrestling/notification/Notification.kt
    - src/main/kotlin/com/goldwrestling/notification/NotificationRepository.kt
  modified:
    - src/main/kotlin/com/goldwrestling/pass/PassTransaction.kt
    - src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt
    - src/test/kotlin/com/goldwrestling/pass/MemberPassTransactionControllerTest.kt
    - src/test/kotlin/com/goldwrestling/pass/PassRepositoryTest.kt

key-decisions:
  - "ClassSession.status/reservedCount에 기본값을 두지 않고 생성자 필수 파라미터로 둠 — 판정 전용 원칙(D-072) grep 검사를 확실히 통과시키고 Pass.kt 관례(명시적 값 요구)와 일관성 유지"
  - "ReservationRepositoryTest의 4번째 부분 인덱스 테스트(같은 회원·같은 날짜·시각의 서로 다른 세션)에서 두 세션이 서로 다른 ClassSchedule을 참조하도록 fixture를 분리 — 같은 스케줄을 재사용하면 uq_class_session(class_schedule_id, class_date) 제약과 충돌해 의도한 대상 제약(ux_reservation_member_timeslot_active)이 아닌 다른 제약에서 실패했다"

requirements-completed: [SCHED-01, SCHED-02, RESV-01, RESV-02, RESV-06, NOTIF-01]

# Metrics
duration: ~20min
completed: 2026-08-07
---

# Phase 4 Plan 3: 시간표·예약·알림 엔티티/리포지토리 + PassTransaction 회원 주체 확장 Summary

**V6~V8 스키마를 schedule/reservation/notification 3개 패키지의 JPA 엔티티·리포지토리로 매핑하고, 이 phase의 동시성 방어 도구 3종(네이티브 get-or-create, 정원 조건부 UPDATE, 예약 취소 compare-and-swap)을 리포지토리 계층에 완성했다 — 서비스 코드(04-04 이후)는 이 메서드들을 조립만 하면 된다.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-08-07T16:11:05+09:00
- **Tasks:** 3/3
- **Files modified:** 18 (created 14, modified 4)

## Accomplishments

- **schedule 패키지**: `ClassType`(reservable 프로퍼티로 `EVENING` 예약 불가 표현)·`ClassSessionStatus` enum, `ClassSchedule`(Flyway 시드 전용, companion factory 없음)·`ClassSession`(판정 전용 메서드 3종: `assertReservable`/`assertSuspendable`/`assertResumable`) 엔티티. `ClassSessionRepository`가 이 저장소 최초로 `nativeQuery = true`를 써 `INSERT ... ON CONFLICT DO NOTHING` get-or-create를 구현하고, 정원·휴강 조건부 UPDATE 4종(`incrementReservedCountIfCapacityAvailable`/`decrementReservedCount`/`suspendIfScheduled`/`resumeIfCanceled`)을 `PassRepository.adjustRemainingCount`와 동일한 KDoc 관례로 문서화. `ClassSessionRepositoryTest` 7개 테스트로 `<behavior>` 6개 항목을 전부 실증
- **reservation 패키지**: `ReservationStatus` enum, `Reservation` 엔티티(비정규화 `classType`/`classDate`/`startTime`, `pass` 참조로 복구 대상 추적 D-091, `canceledByMember`/`canceledByAdmin` nullable 쌍). 취소·변경 판정 메서드는 계획대로 이 플랜에서 만들지 않음(04-09가 TDD로 추가). `ReservationRepository`가 `cancelByMemberIfActive`/`cancelByAdminIfActive` compare-and-swap과 `existsByPassIdAndStatus`(D-089 선행검사)·`findByIdAndMemberId`(IDOR 방어)를 제공. `ReservationRepositoryTest` 7개 테스트로 부분 유니크 인덱스 3종(세션+회원/LESSON 슬롯/회원+날짜+시각)의 실제 거부와 "취소 후 재예약 허용"을 Testcontainers로 실증
- **notification 패키지 + PassTransaction 확장**: `NotificationType`(6종)·`Notification`(append-only, `isRead`/`readAt`만 `var`)·`NotificationRepository`(저장 전용, 조회는 Phase 6) 신설. `PassTransaction.admin`을 nullable로 완화하고 `member`를 추가해 `ck_pass_transaction_subject`(V8)와 짝을 이루는 "행위자" 표현을 완성 — Phase 3의 관리자 주체 경로는 전부 회귀 없이 통과(367→374개 테스트 그린)

## Task Commits

Each task was committed atomically:

1. **Task 1: schedule 패키지 — enum·엔티티·리포지토리(get-or-create + 정원 조건부 UPDATE)** - `cfd73aa` (feat)
2. **Task 2: reservation 패키지 — 엔티티·리포지토리(취소 compare-and-swap) + 부분 유니크 인덱스 검증** - `d5a692b` (feat)
3. **Task 3: notification 패키지 + PassTransaction 회원 주체 매핑** - `a9605ac` (feat)

**Plan metadata:** (다음 커밋에서 이 SUMMARY·STATE.md·ROADMAP.md를 함께 기록)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/schedule/{ClassType,ClassSessionStatus,ClassSchedule,ClassScheduleRepository,ClassSession,ClassSessionRepository}.kt` (신규)
- `src/test/kotlin/com/goldwrestling/schedule/ClassSessionRepositoryTest.kt` (신규) - 7개 테스트
- `src/main/kotlin/com/goldwrestling/reservation/{ReservationStatus,Reservation,ReservationRepository}.kt` (신규)
- `src/test/kotlin/com/goldwrestling/reservation/ReservationRepositoryTest.kt` (신규) - 7개 테스트
- `src/main/kotlin/com/goldwrestling/notification/{NotificationType,Notification,NotificationRepository}.kt` (신규)
- `src/main/kotlin/com/goldwrestling/pass/PassTransaction.kt` - `admin: Admin?` 완화 + `member: Member?` 추가
- `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt` - `PassTransaction(...)` 생성자 호출 3곳에 `member = null` 명시
- `src/test/kotlin/com/goldwrestling/pass/MemberPassTransactionControllerTest.kt` - 동일 이유로 `PassTransaction(...)` 호출 1곳 수정 (편차, 아래 참조)
- `src/test/kotlin/com/goldwrestling/pass/PassRepositoryTest.kt` - `PassTransaction` 회원 주체 저장 성공/CHECK 위반 2건 테스트 3개 추가 (편차, 아래 참조)

## Decisions Made

- `ClassSession`의 `status`/`reservedCount`에 기본값을 두지 않고 생성자 필수 파라미터로 뒀다 — Pass.kt 관례(선택적 nullable 필드만 기본값)와 일관성을 유지하고, acceptance criteria의 "판정 전용 grep 검사"를 명확히 통과시키기 위해서다.
- 그 외에는 PLAN.md·04-PATTERNS.md·04-RESEARCH.md가 지정한 설계를 그대로 채택했다. 별도의 새 도메인 판단은 없었다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] PLAN.md 인터페이스 절의 "PassTransaction 생성자 호출 4곳 모두 AdminPassService.kt"가 실제와 달랐다**
- **Found during:** Task 3 컴파일 검증
- **Issue:** PLAN.md `<interfaces>`는 "`AdminPassService.kt` — `PassTransaction(...)` 생성자 호출 4곳이 있다"고 명시했지만, 실제로는 `AdminPassService.kt`에 3곳(`register`/`adjust`/`cancel`), `MemberPassTransactionControllerTest.kt`에 테스트 픽스처 1곳(총 4곳)이었다. `PassTransaction.member`를 nullable로 추가하되 기본값을 주지 않기로 했으므로(PLAN.md "기본값에 의존하지 않는다"), 이 테스트 파일도 `member = null`을 넘기지 않으면 컴파일이 깨졌다.
- **Fix:** `AdminPassService.kt` 3곳 + `MemberPassTransactionControllerTest.kt` 1곳 총 4곳 모두에 `member = null`을 명시적으로 추가했다.
- **Files modified:** src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt, src/test/kotlin/com/goldwrestling/pass/MemberPassTransactionControllerTest.kt
- **Verification:** `./gradlew compileKotlin compileTestKotlin` 종료 코드 0.
- **Committed in:** `a9605ac` (Task 3 commit)

**2. [Rule 2 - 누락된 필수 기능] PassTransaction의 CHECK 제약 변경에 conventions §10.0 기준 테스트가 필요했다**
- **Found during:** Task 3 작업 중 (PLAN.md의 `<files>` 목록에 테스트 파일이 없음을 확인)
- **Issue:** PLAN.md Task 3의 `<files>`에는 테스트 파일이 없었지만, `docs/conventions.md` §10.0 표는 "리포지토리 커스텀 쿼리/제약"과 "DB 관련"에 Testcontainers 통합테스트를 필수로 요구한다. `ck_pass_transaction_subject`는 "정확히 하나" 불변식의 최종 방어선(V8)이고, `<behavior>`에도 "주체를 둘 다 넣거나 둘 다 비우면 CHECK로 저장이 실패한다"가 명시돼 있어 이 동작을 실제 PostgreSQL로 검증하지 않으면 회귀를 잡을 수 없다. CLAUDE.md 문서 우선순위(`docs/conventions.md` > `.planning/**`)에 따라 conventions.md가 이긴다.
- **Fix:** 기존 `PassRepositoryTest.kt`(같은 관심사를 이미 다루는 Testcontainers 통합테스트)에 3개 테스트를 추가했다 — 회원 주체 저장 성공 1건, CHECK 위반 2건(관리자·회원 둘 다 채움 / 둘 다 비움). 새 테스트 파일을 만들지 않고 기존 파일에 추가해 스프링 컨텍스트가 늘어나지 않게 했다(conventions §10.1).
- **Files modified:** src/test/kotlin/com/goldwrestling/pass/PassRepositoryTest.kt
- **Verification:** `./gradlew test --tests "com.goldwrestling.pass.*"` 전체 그린(신규 3건 포함).
- **Committed in:** `a9605ac` (Task 3 commit)

**3. [Rule 1 - Bug] ReservationRepositoryTest의 "같은 회원·같은 날짜·시각 중복 예약" 테스트 fixture가 의도하지 않은 제약(uq_class_session)에서 먼저 실패**
- **Found during:** Task 2 테스트 최초 실행
- **Issue:** `ux_reservation_member_timeslot_active`(회원+날짜+시각 부분 유니크 인덱스)를 검증하려고 같은 `classDate`·`startTime`의 두 `ClassSession`을 만들었는데, 두 세션이 같은 `ClassSchedule`(테스트 헬퍼가 항상 같은 행을 반환)을 참조해 `class_session`의 `uq_class_session(class_schedule_id, class_date)` 제약이 먼저 위반됐다 — 테스트가 검증하려던 제약과 다른 제약에서 실패해 의도가 흐려졌다.
- **Fix:** 테스트 헬퍼가 `scheduleIndex` 파라미터로 서로 다른 `ClassSchedule` 행을 선택하도록 수정해 두 세션이 서로 다른 시간표를 참조하게 했다 — `ClassSession.classDate`/`startTime`은 애플리케이션이 자유롭게 채우는 비정규화 컬럼이라 실제 시간표 값과 일치할 필요가 없다.
- **Files modified:** src/test/kotlin/com/goldwrestling/reservation/ReservationRepositoryTest.kt
- **Verification:** `./gradlew test --tests "com.goldwrestling.reservation.ReservationRepositoryTest"` 7개 전부 통과, 의도한 제약(`ux_reservation_member_timeslot_active`)에서 정확히 실패함을 확인.
- **Committed in:** `d5a692b` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (Rule 3 1건 — 계획 문서와 실제 코드 위치 불일치, Rule 2 1건 — conventions.md가 요구하는 테스트 보강, Rule 1 1건 — 테스트 fixture 버그). 코드 로직·스키마 변경 없음.
**Impact on plan:** 스코프 확장 없음. 세 편차 모두 PLAN.md가 의도한 동작·범위를 그대로 유지하면서 실행 시점에 드러난 문서-코드 간극·컨벤션 요구·테스트 버그를 바로잡았다.

## Issues Encountered

None — Boot 4 API·신규 의존성 확인이 필요한 작업이 없었다(기존 스택만 재사용, 네이티브 쿼리는 04-02가 이미 검증한 표준 PostgreSQL 문법).

## User Setup Required

None - no external service configuration required. 로컬 검증은 `docker compose up -d` 후 `./gradlew build`로 전부 확인했다.

## Next Phase Readiness

- 04-04(예약 서비스)가 `ClassSessionRepository.insertIfAbsent`·`incrementReservedCountIfCapacityAvailable`·`ReservationRepository.cancelByMemberIfActive`·`PassRepository.adjustRemainingCount`를 그대로 조립해 예약 생성/취소 트랜잭션을 만들 수 있다
- `Reservation.assertCancelableByMember` 등 판정 메서드는 04-09가 TDD로 추가할 자리만 KDoc으로 예고돼 있다
- `NotificationService.create(...)`(생성 전용 헬퍼)는 04-04 이후 예약/휴강 서비스가 같은 트랜잭션 안에서 호출할 예정 — 이 플랜은 엔티티·저장 경로만 준비했다
- 블로커 없음 — `./gradlew ktlintFormat`(무변경) → `./gradlew build`(전체 테스트 그린, Testcontainers 포함) 확인 완료

---

## 이번에 쓴 기술

1. **네이티브 upsert(`INSERT ... ON CONFLICT DO NOTHING`)** — SQL 자체로 "이미 있으면 아무것도 안 함"을 표현하는 PostgreSQL 문법.
   **왜 필요했는가**: `ClassSession`은 "필요할 때 생성"되는데(D-094), 같은 (시간표, 날짜)로 두 회원이 동시에 처음 예약하면 둘 다 "이 세션이 없다"를 보고 둘 다 만들려고 시도한다. JPA `save()`로 만들고 실패하면 다시 조회하는 방식은, Hibernate가 제약 위반 예외를 던진 트랜잭션을 더 이상 신뢰할 수 없는 상태로 만들어 버려 이후 쿼리가 예기치 않게 실패할 수 있다.
   **안 썼으면 뭐가 깨지는가**: `save()` 후 `catch (DataIntegrityViolationException)`로 재조회하는 방식을 썼다면, 같은 트랜잭션 안의 다음 DB 작업(정원 조건부 UPDATE 등)이 "세션이 닫혔다"류의 알 수 없는 오류로 실패할 수 있다 — 예약이라는 핵심 기능이 동시 접속 상황에서만 재현되는 원인불명 500 에러로 무너진다.

2. **조건부 UPDATE(compare-and-swap)★** — `UPDATE ... WHERE 조건`으로 "조건이 지금도 참일 때만" 갱신하고, 갱신된 행 수(0 또는 1)로 성공 여부를 판단하는 패턴.
   **왜 필요했는가**: 정원 10명 수업에 마지막 한 자리를 두 회원이 동시에 예약하면, 둘 다 "9/10, 자리 있음"을 읽고 둘 다 저장을 시도할 수 있다. `reserved_count + 1 <= capacity` 조건을 UPDATE 문 자체에 걸어 두면, 먼저 도착한 요청이 그 조건을 만족시켜 10으로 만들고, 뒤에 온 요청은 UPDATE 시점에 이미 조건(10+1<=10)이 거짓이 되어 0행이 갱신된다 — 애플리케이션이 "자리가 있는지" 미리 확인하는 시점과 실제로 반영하는 시점 사이의 틈을 DB 자신이 메운다.
   **안 썼으면 뭐가 깨지는가**: "조회 → 확인 → 저장" 3단계로 나눠 처리했다면, 확인과 저장 사이에 다른 트랜잭션이 끼어들어 정원 10명 수업에 11명이 예약되는 초과 예약이 발생한다 — 이 프로젝트의 Core Value("초과 예약 0건")가 정면으로 깨진다.

3. **부분 유니크 인덱스(Partial Unique Index)★** — `CREATE UNIQUE INDEX ... WHERE 조건`으로 테이블 전체가 아니라 조건을 만족하는 행에만 유일성을 강제하는 PostgreSQL 기능.
   **왜 필요했는가**: "취소한 타임은 다시 예약할 수 있다"(D-090)와 "같은 세션에 같은 회원이 중복 예약할 수 없다"를 동시에 만족시켜야 한다. 일반 유니크 제약(`UNIQUE(class_session_id, member_id)`)을 걸면 취소된 예약 행도 유일성 검사에 포함돼 재예약이 막힌다. `WHERE status = 'ACTIVE'`를 인덱스에 붙이면 "활성 예약끼리만" 유일하면 된다는 규칙을 DB가 그대로 강제한다.
   **안 썼으면 뭐가 깨지는가**: 애플리케이션 코드로 "활성 예약이 없는지" 미리 확인하고 저장하는 방식만 썼다면, 동시에 두 요청이 확인을 통과한 뒤 둘 다 저장돼 같은 자리가 이중 예약된다 — 조건부 UPDATE와 같은 근본 이유(확인과 저장 사이의 경쟁)다.

4. **엔티티 판정 메서드와 상태 반영의 역할 분리(D-072)** — 엔티티 메서드(`assertReservable` 등)는 "이 상태 전환이 허용되는가"만 판정해 예외를 던지고, 실제 필드 값 변경은 절대 하지 않는다. 실제 반영은 항상 리포지토리의 조건부 UPDATE가 담당한다.
   **왜 필요했는가**: 만약 판정 메서드가 `status = CANCELED`처럼 필드를 직접 바꾸고 그 엔티티를 `save()`한다면, "판정 시점의 상태"와 "저장 시점의 상태" 사이에 다른 트랜잭션이 끼어들 여지가 다시 생긴다 — 조건부 UPDATE로 막은 경쟁을 엔티티 메서드가 우회로로 재생산하는 셈이다. Phase 3에서 실제로 이 패턴 위반이 회귀 버그(T-03-38)의 원인이었다.
   **안 썼으면 뭐가 깨지는가**: 다음 개발자(또는 AI)가 "간단해 보이니까"라며 판정 메서드에 대입문을 추가하면, 코드는 컴파일도 되고 단위테스트도 통과하지만, 동시 요청 상황에서만 조용히 정합성이 깨진다 — 가장 찾기 어려운 종류의 버그다. 이 원칙을 KDoc과 acceptance criteria의 grep 검사로 이중 방어했다.

5. **주체 nullable 쌍 + CHECK 제약** — 한 컬럼에 "타입 코드"를 두는 대신, `admin: Admin?`/`member: Member?` 두 개의 nullable 외래키를 두고 "정확히 하나만 채워짐"을 DB CHECK로 강제하는 패턴(`RefreshToken`이 이미 쓰던 방식).
   **왜 필요했는가**: `PassTransaction`(차감 이력)과 `Reservation`(취소 이력)은 "누가 이 행동을 했는가"가 회원일 수도 관리자일 수도 있다. 별도 "주체 타입" enum 컬럼을 두면, 그 값과 실제 채워진 외래키가 서로 어긋나는(예: 타입은 MEMBER인데 admin_id도 채워진) 상태가 애플리케이션 버그로 발생할 수 있다. nullable 쌍 + CHECK는 이런 불일치 자체를 DB가 저장 시점에 차단한다.
   **안 썼으면 뭐가 깨지는가**: "누가 이 예약을 취소했는지"를 감사할 때 주체 타입 컬럼과 실제 외래키가 어긋나 있으면 잘못된 사람에게 책임을 묻거나, 최악의 경우 둘 다 비어 "아무도 하지 않은 취소"라는 논리적으로 불가능한 행이 남는다 — 이 프로젝트가 예약·차감마다 요구하는 감사 가능성(CLAUDE.md 규칙 6)이 깨진다.

일부러 쓰지 않은 것: **낙관적 락(`@Version`)**. 정원·1:1 슬롯 경쟁에 `@Version` 필드를 쓰면 충돌 시 예외가 나고 애플리케이션이 재시도해야 하는데, 마지막 한 자리를 두고 여러 요청이 몰릴수록 재시도가 폭증해 오히려 응답 지연이 커진다. 이 프로젝트는 D-021에서 이미 "DB 제약 + 조건부 갱신"을 우선으로 정했고, 조건부 UPDATE 한 번으로 재시도 없이 결론이 나므로 낙관적 락을 도입할 이유가 없었다.

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-07*

## Self-Check: PASSED

All 18 created/modified files verified present on disk (schedule 7·reservation 4·notification 3·pass 4). All 3 task commits (`cfd73aa`, `d5a692b`, `a9605ac`) verified in git log.
