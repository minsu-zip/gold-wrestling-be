---
phase: 02-auth-member
plan: 05
subsystem: auth
tags: [spring-security, jwt, filter-chain, problemdetail, rfc9457, authorization]

# Dependency graph
requires:
  - phase: 02-auth-member (02-02, 02-03, 02-04)
    provides: Member/Admin 엔티티, PrincipalType, MemberStatus, JwtDecoder/JwtEncoder 빈(JwtConfig), TokenService
provides:
  - "AuthenticatedPrincipal 값 객체 + AuthenticationPrincipalResolver — 토큰 클레임이 아니라 DB 현재 상태로 인가 판단(D-033)"
  - "JwtAuthenticationFilter — 예외를 던지지 않는 인증 필터(RESEARCH Pitfall 1 회피)"
  - "ProblemDetailResponseWriter/AuthenticationEntryPoint/AccessDeniedHandler — 401/403을 GlobalExceptionHandler와 동일한 problem+json + code 형식으로 통일"
  - "SecurityConfig — permitAll 뼈대를 공개/ROLE_MEMBER/ROLE_ADMIN 역할 기반 인가로 교체, anyRequest().authenticated() 기본값 거부"
affects: [02-06, 02-07, 02-08, 02-09, 02-10, 02-11]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "커스텀 인증 필터는 @Component 없이 SecurityConfig가 생성자 호출로 만들어 addFilterBefore로만 등록"
    - "인증 필터는 실패 시 예외를 던지지 않고 SecurityContext를 비운 채 통과 — 거부는 뒤의 AuthorizationFilter/ExceptionTranslationFilter가 담당"
    - "@AuthenticationPrincipal 값 객체는 엔티티를 담지 않는다 — LazyInitializationException 방지, 실제 엔티티는 서비스에서 재조회"
    - "401/403 응답 조립은 ProblemDetailResponseWriter 한 곳에 모아 EntryPoint/AccessDeniedHandler가 재사용"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/auth/AuthenticatedPrincipal.kt
    - src/main/kotlin/com/goldwrestling/auth/AuthenticationPrincipalResolver.kt
    - src/main/kotlin/com/goldwrestling/auth/JwtAuthenticationFilter.kt
    - src/main/kotlin/com/goldwrestling/common/error/ProblemDetailResponseWriter.kt
    - src/main/kotlin/com/goldwrestling/common/error/ProblemDetailAuthenticationEntryPoint.kt
    - src/main/kotlin/com/goldwrestling/common/error/ProblemDetailAccessDeniedHandler.kt
    - src/test/kotlin/com/goldwrestling/config/SecurityFilterChainTest.kt
    - src/test/kotlin/com/goldwrestling/auth/JwtAuthenticationFilterTest.kt
  modified:
    - src/main/kotlin/com/goldwrestling/config/SecurityConfig.kt
    - src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt

key-decisions:
  - "익명(비로그인) 사용자가 컨트롤러 안에서 AccessDeniedException을 만나면 403이 아니라 401로 응답한다 — Spring Security의 ExceptionTranslationFilter가 '익명 + AccessDeniedException'을 AccessDeniedHandler가 아니라 AuthenticationEntryPoint로 위임하는 표준 동작이며, 인증되지 않은 사용자에게는 '권한 없음'이 아니라 '로그인 필요'가 정확한 신호다"
  - "GlobalExceptionHandlerTest의 테스트 전용 경로(TestErrorController)를 /internal-test에서 permitAll인 /api/system/internal-test로 이동 — anyRequest().authenticated() 기본값 거부 규칙과 충돌해 예외 변환 검증 자체가 401로 막히는 것을 방지"

patterns-established:
  - "인가 규칙은 SecurityConfig의 authorizeHttpRequests(역할)만 담당하고, 회원 상태(ACTIVE 등) 조건은 서비스 계층에서 검사한다(D-040) — 이 플랜은 그 분업의 앞부분(역할)만 구현했다"

