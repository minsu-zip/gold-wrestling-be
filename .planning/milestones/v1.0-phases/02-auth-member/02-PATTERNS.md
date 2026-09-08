# Phase 2: 인증·회원 - Pattern Map

**Mapped:** 2026-08-02
**Files analyzed:** 34 (신규 27 + 수정 4 + 마이그레이션 1 + 대표 테스트 2)
**Analogs found:** 16 / 34 (나머지는 이 레포에 아직 해당 role의 선례가 없음 — "No Analog Found" 참고)

> **컨텍스트:** Phase 1은 엔티티·config·에러 처리·헬스체크만 만든 "뼈대" phase다.
> **컨트롤러(도메인 API)·서비스·리포지토리·필터가 이 레포에 하나도 없다.** 그래서 이 role들은
> 코드 analog 대신 `docs/conventions.md`(§6, §7)와 `RESEARCH.md`의 Code Examples를 표준으로 삼는다.
> 반대로 **엔티티·config 프로퍼티·에러 처리·Flyway 마이그레이션·테스트 골격**은 Phase 1에 강한 선례가 있다.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `auth/KakaoAuthController.kt` | controller | request-response | `system/HealthController.kt` | role-match (약함 — DTO/서비스 위임 선례 없음) |
| `auth/AdminAuthController.kt` | controller | request-response | `system/HealthController.kt` | role-match (약함) |
| `auth/TokenController.kt` | controller | request-response | `system/HealthController.kt` | role-match (약함) |
| `member/MemberController.kt` | controller | CRUD | `system/HealthController.kt` | role-match (약함) |
| `member/OnboardingController.kt` | controller | request-response | `system/HealthController.kt` | role-match (약함) |
| `auth/KakaoAuthService.kt` | service | event-driven (외부 API 호출 + find-or-create) | — | **No Analog Found** |
| `auth/AdminAuthService.kt` | service | request-response | — | **No Analog Found** |
| `auth/TokenService.kt` | service | CRUD(발급/회전/폐기) | — | **No Analog Found** |
| `member/MemberService.kt` | service | CRUD | — | **No Analog Found** |
| `auth/RefreshToken.kt` (엔티티) | model | CRUD | `admin/AdminBranch.kt` (2-FK 조인 엔티티 형태) | role-match |
| `member/Member.kt` (수정 — kakaoId, rejectionReason 추가) | model | CRUD | `member/Member.kt` (자기 자신, 기존 구조 확장) | exact |
| `admin/Admin.kt` (수정 — loginId, passwordHash 추가) | model | CRUD | `admin/Admin.kt` (자기 자신) | exact |
| `auth/RefreshTokenRepository.kt` | repository | CRUD | — | **No Analog Found** (Spring Data JPA 표준 인터페이스로 충분) |
| `member/MemberRepository.kt` (+ `JpaSpecificationExecutor`) | repository | CRUD | — | **No Analog Found** |
| `admin/AdminRepository.kt` | repository | CRUD | — | **No Analog Found** |
| `member/MemberSpecifications.kt` | utility (query builder) | transform | — | **No Analog Found** |
| `config/SecurityConfig.kt` (수정 — permitAll 뼈대 교체) | config | request-response | `config/SecurityConfig.kt` (자기 자신, 교체 대상) | exact |
| `config/JwtProperties.kt` | config | — | `config/CorsProperties.kt` | exact |
| `config/KakaoProperties.kt` | config | — | `config/CorsProperties.kt` | exact |
| `config/JwtConfig.kt` (JwtEncoder/JwtDecoder 빈) | config | — | `config/OpenApiConfig.kt` | role-match |
| `config/ClockConfig.kt` (신규 — `Clock` 빈) | config | — | `config/OpenApiConfig.kt` | role-match |
| `(auth 또는 config)/JwtAuthenticationFilter.kt` | middleware | request-response | — | **No Analog Found** (RESEARCH.md Pattern 1이 표준) |
| `common/error/ProblemDetailAuthenticationEntryPoint.kt` | middleware(에러 변환) | request-response | `common/error/GlobalExceptionHandler.kt` | partial (ProblemDetail 조립 방식만 공유) |
| `common/error/ProblemDetailAccessDeniedHandler.kt` | middleware(에러 변환) | request-response | `common/error/GlobalExceptionHandler.kt` | partial |
| `common/error/ErrorCode.kt` (수정 — 인증 에러코드 추가) | config/enum | — | `common/error/ErrorCode.kt` (자기 자신) | exact |
| `admin/AdminSeeder.kt` (`ApplicationRunner`) | service(부팅 시 1회) | batch | `V2__create_branch_member_admin.sql`의 Branch 시드 INSERT | anti-pattern 참고용 (의도적으로 다른 방식 — 아래 Pitfall 참고) |
| `auth/kakao/KakaoTokenResponse.kt`, `KakaoUserResponse.kt` (외부 계약 DTO) | model(DTO) | transform | `system/HealthController.kt`의 `HealthResponse` | role-match (약함) |
| `auth/dto/*.kt` (`KakaoLoginRequest`, `AdminLoginRequest`, `TokenResponse`) | DTO | request-response | `system/HealthController.kt`의 `HealthResponse` | role-match (약함) |
| `member/dto/*.kt` (`MemberResponse`, `MemberSearchRequest`, `UpdateMemberStatusRequest`, `RejectMemberRequest`, `OnboardingRequest`) | DTO | CRUD | 없음(엔티티 `Member.kt`의 필드 목록만 참고) | **No Analog Found** |
| `common/time/AuditingDateTimeProvider.kt` | utility | — | — | **No Analog Found** |
| `db/migration/V3__*.sql` (kakao_id, rejection_reason, admin 자격, refresh_token 테이블) | migration | CRUD/schema | `db/migration/V2__create_branch_member_admin.sql` | exact |
| `src/test/.../auth/*Test.kt` (컨트롤러 통합테스트) | test | request-response | `system/HealthControllerTest.kt` + `common/error/GlobalExceptionHandlerTest.kt` | role-match |
| `src/test/.../db/FlywayMigrationIntegrationTest.kt` (수정 — V3 검증 추가) | test | schema | `db/FlywayMigrationIntegrationTest.kt` (자기 자신) | exact |

