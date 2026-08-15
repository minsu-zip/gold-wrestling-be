---
phase: 05-batch
plan: 08
subsystem: batch
tags: [spring-scheduling, spring-security, openapi, kotlin, spring-boot-4]

# Dependency graph
requires:
  - phase: 05-batch (05-06, 05-07)
    provides: InactivityBatchRunner.run(trigger, triggeredByAdminId) — 실행 1회 오케스트레이션 + 실행 이력 저장
provides:
  - "매일 새벽 4시(Asia/Seoul) @Scheduled cron이 InactivityBatchRunner를 SCHEDULED 트리거로 자동 실행"
  - "POST /api/admin/batch/inactivity-runs — 관리자가 같은 러너를 MANUAL 트리거로 즉시 실행하고 BatchExecutionResponse를 받는 API"
  - "docs/api/openapi.yaml에 반영된 관리자 배치 수동 실행 API 계약"
affects: [05-09, 06-attendance-이후-운영-단계]

tech-stack:
  added: []
  patterns:
    - "@Scheduled 트리거는 로직 없이 러너 호출 1줄만 둔다(스케줄러=트리거, 로직은 별도 서비스)"
    - "LAZY 연관은 응답 DTO에서 id만 꺼내고 엔티티를 담지 않는다(BatchExecutionResponse, PassResponse와 동일 관례)"
    - "MockMvc 컨트롤러 테스트에서 clock을 과거로 고정하면 발급한 JWT가 실시각 검증(NimbusJwtDecoder)에서 만료로 401 처리된다 — 토큰 발급이 있는 테스트는 clock을 Instant.now()로 리셋한다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/config/SchedulingConfig.kt
    - src/main/kotlin/com/goldwrestling/batch/InactivityBatchScheduler.kt
    - src/main/kotlin/com/goldwrestling/batch/AdminBatchController.kt
    - src/main/kotlin/com/goldwrestling/batch/dto/BatchExecutionResponse.kt
    - src/test/kotlin/com/goldwrestling/batch/AdminBatchControllerTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt
    - docs/api/openapi.yaml

key-decisions:
  - "resolveTriggeredBy의 두 예외는 HTTP 경로에서 도달 불가로 판정 — 도메인 예외/ErrorCode를 새로 추가하지 않고 KDoc에 근거를 남겼다(D-114가 이미 예정한 결론)"
  - "AdminBatchControllerTest는 클래스 레벨 @Transactional을 쓰지 않는다 — 연속 2회 호출이 서로 다른 물리 트랜잭션으로 커밋돼야 D-106 멱등성을 HTTP 레벨에서 실증할 수 있다"

patterns-established:
  - "SchedulingConfig(config/ 설정 클래스)만 conventions §10.0 면제 대상이다. InactivityBatchScheduler는 batch/ 패키지의 @Component이고 §10.0 면제 목록의 'getter/setter 수준의 위임'보다 계약이 크다(트리거 종류·관리자 id·cron 값) — 이 판단이 틀렸고 PR #15 2차 리뷰가 지적해 InactivityBatchSchedulerTest를 추가했다(D-116 커밋)"

requirements-completed: [BATCH-01, BATCH-04]

duration: 45min
completed: 2026-08-15
---

# Phase 05 Plan 08: 배치 실제 트리거(자동 cron + 관리자 수동 실행) Summary

**2주 미사용 차감 배치를 앱 내 @Scheduled(매일 04:00 Asia/Seoul) 자동 실행 + 관리자 수동 실행 API 1개로 붙이고 openapi.yaml에 반영**

## Performance

- **Duration:** 약 45분
- **Tasks:** 3/3 완료
- **Files modified:** 7 (신규 5, 수정 2)

## Accomplishments
- 이 저장소 최초로 스케줄링 인프라(`@EnableScheduling`)를 도입하고, `InactivityBatchRunner`를 매일 새벽 4시(Asia/Seoul) 자동 실행하는 cron 트리거를 붙였다
- 관리자가 `POST /api/admin/batch/inactivity-runs`로 같은 러너를 즉시 실행하고 집계 결과(`BatchExecutionResponse`)를 받을 수 있게 했다 — 자동·수동 두 경로가 하나의 러너를 공유해 로직 중복이 없다
- PR #14 리뷰에서 이월된 미해결 항목(관리자 해석 예외의 HTTP 도달 가능성)을 코드로 판정하고 KDoc에 근거를 남겨 닫았다
- `docs/api/openapi.yaml`을 재생성해 FE 계약에 새 엔드포인트·DTO·enum을 반영했다

## Task Commits

Each task was committed atomically:

1. **Task 1: 스케줄링 인프라 + cron 트리거** - `8aebcb7` (feat)
2. **Task 2: 관리자 수동 실행 API + 응답 DTO** - `9969950` (feat)
3. **Task 3: openapi.yaml 재생성** - `418d1a7` (docs)

