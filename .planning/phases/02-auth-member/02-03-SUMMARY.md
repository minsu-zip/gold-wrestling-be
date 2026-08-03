---
phase: 02-auth-member
plan: 03
subsystem: auth
tags: [jwt, spring-security, kakao-oauth, clock, mockrestserviceserver, ktlint]

# Dependency graph
requires:
  - phase: 02-auth-member (plan 01)
    provides: docs/glossary.md 인증·회원 네이밍, ErrorCode 확장, decisions.md D-036~D-047
  - phase: 02-auth-member (plan 02)
    provides: V3 마이그레이션(kakao_id/login_id/refresh_token), Member/Admin 확장, RefreshToken 엔티티·리포지토리
provides:
  - "JwtProperties/KakaoProperties(@ConfigurationProperties) + application.yml/.env.example 배선"
  - "JwtEncoder/JwtDecoder 빈(HS256 대칭키, 시크릿 32바이트 미만이면 기동 실패)"
  - "Clock 빈(Asia/Seoul) — ClockConfig"
  - "카카오 API 호출용 RestClient 빈(연결 3초/읽기 5초 타임아웃, baseUrl 미설정)"
  - "MutableTestClock/TestClockConfiguration — 시각 의존 테스트 공용 인프라"
  - "KakaoApiMockSupport + kakao/*.json 픽스처 — MockRestServiceServer 기반 카카오 API 목킹 헬퍼"
  - "JwtConfigTest — 발급→검증 왕복 + 만료·다른 시크릿·alg none 3가지 실패 경로 회귀 테스트"
affects: [02-auth-member 이후 플랜 전체(02-04~02-11) — 카카오 로그인·JWT 발급/검증·온보딩·회원 관리 통합테스트가 이 배선과 테스트 인프라 위에서 동작]

# Tech tracking
tech-stack:
  added:
    - "org.springframework.security:spring-security-oauth2-jose (7.1.0, Boot 4.1.0 BOM 관리, 버전 미기입)"
  patterns:
    - "테스트 전용 Clock 교체는 같은 이름 @Bean 재정의가 아니라 다른 이름 + @Primary (D-049)"
    - "Boot의 RestClient.Builder 자동 구성 모듈이 없는 클래스패스에서는 RestClient.builder() 직접 호출 (D-048)"
    - "카카오 API 목킹은 KakaoApiMockSupport + src/test/resources/kakao/*.json 픽스처로 공용화"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/config/JwtProperties.kt
    - src/main/kotlin/com/goldwrestling/config/KakaoProperties.kt
    - src/main/kotlin/com/goldwrestling/config/JwtConfig.kt
    - src/main/kotlin/com/goldwrestling/config/ClockConfig.kt
    - src/main/kotlin/com/goldwrestling/config/KakaoRestClientConfig.kt
    - src/test/kotlin/com/goldwrestling/support/MutableTestClock.kt
    - src/test/kotlin/com/goldwrestling/support/TestClockConfiguration.kt
    - src/test/kotlin/com/goldwrestling/support/KakaoApiMockSupport.kt
    - src/test/resources/kakao/token-response.json
    - src/test/resources/kakao/user-response.json
    - src/test/kotlin/com/goldwrestling/support/MutableTestClockTest.kt
    - src/test/kotlin/com/goldwrestling/config/JwtConfigTest.kt
  modified:
    - build.gradle.kts
    - src/main/resources/application.yml
    - .env.example
    - docs/decisions.md

key-decisions:
  - "D-048: RestClient.Builder 빈을 주입받지 않고 RestClient.builder()를 직접 호출 — spring-boot-restclient 모듈이 classpath에 없어 주입 시 NoSuchBeanDefinitionException으로 앱 기동이 실패함을 실행해 확인"
  - "D-049: 테스트 Clock 교체는 같은 이름 @Bean 재정의가 아니라 다른 이름(testClock) + @Primary — 같은 이름 재정의는 BeanDefinitionOverrideException으로 실패함을 실행해 확인"

patterns-established:
  - "시각 의존 통합테스트는 @Import(TestcontainersConfiguration::class, TestClockConfiguration::class) 조합을 고정해 컨텍스트 캐시가 늘어나지 않게 한다"
  - "테스트 전역 JWT_SECRET 더미값은 build.gradle.kts의 tasks.withType<Test> system property로 주입 — 전체 스위트가 JwtConfig 시크릿 검증에 걸리지 않게 한다"

