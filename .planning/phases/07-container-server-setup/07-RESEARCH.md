# Phase 7: 컨테이너화·서버 구성 - Research

**Researched:** 2026-09-08
**Domain:** Spring Boot 4.1 컨테이너 이미지·멀티스테이지 Dockerfile·멀티 아키텍처 빌드, Docker Compose 운영 구성, Caddy 리버스 프록시·자동 HTTPS, RAM 1GB EC2 메모리 튜닝(JVM/Postgres), 멱등 서버 프로비저닝
**Confidence:** MEDIUM-HIGH (핵심 Boot 4.1/Caddy/Docker 사실은 공식 소스로 검증, Postgres/JVM 수치 튜닝은 일반 가이드라인 기반이라 실측 필요)

## Summary

이 phase는 v1.0 코드베이스를 실제 운영 가능한 컨테이너 산출물로 바꾸는 작업이다. 핵심 리스크는 세 곳에 몰려 있다.

첫째, **Spring Boot 4.1의 레이어 추출 명령**은 `docs/decisions.md` D-13이 이미 "Boot 4에서 `layertools`가 제거됐다"고 전제하는데, 공식 리포지토리(`spring-projects/spring-boot` v4.1.0 태그)의 실제 파셜 Dockerfile을 직접 받아 확인한 결과 명령 자체는 살아 있고 이름이 `tools` jarmode로 바뀐 것이 맞다. `java -Djarmode=tools -jar application.jar extract --layers --destination extracted`가 정확한 형태이고, 레이어 4개(`dependencies` / `spring-boot-loader` / `snapshot-dependencies` / `application`)를 이 순서로 COPY하면 `ENTRYPOINT ["java", "-jar", "application.jar"]` 그대로 실행된다 — `JarLauncher`를 직접 지정할 필요가 없다. `bootJar`는 Boot 4.1 Gradle 플러그인에서 **레이어드 jar가 기본**이라 `layered { }` 블록을 추가할 필요가 없다.

