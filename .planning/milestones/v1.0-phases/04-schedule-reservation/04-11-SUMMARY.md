---
phase: 04-schedule-reservation
plan: 11
subsystem: api
tags: [kotlin, spring-boot, jpa, postgresql, schedule, admin, n+1, tdd]

# Dependency graph
requires:
  - phase: 04-schedule-reservation (plan 04-05)
    provides: "ScheduleService.getWeeklySchedule 그리드 조립 로직(회원용), ScheduleCellResponse/WeeklyScheduleResponse"
  - phase: 04-schedule-reservation (plan 04-10)
    provides: "청크 B(예약 생성·취소·변경·본인 목록) 전체 — 이 플랜의 예약 픽스처가 재사용하는 도메인"
provides:
  - "AdminScheduleService.getWeeklyBoard — 관리자 주간 스케줄 보드(요일×타임 그리드 + 셀별 예약자 명단), 조회 주 범위 제한 없음"
  - "GET /api/admin/schedule/board 엔드포인트"
  - "ScheduleGridSkeleton(schedule 패키지 내부) — 회원용/관리자용 화면이 공유하는 그리드 조립 헬퍼"
  - "ReservationRepository.findAllByClassSessionIdInAndStatusWithMember — member fetch join 관례(이 저장소 최초)"
  - "AdminBranchRepository/AdminBranchNotAssignedException — 관리자 branchId 인가 검증(T-04-53)의 첫 실사용"
affects: [04-13, 04-14]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "그리드 조립 공유: ScheduleService(회원용)와 AdminScheduleService(관리자용)가 '시간표→그리드
       뼈대→세션 덮어쓰기' 로직을 schedule 패키지 내부 object ScheduleGridSkeleton으로 공유 —
       복제 시 시간표 정렬·EVENING 취급이 두 화면에서 어긋나는 것을 구조적으로 방지"
    - "명단 fetch join: 셀별 예약자 명단처럼 LAZY 연관(member)에 접근이 확정된 배치 조회는
       join fetch로 N+1을 원천 차단 — 이 저장소에 join fetch 사용 전례가 없어 이 플랜이 최초 도입"
    - "관리자 지점 스코프 해석: branchId 명시 시 admin_branch 매핑 검증(없으면 403), 생략 시
       관리자의 첫 지점 또는(매핑이 비어 있으면) 시스템 지점이 하나뿐일 때만 그 지점으로 대체 —
       AdminSeeder가 v1에서 admin_branch를 만들지 않기로 한 기존 결정과의 절충(D-101)"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/schedule/AdminScheduleService.kt
    - src/main/kotlin/com/goldwrestling/schedule/AdminScheduleController.kt
    - src/main/kotlin/com/goldwrestling/schedule/ScheduleGridSkeleton.kt
    - src/main/kotlin/com/goldwrestling/schedule/dto/AdminWeeklyBoardResponse.kt
    - src/main/kotlin/com/goldwrestling/schedule/dto/AdminBoardDayResponse.kt
    - src/main/kotlin/com/goldwrestling/schedule/dto/AdminBoardCellResponse.kt
    - src/main/kotlin/com/goldwrestling/schedule/dto/BoardReservationResponse.kt
    - src/main/kotlin/com/goldwrestling/admin/AdminBranchRepository.kt
    - src/main/kotlin/com/goldwrestling/admin/AdminBranchNotAssignedException.kt
    - src/test/kotlin/com/goldwrestling/schedule/AdminScheduleServiceTest.kt
    - src/test/kotlin/com/goldwrestling/schedule/AdminScheduleControllerTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/schedule/ScheduleService.kt
    - src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt
    - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
    - docs/error-codes.md
    - docs/decisions.md

key-decisions:
  - "D-101: branchId 해석은 admin_branch 매핑 우선, 없으면(v1 미도입) 단일 지점으로 대체"
  - "ScheduleService.getWeeklySchedule을 순수 리팩터링 — toCell이 세션을 직접 조회하지 않고
     ScheduleGridSkeleton이 미리 묶어 준 (schedule, session) 쌍을 받도록 변경, 기존 동작 불변"

requirements-completed: [SCHED-03]

# Metrics
duration: ~35min
completed: 2026-08-08
---

# Phase 4 Plan 11: 관리자 주간 스케줄 보드 Summary

