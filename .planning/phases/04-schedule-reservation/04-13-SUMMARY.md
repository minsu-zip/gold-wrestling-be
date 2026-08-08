---
phase: 04-schedule-reservation
plan: 13
subsystem: api
tags: [kotlin, spring-boot, jpa, postgresql, reservation, pass, admin]

# Dependency graph
requires:
  - phase: 04-schedule-reservation (04-11, 04-12)
    provides: AdminReservationService/Controller 골격, ReservationRepository·ClassSessionRepository 조건부 UPDATE, ReservationRefundPolicy, MemberReservationService 차감/복구 경로
provides:
  - AdminReservationService.cancelByAdmin/changeByAdmin (RESV-08 대리 취소·변경)
  - POST /api/admin/reservations/{id}/cancellation, .../change 엔드포인트
  - AdminPassService.cancel 활성 예약 선행 검사 (D-089)
  - ReservationLedgerSupport — 예약 생성/취소 복구 실행부를 회원·관리자 경로가 공유하는 컴포넌트
affects: [04-14, 04-15]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "차감/복구 실행부를 서비스 사이에 공유하는 내부 @Component(ReservationLedgerSupport) — 각 서비스는 CAS 상태 전환(actor별로 다른 조건부 UPDATE)만 직접 수행하고, 세션 정원 복구·잔여 판정·PassTransaction 저장은 공유 컴포넌트에 위임"
    - "pass→reservation 패키지 참조는 AdminPassService.cancel의 existsByPassIdAndStatus 단일 메서드로만 결합 (기능별 패키지 원칙의 유일한 역전 지점)"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/reservation/ReservationLedgerSupport.kt
    - src/main/kotlin/com/goldwrestling/reservation/dto/AdminCancelReservationRequest.kt
    - src/main/kotlin/com/goldwrestling/reservation/dto/AdminChangeReservationRequest.kt
    - src/test/kotlin/com/goldwrestling/reservation/AdminReservationCancellationTest.kt
    - src/test/kotlin/com/goldwrestling/pass/PassCancellationWithReservationTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/reservation/AdminReservationService.kt
    - src/main/kotlin/com/goldwrestling/reservation/AdminReservationController.kt
    - src/main/kotlin/com/goldwrestling/reservation/MemberReservationService.kt
    - src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt

key-decisions:
  - "MemberReservationService의 private 헬퍼(createReservationInternal/performMemberCancellation)를 reservation 패키지의 새 @Component ReservationLedgerSupport로 추출 — AdminReservationService도 같은 경로를 재사용해야 D-021(모든 잔여 변경이 이력을 남긴다) 보장이 두 벌로 흩어지지 않는다"
  - "관리자 대리 변경의 새 예약 생성은 ReservationWindow 검사를 건너뛴다(enforceWindow=false) — policies §3 '관리자는 예약 창 제약 없음'을 반영, 정원 조건부 UPDATE는 그대로 수행해 오버부킹 백도어를 만들지 않는다"
  - "AdminPassService.cancel의 활성 예약 선행 검사는 이용권 조회 직후, 다른 판정보다 먼저 수행 — 조건부 UPDATE 이후에 두면 거부 사유가 다른 실패(경쟁 패배·잔여 충돌)와 뒤섞인다"

requirements-completed: [RESV-08, NOTIF-01]

duration: 65min
completed: 2026-08-08
---

# Phase 04 Plan 13: 관리자 대리 취소·변경 + 이용권 등록 취소 선행 조건 Summary

**관리자 대리 취소(복구 선택)·변경 API 2종과, 활성 예약이 있는 이용권의 등록 취소를 거부하는 D-089 2단계 절차를 구현하고, 예약 생성/복구 실행부를 회원·관리자 경로가 공유하는 `ReservationLedgerSupport`로 리팩터링했다**

## Performance

- **Duration:** ~65 min
- **Tasks:** 2
- **Files modified:** 9 (5 created, 4 modified)

## Accomplishments