requirements-completed: [AUTH-02, AUTH-04]

# Metrics
duration: 45min
completed: 2026-08-02
---

# Phase 2 Plan 5: 인증 필터체인 + 역할 기반 인가 Summary

**JWT Bearer 인증 필터 + ProblemDetail 401/403 통일 + SecurityConfig의 permitAll 뼈대를 공개/ROLE_MEMBER/ROLE_ADMIN 역할 기반 인가로 교체**

## Performance

- **Duration:** 약 45분
- **Tasks:** 3 (계획대로 3개 태스크, Task 3는 TDD RED→GREEN 2커밋으로 분리)
- **Files modified:** 10 (신규 8, 수정 2)

## Accomplishments

- Bearer 토큰이 매 요청 DB 현재 상태 기준으로 인증 주체(`AuthenticatedPrincipal`)로 변환된다(D-033) — 회원 상태를 DB에서 바꾸면 같은 access 토큰이라도 즉시 반영됨을 통합테스트로 증명
- 인증 필터가 어떤 경로에서도 예외를 던지지 않아, 만료·위조·`alg none`·`Bearer` 접두 누락 모두 `application/problem+json` 401로 일관되게 응답(HTML 에러 페이지 없음)
- 401/403이 `GlobalExceptionHandler`와 동일한 형식(`code` 필드 포함)으로 통일됨
- `SecurityConfig`의 `anyRequest().permitAll()` 뼈대가 사라지고, 역할 기반 인가 + 기본값 거부(`anyRequest().authenticated()`)로 교체됨 — ROADMAP Phase 2 Note가 예고한 작업 완료
- `/api/system`, `/v3/api-docs`, `/swagger-ui`, `/api/auth`는 계속 인증 없이 접근 가능함을 회귀 없이 유지

## Task Commits