requirements-completed: [AUTH-01, AUTH-02]

# Metrics
duration: ~25min
completed: 2026-08-02
---

# Phase 2 Plan 3: 인증 배선·테스트 인프라 Summary

**JWT(HS256) 발급·검증 빈 + Clock 빈 + 카카오 RestClient 빈 신규 배선, MutableTestClock·카카오 API 목킹 헬퍼·픽스처로 Wave 0 테스트 인프라 4건 완료, JWT 왕복·만료·다른 시크릿·alg none 회귀테스트 통과**

## Performance

- **Duration:** 약 25분
- **Completed:** 2026-08-02
- **Tasks:** 3/3 완료
- **Files modified:** 4개 수정(docs/decisions.md 포함) + 12개 신규

## Accomplishments

- `JwtProperties`/`KakaoProperties`(`@ConfigurationProperties`) 신규 + `application.yml`에 `goldwrestling.jwt`/`kakao`/`default-branch-name` 블록, `.env.example`에 관리자 시드(D-038)·기본 지점(D-047) 키 추가
- 신규 Gradle 의존성 1건(`spring-security-oauth2-jose`)이 Boot 4.1.0 BOM(`spring-security-bom:7.1.0`) 관리 버전 `7.1.0`으로 실제 해석됨을 확인
- `JwtConfig`: `NimbusJwtEncoder`/`NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)` 빈, 시크릿 32바이트 미만이면 빈 생성 시점에 `IllegalStateException`(값 미노출)
- `ClockConfig`(Asia/Seoul), `KakaoRestClientConfig`(연결 3초/읽기 5초 타임아웃, baseUrl 미설정) 신규
- `MutableTestClock`/`TestClockConfiguration`/`KakaoApiMockSupport` + 카카오 응답 픽스처 2종 — VALIDATION.md Wave 0 요구사항 4건 전부 충족
- `JwtConfigTest`(발급→검증 왕복 + 만료·다른 시크릿·alg none 3가지 실패 경로)와 `MutableTestClockTest`(3건) 전부 통과 — `./gradlew ktlintFormat && ./gradlew build` 전체 43개 테스트 통과

## Task Commits

1. **Task 1: 의존성 1건 추가 + 설정 프로퍼티·application.yml·.env.example** - `54acc8c` (feat)
2. **Task 2: JwtEncoder/JwtDecoder · Clock · 카카오 RestClient 빈** - `be943b1` (feat)
3. **Task 3: 테스트 인프라 + JWT 왕복 검증** - `8a89e4c` (test, Task 2 버그 수정 2건 포함)
4. **결정 기록** - `7be1c99` (docs: D-048~D-049)

## Files Created/Modified

- `build.gradle.kts` — `spring-security-oauth2-jose` 의존성 1건, 테스트 전역 `goldwrestling.jwt.secret` 더미 system property 추가
- `src/main/kotlin/com/goldwrestling/config/JwtProperties.kt` — JWT 설정 프로퍼티 (신규)
- `src/main/kotlin/com/goldwrestling/config/KakaoProperties.kt` — 카카오 설정 프로퍼티 (신규)
- `src/main/kotlin/com/goldwrestling/config/JwtConfig.kt` — JwtEncoder/JwtDecoder 빈 + 시크릿 길이 검증 (신규)
- `src/main/kotlin/com/goldwrestling/config/ClockConfig.kt` — Asia/Seoul Clock 빈 (신규)
- `src/main/kotlin/com/goldwrestling/config/KakaoRestClientConfig.kt` — 카카오 RestClient 빈 (신규)
- `src/main/resources/application.yml` — `goldwrestling.jwt`/`kakao`/`default-branch-name` 블록 추가
- `.env.example` — 관리자 시드·기본 지점 키 추가, JWT_SECRET 주석 보강
- `src/test/kotlin/com/goldwrestling/support/MutableTestClock.kt` — 시각 조작 가능한 테스트 Clock (신규)
- `src/test/kotlin/com/goldwrestling/support/TestClockConfiguration.kt` — 테스트 Clock 빈 구성 (신규)
- `src/test/kotlin/com/goldwrestling/support/KakaoApiMockSupport.kt` — 카카오 API 목킹 헬퍼 (신규)
- `src/test/resources/kakao/token-response.json`, `user-response.json` — 카카오 API 응답 픽스처 (신규)
- `src/test/kotlin/com/goldwrestling/support/MutableTestClockTest.kt` — MutableTestClock 단위테스트 3건 (신규)
- `src/test/kotlin/com/goldwrestling/config/JwtConfigTest.kt` — JWT 발급·검증 통합테스트 4건 (신규)
- `docs/decisions.md` — D-048(RestClient.builder() 직접 호출), D-049(테스트 Clock @Primary 교체) 기록

