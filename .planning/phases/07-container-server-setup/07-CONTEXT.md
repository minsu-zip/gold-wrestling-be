# Phase 7: 컨테이너화·서버 구성 - Context

**Gathered:** 2026-09-08
**Status:** Ready for planning

<domain>
## Phase Boundary

v1.0 백엔드를 **이미지로 빌드하고, RAM 1GB EC2(t3.micro, Ubuntu 24.04)에 app+postgres+Caddy 세 컨테이너를 안전하게 올릴 수 있는 파일·스크립트·문서 세트**를 만든다 — 멀티스테이지 Dockerfile, 운영 compose, Caddyfile, 멱등 서버 초기 세팅 스크립트, 운영 환경변수 문서, 메모리 예산표.

**이 phase 안에서 실제 EC2에 수동 1회 배포까지 수행한다** (성공 기준 3·4가 실서버에서만 검증 가능). Phase 8이 자동화할 경로(이미지 push → 서버 pull → compose up → health 확인)를 손으로 먼저 밟아 둔다.

범위 밖: 배포 자동화 워크플로(Phase 8), S3 백업·복구·로그 로테이션·Actuator **내부** 노출 확정(Phase 9), k6·cron 활성화(Phase 10), FE 배포, 무중단 배포, 모니터링 대시보드, 스테이징.

요구사항: INFRA-01 ~ INFRA-07 (`.planning/REQUIREMENTS.md`).

</domain>

<decisions>
## Implementation Decisions

> 아래 D-번호는 이 CONTEXT 안의 로컬 번호다. `docs/decisions.md`의 D-번호와 다르다 — 실행 시 decisions.md에 기록할 때는 그 시점의 마지막 번호(2026-09-08 기준 D-158)에 이어 붙인다.

### 검증 범위·실서버 첫 기동
- **D-01:** Phase 7은 **실제 EC2에 수동 1회 배포까지** 한다. 성공 기준 3(실도메인 HTTPS·인증서 영속)과 4(1GB에서 OOM kill 없음)는 실서버에서 검증하고 결과를 기록한다. 로컬만으로 phase를 닫지 않는다.
- **D-02:** 이미지 전달 경로 = 소유자 로컬 Mac(arm64)에서 `docker buildx build --platform linux/amd64,linux/arm64 --push` → GHCR → 서버에서 `docker compose pull && up -d`. 로컬 `docker login ghcr.io`(write:packages PAT)는 소유자가 직접 한다. Docker Desktop이 켜져 있어야 buildx가 동작한다(현재 꺼져 있음).
- **D-03:** GHCR 이미지는 **public**으로 둔다 (레포는 PRIVATE이지만 패키지 가시성은 별도 설정). 서버 pull 인증이 사라진다. 이미지 안에 시크릿은 없다(전부 env 주입). 바이트코드 디컴파일로 도메인 로직·마이그레이션·API 표면이 노출되는 것은 **소유자가 감수하기로 확정**. → Phase 8 Q1(pull 인증 방식)은 해소됐고 DEPLOY-03의 "private pull 인증" 문구는 삭제했다.
- **D-04:** 수동 배포 절차(서버 `.env` 작성, `ADMIN_SEED_*` 최초 1회, Flyway V1~V12 적용 확인, `docker stats` 실측, health·HTTPS 확인)를 문서에 그대로 남겨 Phase 8 DEPLOY-06 runbook의 씨앗으로 쓴다.
- **D-05:** 로컬 사전 검증(운영 compose를 로컬에서 띄워 보는 오버라이드, 예: Caddy `tls internal`)의 형태는 Claude 재량. 단, 실서버 검증을 대체하지 않는다.

