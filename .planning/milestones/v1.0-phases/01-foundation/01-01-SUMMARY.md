---
phase: 01-foundation
plan: 01
subsystem: error-handling
tags: [problemdetail, rfc9457, spring-boot-4, exception-handler, kotlin]

# Dependency graph
requires: []
provides:
  - "`common/error` 패키지: ErrorCode(6개 공통 코드) / DomainException(도메인 예외 기반 클래스) / GlobalExceptionHandler(전역 예외→ProblemDetail 변환 단일 진입점)"
  - "`docs/error-codes.md` 에러코드 레지스트리 (FE 계약 문서)"
  - "`docs/decisions.md` D-028 (에러 응답 code 필드 + 레지스트리 결정 기록)"
affects: [02-foundation, 03-foundation, "이후 모든 phase의 도메인 예외"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ResponseEntityExceptionHandler 상속 + handleExceptionInternal 오버라이드로 모든 예외(내장+도메인)를 단일 code 주입 지점으로 통일"
    - "DomainException 추상 클래스를 이후 phase의 모든 도메인 예외가 상속"
    - "테스트 전용 컨트롤러는 SpringBootTest 클래스의 중첩 클래스 + @TestConfiguration @Bean 명시 등록으로 컴포넌트 스캔 오염 방지"

key-files:
  created:
    - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
    - src/main/kotlin/com/goldwrestling/common/error/DomainException.kt
    - src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt
    - src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt
    - docs/error-codes.md
  modified:
    - src/main/resources/application.yml
    - docs/decisions.md

key-decisions:
  - "ErrorCode enum에 defaultStatus: HttpStatus를 보유시켜 코드↔HTTP 상태 대응을 코드 안에 고정 (D-028)"
  - "spring.mvc.problemdetails.enabled=true를 2차 방어선으로 추가 (핸들러 부재 시에도 ProblemDetail 유지)"
  - "테스트 전용 /internal-test/* 컨트롤러는 GlobalExceptionHandlerTest의 형제 중첩 클래스 + @TestConfiguration @Bean으로만 등록, HealthController는 건드리지 않음"

requirements-completed: [FOUND-01]

# Metrics
duration: ~45min
completed: 2026-07-30
---

# Phase 1 Plan 1: 에러 응답 통일 (RFC 9457 ProblemDetail) Summary

**`ResponseEntityExceptionHandler` 상속으로 스프링 내장 예외(404/405/400/415)와 도메인 예외를 단일 `handleExceptionInternal` 지점에서 커스텀 `code` 필드가 붙은 `application/problem+json`으로 통일했고, `docs/error-codes.md` 레지스트리와 D-028 결정 기록까지 완료했다.**

## Performance

- **Completed:** 2026-07-30 (KST 기준 오후, UTC 11:13)
- **Tasks:** 3/3 완료
- **Files:** 신규 5개, 수정 2개 (테스트 코드 제외 프로덕션 소스 3개)
- **측정 소요 시간:** `./gradlew test --tests "com.goldwrestling.common.error.GlobalExceptionHandlerTest"` 단독 실행 ~4~5초, `./gradlew build`(ktlintCheck+compile+전체 테스트) ~8초.
  `01-VALIDATION.md`의 추정치(quick 40~60초·full 90~180초)보다 훨씬 빠르게 나왔는데, 이 세션에서는 Testcontainers Postgres 컨테이너가 앞선 검증 실행들에서 이미 기동돼 재사용되고 있었기 때문이다(콜드 스타트 없음). 콜드 스타트 기준 실측치는 다음 실행자가 컨테이너를 새로 내렸다 올릴 때 갱신 권장.

## Accomplishments
- `ErrorCode` enum 6개(VALIDATION_FAILED/MALFORMED_REQUEST/RESOURCE_NOT_FOUND/METHOD_NOT_ALLOWED/UNSUPPORTED_MEDIA_TYPE/INTERNAL_ERROR) + `defaultStatus` 프로퍼티로 코드↔상태 대응을 코드에 고정
- `DomainException` 추상 클래스 — 이후 phase(잔여 부족·정원 초과 등)가 상속할 기반
- `GlobalExceptionHandler`가 `ResponseEntityExceptionHandler`를 상속해 내장 예외(404/405/400/415)와 도메인 예외, 예상 못 한 예외(500)까지 전부 같은 `handleExceptionInternal`을 거치게 통일
- TDD로 8개 동작을 `GlobalExceptionHandlerTest`에 증명 — RED(테스트 전부 실패, 단 컨텍스트는 정상 로드) → GREEN(전부 통과)
- `docs/error-codes.md` 신설, `docs/decisions.md` D-028 기록

## Task Commits

**이 플랜은 CLAUDE.md 커밋 규칙에 따라 커밋하지 않았다** (아래 "커밋 안 함" 섹션 참조). 코드는 작업 트리에 그대로 남아 있고, 사용자가 확인 후 커밋 단위를 지시하면 그때 커밋한다.

1. Task 1: 에러 계약 정의 (ErrorCode·DomainException·error-codes.md) — 커밋 안 함, 파일만 존재
2. Task 2: GlobalExceptionHandler + 응답 계약 통합테스트 (TDD RED→GREEN) — 커밋 안 함, 파일만 존재
3. Task 3: D-028 결정 기록 + 전체 빌드 게이트 — 커밋 안 함, 파일만 존재

## Files Created/Modified
- `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt` — 공통 에러코드 6개 + HTTP 상태 대응
- `src/main/kotlin/com/goldwrestling/common/error/DomainException.kt` — 도메인 예외 기반 클래스
- `src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt` — 전역 예외 → ProblemDetail 변환 단일 진입점
- `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt` — 404/405/400×2/415/500/도메인 예외/trace 부재 8개 케이스
- `docs/error-codes.md` — 신규 에러코드 레지스트리
- `src/main/resources/application.yml` — `spring.mvc.problemdetails.enabled: true` 추가 (2차 방어선)
- `docs/decisions.md` — D-028 기록, 예시 플레이스홀더를 D-029로 이동

## verify-boot4-api 확인 결과 (CLAUDE.md 규칙 9)

- **`handleExceptionInternal` 실제 시그니처** — Context7 `/spring-projects/spring-framework/v7.0.5`(Framework 7.0.5, 이 프로젝트가 쓰는 7.0.8과 동일 메이저.마이너)로 확인:
  `protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request)`.
  **RESEARCH Pattern 1 스니펫과의 차이**: 리턴 타입이 `@Nullable`이다. RESEARCH 스니펫은 `ResponseEntity<Any>`(non-null)로 선언하고 `!!`로 강제 언래핑했는데, 실제로는 `ResponseEntity<Any>?`로 오버라이드하는 것이 시그니처에 더 맞다(Kotlin은 non-null 리턴으로 좁혀 오버라이드하는 것도 허용하지만, 여기서는 `@Nullable` 원본을 그대로 따라 `?`로 선언해 `!!` 크래시 가능성을 없앴다). `compileKotlin`으로 최종 검증 완료.
- **`spring.mvc.problemdetails.enabled` 키 존재 여부** — 로컬 Gradle 캐시의 `spring-boot-webmvc-4.1.0.jar`의 `META-INF/spring-configuration-metadata.json`에서 직접 확인: `{"name": "spring.mvc.problemdetails.enabled", "type": "java.lang.Boolean", "defaultValue": false}`. **실제로 존재**하므로 `application.yml`에 추가했다 (RESEARCH가 "존재 확인 필요"로 남겼던 항목).
- **`NoResourceFoundException` 패키지** — 로컬 `spring-webmvc-7.0.8.jar`에서 `org/springframework/web/servlet/resource/NoResourceFoundException.class` 확인. RESEARCH 추정과 일치.
- 그 외 예외 매핑에 쓴 패키지(`HttpMediaTypeNotSupportedException`, `HttpRequestMethodNotSupportedException`은 `org.springframework.web`, `HandlerMethodValidationException`·`MethodArgumentTypeMismatchException`은 `org.springframework.web.method.annotation`)도 로컬 jar 목록으로 실측 확인 후 사용 — 추측 없이 진행했다.

## 테스트를 쓰지 않은 변경과 면제 사유 (conventions §10.0)

- `src/main/resources/application.yml` — 설정 값 변경(면제 항목: yml). `spring.mvc.problemdetails.enabled` 자체는 GlobalExceptionHandler가 있는 한 실제 응답 경로에 영향을 주지 않는 2차 방어선이라 별도 테스트 대상이 아니다.
- `docs/error-codes.md`, `docs/decisions.md` — 문서(면제 항목).

## Decisions Made
- ErrorCode enum이 `defaultStatus`를 직접 보유해 코드↔상태 매핑을 코드 안에 고정 (문서와 코드가 갈라지는 것을 원천 차단)
- 테스트 전용 `/internal-test/*` 컨트롤러는 `GlobalExceptionHandlerTest`의 형제 중첩 클래스로 두고 `@TestConfiguration`의 `@Bean` 메서드로만 등록 — 최초 시도는 `@TestConfiguration` **안에** `inner class`로 넣는 것이었는데, 이는 `ConfigurationClassParser`의 "member class" 재귀 처리에 걸려 같은 빈이 두 번 등록되는 `Ambiguous mapping` 오류를 냈다(RED 단계에서 실제로 재현·확인). 컨트롤러를 `Config`의 형제로 옮기고 `@Bean` 메서드로만 등록하는 방식으로 전환해 해결했고, `HealthControllerTest`·`FlywayMigrationIntegrationTest`·`GoldWrestlingApplicationTests`가 이 컨트롤러 없이도 그대로 통과함을 확인해 다른 컨텍스트 오염이 없음을 회귀로 검증했다.
- `resolveErrorCode`의 미매핑 예외 폴백은 "5xx → INTERNAL_ERROR, 그 외 → `ErrorCode.entries`에서 같은 HTTP 상태를 가진 코드, 없으면 INTERNAL_ERROR"로 구현 (plan의 "5xx는 INTERNAL_ERROR" 지시를 코드로 일반화)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] 테스트 전용 컨트롤러의 중첩 위치를 계획과 다르게 조정**
- **Found during:** Task 2 RED 단계
- **Issue:** plan이 제시한 "`@TestConfiguration` 안에 `@RestController` 빈 등록" 구조를 문자 그대로 `inner class`로 구현했더니, `ConfigurationClassParser`가 `@Configuration` 클래스의 멤버 클래스를 재귀적으로 후보 설정 클래스로도 처리해 같은 컨트롤러 빈이 명시적 `@Bean`과 멤버 클래스 스캔 양쪽에서 중복 등록되어 `IllegalStateException: Ambiguous mapping`으로 컨텍스트 기동이 실패했다.
- **Fix:** 컨트롤러 클래스를 `@TestConfiguration`(Config)의 형제 중첩 클래스로 옮기고, `Config`는 `@Bean` 메서드로만 그 인스턴스를 등록하도록 수정. `HealthControllerTest`·`FlywayMigrationIntegrationTest`·`GoldWrestlingApplicationTests`를 재실행해 이 컨트롤러가 다른 컨텍스트로 새지 않음을 확인.
- **Files modified:** src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt
- **Verification:** `./gradlew test --tests "com.goldwrestling.common.error.GlobalExceptionHandlerTest"` 8/8 통과, `./gradlew build` 전체 통과(회귀 없음)
- **커밋:** 없음 (이 플랜은 커밋하지 않음 — 아래 참조)