## Decisions Made

`docs/decisions.md`에 D-048·D-049로 기록(둘 다 verify-boot4-api 절차로 실행 확인한 결과에 근거한 구현 결정):

- **D-048**: `KakaoRestClientConfig`가 `RestClient.Builder`를 빈으로 주입받지 않고 `RestClient.builder()`를 직접 호출한다. Boot 4.1의 `HttpClientSettings`/`ClientHttpRequestFactoryBuilder`(spring-boot-http-client 모듈)가 이 프로젝트 classpath에 없어(`./gradlew dependencies --configuration compileClasspath`로 확인, `spring-boot-starter-webmvc`가 이 모듈을 끌어오지 않음), 주입 시 `NoSuchBeanDefinitionException`으로 앱 기동이 실패했다.
- **D-049**: `TestClockConfiguration`이 프로덕션 `Clock` 빈을 대체하는 방식은 같은 이름(`clock`) `@Bean` 재정의가 아니라 다른 이름(`testClock`) + `@Primary`다. 계획 초안대로 이름을 맞췄더니 `BeanDefinitionOverrideException`으로 컨텍스트 로딩이 실패함을 스크래치 테스트로 재현·확인했다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `KakaoRestClientConfig`가 없는 `RestClient.Builder` 빈을 주입받아 앱 기동 자체가 실패**
- **Found during:** Task 3 (테스트 인프라 작성 중 전체 컨텍스트를 띄우는 스크래치 테스트로 발견)
- **Issue:** Task 2에서 작성한 `kakaoRestClient(builder: RestClient.Builder)`가 스프링 자동 구성 빈을 전제했으나, 이 프로젝트 classpath에는 `RestClient.Builder`를 자동 구성하는 모듈이 없어 `@SpringBootTest`를 쓰는 **모든** 테스트(신규 여부 무관)가 컨텍스트 로딩에서 실패했다.
- **Fix:** `RestClient.builder()`를 직접 호출하도록 변경(D-048). `SimpleClientHttpRequestFactory`의 타임아웃 설정은 그대로 유지.
- **Files modified:** `src/main/kotlin/com/goldwrestling/config/KakaoRestClientConfig.kt`
- **Verification:** `./gradlew build` 전체 43개 테스트 통과
- **Committed in:** `8a89e4c` (Task 3 커밋에 포함)

**2. [Rule 1 - Bug] `JwtConfig`의 시크릿 검증이 기존 전체 테스트 스위트의 컨텍스트 로딩을 깨뜨림**
- **Found during:** Task 3 (`./gradlew build` 전체 실행 중 발견 — HealthControllerTest·GlobalExceptionHandlerTest·AuthRepositoryIntegrationTest·FlywayMigrationIntegrationTest·GoldWrestlingApplicationTests 등 JWT와 무관한 기존 테스트 28건이 함께 실패)
- **Issue:** Task 2에서 추가한 시크릿 32바이트 미만 시 기동 실패 검증이 의도대로 동작했지만, 테스트 환경 어디에도 `goldwrestling.jwt.secret` 기본값이 없어 전체 스프링 컨텍스트를 띄우는 기존 테스트가 모두 함께 깨졌다.
- **Fix:** `src/test/resources/application.yml`을 새로 만드는 대신(클래스패스 리소스 검색 순서상 메인 `application.yml`을 완전히 가릴 위험이 있어 배제) `build.gradle.kts`의 `tasks.withType<Test>`에 전역 시스템 프로퍼티로 더미 시크릿을 주입했다. System property는 리소스 검색에 영향을 주지 않아 안전하다.
- **Files modified:** `build.gradle.kts`
- **Verification:** `./gradlew build` 전체 43개 테스트 통과(이전 실패 28건 포함 전부 복구)
- **Committed in:** `8a89e4c` (Task 3 커밋에 포함)