### 메모리 예산·운영 설정 주입
- **D-06:** 컨테이너별 메모리 limit **app 550M / postgres 300M / caddy 50M**, 스왑 2GB 전제. OS+dockerd 여유분까지 포함한 **메모리 예산표**를 문서에 남긴다(INFRA-05).
- **D-07:** 앱 JVM 힙은 `-XX:MaxRAMPercentage=60`으로 **시작**한다(힙 ≈ 330M). 소유자 초안 70%는 힙 밖(메타스페이스·스레드·코드캐시·GC 오버헤드 — Boot+Hibernate에서 보통 150~250M) 여유가 165M뿐이라 컨테이너 OOM kill 경계에 걸려 기각. 수동 배포 때 `docker stats`로 RSS를 실측해 예산표에 적고 최종 비율을 확정한다. JVM 옵션은 compose의 `JAVA_TOOL_OPTIONS`로 주입해 재빌드 없이 조정 가능하게 한다.
- **D-08:** **Hikari `maximum-pool-size` 5, Tomcat `max-threads` 50**으로 축소. application.yml 기본값을 이 값으로 두고 env 플레이스홀더로 덮어쓸 수 있게 한다. 근거(1GB·단일 지점 트래픽·배치 REQUIRES_NEW가 한 흐름에 커넥션 2개를 씀 → 5로 충분)를 `docs/decisions.md`에 기록.
- **D-09:** **prod 프로필을 신설하지 않는다.** 기존 방식(`.env`/OS 환경변수 동일 키, D-011 계열) 유지. 운영 전용 값은 전부 env 키로 표현한다.
- **D-10:** **Swagger UI·api-docs는 운영에서 비활성.** `springdoc.api-docs.enabled`·`springdoc.swagger-ui.enabled`를 `${SWAGGER_ENABLED:true}` 같은 **명시 플레이스홀더**로 바인딩하고 운영 `.env`에서 false. `springdoc.*` 키를 env 이름으로 직접 쓰지 않는 이유: `.env`를 compose `env_file`이 같이 읽는데 점(.)이 든 키는 거부된다. `SecurityConfig`의 Swagger permitAll은 `generateApiDocs`(D-029, T-02-18) 때문에 그대로 둔다 — 운영에선 springdoc이 404를 낸다. DEPLOY-07의 "Swagger 경로 정책"은 이 404를 뜻한다.
- **D-11:** postgres 메모리 파라미터(`shared_buffers` 등)는 300M 안에서 Claude 재량으로 정하고 예산표에 적는다.

### 런타임 이미지·jar 실행 형태
- **D-12:** 런타임 베이스는 **`eclipse-temurin:21-jre`(Ubuntu noble 계열)**. INFRA-01의 "JDK 21 기반 최소 이미지"는 JRE로 충족하는 것으로 해석하고 decisions에 기록. Alpine·distroless 기각(musl 호환·디버깅 편의).
- **D-13:** **Boot 레이어 추출 이미지**. Boot 4에서는 `layertools` jarmode가 제거됐으므로 `java -Djarmode=tools -jar app.jar extract --layers --destination …` 계열을 쓴다 — **플랜 단계에서 `verify-boot4-api` 스킬로 정확한 명령·옵션을 확인**한다. dependencies / spring-boot-loader / snapshot-dependencies / application 레이어를 순서대로 COPY해 의존성 레이어 캐시를 살린다.
- **D-14:** fat jar 단일 레이어 대비 **절감 수치**(이미지 크기, 코드만 바뀐 재배포 시 pull 바이트)를 `docs/metrics.md`에 기록한다. `docs/metrics.md`는 아직 없다 — **Phase 7이 최초 생성**하고 Phase 10(VERIFY-02)이 이어서 쓴다.
- **D-15:** CDS/AppCDS는 **스킵** → v1.2 후보(deferred).
- **D-16:** 멀티 아키텍처 amd64+arm64는 요구사항(INFRA-02) 그대로. 빌더 스테이지는 `--platform=$BUILDPLATFORM` + `eclipse-temurin:21-jdk` + Gradle wrapper(9.6.1)로 네이티브 1회 컴파일, `-x test`. BuildKit 캐시 마운트·`.dockerignore` 구성은 재량.

### 레포 배치·서버 디렉토리·Caddy 라우팅
- **D-17:** **`Dockerfile`(+`.dockerignore`)은 레포 루트**. `deploy/` 아래에 운영 compose·`Caddyfile`·서버 초기 세팅 스크립트를 둔다(파일명은 재량, 예: `deploy/compose.prod.yml`, `deploy/Caddyfile`, `deploy/server-setup.sh`). 로컬 개발용 `docker-compose.yml`은 무변경.
- **D-18:** 서버에는 **`/opt/gold-wrestling/`에 compose 파일·Caddyfile·`.env`·`backups/`(Phase 9용 자리)만** 둔다. **서버에서 git을 쓰지 않는다.** 세팅 스크립트가 디렉토리 생성·`ubuntu` 소유권·`ubuntu`의 docker 그룹 가입까지 맡는다. → Phase 8 전제: compose·Caddyfile 변경분은 배포 워크플로가 scp로 전달해야 한다.
- **D-19:** Caddyfile의 도메인은 **`{$DOMAIN}` env 플레이스홀더**. compose가 `.env`의 `DOMAIN`을 Caddy 컨테이너에 넘긴다. ACME 이메일도 env(재량).
- **D-20:** **`/actuator/*` 외부 차단은 Phase 7 Caddyfile에서 한다** — `/actuator/health`만 통과, `/actuator/info`를 포함한 나머지는 Caddy에서 차단. 이 결정을 Phase 7이 `docs/decisions.md`에 기록한다. **Phase 9 OPS-05의 경계는 앱 내부 노출 범위**(`management.endpoints.web.exposure`, `show-details: never`) 확정 + 실서버 확인으로 축소했다(REQUIREMENTS·ROADMAP 정정 완료).

