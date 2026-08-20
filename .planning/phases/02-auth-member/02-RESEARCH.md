# Phase 2: 인증·회원 - Research

**Researched:** 2026-08-02
**Domain:** Spring Boot 4.1 / Spring Security 7 기반 stateless JWT 인증, 카카오 OAuth 인가코드 연동, 상태 기반 인가
**Confidence:** MEDIUM-HIGH (핵심 API는 Context7 공식 문서로 검증. 프로젝트 고유 설계 — 스키마 분할, 시드 방식 — 는 추천안 수준)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**카카오 OAuth 연동 방식 (D-032)**
- **인가 코드 방식.** FE는 카카오 리다이렉트와 인가 코드 전달만 담당한다.
- BE가 인가 코드로 카카오 토큰 교환 → 사용자 정보 조회 → 자체 JWT 발급까지 수행한다.
- `client_secret`은 서버 환경변수에만 존재한다 (`.env.example`에 키 이름 동기화 — 시크릿 규칙).

**JWT 토큰 정책 (D-033)**
- **access 30분, refresh 14일.**
- refresh 토큰은 **DB 저장 + 사용 시마다 회전(rotation)**. 로그아웃 = refresh 삭제.
- 회원 상태가 `ACTIVE`가 아니게 되면 refresh를 무효화해 **강제 로그아웃이 가능**해야 한다.
- 상태 게이트 인가(AUTH-04)는 토큰 클레임이 아니라 **DB 현재 상태 기준**으로 검사한다 —
  refresh만 무효화하면 기발급 access가 최대 30분 살아 있는 창이 생기기 때문 (D-033 노트).

**가입 거절·상태 전이 (D-034, policies §5.2)**
- 별도 `REJECTED` 상태 없이 **`INACTIVE` 전환 + 거절 사유 기록**.
- 거절된 회원 재로그인 시 **거절 안내 화면 대상으로 식별**된다.
- 재신청 = 관리자가 상태를 `PENDING`으로 되돌리는 운영 방식 (회원 셀프 재신청 없음).
- 승인 취소 = 별도 기능 없이 기존 상태 변경(MEMBER-03)으로 갈음.

**회원 목록·검색 API (D-035)**
- **page/size 페이지네이션**, 검색어 하나로 **이름·전화번호 부분 일치**, **상태 필터** 제공.
- **승인 대기 목록도 동일 API 재사용** — `status=PENDING` + 온보딩 완료 필터 조합.
  (온보딩 완료 필터가 있어야 policies §5.1 "프로필 입력된 PENDING만 노출"이 지켜진다)

### Claude's Discretion

- 카카오 연동 세부: redirect_uri 관리(환경변수), state 파라미터 처리, 카카오 API 호출 구현
  (Boot 4 기준 — verify-boot4-api 스킬 절차 준수)
- 스키마 설계(V3+): member의 kakao_id·유니크 제약·거절 사유 컬럼, admin 로그인 자격(BCrypt),
  refresh_token 테이블 구성
- 회전 시 토큰 재사용 감지(reuse detection) 도입 여부, 멀티 디바이스 동시 로그인 허용 여부
  (권장: refresh 토큰을 회원당 여러 개 허용 — DB 저장 구조가 자연스럽게 지원)
- 관리자 시드 계정의 비밀번호 주입 방식 — 단 실값 커밋 금지, 환경변수 주입 원칙은 고정
- `created_at` 감사 시각 전략 — Phase 1에서 이월된 결정 (Clock 빈 기반, 이번 phase의 첫
  INSERT 경로에서 확정)
- 인가 규칙 구현 방식 (URL 기반 vs 메서드 시큐리티 조합)

### Deferred Ideas (OUT OF SCOPE)

- 카카오 자동 수집·온보딩 폼 자동 채움 — v2 (KAKAO-01, D-025 기존 결정)
- 회원 프로필 셀프 수정 — v2 (PROF-01, 기존 결정)

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | 카카오 OAuth 가입·로그인, 가입 직후 `PENDING` | §카카오 연동(인가코드 교환), §스키마(member.kakao_id) |
| AUTH-02 | JWT access/refresh 발급, refresh로 access 갱신 | §JWT 발급·검증(NimbusJwtEncoder/Decoder), §refresh 회전 패턴 |
| AUTH-03 | 관리자 ID/PW 로그인, 카카오 없음, 회원과 동일 JWT 체계, 시드 계정 | §관리자 인증, §관리자 시드 주입 방식 |
| AUTH-04 | 역할·상태 기반 인가, `PENDING`은 승인 대기 정보 외 접근 불가 | §인가 규칙(URL+메서드 하이브리드), §상태 게이트 필터 패턴 |
| AUTH-05 | 최초 로그인 온보딩(실명·전화번호 필수, 형식 검증) | §온보딩 식별 로직, §전화번호 검증 |
| AUTH-06 | 온보딩 미완료 재로그인 시 재식별 | §온보딩 완료 판정(name/phoneNumber null 체크) |
| MEMBER-01 | 승인/거절, 승인 목록은 온보딩 완료 `PENDING`만, 승인 시 `ACTIVE` | §D-035 목록 재사용, §거절 사유 컬럼 |
| MEMBER-02 | 목록·상세 조회, 이름·전화번호 검색 | §Specification 기반 동적 검색 |
| MEMBER-03 | 상태 변경(`ACTIVE`/`ON_LEAVE`/`INACTIVE`) | §상태 변경 시 refresh 무효화 연동(D-033) |
| MEMBER-04 | 본인 프로필 조회 | §URL 기반 인가에서 "인증만 요구"되는 예외 엔드포인트 |

</phase_requirements>

## Summary

이번 phase는 이 프로젝트의 첫 인증 계층이다. 세 갈래 작업으로 나뉜다 —
(1) 카카오 인가코드를 서버가 직접 토큰과 교환하고 자체 JWT를 발급하는 로그인 플로우,
(2) 회원·관리자가 공유하는 JWT 검증·회전·상태 게이트 인가 체계, (3) 온보딩·승인·회원 관리 CRUD.

**핵심 발견**: 이 프로젝트는 새 서드파티 인증 라이브러리가 필요 없다. `spring-boot-starter-security`가
이미 의존성에 있고, JWT 서명·검증에 필요한 `NimbusJwtEncoder`/`NimbusJwtDecoder`(HS256, 대칭키)는
`org.springframework.security:spring-security-oauth2-jose` 모듈 하나만 추가하면 되는데, 이 아티팩트는
Boot 4.1.0 BOM이 `spring-security-bom`(7.1.0)을 통해 버전까지 관리한다 — `build.gradle.kts`에 버전을
적지 않아도 된다(Maven Central·BOM으로 실제 확인). 카카오 API 호출은 이미 있는 `spring-boot-starter-webmvc`의
`RestClient`로 충분하고, 관리자 비밀번호 해싱은 `spring-security-crypto`(starter-security에 포함)의
`PasswordEncoder`로 충분하다. 즉 **이번 phase는 신규 외부 패키지 도입이 사실상 0건**이다.

가장 까다로운 지점은 라이브러리 선택이 아니라 **아키텍처 정합**이다: (a) D-033이 요구하는 "DB 현재 상태
기준" 인가는 토큰 클레임만으로 풀 수 없어 커스텀 필터가 매 요청 회원/관리자 상태를 조회해야 한다,
(b) `ExceptionTranslationFilter`보다 앞에 위치하는 커스텀 JWT 필터가 예외를 직접 던지면
`GlobalExceptionHandler`도 `ExceptionTranslationFilter`도 잡지 못해 포맷이 깨진 응답이 나간다 —
필터는 인증 실패 시 컨텍스트를 비워두기만 하고, 거부 자체는 뒤에 있는 `AuthorizationFilter`가 하게
설계해야 한다, (c) `Member`와 `Admin`이 별도 테이블인데 "동일한 JWT 체계"(D-026)를 써야 하므로
refresh_token 테이블과 토큰 클레임 모두 principal 종류(MEMBER/ADMIN)를 명시적으로 다뤄야 한다.