---

**Total deviations:** 2 auto-fixed (Rule 1 버그 수정 2건)
**Impact on plan:** 둘 다 이번 플랜(Task 2)이 만든 코드가 원인인 블로킹 버그였고, Task 3의 "`./gradlew build` 통과" 완료 조건을 만족시키기 위해 필수였다. 스코프 확장 없음.

## Issues Encountered

- **계획 초안과 다르게 구현한 지점 2건**(위 Decisions Made 참조): `KakaoRestClientConfig`의 `RestClient.Builder` 주입 방식, `TestClockConfiguration`의 빈 이름 매칭 방식. 둘 다 verify-boot4-api 절차(실제 classpath jar 확인·javap·스크래치 테스트 실행)로 계획의 가정이 이 프로젝트 classpath에서 성립하지 않음을 확인한 뒤 수정했다.
- 플랜 `<verification>` 4번("`docker compose up -d && ./gradlew bootRun`으로 JWT_SECRET 비움→기동 실패, 채움→기동 확인")은 실행하지 않았다 — 02-02 SUMMARY와 동일한 이유로, 이 실행이 병렬 워크트리 세션 중이라 공유 로컬 Postgres 컨테이너·포트에 영향을 줄 위험이 있어 의도적으로 건너뛰었다. 대신 "시크릿 미주입 시 기동 실패"는 이미 `./gradlew build` 과정에서 **간접 증명**됐다 — system property로 더미 시크릿을 넣기 전, 정확히 이 원인(`JwtConfig.kt:28`의 `IllegalStateException`)으로 `GoldWrestlingApplicationTests`를 포함한 전체 컨텍스트 테스트가 실패하는 것을 실제로 관찰했다(위 Deviations 2번). 사용자가 워크트리 병합 후 로컬에서 `docker compose up -d && ./gradlew bootRun`으로 최종 확인하는 것을 권장한다.
- 테스트 작성 면제 판단 불필요 — 이 플랜의 모든 프로덕션 코드 변경(`JwtConfig`/`ClockConfig`/`KakaoRestClientConfig`)은 플랜이 명시한 대로 `JwtConfigTest`로 커버되거나(JwtConfig), 단순 빈 정의라 config 클래스 면제 대상(conventions §10.0)에 해당한다(ClockConfig/KakaoRestClientConfig — 후속 플랜의 통합테스트가 이 빈들을 실제로 행사할 예정).

## User Setup Required

**외부 서비스 설정 불필요.** 단, 이 플랜은 `user_setup` 항목(JWT_SECRET, KAKAO_REST_API_KEY 등)을 `.env`에 채워야 실제 카카오 로그인·JWT 발급이 동작한다 — 로컬 `.env`에 아래 값을 채울 것을 권장한다(로컬 개발 계속 진행 시 02-04 이전에 필요):
- `JWT_SECRET`: `openssl rand -base64 48` 등으로 생성한 32바이트 이상 랜덤 문자열
- `KAKAO_REST_API_KEY`/`KAKAO_CLIENT_SECRET`/`KAKAO_REDIRECT_URI`: 카카오 개발자 콘솔에서 발급(카카오 로그인 실제 연동을 시험할 때 필요 — 단위/통합테스트는 `MockRestServiceServer`로 대체 가능하므로 당장 필수는 아님)

## Next Phase Readiness