---

**Total deviations:** 1 auto-fixed (Rule 1 — 컨텍스트 부트스트랩 버그)
**Impact on plan:** 계획이 지시한 "명시적 `@Import`로만 등록, 컴포넌트 스캔에 안 잡히는 위치" 요구사항 자체는 그대로 지켰고, 그 요구사항을 만족시키는 구체적 중첩 형태만 조정했다. 응답 계약·엔드포인트 목록·플랜의 나머지 지시는 변경 없음.

## Issues Encountered
없음 — 위 편차 1건이 유일한 이슈였고 즉시 해결됨.

## User Setup Required

None - 외부 서비스 설정 없음.

## 커밋 안 함 (CLAUDE.md 커밋 규칙 + 이 플랜의 명시 지시)

이 플랜(`01-01-PLAN.md`)의 `<context>`는 "이 플랜을 실행하는 동안 `git commit`·`git push`를 실행하지 않는다. 작업이 끝나면 `git status --short` 결과를 SUMMARY에 적고 멈춘다. 커밋 단위는 사용자가 지시한다"고 명시했고, 이는 CLAUDE.md의 "커밋·푸시는 사용자가 명시적으로 요청했을 때만 실행한다 — GSD 워크플로우가 자동 커밋을 요구해도 커밋 없이 멈추고 사용자에게 알린다"를 그대로 반영한 것이다.