**관리자가 요일×타임 그리드에 수업 종류·예약 n/정원·1:1 여부·셀별 예약자 명단(reservationId/memberId/memberName 3필드 고정)을 조회하는 `GET /api/admin/schedule/board`를 추가하고, 회원용 시간표와 그리드 조립 로직을 `ScheduleGridSkeleton`으로 공유했다.**

## Performance

- **Duration:** ~35min
- **Tasks:** 2 completed (Task 1·2 모두 TDD RED→GREEN)
- **Files modified:** 16 (신규 11, 수정 5)

## Accomplishments

- 관리자가 `GET /api/admin/schedule/board`로 요일×타임 그리드를 조회하면 예약이 없는 타임은 `classSessionId=null`·`reservedCount=0`·`reservations=[]`로, 예약이 있는 타임은 셀별 예약자 명단(회원 id·이름)과 함께 내려온다
- 취소된 예약은 명단과 `reservedCount` 어디에도 반영되지 않는다(실제 PostgreSQL 통합테스트로 확인)
- 휴강 처리된 타임은 `suspended=true`와 `cancelReason`이 함께 내려온다
- 관리자는 회원과 달리 조회 주 범위 제한이 없다 — 지난 주·다음 다음 주 모두 정상 조회되고, `weekStart`가 월요일이 아니면(예: 수요일) 거부하지 않고 그 주 월요일로 정규화한다
- 회원 토큰으로는 403(`ACCESS_DENIED`), 인증 없이는 401(`UNAUTHENTICATED`)로 거부된다
- 명단(`BoardReservationResponse`)은 `reservationId`/`memberId`/`memberName` 3필드로 고정되어 전화번호·회원 상태·이용권 정보가 노출되지 않는다(T-04-52)
- 회원용 `ScheduleService`와 관리자용 `AdminScheduleService`가 "시간표 → 그리드 뼈대 → 세션 덮어쓰기" 조립 로직을 `ScheduleGridSkeleton`(schedule 패키지 내부 object)으로 공유해, 시간표 정렬·`EVENING` 취급이 두 화면에서 복제되지 않는다
- 조회 쿼리는 셀 수에 비례하지 않는다 — 시간표 전량 조회 1회, 세션 배치 조회 1회, 예약 명단 배치 조회 1회(`member`를 `join fetch`해 N+1 제거)로 고정

## Task Commits

Each task was committed atomically (TDD RED → GREEN):

1. **Task 1: AdminScheduleService — 주간 보드 조립(셀별 명단 포함) + 서비스 통합테스트**
   - `ba479c2` test(04-11): 관리자 주간 스케줄 보드 서비스 실패 테스트 추가 (RED)
   - `061b1fa` feat(04-11): AdminScheduleService — 주간 스케줄 보드 조립 + 셀별 예약자 명단 (GREEN)
2. **Task 2: GET /api/admin/schedule/board 엔드포인트 + 통합테스트**
   - `141de20` test(04-11): GET /api/admin/schedule/board 엔드포인트 실패 테스트 추가 (RED)
   - `2e724ca` feat(04-11): GET /api/admin/schedule/board 엔드포인트 추가 (GREEN)