- 이후 플랜(02-04~02-11)이 카카오 로그인·JWT 발급/검증·관리자 로그인·온보딩·승인 API를 작성할 때 쓸 배선(`JwtConfig`/`ClockConfig`/`KakaoRestClientConfig`)과 테스트 인프라(`MutableTestClock`/`TestClockConfiguration`/`KakaoApiMockSupport`+픽스처)가 전부 준비됨
- 시각 의존 통합테스트는 `@Import(TestcontainersConfiguration::class, TestClockConfiguration::class)` 조합을 그대로 재사용하면 된다(컨텍스트 캐시 증가 없음)
- 카카오 연동 테스트는 `KakaoApiMockSupport.bindKakaoMock(RestClient.builder())`로 시작하면 된다 — `KakaoRestClientConfig`가 빈 주입 없이 `builder()`를 직접 쓰므로, 프로덕션 `kakaoRestClient` 빈을 테스트에서 교체할 때도 같은 방식(다른 이름 + `@Primary`, D-049 패턴)을 따르는 것을 권장
- **차단 요소 없음.** 이번 실행 세션은 오케스트레이터가 사용자 승인을 받아 태스크 단위 자동 커밋을 허용했다 — 위 4개 커밋 모두 완료 상태이며 push는 하지 않았다

## 이번에 쓴 기술

1. **대칭키 JWT 서명(HMAC-SHA256/HS256)과 알고리즘 confusion 공격 방어 ★**
   - **이 코드에서 왜 필요했는가:** 우리 서버가 발급한 JWT를 우리 서버가 다시 검증해야 한다(로그인한 회원/관리자가 보낸 토큰이 진짜인지). 대칭키(같은 비밀값으로 서명·검증)를 쓰면 서버 하나만 시크릿을 알면 되므로 이 프로젝트(단일 백엔드)에 딱 맞는다. 문제는 JWT 표준이 토큰 헤더에 `"alg": "HS256"`처럼 알고리즘을 **토큰을 보낸 쪽이 스스로 적어서** 보낸다는 점이다 — 검증하는 쪽이 이 값을 그대로 믿으면, 공격자가 헤더를 `"alg": "none"`(서명 없음)으로 바꿔 보내도 통과할 수 있다.
   - **안 썼으면 뭐가 깨지는가:** `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(MacAlgorithm.HS256)`으로 "이 서버는 오직 HS256만 인정한다"고 미리 고정해 두지 않으면, 공격자가 `alg: none` 토큰을 만들어 아무 서명 없이 관리자인 척 요청을 보낼 수 있다. `JwtConfigTest`의 "알고리즘을 none 으로 바꾼 토큰" 테스트가 이 방어가 실제로 동작함을 증명한다.

2. **`Clock` 빈 주입이 테스트에서 갖는 의미**
   - **이 코드에서 왜 필요했는가:** JWT의 만료 시각, refresh 토큰의 14일 유효기간, 이후 phase의 "2주 미사용 차감" 같은 규칙은 전부 "지금이 언제인가"에 의존한다. 코드에서 직접 `Instant.now()`를 부르면, 테스트에서 "14일이 지났다"는 상황을 재현하려면 실제로 14일을 기다려야 한다.
   - **안 썼으면 뭐가 깨지는가:** `Clock`을 주입받는 대신 `Instant.now()`를 직접 호출했다면, `MutableTestClock.advance(Duration.ofDays(14))`처럼 시간을 순간 이동시키는 테스트 자체가 불가능해진다. 이번 플랜에서 만든 `MutableTestClock`이 앞으로 Phase 2(refresh 만료)·Phase 5(2주 미사용 차감) 테스트가 공유하는 시간 여행 장치다.

3. **Boot BOM(Bill of Materials)이 관리하는 의존성에는 버전을 적지 않는 이유**
   - **이 코드에서 왜 필요했는가:** `spring-security-oauth2-jose`를 추가할 때 버전을 적지 않았다. Spring Boot 4.1.0은 "이 버전과 함께 쓰면 서로 호환이 검증된 라이브러리 버전 조합"을 BOM이라는 카탈로그로 미리 정해 둔다 — Spring Security는 이 BOM에서 `7.1.0`으로 지정돼 있다.
   - **안 썼으면 뭐가 깨지는가:** 버전을 직접 적으면, Boot가 다음 패치 버전으로 올라갔을 때(예: 4.1.1) BOM이 가리키는 Spring Security 버전도 함께 올라가지만 우리 코드에 박아둔 버전은 그대로 남는다. 시간이 지나면 "Boot는 최신인데 Security는 옛날 버전" 같은 검증되지 않은 조합이 조용히 생긴다.