---

## Pattern Assignments

### `config/SecurityConfig.kt` (config, request-response) — 교체 대상

**Analog:** `config/SecurityConfig.kt` (Phase 1, 전체 permitAll 뼈대)

**현재 상태 — 교체할 골격** (전체, `config/SecurityConfig.kt:20-52`):
```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val corsProperties: CorsProperties,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            // TODO(인증 phase): 도메인 엔드포인트 추가 시 역할별 인가 규칙으로 대체
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()

    private fun corsConfigurationSource(): CorsConfigurationSource { ... }
}
```

**유지할 것:** 생성자 주입(`corsProperties`), `csrf disable`(STATELESS+Bearer라 공격 표면 없음 — RESEARCH.md Security Domain 표),
`cors { }` 블록·`corsConfigurationSource()` 그대로, `sessionCreationPolicy(STATELESS)`, `httpBasic`/`formLogin`/`logout` disable.

**교체할 것:** `authorizeHttpRequests { it.anyRequest().permitAll() }` 한 줄만 —
공개(`/api/auth/**`, `/api/system/**`) / `ROLE_MEMBER`(`/api/members/me`, 온보딩) / `ROLE_ADMIN`(`/api/admin/**`) 3분류로 교체하고,
`addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)` +
`exceptionHandling { it.authenticationEntryPoint(...).accessDeniedHandler(...) }` 추가 (RESEARCH.md Architecture Patterns 참고).

**새 config 클래스를 난립시키지 않는다** — CONTEXT.md code_context: "Security 필터체인은 기존 `SecurityConfig` 교체."

---

### `config/CorsProperties.kt` → `JwtProperties.kt`, `KakaoProperties.kt`의 analog

**Analog:** `config/CorsProperties.kt` (전체, 12줄)

```kotlin
package com.goldwrestling.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 허용할 프론트엔드 오리진. `.env` 의 `CORS_ALLOWED_ORIGINS`(쉼표 구분)로 주입된다.
 */
@ConfigurationProperties(prefix = "goldwrestling.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
```

**그대로 복사할 패턴:**
- `@ConfigurationProperties(prefix = "goldwrestling.<domain>")` + `data class`
- `GoldWrestlingApplication.kt`에 이미 `@ConfigurationPropertiesScan`이 있으므로 별도 `@EnableConfigurationProperties` 불필요
- KDoc 첫 줄에 어느 `.env` 키가 이 프로퍼티를 채우는지 명시(`JWT_SECRET`, `JWT_ACCESS_TOKEN_EXPIRY_MINUTES`, `JWT_REFRESH_TOKEN_EXPIRY_DAYS` / `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI` — `.env.example`에 이미 키 이름이 있다)
- `application.yml`에 `goldwrestling.jwt.*`, `goldwrestling.kakao.*` 매핑 추가 필요 (`goldwrestling.cors`가 `application.yml:77-80`에 있는 것과 동일한 자리)

