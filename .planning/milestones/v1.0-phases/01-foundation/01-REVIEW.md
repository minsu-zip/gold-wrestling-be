---
phase: 01-foundation
reviewed: 2026-07-30T11:46:08Z
depth: standard
files_reviewed: 18
files_reviewed_list:
  - build.gradle.kts
  - .claude/skills/add-endpoint/SKILL.md
  - docs/api/openapi.yaml
  - docs/conventions.md
  - docs/decisions.md
  - docs/error-codes.md
  - src/main/kotlin/com/goldwrestling/admin/Admin.kt
  - src/main/kotlin/com/goldwrestling/admin/AdminBranch.kt
  - src/main/kotlin/com/goldwrestling/branch/Branch.kt
  - src/main/kotlin/com/goldwrestling/common/error/DomainException.kt
  - src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt
  - src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt
  - src/main/kotlin/com/goldwrestling/member/Member.kt
  - src/main/kotlin/com/goldwrestling/member/MemberStatus.kt
  - src/main/resources/application.yml
  - src/main/resources/db/migration/V2__create_branch_member_admin.sql
  - src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt
  - src/test/kotlin/com/goldwrestling/db/FlywayMigrationIntegrationTest.kt
findings:
  critical: 0
  warning: 4
  info: 10
  total: 14
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-07-30T11:46:08Z
**Depth:** standard
**Files Reviewed:** 18
**Status:** issues_found

## Summary

Phase 1(foundation) 산출물 — 에러 계약(ErrorCode·DomainException·GlobalExceptionHandler), 초기 엔티티 4종(Branch/Member/Admin/AdminBranch) + V2 마이그레이션, openapi 재생성 파이프라인(build.gradle.kts), 관련 문서·테스트 — 을 리뷰했다.

전반적으로 프로젝트 규약을 충실히 지켰다. 엔티티는 `data class` 금지·`fetch = LAZY` 명시·`@Enumerated(STRING)`·nullable 정합을 모두 만족하고, 마이그레이션은 FK·유니크 제약·인덱스를 명시했으며(D-021), 에러 응답은 단일 진입점에서 `code`를 주입하는 구조가 D-028과 일치한다. 통합테스트도 성공·실패 경로와 내부 정보 비노출까지 검증한다. 시크릿 실값·위험 함수·주입 취약점은 발견하지 못했다.

다만 **Critical 0건, Warning 4건, Info 10건**을 찾았다. 핵심 우려는 (1) `resolveErrorCode` 폴백이 매핑 안 된 예외에서 `code`↔HTTP 상태 불일치를 만들어 "FE는 code로만 분기한다"는 계약(D-028)을 깨는 경로가 **지금도 재현 가능**하다는 것, (2) `generateApiDocs` 파이프라인이 이전 실행의 잔존 프로세스를 감지하지 못해 **낡은 스펙을 조용히 커밋**할 수 있다는 것이다.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: resolveErrorCode 폴백이 code↔상태 불일치 응답을 만든다 (406 + INTERNAL_ERROR)

**File:** `src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt:113-120`
**Issue:** 부모 `ResponseEntityExceptionHandler`가 처리하지만 `resolveErrorCode`의 `when`에 매핑되지 않은 예외들이 `else` 폴백으로 떨어진다. 두 가지 결함이 있다.

1. **상태 매칭 실패 시 코드가 거짓말을 한다.** `HttpMediaTypeNotAcceptableException`(406)은 매칭되는 `ErrorCode`가 없어 `INTERNAL_ERROR`로 응답된다 — 즉 **HTTP 406 + `code: INTERNAL_ERROR`**. `docs/error-codes.md`는 `INTERNAL_ERROR = 500`이라고 계약하고 있으므로 레지스트리와 실제 응답이 갈라진다. 이 경로는 지금도 재현된다: `GET /api/system/health`에 `Accept: application/xml`을 보내면 된다. FE는 클라이언트 측 협상 문제를 "서버 오류"로 처리하게 된다. 향후 `ResponseStatusException(409)` 같은 예외도 같은 함정에 빠진다.
2. **폴백이 enum 선언 순서에 의존한다.** `ErrorCode.entries.firstOrNull { it.defaultStatus == ... }`는 400에서 `VALIDATION_FAILED`(선언 순서상 첫 번째)를 고른다. `ServletRequestBindingException`(필수 헤더 누락, 400)이 `@Valid` 실패용 코드로 분류되고, 누군가 enum 항목 순서를 바꾸면 응답 코드가 조용히 달라진다.

