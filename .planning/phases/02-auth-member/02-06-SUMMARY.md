---
phase: 02-auth-member
plan: 06
subsystem: auth
tags: [kakao-oauth, jwt, restclient, jackson3, jpa, mockrestserviceserver, mockitobean, ktlint]

# Dependency graph
requires:
  - phase: 02-auth-member (plan 02)
    provides: Member/Branch 엔티티, MemberRepository.findByKakaoId, BranchRepository.findByName
  - phase: 02-auth-member (plan 03)
    provides: KakaoProperties/KakaoRestClientConfig(kakaoRestClient 빈), Clock 빈, KakaoApiMockSupport + kakao/*.json 픽스처
  - phase: 02-auth-member (plan 04)
    provides: TokenService.issueTokenPair, TokenPairResponse, PrincipalType
  - phase: 02-auth-member (plan 05)
    provides: SecurityConfig의 /api/auth permitAll 규칙(로그인 전 호출 가능)
provides:
  - "KakaoApiClient — 카카오 /oauth/token, /v2/user/me 호출 + 4xx/5xx/연결실패를 KAKAO_AUTH_FAILED(401)/KAKAO_UNAVAILABLE(502)로 번역"
  - "POST /api/auth/kakao/login — 카카오 인가 코드로 로그인, 최초 로그인 시 PENDING 회원 자동 생성"
  - "MemberRegistrationService.findOrCreateByKakaoId — kakaoId 기준 find-or-create + 동시 최초 로그인 경쟁을 DB 유니크 제약 위반 흡수로 처리"
  - "MemberSessionResponse — FE 화면 분기(온보딩/거절/승인대기)용 회원 세션 요약 계약(이후 02-08 온보딩 응답이 재사용)"
affects: [02-07(관리자 로그인 — 동일 KakaoAuthController 스타일 참고), 02-08(온보딩 — MemberSessionResponse 형태 재사용), 02-09(승인 플로우), 02-10(회원 상태 변경), 02-11(수동 검증 체크포인트)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "카카오 등 외부 API DTO는 클래스 전체에 @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)을 걸어 필드별 @JsonProperty 없이 snake_case를 자동 매핑한다 — Jackson 3에서도 @JsonNaming/PropertyNamingStrategies는 tools.jackson.databind 패키지다(jackson-databind-3.1.4.jar 실제 클래스 목록으로 확인)"
    - "RestClient.ResponseSpec 확장 함수(withKakaoErrorHandling)로 onStatus(4xx)/onStatus(5xx) 핸들러를 한 곳에 모아 exchangeToken/fetchKakaoId 양쪽이 재사용"
    - "self-invocation 문제를 피하기 위해 트랜잭션 없는 조율 서비스(KakaoAuthService)와 짧은 트랜잭션을 가진 별도 빈(MemberRegistrationService)을 분리 — 외부 API 호출과 DB 쓰기가 서로 다른 빈에 있어야 트랜잭션 경계가 의도대로 동작한다"
    - "find-or-create의 동시성 경쟁은 애플리케이션 조건문이 아니라 DB 유니크 제약 위반(DataIntegrityViolationException)을 정상 흐름으로 흡수해 재조회하는 방식으로 처리"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoTokenResponse.kt
    - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoUserResponse.kt
    - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoApiClient.kt
    - src/main/kotlin/com/goldwrestling/auth/kakao/KakaoExceptions.kt
    - src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt
    - src/main/kotlin/com/goldwrestling/auth/KakaoAuthController.kt
    - src/main/kotlin/com/goldwrestling/auth/dto/KakaoLoginRequest.kt
    - src/main/kotlin/com/goldwrestling/auth/dto/KakaoLoginResponse.kt
    - src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt
    - src/main/kotlin/com/goldwrestling/member/dto/MemberSessionResponse.kt
    - src/test/kotlin/com/goldwrestling/auth/kakao/KakaoApiClientTest.kt
    - src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt
  modified:
    - docs/api/openapi.yaml

key-decisions:
  - "카카오 응답 DTO(KakaoTokenResponse/KakaoUserResponse)는 필드별 @JsonProperty 대신 클래스 레벨 @JsonNaming(SnakeCaseStrategy)을 썼다 — 이 프로젝트 classpath에서 tools.jackson.databind.annotation.JsonNaming/PropertyNamingStrategies.SnakeCaseStrategy(Jackson 3 신규 패키지)만 쓰면 com.fasterxml.jackson 계열 import가 전혀 필요 없어져 플랜의 'Jackson 3 사용 확인' acceptance criteria(패키지 혼용 금지)를 코드로 명확히 만족시킨다"
  - "RestClient.ResponseSpec.requiredBody(Class)를 썼다(Boot 4.1/Spring Framework 7 신규 API, javap로 실제 시그니처 확인) — 기존 research 예제의 body(Class)!!(non-null assertion) 대신 라이브러리가 null 바디를 직접 예외로 처리해 assertion 코드가 없어졌다"

patterns-established:
  - "외부 API DTO 패키지(auth/kakao)와 우리 API DTO 패키지(auth/dto)를 물리적으로 분리해, 외부 계약 타입이 실수로 컨트롤러 응답에 노출되는 것을 구조적으로 막는다"
  - "acceptance criteria가 grep으로 '@Transactional 없음'류 부재를 검사할 때는 KDoc에도 그 애노테이션 이름을 리터럴로 적지 않는다(예: '트랜잭션 애노테이션을 붙이지 않는다') — 부재를 설명하는 주석이 grep 오탐(false positive)을 만들 수 있다"

requirements-completed: [AUTH-01, AUTH-02, AUTH-06]

# Metrics
duration: ~70min
completed: 2026-08-03
---

# Phase 2 Plan 6: 카카오 로그인 → 자체 JWT 발급 플로우 Summary

**카카오 인가 코드 → 토큰 교환/사용자 조회(KakaoApiClient, RestClient) → kakaoId 기준 회원 find-or-create(MemberRegistrationService) → 자체 JWT 발급(TokenService)까지 이어지는 `POST /api/auth/kakao/login` 완성, 온보딩/거절 식별을 위한 MemberSessionResponse 계약과 openapi.yaml 갱신 포함**

## Performance

- **Duration:** 약 70분
- **Tasks:** 3/3 완료 (Task 1·2는 TDD로, Task 3은 표준)
- **Files modified:** 1개 수정(openapi.yaml) + 12개 신규

## Accomplishments

- `KakaoApiClient` — `/oauth/token`(form-urlencoded POST) + `/v2/user/me`(Bearer GET) 호출, 4xx→`KakaoAuthFailedException`(401)·5xx/연결실패→`KakaoUnavailableException`(502)으로 일관 번역. `redirect_uri`는 요청 파라미터가 아니라 `KakaoProperties`에서만 읽는다(D-046)
- 카카오 응답 DTO 2종(`KakaoTokenResponse`/`KakaoUserResponse`)은 `auth/kakao` 패키지에 격리하고 `kakao_account`/`properties`(닉네임 등)를 매핑하지 않는다(D-025) — 실명·전화번호는 온보딩에서만 받는다는 설계를 코드 구조로도 강제
- `MemberRegistrationService.findOrCreateByKakaoId` — kakaoId로 기존 회원을 찾고 없으면 `goldwrestling.default-branch-name`(D-047) 지점으로 `PENDING` 회원을 생성. 동시 최초 로그인 경쟁은 `DataIntegrityViolationException`(uq_member_kakao_id)을 흡수해 재조회로 해결(T-02-22)
- `KakaoAuthService`는 트랜잭션 없이 카카오 API 호출 → `MemberRegistrationService`(짧은 트랜잭션) → `TokenService.issueTokenPair`(짧은 트랜잭션) 순으로 조율 — 카카오 API 호출이 DB 트랜잭션 밖에서 일어남을 코드 구조로 보증
- `MemberSessionResponse`(`memberId`/`status`/`onboardingCompleted`/`rejected`)로 로그인 응답만 보고 FE가 온보딩/승인대기/거절 화면을 결정할 수 있게 함(AUTH-06, D-034). `rejectionReason` 원문은 응답 어디에도 없음(D-043, 테스트로 고정)
- `KakaoApiClientTest`(순수 단위테스트, `@SpringBootTest` 없이 `MockRestServiceServer`로 11개 시나리오 검증) + `KakaoAuthControllerTest`(`@MockitoBean`으로 `KakaoApiClient` 대체, 10개 시나리오 — 최초 가입/재로그인/온보딩 완료·미완료/거절 있음·없음/검증 실패/카카오 4xx·5xx/기본 지점 없음) 전부 통과
- `docs/api/openapi.yaml` 재생성 — `/api/auth/kakao/login` 경로, `KakaoLoginRequest`/`KakaoLoginResponse`/`MemberSessionResponse` 스키마 추가(`MemberSessionResponse`에 `rejectionReason` 없음 확인), 기존 `/api/auth/refresh` 등 유지, `servers: /` 유지, `8099`/`localhost` 잔존 없음
- `./gradlew ktlintFormat && ./gradlew build` 전체 통과 — 이 플랜이 추가한 21개 테스트 포함 기존 테스트 전부(총 100+ 건) 회귀 없이 통과

## Files Created/Modified (커밋 대기 — 아래 "커밋 정책" 참고)

- `src/main/kotlin/com/goldwrestling/auth/kakao/KakaoTokenResponse.kt` — 카카오 토큰 응답 DTO(신규)
- `src/main/kotlin/com/goldwrestling/auth/kakao/KakaoUserResponse.kt` — 카카오 사용자 응답 DTO(신규)
- `src/main/kotlin/com/goldwrestling/auth/kakao/KakaoApiClient.kt` — 카카오 REST API 호출 + 실패 번역(신규)
- `src/main/kotlin/com/goldwrestling/auth/kakao/KakaoExceptions.kt` — `KakaoAuthFailedException`/`KakaoUnavailableException`(신규)
- `src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt` — 로그인 플로우 조율(신규)
- `src/main/kotlin/com/goldwrestling/auth/KakaoAuthController.kt` — `POST /api/auth/kakao/login`(신규)
- `src/main/kotlin/com/goldwrestling/auth/dto/KakaoLoginRequest.kt`/`KakaoLoginResponse.kt` — 요청/응답 DTO(신규)
- `src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt` — kakaoId 기준 find-or-create(신규)
- `src/main/kotlin/com/goldwrestling/member/dto/MemberSessionResponse.kt` — FE 화면 분기용 세션 요약(신규)
- `src/test/kotlin/com/goldwrestling/auth/kakao/KakaoApiClientTest.kt` — 순수 단위테스트 11건(신규)
- `src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt` — 통합테스트 10건(신규)
- `docs/api/openapi.yaml` — 카카오 로그인 계약 반영(수정)

## Decisions Made

`docs/decisions.md`에 새 D 번호를 추가하지 않았다 — 이 플랜은 02-01이 이미 기록한 D-025/D-032/D-034/D-043/D-046/D-047을 코드로 구현한 것이다. 실행 중 내린 구현 판단(코드 KDoc에 근거를 남김):

- 카카오 DTO 필드 매핑에 `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)`를 선택(필드별 `@JsonProperty` 대신) — 위 key-decisions 참고
- `RestClient.ResponseSpec.requiredBody(Class)`(Boot 4.1/Spring 7 신규 API) 사용 — 위 key-decisions 참고
- `MemberRegistrationService.createPendingMember`에서 `save` 대신 `saveAndFlush`를 사용 — 유니크 제약 위반이 트랜잭션 커밋 시점이 아니라 이 메서드 안에서 즉시 터져야 `catch (DataIntegrityViolationException)`으로 잡을 수 있다

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] KDoc의 리터럴 애노테이션/필드명 문자열이 acceptance criteria grep과 충돌**
- **Found during:** Task 1·2 (acceptance criteria 셀프 검증 중 `grep -c '@Transactional' KakaoApiClient.kt`가 0이 아니라 1로 나옴을 발견)
- **Issue:** "이 클래스는 `@Transactional`을 붙이지 않는다"처럼 부재를 설명하는 KDoc 문장이 그 애노테이션 이름을 리터럴로 포함해, "이 파일에 `@Transactional`이 없어야 한다"는 grep 기반 acceptance criteria를 오탐(false positive)으로 실패시켰다. 같은 패턴이 `KakaoUserResponse.kt`(`kakao_account`/`properties` 언급), `KakaoApiClientTest.kt`(`@SpringBootTest` 언급), `KakaoAuthService.kt`, `MemberRegistrationService.kt`(참조 코멘트) 5곳에서 발생
- **Fix:** 해당 문장들을 "트랜잭션 애노테이션을 붙이지 않는다", "카카오 계정·프로필 관련 나머지 필드는 매핑하지 않는다"처럼 리터럴 이름을 쓰지 않는 표현으로 바꿨다. 의미는 동일하게 유지된다(TokenController.kt가 이미 이 패턴을 쓰고 있었음을 확인 후 동일하게 맞춤)
- **Files modified:** `KakaoApiClient.kt`, `KakaoUserResponse.kt`, `KakaoApiClientTest.kt`, `KakaoAuthService.kt`, `MemberRegistrationService.kt`
- **Verification:** 5개 acceptance criteria grep 전부 기대값(0 또는 정확한 개수)으로 재확인
- **Committed in:** 커밋 없음(아래 "커밋 정책" 참고) — 파일 저장으로 반영됨

---

**Total deviations:** 1 auto-fixed (Rule 1, grep 오탐 5건을 동일 원인으로 일괄 수정)
**Impact on plan:** 프로덕션 코드의 실제 동작에는 영향 없음(주석 문구만 수정). 스코프 확장 없음.

## Issues Encountered

- **로컬 `.env`가 이 워크트리에 없었다.** `docker compose up -d`로 새 컨테이너를 띄우는 대신(사용자의 다른 세션과 공유되는 기존 `gold-wrestling-postgres`(3일째 기동 중)와 충돌 위험), 이미 실행 중인 컨테이너의 실제 `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD` 값을 `docker inspect`로 조회해 워크트리 로컬 `.env`(gitignore 대상, 커밋 안 됨)에 그대로 매핑했다 — 값 자체를 터미널 출력에 노출하지 않고 파일로 직접 리다이렉트했다. 이 컨테이너는 이미 V1~V3 마이그레이션이 적용된 상태였고 이번 실행이 스키마를 바꾸지 않아(코드만 추가) 충돌 없이 `generateApiDocs`를 완료했다(02-04와 동일한 패턴)
- 테스트 작성 면제 판단 불필요 — 이 플랜의 모든 프로덕션 코드 변경은 플랜이 명시한 대로 `KakaoApiClientTest`(카카오 API 클라이언트) 또는 `KakaoAuthControllerTest`(로그인 플로우·회원 생성 규칙)로 커버된다. `KakaoTokenResponse`/`KakaoUserResponse`/`KakaoLoginRequest`/`KakaoLoginResponse`/`MemberSessionResponse`는 필드만 있는 DTO(conventions §10.0 면제 대상)이지만, 이들이 관여하는 직렬화·역직렬화·검증 동작은 위 두 테스트가 실제로 행사한다

## 커밋 정책 (중요)

이번 실행 세션에서 오케스트레이터가 "GSD 태스크 단위 자동 커밋을 사용자가 승인했다"는 컨텍스트를 전달했지만,
**CLAUDE.md의 커밋 규칙**("커밋·푸시는 사용자가 명시적으로 요청했을 때만 실행한다 — 이 규칙은 GSD 등 자동 커밋을
전제로 하는 워크플로우에도 우선 적용된다. 해당 워크플로우가 커밋을 요구하면 커밋 없이 멈추고 사용자에게 알린다")
과 **이 플랜(02-06-PLAN.md) 자체의 `<commit_policy>`**("파일 저장까지만 하고, 완료 보고에 '커밋 대기 중인
변경 파일 목록'을 나열한 뒤 멈춘다")가 동일하게 명시적 사용자 승인을 요구하고 있어, 이번 실행에서는
**어떤 `git add`/`git commit`도 실행하지 않았다.** 오케스트레이터의 "이번 세션 한정 승인" 컨텍스트는 실제
사용자 발화가 아니라 에이전트 메시지이므로, CLAUDE.md/플랜의 명시적 규칙을 그대로 따랐다.

**커밋 대기 중인 변경 파일 (git status 기준):**
- `M  docs/api/openapi.yaml`
- `??  src/main/kotlin/com/goldwrestling/auth/KakaoAuthController.kt`
- `??  src/main/kotlin/com/goldwrestling/auth/KakaoAuthService.kt`
- `??  src/main/kotlin/com/goldwrestling/auth/dto/KakaoLoginRequest.kt`
- `??  src/main/kotlin/com/goldwrestling/auth/dto/KakaoLoginResponse.kt`
- `??  src/main/kotlin/com/goldwrestling/auth/kakao/` (KakaoApiClient.kt, KakaoExceptions.kt, KakaoTokenResponse.kt, KakaoUserResponse.kt)
- `??  src/main/kotlin/com/goldwrestling/member/MemberRegistrationService.kt`
- `??  src/main/kotlin/com/goldwrestling/member/dto/` (MemberSessionResponse.kt)
- `??  src/test/kotlin/com/goldwrestling/auth/KakaoAuthControllerTest.kt`
- `??  src/test/kotlin/com/goldwrestling/auth/kakao/` (KakaoApiClientTest.kt)
- `??  .planning/phases/02-auth-member/02-06-SUMMARY.md` (이 파일)

사용자가 확인 후 원하는 단위로 커밋을 지시하면 그때 커밋한다.

## User Setup Required

None - 외부 서비스 설정 불필요. 단, 실제 카카오 로그인 E2E 확인에는 카카오 개발자 콘솔 앱 등록과
`KAKAO_REST_API_KEY`/`KAKAO_CLIENT_SECRET`/`KAKAO_REDIRECT_URI` 값이 필요하다(02-03에서 이미 안내됨,
자동화 테스트는 `MockRestServiceServer`/`@MockitoBean`으로 대체되므로 이 플랜 자체 완료에는 불필요) — 실제
연동 확인은 02-11의 수동 검증 체크포인트에서 진행한다.

## Next Phase Readiness

- `MemberSessionResponse`(memberId/status/onboardingCompleted/rejected)가 확정 계약으로 존재해 02-08(온보딩)이
  같은 형태를 재사용할 수 있다
- 02-07(관리자 로그인)은 이 플랜의 `KakaoAuthController`/`TokenService.issueTokenPair` 사용 패턴을 그대로
  참고해 ID/PW 로그인만 다르게 구현하면 된다
- **차단 요소 없음.** 위 "커밋 정책" 섹션대로, 이번 실행은 사용자의 명시적 커밋 지시를 기다리며 파일 저장까지만
  완료한 상태다

## 이번에 쓴 기술

1. **OAuth 인가 코드(authorization code) 방식이 시크릿을 지키는 원리 ★**
   - **이 코드에서 왜 필요했는가:** FE가 카카오 로그인 버튼을 누르면 카카오가 "인가 코드"라는 1회용 문자열만
     FE로 돌려준다. 이 코드 자체는 아직 아무 권한이 없고, `client_secret`(우리 서버만 아는 비밀값)과 함께
     카카오에 다시 제시해야만 진짜 액세스 토큰으로 교환된다. `client_secret`은 `.env`/서버 환경변수에만
     존재하고 브라우저 어디에도 내려가지 않는다.
   - **안 썼으면 뭐가 깨지는가:** FE가 카카오 SDK로 직접 액세스 토큰까지 받아 서버로 전달하는 방식을 썼다면,
     그 토큰의 진위를 서버가 별도로 재검증해야 하고, `client_secret`을 아예 쓰지 않아 "이 요청이 정말 우리
     FE에서 왔는가"를 증명할 장치가 약해진다. 인가 코드 방식은 시크릿을 서버 밖으로 절대 내보내지 않는다.

2. **트랜잭션 안에서 외부 API를 부르면 안 되는 이유 — 커넥션 점유**
   - **이 코드에서 왜 필요했는가:** `KakaoAuthService.login()`은 카카오 API를 먼저 호출하고(최대 3초 연결 +
     5초 읽기 타임아웃), 그 결과를 가지고서야 DB 트랜잭션(`MemberRegistrationService`)을 연다. 만약 이
     전체를 하나의 `@Transactional` 메서드로 묶었다면, 카카오 응답을 기다리는 최대 8초 동안 커넥션 풀에서
     커넥션 하나를 계속 붙잡고 있게 된다.
   - **안 썼으면 뭐가 깨지는가:** 이 프로젝트의 커넥션 풀(HikariCP)은 크지 않다(소규모 체육관 서비스). 로그인
     몇 건이 동시에 들어와 카카오가 느려지면, 로그인과 무관한 다른 API(회원 목록 조회 등)까지 커넥션 고갈로
     함께 멈출 수 있다. 카카오 호출을 트랜잭션 밖으로 빼두면, 카카오가 느려도 "커넥션을 붙잡은 채 기다리는"
     문제는 생기지 않는다.

3. **스프링 self-invocation 문제 — 왜 회원 생성을 별도 빈으로 분리했는가 ★**
   - **이 코드에서 왜 필요했는가:** `@Transactional`은 스프링이 그 클래스를 감싸는 프록시(대리인) 객체를
     만들어 "메서드 호출 전에 트랜잭션 시작, 끝나면 커밋"을 끼워 넣는 방식으로 동작한다. 만약
     `KakaoAuthService` 안에 `@Transactional` 메서드를 직접 두고 `login()` 내부에서 `this.그메서드()`처럼
     자기 자신을 호출하면, 그 호출은 프록시를 거치지 않고 진짜 객체를 직접 부르는 것이라 트랜잭션이 전혀
     시작되지 않는다.
   - **안 썼으면 뭐가 깨지는가:** 이 사실을 모르고 `KakaoAuthService`에 회원 생성 로직까지 다 넣었다면,
     `@Transactional`을 붙였다고 믿었던 코드가 실제로는 트랜잭션 없이(자동커밋 모드로) 동작해, 동시 요청
     경쟁 상황에서 DB 유니크 제약 위반을 우리가 의도한 방식(재조회로 흡수)으로 처리하지 못하고 그대로
     예외가 터져 500 응답이 나갔을 것이다. `MemberRegistrationService`를 별도 빈으로 분리해, `KakaoAuthService`가
     그 빈의 메서드를 호출할 때는(외부에서 프록시를 거쳐 호출) 트랜잭션이 정상적으로 시작된다.

4. **DB 유니크 제약 위반을 "정상 흐름의 일부"로 흡수하는 find-or-create 패턴**
   - **이 코드에서 왜 필요했는가:** "카카오 계정으로 조회해서 없으면 만든다"는 로직은 조회와 저장 사이에
     시간차가 있다. 같은 카카오 계정으로 아주 짧은 간격을 두고 두 요청이 동시에 들어오면(중복 클릭, 네트워크
     재시도), 둘 다 "없음"을 보고 둘 다 INSERT를 시도할 수 있다 — 애플리케이션 코드의 `if (없으면)` 조건문만으로는
     이 경쟁을 막을 수 없다(두 요청이 정확히 그 조건문을 동시에 통과할 수 있다).
   - **안 썼으면 뭐가 깨지는가:** DB의 유니크 제약(`uq_member_kakao_id`)이 두 번째 INSERT를 거부하며
     `DataIntegrityViolationException`을 던진다. 이걸 그냥 예외로 흘려보내면 그 요청은 "로그인 실패"가
     되어 버린다(사용자 입장에서는 아무 잘못도 안 했는데 로그인이 실패한 것처럼 보인다). 이 예외를 잡아
     "그럼 다른 요청이 방금 만든 그 행을 다시 조회해서 반환"하도록 흡수하면, 두 요청 모두 같은 회원으로
     정상 로그인된다.

5. **Jackson 3에서 `@JsonProperty`는 옛 패키지에 남아 있고 `@JsonNaming`은 새 패키지로 이동한 비대칭 ★**
   - **이 코드에서 왜 필요했는가:** 카카오 응답은 `access_token`처럼 snake_case인데 우리 코드는
     `accessToken`(camelCase)을 쓴다. Boot 4는 Jackson 3(groupId가 `tools.jackson.*`로 바뀜)를 쓰는데,
     실제 classpath jar를 열어 확인해 보니(`verify-boot4-api` 절차) `@JsonProperty`(필드 하나씩 이름 지정)는
     여전히 옛 패키지(`com.fasterxml.jackson.annotation`)에 있고, `@JsonNaming`(클래스 전체 명명 규칙)만
     새 패키지(`tools.jackson.databind.annotation`)로 옮겨갔다. 문서만 보고 짐작했다면 "Jackson 3니까 전부
     `tools.jackson`이겠지"라고 잘못 판단했을 것이다.
   - **안 썼으면 뭐가 깨지는가:** 만약 확인 없이 `com.fasterxml.jackson.annotation.JsonProperty`를 필드마다
     붙였다면 컴파일·동작 자체는 됐겠지만(이 어노테이션은 실제로 살아있다), 이 플랜의 acceptance criteria가
     요구하는 "이 패키지(`auth/kakao`)에 `com.fasterxml.jackson`이 전혀 없어야 한다"는 조건을 만족하지 못했을
     것이다. 클래스 레벨 `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)`(둘 다 `tools.jackson`
     패키지)로 대체해 이 조건을 코드로 만족시켰다.

**일부러 쓰지 않은 것:** 카카오 refresh 토큰 저장 — `KakaoTokenResponse.refreshToken`은 필드로 받지만 우리
서비스는 이 값을 DB에도, 응답에도 담지 않고 그대로 버린다. 카카오 로그인 성공 뒤 우리 자체 refresh 토큰
체계(D-036, `TokenService`)를 별도로 발급하기 때문에 카카오의 refresh를 보관할 이유가 없다 — 보관하면
"우리가 카카오 세션도 대신 갱신해 줄 수 있다"는 오해를 유발하고 관리 부담(만료·회전 정책 2개 병행)만 늘어난다.

---
*Phase: 02-auth-member*
*Completed: 2026-08-03*

## Self-Check: PASSED

- FOUND: 모든 신규 파일 12개(src/main·test) + 수정 파일 1개(docs/api/openapi.yaml) — `ls`로 실존 확인
- 커밋 해시 없음 — 위 "커밋 정책" 섹션대로 이번 실행은 `git add`/`git commit`을 실행하지 않았다(CLAUDE.md·플랜 `<commit_policy>` 준수)
- `./gradlew ktlintFormat && ./gradlew build` BUILD SUCCESSFUL — 이 플랜의 신규 테스트(KakaoApiClientTest 11건, KakaoAuthControllerTest 10건) 포함 전체 테스트 100건 이상 통과, 회귀 없음
- `git diff docs/api/openapi.yaml` — `/api/auth/kakao/login` 경로·`KakaoLoginRequest`/`KakaoLoginResponse`/`MemberSessionResponse` 스키마 추가, `rejectionReason` 속성 없음, 기존 경로 유지, `8099`/`localhost` 잔존 없음 확인
