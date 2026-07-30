# Phase 1: 기반 - Pattern Map

**Mapped:** 2026-07-30
**Files analyzed:** 11
**Analogs found:** 6 / 11 (나머지는 이 프로젝트에 처음 등장하는 종류의 코드라 analog 없음 — `docs/conventions.md`/skill 템플릿을 표준으로 사용)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt` | middleware (exception handler) | request-response | 없음 (이 프로젝트 최초의 `@RestControllerAdvice`) | no analog — RESEARCH.md Pattern 1 코드가 유일한 근거, `verify-boot4-api` 스킬로 시그니처 재검증 필수 |
| `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt` | utility (enum) | transform | `glossary.md`의 `MemberStatus`/`TransactionReason` enum 정의 (문서, 코드 아님) | no analog — 코드 enum 관례는 conventions §3 `PassStatus` 참조 예시로 유추 |
| `src/main/kotlin/com/goldwrestling/common/error/DomainException.kt` | model (exception base) | transform | 없음 | no analog — RESEARCH.md Pattern 1의 `DomainException` 스니펫이 유일한 출발점 |
| `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt` | test | request-response | `src/test/kotlin/com/goldwrestling/system/HealthControllerTest.kt` | 강한 analog (애노테이션 조합·MockMvc 패턴 그대로 재사용 가능) |
| `src/main/resources/db/migration/V2__create_branch_member_admin.sql` | migration | CRUD (schema) | `src/main/resources/db/migration/V1__baseline.sql` (파일 헤더 주석 스타일만) + `.claude/skills/add-migration/SKILL.md` §2/§3 (컬럼 규약) | role-match (V1은 no-op이라 스키마 내용 자체의 analog는 아님) |
| `src/main/kotlin/com/goldwrestling/branch/Branch.kt` | model (JPA entity) | CRUD | 없음 (엔티티 자체가 이 프로젝트에 아직 하나도 없음) | no analog — `docs/conventions.md` §3 `SessionPass` 예시가 표준 템플릿 |
| `src/main/kotlin/com/goldwrestling/member/Member.kt` | model (JPA entity) | CRUD | 없음, 위와 동일 | no analog — conventions §3 템플릿 + D-05(nullable name/phone) 반영 |
| `src/main/kotlin/com/goldwrestling/admin/Admin.kt` | model (JPA entity) | CRUD | 없음, 위와 동일 | no analog |
| `src/main/kotlin/com/goldwrestling/admin/AdminBranch.kt` | model (JPA entity, M2M join) | CRUD | 없음, 위와 동일 | no analog |
| `build.gradle.kts` (`generateApiDocs` 태스크 체인 추가) | config (build tooling) | batch | 같은 파일의 `tasks.withType<Test>`, `tasks.named<BootRun>("bootRun")` 블록 | partial match (같은 파일 내 태스크 등록 스타일만 참고, Exec 체인 자체는 RESEARCH.md Pattern 3이 유일 출처) |
| `docs/decisions.md` (신규 D-0XX 항목) | doc | — | `docs/decisions.md` D-017 항목 (ProblemDetail 결정) | 강한 analog (포맷 그대로) |

## Pattern Assignments

### `src/main/kotlin/com/goldwrestling/common/error/GlobalExceptionHandler.kt` (middleware, request-response)

**Analog:** 없음 — `common/error` 패키지 자체가 이 phase에서 처음 생긴다. 대신 프로젝트 안에서 "요청을 받아 응답을 만드는" 기존 코드(`HealthController.kt`)의 패키지·임포트 관례와, RESEARCH.md Pattern 1의 구조를 결합해서 작성한다.

**기존 컨트롤러의 임포트/패키지 관례** (`src/main/kotlin/com/goldwrestling/system/HealthController.kt` lines 1-9):
```kotlin
package com.goldwrestling.system

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
```
→ `common/error`도 같은 스타일: 패키지 선언 → 외부 라이브러리 임포트(알파벳/그룹 순, 사이 주석 금지 — ktlint 자동 정렬).

**핵심 구조 (RESEARCH.md Pattern 1, 그대로 채택 — 단 실행 전 `verify-boot4-api` 스킬로 시그니처 재확인)**:
```kotlin
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

    @ExceptionHandler(DomainException::class)
    fun handleDomain(ex: DomainException, request: WebRequest): ResponseEntity<Any> {
        val problem = ProblemDetail.forStatusAndDetail(ex.status, ex.message)
        return super.handleExceptionInternal(ex, problem, HttpHeaders(), ex.status, request)!!
    }

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
        (ex as? DomainException)?.errorCode ?: "INTERNAL_ERROR"
}
```

**주의 (conventions.md §8, CLAUDE.md 규칙 4):**
- 커스텀 공통 래퍼(`ApiResponse<T>` 등) 절대 금지 — `ProblemDetail` 그대로 반환
- `detail`에 스택트레이스·SQL·예외 클래스명 노출 금지 (Security Domain V7)
- 컨트롤러에서 `try-catch`로 에러 응답을 만들지 않는다 — 전역 핸들러 하나로 통일

---

### `src/main/kotlin/com/goldwrestling/common/error/ErrorCode.kt` (utility/enum)

**Analog:** 없음. 이 프로젝트에 아직 코드 enum이 없다(entity 자체가 없어 `@Enumerated(EnumType.STRING)` 예시조차 conventions.md의 문서 스니펫뿐).

**참고할 문서상 enum 관례** (`docs/glossary.md` lines 33-46, `MemberStatus`/`TransactionReason`):
```
PENDING(승인대기) / ACTIVE(활성) / ON_LEAVE(휴회) / INACTIVE(비활성)

