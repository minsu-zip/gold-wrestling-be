# Phase 7: 컨테이너화·서버 구성 - Pattern Map

**Mapped:** 2026-09-08
**Files analyzed:** 11 (신설 8 · 수정 3)
**Analogs found:** 8 / 11 (나머지 3은 프로젝트 최초 파일 — RESEARCH.md 공식 소스가 유일한 근거)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `Dockerfile` | config (빌드 아티팩트) | batch (빌드 1회 → 이미지) | 없음 (레포에 기존 Dockerfile 없음) | no-analog — RESEARCH.md §Code Examples Pattern 1·2가 유일한 근거 |
| `.dockerignore` | config | — | `.gitignore` (제외 패턴 나열 스타일) | role-match |
| `deploy/compose.prod.yml` | config (오케스트레이션) | event-driven (healthcheck 게이트) | `docker-compose.yml` (postgres 서비스 정의) | exact (postgres 서비스는 그대로 이식) / partial (app·caddy 서비스는 신규) |
| 로컬 검증용 compose 오버라이드 (이름 재량) | config | — | `docker-compose.yml` + `deploy/compose.prod.yml`(작성 후) | role-match |
| `deploy/Caddyfile` | config (리버스 프록시) | request-response | 없음 (레포에 기존 Caddyfile 없음) | no-analog — RESEARCH.md §Architecture Patterns Pattern 4가 유일한 근거 |
| `deploy/server-setup.sh` | utility (프로비저닝 스크립트) | batch | `.claude/hooks/guard-ddl-auto.sh` (bash 관례: shebang, `set -...`, 가드 조건문) | partial (역할이 다름 — hook은 검증기, 이 파일은 프로비저너 — 하지만 프로젝트 유일의 bash 스타일 출처) |
| `src/main/resources/application.yml` (env 플레이스홀더 추가) | config | — | `application.yml` 자기 자신 (기존 `${ENV:default}` 섹션) | exact (같은 파일 확장) |
| `.env.example` (키 추가) | config | — | `.env.example` 자기 자신 (기존 섹션 구분·주석 스타일) | exact (같은 파일 확장) |
| `docs/operations.md` (신설) | documentation | — | `docs/conventions.md` (헤딩·표 스타일) | role-match |
| `docs/metrics.md` (신설) | documentation | — | `docs/conventions.md` (헤딩·표 스타일) | role-match |
| `docs/decisions.md` (append) | documentation | — | `docs/decisions.md` D-152 항목 (엔트리 포맷) | exact |
| `src/test/kotlin/.../SwaggerDisabledTest.kt` (신설) | test | request-response | `src/test/kotlin/com/goldwrestling/config/SecurityFilterChainTest.kt` | exact (같은 패키지 `config`, 같은 `@SpringBootTest`+`@AutoConfigureMockMvc`+MockMvc GET+status 조합, `/v3/api-docs` GET을 이미 테스트하고 있음) |

## Pattern Assignments

### `Dockerfile` (config, batch — 레포 루트, D-17)

**Analog:** 없음. RESEARCH.md §Architecture Patterns Pattern 1·2(공식 Boot 4.1 파셜 Dockerfile + 멀티플랫폼 패턴)를 그대로 골격으로 쓴다. 로컬 코드베이스에서 유일하게 연결되는 지점은 아래 두 곳뿐이다.

**빌더가 호출할 Gradle 태스크** (`build.gradle.kts` lines 138-143):
```kotlin
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").flatMap { it.archiveFile }
...
dependsOn("bootJar")
```
→ Dockerfile 빌더 스테이지는 `./gradlew bootJar -x test --no-daemon`만 호출하면 된다. `layered { }` 블록 설정이 `build.gradle.kts`에 없다 — Boot 4.1 Gradle 플러그인이 레이어드 jar를 기본 생성하므로 추가하지 않는다(A5, RESEARCH.md).

**JDK 버전 정합** (`build.gradle.kts` lines 15-18):
```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```
→ Dockerfile 빌더/런타임 베이스 모두 `21`로 고정(`eclipse-temurin:21-jdk-noble` / `eclipse-temurin:21-jre-noble`, **`-noble` 접미사 필수** — RESEARCH.md Pitfall 1).

**CI의 JDK 벤더 정합** (`.github/workflows/ci.yml`):
```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '21'
```
→ `temurin` 벤더 일치. CI가 Boot 4.1.0 + Kotlin 2.3.21 + Gradle 9.6.1(wrapper)로 이미 빌드에 성공하므로 Dockerfile 빌더 스테이지도 같은 조합을 그대로 신뢰해도 된다.

