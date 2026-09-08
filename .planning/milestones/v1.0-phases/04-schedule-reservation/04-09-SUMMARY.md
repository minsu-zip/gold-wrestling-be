---
phase: 04-schedule-reservation
plan: 09
subsystem: api
tags: [kotlin, spring-boot, tdd, reservation, domain-policy]

# Dependency graph
requires:
  - phase: 04-schedule-reservation
    plan: "04-03"
    provides: "reservation/Reservation·ReservationExceptions·ReservationStatus 엔티티 골격"
  - phase: 04-schedule-reservation
    plan: "04-06"
    provides: "판정(object) vs 조회(@Query) 역할 분리 관례, ReservationPassPolicy와 같은 reservation 패키지 배치 원칙"
provides:
  - "Reservation.assertCancelableByMember/assertCancelableByAdmin/assertChangeableByMember/assertChangeableByAdmin — 취소·변경 가능 여부 판정 4종(대입문 없음, D-072)"
  - "reservation/ReservationRefundPolicy.shouldRestore — 취소 시 이용권 복구 수행 여부 순수 판정(D-091, Pitfall 2)"
affects: ["04-10(회원 취소·변경 서비스가 이 4개 판정 메서드와 shouldRestore를 그대로 호출), 04-11(관리자 대리 취소·변경이 assertCancelableByAdmin/assertChangeableByAdmin과 shouldRestore를 재사용)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "당일 판정은 LocalDate 비교만, 마감 판정(ReservationWindow)과 축을 분리 — 판정 메서드 시그니처에 LocalDateTime/OffsetDateTime/Clock이 등장하지 않는 것으로 구조적으로 강제(Pitfall 3)"
    - "adjustRemainingCount 0행의 이중 의미(경쟁 패배 vs 정책상 복구 안 함)를 호출 전 판정으로 분리 — ReservationRefundPolicy.shouldRestore가 false면 adjustRemainingCount를 아예 호출하지 않는다(Pitfall 2)"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/reservation/ReservationRefundPolicy.kt
    - src/test/kotlin/com/goldwrestling/reservation/ReservationCancellationPolicyTest.kt
    - src/test/kotlin/com/goldwrestling/reservation/ReservationRefundPolicyTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/reservation/Reservation.kt

key-decisions:
  - "assertCancelableByMember는 당일(classDate == today)과 과거(classDate < today)를 !classDate.isAfter(today) 한 조건으로 함께 거부한다 — policies §3 문구가 두 케이스를 나눠 설명하지만 실제 판정 로직은 하나의 부등식으로 충분하고, 지난 수업을 당일보다 강하게 막는다는 취지가 그대로 유지된다"
  - "ReservationCancellationPolicyTest와 ReservationRefundPolicyTest 사이에 공유할 픽스처가 없어 REFACTOR 단계를 생략했다 — 전자는 Reservation/Pass/ClassSession 전체 그래프를 조립하는 픽스처가 필요하고 후자는 PassStatus enum 값만 필요해 추상화하면 오히려 간접성만 늘어난다"

requirements-completed: [RESV-04]

# Metrics
duration: ~15min
completed: 2026-08-07
---

# Phase 04 Plan 09: 예약 취소·변경 판정 + 복구 대상 판정 Summary

**`Reservation`에 취소·변경 가능 여부 판정 메서드 4종을, `reservation` 패키지에 `ReservationRefundPolicy.shouldRestore`를 두 번의 RED→GREEN 사이클로 추가해, 당일 판정을 날짜 축으로만 강제하고(Pitfall 3) 취소 복구 여부를 호출 전에 결정하는 순수 판정으로 분리했다(Pitfall 2).**

## Performance

- **Duration:** 약 15분
- **Started:** 2026-08-07T20:38Z경 (컨텍스트 로딩 완료 후 RED 작성 시작)
- **Completed:** 2026-08-07T20:46+09:00 (마지막 빌드 검증)
- **Tasks:** 2/2 (사이클 1: Reservation 판정 메서드, 사이클 2: ReservationRefundPolicy)
- **Files modified:** 4개 (신규 3 + 기존 1 수정)

## Accomplishments

- `Reservation.assertCancelableByMember(today)` — 이미 취소된 예약은 재취소 거부, 당일·과거 수업은 `SameDayModificationNotAllowedException`으로 거부. `!classDate.isAfter(today)` 한 조건으로 당일·과거를 함께 막아 "지난 수업은 당일보다 강하게 막는다"는 정책을 표현한다
- `Reservation.assertCancelableByAdmin()` — 취소 여부만 검사, 당일·과거 제약 없음(policies §3 "관리자는 제약 없음")
- `Reservation.assertChangeableByMember(today, newClassType)` — `assertCancelableByMember`를 먼저 호출해 "변경으로 당일취소 우회"를 막고(T-04-40), 종류 불일치 시 `ReservationTypeMismatchException`
- `Reservation.assertChangeableByAdmin(newClassType)` — `assertCancelableByAdmin` + 종류 일치 검사
- `ReservationCancellationPolicyTest` 12개로 판정 4종 + 당일 경계 3종(어제/오늘/내일) + 변경 우회 방지 1종 + 종류 불일치 2종을 순수 Kotlin 단위테스트로 검증. 네 메서드 시그니처 어디에도 `LocalDateTime`/`OffsetDateTime`/`Clock`이 없어 당일 판정이 시각과 무관함을 구조적으로 증명한다
- `ReservationRefundPolicy.shouldRestore(passStatus, refundRequested)` — 복구 요청 없음 → false, 등록 취소(`CANCELED`) 이용권 → false, 그 외 → true. `endDate`를 파라미터로 받지 않아 만료된 이용권도 복구 대상임을 시그니처로 고정한다
- `ReservationRefundPolicyTest` 4개로 진리표 전 항목(요청 false/이용권 CANCELED/정상/만료됐지만 ACTIVE)을 검증

## Task Commits

Each cycle was committed as RED → GREEN:

1. **사이클 1 RED: 예약 취소·변경 판정 실패 테스트(컴파일 실패로 확보)** - `ce5d5e9` (test)
2. **사이클 1 GREEN: Reservation 판정 메서드 4종 구현** - `54aabd0` (feat)
3. **사이클 2 RED: 복구 수행 여부 판정 실패 테스트(컴파일 실패로 확보)** - `5650798` (test)
4. **사이클 2 GREEN: ReservationRefundPolicy 구현** - `78d4098` (feat)

REFACTOR 단계는 두 사이클 모두 건너뛰었다 — 두 테스트 파일이 공유할 만한 중복 픽스처가 없었다(사유는 key-decisions 참조).

**Plan metadata:** 본 커밋(SUMMARY + STATE + ROADMAP)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/reservation/Reservation.kt` — 판정 메서드 4종 추가(기존 필드·생성자 변경 없음)
- `src/main/kotlin/com/goldwrestling/reservation/ReservationRefundPolicy.kt` — 복구 수행 여부 순수 판정(object)
- `src/test/kotlin/com/goldwrestling/reservation/ReservationCancellationPolicyTest.kt` — 단위테스트 12개
- `src/test/kotlin/com/goldwrestling/reservation/ReservationRefundPolicyTest.kt` — 단위테스트 4개

## Decisions Made

- `assertCancelableByMember`의 당일·과거 검사를 `!classDate.isAfter(today)` 한 줄로 통합 — PLAN.md `<behavior>`가 두 불릿("당일이면 거부", "과거면 거부")으로 나눠 서술했지만, 두 조건이 같은 예외(`SameDayModificationNotAllowedException`)로 귀결되므로 별도 분기가 필요 없었다. 단순 구현 세부(Rule 4 대상 아님)
- `ReservationCancellationPolicyTest`·`ReservationRefundPolicyTest` 간 REFACTOR 생략 — PLAN.md가 "필요 시"로 조건부 지시했고, 실제로 공유 가능한 중복이 없었다(04-06/04-07 SUMMARY와 같은 판단)

## Deviations from Plan

None - plan executed exactly as written. `<must_haves>`의 artifacts·key_links·acceptance_criteria를 모두 충족했다(당일 판정 시그니처에 시각 타입 부재, 판정 메서드 대입문 없음, `ReservationRefundPolicy`가 object이고 `shouldRestore`에 날짜 파라미터 없음).

## Issues Encountered

None — Boot 4 API·신규 의존성 확인이 필요한 작업이 없었다(순수 Kotlin 도메인 로직만 추가, 기존 `Pass.kt`의 판정 전용 KDoc 관례를 그대로 재사용).

## Testing Note (CLAUDE.md 규칙 10)

이 플랜의 프로덕션 코드(`Reservation`의 판정 메서드 4종, `ReservationRefundPolicy`)는 conventions §10.0의 "엔티티 메서드/도메인 규칙" 행에 정확히 해당해 단위테스트 필수 대상이다. `type: tdd` 플랜 지시대로 두 사이클 모두 RED(컴파일 실패로 확보, 04-06 SUMMARY가 확립한 "시그니처만 있고 구현이 없어 참조 해석 실패" 방식과 동일 원리를 엔티티 메서드에 적용) → GREEN(구현) 순서를 지켰다.

## User Setup Required

None - no external service configuration required. 로컬 검증은 `./gradlew ktlintFormat` → `./gradlew build`(Phase 3·4 전체 스위트 그린, DB 필요 없는 순수 단위테스트라 Testcontainers 기동도 함께 확인)로 전부 확인했다.

## Next Phase Readiness

- `Reservation.assertCancelableByMember/Admin`·`assertChangeableByMember/Admin`과 `ReservationRefundPolicy.shouldRestore`가 04-10(회원 취소·변경 서비스)·04-11(관리자 대리 취소·변경)이 그대로 호출할 진입점이다 — 두 서비스는 당일 판정·복구 여부를 다시 판단하지 않고 이 5개 함수만 조합하면 된다
- `ReservationRepository`의 조건부 UPDATE(취소 상태 전환)는 아직 이 플랜의 범위가 아니다 — 04-10이 추가한다
- 블로커 없음

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-07*

## Self-Check: PASSED

모든 생성/수정 파일(4개)과 커밋 해시(4개: ce5d5e9, 54aabd0, 5650798, 78d4098)를 실제로 확인했다.
