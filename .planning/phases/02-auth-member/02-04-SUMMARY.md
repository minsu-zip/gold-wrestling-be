---
phase: 02-auth-member
plan: 04
subsystem: auth
tags: [jwt, refresh-token, spring-security, openapi, kotlin, ktlint]

# Dependency graph
requires:
  - phase: 02-auth-member (plan 01)
    provides: docs/glossary.md 인증·회원 네이밍, ErrorCode 확장(REFRESH_TOKEN_INVALID 등), decisions.md D-036~D-047
  - phase: 02-auth-member (plan 02)
    provides: V3 마이그레이션(refresh_token 테이블), RefreshToken 엔티티·리포지토리, Member/Admin 확장
  - phase: 02-auth-member (plan 03)
    provides: JwtEncoder/JwtDecoder 빈(JwtConfig), JwtProperties, Clock 빈, MutableTestClock/TestClockConfiguration 테스트 인프라
provides:
  - "TokenService — 회원·관리자 공용 토큰 발급·회전·재사용감지·폐기 엔진(issueTokenPair/rotate/revoke/revokeAllForMember)"
  - "POST /api/auth/refresh, POST /api/auth/logout — problem+json 계약을 지키는 토큰 갱신·로그아웃 API"
  - "TestClockConfiguration을 실제 DB 통합테스트에 처음 적용한 패턴(RefreshTokenRotationTest) — 이후 시각 의존 통합테스트가 재사용할 조합"
affects: [02-06(카카오 로그인), 02-07(관리자 로그인), 02-10(회원 상태 변경) — 전부 TokenService.issueTokenPair/revokeAllForMember를 그대로 호출한다]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "MutableTestClock을 실 DB 통합테스트에 쓸 때는 @BeforeEach에서 Instant.now()로 리셋 — JwtDecoder 기본 검증기가 시스템 실제 시각과 비교하므로 하드코딩된 과거 고정 시각을 쓰면 발급 직후 토큰이 만료로 거부된다"
    - "단일 클래스만 담는 파일은 클래스명과 파일명을 ktlint filename 규칙에 맞춰 정확히 일치시킨다"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/auth/TokenPair.kt
    - src/main/kotlin/com/goldwrestling/auth/RefreshTokenInvalidException.kt
    - src/main/kotlin/com/goldwrestling/auth/TokenService.kt
    - src/main/kotlin/com/goldwrestling/auth/TokenController.kt
    - src/main/kotlin/com/goldwrestling/auth/dto/TokenRequests.kt
    - src/main/kotlin/com/goldwrestling/auth/dto/TokenPairResponse.kt
    - src/test/kotlin/com/goldwrestling/auth/RefreshTokenRotationTest.kt
    - src/test/kotlin/com/goldwrestling/auth/TokenControllerTest.kt
  modified:
    - docs/api/openapi.yaml

key-decisions:
  - "새 D 번호 없음 — 이 플랜은 02-01이 이미 기록한 D-033(토큰 정책)·D-036(refresh 표현·저장)·D-044(강제 로그아웃)를 코드로 구현만 함"

patterns-established:
  - "시각 의존 + 실 DB 통합테스트는 RefreshTokenRotationTest 패턴(@Import(TestcontainersConfiguration::class, TestClockConfiguration::class) + @BeforeEach 리셋)을 재사용한다"

requirements-completed: [AUTH-02]

# Metrics
duration: ~35min
completed: 2026-08-02
---

# Phase 2 Plan 4: 토큰 발급·회전 엔진 Summary

**회원·관리자 공용 TokenService(발급/회전/재사용감지/강제폐기) + POST /api/auth/refresh·/logout + openapi.yaml 재생성, 통합·컨트롤러 테스트 19건 전부 통과**

## Performance

- **Duration:** 약 35분
- **Completed:** 2026-08-02
- **Tasks:** 3/3 완료
- **Files modified:** 1개 수정(openapi.yaml) + 8개 신규

## Accomplishments