**실제로 복사해 쓸 골격은 RESEARCH.md 원문 인용을 그대로 쓴다** (07-RESEARCH.md lines 251-270, 공식 소스 검증 완료):
```dockerfile
# syntax=docker/dockerfile:1
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /builder
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon
RUN java -Djarmode=tools -jar build/libs/*.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-noble
WORKDIR /application
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
```
플랜 단계에서 `verify-boot4-api` 스킬로 `-Djarmode=tools ... extract` 옵션을 재확인한다(D-13).

---

### `.dockerignore` (config)

**Analog:** `.gitignore` (제외 패턴 나열 스타일 — 섹션 구분 주석 + 패턴)

**패턴** (`.gitignore` lines 1-25):
```
# --- 시크릿 (절대 커밋 금지) ---
.env
.env.*
!.env.example
...
# --- Build ---
build/
!src/**/build/
.gradle/
.kotlin/
out/
```
→ `.dockerignore`는 반대 방향(빌드 컨텍스트에서 제외)이지만 섹션 주석 스타일을 그대로 따른다. **`.env`는 반드시 포함**(RESEARCH.md §Security Domain — 이미지에 시크릿이 COPY되는 것을 원천 차단). `.git/`, `.gradle/`, `build/`(재빌드 대상이므로), `.idea/`, `*.md`(선택)도 포함.

---

### `deploy/compose.prod.yml` (config, event-driven)

**Analog:** `docker-compose.yml` (postgres 서비스 정의 — 그대로 이식)