**Plan metadata:** (다음 커밋, docs: complete plan)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/schedule/AdminScheduleService.kt` — `getWeeklyBoard`(그리드+명단 조립), `resolveBranchId`(T-04-53 인가 검증)
- `src/main/kotlin/com/goldwrestling/schedule/AdminScheduleController.kt` — `GET /api/admin/schedule/board`(weekStart·branchId 선택 파라미터)
- `src/main/kotlin/com/goldwrestling/schedule/ScheduleGridSkeleton.kt` — 회원용/관리자용 공유 그리드 조립 헬퍼(신규)
- `src/main/kotlin/com/goldwrestling/schedule/ScheduleService.kt` — `ScheduleGridSkeleton` 사용하도록 리팩터링(행위 변경 없음, 기존 `MemberScheduleControllerTest` 10개 전부 통과 확인)
- `src/main/kotlin/com/goldwrestling/schedule/dto/AdminWeeklyBoardResponse.kt`, `AdminBoardDayResponse.kt`, `AdminBoardCellResponse.kt`, `BoardReservationResponse.kt` — 응답 DTO 4종(신규)
- `src/main/kotlin/com/goldwrestling/reservation/ReservationRepository.kt` — `findAllByClassSessionIdInAndStatusWithMember`(member fetch join) 추가
- `src/main/kotlin/com/goldwrestling/admin/AdminBranchRepository.kt`, `AdminBranchNotAssignedException.kt` — branchId 인가 검증 인프라(신규)
- `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt` — `ADMIN_BRANCH_NOT_ASSIGNED`(403) 추가
- `docs/error-codes.md`, `docs/decisions.md` — 신규 에러코드·D-101 기록
- `src/test/kotlin/com/goldwrestling/schedule/AdminScheduleServiceTest.kt` — 그리드 조립 규칙 9종(신규)
- `src/test/kotlin/com/goldwrestling/schedule/AdminScheduleControllerTest.kt` — HTTP 계약 10종(신규)

## Decisions Made

- **D-101 (branchId 해석)** — 04-11-PLAN은 "admin_branch 조회 경로가 있으면 쓰고, 없으면 branchId 파라미터 + 소속 검증"을 지시했다. 실제로 읽어보니 `AdminBranch` 테이블·엔티티는 Phase 1(V2)부터 존재하지만 **어떤 관리자에게도 행이 채워진 적이 없다** — `AdminSeeder.kt`의 기존 KDoc이 "v1은 지점이 하나뿐이라 이 매핑 없이도 운영에 지장이 없다"고 명시적으로 이 보류를 남겨 뒀다. 두 경로를 절충했다: `branchId`를 명시하면 항상 `admin_branch` 매핑을 요구해 거부(다지점 확장 시 T-04-53을 실제로 막는다), 생략하면 관리자의 첫 지점을 쓰되 매핑이 비어 있고 시스템에 지점이 하나뿐이면 그 지점으로 대체한다(기존 시드 관리자가 이 기능을 즉시 쓸 수 있게). `AdminSeeder`는 이번 플랜에서 수정하지 않았다 — v1 단일 지점 가정이 여전히 유효하고, 매핑을 강제로 채우면 "관리자가 실제로 그 지점에 배정됐다"는 의미가 왜곡된다.
- **ScheduleService 리팩터링 범위** — 04-11-PLAN이 "회원용과 겹치는 로직을 공통 헬퍼로 추출한다"를 명시적으로 지시했으므로, `ScheduleService.kt`(04-05 산출물, `files_modified`에 없던 파일)를 수정했다. `toCell`이 `sessionsByKey`를 직접 조회하던 것을 `ScheduleGridSkeleton`이 미리 짝지어 준 `(schedule, session)` 쌍을 받도록 바꿨을 뿐 응답 값·순서·판정 로직은 그대로다 — 기존 `MemberScheduleControllerTest`(10개)가 수정 없이 전부 통과해 행위 불변을 확인했다.
- **회원 이름 non-null 단언** — `BoardReservationResponse.memberName`은 플랜이 `String`(non-null)으로 명시했다. 예약이 존재하려면 회원이 `ACTIVE`(온보딩 완료 전제, `MemberStateGate`)여야 하므로 `member.name`이 없을 수 없지만, 엔티티 타입은 `String?`이다 — `requireNotNull`로 이 불변식을 코드에 명시하고, 위반 시 "데이터 정합성 오류"로 즉시 드러나게 했다(조용히 빈 문자열로 흡수하지 않음).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - 누락된 필수 기능] `AdminBranchRepository`/`AdminBranchNotAssignedException`/`ErrorCode.ADMIN_BRANCH_NOT_ASSIGNED` 신규 추가**
- **Found during:** Task 2 (컨트롤러 branchId 처리)
- **Issue:** 플랜의 위협 모델(T-04-53 "소속되지 않은 지점의 보드 조회")을 완화하려면 `admin_branch` 소속 검증이 필요한데, 이 저장소에 관리자-지점 소속을 검증하는 코드 경로가 전혀 없었다(`AdminBranchRepository` 자체가 없었다)
- **Fix:** `AdminBranchRepository`(조회 2종) + `AdminBranchNotAssignedException`(403) + `ErrorCode.ADMIN_BRANCH_NOT_ASSIGNED`를 추가하고 `AdminScheduleService.resolveBranchId`에서 사용
- **Files modified:** `admin/AdminBranchRepository.kt`, `admin/AdminBranchNotAssignedException.kt`, `common/error/ErrorCode.kt`, `docs/error-codes.md`
- **Committed in:** `061b1fa`

**2. [Rule 1 - 버그] `docs/error-codes.md`에 신규 에러코드 미등재로 `ErrorCodeRegistryTest` 실패**
- **Found during:** Task 1 GREEN 커밋 직전 `./gradlew build` 1차 실행
- **Issue:** `ADMIN_BRANCH_NOT_ASSIGNED`를 `ErrorCode` enum에 추가했지만 `docs/error-codes.md` 표에 등재하지 않아 `ErrorCodeRegistryTest`(코드-문서 1:1 일치 검사)가 실패
- **Fix:** `docs/error-codes.md` "시간표·예약 코드" 표에 행 추가
- **Files modified:** `docs/error-codes.md`
- **Verification:** `./gradlew test --tests "com.goldwrestling.common.error.ErrorCodeRegistryTest"` 통과, `./gradlew build` 전체 통과(519개 테스트)
- **Committed in:** `061b1fa`

---

**Total deviations:** 2 auto-fixed (1 Rule 2 - 누락된 필수 기능, 1 Rule 1 - 버그)
**Impact on plan:** 둘 다 플랜의 위협 모델·프로젝트 규약(에러코드 레지스트리 1:1 일치)을 충족하기 위한 필수 보강이었다. 스코프 확장 없음.

## Issues Encountered

None — 계획대로 진행됐다. `AdminBranch` 매핑이 실제로는 비어 있다는 사실은 "Decisions Made"에 기록한 대로 설계 시점에 흡수했다.

## Known Stubs

없음.

## Threat Flags

없음 — 이 플랜이 여는 새 표면(`GET /api/admin/schedule/board`)은 플랜의 `<threat_model>`(T-04-51~54)이 이미 다루고 있고, 전부 `mitigate`로 테스트가 실증했다:
- T-04-51(회원 토큰으로 명단 조회) — `hasRole("ADMIN")`(기존 SecurityConfig) + 403 테스트
- T-04-52(명단에 전화번호·이용권 정보 노출) — `BoardReservationResponse` 3필드 고정(코드 리뷰로 확인 — DTO 파일에 다른 필드 없음)
- T-04-53(소속되지 않은 지점 조회) — `AdminBranchRepository.existsByAdminIdAndBranchId` 검증 + 403 테스트(신규 인프라, 위 Deviations 참고)
- T-04-54(DoS — 셀별 개별 쿼리) — 조회 단위 1주 고정 + 배치 조회 3회(시간표·세션·명단, `member` fetch join으로 N+1 제거)

## Test Coverage Note

**§10.0 표 적용 판단:** 이 플랜은 신규 서비스 메서드(`getWeeklyBoard`, `resolveBranchId`)와 신규 컨트롤러 엔드포인트이므로 통합테스트가 필수다(면제 대상 아님). `AdminScheduleServiceTest`(9개, 그리드 조립 규칙)와 `AdminScheduleControllerTest`(10개, HTTP 계약)로 관심사를 분리해 각각 작성했다 — 커스텀 쿼리(`findAllByClassSessionIdInAndStatusWithMember`)는 별도 리포지토리 테스트 대신 두 통합테스트가 실제 PostgreSQL로 이미 실행하므로 추가 테스트를 만들지 않았다.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 관리자 대시보드의 진입점(SCHED-03)이 완성되어 04-13(대리 취소/변경)·04-14(휴강 처리)가 이 보드에서 조작 대상 예약/세션 id를 얻을 수 있다
- `ScheduleGridSkeleton`은 이후 관리자 화면이 그리드를 다시 만들 때 재사용 가능하다
- `AdminBranchRepository`/`resolveBranchId`는 다지점 확장(v2, CROSS-01) 시 그대로 확장 가능한 지점이지만, 실제 다지점 운영을 시작하려면 관리자별 `admin_branch` 행을 배정하는 운영 절차(또는 관리 API)가 먼저 필요하다 — 이 플랜은 그 배정 도구를 만들지 않았다
- 04-11은 청크 C(관리자 운영) 첫 플랜이다 — `feature/phase-04c-admin-ops` 브랜치에서 이어서 04-12를 실행한다(같은 wave 11, 파일 겹침 없음)

---
*Phase: 04-schedule-reservation*
*Completed: 2026-08-08*

## Self-Check: PASSED

All created files (service/controller/DTOs/repository/exception/tests) and all 4 task commit hashes
(`ba479c2`, `061b1fa`, `141de20`, `2e724ca`) verified present via filesystem checks and
`git log --oneline --all` respectively.