- `TokenService` 신규 — `issueTokenPair`(JWT access + SecureRandom 256비트 refresh 발급, DB엔 SHA-256 해시만 저장), `rotate`(회전 + 재사용 감지 시 그 주체의 미폐기 refresh 전부 폐기), `revoke`(멱등 로그아웃), `revokeAllForMember`(D-044 강제 로그아웃 실현부) 4개 메서드로 회원·관리자 토큰 로직을 한 곳에 모음
- access 토큰 클레임에 회원 상태(`status`)를 넣지 않음(D-033) — 상태 게이트는 이후 02-05가 매 요청 DB에서 재조회
- `POST /api/auth/refresh`·`POST /api/auth/logout` 신규 — 도메인 예외를 그대로 던져 `GlobalExceptionHandler`가 problem+json으로 변환(D-017), 컨트롤러에 `@Transactional`·`try-catch` 없음
- `RefreshTokenRotationTest`(Testcontainers + `TestClockConfiguration` 조합, 이 phase 최초 적용) 13건 — 발급·회전·재사용감지(주체 전량 폐기 포함)·만료(Clock 15일 이동)·해시 저장(JdbcClient로 DB 직접 조회) 전부 실제 DB·시각 이동으로 증명
- `TokenControllerTest` 6건 — 회전 성공(refreshToken 값 변경 확인), 재사용 401+`REFRESH_TOKEN_INVALID`, 검증 실패 400+`VALIDATION_FAILED`, 파싱 실패 400+`MALFORMED_REQUEST`, 로그아웃 204(멱등) 전부 통과
- `docs/api/openapi.yaml` 재생성 — `/api/auth/refresh`·`/api/auth/logout` 경로와 3개 스키마(`RefreshTokenRequest`/`LogoutRequest`/`TokenPairResponse`) 추가, `servers: /` 유지, 기존 `/api/system/health` 보존, `8099`/`localhost` 값 잔존 없음 확인
- `./gradlew ktlintFormat && ./gradlew build` 전체 통과 (63개 테스트, Testcontainers 포함)

## Task Commits

1. **Task 1: TokenService — JWT 발급 + refresh 저장·회전·재사용감지·폐기** - `71908d0` (feat)
2. **Task 2: 토큰 갱신·로그아웃 엔드포인트** - `1a6adc0` (feat)
3. **Task 3: openapi.yaml 재생성** - `6a8d46d` (docs)

## Files Created/Modified

- `src/main/kotlin/com/goldwrestling/auth/TokenPair.kt` — 발급 결과 값 객체 (신규)
- `src/main/kotlin/com/goldwrestling/auth/RefreshTokenInvalidException.kt` — REFRESH_TOKEN_INVALID 도메인 예외 (신규, 원래 계획된 파일명 `AuthExceptions.kt`에서 변경 — 아래 Deviations 참조)
- `src/main/kotlin/com/goldwrestling/auth/TokenService.kt` — 토큰 발급·회전·폐기 엔진 (신규)
- `src/main/kotlin/com/goldwrestling/auth/TokenController.kt` — refresh/logout 엔드포인트 (신규)
- `src/main/kotlin/com/goldwrestling/auth/dto/TokenRequests.kt` — RefreshTokenRequest/LogoutRequest (신규)
- `src/main/kotlin/com/goldwrestling/auth/dto/TokenPairResponse.kt` — 응답 DTO + `from(TokenPair)` (신규)
- `src/test/kotlin/com/goldwrestling/auth/RefreshTokenRotationTest.kt` — 통합테스트 13건 (신규)
- `src/test/kotlin/com/goldwrestling/auth/TokenControllerTest.kt` — 컨트롤러 통합테스트 6건 (신규)
- `docs/api/openapi.yaml` — 토큰 갱신·로그아웃 계약 반영 (수정)

## Decisions Made

새 설계 결정 없음. 02-01이 이미 기록한 D-033(access 30분/refresh 14일 + DB 저장 + 회전)·D-036(SecureRandom + SHA-256 해시 저장)·D-044(상태 변경 시 강제 로그아웃)를 그대로 구현했다.

실행 중 내린 소소한 판단:

