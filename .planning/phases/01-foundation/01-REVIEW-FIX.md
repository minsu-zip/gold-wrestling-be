---
phase: 01-foundation
fixed_at: 2026-07-30T23:48:27Z
review_path: .planning/phases/01-foundation/01-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 1: Code Review Fix Report

**Fixed at:** 2026-07-30T23:48:27Z
**Source review:** .planning/phases/01-foundation/01-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (Critical 0, Warning 4 — fix_scope: critical_warning, Info 10건은 스코프 밖)
- Fixed: 4
- Skipped: 0

모든 수정 후 `./gradlew ktlintFormat` → `./gradlew build` 그린 확인 (테스트 20개 전부 통과).
API 표면 변화 없음 — `generateApiDocs` 재실행으로 `docs/api/openapi.yaml` diff 없음을 확인했다.

## Fixed Issues

### WR-01: resolveErrorCode 폴백이 code↔상태 불일치 응답을 만든다

**Files modified:** `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt`, `src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt`, `docs/error-codes.md`, `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt`
**Commit:** f37d898
**Applied fix:**
- `NOT_ACCEPTABLE`(406)을 `ErrorCode` enum과 `docs/error-codes.md`에 추가하고 `HttpMediaTypeNotAcceptableException`을 명시 매핑 — "406 + INTERNAL_ERROR" 재현 경로 차단
- `ServletRequestBindingException`(필수 헤더 누락 등) → `MALFORMED_REQUEST` 명시 매핑 — enum 선언 순서에 따라 `VALIDATION_FAILED`로 오분류되던 경로 제거
- 폴백을 상태값 추측(`entries.firstOrNull`) 대신 **4xx → `MALFORMED_REQUEST` / 그 외 → `INTERNAL_ERROR`** 고정으로 교체하고, 이 폴백 규칙을 error-codes.md에 계약으로 명시 (D-028)
- 통합테스트 3개 추가: 406+NOT_ACCEPTABLE, 필수 헤더 누락 400+MALFORMED_REQUEST, 매핑되지 않은 4xx(`ResponseStatusException` 409) 폴백

### WR-02: 포괄 Exception 핸들러가 향후 시큐리티 인가 예외를 500으로 삼킨다

**Files modified:** `src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt`, `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt`
**Commit:** cf5ba22
**Applied fix:**
- `handleUnexpectedException`에서 `AccessDeniedException`·`AuthenticationException`을 되던져 `ExceptionTranslationFilter`가 401/403으로 처리하게 함. 전제·후속 계획(인증 phase의 EntryPoint/AccessDeniedHandler)을 KDoc에 기록
- 예외 클래스 패키지는 verify-boot4-api 절차대로 로컬 Gradle 캐시의 `spring-security-core-7.1.0.jar`에서 실물 확인 (Security 7에서 패키지 동일, `AuthorizationDeniedException`이 `AccessDeniedException` 하위임도 확인)
- 컨트롤러 안에서 던져진 `AccessDeniedException`이 500이 아닌 403으로 응답되는 통합테스트 추가 (실행으로 되던짐→필터 처리 경로 검증)

### WR-03: generateApiDocs가 잔존(stale) 프로세스의 낡은 스펙을 조용히 커밋할 수 있다

**Files modified:** `build.gradle.kts`
**Commit:** 859793a
**Applied fix:**
- `startApiDocsApp`: 기동 전 (1) `app.pid`에 남은 이전 프로세스를 kill 후 종료 대기, (2) 그래도 포트 8099의 `/actuator/health`가 응답하면(추적되지 않는 미지의 프로세스) 즉시 실패
- `waitApiDocsApp`: health 폴링마다 `kill -0`으로 우리가 띄운 PID의 생존을 함께 확인 — 포트 충돌로 즉사한 경우를 "기동 성공"으로 오인하지 않음
- 검증: ① `./gradlew generateApiDocs` 정상 실행 + openapi.yaml diff 없음, ② 8099에 가짜 서버를 띄운 시뮬레이션에서 즉시 실패(에러 메시지 출력) 확인
- 테스트 미작성 사유: `build.gradle.kts`는 conventions §10.0 면제 대상 — 실제 파이프라인 실행 + 실패 시나리오 시뮬레이션으로 검증을 갈음

### WR-04: 프로덕션 산출물이 .planning 전용 결정 ID(D-01~D-09)를 인용해 decisions.md 번호 체계와 충돌한다

**Files modified:** `build.gradle.kts`, `docs/decisions.md`, `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt`, `src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt`, `src/main/kotlin/com/goldwrestling/member/Member.kt`, `src/main/kotlin/com/goldwrestling/admin/Admin.kt`
**Commit:** 3b11c1b
**Applied fix:**
- 참조 교체: `D-01/D-02/D-03`(gradle) → `D-029`, `D-06`(ErrorCode·핸들러) → `D-028`, `D-05`(Member) → `D-025`, `D-04`(Admin) → `D-030`
- 미기록 결정 등재 (CLAUDE.md 규칙 5): **D-030** 초기 스키마 최소 원칙, **D-031** Branch Flyway 시드 — 각 항목의 "유의"에 V2 마이그레이션 주석의 로컬 ID(D-04/D-09)와의 대응 관계를 기록
- `docs/decisions.md`의 D-029 본문에 있던 깨진 "(D-03)" 참조를 문구로 풀어씀. 예시 플레이스홀더는 D-032로 이동
- `V2__create_branch_member_admin.sql`은 커밋된 마이그레이션 수정 금지 원칙에 따라 손대지 않음 (리뷰 Fix 지침과 일치)
- 테스트 미작성 사유: 주석·문서만 변경 (conventions §10.0 면제 — 문서)

---

_Fixed: 2026-07-30T23:48:27Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
