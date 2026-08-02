---
phase: 02-auth-member
plan: 07
subsystem: auth
tags: [spring-security, password-encoding, bcrypt, application-runner, jwt, openapi]

# Dependency graph
requires:
  - phase: 02-auth-member (02-04)
    provides: TokenService (issueTokenPair/rotate), RefreshToken, PrincipalType
  - phase: 02-auth-member (02-05)
    provides: SecurityConfig(URL 역할 규칙), GlobalExceptionHandler(ProblemDetail)
  - phase: 02-auth-member (02-06)
    provides: docs/api/openapi.yaml 현재 상태(카카오 로그인 계약)
provides:
  - PasswordEncoder 빈(DelegatingPasswordEncoder, D-045)
  - AdminSeeder — ApplicationRunner 기반 멱등 관리자 시드(D-038)
  - POST /api/auth/admin/login — 관리자 ID/PW 로그인, 회원과 동일한 JWT 체계(D-026)
  - InvalidCredentialsException — 계정 열거 방지용 단일 실패 응답(T-02-24)
affects: [02-09(관리자 기능), 02-10(관리자 기능)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ApplicationRunner 기반 멱등 시드 — 환경마다 달라야 하는 시크릿(비밀번호)은 Flyway INSERT 대신 기동 시 존재 확인 후 생성"
    - "실패 사유를 구분하지 않는 단일 예외로 계정 열거 공격 차단"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/config/PasswordEncoderConfig.kt
    - src/main/kotlin/com/goldwrestling/config/AdminSeedProperties.kt
    - src/main/kotlin/com/goldwrestling/admin/AdminSeeder.kt
    - src/main/kotlin/com/goldwrestling/auth/AdminAuthService.kt
    - src/main/kotlin/com/goldwrestling/auth/AdminAuthController.kt
    - src/main/kotlin/com/goldwrestling/auth/dto/AdminLoginRequest.kt
    - src/main/kotlin/com/goldwrestling/auth/dto/AdminLoginResponse.kt
    - src/test/kotlin/com/goldwrestling/admin/AdminSeederTest.kt
    - src/test/kotlin/com/goldwrestling/auth/AdminAuthControllerTest.kt
  modified:
    - src/main/resources/application.yml
    - docs/api/openapi.yaml

key-decisions:
  - "PasswordEncoder.encode()의 반환 타입이 jspecify @Nullable(플랫폼 타입)이라 checkNotNull로 명시 처리 — Spring의 @Contract(non-null in → non-null out)를 Kotlin 컴파일러가 인식하지 못함"
  - "AdminSeederTest는 이 플랜에서만 @SpringBootTest(properties=[...])로 admin-seed 값을 재정의해 별도 컨텍스트를 띄운다 — 기본 컨텍스트는 시드 환경변수가 항상 비어 있어(.env 없음) 실제 생성 동작을 검증할 수 없기 때문"

requirements-completed: [AUTH-02, AUTH-03]

# Metrics
duration: ~20min
completed: 2026-08-03
---

# Phase 02 Plan 07: 관리자 ID/PW 인증 Summary

**DelegatingPasswordEncoder + ApplicationRunner 멱등 시드로 첫 관리자 계정을 만들고, `/api/auth/admin/login`이 회원과 동일한 JWT 체계(access/refresh)를 발급하도록 구현**

## Performance

- **Duration:** ~20분
- **Completed:** 2026-08-03
- **Tasks:** 3/3 완료
- **Files modified:** 11 (신규 9, 수정 2)

## Accomplishments
- `PasswordEncoderFactories.createDelegatingPasswordEncoder()` 빈 등록(D-045) — `{bcrypt}` 접두로 알고리즘을 저장 값 자체에 기술
- `AdminSeeder`(`ApplicationRunner`) — 기동 시 `ADMIN_SEED_*` 환경변수가 채워져 있고 해당 `loginId`가 없을 때만 관리자 1명을 멱등 생성(D-038). 빈 값이면 경고 로그만 남기고 앱은 정상 기동
- `POST /api/auth/admin/login` — loginId/password 검증 후 `TokenService.issueTokenPair(PrincipalType.ADMIN, ...)`로 회원과 동일한 access/refresh 토큰 쌍 발급(D-026). refresh 회전·로그아웃 경로도 회원과 그대로 공유됨을 통합테스트로 확인
- `InvalidCredentialsException` — loginId 없음/비밀번호 불일치를 완전히 동일한 401 + `INVALID_CREDENTIALS` + 동일 `detail` 문자열로 응답(T-02-24, 계정 열거 방지)
- `docs/api/openapi.yaml` 재생성 — `/api/auth/admin/login` 경로와 `AdminLoginRequest`/`AdminLoginResponse`/`AdminSummary` 스키마 추가, `passwordHash`·`loginId` 미노출 확인, 기존 경로 그대로 유지

## Task Commits

1. **Task 1: PasswordEncoder 빈 + 멱등 관리자 시드** - `10dc272` (feat)
2. **Task 2: 관리자 ID/PW 로그인 엔드포인트** - `9e7e522` (feat)
3. **Task 3: openapi.yaml 재생성 및 확인** - `fe12559` (docs)

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/config/PasswordEncoderConfig.kt` - `DelegatingPasswordEncoder` 빈(D-045)
- `src/main/kotlin/com/goldwrestling/config/AdminSeedProperties.kt` - `goldwrestling.admin-seed.*` 바인딩(loginId/password/name)
- `src/main/kotlin/com/goldwrestling/admin/AdminSeeder.kt` - 멱등 관리자 시드(`ApplicationRunner`, D-038)
- `src/main/kotlin/com/goldwrestling/auth/AdminAuthService.kt` - 로그인 검증·토큰 발급 조립 + `InvalidCredentialsException`
- `src/main/kotlin/com/goldwrestling/auth/AdminAuthController.kt` - `POST /api/auth/admin/login`
- `src/main/kotlin/com/goldwrestling/auth/dto/AdminLoginRequest.kt` - 요청 DTO(형식 검증만)
- `src/main/kotlin/com/goldwrestling/auth/dto/AdminLoginResponse.kt` - 응답 DTO(`AdminLoginResponse`/`AdminSummary`)
- `src/test/kotlin/com/goldwrestling/admin/AdminSeederTest.kt` - 생성·해시·멱등성·빈값 스킵 통합테스트
- `src/test/kotlin/com/goldwrestling/auth/AdminAuthControllerTest.kt` - 로그인 성공/실패, 역할 인식(404 프로브)·역할 교차(403), 동일 실패 응답, refresh 연동
- `src/main/resources/application.yml` - `goldwrestling.admin-seed` 키 추가
- `docs/api/openapi.yaml` - 관리자 로그인 계약 반영(재생성)

## Decisions Made
- `PasswordEncoder.encode()`의 반환 타입이 jspecify `@Nullable`(플랫폼 타입)로 노출돼 있어(Spring Security 7.1 바이트코드에서 실제 확인) Kotlin 컴파일러가 non-null 대입을 거부함 — `checkNotNull`로 명시적으로 처리하고 그 이유를 KDoc에 남김(verify-boot4-api 절차 준수, 추측 대신 클래스 파일 직접 확인)
- `AdminSeederTest`만 `@SpringBootTest(properties = [...])`로 `admin-seed` 값을 테스트 전용으로 재정의해 별도 스프링 컨텍스트를 띄움 — 이유를 클래스 KDoc에 명시(conventions §10.1 "애노테이션 조합 통일" 원칙의 의도적 예외)

## Deviations from Plan

None - 플랜에 명시된 대로 실행. 다만 로컬 검증 과정에서 아래 두 가지를 자동 수정함(Rule 3 — 태스크 완료를 막는 컴파일 오류):

**1. [Rule 3 - Blocking] `ApplicationArguments` 파라미터 nullable 불일치**
- **발견 시점:** Task 1 컴파일
- **문제:** `ApplicationRunner.run(args: ApplicationArguments)`는 non-null 파라미터인데 `run(args: ApplicationArguments?)`로 오버라이드해 컴파일 실패
- **수정:** 시그니처를 non-null로 맞추고, 테스트에서는 `DefaultApplicationArguments()`로 직접 호출
- **파일:** `AdminSeeder.kt`, `AdminSeederTest.kt`
- **커밋:** `10dc272`

**2. [Rule 3 - Blocking] `PasswordEncoder.encode()` 반환 타입 nullable**
- **발견 시점:** Task 1 컴파일
- **문제:** 위 "Decisions Made" 참고
- **수정:** `checkNotNull`로 처리
- **파일:** `AdminSeeder.kt`
- **커밋:** `10dc272`

---

**Total deviations:** 2 auto-fixed (모두 Rule 3 — 컴파일 차단 이슈)
**Impact on plan:** 둘 다 플랜 범위를 벗어나지 않는 컴파일러 수준 수정. 스코프 확장 없음.

## Issues Encountered

- `docker compose up -d`가 `gold-wrestling-postgres` 컨테이너명 충돌로 실패했다 — 다른 워크트리/세션이 이미 같은 이름의 컨테이너를 3일째 띄워 둔 상태였다. 그 컨테이너가 이미 5432에서 정상 응답 중이라 새로 만들지 않고 그대로 재사용했다.
- 이 워크트리에는 `.env`가 없어(각 워크트리는 파일시스템을 공유하지 않음) `generateApiDocs`가 처음엔 DB 인증(SCRAM 비밀번호 없음)과 `JWT_ACCESS_TOKEN_EXPIRY_MINUTES` 등 빈 값 바인딩 실패로 두 번 막혔다. 메인 레포 루트의 로컬 `.env`(git 추적 대상 아님)를 이 워크트리로 복사한 뒤, 원본에 비어 있던 JWT 관련 세 키만 이 워크트리 전용 로컬 값으로 채워 `generateApiDocs`를 통과시켰다. 재생성 완료 후 이 워크트리의 `.env` 사본은 삭제했다(커밋되지 않음, 원본 `.env`는 건드리지 않음).

## User Setup Required

None - 관리자 시드는 이미 `.env.example`에 `ADMIN_SEED_LOGIN_ID`/`ADMIN_SEED_PASSWORD`/`ADMIN_SEED_NAME` 키가 준비되어 있었다(02-03에서 추가). 로컬에서 관리자 로그인을 실제로 확인하려면 사용자가 본인 `.env`의 이 세 값을 채우고 앱을 재기동하면 된다.

## Next Phase Readiness
- 관리자 인증(AUTH-03)이 완료되어 02-09·02-10(관리자 기능)이 `ROLE_ADMIN` 토큰을 전제로 진행할 수 있다
- `docs/api/openapi.yaml`이 최신 — FE가 관리자 로그인 타입을 생성할 수 있다
- 관리자 회원 목록·승인 API(02-08 이후) 작업 시 `AdminAuthService`/`AdminSeeder` 패턴(단일 트랜잭션 조회 + 토큰 발급 위임)을 그대로 재사용 가능

## Known Stubs

None.

## Threat Flags

None - 이 플랜의 모든 신규 표면(`/api/auth/admin/login`)은 플랜의 `<threat_model>`에 이미 T-02-24~T-02-28로 등록되어 있고, 각 대응이 코드에 반영됨(계정 열거 방지, bcrypt 해싱, 평문 로그 미노출, 시크릿 커밋 금지, 브루트포스는 v1 스코프 밖으로 명시적 accept).

## Self-Check: PASSED

- 9개 신규 파일 전부 `FOUND`
- 3개 태스크 커밋(`10dc272`, `9e7e522`, `fe12559`) 전부 `FOUND`

---
*Phase: 02-auth-member*
*Completed: 2026-08-03*