**postgres 서비스 전체** (`docker-compose.yml` lines 1-27, 그대로 재사용, `ports` 절만 제거하고 `deploy.resources.limits.memory` 추가):
```yaml
services:
  postgres:
    image: postgres:18.4-alpine
    container_name: gold-wrestling-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      TZ: Asia/Seoul
      PGTZ: Asia/Seoul
    volumes:
      # Postgres 18+ 이미지는 데이터 디렉터리를 메이저 버전별 하위 폴더로 관리한다.
      # /var/lib/postgresql/data 에 마운트하면 기동 자체가 실패하므로 /var/lib/postgresql 에 마운트한다.
      - postgres-data:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 10
```
**차이점**(D-11, Claude's Discretion): `ports: ["${DB_PORT}:5432"]` 절은 운영 compose에서 **제거**(호스트 포트 미노출, 내부 네트워크만) — 이건 로컬 compose와 다른 유일한 지점이다. `command: postgres -c shared_buffers=... ` 파라미터는 RESEARCH.md Pattern 5(07-RESEARCH.md lines 336-405)에서 그대로 가져온다.

**app·caddy 서비스는 신규** — 로컬 compose에 대응 서비스가 없다. RESEARCH.md Pattern 5 전체(07-RESEARCH.md lines 336-405)를 골격으로 쓰되, `image:` 는 `${APP_IMAGE:-ghcr.io/minsu-zip/gold-wrestling-be:latest}`(Claude's Discretion), `env_file: .env`, `JAVA_TOOL_OPTIONS`는 compose `environment:`로 주입(D-07).

**주의**: `deploy.resources.limits.memory`만 쓰고 구식 `mem_limit`과 혼용하지 않는다(RESEARCH.md Pitfall 4).

---

### 로컬 검증용 compose 오버라이드 (D-05, 이름 재량)

**Analog:** 방금 작성한 `deploy/compose.prod.yml` 자체 + 기존 `docker-compose.yml`의 오버라이드 관례(compose가 프로젝트 루트 `.env`를 자동으로 읽는 방식, `docker-compose.yml` line 7 주석).

**패턴**: `docker compose -f deploy/compose.prod.yml -f deploy/compose.local-override.yml up -d` 형태로 `DOMAIN=localhost`, Caddy `local_certs`(전역 옵션) 오버라이드. RESEARCH.md §Architecture Patterns Pattern 4의 `{$DOMAIN:localhost}` 플레이스홀더(07-RESEARCH.md line 308)가 이미 오버라이드 없이도 로컬 기본값을 제공하므로, 오버라이드 파일은 최소한(포트 노출 여부 정도)으로 작게 유지 가능.

---

### `deploy/Caddyfile` (config, request-response)

**Analog:** 없음. RESEARCH.md §Architecture Patterns Pattern 4(07-RESEARCH.md lines 302-332)가 유일한 근거 — 공식 Caddy 문서로 검증된 원문.

**골격**:
```caddyfile
{
	email {$ACME_EMAIL}
}

{$DOMAIN:localhost} {
	@blocked-actuator {
		path /actuator/*
		not path /actuator/health
	}
	respond @blocked-actuator 404

	reverse_proxy app:8080
}
```

**연동 지점**: `application.yml`의 `management.endpoints.web.exposure.include: health,info`(현재 lines 56-63)가 앱 내부 1차 노출 범위를 이미 `health,info`로 좁혀 두었다 — Caddyfile은 그 위에 **`/actuator/health`만 통과, `/actuator/info` 포함 나머지 차단**이라는 2차 방어선을 얹는다(D-20). `SecurityConfig.kt`는 건드리지 않는다(Pitfall 5).

---

### `deploy/server-setup.sh` (utility, batch — 멱등)

**Analog:** `.claude/hooks/guard-ddl-auto.sh` (프로젝트에서 유일하게 존재하는 bash 스크립트 — 스타일 참고용. 역할은 가드/훅이라 프로비저닝과 다르지만 이 레포의 유일한 bash 관례 출처)

**Shebang·안전 옵션 패턴** (`.claude/hooks/guard-ddl-auto.sh` line 1-4):
```bash
#!/usr/bin/env bash
# <한 줄 설명>
# <근거 — CLAUDE.md/decisions.md/conventions.md 참조>
set -uo pipefail
```
→ `server-setup.sh`는 `set -euo pipefail`(RESEARCH.md 골격, 07-RESEARCH.md line 481)을 쓴다 — 훅과 달리 실패 시 즉시 중단이 맞다(훅은 실패해도 원본 payload를 그대로 통과시켜야 하므로 `set -e`를 뺐다는 차이가 있음, 참고만 할 것).

**멱등 가드 조건문 스타일** (같은 파일에서 `grep -oiE`, `[ -z "$bad" ] && exit 0` 같은 얕은 조건 분기 관례를 확인) — `server-setup.sh`의 멱등 가드도 같은 얕은 early-return 스타일을 쓴다:
```bash
if ! command -v docker &>/dev/null; then ...; fi
if ! swapon --show | grep -q '/swapfile'; then ...; fi
grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
[ -f /opt/gold-wrestling/.env ] || touch /opt/gold-wrestling/.env
```
전체 골격은 RESEARCH.md §Code Examples "Ubuntu 24.04 서버 초기 세팅 스크립트 골격"(07-RESEARCH.md lines 478-517)을 그대로 쓴다 — 공식 Docker apt 저장소 절차 원문 인용.

---

### `src/main/resources/application.yml` (config — 기존 파일 확장)

**Analog:** 자기 자신의 기존 `${ENV:default}` 섹션 패턴.

**Hikari 플레이스홀더 추가 위치** (현재 lines 17-22, 확장 지점):
```yaml
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:gold_wrestling}
    username: ${DB_USERNAME:gold}
    password: ${DB_PASSWORD:}
    hikari:
      pool-name: gold-wrestling-pool
      maximum-pool-size: ${DB_HIKARI_MAX_POOL_SIZE:5}
```
→ D-08의 `maximum-pool-size: 5` 기본값을 이 스타일 그대로 추가.

**Tomcat 스레드 플레이스홀더 추가 위치** (현재 `server:` 블록, lines 48-54):
```yaml
server:
  port: ${SERVER_PORT:8080}
  tomcat:
    threads:
      max: ${SERVER_TOMCAT_THREADS_MAX:50}
  error:
    include-message: never
    ...
```

**springdoc 플레이스홀더 추가 위치** (현재 lines 65-71, `env_file`이 점 포함 키를 거부하므로 간접 바인딩 — Pitfall 6):
```yaml
springdoc:
  api-docs:
    enabled: ${SWAGGER_ENABLED:true}
    path: /v3/api-docs
  swagger-ui:
    enabled: ${SWAGGER_ENABLED:true}
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
```

**주석 관례** — 각 값 위에 "왜 이 기본값인가"를 한국어로 남기는 스타일을 그대로 따른다. 예시(현재 lines 77-89, `goldwrestling.batch.inactivity-scheduler-enabled` 항목의 주석 블록):
```yaml
    # 2주 미사용 차감 cron(매일 04:00 Asia/Seoul)의 킬 스위치 (decisions.md D-116, D-121).
    # 이 배치는 사람 개입 없이 회원 잔여를 깎는 유일한 경로라 즉시 멈출 수단이 필요하다.
    # false 로 두면 InactivityBatchScheduler 빈 자체가 등록되지 않는다 —
    inactivity-scheduler-enabled: ${BATCH_INACTIVITY_SCHEDULER_ENABLED:false}
```
→ 새 키(SWAGGER_ENABLED 등) 위에도 "로컬 기본 true, 운영에서 false로 덮어쓴다(D-10)" 같은 1~3줄 주석을 남긴다.

---

### `.env.example` (config — 기존 파일 확장)

**Analog:** 자기 자신의 섹션 구분·주석 스타일.

**섹션 헤더 관례** (`.env.example` lines 9, 17, 22, 29, 34, 39, 45, 63):
```
# --- DB 접속 정보 (PostgreSQL) ---
DB_HOST=localhost
...

# --- 서버 ---
SERVER_PORT=8080
TZ=Asia/Seoul
```
→ 새 섹션을 같은 스타일로 추가:
```
# --- 운영 배포 (Phase 7, decisions.md D-XXX) ---
# Caddy가 자동 HTTPS를 발급할 도메인. 로컬은 비워 두면 Caddy가 localhost로 동작(D-19)
DOMAIN=
# Let's Encrypt 인증서 만료 알림을 받을 이메일
ACME_EMAIL=
# 운영에서 Swagger UI/api-docs 노출 여부. 로컬 기본 true, 운영 .env에서 false로 덮어쓴다 (D-10)
SWAGGER_ENABLED=true
# Hikari 커넥션 풀 최대 크기 — 1GB 서버 기준 5로 축소 (D-08)
DB_HIKARI_MAX_POOL_SIZE=5
# Tomcat 최대 스레드 수 — 1GB 서버 기준 50으로 축소 (D-08)
SERVER_TOMCAT_THREADS_MAX=50
# JVM 컨테이너 메모리 플래그. 재빌드 없이 조정 가능하도록 compose가 그대로 주입 (D-07)
JAVA_TOOL_OPTIONS=
# 운영 compose가 pull할 이미지 태그 (Phase 8 SHA 태그 배포 대비, 비우면 :latest)
APP_IMAGE=
```
→ **값에 실제 예시(진짜 도메인·이메일 등)를 넣지 않는다** — 기존 파일도 실값 없이 키 이름과 설명 주석만 있다(CLAUDE.md 시크릿 규칙, `.env.example` line 1-2 명시).

---

### `docs/operations.md` / `docs/metrics.md` (documentation, 신설)

**Analog:** `docs/conventions.md` (헤딩 레벨·표 스타일)

**헤딩·인용 관례** (`docs/conventions.md` lines 1-6):
```markdown
# 코드 규약 (conventions.md)

> 이 프로젝트에서 코드를 쓰거나 고칠 때의 구현 규약. **결정의 "왜"는 `decisions.md`에 있다.**
> 도메인 규칙은 `policies.md`, 이름은 `glossary.md`가 최종 기준이다.
> 여기 적힌 것과 기존 코드가 다르면 **기존 코드가 틀린 것**으로 보고 고친다.

## 1. 패키지 레이아웃 (D-018)
```
→ `docs/operations.md` 첫머리에도 문서 성격을 밝히는 인용구(`> 이 문서는 운영 환경변수·메모리 예산·수동 배포 절차의 정본이다. 값 자체의 "왜"는 decisions.md D-XXX를 본다.`)를 두고, `## N. 제목 (D-XXX)` 넘버링 스타일을 그대로 쓴다.

**표 스타일** — 코드베이스 전반에서 일관: `| 항목 | 값 | 비고 |` 3열 표(README.md "기술 스택" 표, 07-RESEARCH.md의 "메모리 예산표" 요구사항과 동일 구조). `docs/operations.md`의 환경변수 키 표·메모리 예산표(D-06)·`docs/metrics.md`의 이미지 크기 비교표 모두 이 3~4열 표 관례를 따른다.

**코드 블록 인용 관례** — RESEARCH.md처럼 `Source: <URL> (VERIFIED/CITED/ASSUMED, 신뢰도)` 주석을 코드 예제 위에 남기는 습관이 이 레포 문서 전반에 있다(예: RESEARCH.md 전체) — `docs/operations.md`의 수동 배포 절차 커맨드에도 근거 출처(공식 문서 vs 실측)를 구분해 표기하면 일관성이 유지된다.

---

### `docs/decisions.md` (append)

**Analog:** D-152 항목 자체 (엔트리 포맷 — 날짜 / 결정 / 이유 / 기각 대안 3단 구조)

**포맷** (`docs/decisions.md` lines 1645-1657, 그대로 재사용할 템플릿):
```markdown
## D-159. <결정을 한 문장으로>

- 2026-09-08 / <무엇을 했는지 3~4줄. 신설 파일·핵심 수치·근거 문서 참조>
- 이유: <왜 이 선택인지, 대안 대비 우위>
- 기각 대안: <고려했지만 버린 대안과 그 이유 — 없으면 생략 가능>
```
→ CONTEXT.md 안내대로 **로컬 D-01~D-20은 그대로 쓰지 않고**, 이 phase 실행 시점의 `docs/decisions.md` 마지막 번호(현재 확인된 값: **D-158**)에 이어 **D-159부터** 재부여한다. 최소한 다음 항목은 반드시 기록 대상(CONTEXT.md D-08·D-10·D-12·D-20에 대응):
- Hikari pool=5 / Tomcat threads=50 축소 근거(D-08)
- Swagger 운영 비활성을 springdoc enabled 플래그로(SecurityConfig 불변) 처리한 이유(D-10)
- 런타임 베이스를 `eclipse-temurin:21-jre-noble`로 고정한 이유, `-noble` 접미사 필수 사유(D-12)
- Caddy에서 `/actuator/*` 외부 차단(`/actuator/health`만 예외)(D-20)

---

### `src/test/kotlin/com/goldwrestling/config/SwaggerDisabledTest.kt` (test, request-response)

**Analog:** `src/test/kotlin/com/goldwrestling/config/SecurityFilterChainTest.kt` (같은 패키지, 이미 `/v3/api-docs`를 GET하는 테스트가 존재 — 가장 가까운 analog)

**Imports 패턴** (`SecurityFilterChainTest.kt` lines 1-38, 필요한 부분만 발췌):
```kotlin
package com.goldwrestling.config

import com.goldwrestling.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
```
**주의**: RESEARCH.md의 예시 코드(07-RESEARCH.md lines 532-548)는 `mockMvc.get("/v3/api-docs").andExpect { status { isNotFound() } }` 같은 **Kotlin MockMvc DSL**을 쓰는데, 이 레포에는 그 DSL 사용 사례가 **전혀 없다**(grep 결과 0건). 실제 코드베이스는 항상 `MockMvcRequestBuilders`/`MockMvcResultMatchers` 정적 임포트 + `mockMvc.perform(...).andExpect(...)` 체이닝을 쓴다 — **아래 실제 analog 패턴을 따른다.**

**클래스 애노테이션 조합** (`SecurityFilterChainTest.kt` lines 51-54, 트랜잭션 애노테이션은 이 테스트엔 불필요 — DB 픽스처를 쓰지 않으므로 제외):
```kotlin
@SpringBootTest(properties = ["springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class SwaggerDisabledTest {
    @Autowired
    private lateinit var mockMvc: MockMvc
```
`@SpringBootTest(properties = [...])`로 클래스 단위 프로퍼티를 덮어쓰는 패턴은 `JwtConfigTest.kt` line 27, `InactivityBatchPolicyLimitTest.kt` line 48에서도 동일하게 검증됨.

**핵심 테스트 바디 — 실제 GET+status 체인 패턴** (`SecurityFilterChainTest.kt` lines 94-97, 그대로 뒤집을 대상):
```kotlin
@Test
fun `v3-api-docs 는 인증 없이 200 이다`() {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk)
}
```
→ 새 테스트는 이 구조를 그대로 쓰되 기대값만 뒤집는다:
```kotlin
@Test
fun `springdoc 비활성 시 v3 api-docs는 404를 반환한다`() {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound)
}

@Test
fun `springdoc 비활성 시 swagger-ui html은 404를 반환한다`() {
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound)
}
```

**클래스 문서 주석 관례** (`SecurityFilterChainTest.kt` lines 40-49) — 무엇을 검증하는지, 어떤 요구사항/결정 번호에 대응하는지, 다른 테스트와 애노테이션 조합을 왜 다르게/같게 맞췄는지 KDoc으로 남기는 관례. `SwaggerDisabledTest`도 D-10을 참조하는 1문단 KDoc을 클래스 위에 남긴다.

**컨텍스트 캐시 참고** — `@SpringBootTest(properties=...)`로 프로퍼티를 다르게 주는 클래스는 Spring이 별도 ApplicationContext를 새로 띄운다(`InactivityBatchPolicyLimitTest.kt` line 33-39 주석이 같은 트레이드오프를 명시적으로 설명). `SwaggerDisabledTest`도 자신만의 컨텍스트를 띄우는 비용을 감수하는 것이 맞다 — 전역 springdoc 설정을 바꾸면 다른 모든 컨트롤러 테스트의 컨텍스트가 오염된다.

## Shared Patterns

### env 플레이스홀더 + `.env.example` 동기화 (D-011 계열)
**Source:** `application.yml` 전체, `.env.example` 전체
**Apply to:** `application.yml`(Hikari/Tomcat/springdoc 신규 키), `.env.example`(같은 키 추가), `deploy/compose.prod.yml`(`env_file: .env`로 같은 값을 app/caddy 컨테이너에 전달)
```yaml
# application.yml — 항상 ${ENV_KEY:안전한기본값} 형태, 기본값은 로컬 개발이 그대로 동작하는 값
maximum-pool-size: ${DB_HIKARI_MAX_POOL_SIZE:5}
```
```
# .env.example — 값 없이 키 이름 + 주석만, 섹션 헤더로 그룹핑
# Hikari 커넥션 풀 최대 크기 — 1GB 서버 기준 5로 축소 (D-08)
DB_HIKARI_MAX_POOL_SIZE=5
```
env_file이 점(.) 포함 키를 거부하므로 `springdoc.*`처럼 점 표기 Spring 키는 절대 env 이름으로 직접 쓰지 않고 평평한 이름(`SWAGGER_ENABLED`)으로 간접 바인딩한다(Pitfall 6, D-10).

### postgres 볼륨 마운트 경로 (Postgres 18+ 함정)
**Source:** `docker-compose.yml` line 16-18 주석
**Apply to:** `deploy/compose.prod.yml`의 postgres 서비스
```yaml
# Postgres 18+ 이미지는 데이터 디렉터리를 메이저 버전별 하위 폴더로 관리한다.
# /var/lib/postgresql/data 에 마운트하면 기동 자체가 실패하므로 /var/lib/postgresql 에 마운트한다.
- postgres-data:/var/lib/postgresql
```

### 결정 기록 3단 포맷
**Source:** `docs/decisions.md` D-152
**Apply to:** `docs/decisions.md`에 append할 모든 Phase 7 신규 항목
```markdown
## D-XXX. <한 문장 결정>

- <날짜> / <무엇을, 3~4줄>
- 이유: <왜>
- 기각 대안: <버린 대안>
```

### SecurityConfig 불변 경계 (Pitfall 5)
**Source:** `src/main/kotlin/com/goldwrestling/config/SecurityConfig.kt` lines 57-69 (permitAll 목록)
**Apply to:** `application.yml`(springdoc 플레이스홀더), `SwaggerDisabledTest.kt` — 이 phase에서 SecurityConfig.kt에 diff가 생기면 범위 이탈. permitAll은 그대로 두고 springdoc 자체를 비활성화해 404를 유도한다.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `Dockerfile` | config | batch | 레포에 컨테이너화 이력이 전무 — RESEARCH.md §Architecture Patterns Pattern 1·2(공식 Boot 4.1/adoptium 소스로 검증됨)가 유일하고 충분한 근거 |
| `deploy/Caddyfile` | config | request-response | 리버스 프록시 설정 파일이 레포에 존재한 적 없음 — RESEARCH.md §Architecture Patterns Pattern 4(공식 Caddy 문서 검증)가 유일한 근거 |
| `deploy/server-setup.sh` (전체 골격) | utility | batch | 프로비저닝 스크립트 전례 없음 — `.claude/hooks/*.sh`는 스타일(shebang·조건 가드) 참고만 가능, 로직은 RESEARCH.md §Code Examples 원문(공식 Docker apt 절차 검증)을 그대로 사용 |

## Metadata

**Analog search scope:** 레포 루트(`Dockerfile`·`.dockerignore`·`.gitignore`·`docker-compose.yml`·`.env.example`·`build.gradle.kts`), `src/main/resources/application.yml`, `src/main/kotlin/com/goldwrestling/config/`, `src/test/kotlin/com/goldwrestling/{config,batch,attendance}/`, `docs/`, `.github/workflows/ci.yml`, `.claude/hooks/`
**Files scanned:** 약 20개 (grep 기반 후보 탐색 포함)
**Pattern extraction date:** 2026-09-08