- `AdminReservationService.cancelByAdmin` — 당일·과거 제약 없이 예약을 대리 취소, `refund` 선택(기본 true), 이력 주체가 관리자로 기록됨(`PassTransaction.member == null`)
- `AdminReservationService.changeByAdmin` — 취소+재예약을 한 트랜잭션으로, 예약 창 제약 없음, 정원 조건부 UPDATE는 그대로 거쳐 오버부킹 백도어 없음
- `AdminPassService.cancel`에 활성 예약 선행 검사(D-089) 추가 — 활성 예약이 있으면 409 `PASS_HAS_ACTIVE_RESERVATION`으로 거부, 관리자가 대리 취소로 먼저 정리하면 성공하는 2단계 절차를 통합테스트로 실증
- `ReservationLedgerSupport` 신설 — 예약 생성(차감)·취소 복구 실행부를 `MemberReservationService`·`AdminReservationService`가 공유하도록 추출(회원 경로 리팩터링 포함, 행위 불변 확인)

## Task Commits

1. **Task 1: 관리자 대리 취소·변경 서비스 + 엔드포인트**
   - `c58ed91` test(04-13): 관리자 대리 취소·변경 통합테스트 추가 (RED)
   - `557a601` feat(04-13): 관리자 대리 취소·변경 구현 (GREEN, RESV-08)