둘째, **`eclipse-temurin` 베이스 이미지 태그 함정**을 하나 발견했다 — 공식 `docker-library/official-images` 정의를 직접 조회한 결과, 태그 없는 `21-jre`가 가리키는 Ubuntu 계열이 **더 이상 noble이 아니라 "resolute"로 롤링**돼 있다. D-12가 "noble 계열"을 못박았으므로 **반드시 `eclipse-temurin:21-jre-noble`(또는 `21.0.12_8-jre-noble`처럼 패치 버전까지 고정)로 명시**해야 한다. 다행히 adoptium/containers의 실제 noble Dockerfile 소스를 확인한 결과 이 이미지는 `curl`과 `wget`을 **둘 다 이미 포함**하고 있어(역사적 이유로 유지, adoptium/containers#255) 헬스체크에 추가 설치가 필요 없다. 다만 **비루트 사용자는 베이스 이미지가 만들어주지 않으므로** 런타임 Dockerfile이 직접 `groupadd`/`useradd` + `USER`를 선언해야 한다(INFRA-01 요구사항의 실현부).

셋째, **멀티 아키텍처 빌드에서 "빌더는 1회만 컴파일"이 실제로 보장되는 메커니즘**은 공식 문서에 명시적으로 서술돼 있지 않다(Go 크로스컴파일 예시만 있음). JVM은 바이트코드가 아키텍처 독립적이므로 `FROM --platform=$BUILDPLATFORM ... AS builder`로 고정하면 BuildKit의 콘텐츠 주소 캐시가 amd64/arm64 두 그래프에서 동일한 캐시 키를 만들어 두 번째 플랫폼은 캐시 히트로 처리된다는 것이 커뮤니티 정설이지만, 이번 세션에서 공식 확인은 하지 못했다 — **플랜에 `docker buildx build --progress=plain`으로 두 번째 플랫폼이 CACHED로 찍히는지 실측하는 검증 스텝을 반드시 넣어야 한다.**

Postgres 18 300M 튜닝과 JVM 비힙 추정치는 공식 수치가 아니라 일반 가이드라인(shared_buffers 15~25%, work_mem 보수적, 비힙 150~250M)이므로 D-07/D-11이 요구하는 대로 **수동 배포 시 `docker stats` 실측으로 확정**해야 한다. Caddy의 `not path` 매처, `{$DOMAIN:default}` 환경변수, `deploy.resources.limits.memory`가 non-swarm `docker compose up`에서도 적용되는 것은 모두 공식 소스/복수 소스로 검증됐다.

**Primary recommendation:** Boot 4.1 공식 파셜 Dockerfile을 그대로 골격으로 쓰되 베이스 이미지만 `eclipse-temurin:21-jre-noble`로 교체하고 비루트 사용자·헬스체크·JVM 플래그를 더한다. Postgres/JVM 메모리 수치는 "출발값"으로 문서화하고 반드시 실서버 `docker stats` 재조정 절차(D-01)를 플랜에 태스크로 명시한다.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| HTTPS 종단·80→443 리다이렉트·인증서 관리 | CDN/Edge (Caddy 컨테이너) | — | Let's Encrypt ACME는 TLS 종단점이 직접 처리해야 하고, 앱 컨테이너에 인증서 로직을 넣으면 인증서 갱신마다 앱 재시작이 필요해진다 |
| `/actuator/*` 외부 노출 경계 | CDN/Edge (Caddy) | API/Backend (management.endpoints.web.exposure) | Caddy가 1차 방어선(D-20), 앱 내부 설정이 2차 방어선(Phase 9 OPS-05) — 두 겹 방어 |
| 애플리케이션 실행·JVM 메모리 관리 | API/Backend (app 컨테이너) | — | 요청 처리·비즈니스 로직·Hikari/Tomcat 풀은 전부 앱 컨테이너 책임 |
| 영속 데이터 저장 | Database/Storage (postgres 컨테이너) | — | named volume이 컨테이너 재생성과 무관하게 데이터를 보존 |
| 이미지 빌드·배포 아티팩트 | CI/빌드 도구 (로컬 Mac / 이후 Phase 8 CI) | Registry(GHCR) | Phase 7은 로컬 buildx로 1회 빌드, Phase 8이 이 경로를 CI로 자동화 |
| 서버 초기 상태(Docker·스왑·타임존) | OS/Infra (EC2 Ubuntu) | — | 컨테이너 오케스트레이션 이전에 호스트가 준비돼야 함 — 어떤 애플리케이션 계층도 이를 대체할 수 없다 |
| 환경변수·시크릿 주입 | OS/Infra(.env, 서버 환경변수) | API/Backend(application.yml 플레이스홀더) | 값의 출처는 인프라, 값의 소비는 앱 — 두 계층이 키 이름으로만 연결된다 |

## User Constraints (from CONTEXT.md)

<user_constraints>

### Locked Decisions

> `.planning/phases/07-container-server-setup/07-CONTEXT.md`의 로컬 D-01~D-20 (docs/decisions.md 번호와 다름 — 실행 시 마지막 번호 D-158 이후로 재부여)

- **D-01**: 실제 EC2에 수동 1회 배포까지 phase 안에서 수행한다. 성공 기준 3(HTTPS·인증서 영속)·4(1GB에서 OOM 없음)는 실서버 검증만 인정한다.
- **D-02**: 이미지 전달 경로 = 로컬 Mac(arm64) `docker buildx build --platform linux/amd64,linux/arm64 --push` → GHCR → 서버 `docker compose pull && up -d`. Docker Desktop이 켜져 있어야 buildx가 동작한다.
- **D-03**: GHCR 이미지는 **public**. 레포는 private 유지. 서버 pull 인증 없음. 바이트코드 디컴파일 노출은 소유자가 감수.
- **D-04**: 수동 배포 절차(.env 작성, ADMIN_SEED_* 1회, Flyway V1~V12 확인, docker stats 실측, health·HTTPS 확인)를 문서에 남겨 Phase 8 runbook 씨앗으로 쓴다.
- **D-05**: 로컬 사전 검증(운영 compose 로컬 오버라이드, `tls internal` 등) 형태는 Claude 재량이나 실서버 검증을 대체하지 않는다.
- **D-06**: 컨테이너별 메모리 limit **app 550M / postgres 300M / caddy 50M**, 스왑 2GB 전제. 메모리 예산표를 문서에 남긴다.
- **D-07**: JVM 힙은 `-XX:MaxRAMPercentage=60`으로 시작(힙 ≈330M). 수동 배포 때 `docker stats`로 RSS 실측해 최종 비율 확정. JVM 옵션은 `JAVA_TOOL_OPTIONS`로 주입(재빌드 없이 조정 가능).
- **D-08**: Hikari `maximum-pool-size` **5**, Tomcat `max-threads` **50**. application.yml 기본값을 이 값으로, env 플레이스홀더로 덮어쓸 수 있게.
- **D-09**: prod 프로필 신설 안 함. 기존 `.env`/OS 환경변수 동일 키 방식 유지.
- **D-10**: Swagger UI·api-docs는 운영에서 비활성. `springdoc.api-docs.enabled`·`springdoc.swagger-ui.enabled`를 `${SWAGGER_ENABLED:true}` 명시 플레이스홀더로 바인딩, 운영 `.env`에서 false. `springdoc.*`을 env 이름으로 직접 쓰지 않는 이유: env_file이 점(.) 포함 키를 거부하기 때문. `SecurityConfig`의 Swagger permitAll은 그대로 둔다(D-029, generateApiDocs 전제) — 운영에선 springdoc이 404를 낸다.
- **D-11**: postgres 메모리 파라미터(`shared_buffers` 등)는 300M 안에서 재량, 예산표에 기록.
- **D-12**: 런타임 베이스는 **`eclipse-temurin:21-jre`(Ubuntu noble 계열)**. Alpine·distroless 기각.
- **D-13**: Boot 레이어 추출 이미지. `java -Djarmode=tools -jar app.jar extract --layers --destination …` 계열, `verify-boot4-api` 스킬로 정확한 명령 확인. dependencies/spring-boot-loader/snapshot-dependencies/application 순서로 COPY.
- **D-14**: fat jar 단일 레이어 대비 절감 수치를 `docs/metrics.md`에 기록(최초 생성, Phase 10이 이어 씀).
- **D-15**: CDS/AppCDS는 스킵(v1.2 후보).
- **D-16**: amd64+arm64 멀티 아키텍처. 빌더는 `--platform=$BUILDPLATFORM` + `eclipse-temurin:21-jdk` + Gradle wrapper(9.6.1)로 네이티브 1회 컴파일(`-x test`). BuildKit 캐시 마운트·`.dockerignore`는 재량.
- **D-17**: `Dockerfile`(+`.dockerignore`)은 레포 루트. `deploy/`에 운영 compose·Caddyfile·서버 세팅 스크립트. 로컬 `docker-compose.yml`은 무변경.
- **D-18**: 서버에는 `/opt/gold-wrestling/`에 compose·Caddyfile·`.env`·`backups/`만. **서버에서 git 안 씀.** 세팅 스크립트가 디렉토리·소유권·docker 그룹 가입 처리.
- **D-19**: Caddyfile 도메인은 `{$DOMAIN}` env 플레이스홀더. ACME 이메일도 env(재량).
- **D-20**: `/actuator/*` 외부 차단은 **Phase 7 Caddyfile**에서. `/actuator/health`만 통과, 나머지(`/actuator/info` 포함) 차단. `docs/decisions.md`에 기록.

### Claude's Discretion

- 이미지 이름 `ghcr.io/minsu-zip/gold-wrestling-be`, compose `image:`는 `${APP_IMAGE:-ghcr.io/minsu-zip/gold-wrestling-be:latest}` 형태.
- postgres 컨테이너: `postgres:18.4-alpine`, 호스트 포트 미노출, `/var/lib/postgresql` 마운트, `TZ`/`PGTZ` Asia/Seoul.
- 운영 문서 위치: `docs/operations.md` 신설 권장. README는 로컬 실행 중심 유지, 링크만.
- `.env.example`에 운영 키 추가(`DOMAIN`, `SWAGGER_ENABLED`, 풀/스레드 키, `JAVA_TOOL_OPTIONS`, ACME 이메일 등) — 실값 없이.
- 서버 세팅 스크립트의 Docker 설치 방식(공식 apt 저장소), 실행 방식(`ssh … 'bash -s' < deploy/server-setup.sh`), 멱등성 확보 패턴.
- 테스트: conventions §10.0 면제(yml·gradle·문서·config)가 대부분. `SWAGGER_ENABLED=false`에서 `/v3/api-docs`·`/swagger-ui.html` 404 확인 `@SpringBootTest(properties=…)` 1건 권장. 스크립트 멱등성은 서버 2회 실행으로 검증·기록.
- 청크 분할(D-084): 예 7a = Dockerfile·compose·Caddy·app 설정, 7b = 서버 세팅·수동 배포·metrics/operations 문서. 최종 경계는 플랜 wave를 본 뒤 결정. 수동 EC2 배포는 human-verify 체크포인트.

### Deferred Ideas (OUT OF SCOPE)

- CDS/AppCDS(Boot 4 `tools extract` 훈련 실행)로 기동 시간 단축 — v1.2 후보.
- Phase 8 Q1(pull 인증) — 해소됨(GHCR public).
- 로그 로테이션(OPS-06) — Phase 9. compose를 Phase 7에서 쓰더라도 logging 옵션은 Phase 9에서 추가.
- 이미지 취약점 스캔·SBOM — Out of Scope 유지.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-01 | `docker build` 한 번으로 실행 가능한 앱 이미지(멀티스테이지, JDK 21 최소 이미지, 비루트) | Boot 4.1 공식 파셜 Dockerfile 검증 완료(§Code Examples). `eclipse-temurin:21-jre-noble` 베이스 확정, 비루트 사용자는 직접 선언 필요(§Common Pitfalls) |
| INFRA-02 | amd64+arm64 멀티 아키텍처 이미지, 빌더는 네이티브 1회 컴파일 | `--platform=$BUILDPLATFORM` 패턴 검증(§Architecture Patterns), 1회 컴파일 캐시 히트는 ASSUMED — 실측 검증 스텝 필요(§Common Pitfalls) |
| INFRA-03 | 운영 compose 하나로 app+postgres+Caddy, healthcheck 통과 후 기동, `restart: unless-stopped` | `deploy.resources.limits.memory`가 non-swarm에서도 적용됨을 검증(§Architecture Patterns), `depends_on: condition: service_healthy` 패턴(§Code Examples) |
| INFRA-04 | Caddy 자동 HTTPS, 80→443 리다이렉트, 인증서 볼륨 영속 | Caddy 공식 문서로 `{$DOMAIN}`·`auto_https`·`/data /config` 볼륨 검증(§Code Examples) |
| INFRA-05 | RAM 1GB에서 OOM 없이 동작, JVM 힙 상한·postgres 파라미터·메모리 예산표 | MaxRAMPercentage/cgroup v2 메커니즘 검증, Postgres 300M 파라미터 초안(§Common Pitfalls, §Validation Architecture — 실측 필수) |
| INFRA-06 | 멱등 초기 세팅 스크립트(Docker+compose, 스왑 2GB, 타임존, 배포 디렉토리) | 공식 Docker apt 저장소 설치 절차 검증(§Code Examples), 멱등성 가드 패턴(§Common Pitfalls) |
| INFRA-07 | 운영 환경변수 키 목록·의미·예시 문서화(실값 없음) | 기존 `.env.example`·`application.yml` 패턴 확장(§Architecture Patterns) |

</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Boot 4 API·설정 키·의존성 버전은 **추측 금지** — ① context7로 해당 버전 문서 확인 → ② maven-metadata.xml로 실제 버전 조회 → ③ `./gradlew compileKotlin`으로 검증. 이 순서를 건너뛰지 않는다.
- API 응답은 RFC 9457 `ProblemDetail` 고정, 커스텀 래퍼 금지 — 이 phase는 API 응답 형태를 바꾸지 않으므로 해당 없음(springdoc 비활성화는 응답 형태가 아니라 노출 여부).
- DB 스키마 변경은 Flyway만, ddl-auto 금지 — 이 phase는 스키마 변경이 없다.
- 시간대 Asia/Seoul 명시, 주 시작 월요일 — 서버 세팅 스크립트의 `timedatectl set-timezone Asia/Seoul`, compose의 `TZ`/`PGTZ` 환경변수로 반영.
- 프로덕션 코드 추가·수정 시 같은 작업에서 테스트 동반 — Dockerfile·compose·Caddyfile·bash 스크립트·yml·문서는 conventions §10.0 면제 목록. 유일하게 프로덕션 코드로 분류될 수 있는 변경은 `application.yml`의 springdoc 플레이스홀더(설정값이라 면제 대상)와 Hikari/Tomcat 설정(마찬가지로 config 값). 코드 자체를 건드리지 않는 phase이므로 테스트 필요성은 낮으나, **springdoc 비활성 404 확인 테스트 1건**은 "설정이 의도대로 동작하는가"를 검증하는 유일한 관찰 가능 지점이라 권장 유지.
- 커밋·푸시는 명시적 요청 시에만 — 단, `/gsd-execute-phase`로 phase를 실행할 때는 D-084 예외(청크 경계에서 자동 커밋·푸시·PR)가 적용된다. `feature/phase-07*` 브랜치에만 해당, PR 머지는 항상 사용자.
- 브랜치는 반드시 `origin/dev`에서 분기(`origin/HEAD` 확인 필요, `deliver-phase-chunk` 스킬 0단계).
- 시크릿 실값 절대 커밋 금지 — `.env.example`에 키 이름만, `docs/operations.md`도 실값 없이 키 목록·예시만.

## Standard Stack

### Core

| 구성요소 | 버전 | 용도 | 표준으로 쓰는 이유 |
|---------|------|------|-------------------|
| `eclipse-temurin:21-jdk-noble` | 21.0.12_8 계열 (noble 태그로 고정) | Dockerfile 빌더 스테이지 | CI(`ci.yml`)가 이미 temurin 21을 쓴다(D-152) — 빌드·런타임 JDK 벤더 일치 |
| `eclipse-temurin:21-jre-noble` | 21.0.12_8 계열 (noble 태그로 고정) | Dockerfile 런타임 스테이지 | D-12 확정. **주의: 태그 없는 `21-jre`는 현재 "resolute"로 롤링돼 있어 noble이 아니다 — 반드시 `-noble` 접미사 명시** [VERIFIED: github.com/docker-library/official-images] |
| `postgres:18.4-alpine` | 18.4 | DB 컨테이너(로컬과 동일 이미지) | 기존 `docker-compose.yml`과 동일 — 로컬·운영 이미지 일치로 드리프트 방지 [VERIFIED: 로컬 docker-compose.yml] |
| `caddy:2` 또는 `caddy:2-alpine` | 2.x | 리버스 프록시·자동 HTTPS | 자동 HTTPS·ACME·on-demand TLS가 설정 몇 줄로 끝난다 — nginx+certbot 조합 대비 운영 부담이 훨씬 적다 [CITED: caddyserver.com/docs] |
| Docker BuildKit / buildx | Docker Desktop 내장 (버전 무관, 로컬 Docker 28.5.1 / buildx v0.29.1 확인됨) | 멀티 아키텍처 빌드 | `docker-container` 드라이버로 멀티플랫폼 빌드+레지스트리 푸시가 표준 경로 [VERIFIED: docs.docker.com/build] |

### Supporting

| 라이브러리 | 버전 | 용도 | 언제 쓰는가 |
|---------|------|------|-------------|
| `springdoc-openapi-starter-webmvc-ui` | 3.0.3 (이미 build.gradle.kts에 있음, 신규 추가 아님) | Swagger UI·api-docs | 운영에서 비활성화(D-10) — 이 phase는 새 의존성을 추가하지 않는다 |
| BuildKit RUN cache mount | Dockerfile syntax=docker/dockerfile:1 | Gradle 의존성 캐시 재사용 | 빌더 스테이지 `./gradlew bootJar`에서 매 빌드마다 의존성을 새로 받지 않게 |

### Alternatives Considered

| 대신 | 대안 | 트레이드오프 |
|------|------|-------------|
| `eclipse-temurin:21-jre-noble` (Debian 계열) | Alpine JRE, distroless | D-12가 이미 기각 — musl 호환성 문제(네이티브 라이브러리)·디버깅 도구 부재가 1인 운영 환경에서 손해가 더 큼 |
| `caddy:2` (Debian 계열) | `caddy:2-alpine` | Alpine이 이미지 크기는 작지만 커뮤니티에 "장시간 구동 시 메모리가 예상보다 늘어난다"는 보고가 있어(§Common Pitfalls), 50M 제한 안에서는 굳이 최소화보다 안정성 검증이 우선 — 초기엔 `caddy:2`로 시작하고 `docker stats`로 실측 후 판단 |
| 커스텀 nginx+certbot | Caddy | nginx는 인증서 갱신을 certbot cron으로 별도 운영해야 하고, 갱신 실패를 감지하는 로직을 직접 짜야 한다 — Caddy는 이걸 내장 |

**Installation:** 이 phase는 Gradle 의존성을 추가하지 않는다. 신규로 "설치"하는 것은 서버의 OS 패키지(Docker Engine, compose plugin)뿐이다:

```bash
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

**버전 검증 방법:** Gradle 의존성 추가가 없으므로 maven-metadata.xml 조회 대상이 없다. 대신 Docker 이미지 태그는 다음으로 확인한다:
```bash
docker manifest inspect eclipse-temurin:21-jre-noble   # 아키텍처 목록 확인
docker manifest inspect caddy:2                         # 태그 존재·아키텍처 확인
```

## Package Legitimacy Audit

이 phase는 **npm/pip/cargo 패키지를 설치하지 않는다.** 유일한 신규 외부 아티팩트는 Docker Hub 공식 이미지(`eclipse-temurin`, `postgres`, `caddy`)와 Ubuntu 공식 apt 저장소(`download.docker.com`)이므로 slopcheck의 대상 범위(언어 패키지 레지스트리) 밖이다. 대신 아래 기준으로 이미지 출처를 직접 확인했다.

| 이미지/저장소 | 배급처 | 공식 여부 | 근거 | Disposition |
|---|---|---|---|---|
| `eclipse-temurin` | Eclipse Adoptium (Docker 공식 이미지 라이브러리) | 공식 | `docker-library/official-images` 레포에 정의됨 [VERIFIED] | Approved |
| `postgres` | PostgreSQL 공식 (Docker 공식 이미지 라이브러리) | 공식 | 기존 로컬 compose에서 이미 사용 중 [VERIFIED: 로컬 파일] | Approved |
| `caddy` | Caddy 프로젝트 (Docker 공식 이미지 라이브러리) | 공식 | hub.docker.com/_/caddy 공식 문서 확인 [VERIFIED: WebFetch] | Approved |
| `download.docker.com` apt 저장소 | Docker Inc. 공식 | 공식 | docs.docker.com/engine/install/ubuntu 공식 절차 [VERIFIED: WebFetch] | Approved |

**제거된 패키지:** 없음. **SUS 플래그:** 없음.

## Architecture Patterns

### System Architecture Diagram

```
[운영자 로컬 Mac (arm64, Docker Desktop)]
   |  docker buildx build --platform linux/amd64,linux/arm64 --push
   v
[GHCR: ghcr.io/minsu-zip/gold-wrestling-be:latest]  (public 패키지, private 레포)
   |  (서버에서) docker compose pull
   v
+-------------------- EC2 t3.micro (RAM 1GB + swap 2GB) --------------------+
|                                                                            |
|  [인터넷] --443/80--> [Caddy 컨테이너 (50M)]                              |
|                          | reverse_proxy app:8080                        |
|                          | /actuator/health 만 통과, 나머지 actuator 차단 |
|                          v                                                |
|                      [app 컨테이너 (550M, JVM heap≈330M)]                |
|                          | JDBC (내부 네트워크만, 호스트 포트 미노출)     |
|                          v                                                |
|                      [postgres 컨테이너 (300M, named volume)]            |
|                                                                            |
|  세 컨테이너 모두 restart: unless-stopped                                |
+----------------------------------------------------------------------------+

[Caddy 볼륨: /data /config → Let's Encrypt 인증서 영속]
[postgres 볼륨: /var/lib/postgresql → 데이터 영속]
```

### Recommended Project Structure

```
gold-wrestling-be/
├── Dockerfile              # 멀티스테이지, 레포 루트 (D-17)
├── .dockerignore           # build/, .gradle/, .git/, .env 등 제외
├── deploy/
│   ├── compose.prod.yml    # 운영 3-컨테이너 compose (D-17)
│   ├── Caddyfile            # {$DOMAIN} 플레이스홀더, /actuator 차단 (D-19, D-20)
│   └── server-setup.sh      # 멱등 서버 초기 세팅 (D-18)
├── docs/
│   ├── operations.md         # 운영 키 표·메모리 예산표·수동 배포 절차 (신설)
│   └── metrics.md            # 이미지 크기·pull 바이트 절감 수치 (신설, D-14)
├── .env.example             # 운영 키 추가(DOMAIN, SWAGGER_ENABLED 등)
├── src/main/resources/application.yml  # 풀/스레드/springdoc 플레이스홀더 추가
└── docker-compose.yml        # 로컬 개발용, 무변경 (D-17)
```

### Pattern 1: Boot 4.1 레이어 추출 멀티스테이지 Dockerfile

**What:** `jarmode=tools`로 fat jar를 레이어 4개로 쪼개고, 런타임 스테이지가 레이어별로 COPY해 Docker 레이어 캐시를 최대화한다. 코드만 바뀌는 재배포는 `application` 레이어(가장 작음)만 새로 pull하면 된다.

**When to use:** 항상 — Boot 공식 권장 패턴이고 fat jar 단일 COPY 대비 이득만 있고 손해가 없다.

**Example (공식 소스 원문, 베이스 이미지만 이 프로젝트 것으로 교체):**
```dockerfile
# Source: https://github.com/spring-projects/spring-boot/blob/v4.1.0/documentation/.../reference/partials/dockerfile (VERIFIED, 원문 그대로 인용 후 베이스 이미지만 교체)
FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /builder
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-noble
WORKDIR /application
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
ENTRYPOINT ["java", "-jar", "application.jar"]
```

**extract 명령의 실제 옵션 (공식 help 출력, VERIFIED):**
```
Usage: java -Djarmode=tools -jar my-app.jar extract [options]

--launcher                 스프링 부트 런처를 추출할지 여부 (기본 포함 — 위 예제처럼 java -jar로 바로 실행 가능)
--layers <string list>     추출할 레이어 목록
--destination <string>     추출 대상 디렉토리. 기본값은 uber jar 이름(확장자 제외)
--libraries <string>       라이브러리 디렉토리 이름. --launcher를 안 쓸 때만 적용. 기본 lib/
--application-filename     애플리케이션 jar 파일명. --launcher를 안 쓸 때만 적용
--force                    비어있지 않은 디렉토리를 무시하고 강제 추출
```

`bootJar`가 layers.idx를 기본 포함하므로(Boot 4.1 Gradle 플러그인 공식 문서, VERIFIED) `build.gradle.kts`에 `tasks.named<BootJar>("bootJar") { layered { } }` 같은 추가 설정이 **필요 없다.**

### Pattern 2: 멀티 아키텍처 빌드 — 빌더 1회 컴파일 + 런타임 per-arch

**What:** 빌더 스테이지를 `--platform=$BUILDPLATFORM`으로 고정해 QEMU 에뮬레이션 위에서 Gradle을 돌리지 않는다. JVM 바이트코드는 아키텍처 독립적이므로 Go처럼 `GOOS`/`GOARCH`로 크로스컴파일할 필요가 없다 — **그냥 빌더 플랫폼에서 한 번 빌드한 jar를 그대로 양쪽 런타임 스테이지가 COPY해 쓰면 된다.** 런타임 베이스 이미지(`eclipse-temurin:21-jre-noble`)는 그 자체가 멀티 아키텍처 매니페스트라 `--platform` 플래그가 자동으로 맞는 아키텍처를 골라준다.

**When to use:** INFRA-02(멀티 아키텍처 이미지) 전체.

**Example:**
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

```bash
# 로컬 buildx 빌더 준비 (최초 1회) — Source: docs.docker.com/build/building/multi-platform (VERIFIED)
docker buildx create --name gw-builder --driver docker-container --bootstrap --use

# 멀티플랫폼 빌드 + 푸시 (--load와 동시 사용 불가 — 멀티플랫폼은 반드시 --push 또는 --output)
docker buildx build --platform linux/amd64,linux/arm64 \
  -t ghcr.io/minsu-zip/gold-wrestling-be:latest \
  -t ghcr.io/minsu-zip/gold-wrestling-be:$(git rev-parse --short HEAD) \
  --push .
```

**주의(ASSUMED — 실측 필요):** "빌더 스테이지가 정확히 1회만 실행되고 두 번째 아키텍처는 캐시 히트한다"는 BuildKit의 콘텐츠 주소 캐시 동작에 대한 커뮤니티 통설이며, 이번 세션에서 Docker 공식 문서로 명시적 확인은 하지 못했다. **플랜에 `docker buildx build --progress=plain ... 2>&1 | grep -c "RUN ./gradlew bootJar"` 같은 방식으로 builder 스테이지 로그 발생 횟수를 세는 검증 스텝을 넣어 실측한다.**

### Pattern 3: JVM 컨테이너 메모리 플래그

**What:** `-XX:MaxRAMPercentage`는 JDK 10+ 기본 활성화된 `-XX:+UseContainerSupport`가 cgroup v2의 `memory.max`를 읽어 힙 상한을 그 비율로 계산한다(정적 `-Xmx`가 아니라 비율).

**Example (compose `environment` 또는 `.env`의 `JAVA_TOOL_OPTIONS`):**
```
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60.0 -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:MaxMetaspaceSize=150m -XX:+ExitOnOutOfMemoryError -Xss512k
```

- `-XX:+UseSerialGC`: 1 vCPU(t3.micro) 환경에서 병렬 GC 스레드가 오히려 컨텍스트 스위칭 오버헤드만 늘린다 — Serial GC가 소규모 힙·저병렬 환경의 표준 권장이다 [CITED: 복수 컨테이너 JVM 튜닝 가이드, MEDIUM]
- `-XX:ActiveProcessorCount=1`: JVM이 감지한 CPU 수에 맞춰 스레드 풀·GC 스레드 수를 결정하므로, t3.micro의 실제 vCPU 수와 명시적으로 맞춘다
- `-XX:+ExitOnOutOfMemoryError`: OOM 발생 시 크래시 덤프 없이 깨끗하게 종료 → `restart: unless-stopped`가 컨테이너를 재기동시킨다. JDK 8u92+ 지원 [CITED: 복수 소스 교차검증(Oracle 계열 문서 접근 불가, IBM 미러 문서로 대체 확인), MEDIUM]
- 비힙(메타스페이스·스레드 스택·코드 캐시·GC 구조체) 추정치 150~250M은 **ASSUMED**(Spring Boot+Hibernate+Tomcat 애플리케이션의 일반적인 범위, 훈련 지식) — `docs/decisions.md` D-07이 이미 같은 추정을 전제하고 있으며, 수동 배포 시 `docker stats`로 실측해 확정해야 한다

### Pattern 4: Caddy — 자동 HTTPS + actuator 외부 차단

**Example:**
```caddyfile
# Source: caddyserver.com/docs/caddyfile/{concepts,matchers,options} (VERIFIED, 조합은 이 프로젝트 재구성)
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

- `{$DOMAIN:localhost}` — env 미설정 시 로컬 기본값(D-05 로컬 사전 검증에 활용 가능)
- 사이트 주소에 호스트/IP가 있으면 **Caddy가 자동으로 HTTPS + 80→443 리다이렉트를 켠다**(별도 `auto_https` 설정 불필요) — 끄려면 `http://` 접두를 명시해야 하므로, 아무것도 안 하는 것이 곧 INFRA-04 요구사항의 기본 동작이다 [VERIFIED: caddyserver.com/docs/caddyfile-tutorial]
- `not path /actuator/health`로 헬스체크 경로만 예외 처리하는 패턴은 공식 매처 문서의 "Negate Path Matching" 예제와 동일 구조 [VERIFIED]
- 로컬 사전 검증(D-05)은 `{$DOMAIN:localhost}` 블록 위에 전역 옵션 `local_certs`를 추가하거나, 별도 `deploy/Caddyfile.local` 오버레이에서 `tls internal`을 쓰는 두 방법이 있다 — 전역 `local_certs`가 코드 변경 없이 스위치하기 쉬우므로 재량 범위 안에서 권장

**compose 볼륨(공식 패턴, VERIFIED via hub.docker.com/_/caddy):**
```yaml
caddy:
  image: caddy:2
  volumes:
    - ./deploy/Caddyfile:/etc/caddy/Caddyfile:ro
    - caddy_data:/data
    - caddy_config:/config
```

### Pattern 5: 운영 compose — healthcheck 게이트·메모리 제한

```yaml
# Source: docs.docker.com/_/postgres (VERIFIED) + docker/compose 이슈 트래커 교차검증(VERIFIED)
services:
  postgres:
    image: postgres:18.4-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      TZ: Asia/Seoul
      PGTZ: Asia/Seoul
    command: >
      postgres
      -c shared_buffers=64MB
      -c work_mem=4MB
      -c maintenance_work_mem=32MB
      -c effective_cache_size=192MB
      -c max_connections=20
    volumes:
      - postgres-data:/var/lib/postgresql
    deploy:
      resources:
        limits:
          memory: 300M
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 10

  app:
    image: ${APP_IMAGE:-ghcr.io/minsu-zip/gold-wrestling-be:latest}
    restart: unless-stopped
    env_file: .env
    environment:
      JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=60.0 -XX:+UseSerialGC -XX:ActiveProcessorCount=1"
    depends_on:
      postgres:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 550M
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 3s
      retries: 5
      start_period: 40s

  caddy:
    image: caddy:2
    restart: unless-stopped
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    env_file: .env
    deploy:
      resources:
        limits:
          memory: 50M

volumes:
  postgres-data:
  caddy_data:
  caddy_config:
```

`deploy.resources.limits.memory`가 스웜 없이 `docker compose up`에도 적용된다는 것은 Compose V2(현재 이 프로젝트가 쓰는 `docker compose` — plugin, 하이픈 없음)의 공식 동작이며, 구식 `mem_limit`(v2 컴포즈 파일 전용 키)과 혼용하지 않는다 [CITED: docker/compose·docker/docs 이슈 트래커 교차검증, MEDIUM-HIGH].

App 컨테이너의 헬스체크는 `curl`을 쓴다 — noble 베이스 런타임 이미지에 `curl`이 이미 포함돼 있음을 adoptium/containers 공식 소스로 확인했다(§Common Pitfalls 참조).

### Anti-Patterns to Avoid

- **fat jar 단일 COPY 레이어**: 코드 한 줄만 바꿔도 의존성 전체를 포함한 레이어가 다시 pull된다 — D-14가 요구하는 "절감 수치" 비교 대상으로 남겨두되(fat jar 이미지도 1회 빌드해 비교), 실제 운영 Dockerfile은 레이어드로 간다.
- **`server.tomcat.threads.max`를 코드로 하드코딩**: env 플레이스홀더 없이 박아두면 운영 중 조정에 재배포가 필요해진다 — D-08처럼 기본값+플레이스홀더로.
- **postgres 볼륨을 `/var/lib/postgresql/data`에 마운트**: Postgres 18+ 이미지는 메이저 버전별 하위 디렉토리(`/var/lib/postgresql/18/docker`)를 쓰므로 이 경로에 마운트하면 기동이 실패한다 — 반드시 `/var/lib/postgresql`(상위 디렉토리)에 마운트 [VERIFIED: 로컬 docker-compose.yml 주석 + hub.docker.com/_/postgres].

## Don't Hand-Roll

| 문제 | 직접 구현하지 말 것 | 대신 쓸 것 | 이유 |
|------|------|-----------|------|
| Docker 레이어 분리 | `unzip`+`mv`로 수동 레이어 구성 | `java -Djarmode=tools -jar app.jar extract --layers` | Boot 공식 문서가 명시적으로 "jarmode가 이걸 간단하게 만든다"고 서술 — 수동 unzip/mv는 레이어 경계를 잘못 잡기 쉽고 유지보수 비용만 늘어난다 |
| HTTPS 인증서 발급·갱신·리다이렉트 | nginx+certbot 조합, cron 갱신 스크립트 | Caddy 자동 HTTPS | Caddy는 도메인이 있는 사이트 주소만 써도 인증서 발급·자동 갱신·리다이렉트가 기본 동작이다 — 별도 상태 관리 로직이 없다 |
| 멀티 아키텍처 매니페스트 조합 | 아키텍처별로 따로 빌드해 수동으로 `docker manifest create` | `docker buildx build --platform a,b --push` | buildx가 매니페스트 리스트 생성까지 한 번에 처리한다 — 수동 조합은 태그 불일치 사고 위험 |
| 컨테이너 메모리 인지 힙 사이징 | 힙을 고정 `-Xmx` MB로 하드코딩 | `-XX:MaxRAMPercentage` (JDK 10+ 컨테이너 지원 기본 활성) | 컨테이너 limit을 바꿀 때마다 `-Xmx`를 재계산해 재빌드할 필요가 없다 — limit만 compose에서 바꾸면 힙도 비례해 조정된다 |

**Key insight:** 이 phase의 모든 "직접 만들지 말아야 할 것"은 이미 성숙한 도구(Boot jarmode, Caddy, buildx, JVM 컨테이너 지원)가 존재하는 영역이다. 1인 운영 체제에서 이런 인프라 로직을 직접 짜면 "만들 때 한 번, 고장 났을 때 또 한 번" 학습·디버깅 비용이 든다 — 표준 도구를 쓰고 그 설정을 문서화하는 데 시간을 쓰는 편이 낫다.

## Common Pitfalls

### Pitfall 1: `eclipse-temurin:21-jre` 태그가 조용히 다른 OS로 롤링됨

**What goes wrong:** `eclipse-temurin:21-jre`(접미사 없음)를 그대로 쓰면 D-12가 의도한 Ubuntu noble이 아니라 현재 "resolute"로 매핑된 이미지를 받는다. 이후 Adoptium이 기본 태그를 또 롤링하면 이미지가 재빌드 시점마다 조용히 바뀔 수 있다.
**Why it happens:** Docker 공식 이미지 라이브러리의 "기본 태그"는 배급사가 시간이 지나며 최신 Ubuntu LTS로 재매핑한다 — 태그 자체가 버전 고정을 보장하지 않는다.
**How to avoid:** Dockerfile에 `eclipse-temurin:21-jre-noble`처럼 **OS 계열명을 항상 명시**한다. 재현성을 더 높이려면 `21.0.12_8-jre-noble`처럼 패치 버전까지 고정하고, 업데이트는 의도적으로(Dependabot류 또는 수동) 한다.
**Warning signs:** `docker manifest inspect eclipse-temurin:21-jre`의 OS 필드가 예상과 다르게 나오거나, 빌드 시점마다 이미지 다이제스트가 바뀌는데 Dockerfile을 안 건드렸을 때.

### Pitfall 2: 비루트 사용자를 베이스 이미지가 만들어준다고 착각

**What goes wrong:** `eclipse-temurin:21-jre-noble` 공식 Dockerfile(adoptium/containers)을 직접 확인한 결과 **컨테이너는 기본적으로 root로 실행**된다 — 비루트 사용자가 미리 준비돼 있지 않다.
**Why it happens:** 일부 언어 런타임 공식 이미지(예: node)는 비루트 사용자를 기본 제공하지만, JDK/JRE 이미지는 관례가 다르다.
**How to avoid:** 런타임 스테이지에서 직접 `groupadd --system app && useradd --system --gid app --no-create-home app` 후 `USER app`을 선언한다(§Code Examples Pattern 2).
**Warning signs:** `docker run --rm <image> whoami`가 `root`를 반환.

### Pitfall 3: Caddy 컨테이너 메모리가 시간이 지나며 50M을 넘길 수 있음

**What goes wrong:** 커뮤니티 보고(Caddy 공식 이슈 트래커 아님, 포럼 스레드 하나)에 "Caddy 컨테이너가 10시간 안에 500MB, 이후 1GB까지 증가했다"는 사례가 있다. D-06의 50M 제한과 정면으로 부딪힐 수 있다.
**Why it happens:** 불명확 — 인증서 캐시, 로그 버퍼, 혹은 특정 워크로드 패턴에서의 메모리 누수로 추정되나 단일 소스라 원인이 확정되지 않았다.
**How to avoid:** `restart: unless-stopped`가 OOM kill 이후 자동 재기동을 보장하므로 최악의 경우도 짧은 다운타임으로 끝난다. 수동 배포(D-01) 때 `docker stats`를 배포 직후뿐 아니라 **몇 시간 뒤에도 한 번 더** 확인해 50M이 충분한지 판단한다. 부족하면 D-06의 50M 자체를 재조정 대상으로 `docs/operations.md`에 기록한다.
**Warning signs:** `docker stats caddy` 메모리 사용량이 시간에 비례해 계속 증가(반면 요청 트래픽은 일정).
**신뢰도:** LOW(단일 커뮤니티 소스) — 플랜에 "배포 후 수 시간 뒤 재확인" 절차로 반영할 가치는 있지만, 확정된 사실로 문서화하지 않는다.

### Pitfall 4: `docker compose up`을 스웜 전용이라 오해해 `mem_limit`(구문법)와 `deploy.resources.limits.memory`(신문법)를 혼용

**What goes wrong:** 한 서비스에 두 키를 동시에 쓰면 어느 쪽이 적용되는지 파일만 보고 알 수 없다.
**Why it happens:** `deploy.resources`는 원래 Docker Swarm 스펙에서 왔고, 문서 곳곳에 "swarm 전용"이라는 오래된 서술이 남아 있다. 실제로는 Compose V2가 `docker compose up`에서도 이 키를 해석해 적용한다.
**How to avoid:** 이 프로젝트는 `deploy.resources.limits.memory`만 쓴다(위 Pattern 5 예제). `mem_limit`은 쓰지 않는다.
**Warning signs:** `docker inspect <container> | grep -i memory`로 실제 적용된 제한을 확인했을 때 설정값과 다르면 두 키가 충돌하고 있다는 신호.

### Pitfall 5: springdoc 비활성화가 `SecurityConfig`의 permitAll과 충돌한다고 오해

**What goes wrong:** "운영에서 Swagger를 막는다"는 요구를 SecurityConfig에서 `/v3/api-docs/**`·`/swagger-ui/**` permitAll을 지우는 방식으로 처리하려는 유혹이 생길 수 있다.
**Why it happens:** "차단"이라는 말이 보안 필터체인 변경을 연상시킨다.
**How to avoid:** D-10이 이미 명시했듯 이 permitAll은 **`generateApiDocs`(D-029) 로컬 파이프라인이 401 없이 스펙을 받아오기 위한 전제**라 손대면 안 된다. 운영 차단은 `springdoc.api-docs.enabled=false`/`springdoc.swagger-ui.enabled=false`로 springdoc 자체가 컨트롤러를 등록하지 않게 하는 방식이라, permitAll 경로에 요청이 와도 매핑된 핸들러가 없어 404가 난다. SecurityConfig는 건드리지 않는다.
**Warning signs:** SecurityConfig.kt에 diff가 생기면 이 phase 범위를 벗어난 것 — 리뷰에서 반드시 걸러야 한다.

### Pitfall 6: env_file의 점(.) 포함 키 거부를 모르고 `springdoc.api-docs.enabled=false`를 `.env`에 직접 씀

**What goes wrong:** `.env`/`env_file`에 점이 포함된 키를 쓰면 `docker compose`가 "unexpected character "." in variable name" 류의 에러로 기동 자체를 막는다.
**Why it happens:** `application.yml`의 Spring 프로퍼티 키(점 표기)와 OS 환경변수 이름(밑줄 표기)이 다른 네임스페이스라는 걸 놓치기 쉽다.
**How to avoid:** D-10대로 `SWAGGER_ENABLED`라는 평평한(flat) 이름을 만들고 `application.yml`에서 `springdoc.api-docs.enabled: ${SWAGGER_ENABLED:true}`처럼 간접 바인딩한다. Hikari/Tomcat 설정도 같은 패턴(`DB_HIKARI_MAX_POOL_SIZE`, `SERVER_TOMCAT_THREADS_MAX` 등)을 쓴다.
**Warning signs:** `docker compose config` 실행 시 "unexpected character" 에러.
**신뢰도:** [CITED: github.com/docker/compose 이슈 #8862·#12123·#12216 교차검증, MEDIUM-HIGH] — D-10의 근거를 독립적으로 재확인함.

## Code Examples

### Ubuntu 24.04 서버 초기 세팅 스크립트 골격 (멱등)

```bash
#!/usr/bin/env bash
# Source: docs.docker.com/engine/install/ubuntu (VERIFIED, 공식 apt 저장소 절차 그대로 인용)
set -euo pipefail

# --- Docker 설치 (멱등: 이미 있으면 스킵) ---
if ! command -v docker &>/dev/null; then
  sudo apt-get update
  sudo apt-get install -y ca-certificates curl
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  sudo chmod a+r /etc/apt/keyrings/docker.asc
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
  sudo apt-get update
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

# --- 스왑 2GB (멱등: /etc/fstab에 이미 있으면 스킵) ---
if ! swapon --show | grep -q '/swapfile'; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
fi
grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# --- 타임존 ---
sudo timedatectl set-timezone Asia/Seoul

# --- ubuntu 사용자를 docker 그룹에 (멱등: usermod -aG는 원래 멱등) ---
sudo usermod -aG docker ubuntu

# --- 배포 디렉토리 (기존 .env는 절대 덮어쓰지 않음) ---
sudo mkdir -p /opt/gold-wrestling/backups
sudo chown -R ubuntu:ubuntu /opt/gold-wrestling
[ -f /opt/gold-wrestling/.env ] || touch /opt/gold-wrestling/.env
```

**멱등성 검증 원칙:** 각 블록이 "이미 되어 있으면 스킵"을 `command -v`/`swapon --show`/`grep -q`/`[ -f ]`로 먼저 확인한다. `usermod -aG`, `mkdir -p`, `chmod`, `timedatectl set-timezone`은 원래 연산 자체가 멱등(같은 인자로 여러 번 실행해도 결과가 같음)이라 별도 가드가 필요 없다.

### 실행 방식

```bash
scp deploy/server-setup.sh ubuntu@<host>:/tmp/
ssh ubuntu@<host> 'bash /tmp/server-setup.sh'
# 또는 파이프
ssh ubuntu@<host> 'bash -s' < deploy/server-setup.sh
```

### springdoc 비활성 확인 테스트 (기존 코드베이스 패턴 재사용)

```kotlin
// Source: 로컬 코드베이스 패턴(JwtConfigTest.kt, InactivityBatchPolicyLimitTest.kt)을 그대로 따름 [VERIFIED: 로컬 grep]
@SpringBootTest(properties = ["springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"])
@AutoConfigureMockMvc
class SwaggerDisabledTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `springdoc 비활성 시 v3 api-docs는 404를 반환한다`() {
        mockMvc.get("/v3/api-docs").andExpect { status { isNotFound() } }
    }

    @Test
    fun `springdoc 비활성 시 swagger-ui html은 404를 반환한다`() {
        mockMvc.get("/swagger-ui.html").andExpect { status { isNotFound() } }
    }
}
```

`import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`(Boot 4.1 패키지 경로, `AdminAttendanceControllerTest.kt`에서 이미 검증된 임포트) [VERIFIED: 로컬 코드베이스].

## State of the Art

| 예전 방식 | 현재 방식 | 바뀐 시점 | 의미 |
|--------|----------|-----------|------|
| `layertools` jarmode (`java org.springframework.boot.loader.tools.LayerToolsJarMode`, Boot 3.x 이하 서술) | `tools` jarmode(`-Djarmode=tools ... extract`) | Boot 3.3~4.x 사이 (정확한 도입 버전은 이번 세션에서 확인 못함, v4.1.0 기준으로는 `tools`만 존재) | 명령 이름·서브커맨드 구조가 바뀌었지만 레이어 개념(`dependencies`/`spring-boot-loader`/`snapshot-dependencies`/`application`)은 동일하게 유지됨 |
| Docker Compose v3 `version:` 키 명시 | Compose Specification(버전 키 없이 최상위 `services:`부터 시작) | Compose V2 전환 이후 | 운영 compose 파일에 `version: "3.8"` 같은 줄을 넣을 필요가 없다 — 있어도 무시되지만 없는 것이 현재 관례 |
| `eclipse-temurin` 기본 태그 = focal/jammy | 기본 태그 = resolute (지속 롤링) | 지속적(이미지 배급사가 주기적으로 최신 LTS로 롤링) | **버전 없는 짧은 태그를 프로덕션에 쓰면 안 되는 이유** — 항상 OS 계열명 명시 |

**Deprecated/outdated:** Docker Compose v1(`docker-compose` 하이픈 버전)은 이미 EOL — 이 프로젝트는 이미 `docker compose`(공백, plugin) 문법을 쓰고 있어 해당 없음.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | BuildKit이 `--platform=$BUILDPLATFORM`으로 고정된 빌더 스테이지를 여러 타겟 플랫폼 그래프에서 캐시 히트로 재사용해 실제로 1회만 컴파일한다 | Architecture Patterns Pattern 2 | 틀리면 INFRA-02의 "빌더는 네이티브 1회만 컴파일"이 거짓이 되어 amd64/arm64 각각 QEMU 없이도 중복 컴파일이 발생 — 빌드 시간이 예상보다 길어짐(치명적이지 않음, 검증 스텝으로 조기 발견 가능) |
| A2 | 비힙(메타스페이스·스레드 스택·코드 캐시 등) 사용량이 Spring Boot 4 + Hibernate + Tomcat 조합에서 150~250M 범위다 | Architecture Patterns Pattern 3 | 틀리면 `MaxRAMPercentage=60`(힙 330M)이 550M 제한을 초과해 OOM kill 발생 — D-07이 이미 실측 절차를 전제하므로 계획 자체는 안전하나, 초기값이 크게 틀리면 첫 배포에서 즉시 재조정이 필요 |
| A3 | Caddy 컨테이너 메모리가 장시간 구동 시 시작값보다 크게 증가할 수 있다(50M을 초과할 가능성) | Common Pitfalls Pitfall 3 | 틀리면(즉 증가하지 않으면) 불필요한 모니터링 절차만 추가한 것 — 리스크 낮음. 맞으면 50M 제한이 너무 타이트해 Caddy가 반복 재시작될 수 있음 |
| A4 | `# syntax=docker/dockerfile:1` 없이도 최신 Docker/buildx에서 `--mount=type=cache`가 동작한다 (명시하지 않아도 안전) | Code Examples/Architecture Patterns | 틀리면 캐시 마운트 구문이 파싱 에러를 내어 빌드 자체가 실패 — 발견이 즉각적이라(빌드 실패) 실제 위험은 낮음. 안전하게 항상 `# syntax=` 지시문을 포함하는 것으로 완화 |
| A5 | `bootJar`가 Boot 4.1 Gradle 플러그인에서 레이어드 잘 구성을 기본 활성화하고, `layered { }` 블록 없이도 `layers.idx`가 jar에 포함된다 | Architecture Patterns Pattern 1 | 틀리면 `java -Djarmode=tools ... extract`가 레이어를 찾지 못해 즉시 실패 — 발견이 빌드 단계에서 즉각적 |

**참고:** 위 5개는 리스크가 낮거나(발견이 빠름) 이미 D-07/D-06처럼 실측 절차가 전제된 항목들이다. 사용자 확인이 반드시 필요한 것은 A2(메모리 예산 자체는 D-01/D-07가 실측을 요구하므로 계획에 이미 반영됨)뿐이며, 나머지는 플랜의 검증 스텝으로 자연히 드러난다.

## Open Questions (RESOLVED)

> 아래 3건은 플랜 단계에서 전부 해소됐다 — 각 항목의 RESOLVED 줄이 어느 플랜/결정이 채택했는지를 가리킨다.

1. **`tools` jarmode가 정확히 어느 Boot 버전부터 `layertools`를 대체했는가**
   - What we know: v4.1.0 공식 문서에는 `tools` jarmode만 존재하고 `layertools`에 대한 언급이 없다.
   - What's unclear: 정확한 도입/제거 버전(3.3? 4.0?) — 이번 세션에서 버전별 변경 이력까지는 조사하지 않았다.
   - Recommendation: 이 프로젝트는 4.1.0을 쓰므로 실질적 영향은 없다. 플랜/실행 단계에서 `verify-boot4-api` 스킬로 재확인하되, 굳이 역사를 추적할 필요는 없다.
   - **RESOLVED:** 07-02 Task 1이 v4.1.0 공식 파셜 Dockerfile의 `-Djarmode=tools … extract --layers --destination` 명령을 그대로 채택. 도입 버전 추적은 하지 않기로 함(영향 없음).

2. **Caddy `caddy:2`(Debian) vs `caddy:2-alpine` 중 50M 제한에 더 안전한 쪽**
   - What we know: Alpine이 이미지 크기는 작지만 메모리 증가 커뮤니티 보고가 어느 태그 기준인지 불명확하다.
   - What's unclear: 두 배리언트의 실측 RSS 차이.
   - Recommendation: 초기엔 `caddy:2`(Debian, 더 널리 쓰임)로 시작하고, 수동 배포(D-01) 때 `docker stats`로 확인 — 문제 있으면 `-alpine`으로 전환을 고려 대상으로 `docs/operations.md`에 남긴다.
   - **RESOLVED:** 07-03 Task 1이 `image: caddy:2` 채택, 07-04가 `docs/operations.md`에 전환 고려 사항 기록, 07-06 Task 3이 실서버 `docker stats`로 50M 제한 대비 실측.

3. **postgres `max_connections=20`이 실제로 필요한 하한인가**
   - What we know: Hikari pool=5 + `REQUIRES_NEW` 중첩으로 흐름당 최대 2개 커넥션 사용(D-08 근거).
   - What's unclear: 배치·수동 실행 API·향후 관리자 동시 접속이 겹칠 때의 피크 커넥션 수.
   - Recommendation: 20을 초안으로 쓰고, 부하테스트(Phase 10 VERIFY-01)에서 커넥션 부족 에러가 나오면 그때 올린다 — 지금 과도하게 낮추면 최악의 경우 배치+API 동시 실행 시 커넥션 고갈로 500 에러가 날 수 있다.
   - **RESOLVED:** 07-03 Task 1이 `max_connections=20`을 compose `command`에 채택하고 주석으로 Phase 10 재조정 대상임을 명시.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker Engine + buildx | 전체 이미지 빌드 | ✓ | Docker 28.5.1 / buildx v0.29.1-desktop.1 | — |
| Docker Desktop (Mac, 켜져 있어야 함) | `docker buildx build --push` (D-02) | ✓ (로컬 확인됨, 실행 중 여부는 매번 확인 필요) | — | 꺼져 있으면 buildx 실행 전 사용자가 직접 켜야 함(자동화 불가 — 사용자 개입 필요) |
| JDK 21 (Zulu, 로컬) | `./gradlew compileKotlin` 검증, `bootJar` 로컬 실행 | ✓ | Zulu21.52+15-CA (21.0.12) | — |
| Gradle 9.6.1 (wrapper) | 빌드 | ✓ | 9.6.1 | — |
| `hadolint` (Dockerfile 린터) | Dockerfile 정적 검증(선택적 자동 검증 단계) | ✗ | — | `brew install hadolint`로 설치하거나, 생략하고 `docker build` 성공 여부로만 검증(수용 가능한 대체) |
| `shellcheck` (bash 린터) | `deploy/server-setup.sh` 정적 검증(선택적) | ✗ | — | `brew install shellcheck` 또는 `bash -n deploy/server-setup.sh`(구문 오류만 검출)로 대체 |
| `caddy` CLI (로컬) | `caddy validate --config` | ✗ | — | `docker run --rm -v $(pwd)/deploy/Caddyfile:/etc/caddy/Caddyfile caddy:2 caddy validate --config /etc/caddy/Caddyfile`로 대체(컨테이너 안의 caddy 바이너리 사용) |
| EC2 t3.micro 서버 (실서버 검증 대상) | INFRA-04·05 성공 기준, D-01 | 사전 준비 완료(REQUIREMENTS.md) — 이번 세션에서 직접 접속 확인은 하지 않음 | Ubuntu 24.04 | — (필수, human-verify 단계에서 실제 SSH 접속으로 재확인) |

**Missing dependencies with no fallback:** 없음 — Docker Desktop 실행 여부만 사용자 확인이 필요(자동화 불가 항목이지 "대체 불가" 항목은 아님).

**Missing dependencies with fallback:** hadolint, shellcheck, caddy CLI — 셋 다 "있으면 더 좋은" 선택적 정적 검증 도구이고, 없어도 `docker build`/`bash -n`/컨테이너화된 caddy 바이너리로 동등한 검증이 가능하다.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test(`@SpringBootTest`, `@AutoConfigureMockMvc`) — 기존 코드베이스 그대로, 신규 프레임워크 도입 없음 |
| Config file | `build.gradle.kts`의 `tasks.withType<Test>` 블록 (신규 설정 불필요) |
| Quick run command | `./gradlew test --tests "*Swagger*"` |
| Full suite command | `./gradlew ktlintCheck build` (CI와 동일, D-152) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INFRA-01 | `docker build`가 성공하고 이미지가 비루트로 기동 | smoke | `docker build -t gw-be:test . && docker run --rm gw-be:test id -u` (0이 아니어야 함) | ❌ Wave 0(스크립트 없음, 수동/CI 명령으로 대체 가능) |
| INFRA-01 | Dockerfile 자체의 정적 결함(예: 캐시 무효화 안티패턴) | lint | `hadolint Dockerfile` (있으면) 또는 생략 | — (선택적) |
| INFRA-02 | 두 아키텍처 매니페스트가 실제로 존재 | smoke | `docker buildx build --platform linux/amd64,linux/arm64 -t gw-be:test --output=type=image .` 후 `docker buildx imagetools inspect gw-be:test` | ❌ Wave 0 |
| INFRA-02 | 빌더가 1회만 컴파일(캐시 히트) | smoke | `docker buildx build --progress=plain ... 2>&1 \| grep "gradlew bootJar"`로 실행 횟수 확인 | ❌ Wave 0 |
| INFRA-03 | 운영 compose 문법이 유효 | 자동 | `docker compose -f deploy/compose.prod.yml config` | ❌ Wave 0 |
| INFRA-03 | app이 postgres healthcheck 통과 후 기동, 세 컨테이너 모두 정상 | 통합(로컬 오버라이드) | `docker compose -f deploy/compose.prod.yml up -d`(로컬, DOMAIN=localhost 등으로 오버라이드) 후 `docker compose ps` | ❌ Wave 0 |
| INFRA-04 | Caddyfile 문법 유효 | 자동 | `docker run --rm -v $(pwd)/deploy/Caddyfile:/etc/caddy/Caddyfile caddy:2 caddy validate --config /etc/caddy/Caddyfile` | ❌ Wave 0 |
| INFRA-04 | 실제 도메인 HTTPS·인증서 영속 | **human-verify** | 실서버 배포 후 `curl -I https://api.goldwrestling.com`, 컨테이너 재시작 후 재확인 | — (D-01 전제) |
| INFRA-05 | 메모리 예산표 문서 존재·JVM 플래그 반영 | 자동 | `grep -q "MaxRAMPercentage" docker-compose 관련 파일` 또는 문서 존재 확인 | ❌ Wave 0(문서 신설) |
| INFRA-05 | 1GB에서 OOM 없이 부하 중 동작 | **human-verify** | 실서버에서 `docker stats` 관찰, 메모리 예산표에 실측값 기록 | — (D-01 전제) |
| INFRA-06 | 스크립트 구문 오류 없음 | 자동 | `bash -n deploy/server-setup.sh`, `shellcheck deploy/server-setup.sh`(있으면) | ❌ Wave 0 |
| INFRA-06 | 두 번 실행해도 같은 결과(멱등) | **human-verify** | 실서버에서 스크립트 2회 실행, 출력·상태 diff 확인 | — (D-01 전제, discretion 항목) |
| INFRA-07 | 운영 환경변수 문서·`.env.example` 키 동기화 | 자동 | `.env.example`과 `docs/operations.md`의 키 목록 diff(간단 스크립트 또는 수동 대조) | ❌ Wave 0 |
| (부가) Swagger 비활성 | springdoc 비활성 시 404 | 단위 | `./gradlew test --tests "*Swagger*"` | ❌ Wave 0(테스트 신설) |

### Sampling Rate

- **Per task commit:** 관련 정적 검증(`docker compose config`, `bash -n`, `./gradlew test --tests "*Swagger*"`) 중 해당하는 것만 빠르게
- **Per wave merge:** `./gradlew ktlintCheck build` 전체, 로컬 buildx 멀티플랫폼 빌드 1회, 로컬 오버라이드 compose 기동 확인
- **Phase gate:** `/gsd:verify-work` 전에 D-01 실서버 human-verify 항목(HTTPS 영속, OOM 없음, 멱등 2회 실행)이 모두 기록돼 있어야 한다

### Wave 0 Gaps

- [ ] `Dockerfile`, `.dockerignore` — 신설 (INFRA-01, INFRA-02)
- [ ] `deploy/compose.prod.yml` — 신설 (INFRA-03)
- [ ] `deploy/Caddyfile` — 신설 (INFRA-04)
- [ ] `deploy/server-setup.sh` — 신설 (INFRA-06)
- [ ] `src/test/kotlin/.../SwaggerDisabledTest.kt` — 신설 (D-10 관찰 지점)
- [ ] `docs/operations.md`, `docs/metrics.md` — 신설 (INFRA-07, D-14)
- [ ] `.env.example` 확장 — `DOMAIN`, `SWAGGER_ENABLED`, `DB_HIKARI_MAX_POOL_SIZE`, `SERVER_TOMCAT_THREADS_MAX`, `JAVA_TOOL_OPTIONS`, `ACME_EMAIL` 등 키 추가
- [ ] `application.yml` 확장 — Hikari pool size, Tomcat threads max, springdoc enabled 플레이스홀더 추가

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | 아니오 | 이 phase는 인증 로직을 변경하지 않음 |
| V3 Session Management | 아니오 | 해당 없음 |
| V4 Access Control | 부분 | Caddy의 `/actuator/*` 경로 차단(D-20) — SecurityConfig의 인가 규칙과 별개 계층 |
| V5 Input Validation | 아니오 | 이 phase는 입력 검증 로직을 추가하지 않음 |
| V6 Cryptography | 부분 | Let's Encrypt 인증서 발급·저장은 Caddy에 위임(직접 암호화 로직 구현 없음) — 비밀키를 named volume(`/data`)에 영속화하는 것 자체가 컨트롤 |
| V14 Configuration | 예 | 컨테이너 비루트 실행, 이미지 최소화(JRE), 시크릿 미포함(D-03 전제), 운영 설정 전부 env 분리 |

### Known Threat Patterns for 이 스택

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| GHCR public 이미지에서 소스·마이그레이션 역컴파일 노출 | Information Disclosure | D-03이 이미 감수하기로 확정 — 이미지에 시크릿을 넣지 않는 것(env 전량 분리)이 유일한 실질 완화책. 이 phase에서 재검토하지 않는다 |
| `/actuator/*` 전체 노출 시 내부 상태·환경·빈 목록 유출 | Information Disclosure | Caddy 1차 차단(D-20, 이 phase) + 앱 내부 `management.endpoints.web.exposure`(Phase 9 OPS-05) 2차 방어 |
| 컨테이너가 root로 실행되어 컨테이너 탈출 시 호스트 권한 확대 | Elevation of Privilege | 비루트 사용자 강제(INFRA-01, §Common Pitfalls Pitfall 2) |
| 서버에 장기 GHCR PAT를 남겨 유출 시 이미지 레지스트리 쓰기 권한 탈취 | Elevation of Privilege | D-03(GHCR public)이 서버 pull 인증 자체를 없애 이 위협을 원천 제거 — Phase 8의 push 인증(`GITHUB_TOKEN`)은 CI 러너에만 존재 |
| `.env` 파일이 실수로 커밋되거나 이미지에 COPY됨 | Information Disclosure | `.dockerignore`에 `.env` 명시 필수(신규 파일이므로 Wave 0에서 반드시 포함), `docs/operations.md`는 키 이름만 |

## Sources

### Primary (HIGH confidence)

- Context7 `/spring-projects/spring-boot/v4.1.0` — jarmode tools extract 명령·옵션, layered jar 기본 활성화, Hikari/Tomcat 프로퍼티 바인딩 메커니즘
- `https://raw.githubusercontent.com/spring-projects/spring-boot/v4.1.0/documentation/.../reference/partials/dockerfile` — 공식 파셜 Dockerfile 원문 직접 조회
- `https://raw.githubusercontent.com/spring-projects/spring-boot/v4.1.0/loader/spring-boot-jarmode-tools/.../tools-help-extract-output.txt` — extract 옵션 전체 목록
- Context7 `/websites/caddyserver_caddyfile` — `{$DOMAIN}` 환경변수, `not path` 매처, `auto_https` 기본 동작, 전역 옵션 목록
- `https://raw.githubusercontent.com/adoptium/containers/main/21/jre/ubuntu/noble/Dockerfile` — noble JRE 이미지가 curl+wget을 포함하고 비루트 사용자를 만들지 않음을 직접 확인
- `https://api.github.com/repos/docker-library/official-images` (`library/eclipse-temurin` 정의) — 태그 없는 `21-jre`가 현재 resolute로 매핑됨을 확인
- Context7 `/springdoc/springdoc-openapi` — `springdoc.api-docs.enabled`/`springdoc.swagger-ui.enabled` 키 확인
- 로컬 코드베이스: `docker-compose.yml`, `application.yml`, `.env.example`, `build.gradle.kts`, `ci.yml`, `SecurityConfig.kt`, `AdminAttendanceControllerTest.kt`, `JwtConfigTest.kt`, `InactivityBatchPolicyLimitTest.kt`

### Secondary (MEDIUM confidence)

- `docs.docker.com/build/building/multi-platform` — `--platform=$BUILDPLATFORM` 크로스컴파일 패턴(Go 예시), `docker buildx create --driver docker-container`
- `docs.docker.com/build/cache/optimize` — `--mount=type=cache` 구문
- `hub.docker.com/_/postgres`, `hub.docker.com/_/caddy` — 공식 이미지 페이지(WebFetch 요약)
- `docs.docker.com/engine/install/ubuntu` — 공식 Docker apt 저장소 설치 절차
- GitHub 이슈 교차검증: `docker/compose#8964`, `#8862`, `#12123`, `#12216`(env_file 점/하이픈 거부), `docker/compose#5803`, `docker/docs#14185`(deploy.resources가 non-swarm에도 적용)
- PostgreSQL Wiki "Tuning Your PostgreSQL Server" 및 복수 튜닝 가이드(shared_buffers 15~25% 등 일반 가이드라인)

### Tertiary (LOW confidence)

- Caddy 커뮤니티 포럼 스레드(단일) — 장시간 구동 시 메모리 증가 보고, 원인 미확정
- JVM 컨테이너 튜닝 관련 블로그·미디엄 글 여러 건 — `UseSerialGC`/`ActiveProcessorCount=1` 권장 조합, 비힙 150~250M 추정치

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 베이스 이미지·jarmode·springdoc 키는 공식 소스로 직접 확인
- Architecture: HIGH(Dockerfile·Caddy) / MEDIUM(compose deploy.resources 적용 범위) — 핵심 패턴은 공식 문서 인용, compose 세부 동작은 이슈 트래커 교차검증
- Pitfalls: MEDIUM — eclipse-temurin 태그 함정·비루트 사용자·env_file 점 거부는 VERIFIED, Caddy 메모리 증가는 명시적으로 LOW로 분리 표기
- 메모리 튜닝 수치(JVM 비힙, Postgres 파라미터): LOW-MEDIUM — 일반 가이드라인 기반, D-01/D-07/D-11이 이미 실측 절차를 전제하고 있어 계획 리스크는 낮음

**Research date:** 2026-09-08
**Valid until:** 30일(Docker 이미지 태그 롤링 특성상 `eclipse-temurin` 기본 태그 재확인 주기는 더 짧게 — 실행 직전 `docker manifest inspect`로 재확인 권장)
