---
phase: 07
slug: container-server-setup
status: planned
nyquist_compliant: true
wave_0_complete: false  # 산출물은 07-01~07-04가 생성
created: 2026-09-08
---

# Phase 07 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> 원본: `07-RESEARCH.md` §Validation Architecture. 플랜 작성 시 Task ID 열을 채운다.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (`@SpringBootTest`, `@AutoConfigureMockMvc` — Boot 4 패키지 `org.springframework.boot.webmvc.test.autoconfigure`) — 신규 프레임워크 도입 없음 |
| **Config file** | `build.gradle.kts` `tasks.withType<Test>` 블록 (신규 설정 불필요) |
| **Quick run command** | `./gradlew test --tests "*Swagger*"` |
| **Full suite command** | `./gradlew ktlintCheck build` (CI와 동일, D-152) |
| **Estimated runtime** | quick ~40s (컨텍스트 기동 포함) / full ~3~5분 (Testcontainers) |

인프라 산출물(Dockerfile·compose·Caddyfile·셸 스크립트·문서)은 conventions §10.0 면제 대상이라 JUnit 테스트를 억지로 만들지 않는다. 대신 아래 정적 검증 명령을 태스크 verify로 쓴다.

| 정적 검증 | 명령 |
|-----------|------|
| compose 문법 | `docker compose -f deploy/compose.prod.yml config -q` |
| Caddyfile 문법 | `docker run --rm -v "$PWD/deploy/Caddyfile:/etc/caddy/Caddyfile" -e DOMAIN=localhost caddy:2 caddy validate --config /etc/caddy/Caddyfile` |
| 셸 스크립트 문법 | `bash -n deploy/server-setup.sh` (+ `shellcheck` 있으면) |
| Dockerfile lint | `hadolint Dockerfile` (설치돼 있을 때만, 선택) |

---

## Sampling Rate