2. **Task 2: 이용권 등록 취소 선행 조건 (D-089)**
   - `69dd968` test(04-13): 이용권 등록 취소 선행 조건(D-089) 통합테스트 추가 (RED)
   - `5ddd397` feat(04-13): 이용권 등록 취소 선행 조건 구현 (GREEN, D-089)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/reservation/ReservationLedgerSupport.kt` — 예약 생성(차감)·취소 복구(잔여 판정+이력) 공유 컴포넌트, `admin`/`member` 파라미터로 이력 주체를 결정
- `src/main/kotlin/com/goldwrestling/reservation/dto/AdminCancelReservationRequest.kt` — `refund: Boolean = true`
- `src/main/kotlin/com/goldwrestling/reservation/dto/AdminChangeReservationRequest.kt` — 회원용과 동일 형태(시간표 id + 날짜)
- `src/main/kotlin/com/goldwrestling/reservation/AdminReservationService.kt` — `cancelByAdmin`/`changeByAdmin` 추가
- `src/main/kotlin/com/goldwrestling/reservation/AdminReservationController.kt` — `POST .../cancellation`, `POST .../change` 추가
- `src/main/kotlin/com/goldwrestling/reservation/MemberReservationService.kt` — `createReservationInternal`/`performMemberCancellation` 제거, `ReservationLedgerSupport` 위임으로 대체(행위 불변)
- `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt` — `cancel`에 `existsByPassIdAndStatus` 선행 검사 추가
- `src/test/kotlin/com/goldwrestling/reservation/AdminReservationCancellationTest.kt` — 대리 취소·변경 통합테스트 17종(서비스 레벨 취소 7종 + 변경 4종 + HTTP 계약 6종)
- `src/test/kotlin/com/goldwrestling/pass/PassCancellationWithReservationTest.kt` — D-089 통합테스트 6종

## Decisions Made

- 차감/복구 실행부를 `ReservationLedgerSupport`(새 `@Component`)로 추출해 `MemberReservationService`·`AdminReservationService`가 공유 — plan의 action 지시("두 서비스가 공유할 수 있게 복구 처리 헬퍼를 한 곳에 두고 양쪽이 호출한다")를 그대로 따랐으며, `files_modified`에는 없었지만 acceptance criteria("차감/복구 로직이 복제되지 않았다")를 충족하기 위한 필수 리팩터링이었다
- 관리자 대리 변경의 새 예약 생성은 `enforceWindow=false`로 `ReservationWindow.assertBookable`을 건너뛴다 — "당일·지난 날짜로도 변경할 수 있다" 요구를 만족하려면 회원 경로의 창 검사를 그대로 재사용할 수 없었다
- `AdminPassService.cancel`이 `reservation` 패키지를 참조하는 유일한 지점을 `ReservationRepository.existsByPassIdAndStatus` 단일 메서드 주입으로 최소화 — `ReservationService`·`Reservation` 엔티티는 참조하지 않는다

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - 아키텍처 요구사항의 files_modified 누락 보완] `ReservationLedgerSupport.kt` 신설 + `MemberReservationService.kt` 리팩터링**
- **Found during:** Task 1 설계 단계
- **Issue:** Task 1 `<files>` 목록에는 `AdminReservationService.kt`·`AdminReservationController.kt`·DTO 2종·테스트만 있었지만, `<action>` 지시와 acceptance criteria("차감/복구 로직이 MemberReservationService와 AdminReservationService에 복제되지 않았다")는 공유 컴포넌트 신설과 `MemberReservationService` 리팩터링을 명시적으로 요구했다
- **Fix:** `reservation` 패키지에 `ReservationLedgerSupport`를 신설해 예약 생성 실행부(정원·차감 조건부 UPDATE·`Reservation`/`PassTransaction` INSERT)와 취소 복구 실행부(세션 정원 복구·`ReservationRefundPolicy` 판정·잔여 복구·`PassTransaction` INSERT)를 옮기고, 두 서비스가 이를 호출하도록 배선
- **Files modified:** `ReservationLedgerSupport.kt`(신규), `MemberReservationService.kt`
- **Verification:** `MemberReservationCancellationTest`·`MemberReservationServiceTest`·`MemberReservationControllerTest` 등 04-07·04-10 기존 테스트 전부 그대로 통과(행위 불변 확인), `AdminReservationCancellationTest` 17종 통과
- **Committed in:** `557a601` (Task 1 GREEN 커밋)

---

**Total deviations:** 1 auto-fixed (Rule 2 — plan 본문이 요구했으나 files_modified 목록에서 누락된 리팩터링)
**Impact on plan:** plan이 명시한 아키텍처 요구를 충족하기 위한 필수 작업. 범위 확장(scope creep) 아님 — plan의 `<action>` 문구를 그대로 실행한 것이다.

## Issues Encountered

- **테스트 픽스처의 `@Modifying` 리포지토리 메서드 직접 호출 실패**: `AdminReservationCancellationTest`는 실제 커밋/롤백을 검증해야 해서(정원 초과로 변경이 실패하면 기존 예약이 그대로 남는지) 클래스에 `@Transactional`을 붙이지 않았는데, 이 상태에서 픽스처가 `ClassSessionRepository.incrementReservedCountIfCapacityAvailable`(`@Modifying`)을 직접 호출하면 `TransactionRequiredException`이 났다 — 원인은 커스텀 `@Query @Modifying` 메서드가 별도 트랜잭션 경계 없이 호출되면 flush가 실패하기 때문. `ClassSession`을 이미 원하는 `reservedCount`로 직접 구성하는 방식으로 픽스처를 바꿔 해결(SUT 코드는 변경 없음)
- **테스트 cleanUp의 FK 순서 누락**: 관리자 대리 조작 이력(`PassTransaction`)은 `member_id`가 항상 `null`이라 `MemberReservationCancellationTest`의 cleanUp 패턴(`member_id in (...)` 필터)이 걸리지 않아 `pass` 삭제가 FK 위반으로 실패했다 — `pass_id` 경유 삭제로 수정. 또 `HttpContract` 테스트가 발급한 `refresh_token`을 지우지 않아 `member`/`admin` 삭제가 실패했다 — `refresh_token` 삭제 단계를 추가
- 둘 다 테스트 인프라 문제였고 프로덕션 코드에는 영향이 없었다

## User Setup Required

None - no external service configuration required.

**openapi.yaml 재생성 보류**: 이 플랜에서 API 표면이 바뀌었지만(`AdminCancelReservationRequest`·`AdminChangeReservationRequest`·엔드포인트 2개), plan의 `<verification>`이 이미 "openapi.yaml 재생성은 청크 C 마지막 플랜(04-15)에서 한 번에 한다"고 명시했다 — 04-15에서 `docker compose up -d && ./gradlew generateApiDocs`로 한 번에 갱신 예정이다. 이 실행 중에는 `git diff docs/api/openapi.yaml`이 비어 있는 것이 정상이다.

## Next Phase Readiness

- 관리자가 회원을 대신해 예약을 취소·변경할 수 있고, 그 이력이 관리자 주체로 정확히 남는다
- 이용권 오등록 정정이 예약 데이터와 충돌 없이 2단계 절차(대리 취소 → 등록 취소)로만 가능해졌다
- `ReservationLedgerSupport` 공유 컴포넌트가 확보되어, 이후 플랜(휴강 처리 등 D-021 관련 잔여 변경 경로)도 같은 패턴을 재사용할 수 있다
- 04-15에서 openapi.yaml 재생성 + PR 생성(D-084) 필요

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-08*

## Self-Check: PASSED

All 9 created/modified files confirmed present on disk; all 4 task commit hashes (`c58ed91`, `557a601`, `69dd968`, `5ddd397`) confirmed in `git log`.