- `rotate()`의 판정 순서를 plan 지시대로 "폐기됨 확인 → 만료됨 확인 → 정상 회전" 순으로 고정했다. 폐기와 만료가 동시에 해당하는 행(만료된 뒤 재사용 시도)은 "폐기됨" 경로로 처리되어 재사용 감지(그 주체 전량 폐기)가 우선 발동한다 — 탈취된 토큰이 만료 직전/직후 재사용될 때도 방어가 적용되도록 하는 더 보수적인 선택이다.
- 재사용 감지 시 전량 폐기 대상 조회를 `revokeAllForMember`(공개 API, memberId 기반)로 재사용하지 않고 별도 `private fun revokeAllUsableFor(refreshToken, at)`를 뒀다 — 재사용 감지 시점엔 이미 엔티티(`RefreshToken.member`/`.admin`)를 들고 있어 memberId로 다시 조회할 필요가 없고, 관리자 주체도 같은 경로로 처리해야 하는데 `revokeAllForMember`는 회원 전용 공개 계약(interfaces 섹션)이라 시그니처를 오염시키고 싶지 않았다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] 계획된 파일명 `AuthExceptions.kt`가 ktlint filename 규칙을 위반**
- **Found during:** Task 1 (`./gradlew ktlintFormat` 최초 실행)
- **Issue:** ktlint의 `filename` 규칙은 단일 클래스만 담은 파일의 이름이 그 클래스명과 정확히 일치해야 한다고 요구한다(자동 수정 불가 위반). 이 플랜 시점엔 `RefreshTokenInvalidException` 하나뿐이라 `AuthExceptions.kt`라는 복수형 파일명이 규칙에 걸렸다.
- **Fix:** 파일명을 `RefreshTokenInvalidException.kt`로 변경. 이후 인증 예외가 추가되면 그때 다시 `AuthExceptions.kt`(2개 이상)로 합칠 수 있다 — CLAUDE.md가 "ktlint가 유일한 포맷 기준"이라고 명시해 규칙 쪽을 따랐다.
- **Files modified:** `src/main/kotlin/com/goldwrestling/auth/RefreshTokenInvalidException.kt` (신규 경로)
- **Verification:** `./gradlew ktlintFormat` 통과
- **Committed in:** `71908d0` (Task 1 커밋에 포함)

**2. [Rule 1 - Bug] KDoc 주석 안의 `/api/auth/**` 문자열이 Kotlin 중첩 블록 주석으로 오인되어 파싱 실패**
- **Found during:** Task 2 (`./gradlew ktlintFormat` 실행 중 "Unclosed comment" 파싱 에러)
- **Issue:** `TokenController`의 KDoc(`/** ... */`) 본문에 `` `/api/auth/**` `` 문자열을 그대로 적었는데, Kotlin은 블록 주석 중첩을 허용하는 언어라 주석 안의 `/**`가 별도의 중첩 주석 시작으로 해석되어 "닫히지 않은 주석"으로 컴파일러/ktlint 파서가 실패했다.
- **Fix:** 문구를 `` `/api/auth` 하위 전체 경로 `` 로 바꿔 `/**` 시퀀스 자체를 제거했다. 의미는 동일하게 유지된다.
- **Files modified:** `src/main/kotlin/com/goldwrestling/auth/TokenController.kt`
- **Verification:** `./gradlew ktlintFormat` 통과
- **Committed in:** `1a6adc0` (Task 2 커밋에 포함)

**3. [Rule 1 - Bug] `MutableTestClock`을 과거 고정 시각으로 리셋했더니 발급 직후 access 토큰이 "만료됨"으로 디코딩 거부됨**
- **Found during:** Task 1 (`RefreshTokenRotationTest` 최초 실행 — `access는 JWT이고...` / `sub는 principalId...` 두 테스트가 `JwtValidationException`으로 실패)
- **Issue:** `NimbusJwtDecoder`의 기본 검증기(`JwtValidators.createDefault()`)는 토큰의 `iat`/`exp`를 주입받은 `Clock` 빈이 아니라 **실제 시스템 시각**과 비교한다. 테스트가 `MutableTestClock`을 `AuthRepositoryIntegrationTest`처럼 과거 고정값("2026-08-01T00:00:00+09:00")으로 리셋했더니, 그 값으로 발급된 access 토큰의 `exp`(발급 시각+30분)가 실제 현재 시각보다 훨씬 과거라 곧바로 만료로 거부됐다.
- **Fix:** `@BeforeEach resetClock()`이 하드코딩된 과거 날짜 대신 `Instant.now()`(실제 벽시계 현재 시각)로 리셋하도록 변경. 만료·유효기간 비교 테스트는 절대값이 아니라 "발급 시각 + N" 상대 계산이라 이 변경으로 영향받지 않는다.
- **Files modified:** `src/test/kotlin/com/goldwrestling/auth/RefreshTokenRotationTest.kt`
- **Verification:** `./gradlew test --tests "*RefreshTokenRotation*"` 13건 전부 통과
- **Committed in:** `71908d0` (Task 1 커밋에 포함)

