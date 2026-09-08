---
phase: 04-schedule-reservation
plan: 06
subsystem: api
tags: [kotlin, spring-boot, jpa, jpql, bigdecimal, tdd]

# Dependency graph
requires:
  - phase: 04-schedule-reservation
    plan: "04-03"
    provides: "reservation/Reservation·ReservationRepository·ReservationStatus 엔티티 골격, pass/PassRepository의 adjustRemainingCount·cancelIfNotCanceled 조건부 UPDATE 관례"
  - phase: 03-pass
    plan: "03-04"
    provides: "Pass.isExpired(D-066 종료일 포함 판정), PassType/PassStatus"
provides:
  - "reservation/ReservationPassPolicy — requiredPassType(수업 종류→이용권 종류 매핑), DEDUCTION_AMOUNT(1.0 상수), selectCandidate(후보 선택 판정)"
  - "pass/PassRepository.findDeductionCandidates — 만료 임박순·단일 이용권 기준 차감 후보 조회 쿼리"
affects: ["04-07(예약 생성 서비스가 이 두 함수를 그대로 호출해 어느 이용권에서 뺄지 다시 고민하지 않는다)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "판정(object)과 조회(@Query)의 역할 분리 — ReservationPassPolicy는 List<Pass>를 해석만 하고 정렬·필터는 SQL이 전담한다. selectCandidate가 candidates.first()만 취하고 다시 정렬하지 않는 것을 KDoc으로 못박아, 나중에 이 순서를 신뢰하지 못한 호출부가 중복 정렬을 추가하는 것을 막는다"
    - "@Query 시그니처 선언 → 파생 쿼리 파싱 실패로 RED 확인 → @Query 채우기 — 이 저장소에서 처음 쓴 RED 확보 방식. 순수 로직처럼 어서션 실패로 RED를 만들 수 없는 리포지토리 메서드는, 시그니처만 있고 @Query가 없을 때 Spring Data가 메서드명을 파생 쿼리로 잘못 해석해 PropertyReferenceException으로 컨텍스트 기동이 실패하는 것으로 RED를 증명한다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/reservation/ReservationPassPolicy.kt
    - src/test/kotlin/com/goldwrestling/reservation/ReservationPassPolicyTest.kt
    - src/test/kotlin/com/goldwrestling/pass/PassDeductionCandidateTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/pass/PassRepository.kt

key-decisions:
  - "ReservationPassPolicy는 reservation 패키지에 둔다 — schedule이 pass를 참조하지 않도록, 의존 방향을 reservation→schedule·reservation→pass 두 갈래로만 유지한다(D-018, PLAN.md 지시 그대로)"
  - "findDeductionCandidates 반환은 List<Pass> 그대로 — selectCandidate가 별도 도메인 예외로 감싸는 지점이라 리포지토리는 순수 조회만 책임진다(관례: PassRepositoryTest의 다른 @Query 메서드들과 동일)"

requirements-completed: [RESV-01, RESV-02, RESV-03]

# Metrics
duration: ~20min
completed: 2026-08-07
---

# Phase 04 Plan 06: 차감 대상 이용권 선택 정책(D-091) Summary

**ReservationPassPolicy(수업 종류→이용권 매핑, 후보 선택)와 PassRepository.findDeductionCandidates(만료 임박순·단일 이용권 기준 조회)를 두 번의 RED→GREEN 사이클로 구현해, "예약 1건 ↔ 이용권 1장" 규칙을 합산 금지·수업날 기준 만료 판정·타 회원 격리 3개 방어선으로 코드에 고정했다.**

## Performance

- **Duration:** 약 20분
- **Started:** 2026-08-07T20:03Z경 (컨텍스트 로딩 완료 후 RED 작성 시작)
- **Completed:** 2026-08-07T20:07+09:00 (마지막 검증 커밋)
- **Tasks:** 2/2 (사이클 1: ReservationPassPolicy, 사이클 2: findDeductionCandidates)
- **Files modified:** 4개 (신규 3 + 기존 1 수정)

## Accomplishments

- `ReservationPassPolicy.requiredPassType` — `SESSION`→`SESSION_PASS`, `LESSON`→`LESSON_PASS` 고정 매핑, `EVENING`은 `ClassSessionNotReservableException`으로 거부한다(교차 사용 없음, policies §3)
- `ReservationPassPolicy.selectCandidate` — 빈 리스트는 `InsufficientPassCountException`, 아니면 `candidates.first()`. 정렬은 쿼리가 이미 했다는 것을 KDoc으로 명시해 서비스 계층이 다시 정렬하지 않게 강제한다
- `PassRepository.findDeductionCandidates` — `status=ACTIVE`, `endDate >= classDate`(`Pass.isExpired`와 같은 비교축, D-066), `remainingCount >= requiredAmount`(단일 이용권 기준), `order by endDate asc, id asc`. KDoc에 "이 쿼리의 classDate는 예약일이 아니라 수업 날짜"를 굵게 명시했다
- `PassDeductionCandidateTest` 10개로 T-04-23(수업날 기준 만료 우회 차단)·T-04-24(0.5회 두 장 합산 금지)·T-04-25(타 회원 이용권 격리)·T-04-26(등록 취소 이용권 제외)을 실제 PostgreSQL(Testcontainers)로 증명했다 — 계획서가 요구한 9개 경계 케이스 전부 + 타 회원 격리 테스트 1개
- `ReservationPassPolicyTest` 5개로 매핑 3종 + `selectCandidate` 2종(빈 후보/정렬 신뢰)을 순수 Kotlin 단위테스트로 검증했다

## Task Commits

Each cycle was committed as RED → GREEN:

1. **사이클 1 RED: 매핑·후보 선택 실패 테스트** - `355d8a9` (test)
2. **사이클 1 GREEN: ReservationPassPolicy 구현** - `7340441` (feat)
3. **사이클 2 RED: findDeductionCandidates 실패 테스트(시그니처만 선언)** - `fc508a6` (test)
4. **사이클 2 GREEN: findDeductionCandidates @Query 구현** - `f5791d9` (feat)

REFACTOR 단계는 두 사이클 모두 건너뛰었다 — `ReservationPassPolicyTest`(순수 단위테스트, in-memory `Pass` 픽스처)와 `PassDeductionCandidateTest`(Testcontainers 통합테스트, 리포지토리로 영속화하는 픽스처)는 성격이 달라 공유할 만한 중복 헬퍼가 없었다.

**Plan metadata:** 본 커밋(SUMMARY + STATE + ROADMAP)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/reservation/ReservationPassPolicy.kt` — 수업 종류↔이용권 종류 매핑·차감 단위 상수·후보 선택 판정(순수 object)
- `src/main/kotlin/com/goldwrestling/pass/PassRepository.kt` — `findDeductionCandidates` 추가(기존 메서드 4개는 변경 없음)
- `src/test/kotlin/com/goldwrestling/reservation/ReservationPassPolicyTest.kt` — 단위테스트 5개
- `src/test/kotlin/com/goldwrestling/pass/PassDeductionCandidateTest.kt` — 통합테스트 10개

## Decisions Made

- `ReservationPassPolicy`를 `reservation` 패키지에 배치 — PLAN.md가 명시한 의존 방향(schedule이 pass를 모르게) 그대로 따랐다. 별도 재량 판단 아님
- 리포지토리 메서드의 RED를 어서션 실패가 아니라 "시그니처만 선언 → Spring Data 파생 쿼리 파싱 실패로 컨텍스트 기동 자체가 실패"로 확보했다 — PLAN.md의 명시적 지시(`<implementation>` "시그니처만 먼저 선언해 컴파일을 통과시키고 테스트가 실패하는 것을 확인한다")를 그대로 따른 것이며, 이 저장소에서 리포지토리 `@Query` 메서드에 이 RED 확보 방식을 쓴 첫 사례다(단순 구현 세부, Rule 4 대상 아님)

## Deviations from Plan

None - plan executed exactly as written. 문서 정합(decisions.md D-091, policies.md §3)은 이미 04-01에서 완료되어 있어 이 플랜에서 추가 작업이 필요하지 않았다.

## Issues Encountered

None — Boot 4 API·신규 의존성 확인이 필요한 작업이 없었다(`@Query`+JPQL은 Phase 3에서 이미 검증된 패턴을 그대로 이식).

## Testing Note (CLAUDE.md 규칙 10)

이 플랜의 프로덕션 코드(`ReservationPassPolicy`, `PassRepository.findDeductionCandidates`)는
conventions §10.0의 "엔티티 메서드/도메인 규칙"·"리포지토리 커스텀 쿼리" 행에 정확히 해당해
단위테스트·통합테스트 둘 다 필수 대상이었다. `type: tdd` 플랜 지시대로 두 사이클 모두 RED(실패
확인) → GREEN(구현) 순서를 지켰다 — RED 커밋(`355d8a9`, `fc508a6`)이 각각 GREEN 커밋(`7340441`,
`f5791d9`)보다 먼저 있다.

## User Setup Required

None - no external service configuration required. 로컬 검증은 `docker compose up -d` 후
`./gradlew ktlintFormat` → `./gradlew build`(Phase 3 이용권 테스트 전체 포함 전 스위트 그린 확인)로
전부 확인했다.

## Next Phase Readiness

- `ReservationPassPolicy.requiredPassType`/`selectCandidate`와 `PassRepository.findDeductionCandidates`가
  04-07(예약 생성 서비스)이 그대로 호출할 두 진입점이다 — 04-07은 "어느 이용권에서 뺄지"를 다시
  판정하지 않고 이 두 함수만 조합하면 된다(성공 기준 그대로 충족)
- 블로커 없음

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-07*

## Self-Check: PASSED

모든 생성 파일(4개)과 커밋 해시(4개: 355d8a9, 7340441, fc508a6, f5791d9)를 실제로 확인했다.