**Primary recommendation:** `spring-security-oauth2-jose`의 `NimbusJwtEncoder`/`NimbusJwtDecoder`(HS256
대칭키, `.env`의 `JWT_SECRET`)로 자체 JWT를 발급·검증하고, 인증은 커스텀 `OncePerRequestFilter` 하나가
담당한다(예외를 던지지 않고 미인증 상태로 통과시킴). `authorizeHttpRequests`로 역할(공개/MEMBER/ADMIN)을
가르고, `ACTIVE` 상태 게이트처럼 URL 패턴만으로 표현하기 어려운 조건은 서비스 계층에서 principal의
최신 상태를 조회해 검사한다(§인가 규칙 참고). 관리자 시드는 Flyway가 아니라 `ApplicationRunner`
기반 멱등 시드로 처리한다(비밀은 환경마다 달라야 하므로 D-031의 Branch 시드 패턴과 의도적으로 다르다).

## Architectural Responsibility Map

이 레포는 백엔드 단일 서비스다(FE는 별도 레포). 아래 매핑은 **BE 내부** 계층 경계를 가른다 —
Spring Security 필터체인(횡단) vs 서비스/도메인(비즈니스 규칙) vs DB(영속·제약).

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 카카오 인가코드 → 토큰 교환·사용자 조회 | API/Backend (`auth` 서비스, RestClient) | — | 시크릿(client_secret) 보호가 이유의 전부다(D-032). 외부 API 호출은 트랜잭션 밖에서 수행 |
| 자체 JWT 발급·서명 | API/Backend (Security 설정 계층) | — | 서명 키가 서버에만 존재. 무상태이므로 세션 계층 없음 |
| JWT 검증 + SecurityContext 구성 | API/Backend (필터체인) | Database (상태 재조회) | 서명 검증은 필터 단독으로 가능하지만, D-033 상태 게이트는 매 요청 DB 조회가 필요 — 필터가 두 계층을 다 만진다 |
| refresh 토큰 저장·회전·무효화 | API/Backend (서비스) | Database/Storage (`refresh_token` 테이블) | 회전·강제 로그아웃이 DB 레코드의 존재/삭제로 구현되므로 두 계층이 함께 움직인다 |
| 온보딩 완료 판정 | Database (컬럼 nullable 여부) | API/Backend (판정 로직) | `name`/`phoneNumber` null 여부가 곧 판정 기준(D-025) — DB 스키마 자체가 상태를 표현 |
| 상태 게이트 인가(`PENDING` 접근 제한) | API/Backend (필터/서비스) | Database (현재 상태 SELECT) | D-033 노트: 토큰 클레임이 아니라 DB 현재 상태가 기준 |
| 회원 승인/거절/상태 변경 | API/Backend (서비스) | Database (상태 컬럼 + refresh_token cascade) | 상태 변경이 즉시 refresh 무효화로 이어져야 하므로(D-033) 두 계층이 한 트랜잭션에서 결합 |
| 회원 목록·검색(페이지네이션) | Database (인덱스·쿼리) | API/Backend (Specification 조립) | 검색·필터 성능은 인덱스가 좌우하고, 조합 로직은 BE가 조립 |
| 관리자 ID/PW 인증 | API/Backend (서비스, `PasswordEncoder`) | Database (`admin.password_hash`) | 세션이 아니라 로그인 성공 시 바로 JWT를 발급하는 단발성 검증 |

## Project Constraints (from CLAUDE.md)

- **Boot 4.1.x / Spring Framework 7 고정.** Boot 3 예제 이식 금지. 새 의존성·API는
  ① context7 MCP 확인 → ② maven-metadata.xml 실제 버전 확인 → ③ `./gradlew compileKotlin` 검증 순서.
  (본 문서는 이 순서를 실제로 수행했다 — §Standard Stack 참고)
- 에러 응답은 RFC 9457 `ProblemDetail` 고정(D-017), 커스텀 공통 래퍼 금지. 새 에러코드는
  `docs/error-codes.md`를 같은 PR에서 갱신
- DB 스키마 변경은 Flyway만(`ddl-auto=validate` 고정), 커밋된 마이그레이션 수정 금지 — 새 버전 추가
- 횟수는 이번 phase와 무관(Pass는 Phase 3) — `BigDecimal`/`compareTo` 규칙은 해당 없음
- 컨트롤러는 DTO만 주고받는다(D-019), 서비스 `@Transactional(readOnly = true)` 기본 + 변경 메서드만
  오버라이드(D-020), 컨트롤러·리포지토리에 `@Transactional` 금지
- 시간대 `Asia/Seoul`, 타임스탬프는 `OffsetDateTime`+`TIMESTAMPTZ`, 현재 시각은 `Clock` 빈 주입(§5)
- 네이밍은 `docs/glossary.md`만. **이번 phase는 새 개념이 다수 등장한다** — `kakaoId`, `loginId`,
  `passwordHash`, `rejectionReason`(또는 유사), `RefreshToken` 등은 계획 단계에서 glossary.md에
  먼저 추가한 뒤 코드에 써야 한다 (규칙 3)
- 요구사항이 모호하면 질문한다(규칙 8) — 본 문서 §Open Questions에 해당 항목을 정리했다
- 모르는 API 추측 금지(규칙 9) — verify-boot4-api 스킬 절차 준수
- 프로덕션 코드 변경 시 같은 작업에서 테스트 동반(규칙 10) — 인증·상태 게이트는 conventions §10.0 표의
  "잘못되면 잔여 횟수·예약이 틀어지는가" 기준과 별개로, **잘못되면 인가 자체가 뚫리는 코드**이므로
  전부 테스트 대상으로 간주해야 한다 (면제 대상 아님)

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|---------------|
| `spring-boot-starter-security` | Boot 4.1.0 관리(이미 의존성에 있음) | 필터체인, `PasswordEncoder`, `SecurityContext` | 이미 프로젝트에 존재. Spring 표준 보안 계층 |
| `org.springframework.security:spring-security-oauth2-jose` | **7.1.0** (Boot 4.1.0 BOM이 `spring-security-bom` 경유로 관리 — 버전 미기입) | `NimbusJwtEncoder`/`NimbusJwtDecoder`로 자체 JWT 서명·검증 | Spring Security 공식 모듈. Nimbus JOSE+JWT를 감싸 API가 안정적이고, 별도 버전 관리가 필요 없다(`[VERIFIED: Maven Central + Boot 4.1.0 BOM]`, `spring-boot-dependencies-4.1.0.pom` → `spring-security-bom-7.1.0.pom`에서 직접 확인) |
| `spring-boot-starter-webmvc` (기존) | — | 카카오 API 호출용 `RestClient` | 이미 의존성에 있음. 별도 HTTP 클라이언트 라이브러리 불필요 |
| `spring-security-crypto`(starter-security에 포함) | — | 관리자 비밀번호 해싱(`PasswordEncoder`) | Spring Security 표준. `BCryptPasswordEncoder` 또는 `PasswordEncoderFactories.createDelegatingPasswordEncoder()` |

