---
phase: 03-pass
plan: 06
subsystem: api
tags: [spring-boot, jpa, kotlin, jwt-auth, springdoc, pass, transactional]

# Dependency graph
requires:
  - phase: 03-pass (03-01/03-02/03-04)
    provides: Pass/PassTransaction/PassType 엔티티, PassRepository/PassTransactionRepository, ErrorCode·PassExceptions, V4 스키마
provides:
  - "POST /api/admin/members/{memberId}/passes — 관리자 이용권 등록 API (저녁반 회비/예약제 횟수권/1:1 레슨권 공통)"
  - "AdminPassService.register — Pass 저장 + INITIAL_GRANT 이력을 같은 트랜잭션에서 처리"
  - "RegisterPassRequest/PassResponse — 이후 03-07~03-10이 재사용할 FE 계약"
  - "AuthenticatedPrincipal.requireAdminId — requireMemberId와 대칭인 관리자 주체 확정 헬퍼"
affects: [03-07-이용권-수동-가감, 03-08-기간수정, 03-09-등록취소, 03-10-회원-본인-조회]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "등록+초기이력을 같은 @Transactional 메서드에서 처리해 '이력 없는 잔여 변경'을 원천 차단 (CLAUDE.md 규칙 6)"
    - "ResponseEntity.created(...)로 Location 헤더를 주되, springdoc 문서화를 위해 @ResponseStatus(CREATED)를 함께 붙임 (실제 상태는 ResponseEntity가 결정)"
    - "JWT 만료 검증은 실제 시스템 시각 기준 — 날짜 단언이 필요한 통합테스트는 Clock을 Instant.now()로 리셋하고 LocalDate.now(clock)로 동적으로 비교"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/pass/dto/RegisterPassRequest.kt
    - src/main/kotlin/com/goldwrestling/pass/dto/PassResponse.kt
    - src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt
    - src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt
    - src/test/kotlin/com/goldwrestling/auth/AuthenticatedPrincipalTest.kt
    - src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/auth/AuthenticatedPrincipal.kt
    - docs/decisions.md
    - docs/api/openapi.yaml

key-decisions:
  - "D-068: 이용권 등록은 회원 상태(PENDING/ON_LEAVE/INACTIVE)로 제한하지 않는다 — 계획 단계 사용자 확정"

patterns-established:
  - "관리자 컨트롤러는 principal.requireAdminId()로만 주체 id를 얻는다 — 요청 바디로 adminId를 받지 않음 (T-03-22 이력 주체 위조 방지)"

requirements-completed: [PASS-01, PASS-02]

# Metrics
duration: 40min
completed: 2026-08-03
---

# Phase 3 Plan 06: 관리자 이용권 등록 API Summary

**관리자가 회원에게 저녁반 회비·예약제 횟수권·1:1 레슨권을 등록하는 `POST /api/admin/members/{memberId}/passes` API. 등록과 `INITIAL_GRANT` 이력 기록이 한 트랜잭션에서 원자적으로 처리된다.**

## Performance

- **Duration:** 약 40분
- **Started:** 2026-08-03T08:16:00Z (추정 — 세션 시작 시각 미기록)
- **Completed:** 2026-08-03T08:56:35Z
- **Tasks:** 3/3 완료
- **Files modified:** 9 (신규 6, 수정 3)

## Accomplishments

- `RegisterPassRequest`/`PassResponse` DTO 확정 — 이후 03-07~03-10 플랜이 그대로 재사용할 FE 계약
- `AdminPassService.register`가 `Pass.register` 조립 + `passRepository.save` + (횟수제만) `INITIAL_GRANT` `PassTransaction` 저장을 같은 `@Transactional` 메서드에서 처리
- `AdminPassController`가 `principal.requireAdminId()`로만 관리자 주체를 확정하고 201 + `Location` 헤더를 반환
- 통합테스트 10건(성공 2종·이력 검증 2종·startDate 기본값/과거 허용·대표 실패 3종·인가 2종)이 전부 통과
- `openapi.yaml` 재생성 — `POST /api/admin/members/{memberId}/passes`, `RegisterPassRequest`, `PassResponse` 스키마 추가

## Task Commits

Each task was committed atomically:

1. **Task 1: 요청·응답 DTO 2종 + AuthenticatedPrincipal.requireAdminId** - `7cf33b4` (feat)
2. **Task 2: AdminPassService.register + AdminPassController** - `57c20a5` (feat)
3. **Task 3: AdminPassControllerTest 통합테스트 + openapi.yaml 재생성** - `a5c04a5` (test)