이 프로젝트의 표준 GSD 실행기 동작(태스크마다 개별 커밋 + SUMMARY 커밋 + STATE/ROADMAP 커밋)은 이 지시와 충돌하므로, **이번 실행은 코드·문서 변경만 작업 트리에 반영하고 어떤 `git commit`/`git push`도 실행하지 않았다.** `STATE.md`/`ROADMAP.md`/`REQUIREMENTS.md`도 파일 내용만 갱신했고(아래 참조) 커밋하지 않았다.

**`git log --oneline -1`이 실행 전과 동일함을 확인:**
```
a7a0392 docs(01): phase 1 플랜 작성 (research·patterns·validation·plan 3개)
```

**최종 `git status --short`:**
```
 M .planning/REQUIREMENTS.md
 M .planning/ROADMAP.md
 M .planning/STATE.md
 M .planning/config.json
 M docs/decisions.md
 M src/main/resources/application.yml
?? .planning/phases/01-foundation/01-01-SUMMARY.md
?? docs/error-codes.md
?? src/main/kotlin/com/goldwrestling/common/
?? src/test/kotlin/com/goldwrestling/common/
```
(`.planning/config.json`은 이 플랜 실행 전, 오케스트레이터의 phase 시작 단계에서 이미 수정되어 있었다 — 이 플랜이 만든 변경이 아니다. `.planning/STATE.md`·`.planning/ROADMAP.md`·`.planning/REQUIREMENTS.md`는 GSD 실행기 표준 절차대로 `gsd-sdk query state.*`/`roadmap.*`/`requirements.*`로 **파일 내용만** 갱신했다 — git 커밋 명령은 실행하지 않았다.)