**신규 Gradle 의존성 추가는 1건뿐이다:**
```kotlin
// build.gradle.kts — Boot BOM이 관리하므로 버전 미기입 (D-014 원칙과 동일)
implementation("org.springframework.security:spring-security-oauth2-jose")
```

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-security-test` (기존 테스트 의존성) | — | `@WithMockUser`, `SecurityMockMvcRequestPostProcessors` | 컨트롤러 통합테스트에서 인증된/미인증 요청 시뮬레이션 |
| `org.springframework.test.web.client.MockRestServiceServer`(spring-test, 기존) | — | 카카오 토큰/사용자 API 호출을 목킹 | `RestClient.Builder`에 `MockRestServiceServer.bindTo(...)` — 외부 API 없이 카카오 연동 테스트 (`[CITED: docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html]`) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `spring-security-oauth2-jose`(Nimbus) | `io.jsonwebtoken:jjwt-api` 0.13.0(2026-08 기준 최신, `[VERIFIED: npm registry]`는 해당 없음 — Maven, `[ASSUMED]` 출처는 WebSearch) | jjwt는 API가 더 간결(`Jwts.builder().signWith(key).compact()`)하지만, **별도 버전을 직접 관리**해야 하고 Boot BOM 보호를 받지 못한다. Nimbus 경로는 서명 키 구성이 한 단계 더 필요하지만(§Code Examples) 이미 신뢰하는 Spring Security 신뢰 경계 안에 있다. **비대칭키(RS256) 전환이 필요해지면** jjwt·Nimbus 둘 다 지원하므로 선택에 영향 없음 |
| `spring-security-oauth2-client`(카카오 리다이렉트 위임) | 직접 `RestClient` 호출 | D-032가 이미 기각 — STATELESS JWT 체계와 세션 기반 리다이렉트 흐름이 상충 |
| 커스텀 `OncePerRequestFilter` | `oauth2ResourceServer().jwt(...)` 완전 자동설정 | 리소스서버 자동설정은 "발급자가 다른 시스템"을 전제로 한 필터·에러 응답 규격을 갖고 있어, **D-033의 DB 상태 재조회**를 끼워 넣기 어렵다. `NimbusJwtDecoder`는 자동설정 없이 단독 사용 가능해 커스텀 필터 안에서 그대로 쓸 수 있다 |
| `ApplicationRunner` 기반 관리자 시드 | Flyway `INSERT` + `spring.flyway.placeholders`(D-031과 동일 패턴) | Flyway 시드는 D-031(Branch)처럼 **환경 간 동일한 값**이 보장돼야 하는 데이터에 적합하다. 관리자 비밀번호는 반대로 **환경마다 달라야 하는 시크릿**이라 같은 패턴을 쓰면 "플레이스홀더 미설정 시 깨진 계정이 그 환경에 영구 시드"되는 리스크가 생긴다 — 근거는 §Common Pitfalls 참고 |

**버전 검증 방법 (수행함):**
```bash
curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.0/spring-boot-dependencies-4.1.0.pom | grep spring-security.version
# → <spring-security.version>7.1.0</spring-security.version>
curl -s https://repo1.maven.org/maven2/org/springframework/security/spring-security-bom/7.1.0/spring-security-bom-7.1.0.pom | grep -A1 oauth2-jose
# → spring-security-oauth2-jose 7.1.0 이 BOM에 명시적으로 관리됨을 확인
```

## Package Legitimacy Audit

이번 phase에서 추가하는 신규 외부 패키지는 **`org.springframework.security:spring-security-oauth2-jose` 1건**뿐이다.
Maven 생태계 패키지라 `slopcheck`(PyPI/npm 대상)의 적용 범위를 벗어난다. 대신 더 강한 기준인
**Boot 4.1.0 공식 BOM의 관리 대상 여부 + Context7 공식 문서 존재**로 검증했다(§Standard Stack).

| Package | Registry | Age | Downloads | Source Repo | 검증 방법 | Disposition |
|---------|----------|-----|-----------|--------------|-----------|-------------|
| `spring-security-oauth2-jose` | Maven Central | Spring Security 프로젝트의 핵심 모듈(수년째 유지, 최초 릴리스는 5.x대) | Spring Security 자체가 초당 수백만 다운로드 규모 | github.com/spring-projects/spring-security | Boot 4.1.0 BOM(`spring-security-bom:7.1.0`)에 명시적 관리 + Context7 공식 레퍼런스(`/websites/spring_io_spring-security_reference_7_0`)에 `NimbusJwtEncoder`/`NimbusJwtDecoder` API 문서 존재 확인 | Approved — `[VERIFIED: Maven Central + Boot 4.1.0 BOM + Context7]` |

**Packages removed due to slopcheck [SLOP] verdict:** 없음 (slopcheck 대상 생태계 아님)
**Packages flagged as suspicious [SUS]:** 없음

내부적으로 Nimbus JOSE+JWT(`com.nimbusds:nimbus-jose-jwt`)를 전이 의존성으로 끌어오지만, 이는
`spring-security-oauth2-jose`가 관리하는 전이 의존성이라 직접 버전을 지정하지 않는다(BOM 원칙).

## Architecture Patterns

### System Architecture Diagram

```
[FE: 카카오 리다이렉트+state 검증, code 전달]      [FE: ID/PW 폼]
              |                                          |
              v                                          v
   POST /api/auth/kakao/login {code}          POST /api/auth/admin/login {loginId,password}
              |                                          |
              v                                          v
   +-------------------------+              +--------------------------+
   | KakaoAuthService        |              | AdminAuthService         |
   | 1) RestClient로         |              | 1) Admin 조회             |
   |    kauth.kakao.com      |              | 2) PasswordEncoder.matches|
   |    /oauth/token 교환     |              +--------------------------+
   | 2) kapi.kakao.com               |                    |
   |    /v2/user/me 로 kakaoId 조회   |                    |
   | 3) kakaoId로 Member find-or-     |                    |
   |    create (없으면 PENDING 생성)  |                    |
   +----------------+----------------+                    |
                     |                                     |
                     v                                     v
              +----------------------------------------------+
              |  TokenService (공용, principalType=MEMBER/ADMIN) |
              |  - JwtEncoder(NimbusJwtEncoder)로 access 발급    |
              |  - refresh 발급 + DB(refresh_token) 저장         |
              +----------------------------------------------+
                                    |
                                    v
                     access/refresh 토큰 쌍 응답 (JSON)

--- 이후 모든 인증 필요 요청 ---

  Authorization: Bearer <access>
        |
        v
  +----------------------------+
  | JwtAuthenticationFilter    |  <- addFilterBefore(UsernamePasswordAuthenticationFilter)
  |  1) 헤더에서 토큰 추출        |     (예외를 던지지 않는다 — 실패 시 컨텍스트 비움만)
  |  2) NimbusJwtDecoder 검증   |
  |  3) principalType/Id 클레임 |
  |     추출 → Member/Admin      |
  |     현재 상태 DB 재조회       |  <- D-033: 상태 게이트는 클레임이 아니라 DB 기준
  |  4) 유효하면 SecurityContext|
  |     에 Authentication 설정   |
  +----------------------------+
        |
        v  (미인증 상태로 통과 가능)
  +----------------------------+
  | authorizeHttpRequests       |  역할(ROLE_MEMBER/ROLE_ADMIN) 기반 1차 게이트
  +----------------------------+
        |
        v
  +----------------------------+
  | ExceptionTranslationFilter  |  <- 이 필터부터 예외를 정상적으로 401/403으로 번역
  | AuthorizationFilter         |
  +----------------------------+
        |
        v
  +----------------------------+
  | 컨트롤러 → 서비스            |  상태(ACTIVE) 게이트처럼 URL 패턴으로 못 가르는 조건은
  |  (필요시 principal의 최신    |  여기서 도메인 예외로 던지고 GlobalExceptionHandler가 처리
  |   상태를 서비스에서 재검증)   |
  +----------------------------+

--- refresh 갱신 ---

  POST /api/auth/refresh {refreshToken}
        |
        v
  refresh_token 테이블에서 해시 조회 → 만료/폐기 확인
        |
        v
  회전: 기존 row 폐기(또는 삭제) + 새 access/refresh 발급 + 새 row 저장
        (이미 폐기된 토큰 재사용 감지 시 → 해당 principal의 모든 refresh 폐기)
