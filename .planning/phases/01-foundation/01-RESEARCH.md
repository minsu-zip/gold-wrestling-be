# Phase 1: 기반 - Research

**Researched:** 2026-07-30
**Domain:** Spring Boot 4.1 전역 에러 처리(RFC 9457) · Flyway 초기 스키마 설계 · springdoc 기반 API 계약 재생성 파이프라인
**Confidence:** HIGH (핵심 메커니즘은 Context7 공식 문서로 검증) / MEDIUM (커스텀 gradle 태스크 구현 패턴은 일반 관례, Boot 4 전용 검증 자료 없음)

## Summary

Phase 1은 코드가 거의 없는 상태에서 세 가지 뼈대를 만드는 phase다. 셋 다 "이후 phase가 전제로 삼는" 기반이므로 여기서 잘못 잡으면 되돌리는 비용이 크다.

**FOUND-01(에러 응답)**의 핵심 발견은, Spring Boot 4.1(Framework 7)이 `spring.mvc.problemdetails.enabled=true`일 때 `ProblemDetailsExceptionHandler`라는 `@ControllerAdvice`(내부적으로 `ResponseEntityExceptionHandler`를 상속)를 **자동 등록**해서 검증 실패·404·405 같은 스프링 내장 예외를 이미 ProblemDetail로 바꿔준다는 것이다. 이 자동 빈은 `@ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)`로 조건화되어 있어서, **우리가 `ResponseEntityExceptionHandler`를 상속하는 커스텀 `@RestControllerAdvice`를 하나 만들면 자동 등록 빈은 비활성화되고 우리 클래스가 모든 내장 예외(검증 실패, 404, 405 등)까지 전부 가져간다.** 이게 D-06(커스텀 `code` 필드를 모든 에러 응답에 통일되게 붙이는 것)을 구현하는 유일하게 깔끔한 방법이다 — `handleExceptionInternal`을 오버라이드하면 스프링이 만든 내장 예외든 우리가 던진 도메인 예외든 같은 지점을 거치므로 거기서 `code` 프로퍼티를 얹으면 예외 없이 모든 에러가 같은 모양이 된다.

**FOUND-02(스키마)**는 특별한 기술적 함정은 없다. `docs/policies.md`·`glossary.md`·기존 skill(`add-migration`)에 규약이 이미 충분히 구체적으로 박혀 있으므로, 이 research의 역할은 "확실한 것만 넣는다"(D-04)는 제약 안에서 4개 테이블의 최소 컬럼을 구체적으로 제안하는 것이다.

**FOUND-03(openapi 파이프라인)**은 D-01/D-02로 이미 "gradle 플러그인 대신 커스텀 태스크"까지 정해져 있다. springdoc 3.0.3이 `/v3/api-docs.yaml` 엔드포인트를 기본 제공한다는 것은 Context7 공식 문서로 확인했다(HIGH). 다만 "앱을 백그라운드로 기동 → 대기 → 다운로드 → 종료"를 Gradle Kotlin DSL로 어떻게 구현할지는 Boot 4 전용 검증 자료가 없어 **일반적인 엔지니어링 패턴**(Exec 태스크 + bash 백그라운드 프로세스 + curl 폴링)으로 제시한다 — 이 부분은 MEDIUM/LOW로 표시하고 실행 단계에서 직접 검증이 필요하다.

**Primary recommendation:** `common/error/GlobalExceptionHandler`를 `ResponseEntityExceptionHandler` 상속 `@RestControllerAdvice`로 만들어 `handleExceptionInternal`에서 `code` 프로퍼티를 주입하고, `spring.mvc.problemdetails.enabled=true`는 켜두되(내장 예외 매핑이 이미 되어 있는지 회귀 확인용) 실제 응답은 우리 클래스가 전담하게 한다. 스키마는 4개 테이블을 단일 마이그레이션(V2)으로 묶고, openapi 파이프라인은 Exec 태스크 체인 + `finalizedBy`로 프로세스 정리를 보장한다.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 전역 에러 응답 변환(ProblemDetail) | API/Backend | — | Spring MVC의 `@RestControllerAdvice`는 컨트롤러 계층 횡단 관심사. DB·클라이언트 관여 없음 |
| 초기 스키마(Branch/Member/Admin/AdminBranch) | Database/Storage | API/Backend | 스키마는 DB 소유이지만 JPA 엔티티 매핑(선택)으로 API 계층과 즉시 연결됨 |
| API 계약 재생성 파이프라인 | Build Tooling (Gradle) | API/Backend | springdoc은 백엔드 앱 안에서 실행되지만, "한 명령" 오케스트레이션은 빌드 도구의 책임 |

## Project Constraints (from CLAUDE.md)

