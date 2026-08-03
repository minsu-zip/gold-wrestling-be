---
phase: 03-pass
plan: 07
subsystem: api

# Dependency graph
requires:
  - phase: 03-pass (03-03)
    provides: "Pass.validateAdjustment 판정 메서드 (policies §4.2a, D-056)"
  - phase: 03-pass (03-06)
    provides: "AdminPassService/AdminPassController 뼈대, PassResponse, AdminPassControllerTest 골격"
provides:
  - "POST /api/admin/passes/{passId}/adjustments — 관리자 수동 가감 API (사유 필수)"
  - "AdminPassService.adjust — 사전 판정 → 조건부 UPDATE → 재조회 → 이력 저장 3단 구조"
  - "PassLedgerInvariantTest — \"잔여 = 이력 합계\" Core Value를 빌드가 강제하는 통합테스트"
affects: ["03-08 (기간수정)", "03-09 (등록 취소, PassLedgerInvariantTest에 취소 케이스 추가)", "Phase 4 (예약 차감이 동일한 3단 구조 재사용)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "사전 판정(엔티티) → 조건부 UPDATE(DB) → 반환 0이면 재조회 후 정확한 예외 → 같은 트랜잭션 이력 저장 (D-021, TokenService.rotate와 동일 계열)"
    - "벌크 UPDATE(clearAutomatically=true) 호출 전 필요한 스칼라 값을 지역 변수로 미리 확보 — 이후 준영속 엔티티의 LAZY 접근 금지"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/pass/dto/AdjustPassRequest.kt
    - src/test/kotlin/com/goldwrestling/pass/PassLedgerInvariantTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt
    - src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt
    - src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt
    - docs/api/openapi.yaml

key-decisions:
  - "note 필드가 JSON에 아예 없으면 Kotlin non-null 파라미터 역직렬화 단계에서 MALFORMED_REQUEST — 값이 공백이면 @NotBlank로 VALIDATION_FAILED. 기존 MemberStatusChangeTest 계열과 동일 규칙을 그대로 따름(신규 결정 아님, 기존 관례 적용)"

patterns-established:
  - "adjustRemainingCount 반환 0 = 경쟁 패배 또는 사전 판정 이후 상태 변경 → 재조회 후 판정을 다시 돌려 정확한 도메인 예외로 변환, 그래도 원인 불명이면 PassStateConflictException"

requirements-completed: [PASS-02, PASS-03]

# Metrics
duration: 15min
completed: 2026-08-03
---

# Phase 3 Plan 07: 관리자 수동 가감 API + 원장 불변식 테스트 Summary

**관리자 수동 가감 API(POST /api/admin/passes/{passId}/adjustments)를 사전 판정→조건부 UPDATE→이력 3단 구조로 구현하고, "잔여 = PassTransaction 이력 합계" Core Value를 PassLedgerInvariantTest로 실제 PostgreSQL에서 증명**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-08-03T09:00:00Z (approx.)
- **Completed:** 2026-08-03T09:10:10Z
- **Tasks:** 3
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments
- `AdminPassService.adjust`: 조회 → `Pass.validateAdjustment`(사전 판정) → `PassRepository.adjustRemainingCount`(조건부 UPDATE) → 반환 0이면 재조회 후 정확한 도메인 예외, 성공이면 재조회한 영속 `Pass`로 `ADMIN_ADJUST` 이력을 같은 트랜잭션에서 저장
- `POST /api/admin/passes/{passId}/adjustments` 엔드포인트 + 통합테스트 10건(성공 2, 실패 5종, 인가 2, note 필드 누락 1) 추가, `openapi.yaml` 재생성
- `PassLedgerInvariantTest`: 등록·연속 가감·정확히 0 소진·거부된 가감·기간제 5개 시나리오에서 잔여와 이력 합계가 항상 같음을 실증 — 이 프로젝트의 Core Value가 처음으로 빌드가 막는 실행 가능한 테스트가 됨

## Task Commits

Each task was committed atomically:

1. **Task 1: AdjustPassRequest DTO + AdminPassService.adjust** - `5a761de` (feat)
2. **Task 2: 가감 엔드포인트 + 통합테스트 + openapi 재생성** - `69a61b1` (feat)
3. **Task 3: PassLedgerInvariantTest — "잔여 = 이력 합계" 증명** - `b4a8243` (test)

_Plan metadata commit intentionally omitted — CLAUDE.md 커밋 규칙에 따라 execute-phase 시작 시 사용자가 명시적으로 승인한 태스크 단위 커밋만 실행했다. STATE.md/ROADMAP.md는 오케스트레이터가 별도로 갱신한다._

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/pass/dto/AdjustPassRequest.kt` - 가감 요청 DTO(amount 형식 검증, note NotBlank — D-061)
- `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt` - `adjust` 메서드 추가(사전 판정→조건부 UPDATE→재조회→이력)
- `src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt` - `POST /passes/{passId}/adjustments` 추가
- `src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt` - "수동 가감" 섹션 10건 추가(등록 섹션 10건 + 신규 10건 = 20건)
- `src/test/kotlin/com/goldwrestling/pass/PassLedgerInvariantTest.kt` - 원장 불변식 통합테스트 5건 (신규)
- `docs/api/openapi.yaml` - `AdjustPassRequest` 스키마 및 `/api/admin/passes/{passId}/adjustments` 경로 추가 (`generateApiDocs` 재생성)

## Decisions Made
- `note` 필드 검증 실패 경로를 두 가지로 분리: 필드 자체가 없으면(Kotlin non-null 파라미터 역직렬화 실패) `MALFORMED_REQUEST`, 값이 공백이면(`@NotBlank` Bean Validation) `VALIDATION_FAILED`. 새로운 결정이 아니라 이미 `MemberStatusChangeTest`가 확립한 코드베이스 관례를 그대로 적용한 것 — 계획 문서의 "note 누락/공백 → 400 VALIDATION_FAILED" 표현을 실제 동작에 맞게 두 케이스로 분리해 정확히 검증했다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] AdjustPassRequest.kt KDoc·Schema에서 "0.5" 문자열 제거**
- **Found during:** Task 1 자체 점검 (acceptance_criteria: "AdjustPassRequest.kt에 0.5 문자열이 없다")
- **Issue:** 초안 KDoc·`@Schema(description=...)`에 "0.5 단위" 문구가 들어가 도메인 규칙이 DTO 문서에 노출됨(도메인 규칙은 `Pass.validateAdjustment` 전용이어야 함)
- **Fix:** "0.5 단위" 대신 "가감 단위 배수"처럼 구체적 수치 없이 서술하도록 문구 수정
- **Files modified:** src/main/kotlin/com/goldwrestling/pass/dto/AdjustPassRequest.kt
- **Verification:** `grep -c "0.5" AdjustPassRequest.kt` == 0, `./gradlew compileKotlin` 통과
- **Committed in:** `5a761de` (Task 1 commit)

**2. [Rule 1 - Bug] "note 누락" 테스트의 기대 에러코드를 VALIDATION_FAILED → MALFORMED_REQUEST로 정정**
- **Found during:** Task 2 (`AdminPassControllerTest` 첫 실행 시 1건 실패)
- **Issue:** `note: String`은 Kotlin non-null 생성자 파라미터라 JSON에 키 자체가 없으면 `@Valid`가 실행되기 전 jackson-module-kotlin 역직렬화 단계에서 이미 실패(`HttpMessageNotReadableException`) → `MALFORMED_REQUEST`가 나가는데, 최초 작성한 테스트는 `VALIDATION_FAILED`를 기대해 실패함
- **Fix:** 이 코드베이스의 기존 관례(`MemberStatusChangeTest`의 "status 필드가 없으면 MALFORMED_REQUEST" 케이스)를 그대로 따라 테스트 기대값·이름을 정정하고, "공백"과 "필드 자체 누락"을 별도 테스트로 분리
- **Files modified:** src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt
- **Verification:** `./gradlew test --tests "com.goldwrestling.pass.AdminPassControllerTest"` 20건 전부 green
- **Committed in:** `69a61b1` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (모두 Rule 1 — 계획 문서와 실제 코드베이스 관례 사이의 사소한 표현 불일치 정정)
**Impact on plan:** 두 건 모두 계획의 의도(도메인 규칙 DTO 밖 유지, note 검증 실패 커버리지)를 그대로 달성하기 위한 표현·기대값 정정. 스코프 확장 없음.

## Issues Encountered
- 워크트리에 `.env`가 없어 `./gradlew generateApiDocs`가 실패할 상황이었음 — 03-06과 동일하게 메인 레포의 `.env`를 워크트리로 복사(커밋 대상 아님, `.gitignore`로 제외됨 확인)해 해결
- 워크트리 브랜치가 스폰 시점 base(1d048c753d7b9bb4619523eb20bea7e80996ee69)보다 뒤처져 있어(초기 커밋만 존재) 실행 시작 전 `git reset --hard`로 base를 맞춤 — worktree_branch_check 절차대로 처리

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- 03-08(기간 수정)이 참조할 `AdminPassService`·`AdminPassController` 구조가 03-07과 동일한 패턴(사전 판정→조건부 UPDATE→이력)을 그대로 이어갈 수 있음
- 03-09(등록 취소)는 `PassLedgerInvariantTest`에 취소 케이스를 추가하는 것으로 원장 불변식 검증을 완성하면 됨 (파일 상단 주석에 명시)
- Phase 4(예약 차감)가 이번 plan에서 확립한 "사전 판정(엔티티)→조건부 UPDATE(DB)→반환 0 재조회→같은 트랜잭션 이력" 구조를 그대로 재사용 가능
- 차단 요소 없음

---
*Phase: 03-pass*
*Completed: 2026-08-03*