---

### `config/OpenApiConfig.kt` → `JwtConfig.kt`, `ClockConfig.kt`의 analog

**Analog:** `config/OpenApiConfig.kt` (전체, 27줄)

```kotlin
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI() ... 
}
```

**패턴:** `@Configuration` 클래스 + `@Bean` 메서드 하나. `JwtConfig`는 `jwtEncoder()`/`jwtDecoder()` 두 개,
`ClockConfig`는 `clock(): Clock = Clock.system(ZoneId.of(SEOUL_ZONE_ID))`(`GoldWrestlingApplication.kt`의
`SEOUL_ZONE_ID` 상수 재사용) 하나. 생성자로 `JwtProperties`를 주입받는 형태는 `SecurityConfig`가
`CorsProperties`를 주입받는 것과 동일 관용구.

---

### 엔티티 확장 — `member/Member.kt`, `admin/Admin.kt` 수정 / `auth/RefreshToken.kt` 신규

**Analog:** `member/Member.kt` (전체), `admin/AdminBranch.kt` (전체, 2-FK 조인 엔티티)

**Member.kt 현재 구조** (`member/Member.kt:27-44`) — `kakaoId`(nullable→아니오, unique), `rejectionReason`(nullable) 추가 시 그대로 따를 패턴:
```kotlin
@Entity
@Table(name = "member")
class Member(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    val branch: Branch,
    @Column(length = 50)
    var name: String?,
    @Column(name = "phone_number", length = 20)
    var phoneNumber: String?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MemberStatus = MemberStatus.PENDING,
    // 추가: kakaoId: Long (nullable = false, unique), rejectionReason: String?(nullable)
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
```
- `data class` 금지, `@ManyToOne(fetch = LAZY)` 명시, `@Enumerated(EnumType.STRING)` 필수 — conventions.md §3 그대로 준수된 예시
- `nullable` Kotlin 타입과 DB 제약을 반드시 맞춘다 (`name`/`phoneNumber`가 왜 `String?`인지의 주석 스타일도 그대로 재사용 — "온보딩 전에는 값이 없다")