### Claude's Discretion
- 이미지 이름 `ghcr.io/minsu-zip/gold-wrestling-be`, compose `image:`는 `${APP_IMAGE:-ghcr.io/minsu-zip/gold-wrestling-be:latest}` 형태로 태그 교체 가능하게(Phase 8 SHA 태그 배포 대비)
- postgres 컨테이너: 이미지는 로컬·Testcontainers와 같은 `postgres:18.4-alpine`, **호스트 포트 미노출**(compose 내부 네트워크만), `/var/lib/postgresql` 마운트(로컬 compose의 노하우 그대로), `TZ`/`PGTZ` Asia/Seoul
- 운영 문서 위치: `docs/operations.md` 신설 추천(환경변수 키 표·메모리 예산표·수동 배포 절차·서버 세팅). README는 로컬 실행 중심으로 유지하고 링크만
- `.env.example`에 운영 키 추가(`DOMAIN`, `SWAGGER_ENABLED`, 풀/스레드 키, `JAVA_TOOL_OPTIONS`, ACME 이메일 등) — CLAUDE.md 시크릿 규칙대로 실값 없이
- 서버 세팅 스크립트의 Docker 설치 방식(공식 apt 저장소 권장), 실행 방식(`ssh … 'bash -s' < deploy/server-setup.sh` 등), 멱등성 확보 패턴
- 테스트: conventions §10.0 면제(yml·gradle·문서·config)가 대부분이라 억지로 만들지 않는다. 다만 `SWAGGER_ENABLED=false`에서 `/v3/api-docs`·`/swagger-ui.html`이 404인지 확인하는 `@SpringBootTest(properties=…)` 1건은 권장. 스크립트 멱등성은 서버에서 **2회 실행**으로 검증하고 기록
- 청크 분할(D-084): 예컨대 7a = Dockerfile·compose·Caddy·app 설정(env 키·풀 축소·Swagger 토글), 7b = 서버 세팅 스크립트·수동 배포·metrics/operations 문서. 최종 경계는 플랜의 wave를 본 뒤 `deliver-phase-chunk` 절차로 정한다. 수동 EC2 배포는 사람 확인(human-verify) 체크포인트로 둔다

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 요구사항·로드맵·전제값
- `.planning/REQUIREMENTS.md` — INFRA-01~07 본문, 사전 준비 전제값(도메인·EC2·보안그룹·SSH), Q1 해소 표기
- `.planning/ROADMAP.md` §Phase 7 — 성공 기준 5개; §Phase 8/9 — 이 phase가 넘겨주는 전제(scp 전달, Actuator 경계)
- `.planning/PROJECT.md` — "사전 준비 완료" 값, 운영 인프라 제약(1GB·무중단 배포 없음), Out of Scope
- `CLAUDE.md` — 시크릿 규칙(실값 커밋 금지, `.env.example` 키 동기화), 규칙 9(Boot 4 API는 추측하지 않고 확인), 규칙 10(테스트 면제 목록), 학습 모드 보고 형식

### 기술 결정 (docs/decisions.md)
- `docs/decisions.md` D-011 — `.env`/OS 환경변수 동일 키 주입 방식(prod 프로필을 만들지 않는 근거)
- `docs/decisions.md` D-029 — `generateApiDocs`가 로컬 Postgres·Swagger permitAll을 전제함(운영 Swagger 비활성과 충돌하지 않게)
- `docs/decisions.md` D-038 — 관리자 시드 최초 1회 멱등 생성(수동 배포 절차에 반영)
- `docs/decisions.md` D-116 · D-121 · D-151 — cron 킬 스위치 기본 꺼짐(운영 `.env`에 명시적으로 false)
- `docs/decisions.md` D-152 — CI 워크플로(`ktlintCheck build`, JDK 21 temurin, Gradle setup) — Dockerfile 빌더 스테이지와 버전 정합
- `docs/decisions.md` D-084 · `.claude/skills/deliver-phase-chunk/SKILL.md` — 청크 단위 브랜치·PR 절차

