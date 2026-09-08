---
phase: 05-batch
plan: 02
subsystem: database
tags: [flyway, jpa, kotlin, batch, postgresql, testcontainers]

# Dependency graph
requires:
  - phase: 05-batch (05-01)
    provides: "glossary.md 배치 용어(BatchExecution·BatchTrigger·BatchExecutionStatus·returnedFromLeaveAt), D-110~D-114 결정"
provides:
  - "V9 마이그레이션 — pass_transaction ck_pass_transaction_subject를 at-most-one으로 완화, member.returned_from_leave_at 컬럼, batch_execution 테이블"
  - "batch 패키지 골격 — BatchExecution 엔티티(append-only)·BatchTrigger·BatchExecutionStatus enum·BatchExecutionRepository"
  - "AdminMemberService.changeStatus의 ON_LEAVE→ACTIVE 복귀 시각 기록 (D-105 기준일 후보 ③ 실제 데이터 경로)"
affects: [05-03, 05-04, 05-05, 05-06, 05-07, 05-08, 05-09]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "PassTransaction 시스템 주체(admin·member 둘 다 null) — CHECK를 exactly-one에서 at-most-one으로 완화해 표현"
    - "상태 변경 서비스에서 previousStatus를 대입 직전에 지역 변수로 보관해 전이 판정(ON_LEAVE→ACTIVE)에 사용"

key-files:
  created:
    - src/main/resources/db/migration/V9__relax_pass_transaction_subject_and_add_batch_execution.sql
    - src/main/kotlin/com/goldwrestling/batch/BatchExecution.kt
    - src/main/kotlin/com/goldwrestling/batch/BatchTrigger.kt
    - src/main/kotlin/com/goldwrestling/batch/BatchExecutionStatus.kt
    - src/main/kotlin/com/goldwrestling/batch/BatchExecutionRepository.kt
    - src/test/kotlin/com/goldwrestling/batch/BatchExecutionRepositoryTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/pass/PassTransaction.kt
    - src/test/kotlin/com/goldwrestling/pass/PassRepositoryTest.kt
    - src/main/kotlin/com/goldwrestling/member/Member.kt
    - src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt
    - src/test/kotlin/com/goldwrestling/member/MemberStatusChangeTest.kt

key-decisions:
  - "PassRepositoryTest의 '둘 다 비어 있으면 실패' 테스트를 '시스템 주체로 성공'으로 교체 — V9이 그 행동을 바꿨으므로 옛 단언을 남겨두면 거짓 회귀 방지가 아니라 거짓 실패가 된다"
  - "AdminMemberService 생성자에 Clock을 추가 — 수동 인스턴스화 호출부가 없어 Spring DI만으로 전파, 별도 마이그레이션 불필요"

patterns-established:
  - "배치(시스템) 주체 원장 행: PassTransaction(admin = null, member = null, reason = INACTIVITY)로 표현하고 V9 CHECK가 이를 유일하게 허용되는 '둘 다 null' 경로로 고정"

requirements-completed: [BATCH-01, BATCH-04]

# Metrics
duration: ~35min
completed: 2026-08-15
---

# Phase 5 Plan 2: 배치 스키마 선결 과제 Summary

**V9 마이그레이션 하나로 pass_transaction 시스템 주체 CHECK 완화·member 휴회 복귀 시각 컬럼·batch_execution 테이블 3건을 동시에 추가하고, 그 스키마를 실제로 쓰는 첫 코드(BatchExecution 엔티티·AdminMemberService 복귀 시각 기록)까지 같은 플랜에서 GREEN으로 만듦**

## Performance

- **Duration:** ~35분 (컨텍스트 로딩 포함, Docker Desktop 기동 대기 시간 제외 시 코딩 자체는 ~15분)
- **Started:** 2026-08-15T12:07 (Docker 기동 대기 이후 실작업 시작)
- **Completed:** 2026-08-15T12:21
- **Tasks:** 3 완료 (Task 2·3은 TDD RED→GREEN 2커밋씩)
- **Files modified:** 11 (신규 6, 수정 5)

## Accomplishments
- `V9__relax_pass_transaction_subject_and_add_batch_execution.sql`이 세 가지 스키마 선결 과제를 한 번에 해결: `ck_pass_transaction_subject`를 "정확히 하나"에서 "최대 하나"로 완화(D-110), `member.returned_from_leave_at` 컬럼 추가(D-111), `batch_execution` 테이블 신설(D-113, MANUAL 트리거 시 관리자 필수 CHECK 포함)
- `batch` 패키지 골격(엔티티·enum 2종·리포지토리)이 append-only 관례(conventions §3)로 GREEN — `BatchExecutionRepositoryTest` 5케이스가 저장·조회·CHECK 위반 2종·정상 조합 2종을 실제 PostgreSQL(Testcontainers)로 증명
- `AdminMemberService.changeStatus`가 `previousStatus`를 상태 대입 전에 보관해 `ON_LEAVE→ACTIVE` 전이에서만 `returnedFromLeaveAt`을 `Clock` 기준으로 기록 — PENDING·INACTIVE→ACTIVE·approve는 건드리지 않고, 재복귀 시 더 최근 시각으로 덮어쓰는 것까지 6개 테스트로 고정

## Task Commits

Each task was committed atomically:

1. **Task 1: V9 마이그레이션 — CHECK 완화 + 복귀 시각 컬럼 + batch_execution 테이블** - `3bc85fe` (feat)
2. **Task 2: batch 패키지 엔티티·enum·리포지토리 + Member 복귀 시각 필드** - RED `9e3096f` (test) → GREEN `721d7e5` (feat)
3. **Task 3: ON_LEAVE→ACTIVE 복귀 시각 기록** - RED `208cc31` (test) → GREEN `1fdfef0` (feat)