- **After every task commit:** 해당 산출물의 정적 검증 1개 (`docker compose config` / `bash -n` / `caddy validate` / `./gradlew test --tests "*Swagger*"`)
- **After every plan wave:** `./gradlew ktlintCheck build` + 로컬 `docker build` 1회 + 로컬 오버라이드 compose 기동 확인
- **Before `/gsd:verify-work`:** Full suite green + D-01 실서버 human-verify 3건(HTTPS 영속, OOM 없음, 스크립트 2회 실행) 기록 완료
- **Max feedback latency:** 60초 (정적 검증) / 300초 (full build)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-02 T1 | 07-02 | 1 | INFRA-01 | T-07-03 | 컨테이너가 비루트(uid≠0)로 실행 | smoke | `docker build -t gw-be:test . && docker run --rm --entrypoint id gw-be:test -u` → `0`이 아님 | ✅ 계획됨 | ⬜ pending |
| 07-02 T1 | 07-02 | 1 | INFRA-01 | T-07-05 | `.env`가 이미지에 포함되지 않음 | source | `grep -v '^#' .dockerignore \| grep -qx '.env'` | ✅ 계획됨 | ⬜ pending |
| 07-05 T2 | 07-05 | 3 | INFRA-02 | — | amd64+arm64 매니페스트 1개 | smoke | `docker buildx imagetools inspect ghcr.io/minsu-zip/gold-wrestling-be:latest` 에 두 플랫폼 출력 | ✅ 계획됨 | ⬜ pending |
| 07-02 T2 | 07-02 | 1 | INFRA-02 | — | 빌더 스테이지 1회만 컴파일 | smoke | `docker buildx build --platform linux/amd64,linux/arm64 --output=type=cacheonly --progress=plain . 2>&1 \| grep -c 'gradlew bootJar'` → 1 | ✅ 계획됨 | ⬜ pending |
| 07-03 T1 | 07-03 | 2 | INFRA-03 | — | 운영 compose 문법 유효 | static | `docker compose -f deploy/compose.prod.yml --env-file deploy/.env config -q` (사전 `cp -n .env.example deploy/.env`; service `env_file:`은 project dir=`deploy/` 기준)` | ✅ 계획됨 | ⬜ pending |
| 07-03 T3 | 07-03 | 2 | INFRA-03 | — | app이 postgres healthy 이후 기동, 3컨테이너 Up | integration(local override) | `docker compose -f deploy/compose.prod.yml -f deploy/compose.local.yml --env-file deploy/.env up -d --wait` | ✅ 계획됨 | ⬜ pending |
| 07-03 T2 | 07-03 | 2 | INFRA-04 | T-07-02 | Caddyfile 문법 유효, `/actuator/health`만 통과 | static | 컨테이너 경유 `caddy validate --config /etc/caddy/Caddyfile` | ✅ 계획됨 | ⬜ pending |
| 07-03 T3 | 07-03 | 2 | INFRA-04 | T-07-02 | 엣지에서 `/actuator/info` 404 · 앱 내부 200 | integration(local override) | `curl -sk -o /dev/null -w '%{http_code}' https://localhost/actuator/info` → 404 | ✅ 계획됨 | ⬜ pending |
| 07-03 T1 | 07-03 | 2 | INFRA-05 | — | JVM 플래그·메모리 limit이 compose에 반영 | source | `grep -q 'MaxRAMPercentage' deploy/compose.prod.yml && grep -q 'memory: 550M' deploy/compose.prod.yml` | ✅ 계획됨 | ⬜ pending |
| 07-03 T3 | 07-03 | 2 | INFRA-05 | — | 메모리 limit이 런타임에 실적용 | integration | `docker inspect --format '{{.HostConfig.Memory}}'` → 576716800 / 314572800 / 52428800 | ✅ 계획됨 | ⬜ pending |
| 07-04 T2 | 07-04 | 3 | INFRA-05 | — | 메모리 예산표 문서 존재 | source | `grep -q '메모리 예산' docs/operations.md` | ✅ 계획됨 | ⬜ pending |
| 07-06 T3 | 07-06 | 4 | INFRA-05 | T-07-10 | 1GB 실서버에서 OOM kill 0건 | human-verify | `dmesg \| grep -ci 'oom'` → 0, `.State.OOMKilled` 전부 false | — (실서버) | ⬜ pending |
| 07-04 T1 | 07-04 | 3 | INFRA-06 | — | 스크립트 구문 오류 없음 | static | `bash -n deploy/server-setup.sh` | ✅ 계획됨 | ⬜ pending |
| 07-06 T1 | 07-06 | 4 | INFRA-06 | T-07-13 | 2회 실행 멱등 | human-verify | `ssh ... 'bash -s' < deploy/server-setup.sh` 2회 → 상태 요약 diff 없음 | — (실서버) | ⬜ pending |
| 07-04 T2 | 07-04 | 3 | INFRA-07 | T-07-05 | 운영 키 문서·`.env.example` 동기화, 실값 없음 | source | `.env.example`의 모든 `^[A-Z_]+=` 키가 `docs/operations.md`에 존재하는 루프 → exit 0 | ✅ 계획됨 | ⬜ pending |
| 07-01 T2 | 07-01 | 1 | D-10 (Swagger 토글) | T-07-02 | `SWAGGER_ENABLED=false`에서 `/v3/api-docs`·`/swagger-ui.html` 404 | unit | `./gradlew test --tests "*SwaggerDisabledTest*"` | ✅ 계획됨 | ⬜ pending |
| 07-01 T1 | 07-01 | 1 | D-08 | — | Hikari 5 / Tomcat 50 기본값·env 플레이스홀더 | source | `grep -q 'maximum-pool-size: ${DB_HIKARI_MAX_POOL_SIZE:5}' src/main/resources/application.yml` | ✅ 계획됨 | ⬜ pending |
| 07-06 T2 | 07-06 | 4 | INFRA-04 | T-07-09 | 실도메인 HTTPS·80→443·인증서 영속 | human-verify | `curl -sI http://<domain>` 30x, `curl -s https://<domain>/actuator/health` UP, `restart caddy` 후 발급 로그 0건 | — (실서버) | ⬜ pending |
| 07-05 T3 | 07-05 | 3 | D-03 | T-07-04 | GHCR public 무인증 pull | smoke | `docker logout ghcr.io && docker manifest inspect ghcr.io/minsu-zip/gold-wrestling-be:latest` | ✅ 계획됨 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `Dockerfile`, `.dockerignore` — 신설 (INFRA-01, INFRA-02)
- [ ] `deploy/compose.prod.yml` — 신설 (INFRA-03), 로컬 사전 검증용 오버라이드 파일(D-05, 파일명 재량)
- [ ] `deploy/Caddyfile` — 신설 (INFRA-04)
- [ ] `deploy/server-setup.sh` — 신설 (INFRA-06)
- [ ] `src/test/kotlin/com/goldwrestling/config/SwaggerDisabledTest.kt`(경로 재량) — 신설 (D-10)
- [ ] `docs/operations.md`, `docs/metrics.md` — 신설 (INFRA-07, INFRA-05, D-14)
- [ ] `.env.example` 확장 — `DOMAIN`, `ACME_EMAIL`, `SWAGGER_ENABLED`, 풀/스레드 키, `JAVA_TOOL_OPTIONS`
- [ ] `application.yml` 확장 — Hikari pool size, Tomcat threads max, springdoc enabled 플레이스홀더

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `https://api.goldwrestling.com` HTTPS 서비스, 80→443 리다이렉트, 인증서 영속 | INFRA-04 | 실도메인·Let's Encrypt는 실서버에서만 발급됨 (D-01) | 배포 후 `curl -I http://api.goldwrestling.com` → 308/301, `curl -I https://api.goldwrestling.com/actuator/health` → 200. `docker compose restart caddy` 후 Caddy 로그에 새 인증서 발급(obtaining) 로그가 없어야 함 |
| 1GB 서버에서 OOM kill 없이 기동·부하 | INFRA-05 | 메모리 실측은 실서버에서만 의미 있음 (D-01, D-07) | `docker stats --no-stream`으로 RSS 기록 → `docs/operations.md` 예산표 "실측" 열에 기입. `dmesg \| grep -i oom` 결과 없음. `MaxRAMPercentage` 최종 비율 확정 |
| 서버 세팅 스크립트 멱등성 | INFRA-06 | 실제 Ubuntu 24.04에 Docker 설치·스왑·타임존이 필요 | `ssh ubuntu@host 'bash -s' < deploy/server-setup.sh` 2회 실행. 2회째 exit 0, `swapon --show`·`timedatectl`·`docker --version` 결과 동일 |
| `/actuator/info` 외부 차단 | D-20 | Caddy 매처는 실트래픽으로 확인 | `curl -s -o /dev/null -w "%{http_code}" https://api.goldwrestling.com/actuator/info` → 403 또는 404; `/actuator/health` → 200 |
| GHCR public 이미지 무인증 pull | D-03 | 패키지 가시성은 GitHub UI 설정 | 서버에서 `docker logout ghcr.io` 상태로 `docker compose pull` 성공 |
| fat jar 대비 레이어 이미지 절감 수치 | D-14 | 두 이미지를 빌드해 비교하는 측정 작업 | 이미지 크기 + 코드만 바꾼 재빌드 후 pull 전송 바이트를 `docs/metrics.md`에 기록 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (07-01~07-04가 전 신설 파일을 생성)
- [x] No watch-mode flags
- [x] Feedback latency < 300s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** planned 2026-09-08 — 모든 행이 플랜 태스크에 매핑됨. 실서버 3건(07-06)은 human-verify로 분류.
