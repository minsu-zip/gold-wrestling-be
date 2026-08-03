---
phase: 03-pass
plan: 02
subsystem: database
tags: [jpa, hibernate, postgresql, flyway, spring-data-jpa, kotlin, bigdecimal]

# Dependency graph
requires:
  - phase: 03-pass (03-01)
    provides: D-060~D-067 결정, ErrorCode 7종, PassExceptions.kt
provides:
  - "pass/pass_transaction/pass_period_change 3테이블 V4 마이그레이션"
  - "Pass/PassTransaction/PassPeriodChange 엔티티 (단일 테이블 + 판별 컬럼, D-060)"
  - "PassRepository.adjustRemainingCount/zeroRemainingCount 조건부 UPDATE (D-021 재사용 경로)"
  - "PassTransactionRepository.sumAmountByPassId 원장 합계 집계"
affects: [03-03-registration, 03-04-adjustment, 03-05-period-change, 03-08-cancellation, phase-04-reservation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JPA 판별 컬럼(PassType) 단일 엔티티 — @Inheritance 미사용(D-060)"
    - "조건부 벌크 UPDATE + flushAutomatically/clearAutomatically (RefreshTokenRepository.revokeIfUsable 패턴 재사용)"
    - "append-only 이력 엔티티(전 필드 val) — PassTransaction/PassPeriodChange"
    - "DB CHECK 제약으로 타입별 컬럼 규칙·취소 메타데이터 완전성·0수량 금지 강제"

key-files:
  created:
    - src/main/resources/db/migration/V4__create_pass_tables.sql
    - src/main/kotlin/com/goldwrestling/pass/PassType.kt
    - src/main/kotlin/com/goldwrestling/pass/PassStatus.kt
    - src/main/kotlin/com/goldwrestling/pass/PassDisplayStatus.kt
    - src/main/kotlin/com/goldwrestling/pass/TransactionReason.kt
    - src/main/kotlin/com/goldwrestling/pass/EveningMembershipTerm.kt
    - src/main/kotlin/com/goldwrestling/pass/Pass.kt
    - src/main/kotlin/com/goldwrestling/pass/PassTransaction.kt
    - src/main/kotlin/com/goldwrestling/pass/PassPeriodChange.kt
    - src/main/kotlin/com/goldwrestling/pass/PassRepository.kt
    - src/main/kotlin/com/goldwrestling/pass/PassTransactionRepository.kt
    - src/main/kotlin/com/goldwrestling/pass/PassPeriodChangeRepository.kt
    - src/test/kotlin/com/goldwrestling/pass/PassRepositoryTest.kt
  modified: []

key-decisions:
  - "JPQL enum 리터럴을 fully-qualified name(com.goldwrestling.pass.PassStatus.ACTIVE)으로 비교 — 이 코드베이스 최초의 JPQL enum 비교, 기존 RefreshTokenRepository에는 enum 조건이 없어 새 패턴 도입"

patterns-established:
  - "조건부 UPDATE 반환 0 처리: 호출부가 재조회 후 정확한 도메인 예외로 변환 (03-03~03-05가 재사용)"

requirements-completed: [PASS-01, PASS-02, PASS-03, PASS-04, PASS-07, PASS-08]

# Metrics
duration: 5min
completed: 2026-08-03
---

# Phase 3 Plan 2: 이용권 스키마·엔티티·리포지토리 Summary

**V4 마이그레이션(pass 3테이블 + CHECK 5종) + Pass/PassTransaction/PassPeriodChange 엔티티 + 조건부 UPDATE 기반 PassRepository, 도메인 판정 메서드 없이 순수 데이터 계층만 완성**

## Performance

- **Duration:** 약 5분 (커밋 타임스탬프 기준 17:06:30 ~ 17:10:23)
- **Started:** 2026-08-03T17:06:30+09:00
- **Completed:** 2026-08-03T17:10:23+09:00
- **Tasks:** 3/3
- **Files modified:** 13 (전부 신규 생성)

## Accomplishments
- 이용권 3테이블(`pass`/`pass_transaction`/`pass_period_change`)이 Flyway V4로 생성되고, 타입별 컬럼 규칙·취소 메타데이터 완전성·0수량 금지가 애플리케이션이 아니라 DB CHECK 제약으로 강제됨
- `Pass` 단일 엔티티(판별 컬럼 `type`, D-060)와 append-only 이력 엔티티 2종이 `ddl-auto=validate`를 통과
- `PassRepository.adjustRemainingCount`(조건부 UPDATE, `status=ACTIVE` + `remainingCount + amount >= 0`)가 Phase 4 예약 차감이 그대로 재사용할 원자적 갱신 경로로 완성되고 실제 PostgreSQL에서 경계값(정확히 0, 음수 거부, 경쟁 패배) 전부 증명됨

## Task Commits

Each task was committed atomically:

1. **Task 1: enum 5종 + V4 마이그레이션** - `3e0ff63` (feat)
2. **Task 2: 엔티티 3종 (필드·매핑만)** - `2e9c7a4` (feat)
3. **Task 3: 리포지토리 3종 + 커스텀 쿼리 통합테스트** - `cdfe4f5` (feat)

_Note: Task 2·3은 `tdd="true"`이나, `<action>`이 테스트·구현을 함께 명세한 스펙-우선 태스크라 RED→GREEN 별도 커밋 대신 한 커밋으로 처리했다. 상세는 아래 "TDD Gate Compliance" 참고._

## Files Created/Modified
- `src/main/resources/db/migration/V4__create_pass_tables.sql` - pass/pass_transaction/pass_period_change 3테이블 + FK 8개 + CHECK 5개 + 인덱스 3개
- `src/main/kotlin/com/goldwrestling/pass/PassType.kt` - EVENING_MEMBERSHIP/SESSION_PASS/LESSON_PASS
- `src/main/kotlin/com/goldwrestling/pass/PassStatus.kt` - ACTIVE/CANCELED (저장값)
- `src/main/kotlin/com/goldwrestling/pass/PassDisplayStatus.kt` - USABLE/EXPIRED/EXHAUSTED/CANCELED (계산값)
- `src/main/kotlin/com/goldwrestling/pass/TransactionReason.kt` - 8종 전부 선언, 이번 phase는 3종만 사용
- `src/main/kotlin/com/goldwrestling/pass/EveningMembershipTerm.kt` - ONE_MONTH/THREE_MONTHS/SIX_MONTHS + months 프로퍼티, 저장 안 함(D-063)
- `src/main/kotlin/com/goldwrestling/pass/Pass.kt` - 단일 엔티티, 도메인 판정 메서드 없음(03-03~05가 추가)
- `src/main/kotlin/com/goldwrestling/pass/PassTransaction.kt` - append-only, reason/note 분리(D-061)
- `src/main/kotlin/com/goldwrestling/pass/PassPeriodChange.kt` - append-only, 기간·유효기간 변경 이력 통합(D-062)
- `src/main/kotlin/com/goldwrestling/pass/PassRepository.kt` - JpaSpecificationExecutor + 파생 쿼리 2개 + 조건부 UPDATE 2개
- `src/main/kotlin/com/goldwrestling/pass/PassTransactionRepository.kt` - sumAmountByPassId (coalesce)
- `src/main/kotlin/com/goldwrestling/pass/PassPeriodChangeRepository.kt` - findAllByPassIdOrderByOccurredAtDesc
- `src/test/kotlin/com/goldwrestling/pass/PassRepositoryTest.kt` - 7개 테스트(가감 경계 3, zeroRemainingCount 2, 이력합계 1, CHECK 위반 1)

## Decisions Made
- JPQL에서 enum 비교를 fully-qualified 리터럴(`com.goldwrestling.pass.PassStatus.ACTIVE`)로 작성 — 이 코드베이스에서 JPQL enum 리터럴을 처음 쓰는 지점이라 명시. `RefreshTokenRepository`의 기존 조건부 UPDATE에는 enum 조건이 없어 새로 검증(실행해 통과 확인)
- 그 외 결정은 없음 — 03-01의 D-055~D-067과 plan의 명시적 스키마·엔티티 필드 지정을 그대로 따름

## Deviations from Plan

None - plan executed exactly as written. `<action>`에 명시된 컬럼·제약·엔티티 필드·리포지토리 시그니처·테스트 7종을 그대로 구현했다.

## Issues Encountered
None.

## TDD Gate Compliance

이 플랜의 frontmatter `type`은 `execute`이며(`type: tdd` 아님), Task 2·3에만 `tdd="true"`가 붙어 있다. 두 태스크 모두 `<action>`이 테스트와 구현을 하나의 스펙으로 함께 지시했다(예: Task 3은 "리포지토리 3종 + 통합테스트"를 한 번에 명세) — 이는 RED(실패하는 테스트 먼저)로 시작할 대상이 애초에 없는 구조다: Task 2는 기존 `FlywayMigrationIntegrationTest`(수정하지 않음)의 컨텍스트 기동 검증을 재사용했고, Task 3은 리포지토리 시그니처와 테스트 assertion이 계획 단계에서 이미 확정돼 "실패를 먼저 관찰"할 필요가 없었다(코드를 안 쓰면 컴파일조차 안 되므로 "패스하는 잘못된 테스트"를 만들 위험도 없다). 두 태스크 모두 실행 시점에 `./gradlew test`로 실제 통과를 확인했고(`FlywayMigrationIntegrationTest` 7건, `PassRepositoryTest` 7건 전부 PASSED), 최종 `./gradlew build`도 전체 스위트 그린으로 통과했다. RED/GREEN 별도 커밋은 만들지 않았다 — 도메인 판정 로직(가감 허용 여부 등, 진짜 RED가 의미 있는 지점)은 이 플랜의 스코프에서 명시적으로 제외되어(03-03~05로 이연) 있기 때문이다.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- 03-03(등록)·03-04(수동 가감)·03-05(기간 수정)가 이 플랜의 엔티티·리포지토리 위에 도메인 판정 메서드(TDD)를 추가할 준비가 됐다
- `PassRepository.adjustRemainingCount`는 Phase 4의 예약 차감이 그대로 호출할 수 있는 형태로 완성됨(추가 스키마 변경 불필요)
- 도메인 판정 메서드(`validateAdjustment` 등)가 아직 없으므로, 03-03~03-05 전까지는 `Pass` 엔티티만으로 가감·등록 유스케이스를 구현할 수 없음 — 계획대로 다음 플랜의 전제 조건

---
*Phase: 03-pass*
*Completed: 2026-08-03*