### 기존 설정·코드 (수정 대상 또는 정합 대상)
- `src/main/resources/application.yml` — env 플레이스홀더 패턴, Hikari 설정 위치, `management.endpoints.web.exposure`, springdoc 경로
- `.env.example` — 운영 키 추가 위치와 주석 스타일
- `docker-compose.yml` — postgres 18.4-alpine + `/var/lib/postgresql` 마운트 주의점 + healthcheck(운영 compose에 그대로 이식)
- `src/main/kotlin/com/goldwrestling/config/SecurityConfig.kt` — `/actuator/health`·`/actuator/info`·Swagger permitAll(변경 금지, T-02-17/18)
- `build.gradle.kts` — Boot 4.1.0, Kotlin 2.3.21, JDK 21 toolchain, `bootJar`; `gradle/wrapper/gradle-wrapper.properties`(Gradle 9.6.1)
- `.github/workflows/ci.yml` — 러너 셋업(참고용; Phase 8이 재사용)
- `docs/conventions.md` §10.0 — 변경유형별 테스트 표·면제 목록

### 절차 스킬
- `.claude/skills/verify-boot4-api/SKILL.md` — `-Djarmode=tools extract` 옵션, springdoc enabled 키, Hikari/Tomcat 키 이름 확인에 사용

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `docker-compose.yml`의 postgres 서비스 정의(18.4-alpine, `/var/lib/postgresql` 마운트, `pg_isready` healthcheck, TZ/PGTZ) — 운영 compose의 postgres 서비스로 거의 그대로 이식
- `.env.example`의 키 목록·주석 — INFRA-07 운영 환경변수 문서의 출발점 (`KAKAO_REDIRECT_URI`, `CORS_ALLOWED_ORIGINS`, `ADMIN_SEED_*`, JWT, 배치 킬 스위치 이미 존재)
- `application.yml`의 `${ENV:default}` 패턴 — 새 키(Swagger 토글, 풀 크기, 스레드 수)를 같은 방식으로 추가
- `build.gradle.kts`의 `bootJar` — 빌더 스테이지가 그대로 호출(`./gradlew bootJar -x test`)
- `ci.yml`의 temurin 21 + `gradle/actions/setup-gradle` — 빌더 이미지 버전 선택 근거

### Established Patterns
- 프로필 없음, 전부 env 주입(D-011) — 운영 전용 설정도 env 키로 표현
- 시크릿은 `.env`(로컬)·환경변수(서버)만, `.env.example`에 키 이름 동기화
- 기본값은 안전한 쪽(fail-safe): cron 기본 꺼짐(D-121) — Swagger 토글은 반대로 로컬 기본 true·운영 false를 `.env`에 명시
- Actuator는 `health,info`만 노출, `show-details: never`(이미 설정) — Caddy 차단은 이 위에 한 겹 더
- 청크 단위 브랜치(`feature/phase-07a-…`)·PR·머지는 사용자(D-084)

### Integration Points
- `SecurityConfig` permitAll 목록(변경 금지) ↔ Caddy 차단 규칙(외부 경계) ↔ `management.endpoints.web.exposure`(내부 경계, Phase 9)
- `AdminSeeder`(ApplicationRunner) ↔ 수동 배포 절차의 `ADMIN_SEED_*` 1회성
- Flyway V1~V12 ↔ 빈 운영 DB 최초 기동 확인
- `/actuator/health` ↔ compose healthcheck·Phase 8 배포 게이트(DEPLOY-04)

</code_context>

<specifics>
## Specific Ideas

- 메모리 예산표는 "limit / 실측 RSS / 여유"를 한 표에 — 실측 열은 수동 배포에서 채운다
- 절감 수치는 fat jar 이미지와 레이어 이미지를 **둘 다 빌드해** 비교(이미지 크기, 코드만 바꾼 뒤 `docker pull` 전송량)
- 서버는 "git 없음, 이미지는 GHCR, 파일 4종만" — 서버 이전(Phase 9 OPS-04)이 `/opt/gold-wrestling` 복사로 끝나게

</specifics>

<deferred>
## Deferred Ideas

- **CDS/AppCDS(Boot 4 `tools extract` 훈련 실행)로 기동 시간 단축** — v1.2 후보. 1GB 서버에서 배포 다운타임을 줄이는 다음 단계
- **Phase 8 Q1(pull 인증)** — 해소됨(GHCR public). Phase 8은 push 인증(`GITHUB_TOKEN` write:packages)만 다루면 된다
- **로그 로테이션(OPS-06)** — Phase 9 그대로. compose를 Phase 7에서 쓰더라도 logging 옵션은 Phase 9에서 추가
- **이미지 취약점 스캔·SBOM** — 기존 Out of Scope 유지

None beyond the above — discussion stayed within phase scope.

</deferred>

---

*Phase: 07-container-server-setup*
*Context gathered: 2026-09-08*