_Note: Task 1은 마이그레이션 + 엔티티 KDoc 갱신 + 기존 테스트 교체를 한 커밋으로 처리했다 — add-migration SKILL §5("마이그레이션 + 엔티티 + 테스트를 같은 커밋에")를 따름. Task 2·3은 tdd="true"라 RED/GREEN을 분리했다._

## Files Created/Modified
- `src/main/resources/db/migration/V9__relax_pass_transaction_subject_and_add_batch_execution.sql` - CHECK 완화 + 컬럼 + 테이블 3건
- `src/main/kotlin/com/goldwrestling/pass/PassTransaction.kt` - KDoc을 "정확히 하나"→"둘 다 채우는 것만 금지"로 갱신
- `src/test/kotlin/com/goldwrestling/pass/PassRepositoryTest.kt` - 시스템 주체 저장 성공 케이스로 교체
- `src/main/kotlin/com/goldwrestling/batch/BatchExecution.kt` - append-only 배치 실행 이력 엔티티
- `src/main/kotlin/com/goldwrestling/batch/BatchTrigger.kt` - SCHEDULED/MANUAL enum
- `src/main/kotlin/com/goldwrestling/batch/BatchExecutionStatus.kt` - SUCCESS/PARTIAL_FAILURE enum
- `src/main/kotlin/com/goldwrestling/batch/BatchExecutionRepository.kt` - JpaRepository만 상속(조회 API 없음)
- `src/test/kotlin/com/goldwrestling/batch/BatchExecutionRepositoryTest.kt` - 저장·조회·CHECK 5케이스
- `src/main/kotlin/com/goldwrestling/member/Member.kt` - `returnedFromLeaveAt` 필드(생성자 마지막 파라미터)
- `src/main/kotlin/com/goldwrestling/member/AdminMemberService.kt` - `Clock` 주입, `changeStatus`의 복귀 시각 기록 분기
- `src/test/kotlin/com/goldwrestling/member/MemberStatusChangeTest.kt` - 복귀 시각 관련 6케이스 추가

## Decisions Made

- **`PassRepositoryTest`의 기존 "둘 다 비어 있으면 위반" 테스트를 삭제하지 않고 의미를 바꿔 교체**: V9이 그 행동 자체를 바꿨으므로(이제 성공해야 함) 테스트를 남겨두면 계속 실패하는 게 아니라 "틀린 것을 검증하는 통과 테스트"가 될 위험이 있었다 — 케이스 이름과 단언을 새 행동에 맞게 다시 썼다.
- **`AdminMemberService` 생성자에 `Clock` 추가는 별도 이관 작업 없이 안전**: 저장소 전체에서 이 서비스를 수동으로 `new`하는 곳이 없어(grep 확인) Spring DI가 자동으로 새 파라미터를 채운다. Boot 컨텍스트가 이미 `ClockConfig` 빈을 갖고 있어 프로덕션·테스트 양쪽 다 별도 배선이 필요 없었다.
- **재복귀 테스트에서 1차 캐시 문제를 발견해 즉시 고침(Rule 1)**: `@Transactional` 테스트 안에서 `memberRepository.findById`를 두 번 호출하면 Hibernate 영속성 컨텍스트가 같은 엔티티 인스턴스를 돌려준다 — 첫 번째 조회 결과를 엔티티 참조로 들고 있으면 두 번째 갱신이 그 참조도 함께 바꿔버려 "이전 값과 다른지" 비교가 항상 거짓 통과한다. 엔티티 참조 대신 조회 시점의 값만 별도 변수로 떼어 비교하도록 고쳤다.

## Deviations from Plan

None - plan executed exactly as written. Task 1의 acceptance criteria(파일명·grep 패턴·V1~V8 무변경) 전부 그대로 충족했고, Task 2·3의 `<behavior>` 목록도 추가·수정 없이 테스트로 그대로 옮겼다.

## Issues Encountered

- Docker Desktop이 세션 시작 시 꺼져 있어 Testcontainers 실행이 불가능했다 — `open -a Docker` 후 기동 대기(폴링)로 해결, 이후 전 태스크의 `./gradlew test`/`build`가 정상 동작했다. 코드 변경과 무관한 로컬 환경 이슈.

## User Setup Required

None - 로컬 실행은 `docker compose up -d` 후 `./gradlew bootRun`으로 기존과 동일하다. 이 플랜은 마이그레이션만 추가했을 뿐 환경변수·외부 서비스 설정을 요구하지 않는다.

## Next Phase Readiness

- 05-05(배치 차감 실행부)가 `PassTransaction(admin = null, member = null, reason = INACTIVITY)`를 저장할 때 DB 제약에 막히지 않는다 — V9 CHECK가 이미 허용
- 05-06(기준일 계산)이 `member.returnedFromLeaveAt`을 후보 ③으로 조회할 수 있는 실제 데이터 경로가 살아있다 — 관리자가 회원을 ON_LEAVE→ACTIVE로 전환하는 순간부터 값이 쌓인다
- `batch_execution`에 실행 1건을 기록할 준비가 됐다 — 05-07(스케줄러·실행 서비스)이 `BatchExecutionRepository.save(...)`만 호출하면 된다. MANUAL 트리거는 관리자 없이 저장 시도하면 DB가 거부한다(CHECK)
- `./gradlew build` 그린 확인 완료 — Testcontainers가 V1~V9 전체를 재생하고 엔티티 매핑을 검증했다
- Blocker 없음

---
*Phase: 05-batch*
*Completed: 2026-08-15*

## Self-Check: PASSED

All created/modified files and task commits (3bc85fe, 9e3096f, 721d7e5, 208cc31, 1fdfef0) verified present.