ADMIN_ADJUST / EVENING_HALF / INACTIVITY / CLASS_CANCELED_REFUND
```
→ 대문자 스네이크 문자열 상수(D-06 "`RESERVATION_FULL` 같은 대문자 스네이크"와 동일 컨벤션). Kotlin `enum class`로 선언하고, `@Enumerated(EnumType.STRING)`과 동일하게 **DB/JSON에는 항상 name 문자열**로 노출한다(ordinal 금지 원칙과 동일 정신).

**주의:** 이번 phase는 공통 에러코드(`INTERNAL_ERROR`, `VALIDATION_FAILED`, `RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED` 등)만 정의한다. 도메인 에러코드(`RESERVATION_FULL` 등)는 해당 도메인이 생기는 이후 phase에서 추가 — `docs/error-codes.md`(D-07)에도 함께 기록해야 한다.

---

### `src/main/kotlin/com/goldwrestling/common/error/DomainException.kt` (model, exception base)

**Analog:** 없음. RESEARCH.md Pattern 1 스니펫에서 `ex.status`, `ex.message`, `ex.errorCode` 프로퍼티를 참조하고 있으므로, 최소한 아래 형태가 필요하다(플래너가 구체화):
```kotlin
package com.goldwrestling.common.error

import org.springframework.http.HttpStatusCode

abstract class DomainException(
    val status: HttpStatusCode,
    val errorCode: String,
    message: String,
) : RuntimeException(message)
```
**주의:** `data class` 금지 규칙은 엔티티 한정이지만, 예외 클래스도 이 프로젝트에서 `data class`로 만들 이유가 없다(상속·메시지 체인과 궁합이 나쁨) — 일반 `class`/`abstract class` 유지.

---

### `src/test/kotlin/com/goldwrestling/common/error/GlobalExceptionHandlerTest.kt` (test, request-response)

**Analog:** `src/test/kotlin/com/goldwrestling/system/HealthControllerTest.kt` (전체 35줄, 강한 analog)

**애노테이션 조합 — 반드시 그대로 재사용** (lines 1-23, conventions §10.1 "애노테이션 조합을 통일해야 컨텍스트 캐시가 재사용된다"):
```kotlin
package com.goldwrestling.system