- Kotlin + Spring Boot 4.1.x(Spring Framework 7) 전용 — Boot 3 예제·지식 그대로 이식 금지. 의존성은 Boot 4 호환 버전만
- `springdoc-openapi`는 **3.x 라인 고정**(2.x는 Boot 3 전용) — 이미 `springdoc-openapi-starter-webmvc-ui:3.0.3` 적용됨
- DB 스키마 변경은 **오직 Flyway 마이그레이션**. `ddl-auto`는 `validate` 고정, 이미 커밋된 마이그레이션 파일 수정 금지
- 에러 응답은 RFC 9457 `ProblemDetail` 고정 — 커스텀 공통 래퍼(`ApiResponse<T>` 등) 금지
- 코드 포맷은 ktlint(`ktlint_official` 스타일, `.editorconfig` 단일 출처) — 작업 마지막 `ktlintFormat` → `build`
- 엔티티: `data class` 금지, `@ManyToOne(fetch = LAZY)` 명시, `@Enumerated(EnumType.STRING)` 필수
- 컨트롤러는 DTO만 주고받는다(엔티티 API 노출 금지) — Phase 1은 CRUD API가 없어 직접 해당 사항 없음, 단 Phase 2가 바로 이 규약 위에서 Member 엔티티를 노출하지 않고 DTO로 감쌀 것을 전제
- 서비스 클래스 `@Transactional(readOnly = true)` 기본, 컨트롤러·리포지토리에 `@Transactional` 금지 — Phase 1은 서비스 로직이 거의 없어 해당 사항 제한적
- 시간 타입: `LocalDate`/`LocalTime`/`OffsetDateTime`(`timestamptz`), `LocalDateTime`+`timestamp` 조합 금지. 현재 시각은 `Clock` 빈 주입
- 시크릿 실값 커밋 금지, `.env.example` 키 동기화
- 커밋·푸시는 사용자가 명시적으로 요청했을 때만. GSD 워크플로우가 자동 커밋을 요구해도 커밋 없이 멈추고 사용자에게 알린다
- 프로덕션 코드 변경 시 같은 작업 안에서 테스트 동반(`conventions.md` §10.0 표 기준), 면제 시 완료 보고에 한 줄로 사유 명시

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FOUND-01 | 모든 에러 응답이 RFC 9457 ProblemDetail로 반환 — 전역 예외 핸들러, 스프링 내장 에러(400/404/405) 포함 | `ResponseEntityExceptionHandler` 상속 패턴(HIGH, Context7 검증) + `handleExceptionInternal` 오버라이드로 `code` 프로퍼티 통일 주입(MEDIUM) |
| FOUND-02 | Flyway 초기 스키마 — Branch/Member/Admin/AdminBranch, 핵심 테이블 `branch_id` 확장 전제 | `add-migration` skill 규약 + policies §5.1(Member nullable 컬럼 근거) + glossary 네이밍 매핑 |
| FOUND-03 | 한 명령으로 `docs/api/openapi.yaml` 재생성 파이프라인 (springdoc 기반) | `/v3/api-docs.yaml` 엔드포인트(HIGH, Context7) + 커스텀 Gradle Exec 태스크 체인(MEDIUM, 일반 패턴) |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.1.0 | 애플리케이션 프레임워크 | 이미 결정됨(D-014), 프로젝트 전체 기준 |
| springdoc-openapi-starter-webmvc-ui | 3.0.3 | OpenAPI 3.1 스펙 생성 + Swagger UI | Boot 4 호환 라인의 최신 안정 버전. `maven-metadata.xml`로 `<release>3.0.3</release>` 확인 [VERIFIED: Maven Central] |
| Flyway (flyway-database-postgresql) | Boot 4.1 BOM 관리(12.4.0) | DB 마이그레이션 | Boot BOM이 관리하는 버전 그대로 사용, 버전 직접 명시 안 함(관례 준수) [VERIFIED: Maven Central — spring-boot-dependencies-4.1.0.pom `flyway.version=12.4.0`] |
| PostgreSQL | 18.4(Testcontainers 이미지) / 18(docker-compose) | 운영 DB | 기존 `TestcontainersConfiguration.kt`에서 이미 `postgres:18.4-alpine` 사용 중 — 신규 결정 아님 |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| testcontainers-postgresql | Boot 4.1 BOM 관리(testcontainers 2.0.5) | 마이그레이션 통합테스트 | 이미 배선 완료(`FlywayMigrationIntegrationTest`), 신규 마이그레이션 추가 시 자동 재생 검증 |

이번 phase에서 **신규 외부 패키지 추가는 없다.** 기존 `build.gradle.kts`의 의존성(springdoc, flyway, testcontainers 등)을 그대로 사용한다.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| 커스텀 `ResponseEntityExceptionHandler` 상속 | 자동 등록된 `ProblemDetailsExceptionHandler`만 쓰고 도메인 예외는 별도 `@ExceptionHandler`로 분리 | 내장 예외와 도메인 예외의 응답 모양(특히 `code` 필드 유무)이 갈라짐 — D-06("FE 분기는 code로만") 위반 위험. **채택하지 않음** |
| Flyway 단일 마이그레이션(V2)에 4개 테이블 모두 | 테이블당 별도 마이그레이션(V2~V5) | 4개 테이블이 서로 FK로 강하게 얽혀 있어(Member/Admin → Branch, AdminBranch → Admin+Branch) 분리해도 이득이 적고 순서 관리 부담만 늘어남. 단일 마이그레이션 권장하되 팀 취향이면 분리도 무방 |
| 커스텀 Gradle Exec 태스크 체인 | `springdoc-openapi-gradle-plugin` 1.9.0 | CONTEXT.md D-01에서 이미 기각(Boot 4.1 미해결 이슈 다수). 재검토 불필요 |

**Installation:** 신규 설치 없음. `springdoc.api-docs.path=/v3/api-docs`가 이미 `application.yml`에 설정되어 있고 `.yaml` 접미사로 YAML 응답을 받을 수 있다.

**Version verification:** 아래 두 값을 Maven Central에서 직접 조회해 확인함 (2026-07-30 기준):
```bash
curl -s https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml | grep '<release>'
# <release>3.0.3</release>
curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.0/spring-boot-dependencies-4.1.0.pom | grep flyway.version
# <flyway.version>12.4.0</flyway.version>
```

## Package Legitimacy Audit