4. **외부 API 호출에 타임아웃을 거는 이유 — 스레드 점유와 연쇄 장애**
   - **이 코드에서 왜 필요했는가:** 카카오 로그인 처리 중 우리 서버가 카카오 서버(`kauth.kakao.com`)에 HTTP 요청을 보내는데, 카카오가 응답하지 않으면(네트워크 문제, 카카오 측 장애) 기본 설정으로는 우리 요청 스레드가 응답이 올 때까지 무한정 기다린다.
   - **안 썼으면 뭐가 깨지는가:** 서버가 처리할 수 있는 동시 요청 스레드 수는 한정돼 있다. 카카오가 느려지는 순간 로그인 요청들이 스레드를 하나씩 붙잡고 놓지 않으면, 로그인과 전혀 상관없는 다른 API(회원 목록 조회 등)까지 스레드 고갈로 함께 멈춘다. 연결 3초/읽기 5초 타임아웃은 "카카오가 느리면 우리 로그인만 실패하고 나머지는 정상 동작한다"를 보장하는 최소 방어선이다.

5. **`BeanDefinitionOverrideException`과 스프링 빈 이름 충돌 ★**
   - **이 코드에서 왜 필요했는가:** 테스트에서만 `Clock`을 다른 구현(`MutableTestClock`)으로 바꿔치기하고 싶었다. 계획 초안은 "같은 이름의 `@Bean`을 하나 더 등록하면 나중 것이 이긴다"고 가정했지만, 실제로 실행해보니 스프링은 같은 이름의 빈 정의가 두 번 등록되는 것을 "실수(설정 충돌)"로 간주해 컨텍스트 시작 자체를 막았다(예외를 던짐).
   - **안 썼으면 뭐가 깨지는가:** 이 사실을 모르고 그대로 진행했다면, 이후 플랜들이 시각 의존 통합테스트를 작성하려는 순간 전부 컨텍스트 로딩 실패로 막혔을 것이다. 다른 이름(`testClock`) + `@Primary`("타입이 같은 후보가 여럿이면 이걸 우선 선택하라") 조합으로 바꿔 해결했다 — 이름 충돌 없이, 타입 기반 주입 지점이 자동으로 테스트 빈을 고른다.

**일부러 쓰지 않은 것:** Boot 4.1의 `HttpClientSettings`/`ClientHttpRequestFactoryBuilder`(카카오 RestClient 타임아웃 설정용 최신 API) — 이 프로젝트 classpath에 해당 모듈이 없어서 새 의존성을 추가해야 하는데, 이번 phase가 "신규 패키지 1건"으로 예산을 고정해 뒀고 타임아웃 설정 하나 때문에 모듈을 더 추가할 실익이 없다고 판단했다(D-048). 대신 이미 있는 `spring-web`의 `SimpleClientHttpRequestFactory`로 같은 효과를 냈다.

---
*Phase: 02-auth-member*
*Completed: 2026-08-02*

## Self-Check: PASSED

- FOUND: src/main/kotlin/com/goldwrestling/config/JwtProperties.kt
- FOUND: src/main/kotlin/com/goldwrestling/config/KakaoProperties.kt
- FOUND: src/main/kotlin/com/goldwrestling/config/JwtConfig.kt
- FOUND: src/main/kotlin/com/goldwrestling/config/ClockConfig.kt
- FOUND: src/main/kotlin/com/goldwrestling/config/KakaoRestClientConfig.kt
- FOUND: src/test/kotlin/com/goldwrestling/support/MutableTestClock.kt
- FOUND: src/test/kotlin/com/goldwrestling/support/TestClockConfiguration.kt
- FOUND: src/test/kotlin/com/goldwrestling/support/KakaoApiMockSupport.kt
- FOUND: src/test/resources/kakao/token-response.json
- FOUND: src/test/resources/kakao/user-response.json
- FOUND: src/test/kotlin/com/goldwrestling/support/MutableTestClockTest.kt
- FOUND: src/test/kotlin/com/goldwrestling/config/JwtConfigTest.kt
- 커밋 `54acc8c`, `be943b1`, `8a89e4c`, `7be1c99` 전부 `git log --oneline --all`에서 확인됨
- `./gradlew ktlintFormat && ./gradlew build` BUILD SUCCESSFUL (43개 테스트 전체 통과, Testcontainers 포함)