**사용자에게 요청:** 위 변경 사항(3개 신규 소스 파일 + 테스트 + 문서 2건 수정 + STATE/ROADMAP/REQUIREMENTS 갱신)을 확인한 뒤, 원하는 커밋 단위(예: 태스크별 3개 커밋 vs 플랜 전체 1개 커밋)를 알려주시면 그대로 커밋하겠습니다.

## 이번에 쓴 기술

1. **`@RestControllerAdvice` + `ResponseEntityExceptionHandler` 상속으로 예외 처리 진입점 단일화** ★
   - **왜 필요했는가:** 이 프로젝트는 "스프링이 자체적으로 던지는 에러(404/405/400/415)도 우리가 던지는 도메인 에러(잔여 부족 등, 다음 phase)와 똑같은 모양(+커스텀 `code` 필드)으로 응답"해야 한다(D-06). 스프링은 `@ExceptionHandler`를 여러 컨트롤러 어드바이스에 나눠 등록할 수 있는데, 그렇게 하면 어떤 예외가 어느 핸들러로 가는지 추적하기 어려워지고 일부 응답에만 `code`가 빠질 위험이 생긴다. `ResponseEntityExceptionHandler`를 상속하면 스프링 MVC의 내장 예외 처리 로직 전체가 결국 `handleExceptionInternal`이라는 한 메서드를 통과하도록 설계돼 있어서, 거기 한 곳에서 `code`를 심으면 예외 종류에 상관없이 다 적용된다.
   - **안 썼으면 뭐가 깨지는가:** 개별 `@ExceptionHandler`를 나열하는 방식으로 만들었다면, 404/405처럼 스프링이 자동으로 처리하는 예외는 우리 코드를 거치지 않고 스프링 기본 응답(또는 Boot가 자동 등록하는 별도 핸들러의 응답)으로 나가서 `code` 필드가 통째로 빠졌을 것이다.