1. **Task 1: AuthenticatedPrincipal + AuthenticationPrincipalResolver + JwtAuthenticationFilter** - `8526dd9` (feat)
2. **Task 2: 401/403 응답을 ProblemDetail + code로 통일** - `d00e8da` (feat)
3. **Task 3: SecurityConfig 교체 + 필터체인 통합테스트**
   - RED (테스트 먼저, 구 permitAll 대비 10/15 실패 확인) - `a73e046` (test)
   - GREEN (SecurityConfig 교체, 전체 통과) - `c03e1aa` (feat)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/auth/AuthenticatedPrincipal.kt` - 엔티티를 담지 않는 인증 주체 값 객체
- `src/main/kotlin/com/goldwrestling/auth/AuthenticationPrincipalResolver.kt` - principalType/Id → DB 현재 상태 조회(D-033)
- `src/main/kotlin/com/goldwrestling/auth/JwtAuthenticationFilter.kt` - 예외를 던지지 않는 Bearer 토큰 검증 필터
- `src/main/kotlin/com/goldwrestling/common/error/ProblemDetailResponseWriter.kt` - 401/403 응답 조립 헬퍼(Jackson 3)
- `src/main/kotlin/com/goldwrestling/common/error/ProblemDetailAuthenticationEntryPoint.kt` - 401 + UNAUTHENTICATED
- `src/main/kotlin/com/goldwrestling/common/error/ProblemDetailAccessDeniedHandler.kt` - 403 + ACCESS_DENIED
- `src/main/kotlin/com/goldwrestling/config/SecurityConfig.kt` - permitAll → 역할 기반 인가 + 필터 등록 + exceptionHandling
- `src/test/kotlin/com/goldwrestling/config/SecurityFilterChainTest.kt` - URL 인가 규칙 통합테스트(공개/ROLE_MEMBER/ROLE_ADMIN)
- `src/test/kotlin/com/goldwrestling/auth/JwtAuthenticationFilterTest.kt` - 필터 실패/성공 경로 + D-033 상태 재조회 통합테스트
- `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt` - 테스트 전용 경로 이동 + access-denied 테스트 기대값 수정(아래 Deviations 참고)

## Decisions Made

- 익명 사용자의 `AccessDeniedException`은 403이 아니라 401로 응답한다(위 key-decisions 참고, Spring Security 표준 동작을 그대로 수용)
- `GlobalExceptionHandlerTest`의 테스트 전용 컨트롤러 경로를 permitAll 영역(`/api/system/internal-test`) 아래로 이동

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `GlobalExceptionHandlerTest`의 테스트 전용 경로가 새 default-deny 규칙에 막혀 회귀**
- **Found during:** Task 3 (SecurityConfig 교체 후 전체 테스트 실행)
- **Issue:** `anyRequest().authenticated()`를 마지막 규칙으로 추가하면서, `GlobalExceptionHandlerTest`가 쓰던 `/internal-test/**`·`/api/nonexistent` 경로가 어떤 permitAll 규칙에도 걸리지 않아 401로 막혔다 — 400/404/405/415/500 등 예외 변환 로직 자체에 도달하지 못해 Phase 1 테스트 전부가 깨지는 상황이었다
- **Fix:** 테스트 전용 컨트롤러(`TestErrorController`)와 `/api/nonexistent` 검증 경로를 permitAll인 `/api/system/internal-test`·`/api/system/nonexistent` 하위로 이동. 프로덕션 컨트롤러가 아니므로(`@TestConfiguration`으로만 등록) `docs/api/openapi.yaml`에는 영향 없음
- **Files modified:** `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt`
- **Verification:** `GlobalExceptionHandlerTest` 12개 테스트 전부 통과
- **Committed in:** `a73e046` (RED 커밋에 포함)

**2. [Rule 1 - Bug] "인가 예외는 403" 테스트가 새 EntryPoint 도입 후 401로 바뀜**
- **Found during:** Task 3 전체 테스트 실행
- **Issue:** `컨트롤러 안에서 던져진 시큐리티 인가 예외는 500 으로 삼켜지지 않고 403 으로 처리된다` 테스트는 Phase 1에 커스텀 `AuthenticationEntryPoint`가 없어 스프링 기본값(`Http403ForbiddenEntryPoint`, 403)에 의존하고 있었다. 이 플랜이 `ProblemDetailAuthenticationEntryPoint`를 등록하면서, 익명 사용자의 `AccessDeniedException`은 `ExceptionTranslationFilter`가 (스프링 표준 동작대로) `AccessDeniedHandler`가 아니라 `AuthenticationEntryPoint`로 위임해 401이 나가도록 바뀌었다 — 버그가 아니라 더 정확한 동작(비로그인 사용자에게는 "권한 없음"이 아니라 "로그인 필요"가 맞다)
- **Fix:** 테스트 이름과 기대값을 401 + `UNAUTHENTICATED` + `application/problem+json`으로 수정. "인증된 사용자가 역할 부족으로 403을 받는" 경로는 `SecurityFilterChainTest`(관리자 전용 경로에 회원 토큰 접근)가 별도로 검증하므로 커버리지 공백 없음
- **Files modified:** `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt`
- **Verification:** 해당 테스트 통과, 전체 76개 테스트 통과
- **Committed in:** `a73e046` (RED 커밋에 포함)

---

**Total deviations:** 2 auto-fixed (둘 다 Rule 1 — Task 3의 SecurityConfig 교체가 유발한 기존 테스트 회귀 수정)
**Impact on plan:** 계획된 프로덕션 코드 범위는 그대로. 기존 Phase 1 테스트 2개의 기대값만 새로 도입된 보안 계층의 정확한 동작에 맞게 조정했다. 스코프 확장 없음.

## Issues Encountered

- **로컬 `.env`가 전부 빈 값이라 `@SpringBootTest`가 부팅 시 `JWT_ACCESS_TOKEN_EXPIRY_MINUTES` 등 숫자 프로퍼티 바인딩에서 즉시 실패**(이 워크트리뿐 아니라 이 플랜 이전부터 존재하던 환경 문제 — `JwtConfigTest`로 재현·확인함). 실제 `.env` 값은 알 수 없어(로컬 시크릿) 건드리지 않고, 테스트 실행 시에만 `JWT_SECRET`·`JWT_ACCESS_TOKEN_EXPIRY_MINUTES`·`JWT_REFRESH_TOKEN_EXPIRY_DAYS`·`SERVER_PORT`·`CORS_ALLOWED_ORIGINS`·`TZ`를 셸 환경변수로 임시 주입해 실행했다(커밋된 파일에는 반영되지 않음). `.env` 파일 자체는 메인 레포에서 복사해 `.env.example` 대비 어떤 키가 비어 있는지만 확인했고, 값은 출력하지 않았다.
- **`docker compose up -d && ./gradlew generateApiDocs` 를 직접 실행하지 못했다.** 로컬 Docker에 이미 `gold-wrestling-postgres` 컨테이너(호스트 공유, 3일째 기동 중)가 떠 있는데, 이 워크트리의 `.env`에는 그 컨테이너를 초기화할 때 쓰인 실제 비밀번호가 없다(빈 값). 사용자의 다른 작업과 공유되는 컨테이너/볼륨을 재기동·재생성해 데이터를 건드리는 위험을 피하려고 이 단계는 건너뛰었다. 대신 `/v3/api-docs`·`/actuator/health`·`/api/system/health`가 인증 없이 200을 반환함을 `SecurityFilterChainTest`(Testcontainers 기반 실제 통합테스트, 새 `SecurityConfig`를 그대로 통과)로 증명했다 — `generateApiDocs`가 의존하는 것과 동일한 엔드포인트·동일한 인가 규칙이다. 이 플랜은 새 API 엔드포인트를 추가하지 않으므로 `docs/api/openapi.yaml`도 수정하지 않았다(`git diff` 비어 있음, plan의 명시적 기대와 일치).

## User Setup Required

None - 외부 서비스 설정 불필요. 단, 로컬에서 `./gradlew test`/`build`를 직접 돌리려면 `.env`의 `JWT_SECRET`·`JWT_ACCESS_TOKEN_EXPIRY_MINUTES`·`JWT_REFRESH_TOKEN_EXPIRY_DAYS` 값을 채워야 한다(현재 빈 값).

## Next Phase Readiness

- 이후 모든 회원/관리자 엔드포인트(02-06~02-11)는 컨트롤러에서 `@AuthenticationPrincipal AuthenticatedPrincipal`로 인증 주체를 받을 수 있다
- `SecurityConfig`의 역할 분리(`/api/members`→MEMBER, `/api/admin`→ADMIN)가 고정되어, 새 엔드포인트는 이 두 경로 규칙 중 하나에 자연히 속하거나 명시적으로 규칙을 추가해야 한다(빠뜨리면 401로 즉시 드러남)
- 회원 상태(`ACTIVE` 등) 게이트는 이 플랜 범위 밖 — D-040에 따라 `MemberStateGate`(서비스 계층)가 이후 플랜에서 구현될 예정
- 블로커 없음. 로컬 `.env` 값 채움은 사용자 액션이 필요하지만 이 플랜의 완료를 막지 않는다(위 Issues Encountered 참고)

---
*Phase: 02-auth-member*
*Completed: 2026-08-02*

## Self-Check: PASSED

- 생성 파일 10개 전부 `FOUND` (main 6, test 3, SUMMARY 1)
- 커밋 해시 4개(`8526dd9`, `d00e8da`, `a73e046`, `c03e1aa`) 전부 `git log`에서 `FOUND`
- `./gradlew build` BUILD SUCCESSFUL (ktlintCheck + compile + 전체 테스트 76개 통과)