**Fix:**
```kotlin
// 1) 부모가 처리하는 나머지 예외를 명시 매핑
is HttpMediaTypeNotAcceptableException -> ErrorCode.NOT_ACCEPTABLE  // enum·error-codes.md에 406 추가
is ServletRequestBindingException -> ErrorCode.MALFORMED_REQUEST

// 2) 폴백은 상태값 추측을 버리고 고정 코드로
else -> if (statusCode.is4xxClientError) ErrorCode.MALFORMED_REQUEST else ErrorCode.INTERNAL_ERROR
```
(406을 별도 코드로 만들지 않겠다면 최소한 `INTERNAL_ERROR`가 4xx 상태와 함께 나가는 조합만은 막아야 한다. 어느 쪽이든 `docs/error-codes.md`를 같은 커밋에서 갱신할 것 — D-028.)

### WR-02: 포괄 Exception 핸들러가 향후 시큐리티 인가 예외를 500으로 삼킨다

**File:** `src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt:52-61`
**Issue:** `@ExceptionHandler(Exception::class)`는 인증 phase에서 `@PreAuthorize`/메서드 시큐리티가 도입되는 순간 함정이 된다. 메서드 시큐리티의 `AuthorizationDeniedException`(`AccessDeniedException` 하위)은 컨트롤러·서비스 안에서 던져져 필터가 아니라 이 advice에 먼저 잡히고, 이 핸들러가 **403이어야 할 응답을 500 + `INTERNAL_ERROR`로 변환**한다. 이는 Spring Security + 포괄 핸들러 조합의 잘 알려진 결함인데, 코드 주석·TODO 어디에도 이 전제가 기록되어 있지 않아 인증 phase가 그대로 밟을 가능성이 높다.
**Fix:** 지금 방어선을 넣어 두는 것이 가장 싸다.
```kotlin
@ExceptionHandler(Exception::class)
fun handleUnexpectedException(ex: Exception, request: WebRequest): ResponseEntity<Any> {
    // 시큐리티 예외는 프레임워크(ExceptionTranslationFilter/시큐리티 핸들러)가 401/403으로 처리하도록 되던진다
    if (ex is org.springframework.security.access.AccessDeniedException ||
        ex is org.springframework.security.core.AuthenticationException
    ) {
        throw ex
    }
    ...
}
```

### WR-03: generateApiDocs가 잔존(stale) 프로세스의 낡은 스펙을 조용히 커밋할 수 있다

**File:** `build.gradle.kts:119-153`
**Issue:** `startApiDocsApp`은 8099 포트가 비어 있는지 확인하지 않고, 기동 전에 이전 PID를 정리하지도 않으며, `app.pid`를 **덮어써** 기존 프로세스 추적을 끊는다. 실패 시나리오: 이전 실행에서 Gradle 데몬이 죽어 finalizer(`stopApiDocsApp`)가 돌지 못하면 옛 jar의 앱이 8099에 살아남는다. 다음 `generateApiDocs`에서 새 java 프로세스는 포트 충돌로 즉사하지만, `waitApiDocsApp`의 health 폴링은 **잔존 앱이 응답해 즉시 성공**하고, `downloadApiDocs`는 **옛 jar의 스펙**을 `docs/api/openapi.yaml`로 내려받는다. "항상 최신 스펙"(D-029)이라는 계약이 소리 없이 깨지고, FE는 낡은 계약으로 타입을 생성한다.
**Fix:** `startApiDocsApp`의 `doFirst`에서 (1) `app.pid`가 있으면 먼저 kill, (2) `curl -sf http://localhost:8099/actuator/health`가 이미 응답하면 즉시 실패시킨다. 추가로 `waitApiDocsApp`에서 `kill -0 $(cat app.pid)`로 우리가 띄운 PID가 살아 있는지 함께 확인하면 "다른 프로세스가 응답 중"인 상황을 확실히 걸러낸다.

### WR-04: 프로덕션 산출물이 .planning 전용 결정 ID(D-01~D-09)를 인용해 decisions.md 번호 체계와 충돌한다