2. **`@ConditionalOnMissingBean`으로 자동설정 빈을 조건부로 대체하는 방식** ★
   - **왜 필요했는가:** Boot 4.1은 `spring.mvc.problemdetails.enabled=true`일 때 `ProblemDetailsExceptionHandler`라는 빈을 자동으로 등록해서 검증 실패 등을 이미 `ProblemDetail`로 바꿔준다. 그런데 이 자동 빈은 `@ConditionalOnMissingBean(ResponseEntityExceptionHandler::class)` 조건이 걸려 있어서, 우리가 그 타입(또는 하위 타입)의 빈을 하나라도 등록하면 자동 빈은 스스로 물러난다. 이 메커니즘 덕분에 "직접 만든 핸들러 vs 자동 핸들러가 동시에 떠서 응답이 뒤섞이는" 상황(RESEARCH Pitfall 1)을 걱정할 필요 없이, 우리 클래스 하나만 등록하면 됐다.
   - **안 썼으면 뭐가 깨지는가:** 우리가 `ResponseEntityExceptionHandler`를 상속하지 않고 별개의 `@RestControllerAdvice`만 만들었다면, 자동 등록된 `ProblemDetailsExceptionHandler`와 우리 핸들러가 동시에 남아 어떤 예외가 어느 쪽으로 가는지 예측 불가능해지고, 응답에 `code`가 있는 것과 없는 것이 섞였을 것이다.

3. **RFC 9457 `ProblemDetail`(`application/problem+json`)**
   - **왜 필요했는가:** 이 프로젝트는 앞으로 5개 phase가 각자 도메인 에러(잔여 부족, 정원 초과 등)를 던진다. 그때마다 에러 응답 모양을 새로 정하면 FE는 에러 종류마다 다른 파싱 코드를 짜야 한다. `ProblemDetail`은 스프링 내장 표준 타입이라 `type`/`title`/`status`/`detail`/`instance` 같은 필드가 이미 정해져 있고, 우리는 여기에 `code`만 얹었다.
   - **안 썼으면 뭐가 깨지는가:** 직접 만든 `ApiResponse<T>` 같은 공통 래퍼를 썼다면 성공 응답까지 전부 한 겹 감싸야 해서 FE가 매번 벗겨야 하고(D-017에서 이미 기각된 대안), 우리 에러와 스프링이 자체적으로 던지는 에러의 응답 모양이 갈라졌을 것이다.

