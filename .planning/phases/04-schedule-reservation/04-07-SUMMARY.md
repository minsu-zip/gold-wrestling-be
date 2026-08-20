---
phase: 04-schedule-reservation
plan: 07
subsystem: api
tags: [kotlin, spring-boot, jpa, transactional, tdd, reservation]

# Dependency graph
requires:
  - phase: 04-schedule-reservation
    plan: "04-03"
    provides: "schedule/reservation/notification 엔티티·리포지토리 골격, PassRepository.adjustRemainingCount 조건부 UPDATE 관례"
  - phase: 04-schedule-reservation
    plan: "04-05"
    provides: "ClassSessionService.getOrCreate(세션 실체화 단일 진입점), ReservationWindow.assertBookable"
  - phase: 04-schedule-reservation
    plan: "04-06"
    provides: "ReservationPassPolicy.requiredPassType/selectCandidate, PassRepository.findDeductionCandidates"
provides:
  - "reservation/MemberReservationService.reserve — 예약 생성 10단계 트랜잭션 조립(세션 확보→판정→정원→차감→예약→이력→알림)"
  - "notification/NotificationService — 알림 생성 4개 팩토리 메서드(createReservationCreated/Canceled/Changed, createClassSessionSuspended), 자체 트랜잭션 없음"
  - "reservation/dto/{ReserveRequest,ReservationResponse} — 04-08 컨트롤러가 그대로 노출할 요청·응답 DTO"
  - "reservation/ReservationRepository.existsByMemberIdAndClassDateAndStartTimeAndStatus — 중복 예약 사전 검사"