**File:** `build.gradle.kts:104,107,110` / `src/main/resources/db/migration/V2__create_branch_member_admin.sql:2,12,19,33` / `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt:6` / `src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt:27` / `src/main/kotlin/com/goldwrestling/member/Member.kt:20` / `src/main/kotlin/com/goldwrestling/admin/Admin.kt:13` / `docs/decisions.md:263`
**Issue:** 코드 주석과 마이그레이션이 인용하는 `D-01`~`D-09`는 `docs/decisions.md`가 아니라 `.planning/phases/01-foundation/01-CONTEXT.md`에만 존재하는 phase-로컬 ID다. CLAUDE.md 문서 우선순위에 따르면 `.planning/`은 "실행 상태이지 스펙이 아니다" — 영속 산출물(코드·마이그레이션·build 파일)이 스펙 근거로 인용할 대상이 아니다. 더 나쁜 것은 표기 충돌이다: `build.gradle.kts:104`의 "D-01/D-02"는 decisions.md의 `D-001`(차감 시점)·`D-002`(배치 역할)와 혼동을 유발한다. 특히 마이그레이션 파일은 수정 금지 원칙 때문에 이 깨진 참조가 영구히 남는다. `docs/decisions.md:263`의 "(D-03)"도 같은 종류의 깨진 참조다(레지스트리에 D-03이 없다). `D-04`(최소 스키마 원칙)·`D-09`(시드 주입)는 실제 설계 결정인데 decisions.md에 기록조차 없다 — CLAUDE.md 규칙 5 위반.
**Fix:** 코드·gradle 주석의 `D-06`→`D-028`, `D-01/D-02/D-03`→`D-029`로 교체하고, `D-04`(최소 스키마)·`D-09`(Flyway 시드) 내용을 decisions.md에 새 D 번호로 등재한 뒤 코드 주석이 그 번호를 가리키게 한다. `docs/decisions.md:263`의 "(D-03)"은 문구로 풀어 쓴다. (V2 마이그레이션 파일 자체는 수정 금지이므로 그대로 두되, 대응되는 decisions.md 항목이 존재하게 만드는 것이 교정이다.)

## Info

### IN-01: conventions.md의 CLAUDE.md 규칙 번호 참조가 하나씩 어긋나 있다

**File:** `docs/conventions.md:90,111`
**Issue:** 90행 "이력 없는 변경은 … (CLAUDE.md 규칙 5)" — PassTransaction 이력은 규칙 **6**이다(규칙 5는 decisions 기록). 111행 "openapi.yaml을 재생성해 … (CLAUDE.md 규칙 3)" — openapi 재생성은 규칙 **4**다(규칙 3은 glossary 네이밍).
**Fix:** 각각 규칙 6, 규칙 4로 수정.

### IN-02: 에러코드 레지스트리의 일부 매핑 경로에 테스트가 없다

**File:** `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt`
**Issue:** `docs/error-codes.md`가 계약으로 명시한 발생 지점 중 `MissingServletRequestParameterException`·`MethodArgumentTypeMismatchException`(→ MALFORMED_REQUEST), `HandlerMethodValidationException`(→ VALIDATION_FAILED) 경로는 통합테스트가 없다. 현재는 본문 파싱 실패(`HttpMessageNotReadableException`) 하나로 MALFORMED_REQUEST를 대표하는데, 레지스트리 "발생 지점" 열이 계약이라면 세 경로 모두 한 번씩은 검증하는 편이 안전하다.
**Fix:** `TestErrorController`에 `@RequestParam` 필수 파라미터 엔드포인트 하나를 추가해 누락·타입 불일치 케이스 2개를 붙인다.

### IN-03: handleExceptionInternal이 null을 반환한 뒤 응답을 다시 만드는 폴백은 null 계약과 모순된다

**File:** `src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt:44,59-60`
**Issue:** 부모 `handleExceptionInternal`이 null을 반환하는 경우는 "응답이 이미 커밋됨 — 더 쓰지 말라"는 신호인데, `?: ResponseEntity.of(problem).build()`는 그 신호를 무시하고 새 응답을 반환한다. 커밋된 응답에 다시 쓰려다 로그 경고/`IllegalStateException`성 소음만 남긴다. 실해는 드물지만 계약 위반이다.
**Fix:** 두 핸들러의 반환 타입을 `ResponseEntity<Any>?`로 바꾸고 폴백 없이 그대로 전달한다.

### IN-04: configuration-processor가 annotationProcessor로 선언되어 Kotlin 소스에 동작하지 않는다

**File:** `build.gradle.kts:48`
**Issue:** `annotationProcessor(...)`는 Java 소스에만 적용된다. `@ConfigurationProperties`인 `CorsProperties`는 Kotlin이라 메타데이터(`spring-configuration-metadata.json`)가 생성되지 않는다 — 사실상 죽은 설정이다(기능 영향은 없고 IDE 자동완성 손실뿐).
**Fix:** kapt를 붙여 `kapt("org.springframework.boot:spring-boot-configuration-processor")`로 바꾸거나, 메타데이터가 필요 없다면 선언을 제거한다.

### IN-05: `logging.level.org.hibernate.orm.jdbc.bind: info`는 no-op이다