```

### Recommended Project Structure

```
src/main/kotlin/com/goldwrestling/
├── auth/                          # 신규 — 로그인·토큰 (CONTEXT.md code_context에서 이미 지정)
│   ├── KakaoAuthController.kt     # POST /api/auth/kakao/login
│   ├── AdminAuthController.kt     # POST /api/auth/admin/login
│   ├── TokenController.kt         # POST /api/auth/refresh, DELETE /api/auth/logout
│   ├── KakaoAuthService.kt        # RestClient 호출 + Member find-or-create
│   ├── AdminAuthService.kt        # Admin 조회 + PasswordEncoder 검증
│   ├── TokenService.kt            # JWT 발급 + refresh 회전(공용, MEMBER/ADMIN 겸용)
│   ├── RefreshToken.kt            # 엔티티
│   ├── RefreshTokenRepository.kt
│   ├── kakao/
│   │   ├── KakaoTokenResponse.kt  # 카카오 API 응답 매핑 DTO(외부 계약, 우리 API DTO 아님)
│   │   └── KakaoUserResponse.kt
│   └── dto/
│       ├── KakaoLoginRequest.kt
│       ├── AdminLoginRequest.kt
│       └── TokenResponse.kt       # access/refresh 응답
├── config/
│   ├── SecurityConfig.kt          # 기존 파일 교체 — 필터체인·인가 규칙
│   ├── JwtProperties.kt           # 신규 — CorsProperties와 동일 패턴(@ConfigurationProperties)
│   ├── KakaoProperties.kt         # 신규
│   └── JwtConfig.kt               # 신규 — JwtEncoder/JwtDecoder 빈 정의
├── member/
│   ├── MemberController.kt        # 기존 확장 — 관리자용 목록/상세/검색/상태변경, 본인 프로필
│   ├── MemberService.kt
│   ├── MemberRepository.kt        # JpaSpecificationExecutor 추가
│   ├── MemberSpecifications.kt    # 신규 — 검색어/상태/온보딩완료 조합 쿼리
│   ├── OnboardingController.kt    # 신규 또는 MemberController에 통합 — 온보딩 제출
│   ├── Member.kt                  # 기존 — kakaoId, rejectionReason 필드 추가
│   └── dto/
├── admin/
│   ├── Admin.kt                   # 기존 — loginId, passwordHash 필드 추가
│   ├── AdminSeeder.kt             # 신규 — ApplicationRunner 기반 멱등 시드
│   └── ...
└── common/
    └── time/
        └── AuditingDateTimeProvider.kt  # 신규 — Clock 기반 @CreatedDate 지원