이번 phase는 **신규 외부 패키지를 설치하지 않는다.** 기존 `build.gradle.kts`에 이미 존재하는 의존성만 사용하므로 Package Legitimacy Gate(slopcheck 등)는 해당 사항 없음.

**Packages removed due to slopcheck [SLOP] verdict:** none (신규 패키지 없음)
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
[클라이언트/Swagger UI/FE 코드생성기]
        │
        ▼ HTTP 요청 (모든 경로)
[DispatcherServlet]
        │
        ├─ 정상 매핑 성공 ──▶ [Controller] ──▶ 정상 응답
        │
        └─ 예외 발생 지점 (아래 중 하나) ─────────────┐
              - 존재하지 않는 경로(NoResourceFoundException)      │
              - 잘못된 HTTP 메서드(HttpRequestMethodNotSupportedException) │
              - 요청 바디 파싱 실패(HttpMessageNotReadableException)       │
              - @Valid 검증 실패(MethodArgumentNotValidException)          │
              - 도메인 예외(잔여부족·정원초과 등, 향후 phase)              │
                                                                            ▼
                                              [GlobalExceptionHandler]
                                              (ResponseEntityExceptionHandler 상속,
                                               @RestControllerAdvice)
                                                          │
                                              handleExceptionInternal() 오버라이드
                                              ── ProblemDetail에 `code` 프로퍼티 주입
                                                          │
                                                          ▼
                                    application/problem+json 응답 (RFC 9457)
                                    { type, title, status, detail, instance, code }

[Gradle "generateApiDocs" 태스크] (FOUND-03, 별도 트리거)
   1. bootRun을 백그라운드 프로세스로 기동
   2. /actuator/health 폴링 (UP 대기)
   3. GET /v3/api-docs.yaml → docs/api/openapi.yaml 저장
   4. 백그라운드 프로세스 종료 (finalizedBy, 실패해도 항상 실행)
```

### Recommended Project Structure
```
src/main/kotlin/com/goldwrestling/
├── common/
│   └── error/
│       ├── GlobalExceptionHandler.kt   # ResponseEntityExceptionHandler 상속
│       ├── ErrorCode.kt                # code enum (예: RESOURCE_NOT_FOUND 등 공통 코드)
│       └── DomainException.kt          # 향후 phase의 도메인 예외 기반 클래스
├── branch/
│   └── Branch.kt                       # 엔티티만 (컨트롤러 없음 — Branch API는 스코프 밖)
├── member/
│   └── Member.kt                       # nullable name/phone (D-05)
└── admin/
    ├── Admin.kt
    └── AdminBranch.kt                   # M2M 조인 엔티티(또는 @ManyToMany + @JoinTable)

src/main/resources/db/migration/
└── V2__create_branch_member_admin.sql   # Branch, Member, Admin, AdminBranch + 송파점 시드(D-09)