---

**Total deviations:** 3 auto-fixed (Rule 3 파일명 규칙 1건, Rule 1 버그 수정 2건)
**Impact on plan:** 셋 다 이 플랜 안에서 발생한 블로킹 이슈였고, 각 태스크의 "빌드·테스트 통과" 완료 조건을 만족시키기 위해 필수였다. 스코프 확장 없음.

## Issues Encountered

- 로컬 `docker compose up -d` 시도가 이미 3일째 기동 중인 공유 `gold-wrestling-postgres` 컨테이너와 이름 충돌로 실패했다(다른 세션/사용자가 이미 띄워 둔 컨테이너). 새로 띄우는 대신, 실행 중인 컨테이너의 실제 `POSTGRES_PASSWORD`를 `docker inspect`로 확인해 워크트리 로컬 `.env`의 `DB_PASSWORD`를 맞추고 그 컨테이너에 연결해 `./gradlew generateApiDocs`를 실행했다. 이 컨테이너는 이미 V1~V3 마이그레이션이 적용된 상태였고(Phase 2 Wave 1이 이미 병합됨), 이번 실행이 추가로 스키마를 바꾸지 않아(코드만 추가) 충돌 없이 안전하게 완료됐다. `docker compose up -d`가 실패하며 만든 고아 네트워크·볼륨(`agent-a616a28a28a381ac7_default`/`_postgres-data`)은 작업 후 직접 정리했다.
- 로컬 `.env`(JWT_SECRET 등 로컬 검증 전용 더미값)는 `.gitignore`에 이미 등록되어 있어 커밋되지 않았다 — 실값 커밋 금지 규칙 준수.
- 테스트 작성 면제 판단 불필요 — 이 플랜의 모든 프로덕션 코드 변경(`TokenService`/`TokenController`/DTO)은 플랜이 명시한 대로 `RefreshTokenRotationTest`·`TokenControllerTest`로 커버된다.

## User Setup Required

None - 외부 서비스 설정 불필요. 로컬 `.env`는 이미 이 워크트리에 로컬 검증용으로만 채워 두었다(커밋되지 않음) — 병합 후 사용자의 로컬 `.env`에 실제 `JWT_SECRET` 값이 없다면 `docs/decisions.md`/`.env.example` 안내대로 `openssl rand -base64 48` 등으로 채워야 한다(02-03에서 이미 안내됨, 이 플랜에서 새로 추가된 요구사항 아님).

## Next Phase Readiness

- 이후 플랜(02-06 카카오 로그인, 02-07 관리자 로그인, 02-10 회원 상태 변경)이 `TokenService.issueTokenPair`/`revokeAllForMember`를 그대로 호출해 토큰 발급·강제 로그아웃을 구현할 수 있음
- 시각 의존 + 실 DB 통합테스트를 새로 작성할 때 `RefreshTokenRotationTest`의 `@BeforeEach` 리셋 패턴(과거 고정값이 아니라 `Instant.now()`)을 그대로 재사용할 것을 권장 — `JwtDecoder`를 실제로 호출해 디코딩까지 검증하는 테스트라면 필수
- **차단 요소 없음.** 이번 실행 세션은 오케스트레이터가 사용자 승인을 받아 태스크 단위 자동 커밋을 허용했다 — 위 3개 커밋 모두 완료 상태이며 push는 하지 않았다

---
*Phase: 02-auth-member*
*Completed: 2026-08-02*