affects: ["04-08(컨트롤러가 MemberReservationService.reserve를 호출하고 MemberStateGate.requireActive를 추가로 호출)", "04-09(취소·변경이 NotificationService의 나머지 3개 메서드를 그대로 쓴다)", "04-14(휴강 처리가 createClassSessionSuspended를 그대로 쓴다)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "생성 전용 헬퍼 서비스(자체 @Transactional 없음) — NotificationService는 항상 호출부 트랜잭션에 편승한다. 이 저장소에 '조회 없이 생성만 하는 서비스' 전례가 없어 이번에 확립"
    - "다단계 조건부 UPDATE 조립 + 마지막 clearAutomatically 이후 일괄 재조회 — AdminPassService의 '조회→판정→조건부UPDATE→재조회→이력저장' 순서를 3단계(정원·차감·저장) 조립으로 확장"
    - "서비스 자체 트랜잭션 경계를 검증하는 통합테스트는 테스트 메서드에 @Transactional을 붙이지 않는다 — 참여 트랜잭션의 rollback-only 마킹은 테스트 종료 시점에야 반영되므로, '롤백 후 DB 상태 불변'을 검증하려면 각 서비스 호출이 실제로 커밋/롤백되는 독립 트랜잭션이어야 한다(동시성 테스트와 같은 이유, 다른 문제)"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/reservation/MemberReservationService.kt
    - src/main/kotlin/com/goldwrestling/reservation/dto/ReserveRequest.kt
    - src/main/kotlin/com/goldwrestling/reservation/dto/ReservationResponse.kt
    - src/test/kotlin/com/goldwrestling/reservation/MemberReservationServiceTest.kt
    - src/test/kotlin/com/goldwrestling/notification/NotificationServiceTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/notification/NotificationService.kt
    - src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt

key-decisions:
  - "취소·변경 알림 메시지 문구(휴강 알림 외)는 PLAN.md가 정확한 문장을 지정하지 않아 기존 예약 알림 문구 톤을 따라 직접 작성 — '{회원명}님이/의 {M/d HH:mm} {수업종류} 예약을 취소/변경했습니다', byAdmin일 때 '관리자가 ' 접두 + 조사(이→의)만 바꿔 재사용"
  - "MemberReservationServiceTest는 클래스 레벨 @Transactional을 붙이지 않는다 — reserve()가 실패 시 실제로 DB에 롤백되는지(잔여·정원 불변)를 검증해야 하는데, 테스트가 @Transactional이면 reserve()의 트랜잭션이 테스트 트랜잭션에 참여해 rollback-only 마킹만 될 뿐 테스트 안에서는 아직 반영되지 않는다. ClassSessionConcurrencyTest와 같은 이유로 클래스 레벨 @Transactional을 빼고 @AfterEach에서 직접 정리했다"

requirements-completed: [RESV-01, RESV-02, NOTIF-01]

# Metrics
duration: ~25min
completed: 2026-08-07
---

# Phase 04 Plan 07: 예약 생성 트랜잭션 + 관리자 알림 헬퍼 Summary

**MemberReservationService.reserve가 시간표 조회부터 정원 조건부 UPDATE·이용권 차감·예약 INSERT·PassTransaction 이력·관리자 알림까지 10단계를 하나의 @Transactional로 원자 조립하고, NotificationService가 4개 알림 팩토리 메서드를 트랜잭션 편승 헬퍼로 제공한다.**

## Performance

- **Duration:** 약 25분
- **Started:** 2026-08-07T20:10Z경 (컨텍스트 로딩 시작)
- **Completed:** 2026-08-07T20:36:28+09:00 (마지막 GREEN 커밋)
- **Tasks:** 2/2 (Task 1: NotificationService, Task 2: MemberReservationService.reserve)
- **Files modified:** 7개 (신규 5 + 기존 2 수정)

## Accomplishments

- `NotificationService` — `createReservationCreated`/`createReservationCanceled`/`createReservationChanged`/`createClassSessionSuspended` 4개 팩토리 메서드를 구현했다. 클래스에 `@Transactional`이 없다(호출부 트랜잭션 편승, D-020 연장) — `NotificationServiceTest` 7개가 `<behavior>` 전 항목(비정규화 값·isRead/readAt 초기값·occurredAt 시각 고정·휴강 알림의 reservation null/classSession non-null·수업 종류 한글 라벨)을 실제 PostgreSQL로 검증한다
- `MemberReservationService.reserve` — 회원 조회 → 시간표 조회(지점·요일 검증) → 예약 창 판정 → 이용권 종류 판정(저녁반 거부) → 세션 get-or-create·휴강 검사 → 중복 예약 사전 검사 → 정원 조건부 UPDATE → 차감 후보 선정 → 이용권 차감(조건부 UPDATE) → `Reservation` INSERT → `PassTransaction` INSERT(회원 주체) → 알림 생성까지 10단계를 순서 그대로 구현했다. 각 단계에 번호 주석을 남겨 순서 변경이 롤백 범위·에러코드에 미치는 영향을 코드에서 바로 추적할 수 있게 했다
- `MemberReservationServiceTest` 10개로 정상 예약(SESSION/LESSON) 2건과 실패 8종(저녁반/휴강/예약창-다음주/예약창-이미시작/중복/정원초과/잔여부족/PassTransaction 주체·금액)을 실제 PostgreSQL로 증명했다. 실패 케이스마다 잔여·`reserved_count`가 요청 전과 동일함을 함께 단언한다
- 정원 초과(⑦)·이용권 부족(⑨) 모두 재시도 루프 없이 즉시 실패시킨다(CONTEXT.md Claude's Discretion 확정) — `while`/`retry` 부재를 grep으로 확인
- `ReservationRepository.existsByMemberIdAndClassDateAndStartTimeAndStatus`를 추가해 중복 예약 사전 검사를 구현했다 — 실제 방어는 부분 유니크 인덱스(`ux_reservation_member_timeslot_active`, V6, D-021)이고 이 메서드는 정확한 에러 안내용임을 KDoc에 명시
- `./gradlew test --tests "com.goldwrestling.pass.*"` 전체 통과 확인(Phase 3 회귀 없음), `./gradlew build` 그린

## Task Commits

Each cycle was committed as RED → GREEN:

1. **Task 1 RED: NotificationServiceTest 7개 실패 확인** - `03ec9dd` (test)
2. **Task 1 GREEN: NotificationService 4개 팩토리 메서드 구현** - `517f838` (feat)
3. **Task 2 RED: MemberReservationServiceTest 10개 실패 확인** - `576baf5` (test)
4. **Task 2 GREEN: MemberReservationService.reserve 구현** - `b27f144` (feat)

REFACTOR 단계는 두 사이클 모두 건너뛰었다 — GREEN 구현이 이미 04-06까지 확립된 관례(조건부 UPDATE, 판정 vs 반영 분리, 재조회 후 저장)를 그대로 재사용해 별도로 정리할 중복이 없었다.

**Plan metadata:** 본 커밋(SUMMARY + STATE + ROADMAP)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/notification/NotificationService.kt` — 알림 생성 4개 팩토리 메서드
- `src/main/kotlin/com/goldwrestling/reservation/MemberReservationService.kt` — 예약 생성 10단계 트랜잭션 조립
- `src/main/kotlin/com/goldwrestling/reservation/dto/ReserveRequest.kt` — 예약 생성 요청(시간표 id + 날짜, classSessionId 없음, D-094)
- `src/main/kotlin/com/goldwrestling/reservation/dto/ReservationResponse.kt` — 예약 응답(엔티티 비노출, D-019)
- `src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt` — 중복 예약 사전 검사 메서드 추가
- `src/test/kotlin/com/goldwrestling/notification/NotificationServiceTest.kt` — 통합테스트 7개
- `src/test/kotlin/com/goldwrestling/reservation/MemberReservationServiceTest.kt` — 통합테스트 10개

## Decisions Made

- 취소·변경 알림의 정확한 메시지 문구는 PLAN.md가 예약·휴강 알림만 예시로 지정했다 — 나머지 2종(취소·변경)은 같은 톤("{회원명}님이/의 ... 예약을 취소/변경했습니다", 관리자 대리 시 "관리자가 " 접두)으로 직접 작성했다. 문구 자체가 도메인 규칙이 아니라 표시 문자열이라 Rule 4(아키텍처 결정) 대상이 아니라고 판단했다
- `MemberReservationServiceTest`에 클래스 레벨 `@Transactional`을 붙이지 않기로 했다 — `reserve()`가 실패했을 때 실제로 DB가 롤백되는지(요구사항 핵심: "실패 시 잔여·예약 인원·행이 하나도 남지 않는다")를 검증하려면, 테스트가 그 트랜잭션에 참여(REQUIRED)하지 않고 매 호출이 독립적으로 커밋/롤백돼야 한다. 테스트가 `@Transactional`이면 실패한 `reserve()` 호출의 rollback-only 마킹이 테스트가 끝날 때까지 실제 DB에 반영되지 않아, 검증 시점에 잘못된(아직 커밋된 것처럼 보이는) 상태를 읽게 된다 — `ClassSessionConcurrencyTest`가 동시성 재현을 위해 같은 선택을 한 것과 근본 이유는 다르지만 결론(클래스 레벨 `@Transactional` 배제 + `@AfterEach` 수동 정리)은 같다

## Deviations from Plan

None - plan executed exactly as written. Task 1·2 모두 `<action>` 절차 그대로 구현했고, 인터페이스 절이 제공한 기존 컴포넌트(ClassSessionService.getOrCreate, ReservationWindow.assertBookable, ReservationPassPolicy, PassRepository.findDeductionCandidates/adjustRemainingCount)를 재구현 없이 그대로 조립했다.

## Issues Encountered

None — Boot 4 API·신규 의존성 확인이 필요한 작업이 없었다(기존 스택·기존 조건부 UPDATE 관례만 재사용).

## Testing Note (CLAUDE.md 규칙 10)

이 플랜의 프로덕션 코드(`NotificationService`, `MemberReservationService`, DTO 2종, `ReservationRepository` 추가 메서드)는 전부 conventions §10.0의 "서비스 메서드"·"리포지토리 커스텀 쿼리" 행에 해당해 단위/통합테스트 필수 대상이다. `tdd="true"` 지시대로 두 사이클 모두 RED(실패 확인, `TODO()` 스텁이 `NotImplementedError`를 던지는 것으로 확보) → GREEN(구현) 순서를 지켰다. DTO 2종(ReserveRequest/ReservationResponse)은 필드 나열 + companion factory뿐이라 별도 단위테스트를 추가하지 않았다 — `ReservationResponse.from`의 변환 로직은 `MemberReservationServiceTest`의 성공 케이스 단언이 이미 검증한다.

## API 표면 변경 관련 (openapi.yaml 미재생성 사유)

`ReserveRequest`/`ReservationResponse` DTO를 새로 만들었으나 **이 플랜은 컨트롤러를 만들지 않는다**(PLAN.md `<verification>`에 명시: "API 노출과 동시성 실증은 04-08이 같은 청크 안에서 이어서 한다", D-084). 아직 어떤 `@RestController`도 이 DTO들을 참조하지 않아 springdoc이 문서화할 신규 엔드포인트가 없다 — `openapi.yaml` 재생성은 04-08에서 컨트롤러가 실제로 붙는 시점에 함께 한다.

## User Setup Required

None - no external service configuration required. 로컬 검증은 `docker compose up -d`(이미 기동 중) 후 `./gradlew ktlintFormat` → `./gradlew build`로 전부 확인했다.

## Next Phase Readiness

- `MemberReservationService.reserve(memberId, ReserveRequest)`가 04-08의 컨트롤러가 그대로 호출할 진입점이다 — 04-08은 `MemberStateGate.requireActive(principal)` 호출과 `AuthenticatedPrincipal`에서 `memberId` 추출만 추가하면 된다
- `NotificationService`의 나머지 3개 메서드(`createReservationCanceled`/`createReservationChanged`/`createClassSessionSuspended`)는 04-09(취소·변경)·04-14(휴강)이 그대로 재사용한다
- 04-08이 컨트롤러를 추가하면 그 커밋에서 `openapi.yaml`을 재생성해야 한다(이번 플랜에서 보류한 재생성)
- 블로커 없음

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-07*

## Self-Check: PASSED

모든 생성/수정 파일(8개)과 커밋 해시(4개: 03ec9dd, 517f838, 576baf5, b27f144)를 실제로 확인했다.