build.gradle.kts                          # generateApiDocs 커스텀 태스크 체인 추가
```

### Pattern 1: 전역 예외 핸들러가 내장 예외까지 흡수하는 구조
**What:** `ResponseEntityExceptionHandler`를 상속하는 `@RestControllerAdvice` 하나를 만들면, Boot의 자동 `ProblemDetailsExceptionHandler`(`@ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)`)가 비활성화되고 우리 클래스가 검증 실패·404·405를 포함한 모든 MVC 예외를 가져간다.
**When to use:** 이 프로젝트처럼 "내장 에러도 예외 없이 같은 포맷(+커스텀 code)"이 요구사항일 때. 여러 개의 독립된 `@ExceptionHandler`를 분산시키는 대신 단일 진입점에서 공통 로직(code 주입)을 강제한다.
**Example:**
```kotlin
// Source: Context7 — spring-projects/spring-boot,
// module/spring-boot-webmvc/.../ProblemDetailsExceptionHandler.java 구조를 참고해 재구성
package com.goldwrestling.common.error

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    // 도메인 예외(향후 phase에서 확장) — 여기서도 같은 handleExceptionInternal을 거치므로
    // code 주입 로직이 자동으로 적용된다.
    @ExceptionHandler(DomainException::class)
    fun handleDomain(ex: DomainException, request: WebRequest): ResponseEntity<Any> {
        val problem = ProblemDetail.forStatusAndDetail(ex.status, ex.message)
        return super.handleExceptionInternal(ex, problem, HttpHeaders(), ex.status, request)!!
    }

    // 스프링 내장 예외(검증 실패, 404, 405 등)도 결국 이 메서드를 통과한다.
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val problem =
            (body as? ProblemDetail)
                ?: ProblemDetail.forStatusAndDetail(statusCode, ex.message ?: "Unexpected error")
        problem.setProperty("code", resolveCode(ex))
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request)!!
    }

    private fun resolveCode(ex: Exception): String =
        (ex as? DomainException)?.errorCode ?: "INTERNAL_ERROR" // 매핑 표는 실행 시 구체화
}
```
**신뢰도:** 상속 구조·자동설정 조건(`@ConditionalOnMissingBean`)은 Context7 공식 소스로 확인(HIGH). 위 코드 스니펫 자체는 그 구조를 바탕으로 이번 세션에서 조립한 것이라 `handleExceptionInternal`의 정확한 오버로드 시그니처는 **Spring Framework 7 API 문서로 재확인 필요**(MEDIUM) — `verify-boot4-api` skill 절차(context7 재확인 → `compileKotlin`)를 반드시 거칠 것.

### Pattern 2: Flyway 마이그레이션에 시드 데이터 함께 넣기 (D-09)
**What:** 스키마 생성과 같은 마이그레이션 파일(또는 바로 다음 버전)에 송파점 1건을 `INSERT`로 시딩.
**When to use:** Branch 관리 API가 스코프 밖(D-09)이므로, 유일한 지점 데이터는 마이그레이션이 아니면 주입할 방법이 없다. Testcontainers가 마이그레이션을 처음부터 재생하므로 테스트 DB에도 동일 시드가 보장된다.
**Example:**
```sql
-- Source: CONTEXT.md D-09 + add-migration skill 규약을 조합
-- V2__create_branch_member_admin.sql
CREATE TABLE branch (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO branch (name) VALUES ('송파점');

CREATE TABLE member (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    name VARCHAR(50),                 -- 온보딩 전 nullable (D-05, policies §5.1)
    phone_number VARCHAR(20),         -- 온보딩 전 nullable
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- MemberStatus enum
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_member_branch FOREIGN KEY (branch_id) REFERENCES branch (id)
);

CREATE TABLE admin (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    -- 로그인 자격(ID/PW 해시)은 AUTH phase(Phase 2)에서 V+1로 추가 (D-04)
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE admin_branch (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    CONSTRAINT fk_admin_branch_admin FOREIGN KEY (admin_id) REFERENCES admin (id),
    CONSTRAINT fk_admin_branch_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT uq_admin_branch UNIQUE (admin_id, branch_id)
);

CREATE INDEX idx_member_branch ON member (branch_id);
```
**신뢰도:** MEDIUM — 규약(snake_case, IDENTITY PK, FK 명시)은 skill·conventions에서 직접 인용(CITED). 구체적 컬럼 목록(name 길이, status 기본값 등)은 이번 세션에서 조합한 제안이라 **플래너·사용자 확인 필요**(플래너 재량 영역, D-05 참고).

### Pattern 3: Gradle 커스텀 태스크 체인으로 "한 명령" openapi 재생성
**What:** `bootRun`을 백그라운드로 띄우는 태스크 → health 폴링 태스크 → yaml 다운로드 태스크 → 종료 태스크를 `dependsOn`/`finalizedBy`로 묶어 하나의 진입점(`generateApiDocs`)으로 노출.
**When to use:** D-01/D-02가 이미 확정한 방향. springdoc gradle 플러그인 없이 동일 효과를 내야 할 때.
**Example:**
```kotlin
// Source: 일반적인 Gradle Exec 태스크 패턴을 이 프로젝트에 맞게 조합 (공식 문서 원본 없음 — MEDIUM/LOW)
val appPidFile = layout.buildDirectory.file("run/app.pid")
val appLogFile = layout.buildDirectory.file("run/app.log")

tasks.register<Exec>("startAppForDocs") {
    dependsOn("bootJar")
    doFirst { layout.buildDirectory.dir("run").get().asFile.mkdirs() }
    commandLine(
        "bash", "-c",
        """
        nohup java -jar ${tasks.named("bootJar").get().outputs.files.singleFile} \
          --server.port=8080 > ${appLogFile.get().asFile} 2>&1 &
        echo ${'$'}! > ${appPidFile.get().asFile}
        """.trimIndent(),
    )
}

tasks.register<Exec>("waitForAppHealth") {
    dependsOn("startAppForDocs")
    commandLine(
        "bash", "-c",
        """
        for i in {1..30}; do
          curl -sf http://localhost:8080/actuator/health && exit 0
          sleep 1
        done
        echo "앱이 30초 안에 기동하지 않음" >&2
        exit 1
        """.trimIndent(),
    )
}

tasks.register<Exec>("downloadApiDocs") {
    dependsOn("waitForAppHealth")
    commandLine(
        "bash", "-c",
        "curl -sf http://localhost:8080/v3/api-docs.yaml -o docs/api/openapi.yaml",
    )
}

tasks.register<Exec>("stopAppForDocs") {
    commandLine(
        "bash", "-c",
        "kill \$(cat ${appPidFile.get().asFile}) 2>/dev/null || true",
    )
}

tasks.register("generateApiDocs") {
    dependsOn("downloadApiDocs")
    finalizedBy("stopAppForDocs")   // 다운로드가 실패해도 프로세스는 반드시 정리
}
```
**신뢰도:** LOW/MEDIUM — Exec 태스크·bash 백그라운딩·`finalizedBy` 정리 패턴 자체는 Gradle의 표준 기능이지만, 이 정확한 조합을 공식 문서나 Boot 4 사례로 검증하지는 못했다. **실행 단계에서 로컬로 직접 검증 필수** (`docker compose up -d` 전제, D-03). Windows 미지원(bash 의존) — 이 프로젝트는 로컬 개발 환경이 macOS/Linux로 보이므로 문제없다고 가정하지만, 확인되지 않았다면 플래너가 확인 질문을 남겨야 한다.

### Anti-Patterns to Avoid
- **`@ExceptionHandler`를 컨트롤러마다 개별 배치:** 도메인 예외 종류가 늘어날수록(Phase 3~4에서 급증) 응답 모양이 파편화된다. 반드시 전역 하나로 통일.
- **`ProblemDetail`을 손으로 `Map`처럼 직렬화:** 스프링이 이미 `application/problem+json` 컨텐트 타입과 표준 필드 직렬화를 처리한다. 커스텀 직렬화를 만들면 D-017(표준 스키마 채택 이유)의 이점이 사라진다.
- **엔티티에 `data class` 사용:** 지연 로딩 프록시와 충돌(conventions §3). Branch/Member/Admin/AdminBranch 전부 일반 `class`로.
- **`@Enumerated(EnumType.ORDINAL)`:** MemberStatus 등 enum 순서가 바뀌면 데이터가 깨진다. 반드시 `STRING`.
- **마이그레이션 파일을 여러 번 고쳐가며 반복 실행:** 이미 로컬에 적용된 V2를 수정하면 체크섬 불일치로 기동이 막힌다. 틀렸으면 V3을 추가.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 에러 응답 표준화 | 커스텀 `ApiResponse<T>`/`ErrorResponse` 래퍼 | Spring 내장 `ProblemDetail` + `ResponseEntityExceptionHandler` | D-017에서 이미 기각된 대안. 표준 스키마라 springdoc·FE 생성기가 그대로 처리 |
| OpenAPI 스펙 생성 | 수작업으로 openapi.yaml 편집 | springdoc 런타임 introspection(`/v3/api-docs.yaml`) | 컨트롤러·DTO가 바뀔 때마다 수작업 동기화는 필연적으로 드리프트된다 |
| 앱 기동 대기 로직 | 임의 `sleep N` | `/actuator/health` 폴링 루프 | 콜드 스타트 시간이 환경마다 다르다. 고정 sleep은 느린 환경에서 실패, 빠른 환경에서 낭비 |

**Key insight:** Phase 1은 "미래 phase가 반복해서 쓸 패턴"을 만드는 단계다. 여기서 손으로 만든 임시방편(래퍼, 수작업 스펙 편집, 고정 sleep)이 하나라도 들어가면 이후 5개 phase가 그 위에 쌓이면서 고치기 어려워진다.

## Common Pitfalls

### Pitfall 1: `spring.mvc.problemdetails.enabled`만 켜고 끝냈다고 착각
**What goes wrong:** 이 프로퍼티를 켜면 검증 실패 등 일부는 이미 ProblemDetail로 나온다. 그런데 커스텀 `code` 필드가 빠진 "표준 ProblemDetail"과, 우리가 만든 `code` 포함 응답이 섞여서 FE가 어떤 응답엔 code가 있고 어떤 응답엔 없는 상황이 생긴다.
**Why it happens:** 자동 등록된 `ProblemDetailsExceptionHandler`와 우리가 만든 `@RestControllerAdvice`가 동시에 등록되면(둘 다 `ResponseEntityExceptionHandler`가 아니라 별도 `@ExceptionHandler` 모음으로 만들었을 경우) 어떤 예외가 어느 핸들러로 가는지 예측하기 어려워진다.
**How to avoid:** 우리 핸들러를 **반드시 `ResponseEntityExceptionHandler` 상속**으로 만들어 자동 등록 빈을 완전히 대체한다(`@ConditionalOnMissingBean` 조건 활용). 두 핸들러가 공존하지 않도록 한다.
**Warning signs:** 통합테스트에서 404 응답 바디에 `code`가 없는데 도메인 예외 응답에는 있는 경우.

### Pitfall 2: 정적 리소스 404가 `/error`로 새서 ProblemDetail을 우회
**What goes wrong:** Boot 3.2 이전 관례로 알고 있으면 "존재하지 않는 경로는 `BasicErrorController`(`/error`)로 포워딩된다"고 가정하기 쉽다. 이 경우 `application.yml`의 `server.error.*` 설정만 건드리고 끝내면, 우리 `@RestControllerAdvice`를 거치지 않는 별도 JSON 포맷(`ProblemDetail`이 아닌)이 나온다.
**Why it happens:** Spring Framework 6.1(Boot 3.2)부터 매핑되지 않은 경로는 기본적으로 `NoResourceFoundException`을 던지도록 바뀌었고, 이 예외는 `ResponseEntityExceptionHandler`가 처리하는 예외 목록에 포함된다 — 즉 `/error` 포워딩 없이 우리 핸들러가 바로 받는다. 하지만 **이 동작을 검증하지 않고 넘어가면** 정적 리소스 핸들링을 끄거나(`spring.web.resources.add-mappings=false`) 커스텀 `HandlerMapping`을 추가했을 때 다시 `/error`로 새는 경우가 생길 수 있다.
**How to avoid:** FOUND-01 완료 조건에 반드시 "존재하지 않는 경로에 대한 GET 요청"을 통합테스트로 포함시켜 실제 응답 컨텐트 타입이 `application/problem+json`인지 확인한다.
**Warning signs:** 존재하지 않는 경로 요청 시 `text/html`(Whitelabel Error Page)이나 다른 JSON 포맷이 반환됨.

### Pitfall 3: springdoc이 생성한 openapi.yaml에 에러 응답 스키마가 안 보임
**What goes wrong:** `docs/api/openapi.yaml`을 열어봐도 `ProblemDetail`이나 `code` 필드에 대한 스키마 정의가 없다 — springdoc이 이걸 자동으로 넣어주지 않는 것을 "버그"로 오인.
**Why it happens:** springdoc은 각 `@RequestMapping` 메서드의 리턴 타입을 스캔해 `responses`를 만든다. `@RestControllerAdvice`의 예외 핸들러는 특정 경로에 묶여 있지 않으므로 springdoc이 자동으로 각 엔드포인트의 에러 응답에 이 스키마를 추가하지 않는다(수동으로 모든 엔드포인트에 `@ApiResponse`를 달아야 하는데, 이는 반복 작업이 크다).
**How to avoid:** 이미 CONTEXT.md D-07에서 해결됨 — 에러코드는 openapi.yaml이 아니라 별도 `docs/error-codes.md`로 계약 관리한다. 이 설계가 옳았다는 것을 research로 재확인. 굳이 openapi.yaml에 에러 스키마를 강제로 넣으려 하지 않는다(과한 작업 대비 이득 적음).
**Warning signs:** 없음 — 이건 "고칠 문제"가 아니라 "확인된 설계"다.

### Pitfall 4: Flyway 마이그레이션에서 `branch_id`를 Member에 안 넣고 나중에 추가하려다 기존 데이터 이슈
**What goes wrong:** FOUND-02 성공 기준이 "핵심 테이블에 `branch_id`를 둘 수 있는 구조"라고만 되어 있어, Member 테이블 자체에는 `branch_id`를 빼고 넘어가는 실수가 가능하다. 나중에(Phase 2~4에서) 추가하면 기존 회원 레코드에 `NOT NULL` 컬럼을 넣는 마이그레이션이 필요해진다.
**Why it happens:** "확장 전제"라는 표현을 "지금 당장은 안 넣어도 된다"로 오독하기 쉽다.
**How to avoid:** `requirements.md` §1이 명확히 "모든 핵심 엔티티에 `branch_id`를 둔다"고 명시하므로, Member는 **Phase 1에서부터 `branch_id NOT NULL` FK를 갖는다** (MVP는 항상 송파점 하나뿐이므로 값 채우기는 트리비얼). Admin은 `AdminBranch` M2M으로 이미 커버되므로 Admin 자체에 `branch_id` 컬럼은 불필요.
**Warning signs:** Member 마이그레이션에 `branch_id` 컬럼이 없음.

## Code Examples

### 통합테스트로 내장 에러(404) 형식 확인
```kotlin
// Source: 기존 FlywayMigrationIntegrationTest.kt의 애노테이션 조합을 그대로 재사용 (컨텍스트 캐시 유지)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class GlobalExceptionHandlerTest {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `존재하지 않는 경로는 problem+json 으로 응답한다`() {
        val response = restTemplate.getForEntity("/api/nonexistent", String::class.java)

        assertThat(response.statusCode.value()).isEqualTo(404)
        assertThat(response.headers.contentType.toString()).contains("application/problem+json")
    }

    @Test
    fun `허용되지 않은 메서드는 405 problem+json 으로 응답한다`() {
        val response = restTemplate.postForEntity("/api/system/health", null, String::class.java)

        assertThat(response.statusCode.value()).isEqualTo(405)
        assertThat(response.headers.contentType.toString()).contains("application/problem+json")
    }
}
```
**주의:** 기존 통합테스트(`FlywayMigrationIntegrationTest`, `HealthControllerTest`)와 **애노테이션 조합을 통일**해야 컨텍스트 캐시가 재사용된다 — 조합이 다르면 스프링 컨텍스트가 새로 뜨며 전체 테스트가 느려진다(conventions §10.1). 실제 조합은 기존 두 테스트 파일을 먼저 확인 후 맞출 것.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| 매핑 안 된 경로 → `/error`(`BasicErrorController`) 포워딩 | `NoResourceFoundException`을 던져 일반 예외 처리 경로로 통합 | Spring Framework 6.1 / Boot 3.2 | 커스텀 `ErrorController` 없이도 `@RestControllerAdvice`에서 404를 잡을 수 있게 됨 — 이번 phase의 핵심 전제 |
| 수동 `@RestControllerAdvice` + 개별 `@ExceptionHandler` 나열 | `spring.mvc.problemdetails.enabled` + 자동 `ProblemDetailsExceptionHandler` | Boot 3.2+ (Boot 4.1까지 유지) | 기본 제공되는 걸 우리가 상속해서 확장하는 게 표준 경로가 됨. 재발명할 필요 없음 |

**Deprecated/outdated:** 없음 — Phase 1 범위 안에서 명시적으로 폐기된 API는 발견되지 않음.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `handleExceptionInternal`의 정확한 파라미터 시그니처(Kotlin에서의 nullable 처리 포함)가 Spring Framework 7에서도 Boot 3 시절과 동일하다 | Architecture Patterns > Pattern 1 | 컴파일 에러 또는 오버라이드 실패 — `verify-boot4-api` skill 절차(context7 재확인 → `compileKotlin`)로 실행 단계에서 반드시 재검증 |
| A2 | Gradle Exec 태스크 체인(백그라운드 프로세스 기동 → curl 폴링 → 다운로드 → kill)이 이 프로젝트의 로컬 개발 환경(macOS로 추정)에서 bash 의존 없이도 동작 가능하다는 전제 | Architecture Patterns > Pattern 3 | Windows 환경이면 bash 스크립트가 깨진다. 사용자 로컬 환경이 macOS/Linux인지 확인 필요(현재 세션 환경은 Darwin으로 확인되었으나, 이것이 실제 개발 머신이라는 보장은 별도 확인 필요) |
| A3 | Member/Admin/AdminBranch의 구체적 컬럼 목록(길이 제한, 기본값 등)이 정책 문서에 명시되지 않은 부분은 이번 세션에서 합리적으로 제안한 것이며 확정된 결정이 아니다 | Architecture Patterns > Pattern 2 | 컬럼 스펙이 실제 운영 요구(예: 이름 50자 제한이 실제로 충분한지)와 다르면 다음 phase에서 컬럼 크기 변경 마이그레이션이 필요해질 수 있음. 리스크는 낮음(변경 비용이 작음) |
| A4 | Admin 테이블에 로그인 자격(ID/PW 해시)을 이번 phase에 넣지 않고 Phase 2에서 V+1로 추가한다는 해석(D-04 "최소 스키마" 준수) | Architecture Patterns > Pattern 2 | D-04가 "확실한 정체성 컬럼만"이라고 했으므로 낮은 리스크. 다만 플래너가 "Admin은 로그인이 안 되는 상태로 시드만 넣는 게 맞나?"를 확인 질문으로 남길 가치가 있음 |

## Open Questions (RESOLVED)

> 아래 3건은 모두 플래닝 단계에서 종결되었다. 각 항목의 `RESOLVED:` 줄이 어느 플랜이 어떻게 결론지었는지 가리킨다.
> 내용은 조사 당시 기록을 그대로 보존하고, 결론만 덧붙였다.

1. **Gradle 태스크 체인이 CI 없는 로컬 전용이라는 전제가 맞는가?**
   - What we know: D-03에서 "CI에서의 스펙 검증은 배포 단계(M7)에서 고려"라고 명시함 — 즉 이번 phase는 로컬 실행만 보장하면 된다.
   - What's unclear: 로컬 개발 환경이 실제로 bash를 지원하는 셸(macOS/Linux)인지, 아니면 Windows(WSL 없이)인지.
   - Recommendation: 플랜 단계에서 "로컬 개발 OS가 macOS/Linux인가?"를 확인 질문으로 남기거나, 이미 CLAUDE.md·기존 대화에서 macOS로 확인된 것으로 보고(현재 세션 환경이 Darwin) 그대로 진행.
   - **RESOLVED:** 권고안 채택 — Darwin(macOS) 기준 bash/curl 태스크로 확정. `01-03-PLAN.md` `<interfaces>`에 "Docker·Gradle 9.6.1·bash·curl 사용 가능(Darwin), Windows 대응은 스코프 밖"으로 명시했고, CI 검증은 D-03에 따라 M7로 이연했다.

2. **`handleExceptionInternal`을 오버라이드했을 때 `WebExchangeBindException`(WebFlux) 같은 리액티브 전용 예외까지 신경 써야 하는가?**
   - What we know: 이 프로젝트는 `spring-boot-starter-webmvc`(서블릿 스택)만 쓴다.
   - What's unclear: 딱히 없음 — WebFlux는 전혀 쓰지 않으므로 무관.
   - Recommendation: 무시해도 된다. (기록만 남김 — 확인 과정에서 나온 잠재적 혼동 지점)
   - **RESOLVED:** 권고안 채택 — 무관 처리. `01-01-PLAN.md` Task 2의 예외→`ErrorCode` 매핑 목록은 서블릿 스택 예외(`MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `NoResourceFoundException`, `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`)만 다루고 WebFlux 전용 예외를 포함하지 않는다.