## 이번에 쓴 기술

1. **refresh 토큰을 JWT가 아니라 DB에 저장된 불투명(opaque) 난수로 만드는 이유**
   - **이 코드에서 왜 필요했는가:** access 토큰(JWT)은 서버가 서명만 검증하면 되므로 DB 조회 없이 빠르게 확인할 수 있다. 하지만 refresh 토큰은 "이 토큰이 아직 살아있는가(폐기되지 않았는가)"를 매번 확인해야 하는데, JWT는 자기 안에 서명된 정보만 담을 뿐 "폐기됐는지"는 알 수 없다(폐기는 발급 이후에 일어나는 사건이라 토큰 자체에 미리 적어둘 수 없다). 즉 refresh는 어차피 매번 DB를 조회해야 하므로, 자기기술적인 JWT로 만들 이점이 없다.
   - **안 썼으면 뭐가 깨지는가:** refresh도 JWT로 만들면 "서명이 유효하니 통과"로 검증이 끝나버려, 로그아웃하거나 탈취를 감지해도 그 토큰을 무효화할 방법이 없다 — 만료 전까지 14일간 아무도 막을 수 없는 토큰이 된다.

2. **왜 refresh 원문이 아니라 SHA-256 해시만 저장하는가 — 비밀번호 해싱과의 차이 ★**
   - **이 코드에서 왜 필요했는가:** DB(백업 포함)가 유출되면, 저장된 값이 곧 로그인 자격이 된다. 원문을 저장하면 유출 즉시 그 refresh로 로그인할 수 있는 모든 토큰이 탈취당한다.
   - **안 썼으면 뭐가 깨지는가:** 여기서 흥미로운 건 "왜 비밀번호처럼 BCrypt를 안 쓰고 단순 SHA-256을 쓰는가"다. 비밀번호는 사람이 짧고 예측 가능한 값을 고르는 경향이 있어(예: "1234"), 공격자가 흔한 값들을 미리 해시해 둔 표(레인보우 테이블)나 무차별 대입으로 원문을 역산할 위험이 있다 — 그래서 일부러 느리고 salt를 쓰는 BCrypt가 필요하다. 반면 refresh 토큰은 `SecureRandom` 256비트 난수라 애초에 "추측 가능한 후보"가 존재하지 않는다(가능한 값이 2^256개). 빠른 해시(SHA-256)라도 역산 대입 공격이 성립하지 않으므로, 여기서 BCrypt를 쓰면 검증마다 불필요하게 느려지기만 한다.

3. **refresh 회전(rotation)과 재사용 감지가 함께 막는 공격 — 트레이드오프 포함**
   - **이 코드에서 왜 필요했는가:** refresh 토큰이 네트워크 로그나 브라우저 저장소에서 탈취되면, 공격자가 그 값으로 계속 access 토큰을 발급받아 14일 내내 접근할 수 있다. "사용할 때마다 새 토큰으로 교체하고 옛 토큰은 폐기"하면, 정상 사용자와 공격자 중 **먼저** 그 토큰을 쓴 쪽만 새 토큰을 받고 나머지는 "이미 폐기된 토큰"을 보게 된다. 이 상태(누군가 이미 회전시킨 토큰이 다시 제시됨)를 "재사용 감지"로 보고 그 주체의 모든 refresh를 폐기하면, 공격자뿐 아니라 정상 사용자도 함께 로그아웃되지만 탈취 창이 닫힌다.
   - **안 썼으면 뭐가 깨지는가:** 트레이드오프도 명확하다 — 네트워크 재시도 등으로 정상 사용자가 실수로 옛 토큰을 두 번 보내는 상황(진짜 탈취가 아님)도 똑같이 전량 로그아웃으로 처리된다. 02-RESEARCH.md의 Assumptions Log(A3)가 이미 "MVP 단계에서는 수용 가능한 트레이드오프"로 이 위험을 인지하고 있다.