**AdminBranch.kt (2-FK 조인 형태)** — `RefreshToken`이 Member/Admin 둘 중 하나를 가리켜야 하는 구조(Open Question #2, nullable FK 쌍 + CHECK 1차 권장)의 가장 가까운 선례:
```kotlin
@Entity
@Table(name = "admin_branch")
class AdminBranch(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    val admin: Admin,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    val branch: Branch,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
```
`RefreshToken`은 이 형태를 참고하되 두 `@JoinColumn`을 **둘 다 `nullable = true`**로 바꾸고 (member 전용이면 admin_id는 null, 반대도 마찬가지),
DB CHECK 제약(`principal 정확히 하나만 non-null`)을 V3 마이그레이션에 추가한다 — `AdminBranch`처럼 양쪽 다 `nullable = false`로 두면 안 된다.
`token_hash`(원문 저장 금지, SHA-256), `expiresAt`(`OffsetDateTime`), `revoked`(회전/무효화 플래그) 컬럼 추가.

---

### `db/migration/V2__create_branch_member_admin.sql` → `V3__*.sql`의 analog

**Analog:** 전체 파일 (49줄)

```sql
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

**그대로 따를 패턴:**
- `id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY` — 모든 새 테이블(`refresh_token`)에 동일 PK 스타일
- `CONSTRAINT fk_<table>_<ref> FOREIGN KEY (...) REFERENCES ...` 이름 규약
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` — `refresh_token`에도 동일 (단, 이번엔 실제로 애플리케이션이 읽을 수도 있음 — `AuditingDateTimeProvider` 연동 여부는 계획 단계 결정)
- `CREATE INDEX idx_<table>_<col>` — 조회 패턴(로그인 시 kakao_id 조회, refresh 회전 시 token_hash 조회)에 맞춰 인덱스 추가
- 조회/유니크가 필요한 컬럼(`kakao_id`, `admin.login_id`, `refresh_token.token_hash`)엔 `CONSTRAINT uq_<table>_<col> UNIQUE (...)` (V2에 `uq_branch_name` 선례 있음, `V2__create_branch_member_admin.sql`은 branch에서 확인 가능 — 실제로는 V1 파일이 아니라 `V2`가 이 제약을 갖고 있으니 그대로 벤치마킹)
- 파일 맨 위 주석으로 "이번 마이그레이션이 왜 필요한가 + 이전 마이그레이션과의 관계"를 1~2줄 남기는 스타일 유지 (V1, V2 둘 다 이렇게 시작함)
- **주의(add-migration 스킬 §3):** `refresh_token`은 로그인마다 생기는 회전 대상 데이터라 "동시성 방어 제약"에 해당하지 않지만, `member.kakao_id UNIQUE`는 동일 카카오 계정 중복 가입 방지이므로 반드시 제약으로 건다

---

### 에러 처리 3종 세트 — `common/error/DomainException.kt`, `ErrorCode.kt`, `GlobalExceptionHandler.kt`

**Analog:** 전체 3개 파일 (기존 그대로 재사용, 신규 에러코드만 추가)

**DomainException 상속 패턴** (`common/error/DomainException.kt:14-18`, 인증 예외는 이 기반 클래스를 그대로 상속):
```kotlin
abstract class DomainException(
    val errorCode: ErrorCode,
    message: String,
    val status: HttpStatus = errorCode.defaultStatus,
) : RuntimeException(message)
```
예: `class InvalidCredentialsException : DomainException(ErrorCode.INVALID_CREDENTIALS, "아이디 또는 비밀번호가 올바르지 않습니다.")`,
`class RefreshTokenReuseDetectedException : DomainException(ErrorCode.REFRESH_TOKEN_REUSED, ...)`

**ErrorCode 확장 패턴** (`common/error/ErrorCode.kt:16-39`) — 새 enum 값 추가 시 `docs/error-codes.md`도 같은 PR에서 갱신 (D-028):
```kotlin
enum class ErrorCode(val defaultStatus: HttpStatus) {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    // ... 기존 7개 유지 ...
    // 추가 예: INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED), MEMBER_NOT_ACTIVE(HttpStatus.FORBIDDEN),
    //          REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED), ONBOARDING_INCOMPLETE(HttpStatus.FORBIDDEN)
}
```

**GlobalExceptionHandler는 수정 불필요** — `DomainException` 하위 클래스는 이미 `handleDomainException`이 처리한다.
단, `GlobalExceptionHandler.kt:63-75`의 "시큐리티 예외는 되던진다" 규칙이 이번 phase에서 실제로 발동한다는 점이 핵심
(주석에 이미 "인증 phase에서 필요해지면 별도 EntryPoint/Handler로 다룬다"고 예고돼 있음):
```kotlin
@ExceptionHandler(Exception::class)
fun handleUnexpectedException(ex: Exception, request: WebRequest): ResponseEntity<Any> {
    if (ex is AccessDeniedException || ex is AuthenticationException) {
        throw ex   // 여기로 오면 절대 삼키지 않는다 — ExceptionTranslationFilter가 처리하도록
    }
    ...
}
```
→ `ProblemDetailAuthenticationEntryPoint`/`ProblemDetailAccessDeniedHandler`는 **이 규칙과 짝을 맞춰야** 401/403이 `application/problem+json`으로 나간다. `problem.setProperty("code", ...)`로 `code` 필드를 넣는 부분은 `GlobalExceptionHandler.handleExceptionInternal`(`common/error/GlobalExceptionHandler.kt:81-93`)의 `problem.setProperty("code", resolveErrorCode(ex, statusCode).name)`과 동일한 관용구를 그대로 복사한다.

---

### 컨트롤러 골격 — `system/HealthController.kt` (약한 analog, 유일한 컨트롤러 선례)

**Analog:** `system/HealthController.kt` (전체, 33줄)

```kotlin
@RestController
@RequestMapping("/api/system")
@Tag(name = "system", description = "서버 상태·환경 확인")
class HealthController {
    @GetMapping("/health")
    @Operation(summary = "서버 기동 및 기준 시간대 확인")
    fun health(): HealthResponse = HealthResponse(...)
}

data class HealthResponse(val status: String, val serverTime: ZonedDateTime, val timeZone: String)
```

**가져올 것:** `@RestController` + `@RequestMapping("/api/...")` + `@Tag`, 메서드마다 `@Operation(summary = ...)`.
**가져오지 못하는 것(이 phase가 처음 만드는 부분):** 서비스 위임, DTO 요청 바인딩(`@RequestBody @Valid`), 인증 주체 파라미터(`@AuthenticationPrincipal`) —
이건 `docs/conventions.md` §2(DTO 네이밍)·§6(컨트롤러는 DTO만)·`.claude/skills/add-endpoint/SKILL.md`의 절차를 따른다.
컨트롤러에 `@Transactional` 금지(D-020) — `HealthController`도 안 붙어 있어 이 규칙과 일치.

**본인 프로필/온보딩처럼 "인증만 필요, 역할 무관"인 예외 엔드포인트**는 RESEARCH.md Pattern 3에서 이미 URL 인가 규칙에
별도로 열어주기로 확정됨 — `MemberController`가 아니라 `SecurityConfig`의 `authorizeHttpRequests`에서 처리.

---

### 테스트 골격 — `system/HealthControllerTest.kt`, `common/error/GlobalExceptionHandlerTest.kt`

**Analog:** 두 파일 전체

**공통 애노테이션 조합** (컨텍스트 캐시 재사용 — conventions §10.1, 반드시 동일 조합 유지):
```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class XxxControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc
    ...
}
```
- `@AutoConfigureMockMvc`는 Boot 4 패키지(`org.springframework.boot.webmvc.test.autoconfigure`)로 임포트 — Boot 3 예제 그대로 쓰면 컴파일 안 됨 (CLAUDE.md 기술 규칙)
- `GlobalExceptionHandlerTest`의 "테스트 전용 컨트롤러를 `@TestConfiguration`+`@Import`로만 등록" 패턴(`common/error/GlobalExceptionHandlerTest.kt:179-213`)은
  JWT 필터·인가 규칙 자체를 검증하는 테스트(예: 만료 토큰 → 401 problem+json)에서 재사용 가능 — 실제 도메인 엔드포인트를 오염시키지 않고 필터체인만 검증할 때 유용
- `content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)` + `jsonPath("$.code")` 검증 관용구는 인증 실패(401)·인가 실패(403) 테스트에도 그대로 적용
- `FlywayMigrationIntegrationTest.kt`(`@SpringBootTest` + `@Import(TestcontainersConfiguration::class)`, `JdbcClient`로 스키마 직접 조회)는 V3 마이그레이션 검증에 그대로 확장 —
  기존 "admin 테이블에는 아직 로그인 자격 컬럼이 없다"(`db/FlywayMigrationIntegrationTest.kt:77-86`) 테스트는 V3 적용 후 **반대 방향으로(있다) 수정**해야 함 — 이 테스트를 잊고 방치하면 회귀가 조용히 깨진다

---

## Shared Patterns

### 엔티티 규약 (D-016/D-018 이후 §3)
**Source:** `member/Member.kt`, `admin/Admin.kt`, `admin/AdminBranch.kt`, `branch/Branch.kt`
**Apply to:** `Member.kt`(수정), `Admin.kt`(수정), `RefreshToken.kt`(신규)
```kotlin
class X(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "...", nullable = ...) val ref: Y,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = N) var status: Z,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null
}
```
`data class` 금지, `equals`는 `id` 기준만, nullable 컬럼은 Kotlin 타입도 nullable.

### 트랜잭션 경계 (D-020)
**Source:** `docs/conventions.md` §7 (코드 선례는 아직 없음 — 이 phase가 첫 서비스 클래스)
**Apply to:** `KakaoAuthService`, `AdminAuthService`, `TokenService`, `MemberService`
```kotlin
@Service
@Transactional(readOnly = true)
class XxxService(...) {
    @Transactional
    fun mutatingMethod(...) { ... }
}
```
컨트롤러·리포지토리에 `@Transactional` 금지. **카카오 API 호출은 트랜잭션 밖에서 먼저 끝내고, 그 결과만 갖고 짧은 트랜잭션을 연다** (RESEARCH.md Anti-Patterns — `@Transactional` 안에서 외부 API 호출 금지).

### 에러 응답 통일 (D-017/D-028)
**Source:** `common/error/DomainException.kt`, `ErrorCode.kt`, `GlobalExceptionHandler.kt` (전체, 위 Pattern Assignments에 인용)
**Apply to:** 모든 신규 서비스·컨트롤러, 그리고 신규 `ProblemDetailAuthenticationEntryPoint`/`AccessDeniedHandler`
- 도메인 예외 = `DomainException` 상속 + `ErrorCode` 추가(같은 PR에서 `docs/error-codes.md` 갱신)
- 인증/인가(401/403)는 컨트롤러 예외 경로와 별도로 `AuthenticationEntryPoint`/`AccessDeniedHandler`가 담당하지만 **같은 `code` 프로퍼티 주입 관용구**를 공유해야 함

### DTO 명명·변환 (D-019, conventions §6)
**Source:** `docs/conventions.md` §6 (코드 선례 없음 — `HealthResponse`가 유일하지만 변환 로직이 없는 단순 사례)
**Apply to:** 모든 신규 DTO
- `<동작><대상>Request` / `<대상>Response` 네이밍
- 응답 DTO는 `companion object`의 `from(entity)`
- 형식 검증(`jakarta.validation`)만 DTO에, 도메인 규칙(상태 전이 가능 여부 등)은 서비스/엔티티에

### Flyway 마이그레이션 (add-migration 스킬 + V1/V2 선례)
**Source:** `db/migration/V2__create_branch_member_admin.sql`
**Apply to:** `V3__*.sql`
- 파일명 `V3__<snake_case_설명>.sql`, PK/FK/인덱스/유니크 네이밍 규약 그대로
- **커밋된 V1/V2는 절대 수정 금지** — 이번 phase의 스키마 변경은 전부 새 버전(V3+)으로

---

## No Analog Found

이 phase가 이 role의 **첫 코드**라 코드베이스 analog가 없다. 대신 표준으로 삼을 문서를 명시한다.

| File | Role | Data Flow | Reason / 대신 참고할 표준 |
|---|---|---|---|
| `auth/KakaoAuthService.kt`, `AdminAuthService.kt`, `TokenService.kt`, `member/MemberService.kt` | service | 다양 | 이 레포 최초의 `@Service` 클래스. `docs/conventions.md` §7(트랜잭션 경계) + RESEARCH.md Code Examples 사용 |
| `auth/RefreshTokenRepository.kt`, `member/MemberRepository.kt`, `admin/AdminRepository.kt` | repository | CRUD | 이 레포 최초의 `JpaRepository`. Spring Data JPA 표준 인터페이스(`interface X : JpaRepository<Entity, Long>`, `MemberRepository`는 `JpaSpecificationExecutor<Member>` 추가 상속)로 충분 — 커스텀 구현 불필요 |
| `member/MemberSpecifications.kt` | utility(query) | transform | `Specification<Member>` 조합 헬퍼. RESEARCH.md §Architecture Patterns "Specification 기반 동적 검색" 참고, 코드 선례 없음 |
| `JwtAuthenticationFilter.kt` | middleware | request-response | 이 레포 최초의 커스텀 `OncePerRequestFilter`. **RESEARCH.md Pattern 1(예외를 던지지 않는다) 코드 예시를 표준으로 그대로 따른다** — 임의 변형 금지(Pitfall 1 참고) |
| `admin/AdminSeeder.kt` | service(부팅 시) | batch | `ApplicationRunner` 멱등 시드는 이 레포 최초. V2의 Branch INSERT 시드와 **의도적으로 다른 메커니즘**(Common Pitfall 2 — 시크릿은 Flyway 플레이스홀더 금지) |
| `member/dto/*`, `auth/dto/*`, `auth/kakao/*` | DTO | 다양 | `docs/conventions.md` §6 네이밍 규칙만 존재. `HealthResponse`는 변환 로직이 없어 참고 가치 낮음 |
| `common/time/AuditingDateTimeProvider.kt` | utility | — | JPA Auditing용 `DateTimeProvider` 커스텀 빈은 이 레포 최초. RESEARCH.md §Sources Secondary의 `docs.spring.io/spring-data/jpa/reference/auditing.html`(`dateTimeProviderRef`) 참고 |

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/goldwrestling/**`, `src/test/kotlin/com/goldwrestling/**`, `src/main/resources/db/migration/**`, `src/main/resources/application.yml`, `docs/conventions.md`, `.claude/skills/{add-endpoint,add-migration,add-domain-test}/SKILL.md`
**Files scanned:** 17개 프로덕션 Kotlin 파일(Phase 1 전체) + 4개 테스트 파일 + 2개 마이그레이션 + 1개 application.yml + 3개 스킬 문서
**Pattern extraction date:** 2026-08-02