3. **AdminBranch 조인 테이블에 서로게이트 PK(`id`)를 둘지, 복합 PK(`admin_id, branch_id`)로 할지**
   - What we know: `add-migration` skill은 "PK: id BIGINT GENERATED BY DEFAULT AS IDENTITY"를 모든 테이블의 기본 관례로 제시.
   - What's unclear: 순수 M2M 조인 테이블에도 이 관례를 그대로 적용할지, 복합 PK가 더 관용적인지는 정책 문서에 명시 없음.
   - Recommendation: 관례 일관성을 위해 서로게이트 PK + `UNIQUE(admin_id, branch_id)` 제약을 권장(위 Pattern 2 예시). 플래너 재량(CONTEXT.md에 명시됨).
   - **RESOLVED:** 권고안 채택 — `01-02-PLAN.md` Task 1의 `admin_branch` 스펙이 서로게이트 PK(`id` identity) + `CONSTRAINT uq_admin_branch UNIQUE (admin_id, branch_id)` + `idx_admin_branch_branch`로 확정. 선택 이유를 마이그레이션 SQL 주석 한 줄로 남기도록 지시했고, 중복 권한 부여 거부는 T-01-08 완화 수단으로도 등록됐다.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker / docker-compose | Testcontainers 통합테스트, 로컬 Postgres(D-03) | ✓ | 로컬 확인됨(`docker info` 정상) | — |