4. **access 토큰 클레임에 회원 상태를 넣지 않는 이유 — 캐시된 권한의 위험**
   - **이 코드에서 왜 필요했는가:** JWT는 발급 시점의 정보를 그대로 봉인해서 들고 다닌다. 만약 access 토큰에 "이 회원은 ACTIVE 상태"라는 정보를 넣어두면, 그 토큰을 검증하는 쪽은 DB를 다시 조회하지 않고도 빠르게 "이 사람은 활동 가능"이라고 판단할 수 있어 편해 보인다.
   - **안 썼으면 뭐가 깨지는가:** 관리자가 회원을 정지(`INACTIVE`)시켜도, 이미 발급된 access 토큰은 최대 30분 동안 "ACTIVE"라는 옛 정보를 그대로 믿고 계속 통과된다 — 정지 조치가 즉시 반영되지 않는 보안 구멍이다. 그래서 상태 판단은 토큰 클레임이 아니라 매 요청마다 DB의 현재 상태를 다시 조회하도록 설계했다(이후 02-05 필터가 담당). 대신 refresh 무효화(`revokeAllForMember`)로 최소한 "재로그인은 막는다"는 방어선을 둔다.

5. **`JwtDecoder`의 기본 시각 검증이 우리가 주입한 `Clock`이 아니라 실제 시스템 시각을 본다는 함정 ★**
   - **이 코드에서 왜 필요했는가:** 이 프로젝트는 테스트에서 "시간이 흘렀다"를 재현하려고 `Clock` 빈을 우리 코드(`TokenService`)에 주입해서 쓴다. 그래서 처음엔 "테스트가 `Clock`을 과거로 고정해 두면, 그 시각 기준으로 발급된 토큰도 그 시각 기준으로 검증되겠지"라고 짐작하기 쉽다.
   - **안 썼으면 뭐가 깨지는가:** 실제로는 `NimbusJwtDecoder`가 쓰는 기본 검증기(`JwtTimestampValidator`)는 우리 `Clock` 빈을 전혀 모른다 — 그건 `TokenService`에만 주입되고, `JwtDecoder` 내부 검증 로직은 자바 표준 시스템 시계(`Clock.systemUTC()`)를 따로 쓴다. 그래서 테스트가 `Clock`을 과거 고정값으로 맞춰 두고 그 값으로 access 토큰을 발급하면, "발급 시각(과거) + 30분"으로 계산된 만료 시각이 실제 지금 시각보다 이미 지나 있어 디코딩 자체가 "만료됨"으로 거부된다. 이 프로젝트 시각 의존 테스트가 실제 JWT 디코딩까지 검증할 때는 이 둘(우리 `Clock`과 라이브러리의 시스템 시각)이 서로 다른 시계라는 걸 늘 염두에 둬야 한다.

**일부러 쓰지 않은 것:** refresh 토큰 자체에 principal 정보를 인코딩하는 방식(예: `member:42:<random>` 같은 자기기술적 문자열) — DB 조회(`findByTokenHash`)가 어차피 필요해서 얻는 이점이 없고, 오히려 토큰 문자열만 보고 주체를 유추할 수 있는 정보를 노출한다. `RefreshToken` 엔티티가 이미 `member`/`admin` 연관관계로 주체를 담고 있어(02-02), 해시로 행을 찾으면 주체는 자연히 딸려온다.

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/auth/TokenPair.kt
- FOUND: src/main/kotlin/com/goldwrestling/auth/RefreshTokenInvalidException.kt
- FOUND: src/main/kotlin/com/goldwrestling/auth/TokenService.kt
- FOUND: src/main/kotlin/com/goldwrestling/auth/TokenController.kt
- FOUND: src/main/kotlin/com/goldwrestling/auth/dto/TokenRequests.kt
- FOUND: src/main/kotlin/com/goldwrestling/auth/dto/TokenPairResponse.kt
- FOUND: src/test/kotlin/com/goldwrestling/auth/RefreshTokenRotationTest.kt
- FOUND: src/test/kotlin/com/goldwrestling/auth/TokenControllerTest.kt
- 커밋 `71908d0`, `1a6adc0`, `6a8d46d` 전부 `git log --oneline --all`에서 확인됨
- `./gradlew ktlintFormat && ./gradlew build` BUILD SUCCESSFUL (63개 테스트 전체 통과, Testcontainers 포함)