```

### Pattern 1: 커스텀 JWT 인증 필터는 예외를 던지지 않는다

**What:** `OncePerRequestFilter`에서 토큰이 없거나 무효해도 `AuthenticationException`을 던지지 않고,
`SecurityContextHolder`를 비운 채로 `filterChain.doFilter(request, response)`를 호출해 다음 필터로 넘긴다.
**When to use:** 이 프로젝트의 유일한 인증 필터(`JwtAuthenticationFilter`) 구현 시 항상.
**Why (근거):** Spring Security의 표준 필터 순서는
`... UsernamePasswordAuthenticationFilter → ... → AnonymousAuthenticationFilter → ExceptionTranslationFilter → AuthorizationFilter`이다
(`[VERIFIED: Context7 spring-security-reference-7_0, servlet/architecture.html DEBUG 로그 예시]`).
커스텀 필터를 `addFilterBefore(filter, UsernamePasswordAuthenticationFilter::class.java)`로 등록하면
`ExceptionTranslationFilter`**보다 앞**에 위치하게 되어, 그 필터의 try-catch 범위 밖에서 예외가 발생한다 —
즉 우리 `GlobalExceptionHandler`도, Spring Security의 `AuthenticationEntryPoint`도 잡지 못하고 컨테이너
기본 에러 페이지(HTML)가 나가 RFC 9457 계약이 깨진다. 인증 실패는 필터가 그냥 "미인증"으로 넘기고,
실제 401/403 판단과 응답 작성은 뒤에 있는 `AuthorizationFilter`(→ `ExceptionTranslationFilter`가 감쌈)에
맡긴다.
```kotlin
// Source: 패턴 — Context7 Spring Security 7.0 필터 순서 문서 기반 추론, 코드는 표준 관용구
class JwtAuthenticationFilter(
    private val jwtDecoder: JwtDecoder,
    private val memberRepository: MemberRepository,
    private val adminRepository: AdminRepository,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractBearerToken(request)
        if (token != null) {
            try {
                val jwt = jwtDecoder.decode(token)
                val authentication = buildAuthentication(jwt) // DB 상태 재조회 포함, 실패 시 null
                if (authentication != null) {
                    SecurityContextHolder.getContext().authentication = authentication
                }
            } catch (ex: JwtException) {
                // 의도적으로 무시 — 컨텍스트를 비운 채 다음 필터로. 401 응답은 AuthorizationFilter가 만든다.
            }
        }
        filterChain.doFilter(request, response)
    }
}
```

### Pattern 2: 401/403을 ProblemDetail로 통일 — 별도 EntryPoint/Handler 필요

**What:** `GlobalExceptionHandler`는 `AccessDeniedException`/`AuthenticationException`을 의도적으로
되던진다(기존 코드 주석에 이미 명시됨). 이 둘을 잡아 `ProblemDetail`(+`code`)로 응답하는 것은
`AuthenticationEntryPoint`/`AccessDeniedHandler`의 몫이다.
**When to use:** `SecurityConfig`의 `exceptionHandling { }` 블록에 커스텀 빈 두 개를 등록.
**근거:** Spring Security 7 자체에는 OAuth2 리소스서버 검증 실패를 RFC 9457로 내보내는 내장 기능이
아직 없다(`[CITED: github.com/spring-projects/spring-security issue #15549 — RFC 9457 지원은 아직 feature
request 상태]`). 따라서 이 프로젝트처럼 `code` 필드까지 포함한 형식을 원하면 직접 구현해야 한다.
`GlobalExceptionHandler`의 `resolveErrorCode`/`code` 주입 로직과 형태를 맞추기 위해, JSON 직렬화 방식을
공유하는 작은 헬퍼(`ProblemDetailResponseWriter` 등)를 `common/error`에 두고 EntryPoint/Handler 양쪽에서
재사용하는 것을 권장한다(중복 방지).
```kotlin
// Source: 패턴 — Context7 AuthenticationEntryPoint/AccessDeniedHandler 인터페이스 문서 기반
@Component
class ProblemDetailAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.")
        problem.setProperty("code", "UNAUTHENTICATED") // docs/error-codes.md에 등록 필요
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = "application/problem+json"
        objectMapper.writeValue(response.writer, problem)
    }
}
```

### Pattern 3: 인가 규칙 — URL 기반(역할) + 서비스 계층 상태 게이트 하이브리드

**What:** `authorizeHttpRequests`로 공개(`/api/auth/**`) / `ROLE_MEMBER`(`/api/members/me`,
`/api/auth/onboarding` 등) / `ROLE_ADMIN`(`/api/admin/**`)까지만 URL 패턴으로 가른다. "`ACTIVE`가
아니면 막는다"처럼 **같은 역할 안에서 상태에 따라 갈리는 조건**은 URL 패턴이 아니라 서비스 계층에서
principal의 최신 DB 상태를 다시 조회해 도메인 예외(`DomainException` 하위)로 던진다.
**When to use:** 이 phase 및 이후 모든 phase의 회원 대상 엔드포인트.
**Why:** policies §5의 상태 규칙은 "PENDING/온보딩 미완료는 승인 대기 정보 외 접근 불가"처럼 **엔드포인트별로
예외가 있는 규칙**이라(`/api/members/me`, 온보딩 제출 API는 PENDING도 접근 가능해야 함), 단일
`@PreAuthorize("hasRole('MEMBER') and @memberGate.isActive(...)")` 규칙을 전역에 걸면 예외 엔드포인트를
또 따로 열어줘야 해서 URL 인가 규칙이 오히려 더 복잡해진다. 반대로 서비스 계층 검사는 "이 엔드포인트가
ACTIVE를 요구하는가"를 코드 리뷰에서 바로 보이게 하고, 도메인 예외 경로(`GlobalExceptionHandler`)를 그대로
재사용해 `ProblemDetail` 형식이 자동으로 통일된다. **주의:** 서비스 계층 검사이므로 "이 서비스 메서드에
상태 검사를 빠뜨리는" 실수가 가능하다 — 신규 회원 대상 엔드포인트를 만들 때마다 이 검사가 필요한지
체크리스트화할 것을 권장한다(§Common Pitfalls).

### Anti-Patterns to Avoid

- **JWT 클레임에 회원 상태(`status`)를 넣고 그것만으로 인가 판단**: D-033이 명시적으로 금지한다 —
  refresh를 지워도 최대 30분 access 유효기간 동안 구멍이 생긴다. 클레임에는 `principalType`,
  `principalId`, `iat`, `exp` 정도만 넣고 상태는 항상 DB에서 재조회한다.
- **`@Transactional`이 걸린 서비스 메서드 안에서 카카오 API를 호출**: conventions §7 "트랜잭션 안에서
  외부 API 호출 금지"에 해당. 카카오 호출(토큰 교환 + 사용자 조회)은 트랜잭션 밖에서 먼저 끝내고,
  그 결과(kakaoId 등)만 가지고 짧은 트랜잭션(Member find-or-create)을 연다.
- **`refresh_token`에 원문 토큰을 그대로 저장**: DB 유출 시 즉시 탈취로 이어진다. 비밀번호만큼
  느린 해시(BCrypt)일 필요는 없지만(토큰 자체가 이미 고엔트로피 랜덤값), SHA-256 등으로 해시해
  `token_hash` 컬럼에 저장하고 조회도 해시로 한다.
- **Admin에 `role` 컬럼을 따로 두기**: 이 스키마에서 역할은 "어느 테이블 행인가"로 이미 결정된다
  (Member 행 = MEMBER, Admin 행 = ADMIN). 별도 `role` 컬럼은 두 정보가 어긋날 수 있는 불필요한 중복이다.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|--------------|-----|
| JWT 서명·검증(HMAC) | 직접 `HMAC-SHA256` 계산 + Base64Url 인코딩 | `NimbusJwtEncoder`/`NimbusJwtDecoder`(`spring-security-oauth2-jose`) | 서명 검증에서의 사소한 실수(예: 알고리즘 confusion, 타이밍 공격 취약 비교)가 인증 우회로 직결된다. Nimbus는 이 클래스의 취약점을 이미 다뤄본 라이브러리다 |
| 비밀번호 해싱 | 직접 salt+hash 구현 | `PasswordEncoder`(`BCryptPasswordEncoder` 또는 `DelegatingPasswordEncoder`) | 적응형 해싱(work factor)과 salt 처리를 직접 하면 실수하기 쉽고, 이미 starter-security에 포함돼 추가 비용이 0이다 |
| 카카오 인가코드 검증·토큰 파싱 | 직접 OAuth 스펙 구현 | 카카오 공식 REST API(`kauth.kakao.com/oauth/token`, `kapi.kakao.com/v2/user/me`)를 `RestClient`로 호출 | 이건 "라이브러리 대신 API"의 사례다 — 카카오가 제공하는 REST 엔드포인트 자체가 이미 검증된 계약이므로 SDK조차 필요 없다 |
| refresh 토큰 재사용 감지 | 커스텀 anomaly-detection 로직 | 단순 규칙: "이미 폐기된 토큰이 다시 제시되면 해당 principal의 모든 refresh를 즉시 폐기" | 완전한 디바이스 핑거프린팅·이상탐지는 이 프로젝트 규모(솔로 개발, MVP)에 과하다. 폐기 여부 플래그 하나로 충분한 방어가 된다 |

**Key insight:** 이 phase는 "만들 게 없어서 오히려 위험한" phase다 — 라이브러리 선택은 거의 자명하지만
(이미 있는 Spring Security 생태계 안에서 해결됨), **필터 순서·트랜잭션 경계·상태 재조회 시점** 같은
배선(wiring) 실수가 조용히 보안 구멍을 만든다. 코드 리뷰에서 "무엇을 썼는가"보다 "언제·어디서 상태를
확인하는가"를 더 봐야 한다.

## Common Pitfalls

### Pitfall 1: 커스텀 JWT 필터가 예외를 던져 401 응답이 HTML로 나감

**What goes wrong:** `JwtAuthenticationFilter`(또는 유사 이름)에서 토큰 검증 실패 시
`throw BadCredentialsException(...)`처럼 예외를 직접 던지면, `addFilterBefore(..., UsernamePasswordAuthenticationFilter::class.java)`
위치가 `ExceptionTranslationFilter`보다 앞이라 아무도 못 잡는다.
**Why it happens:** 많은 튜토리얼 예제가 필터 안에서 바로 예외를 던지는 코드를 보여준다(세션 기반
`UsernamePasswordAuthenticationFilter`의 관용구를 그대로 복붙).
**How to avoid:** §Architecture Patterns Pattern 1 그대로 — 필터는 실패 시 조용히 다음으로 넘기고,
실제 거부는 `AuthorizationFilter`가 하게 둔다. 필터 안에서 즉시 응답을 써야 하는 예외적 상황(예: 만료된
토큰과 서명 자체가 틀린 토큰을 구분해 다른 메시지를 주고 싶을 때)이라면 직접 `AuthenticationEntryPoint`를
주입받아 호출하고 `return`한다(예외를 던지지 않음).
**Warning signs:** 401이어야 할 응답이 `text/html`로 나가거나, 통합테스트에서 `problemdetail` JSON 대신
스택트레이스가 보인다.

### Pitfall 2: 관리자 시드를 Flyway `INSERT`로 넣다가 플레이스홀더 미설정 시 깨진 계정이 영구 고정

**What goes wrong:** D-031의 Branch 시드 패턴(Flyway `INSERT`)을 그대로 관리자 계정에 적용하면,
`spring.flyway.placeholders.adminPasswordHash` 같은 값이 비어있는 채로 마이그레이션이 한 번 실행되는
순간 그 값(빈 문자열 또는 `${adminPasswordHash}` 리터럴)이 해당 환경 DB에 영구히 박힌다. 이미 적용된
마이그레이션은 재실행되지 않고, 커밋된 마이그레이션 파일도 수정 금지라 고치려면 새 마이그레이션(UPDATE)이
필요하다.
**Why it happens:** Flyway 시드는 "모든 환경에 같은 값"이 들어가야 하는 데이터(Branch명 등)에는
안전하지만, 관리자 비밀번호처럼 "환경마다 달라야 하는 시크릿"에는 이 안전성이 반대로 작용한다.
**How to avoid:** `ApplicationRunner`로 매 기동 시 `login_id` 존재 여부를 확인하고, 없으면
`ADMIN_SEED_LOGIN_ID`/`ADMIN_SEED_PASSWORD`(평문, `.env`/환경변수)를 읽어 `PasswordEncoder`로 해시한
뒤 INSERT하는 멱등 로직으로 처리한다. 환경변수가 비어있으면 시드를 건너뛰고 경고 로그만 남긴다(앱은
정상 기동).
**Warning signs:** 로컬 DB 초기화 후 관리자 로그인이 항상 실패하는데 원인이 안 보임 — 실제로는
빈 문자열 해시가 박혀 어떤 비밀번호로도 `matches()`가 false.

### Pitfall 3: `Member`와 `Admin`이 별도 테이블인데 토큰에 principal 구분이 없어 ID 충돌

**What goes wrong:** JWT의 `sub` 클레임에 그냥 숫자 ID만 넣으면, `Member(id=1)`과 `Admin(id=1)`이
같은 토큰처럼 취급될 위험이 있다(필터가 어느 리포지토리에서 조회할지 알 수 없음).
**Why it happens:** "동일한 JWT 체계"(D-026)를 "동일한 클레임 스키마"로 오해하면 principal 종류
구분을 빠뜨리기 쉽다.
**How to avoid:** 클레임에 `principalType`(`MEMBER`/`ADMIN`, glossary의 역할 코드와 동일 값 사용)과
`principalId`를 분리해서 넣는다. `refresh_token` 테이블도 동일하게 principal 종류를 구분하는 컬럼
쌍(nullable FK 두 개 + CHECK 제약, 또는 `principal_type`+`principal_id` 조합)을 둔다(§Schema).
**Warning signs:** 관리자 계정으로 로그인했는데 회원 데이터가 노출되거나, 반대로 회원 토큰으로 관리자
API 인가가 우연히 통과.

### Pitfall 4: `@ManyToOne(fetch = LAZY)`인 `Member.branch`를 SecurityContext principal에 그대로 담기

**What goes wrong:** JWT 필터가 트랜잭션 밖(필터 레벨)에서 `Member` 엔티티를 로드해 그대로
Authentication의 principal로 넣으면, 이후 컨트롤러/서비스에서 지연 로딩 필드(`branch` 등)에 접근할 때
`LazyInitializationException`이 난다(필터 시점엔 영속성 컨텍스트가 이미 닫혀 있을 수 있음).
**Why it happens:** "엔티티를 그대로 쓰면 편하다"는 유혹 + D-019(엔티티를 컨트롤러 밖으로 노출 금지)는
알아도 필터 내부 principal까지는 놓치기 쉽다.
**How to avoid:** principal은 엔티티가 아니라 필요한 필드만 담은 작은 값 객체(`AuthenticatedPrincipal`
구현체 — `id`, `status`, `principalType` 정도)로 만든다. 서비스에서 실제 엔티티가 필요하면 그 안에서
다시 리포지토리로 조회한다(어차피 D-033 때문에 상태는 재조회해야 한다).
**Warning signs:** 인증된 요청인데 특정 엔드포인트에서만 500(`LazyInitializationException`)이 남.

## Code Examples

### JWT 발급 (NimbusJwtEncoder, HS256 대칭키)

```kotlin
// Source: 패턴 조합 — Context7 NimbusJwtEncoder API 문서(생성자 시그니처) +
// WebSearch로 교차확인한 ImmutableSecret 관용구(MEDIUM confidence — 공식 예제는 RSA 기반이라
// 대칭키 버전은 커뮤니티 패턴으로 검증했음을 명시)
@Configuration
class JwtConfig(
    private val jwtProperties: JwtProperties,
) {
    private val secretKey: SecretKeySpec
        get() = SecretKeySpec(jwtProperties.secret.toByteArray(), "HmacSHA256")

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secretKey))

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
}

// 발급
fun issueAccessToken(principalType: PrincipalType, principalId: Long, clock: Clock): String {
    val now = clock.instant()
    val claims = JwtClaimsSet.builder()
        .issuedAt(now)
        .expiresAt(now.plus(Duration.ofMinutes(30)))
        .subject(principalId.toString())
        .claim("principalType", principalType.name)
        .build()
    val header = JwsHeader.with(MacAlgorithm.HS256).build()
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
}
```

### 카카오 토큰 교환 + 사용자 조회 (RestClient)

```kotlin
// Source: 카카오 공식 문서(developers.kakao.com/docs/latest/en/kakaologin/rest-api) —
// [CITED] 파라미터·엔드포인트 확인. RestClient 사용법은 Spring 표준(webmvc starter에 이미 포함)
@Service
class KakaoAuthService(
    private val kakaoRestClient: RestClient, // 별도 빈으로 baseUrl 없이 구성 (엔드포인트가 2개 다른 호스트)
    private val kakaoProperties: KakaoProperties,
) {
    fun exchangeToken(code: String): KakaoTokenResponse =
        kakaoRestClient.post()
            .uri("https://kauth.kakao.com/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "authorization_code")
                    add("client_id", kakaoProperties.restApiKey)
                    add("redirect_uri", kakaoProperties.redirectUri)
                    add("code", code)
                    add("client_secret", kakaoProperties.clientSecret)
                },
            )
            .retrieve()
            .body(KakaoTokenResponse::class.java)!!

    fun fetchUser(kakaoAccessToken: String): KakaoUserResponse =
        kakaoRestClient.get()
            .uri("https://kapi.kakao.com/v2/user/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $kakaoAccessToken")
            .retrieve()
            .body(KakaoUserResponse::class.java)!!
}
```

### 온보딩 완료 판정 (D-025 — 별도 상태 없이 필드 존재 여부)

```kotlin
// Source: 프로젝트 자체 규약(D-025, policies §5.1) — 외부 문서 인용 아님
val Member.isOnboardingComplete: Boolean
    get() = !name.isNullOrBlank() && !phoneNumber.isNullOrBlank()
```

### 카카오 API 목킹 (MockRestServiceServer + RestClient)

```kotlin
// Source: [CITED] docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html
// "Spring Framework historically provided MockRestServiceServer for testing RestClient or RestTemplate"
val builder = RestClient.builder()
val mockServer = MockRestServiceServer.bindTo(builder).build()
mockServer.expect(requestTo("https://kauth.kakao.com/oauth/token"))
    .andRespond(withSuccess(kakaoTokenResponseJson, MediaType.APPLICATION_JSON))
val kakaoRestClient = builder.build()
// kakaoRestClient를 KakaoAuthService에 주입해 실제 네트워크 호출 없이 검증
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|-------------------|----------------|--------|
| 카카오 SDK가 FE에서 액세스 토큰까지 발급받아 BE로 전달 | BE가 인가 코드만 받아 직접 토큰 교환(D-032) | 이 프로젝트 결정(2026-08) | 시크릿이 브라우저에 노출되지 않음. 업계에서도 "BFF가 토큰 교환을 대행"하는 패턴이 일반적 |
| `spring-security-oauth2-client`로 전체 OAuth 리다이렉트 위임 | STATELESS 커스텀 필터 + 수동 API 호출 | — | 이 프로젝트는 세션 기반 OAuth2Login 흐름과 상충해 처음부터 채택하지 않음(D-032 기각 대안) |

**Deprecated/outdated:** 해당 없음 — 이번 phase에서 다루는 모든 API(NimbusJwtEncoder/Decoder,
RestClient, Spring Security 7 필터체인)는 Boot 4.1/Security 7 기준 현재 API다.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|-----------------|
| A1 | `NimbusJwtEncoder` + `ImmutableSecret`로 HS256 대칭키 인코더를 구성하는 방법(생성자 시그니처는 공식 문서로 확인했으나, `ImmutableSecret` 관용구 자체는 WebSearch로 교차 확인) | Code Examples "JWT 발급" | 실제 구현 시 컴파일 에러 나면 `verify-boot4-api` 절차(§Boot4 표)대로 `./gradlew compileKotlin`으로 즉시 드러남 — 리스크 낮음(컴파일 타임에 걸러짐) |
| A2 | 관리자 시드를 `ApplicationRunner`로 처리하는 것이 Flyway 플레이스홀더 방식보다 낫다는 판단 | Standard Stack "Alternatives Considered", Common Pitfalls #2 | 팀(사용자) 취향에 따라 Flyway 플레이스홀더를 선호할 수도 있음 — 강한 근거(환경별 시크릿 차이)가 있어 리스크는 낮지만 결정 자체는 사용자 확인 권장 |
| A3 | refresh_token 재사용 감지를 "전량 폐기" 방식으로 구현하는 것이 이 프로젝트 규모에 적절하다는 판단 | Don't Hand-Roll | 멀티 디바이스 사용자가 실수로 옛 토큰을 다시 보내는 상황(네트워크 재시도 등)과 실제 탈취를 구분 못해 정상 사용자가 전량 로그아웃될 수 있음 — MVP 단계에서는 수용 가능한 트레이드오프로 판단 |
| A4 | `refresh_token` 스키마를 "nullable FK 두 개 + CHECK" 방식으로 설계하는 것이 principal_type 문자열 컬럼 방식보다 낫다는 판단 | Anti-Patterns, Recommended Project Structure | 강한 참조 무결성(FK)이 이 프로젝트 규모(솔로 개발, 관리자 소수)에 필요 이상일 수 있음 — 대안(문자열 principal_type)도 충분히 타당해 Open Questions에 재확인 필요 |

## Open Questions (RESOLVED)

> 세 항목 모두 플래닝 단계에서 결정됨 — 1번은 D-041(유니크 제약 없음, 하이픈 제거 저장),
> 2번은 D-037(nullable FK 쌍 + CHECK 제약), 3번은 02-02 Task 1(admin 상태·역할 컬럼 없음, KDoc에 사유 기록).

1. **`phone_number`에 DB 유니크 제약을 걸 것인가** — RESOLVED: D-041 (유니크 제약 없음)
   - What we know: policies §5.1 "회원 관리·식별의 기준은 이름 + 전화번호"라고만 되어 있고, 유니크
     제약을 걸라는 명시적 문장은 없다.
   - What's unclear: 실제로 한 전화번호로 두 명이 가입 시도할 수 있는 시나리오(가족 공유폰 등)를
     허용해야 하는지, 아니면 중복 가입 방지가 목적인지.
   - Recommendation: MVP는 유니크 제약 없이 진행하고(운영 규모가 작아 관리자가 직접 확인 가능),
     필요해지면 v2에서 추가 — 단, discuss-phase나 계획 단계에서 사용자에게 한 번 확인 권장.

2. **refresh_token의 principal 참조 방식 — nullable FK 두 개 vs `principal_type`+`principal_id` 문자열** — RESOLVED: D-037 (nullable FK 쌍 + CHECK)
   - What we know: Member/Admin이 별도 테이블이라 단일 FK로 표현 불가.
   - What's unclear: 이 프로젝트가 지금까지 다대다에는 서로게이트 PK를 쓰는 등(admin_branch) FK
     정합성을 중시해왔다(add-migration §2). nullable FK 쌍 + CHECK 제약이 이 관례와 더 맞는지,
     아니면 문자열 조합이 더 단순한지.
   - Recommendation: nullable FK 쌍 + CHECK 제약을 1차 권장(§Architecture Patterns, Anti-Patterns).
     계획 단계에서 add-migration 스킬 절차대로 실제 스키마를 작성하며 재확인.

3. **관리자 계정에 상태(`status`)/역할 세분화가 필요한가** — RESOLVED: 02-02 Task 1 (이번 phase 스코프 밖, login_id/password_hash만 추가)
   - What we know: requirements·policies 어디에도 Admin의 상태 전이나 다중 역할(예: 슈퍼관리자 vs
     지점관리자)이 언급되지 않는다. `AdminBranch`(다대다)만으로 지점 범위가 표현된다.
   - What's unclear: 이번 phase 스코프에 없다는 것만 확인됨 — v1 범위 밖으로 간주해도 되는지.
   - Recommendation: 이번 phase는 Admin에 `login_id`/`password_hash`만 추가하고 상태·세부 역할은
     스코프 밖으로 명시. 필요해지면 별도 요구사항으로 논의.

## Environment Availability

이 phase는 코드/설정 변경과 신규 Gradle 의존성 1건(§Standard Stack, Boot BOM 관리 대상)만 필요하다.
외부 서비스 의존은 카카오 API(런타임에만 필요, 로컬 개발/테스트는 `MockRestServiceServer`로 대체 가능)뿐이다.

| Dependency | Required By | Available | Version | Fallback |
|------------|--------------|-----------|---------|----------|
| PostgreSQL(Docker Compose) | 회원/관리자/refresh_token 스키마 | ✓ (Phase 1에서 이미 구성) | — | — |
| 카카오 개발자 콘솔 앱(REST API 키·Redirect URI 등록) | AUTH-01 실제 연동 테스트 | 미확인 — 로컬 환경에 등록 여부는 사용자만 알 수 있음 | — | 단위/통합테스트는 `MockRestServiceServer`로 카카오 API 자체를 대체 가능하므로 개발·검증 자체는 이 등록 없이도 진행 가능. 단, **실제 카카오 로그인 E2E 확인**(콘솔 등록 값)은 사용자가 별도로 준비해야 함 |
| `spring-security-oauth2-jose` 아티팩트 | JWT 발급·검증 | ✓ (Maven Central에서 확인됨) | 7.1.0(Boot BOM 관리) | — |

**Missing dependencies with no fallback:** 없음
**Missing dependencies with fallback:** 카카오 개발자 콘솔 앱 등록 — 위 표 참고

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit5 (Kotlin) + MockMvc(`spring-boot-starter-webmvc-test`) + Testcontainers(`testcontainers-postgresql`) — 기존 Phase 1 구성 그대로 |
| Config file | `build.gradle.kts`(`tasks.withType<Test>`), `src/test/kotlin/.../TestcontainersConfiguration.kt` |
| Quick run command | `./gradlew test --tests "com.goldwrestling.auth.*"` (신규 패키지만) |
| Full suite command | `./gradlew build` (ktlintFormat 이후 — conventions §11.5) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|---------------------|--------------|
| AUTH-01 | 카카오 로그인 시 최초 가입은 `PENDING` 생성 | 통합(MockRestServiceServer로 카카오 목킹) | `./gradlew test --tests "*KakaoAuthController*"` | ❌ Wave 0 |
| AUTH-02 | access/refresh 발급, refresh로 갱신 | 통합 | `./gradlew test --tests "*TokenController*"` | ❌ Wave 0 |
| AUTH-03 | 관리자 ID/PW 로그인 | 통합 | `./gradlew test --tests "*AdminAuthController*"` | ❌ Wave 0 |
| AUTH-04 | `PENDING` 회원의 제한된 접근 | 통합(SecurityMockMvcRequestPostProcessors) | `./gradlew test --tests "*SecurityFilterChain*"` | ❌ Wave 0 |
| AUTH-05 | 온보딩 형식 검증(전화번호 정규식 등) | 단위 | `./gradlew test --tests "*OnboardingValidation*"` | ❌ Wave 0 |
| AUTH-06 | 온보딩 미완료 재로그인 재식별 | 단위(`isOnboardingComplete`) + 통합 | `./gradlew test --tests "*OnboardingStatus*"` | ❌ Wave 0 |
| MEMBER-01 | 승인 목록 = 온보딩 완료 `PENDING`만 | 통합(Testcontainers, Specification 쿼리) | `./gradlew test --tests "*MemberSpecification*"` | ❌ Wave 0 |
| MEMBER-02 | 이름·전화번호 검색 | 통합 | `./gradlew test --tests "*MemberSearch*"` | ❌ Wave 0 |
| MEMBER-03 | 상태 변경 시 refresh 무효화 | 통합 | `./gradlew test --tests "*MemberStatusChange*"` | ❌ Wave 0 |
| MEMBER-04 | 본인 프로필 조회 | 통합 | `./gradlew test --tests "*MemberProfile*"` | ❌ Wave 0 |
| (동시 로그인/회전) | refresh 회전 시 이전 토큰 재사용 감지 | 단위 + 통합 | `./gradlew test --tests "*RefreshTokenRotation*"` | ❌ Wave 0 |
| (필터 순서 회귀) | 만료/위조 토큰 요청이 `application/problem+json` 401로 응답 | 통합 | `./gradlew test --tests "*JwtAuthenticationFilter*"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** 해당 태스크가 건드린 클래스의 테스트만 (`--tests "*ClassName*"`)
- **Per wave merge:** `./gradlew build` (ktlintFormat 포함 전체)
- **Phase gate:** `/gsd:verify-work` 전 전체 스위트 green

### Wave 0 Gaps

- [ ] `auth` 패키지 테스트 디렉터리 자체가 없음(`src/test/kotlin/com/goldwrestling/auth/`) — 신규 생성 필요
- [ ] 카카오 API 응답 픽스처(JSON) — `src/test/resources/kakao/` 등에 샘플 응답 고정
- [ ] `Clock` 테스트 고정 패턴은 Phase 1에 선례 없음(첫 시각 의존 로직) — `TestClockConfig` 또는
      `@TestConfiguration`으로 고정된 `Clock` 빈을 auth 통합테스트에서 재사용할 수 있게 준비
- [ ] `MockRestServiceServer` 기반 카카오 목킹 헬퍼 — 여러 테스트에서 재사용되므로 공용 유틸로 추출 권장

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|----------------|---------|--------------------|
| V2 Authentication | Yes | 카카오 OAuth(인가코드) + 관리자 ID/PW, `PasswordEncoder`(BCrypt) — 자체 패스워드 정책은 관리자 소수 계정이라 별도 정책(복잡도 규칙 등) MVP 스코프 밖으로 판단(Open Question 아님, 명시적 스코프 판단) |
| V3 Session Management | Yes(무상태 변형) | JWT access(30분)+refresh(14일, DB 저장+회전). 세션 쿠키 없음(STATELESS) — refresh 회전·재사용감지가 세션 하이재킹 방어 역할 |
| V4 Access Control | Yes | `authorizeHttpRequests`(역할) + 서비스 계층 상태 게이트(ACTIVE). 서버 측(DB) 재검증이 핵심 — 클라이언트가 보내는 토큰 클레임을 신뢰하지 않음 |
| V5 Input Validation | Yes | `jakarta.validation`(`@NotBlank`, `@Pattern`)으로 전화번호 형식 검증(온보딩), `@Size`로 loginId/password 길이 제한 |
| V6 Cryptography | Yes | 비밀번호: `BCryptPasswordEncoder`(적응형 해시, 직접 구현 금지). JWT 서명: HMAC-SHA256(`NimbusJwtEncoder`), 시크릿은 `.env`/환경변수(`JWT_SECRET`)로만 주입, 코드·문서에 실값 금지(CLAUDE.md 시크릿 규칙) |

### Known Threat Patterns for Spring Boot 4 + JWT

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|------------------------|
| JWT 알고리즘 confusion(예: `alg: none` 또는 대칭키를 비대칭키처럼 취급) | Spoofing | `NimbusJwtDecoder.withSecretKey(...).macAlgorithm(MacAlgorithm.HS256)`으로 알고리즘을 명시 고정 — 토큰 헤더의 `alg` 값을 신뢰하지 않음 |
| refresh 토큰 탈취 후 장기간 재사용 | Spoofing, Elevation of Privilege | DB 저장 + 회전 + 재사용 감지(D-033, §Don't Hand-Roll) |
| 상태가 `INACTIVE`로 바뀐 회원의 기발급 access 토큰으로 계속 접근 | Elevation of Privilege | D-033 노트대로 상태 게이트를 매 요청 DB 기준으로 재검사 |
| 카카오 `client_secret` 노출 | Information Disclosure | 서버 환경변수에만 존재(D-032), FE에는 전달되지 않음, 로그에 남기지 않음 |
| 관리자 로그인 브루트포스 | Spoofing | MVP 스코프 밖으로 판단되지만(§Open Questions #3 근접) 최소한 실패 시 상세 사유 노출 금지(`AuthenticationException` 메시지를 그대로 응답에 넣지 않음) — rate limiting은 v1 요구사항에 없어 이번 phase 스코프 아님, 필요시 별도 논의 |
| CSRF | Tampering | STATELESS + 쿠키 미사용(Authorization 헤더의 Bearer 토큰만 사용)이므로 CSRF 공격 표면 자체가 없음 — `csrf { it.disable() }` 유지(기존 SecurityConfig 그대로) |

## Sources

### Primary (HIGH confidence)
- Context7 `/websites/spring_io_spring-security_reference_7_0` — 커스텀 필터 등록(`addFilterAfter`/필터 순서 DEBUG 로그), `NimbusJwtDecoder.withSecretKey`, `NimbusJwtEncoder` 생성자, `AuthenticationEntryPoint`/`AccessDeniedHandler` 인터페이스, RestClient 기반 OAuth2 클라이언트 커스터마이징
- Maven Central `spring-boot-dependencies-4.1.0.pom`, `spring-security-bom-7.1.0.pom` — `spring-security-oauth2-jose` 버전 관리 확인
- 카카오 공식 개발자 문서(`developers.kakao.com/docs/latest/en/kakaologin/rest-api`) — 토큰 교환/사용자 조회 엔드포인트·파라미터·기본 제공 필드(`id`, `connected_at`)

### Secondary (MEDIUM confidence)
- WebSearch로 교차 확인한 `NimbusJwtEncoder` + `ImmutableSecret` HS256 대칭키 구성 관용구(공식 예제는 RSA 기반 DPoP 예제뿐이라 대칭키 버전은 커뮤니티 패턴으로 검증)
- `docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html`(WebSearch로 발견, 공식 spring.io 도메인) — `MockRestServiceServer`가 `RestClient`/`RestTemplate` 둘 다 지원한다는 서술
- `docs.spring.io/spring-data/jpa/reference/auditing.html`(WebSearch로 링크만 확인, 본문 미열람) — `@EnableJpaAuditing(dateTimeProviderRef=...)` 커스텀 `DateTimeProvider` 빈 패턴

### Tertiary (LOW confidence)
- GitHub Issue `spring-projects/spring-security#15549` — RFC 9457 OAuth2 JWT 검증 에러 지원이 아직 feature request 상태라는 정황(이슈 자체는 확인했으나 최종 결론까지는 추적하지 않음 — "아직 내장 지원 없다"는 결론에는 영향 없음)

## Metadata

**Confidence breakdown:**
- Standard Stack(라이브러리 선택): HIGH — Context7 공식 문서 + Maven Central/BOM 직접 조회로 검증
- Architecture(필터 순서, 예외 전파 경로): HIGH — Context7 공식 문서의 필터 순서 로그 예시로 직접 확인
- JWT 인코더의 정확한 대칭키 구성 코드: MEDIUM — 공식 예제가 RSA 기반이라 HS256 버전은 커뮤니티 패턴 교차확인
- 스키마 설계(refresh_token FK 방식, 관리자 시드 방식): MEDIUM — 프로젝트 관례와의 정합성 논증이지 외부 검증 대상이 아님. Open Questions에 재확인 항목으로 남김
- Pitfalls: HIGH(필터 순서 관련) / MEDIUM(시드 방식 관련, 프로젝트 고유 리스크 판단)

**Research date:** 2026-08-02
**Valid until:** 30일(Spring Boot 4.1/Security 7 라인이 안정 릴리스라 API 변경 가능성 낮음. 단 Boot
4.1.x 패치 버전이 올라가면 `spring-security.version` 재확인 권장)