| Gradle Wrapper | 빌드·테스트·커스텀 태스크 | ✓ | 9.6.1 (`gradle-wrapper.properties`) | — |
| bash | FOUND-03 커스텀 Exec 태스크 | ✓ (세션 환경 Darwin 기준) | — | 대상 개발 머신이 Windows라면 PowerShell 스크립트로 재작성 필요(A2 참고) |
| curl | health 폴링·yaml 다운로드 | ✓ (Darwin/Linux 기본 포함) | — | 미설치 환경이면 `wget`으로 대체 |

**Missing dependencies with no fallback:** 없음
**Missing dependencies with fallback:** bash 스크립트(Windows 환경일 경우 재작성 필요, A2 참고)

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5(`kotlin-test-junit5`) + AssertJ + Testcontainers(`testcontainers-postgresql`, `spring-boot-testcontainers`) |
| Config file | 없음 — `build.gradle.kts`의 `tasks.withType<Test>` 블록이 유일한 설정(`user.timezone=Asia/Seoul`) |
| Quick run command | `./gradlew test --tests "com.goldwrestling.common.error.*"` (신규 패키지 기준) |
| Full suite command | `./gradlew build` (ktlintCheck + compile + test 전부 포함) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FOUND-01 | 존재하지 않는 경로 → 404 problem+json | integration | `./gradlew test --tests "*GlobalExceptionHandlerTest*"` | ❌ Wave 0 (신규) |
| FOUND-01 | 잘못된 메서드 → 405 problem+json | integration | `./gradlew test --tests "*GlobalExceptionHandlerTest*"` | ❌ Wave 0 (신규) |
| FOUND-01 | 검증 실패(`@Valid`) → 400 problem+json + code | integration | 위와 동일 클래스에 케이스 추가 | ❌ Wave 0 (신규, 검증 대상 엔드포인트 필요 — 없으면 임시 테스트용 컨트롤러 고려) |
| FOUND-02 | 마이그레이션이 실제 DB에 적용되고 엔티티 매핑과 일치 | integration (기존 배선 재사용) | `./gradlew test --tests "*FlywayMigrationIntegrationTest*"` | ✅ (기존 파일, 검증 케이스만 늘어남 — "2" 버전이 기록되는지 등) |
| FOUND-03 | openapi.yaml이 실제로 재생성되고 유효한 스펙인지 | manual | `./gradlew generateApiDocs` 실행 후 `git diff docs/api/openapi.yaml` 육안 확인 | — (자동화 대상 아님, add-endpoint skill과 동일하게 수동 검증) |