**Plan metadata:** (이 커밋 — SUMMARY.md 작성 시점에 뒤따름)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/pass/dto/RegisterPassRequest.kt` - 등록 요청 DTO(형식 검증만, 도메인 규칙은 `Pass.register`에 위임)
- `src/main/kotlin/com/goldwrestling/pass/dto/PassResponse.kt` - 등록·이후 조회 API가 공유하는 응답 DTO, `displayStatus`는 `pass.displayStatus(today)` 위임
- `src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt` - 등록 유스케이스, Pass 저장 + INITIAL_GRANT 이력을 한 트랜잭션에서 처리
- `src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt` - `POST /api/admin/members/{memberId}/passes`, 201 + Location 헤더
- `src/main/kotlin/com/goldwrestling/auth/AuthenticatedPrincipal.kt` - `requireAdminId()` 추가(`requireMemberId`와 대칭)
- `src/test/kotlin/com/goldwrestling/auth/AuthenticatedPrincipalTest.kt` - `requireAdminId`/`requireMemberId` 4케이스 단위테스트
- `src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt` - 등록 API 통합테스트 10건
- `docs/decisions.md` - D-068 기록
- `docs/api/openapi.yaml` - 등록 경로·DTO 스키마 재생성

## Decisions Made

- **D-068** (docs/decisions.md에 원본 기록): 이용권 등록은 회원 상태로 제한하지 않는다. 계획 단계 AskUserQuestion에서 사용자가 "제한 없음, 관리자 재량"을 직접 선택했다 — 이번 실행은 그 결정을 그대로 구현했을 뿐 새로 판단하지 않았다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `AdminPassController`에 `@ResponseStatus(CREATED)` 추가**
- **Found during:** Task 3 (openapi.yaml 재생성 검증 중)
- **Issue:** `ResponseEntity.created(...)`로 실제 런타임 응답은 201이었지만, springdoc이 반환 타입 바이트코드에서 상태를 추론하지 못해 `openapi.yaml`에 `200`으로 잘못 문서화됨. FE가 문서만 보고 201을 기대하지 않게 될 위험.
- **Fix:** 메서드에 `@ResponseStatus(HttpStatus.CREATED)`를 함께 붙였다. Spring MVC는 컨트롤러가 `ResponseEntity`를 반환하면 그 안의 상태를 항상 우선하므로 런타임 동작(201)은 그대로이고, springdoc만 이 애노테이션을 읽어 문서를 201로 정정한다.
- **Verification:** `./gradlew test --tests AdminPassControllerTest`로 런타임 201 유지 확인(10/10 통과) 후 `generateApiDocs` 재실행, `git diff docs/api/openapi.yaml`에서 `"201": description: Created`로 반영됨을 확인.
- **Files modified:** `src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt`
- **Committed in:** `a5c04a5` (Task 3 커밋에 포함 — 발견 시점이 openapi 재생성 검증 단계였다)

---

**Total deviations:** 1 auto-fixed (Rule 1 — 버그 수정)
**Impact on plan:** FE 계약 정확성을 위한 필수 수정. 스코프 확장 없음.

## Issues Encountered

- **워크트리에 `.env` 부재**: `generateApiDocs`(Testcontainers 아닌 실제 DB 필요) 실행 시 `SCRAM-based authentication` 오류로 실패. 이 실행 워크트리(`.claude/worktrees/agent-ab0c6fe87cc7c15e3`)는 `.git` worktree라 gitignore된 `.env`가 메인 체크아웃에서 자동 복사되지 않는다. 메인 레포의 `.env`(실값 포함, 커밋 대상 아님)를 그대로 복사해 해결 — 시크릿 값을 로그·문서·커밋에 남기지 않았다.
- **테스트 Clock을 고정 시각으로 세팅하면 방금 발급한 JWT가 만료된 것으로 거부됨**: 최초 작성 시 `startDate` 기본값 단언을 위해 `Clock`을 임의의 고정 `Instant`(2026-08-03T10:00+09:00)로 세팅했더니, `JwtDecoder`(Nimbus)가 애플리케이션 `Clock`이 아니라 **실제 시스템 시각**으로 `exp`를 검증해 모든 인증 테스트가 401로 실패했다. `MemberSearchTest`의 `resetClock` 패턴(`Instant.now()`로 리셋)을 그대로 따르고, `startDate` 단언은 상수 대신 `LocalDate.now(clock)`로 그때그때 계산하도록 고쳐 해결했다.

## User Setup Required

None - no external service configuration required. (단, 로컬에서 `generateApiDocs`/`bootRun`을 실행하려면 이 워크트리에도 `.env`가 있어야 한다 — 위 Issues Encountered 참고. 이미 복사해 두었다.)

## Next Phase Readiness

- `PassResponse`가 확정되어 03-07(수동 가감)·03-08(기간수정)·03-09(등록취소)·03-10(회원 본인 조회)이 그대로 재사용 가능
- `AdminPassControllerTest`에 `// ---------- 등록 ----------` 섹션 주석을 남겨, 이후 플랜이 같은 클래스에 `// ---------- 가감 ----------` 등 섹션을 추가하며 컨텍스트 캐시를 재사용할 수 있도록 준비됨
- 이 플랜은 이용권이 처음 생기는 경로라, 이후 모든 플랜의 통합테스트 픽스처가 이 등록 API(또는 `Pass.register` 직접 호출)를 기반으로 만들어질 것

---
*Phase: 03-pass*
*Completed: 2026-08-03*

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/pass/dto/RegisterPassRequest.kt
- FOUND: src/main/kotlin/com/goldwrestling/pass/dto/PassResponse.kt
- FOUND: src/main/kotlin/com/goldwrestling/pass/AdminPassService.kt
- FOUND: src/main/kotlin/com/goldwrestling/pass/AdminPassController.kt
- FOUND: src/test/kotlin/com/goldwrestling/auth/AuthenticatedPrincipalTest.kt
- FOUND: src/test/kotlin/com/goldwrestling/pass/AdminPassControllerTest.kt
- FOUND commit: 7cf33b4 (Task 1)
- FOUND commit: 57c20a5 (Task 2)
- FOUND commit: a5c04a5 (Task 3)