import com.goldwrestling.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class HealthControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `헬스 엔드포인트는 인증 없이 접근 가능하고 기준 시간대를 반환한다`() {
        mockMvc
            .perform(get("/api/system/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
    }
}
```

**적용 시 바꿀 것:** `get("/api/system/health")` → `get("/api/nonexistent")`(404 케이스), `post("/api/system/health", ...)`(405 케이스), 검증 실패(400) 케이스는 RESEARCH.md가 지적한 대로 트리거할 엔드포인트가 없으므로 플래너가 결정 필요(임시 테스트용 컨트롤러 또는 `HealthController`에 검증 파라미터 추가).

**단언 방식 변경:** `status().isOk` → `status().is4xxClientError()`류 + `jsonPath("$.code").value("...")`, `content().contentType("application/problem+json")` 확인 (RESEARCH.md Code Examples 섹션 예시대로 `TestRestTemplate`로 컨텐트 타입까지 확인해도 됨 — 단 그 경우 `@AutoConfigureMockMvc` 대신 `RANDOM_PORT` 조합이 되어 컨텍스트가 분리되므로, **MockMvc 방식(HealthControllerTest와 동일 조합) 우선 권장**).

---

### `src/main/resources/db/migration/V2__create_branch_member_admin.sql` (migration, CRUD/schema)

**Analog:** `src/main/resources/db/migration/V1__baseline.sql` (파일 헤더 주석 스타일 참고용, 전체 4줄 — 내용 자체는 no-op이라 스키마 analog 아님)

**V1 헤더 주석 스타일** (lines 1-4):
```sql
-- V1: 베이스라인.
-- 스키마의 유일한 주체는 Flyway 이며(ddl-auto=validate), 실제 테이블은 도메인 설계 확정 후 V2 부터 추가한다.
-- 이 마이그레이션은 Flyway 배선(flyway_schema_history 생성)만 확정하는 no-op 이다.
SELECT 1;
```
→ V2도 파일 최상단에 목적을 한글 주석으로 명시하는 스타일 유지.

**스키마 내용은 analog 없음 — `add-migration` 스킬 §2/§3 + RESEARCH.md Pattern 2가 표준**:
```sql
CREATE TABLE branch (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO branch (name) VALUES ('송파점');

CREATE TABLE member (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    name VARCHAR(50),
    phone_number VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_member_branch FOREIGN KEY (branch_id) REFERENCES branch (id)
);

CREATE INDEX idx_member_branch ON member (branch_id);
```

**필수 확인 사항 (add-migration 스킬 절대 금지 항목):**
- 번호는 건너뛰지 않고 순차 — 현재 최신은 V1이므로 다음은 반드시 V2
- 이미 커밋된 V1은 절대 수정하지 않는다
- `NOT NULL` 여부를 엔티티 Kotlin nullability와 반드시 일치 (Member.name/phoneNumber는 nullable — D-05)
- 작성 후 `./gradlew build`로 Testcontainers 재생 검증 (`FlywayMigrationIntegrationTest`가 자동으로 버전 "2" 존재를 검증하도록 케이스 추가 권장)

---

### `src/main/kotlin/com/goldwrestling/branch/Branch.kt`, `member/Member.kt`, `admin/Admin.kt`, `admin/AdminBranch.kt` (model, JPA entity, CRUD)

**Analog:** 없음 — 코드베이스에 JPA 엔티티가 아직 하나도 없다. `docs/conventions.md` §3의 `SessionPass` 예시가 유일하고 권위 있는 템플릿이다(RESEARCH.md도 동일하게 인용).

**표준 템플릿** (`docs/conventions.md` lines 47-65):
```kotlin
@Entity
@Table(name = "session_pass")
class SessionPass(                          // data class 금지
    @ManyToOne(fetch = FetchType.LAZY)      // 기본값이 EAGER라 반드시 명시
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Column(name = "remaining_count", nullable = false, precision = 4, scale = 1)
    var remainingCount: BigDecimal,         // 변경되는 필드만 var

    @Enumerated(EnumType.STRING)            // ORDINAL 금지
    @Column(nullable = false, length = 20)
    var status: PassStatus,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null                    // 저장 전에는 null
}
```

**Member 적용 시 차이점 (D-05):** `name`, `phoneNumber`는 온보딩 전 값이 없으므로 nullable — Kotlin 타입도 `String?`로 맞춘다 (엔티티 nullable과 컬럼 `NOT NULL` 불일치는 `ddl-auto=validate`가 못 잡는 버그 유형이므로 반드시 컬럼도 nullable).

**AdminBranch(M2M 조인) 적용 시:** RESEARCH.md Open Question 3 — 서로게이트 PK(`id`) + `UNIQUE(admin_id, branch_id)` 제약 권장(스킬의 "PK는 항상 id" 관례와 일관).

**빌드 설정 근거** (`build.gradle.kts` lines 84-88, 이미 적용되어 있어 추가 설정 불필요):
```kotlin
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
```
→ `@Entity` 클래스는 자동으로 `open` 컴파일되므로 엔티티 클래스에 직접 `open` 키워드를 붙일 필요 없음.

---

### `build.gradle.kts` (`generateApiDocs` 태스크 체인 추가, config/build tooling)

**Analog (같은 파일 내 기존 태스크 등록 스타일)** — lines 90-101:
```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("user.timezone", "Asia/Seoul")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("user.timezone", "Asia/Seoul")
}
```
→ 새 태스크도 파일 하단에 `tasks.register<...>("...") { ... }` 블록으로 이어 붙이는 스타일 유지. Exec 체인 자체의 구체 구현은 이 파일에 analog가 없으므로 RESEARCH.md Pattern 3 스니펫(`startAppForDocs`/`waitForAppHealth`/`downloadApiDocs`/`stopAppForDocs`/`generateApiDocs`)이 유일한 출발점 — LOW/MEDIUM 신뢰도이므로 로컬 실행으로 직접 검증 필수(D-03 전제).

**주의:** `ktlint { }` 블록(lines 74-81)의 "스타일 규칙은 `.editorconfig`에만" 원칙은 신규 태스크에도 적용 — 태스크 코드 자체에 스타일 관련 설정을 추가하지 않는다.

---

### `docs/decisions.md` (신규 D-0XX 항목, doc)

**Analog:** `docs/decisions.md` D-017 항목 (lines 126-131, 강한 포맷 analog)
```markdown
## D-017. 에러 응답: RFC 9457 ProblemDetail

- 2026-07 / Spring 내장 `ProblemDetail`(`application/problem+json`)을 전역 예외 핸들러에서 반환.
  도메인 에러는 `type`에 에러코드 URI, 부가 정보는 `properties`에 담는다
- 이유: FE가 `openapi.yaml`로 타입을 생성한다(D-013). 표준 스키마라 생성기가 그대로 처리한다.
```
→ CONTEXT.md D-08 지시대로, D-06(커스텀 `code` 필드 + 에러코드 레지스트리)을 이 포맷 그대로 새 D 번호로 기록한다. 현재 최신 번호가 D-019(디렉토리 grep 기준)이므로 다음은 **D-020 이상**(다른 phase/작업과 충돌 없는지 실행 시점에 `docs/decisions.md` 최신 번호 재확인).

---

## Shared Patterns

### 통합테스트 애노테이션 조합 통일 (conventions §10.1)
**Source:** `src/test/kotlin/com/goldwrestling/system/HealthControllerTest.kt`, `src/test/kotlin/com/goldwrestling/db/FlywayMigrationIntegrationTest.kt`
**Apply to:** `GlobalExceptionHandlerTest`, 향후 모든 `@SpringBootTest` 통합테스트
```kotlin
@SpringBootTest
@AutoConfigureMockMvc                              // MockMvc 필요할 때만 추가
@Import(TestcontainersConfiguration::class)
```
컨텍스트 캐시 재사용을 위해 이 조합에서 벗어나지 않는다. `RANDOM_PORT` + `TestRestTemplate` 조합(RESEARCH.md Code Examples)은 새 컨텍스트를 띄우므로 꼭 필요할 때만 예외적으로 사용.

### Flyway 시드 + 스키마 동시 마이그레이션 (D-09)
**Source:** `.claude/skills/add-migration/SKILL.md` §2/§3, RESEARCH.md Pattern 2
**Apply to:** `V2__create_branch_member_admin.sql`
테이블 생성과 송파점 시드 INSERT를 같은 파일에 — Branch API가 스코프 밖이라 마이그레이션이 유일한 데이터 주입 경로.

### `Clock` 빈 주입 (conventions §5) — 이번 phase 해당 여부
Phase 1은 시각 의존 로직이 없어(엔티티에 `created_at`은 DB `now()` 기본값 사용) 직접 해당 사항은 제한적이나, 이후 phase의 엔티티/서비스가 이 원칙 위에 쌓이므로 `main`에 `Clock` 빈 등록이 필요하면 이번 phase에서 미리 챙길지 플래너가 판단 (RESEARCH.md에 명시적 언급 없음 — CONTEXT.md에도 없음, 확인 질문 후보).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `common/error/GlobalExceptionHandler.kt` | middleware | request-response | `@RestControllerAdvice`가 이 코드베이스에 처음 등장 — RESEARCH.md Pattern 1이 유일 근거, 실행 전 `verify-boot4-api` 재검증 필수 |
| `common/error/ErrorCode.kt` | utility | transform | 코드 enum 자체가 프로젝트에 아직 없음 — glossary.md 문서 관례로 유추 |
| `common/error/DomainException.kt` | model | transform | 예외 계층구조가 처음 생김 |
| `branch/Branch.kt`, `member/Member.kt`, `admin/Admin.kt`, `admin/AdminBranch.kt` | model (entity) | CRUD | JPA 엔티티가 코드베이스에 전무 — conventions.md §3 템플릿이 유일 표준 |
| `build.gradle.kts`의 Exec 태스크 체인 | build tooling | batch | Gradle Exec 프로세스 오케스트레이션 analog 없음 — RESEARCH.md Pattern 3(LOW/MEDIUM 신뢰도), 로컬 검증 필수 |
| `docs/error-codes.md` | doc | — | 신규 문서 종류, 포맷은 플래너/실행 재량 (표 형태 권장: 코드 / HTTP 상태 / 의미) |

## Metadata

**Analog search scope:** `src/main/kotlin/com/goldwrestling/**`, `src/test/kotlin/com/goldwrestling/**`, `src/main/resources/db/migration/**`, `build.gradle.kts`, `docs/**`, `.claude/skills/**`
**Files scanned:** 12 (전체 소스 파일 + build.gradle.kts + conventions.md + 3개 skill 문서 + decisions.md 발췌)
**Pattern extraction date:** 2026-07-30