### Sampling Rate
- **Per task commit:** 관련 패키지만 `--tests` 필터로 빠르게
- **Per wave merge:** `./gradlew build` (ktlintCheck + 전체 테스트)
- **Phase gate:** `./gradlew build` 통과 + `generateApiDocs` 수동 1회 실행 확인 후 `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt` — FOUND-01 커버 (신규 작성 필요)
- [ ] 검증 실패(400) 케이스를 실제로 트리거할 엔드포인트가 이 phase엔 아직 없음 — `HealthController`에 임시 검증 파라미터를 추가하거나, 최소한의 테스트 전용 컨트롤러를 `src/test`에 둘지 플래너가 결정 필요
- 프레임워크 자체 설치는 이미 완료(기존 `FlywayMigrationIntegrationTest`, `HealthControllerTest`가 증명)

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Phase 1은 인증 없음(SecurityConfig permitAll 유지) — Phase 2 스코프 |
| V3 Session Management | no | STATELESS + JWT는 Phase 2 |
| V4 Access Control | no | 인가 규칙은 Phase 2에서 permitAll을 교체 |
| V5 Input Validation | yes | `jakarta.validation`(`@NotNull` 등) — 단 이번 phase는 검증 대상 엔드포인트가 거의 없음(향후 phase가 본격 적용) |
| V6 Cryptography | no | 이번 phase엔 비밀번호·토큰 저장 없음(Admin 자격은 Phase 2) |
| V7 Error Handling & Logging | yes | 이번 phase 핵심 — `server.error.include-message: never`, `include-stacktrace: never` 이미 설정됨. `ProblemDetail`의 `detail` 필드에 내부 예외 메시지·SQL·스택트레이스를 넣지 않는다(conventions §8) |