_TDD 표시(`tdd="true"`)가 있었으나 대상이 신규 API 배선(컨트롤러+DTO+테스트)이라 RED→GREEN 단계로 나누지 않고 구현+테스트를 한 커밋에 담았다 — 배치 도메인 로직(차감·기준일 계산) 자체는 이미 05-03·05-05가 RED/GREEN으로 다뤘고, 이 플랜은 그 로직에 새 진입점(HTTP)을 배선하는 작업이다._

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/config/SchedulingConfig.kt` - `@EnableScheduling`만 켜는 단일 책임 설정 클래스
- `src/main/kotlin/com/goldwrestling/batch/InactivityBatchScheduler.kt` - 매일 04:00 Asia/Seoul cron이 러너를 SCHEDULED로 호출
- `src/main/kotlin/com/goldwrestling/batch/AdminBatchController.kt` - 관리자 수동 실행 엔드포인트(1개, 요청 본문 없음)
- `src/main/kotlin/com/goldwrestling/batch/dto/BatchExecutionResponse.kt` - 실행 결과 응답 DTO (LAZY 연관은 id만)
- `src/main/kotlin/com/goldwrestling/batch/InactivityBatchRunner.kt` - `resolveTriggeredBy` KDoc에 두 예외의 HTTP 도달 불가 근거 추가
- `src/test/kotlin/com/goldwrestling/batch/AdminBatchControllerTest.kt` - 컨트롤러 통합테스트 6종
- `docs/api/openapi.yaml` - `/api/admin/batch/inactivity-runs` + `BatchExecutionResponse` 스키마 반영

## Decisions Made

- **resolveTriggeredBy 두 예외는 HTTP 경로에서 도달 불가(PR #14 리뷰 Info 이월 확인)**:
  - `requireNotNull(triggeredByAdminId)` — 호출부가 넘기는 값은 `AuthenticatedPrincipal.requireAdminId(): Long`의 반환값인데, 이 시그니처가 non-null `Long`이라 컴파일 타임에 null이 될 수 없다.
  - 관리자 조회 실패 `IllegalStateException` — `JwtAuthenticationFilter.authenticate`가 매 요청마다 `AuthenticationPrincipalResolver.resolve`로 관리자 존재를 먼저 확인하고, 없으면 인증 자체를 401로 실패시켜 컨트롤러에 도달하지 못한다. 게다가 `AdminRepository`에는 관리자 삭제 기능이 아예 구현돼 있지 않다(저장소 전체에 admin 삭제 호출부 없음).
  - 결론: 두 경로 모두 새 도메인 예외·`ErrorCode`·`docs/error-codes.md` 항목을 추가하지 않는다 — D-114가 "새 에러코드는 추가하지 않는다"로 이미 예정한 결론과 일치한다. 근거는 `InactivityBatchRunner.resolveTriggeredBy` KDoc에 남겼다.
- **AdminBatchControllerTest는 클래스 레벨 `@Transactional`을 쓰지 않는다** — "연속 2회 호출 시 두 번째 `deductedCount`가 0"을 검증하려면 첫 호출의 차감이 실제로 커밋돼야 한다. 대신 `@AfterEach`에서 이 클래스가 만든 데이터만 정리한다(`InactivityBatchFailureIsolationTest` 관례 재사용).
- **컨트롤러 테스트의 clock 리셋은 `Instant.now()`(실제 현재 시각)를 쓴다** — `BatchFixtures.FIXED_TIME`(과거 고정 시각)으로 리셋하면 `TokenService.issueTokenPair`가 그 시각 기준으로 계산한 토큰 `exp`가, 검증 시점의 실제 시스템 시각(`NimbusJwtDecoder`는 주입된 `Clock` 빈이 아니라 시스템 시각으로 만료를 판정)보다 이미 지나 있어 모든 인증이 401로 실패한다. "14일 전" 같은 상대 날짜는 `LocalDate.now(clock)`으로 테스트 실행 시점 기준 상대값을 계산해 얻는다.

## Deviations from Plan

None — plan에 명시된 작업만 수행했다. Task 2의 "[필수 확인]" 블록은 새 코드 추가가 아니라 plan이 지시한 판정·문서화 작업이라 별도 Rule로 분류하지 않는다.

## Issues Encountered

- **AdminBatchControllerTest 최초 작성본이 인증 401로 전부 실패했다.** 원인은 clock을 `BatchFixtures.FIXED_TIME`(2026-08-02, 실행 시점 기준 과거)으로 고정했기 때문 — `TokenService`는 주입된 `Clock` 빈으로 토큰 `exp`를 계산하지만 `NimbusJwtDecoder`는 검증 시 실제 시스템 시각을 쓴다. `AdminScheduleControllerTest` 관례(clock을 `Instant.now()`로 리셋)를 따라 수정했다.
- **`@AfterEach` 정리가 FK 위반으로 실패했다.** `TokenService.issueTokenPair`가 만드는 `refresh_token` 행이 member/admin을 FK로 참조하는데, 정리 순서에 `refresh_token` 삭제가 빠져 있었다 — member/admin 삭제 전에 `refresh_token`을 먼저 지우도록 정리 순서를 보강했다.
- 둘 다 이 플랜의 새 테스트 파일 안에서 발견·수정했다(Rule 1 — 버그 수정, 별도 커밋 분리 없이 Task 2 커밋에 포함).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 배치가 자동(매일 새벽)·수동(관리자 API) 두 경로로 실제로 동작한다. 05-09(청크 B 마지막 플랜)로 진행 가능
- `AdminScheduleControllerTest`·`AdminPassControllerTest` 등 기존 관리자 컨트롤러 테스트가 `@EnableScheduling` 도입 이후에도 그린이다(전체 `./gradlew build` 통과로 확인) — 회귀 없음

---
*Phase: 05-batch*
*Completed: 2026-08-15*

## Self-Check: PASSED

모든 생성/수정 파일과 커밋 해시(8aebcb7, 9969950, 418d1a7)를 확인함 — 누락 없음.