**File:** `src/main/resources/application.yml:73-75`
**Issue:** 바인드 파라미터 로깅은 `trace`에서만 출력된다. `info`로 지정하는 것은 루트 기본값과 같아 아무 효과가 없다. "파라미터를 보이게 하려던 것"이면 동작하지 않고, "안 보이게 고정하려던 것"이면 불필요한 줄이다 — 어느 쪽이든 의도가 코드로 드러나지 않는다.
**Fix:** 의도를 주석으로 명시하거나(예: "PII 노출 방지 — trace로 올리지 말 것") 줄을 제거한다.

### IN-06: openapi.yaml 응답 미디어 타입이 `*/*`로 생성된다

**File:** `docs/api/openapi.yaml:22`
**Issue:** `HealthController.health()`에 `produces`가 없어 스펙이 `'*/*'`로 새겨졌다. FE 타입 생성은 대체로 동작하지만, 계약 파일로서는 `application/json` 명시가 정확하다. 이후 도메인 엔드포인트가 같은 패턴을 복제하기 전에 잡아 두는 편이 좋다.
**Fix:** `@GetMapping("/health", produces = [MediaType.APPLICATION_JSON_VALUE])` 후 `generateApiDocs` 재실행.

### IN-07: Admin.name·Branch.name이 변경 경로 없이 `var`다

**File:** `src/main/kotlin/com/goldwrestling/admin/Admin.kt:24`, `src/main/kotlin/com/goldwrestling/branch/Branch.kt:21`
**Issue:** conventions §3은 "변경되는 필드만 var"인데 Phase 1에는 이 필드들을 수정하는 코드 경로가 없다. 불필요한 가변성은 나중에 이력 없는 수정 경로가 슬쩍 생기는 것을 막지 못한다.
**Fix:** `val`로 두고, 수정 기능이 생기는 phase에서 `var`로 바꾼다.

### IN-08: member.status에 허용 값 CHECK 제약이 없다

**File:** `src/main/resources/db/migration/V2__create_branch_member_admin.sql:22`
**Issue:** `status VARCHAR(20)`은 enum 4개 값 이외의 문자열도 받는다. 지금은 앱이 유일한 쓰기 경로라 위험이 낮지만, 수동 SQL·시드가 오타를 넣으면 `@Enumerated(STRING)` 역직렬화가 런타임에 터진다. "제약도 마이그레이션에 명시"(conventions §9) 취지의 선택적 강화 항목.
**Fix:** 다음 마이그레이션에서 `CHECK (status IN ('PENDING','ACTIVE','ON_LEAVE','INACTIVE'))` 추가를 검토 (enum 값 추가 시 마이그레이션이 함께 필요해지는 트레이드오프 포함해 결정).

### IN-09: DB 세션 시간대 테스트가 JVM 기본 시간대에 암묵 의존한다

**File:** `src/test/kotlin/com/goldwrestling/db/FlywayMigrationIntegrationTest.kt:35-39`
**Issue:** `SHOW TIME ZONE`이 Asia/Seoul인 것은 JDBC 드라이버가 JVM 기본 시간대를 세션에 전달한 결과인데, 그 JVM 기본값은 `build.gradle.kts`의 `systemProperty("user.timezone", ...)`가 만든다(프로덕션의 `main()` `TimeZone.setDefault`는 `@SpringBootTest`에서 실행되지 않는다). Gradle 밖(IDE 단독 실행)에서, 그리고 머신 시간대가 Seoul이 아니면 이 테스트는 코드 결함 없이 실패한다.
**Fix:** 테스트가 검증하려는 것이 "배선"이라면 그대로 두되 KDoc에 이 전제(반드시 Gradle로 실행)를 명시하거나, 테스트 리소스에서 `user.timezone`을 고정하는 junit-platform 설정을 추가한다.

### IN-10: swagger-ui가 환경 구분 없이 노출된다

**File:** `src/main/resources/application.yml:65-71`
**Issue:** springdoc UI·api-docs가 프로파일 구분 없이 활성화되어 있고 현재 시큐리티는 permitAll이라, 이대로 배포되면 API 표면 전체가 공개된다. 시크릿은 없지만 공격 표면 정찰 자료가 된다. 배포는 M7이므로 지금은 정보성 지적이다.
**Fix:** 배포 전(늦어도 인증 phase)에서 prod 프로파일에 `springdoc.api-docs.enabled: false` / `springdoc.swagger-ui.enabled: false`를 넣거나 관리자 인증 뒤로 숨긴다.

---

_Reviewed: 2026-07-30T11:46:08Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