4. **콘텐트 협상(Content Negotiation)이 415로 이어지는 경로** ★
   - **왜 필요했는가:** 415(Unsupported Media Type)는 "요청 URL이 잘못됨"이 아니라 "이 컨트롤러 메서드가 처리할 수 있는 `Content-Type`이 아닌 본문을 보냈음"을 뜻한다. 이번 테스트에서는 JSON만 받는(`consumes = APPLICATION_JSON_VALUE`) 엔드포인트에 `text/plain`으로 요청을 보내 이 경로를 직접 재현했다. 스프링이 요청의 `Content-Type` 헤더와 컨트롤러 메서드가 선언한 `consumes` 목록을 비교해서 일치하는 `HttpMessageConverter`가 없으면 `HttpMediaTypeNotSupportedException`을 던진다.
   - **안 썼으면 뭐가 깨지는가:** 이 경로를 명시적으로 매핑하지 않았다면 415 응답에 `code`가 안 붙어서 FE가 "지원 안 하는 요청 형식"과 "그냥 서버 에러"를 구분하지 못했을 것이다.

5. **스프링 테스트 컨텍스트 캐시와 애노테이션 조합 통일**
   - **왜 필요했는가:** `@SpringBootTest`는 매번 새로 애플리케이션을 통째로 띄우면 느리다. 스프링은 같은 설정(애노테이션 조합 + `@Import` 인자)을 쓰는 테스트끼리는 이미 띄운 컨텍스트를 재사용한다. 이번 테스트는 기존 `HealthControllerTest`와 애노테이션 조합(`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import`)을 똑같이 맞췄지만, `@Import` 인자에 테스트 전용 컨트롤러가 하나 더 들어가서 이 조합만은 별도 컨텍스트가 하나 더 뜬다 — 그래서 plan이 지시한 대로 FOUND-01의 8개 케이스를 전부 이 한 클래스에 모아 추가 컨텍스트를 1개로 묶었다.
   - **안 썼으면 뭐가 깨지는가:** 애노테이션 조합을 테스트마다 다르게 썼다면 매 테스트 클래스마다 새 컨텍스트가 뜨면서 전체 테스트 스위트가 눈에 띄게 느려졌을 것이다.

6. **일부러 쓰지 않은 것 — 커스텀 응답 래퍼(`ApiResponse<T>`), 낙관적 락/트랜잭션 관련 기법**
   - 이번 작업 범위(에러 응답 통일)에는 잔여 횟수 차감이나 동시성 로직이 전혀 없어서 트랜잭션 전파·락 관련 기법은 등장하지 않았다. 커스텀 응답 래퍼는 D-017에서 이미 기각된 설계라 이번에도 만들지 않았다 — `ProblemDetail`이 곧 계약이고, 성공 응답은 이 래퍼와 무관하게 컨트롤러가 반환하는 DTO 그대로 나간다.

## Next Phase Readiness
- `DomainException`·`ErrorCode`가 준비됐으므로, 이후 phase(회원·이용권·예약 등)는 `DomainException`을 상속하고 `ErrorCode`에 도메인 코드를 추가하는 것만으로 같은 응답 형식을 얻는다. 새 코드를 추가할 때는 `docs/error-codes.md` 표도 같은 PR에서 갱신해야 한다(D-028).
- 같은 phase의 `01-02-PLAN.md`(초기 스키마)·`01-03-PLAN.md`(openapi 파이프라인)가 이 플랜 위에서 순차 실행될 수 있다. 단, **이 플랜의 변경사항이 아직 커밋되지 않았으므로**, 사용자가 커밋 여부/단위를 결정한 뒤 다음 플랜을 진행하는 것을 권장한다.
- 블로커: 없음.

## Self-Check: PASSED

- 생성 파일 6개 전부 `[ -f ]`로 존재 확인: ErrorCode.kt, DomainException.kt, GlobalExceptionHandler.kt, GlobalExceptionHandlerTest.kt, docs/error-codes.md, 이 SUMMARY 파일
- `git log --oneline -1`이 실행 전(`a7a0392`)과 동일 — 커밋 없음 확인
- `./gradlew build` 전체 그린 (ktlintCheck + compileKotlin + 전체 테스트, GoldWrestlingApplicationTests 포함 12개 테스트 전부 PASSED)

---
*Phase: 01-foundation*
*Completed: 2026-07-30*