### Known Threat Patterns for {stack}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| 에러 응답에 스택트레이스·SQL·내부 클래스명 노출 | Information Disclosure | `ProblemDetail`의 `detail`은 사용자 대면 메시지만, 원인 예외는 서버 로그에만(D-017, conventions §8) |
| 도메인 예외 미포착 시 500 응답에 예외 클래스명 노출 | Information Disclosure | `GlobalExceptionHandler`에 포괄 `Exception::class` 핸들러를 두어 예상 못 한 예외도 안전한 `INTERNAL_ERROR` code로 통일 응답 |

## Sources

### Primary (HIGH confidence)
- Context7 `/spring-projects/spring-boot` — `ProblemDetailsExceptionHandler` 자동설정 조건(`@ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)`), `spring.mvc.problemdetails.enabled` 동작
- Context7 `/springdoc/springdoc-openapi` — `/v3/api-docs.yaml` 엔드포인트, `springdoc.api-docs.path` 설정
- Maven Central `maven-metadata.xml` — `springdoc-openapi-starter-webmvc-ui` 최신 릴리스 `3.0.3` 실측 조회
- Maven Central `spring-boot-dependencies-4.1.0.pom` — Flyway BOM 관리 버전(`12.4.0`), `spring-boot-starter-webmvc` 좌표 확인

### Secondary (MEDIUM confidence)
- `ResponseEntityExceptionHandler.handleExceptionInternal` 오버라이드 패턴 — Spring Framework의 널리 알려진 확장 지점이나 이번 세션에서 Framework 7 전용 문서로 시그니처를 재확인하지 못함(`verify-boot4-api` skill로 실행 단계 재검증 필요)

### Tertiary (LOW confidence)
- Gradle Exec 태스크 체인(백그라운드 프로세스 기동/폴링/종료) — WebSearch로 springdoc-gradle-plugin의 내부 동작 설명은 확인했으나(이미 D-01로 기각된 도구), 이를 대체하는 커스텀 태스크 구현 자체는 공식 예시가 아닌 이번 세션의 조합. 실행 단계에서 직접 검증 필수

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 버전은 Maven Central에서 직접 조회, 신규 패키지 없음
- Architecture (에러 처리): HIGH(구조) / MEDIUM(정확한 코드 시그니처) — 자동설정 조건은 공식 소스 확인, 세부 오버라이드 API는 실행 단계 재검증 필요
- Architecture (openapi 파이프라인): MEDIUM(엔드포인트) / LOW(gradle 태스크 구현) — 엔드포인트는 검증됨, 태스크 오케스트레이션은 일반 패턴 제안
- Pitfalls: MEDIUM — Boot 3.2의 `NoResourceFoundException` 변경은 잘 알려진 사실이나 이번 세션에서 Framework 7 릴리스 노트로 재확인하지 못함(훈련 지식 기반)

**Research date:** 2026-07-30
**Valid until:** 2026-08-29 (30일 — 안정적 스택이나 Boot 4.1이 상대적으로 신규 릴리스라 마이너 패치 추적 필요)
</content>
