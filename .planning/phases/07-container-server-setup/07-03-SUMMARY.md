---
phase: 07-container-server-setup
plan: 03
subsystem: infra
tags: [docker-compose, caddy, auto-https, healthcheck, jvm-memory, reverse-proxy]

# Dependency graph
requires:
  - phase: 07-container-server-setup (plan 01)
    provides: "운영 배포 env 키 계약(SWAGGER_ENABLED 등), D-169~D-175 결정 로그"
  - phase: 07-container-server-setup (plan 02)
    provides: "레포 루트 Dockerfile, 로컬 빌드 이미지 gw-be:test"
provides:
  - "deploy/compose.prod.yml — app+postgres+caddy 3컨테이너 운영 오케스트레이션(healthcheck 게이트·메모리 제한·볼륨 영속)"
  - "deploy/Caddyfile — {$DOMAIN:localhost} 자동 HTTPS + /actuator/* 외부 차단(D-20, /actuator/health만 예외)"
  - "deploy/compose.local.yml — 실서버 없이 로컬에서 운영 compose를 그대로 실증하는 최소 오버라이드(D-05)"
  - "로컬 실기동 실측값(메모리 바이트·actuator 차단 이중 관측·Flyway 최초/재기동 로그)"
  - "Dockerfile ENTRYPOINT 버그(application.jar 미존재) 발견 — 오케스트레이터가 같은 청크에서 Dockerfile을 공식 형태로 고쳐 해소(D-176), compose 우회 제거"
affects: [07-04, 07-05, 07-06, 08-deploy-pipeline]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "docker compose depends_on condition: service_healthy 로 postgres → app 기동 순서 게이트"
    - "deploy.resources.limits.memory (신문법) 만 사용, mem_limit(구문법) 미혼용"
    - "Caddy named matcher(@blocked-actuator) + respond 선언 순서로 특정 경로만 차단하는 2차 방어선"
    - "compose 서비스 env_file: 은 project directory(첫 -f 파일 디렉토리) 기준 — 로컬 검증은 deploy/.env 필요"

key-files:
  created:
    - deploy/compose.prod.yml
    - deploy/Caddyfile
    - deploy/compose.local.yml
  modified: []

key-decisions:
  - "container_name을 gold-wrestling-*(로컬 dev docker-compose.yml과 동일)에서 gw-prod-*로 변경 — 로컬에서 두 compose를 동시에 띄워 검증 가능하게 함"
  - "compose.local.yml에서 local_certs 전역 옵션 주입을 포기 — Caddy가 사이트 주소 'localhost'를 비공인 도메인으로 자동 인식해 내부 CA 자체 서명 인증서를 스스로 발급함을 실측 확인, deploy/Caddyfile 무변경으로 단순화"
  - "app 서비스에 entrypoint/command 오버라이드를 임시 추가했다가 제거 — Dockerfile ENTRYPOINT(java -jar application.jar)가 실제 산출물 파일명과 불일치하는 버그를 compose에서 우회했으나, 오케스트레이터가 Dockerfile 자체를 고쳐(D-176) 우회를 걷어냈다. compose는 이미지 ENTRYPOINT를 그대로 쓴다"

patterns-established:
  - "compose.local.yml 오버라이드는 이미지 치환 + 호스트 포트 노출 + (필요시) env 값 명시로 최소화하고, deploy/Caddyfile·compose.prod.yml 본문은 절대 로컬 전용으로 오염시키지 않는다"

requirements-completed: [INFRA-03, INFRA-04, INFRA-05]

# Metrics
duration: 약 60분(디버깅 포함)
completed: 2026-09-08
---

# Phase 7 Plan 03: 운영 오케스트레이션 3종 세트 — 로컬 실기동 검증 Summary

**app+postgres+caddy 3컨테이너 운영 compose·Caddyfile·로컬 검증 오버라이드를 신설하고, 로컬에서 실제로 3컨테이너를 기동해 healthcheck 게이트·메모리 제한(550M/300M/50M 바이트 실측 일치)·actuator 이중 차단·80→443 리다이렉트·Flyway V1~V12 최초 적용과 재기동 스킵·볼륨 영속을 전부 관측으로 증명했다. 이 과정에서 Dockerfile ENTRYPOINT 버그와 Caddy env 빈 문자열 함정 2건을 발견해 compose 레벨에서 우회했다.**

## Performance

- **Duration:** 약 60분 (기동 버그 2건 디버깅 포함)
- **Completed:** 2026-09-08T14:30Z (23:30 KST)
- **Tasks:** 3/3 완료
- **Files modified:** 3 (전부 신설, 이후 Task 3에서 2개 파일 추가 수정)

## Accomplishments

- `deploy/compose.prod.yml` 신설 — 로컬 `docker-compose.yml`의 postgres 정의를 이식(ports 절만 제거)하고 app·caddy 서비스를 새로 작성. 세 서비스 모두 `restart: unless-stopped`, `deploy.resources.limits.memory`(550M/300M/50M, `mem_limit` 미혼용), app은 `depends_on: postgres: condition: service_healthy`로 기동 게이트, JVM 힙은 `JAVA_TOOL_OPTIONS`로 재빌드 없이 조정 가능하게 주입.
- `deploy/Caddyfile` 신설 — `{$DOMAIN:localhost}` 자동 HTTPS(80→443 리다이렉트 기본 동작), `@blocked-actuator` 매처로 `/actuator/health`만 통과시키고 `/actuator/info`를 포함한 나머지 `/actuator/*`는 404 차단(D-20). `caddy fmt --overwrite`로 공식 포맷 적용.
- `deploy/compose.local.yml` 신설(28줄, 40줄 미만 기준 충족) — GHCR 이미지 대신 `gw-be:test`로 치환, 로컬 확인용 `18080:8080` 포트 노출, caddy `environment`에 `DOMAIN=localhost`/`ACME_EMAIL` 명시.
- **로컬 3컨테이너 실기동 검증(Task 3)**을 실제로 수행해 아래를 전부 관측으로 확정:
  - postgres → app 기동 순서: `StartedAt` 5초 차 확인, healthcheck 게이트 정상 작동
  - 메모리 제한 실측: app `576716800`(550M) / postgres `314572800`(300M) / caddy `52428800`(50M) — `deploy.resources.limits.memory`가 non-swarm `docker compose up`에도 그대로 적용됨을 실증
  - actuator 차단이 Caddy 계층에서만 일어남: 엣지(`https://localhost`)는 `/actuator/health` 200 / `/actuator/info` 404, app 컨테이너 내부 직접 호출은 둘 다 200
  - 80→443 리다이렉트: `http://localhost/actuator/health` → 308
  - Flyway: 최초 기동 시 V1~V12 12건 `Successfully applied`, `down`(볼륨 미삭제) 후 재기동 시 `Schema "public" is up to date. No migration necessary.`로 볼륨 영속 증명
  - 검증 후 `down -v`로 정리, `gw-be:test` 이미지는 보존, 남은 컨테이너 0개
- **버그 2건 발견 및 compose 레벨 우회**(둘 다 Dockerfile·Caddyfile 원본 대신 compose에서 고침 — 아래 Deviations 참조).

## Task Commits

Each task was committed atomically:

1. **Task 1: deploy/compose.prod.yml 신설** - `bf3fe68` (feat)
2. **Task 2: deploy/Caddyfile + deploy/compose.local.yml 신설** - `23aa8e5` (feat)
3. **Task 3: 로컬 3컨테이너 기동 검증 중 발견한 버그 우회** - `889306d` (fix)

_TDD 아님 — 인프라 오케스트레이션 아티팩트(compose·Caddyfile)는 conventions §10.0 면제 대상. 아래 "테스트 미작성" 절 참조._

## Files Created/Modified

- `deploy/compose.prod.yml` - app+postgres+caddy 운영 오케스트레이션, healthcheck 게이트, 메모리 제한, container_name을 `gw-prod-*`로, app entrypoint/command를 실제 jar 파일명 자동 탐색으로 우회
- `deploy/Caddyfile` - 자동 HTTPS + actuator 외부 차단 2차 방어선, `caddy fmt` 공식 포맷 적용
- `deploy/compose.local.yml` - 로컬 검증 최소 오버라이드(이미지 치환, 호스트 포트, DOMAIN/ACME_EMAIL 명시)

## Decisions Made

- **container_name 접두사를 `gw-prod-*`로 변경**: 로컬 dev용 `docker-compose.yml`이 이미 `gold-wrestling-postgres`라는 컨테이너 이름을 쓰고 있어(개발자 Mac에서 상시 실행 중), 운영 compose가 같은 이름을 쓰면 로컬 사전 검증(D-05) 자체가 불가능했다. 서버에는 로컬 dev compose가 없으므로 실제 운영 동작에는 영향이 없다.
- **`local_certs` 전역 옵션 주입을 포기**: 07-RESEARCH.md는 로컬 검증에 `local_certs` 또는 `tls internal`이 필요하다고 가정했지만, 실측 결과 Caddy는 사이트 주소가 `localhost`처럼 공인 도메인이 아님을 스스로 감지해 내부 CA로 자체 서명 인증서를 자동 발급한다(로그: `"issuer":"local"`). 별도 전역 옵션 없이 `deploy/Caddyfile`을 그대로 써도 로컬 HTTPS가 동작해, 원래 계획했던 Compose inline config(`configs.content` + `import`) 방식보다 훨씬 단순한 설계로 대체했다.
- **app 서비스에 `entrypoint`/`command` 오버라이드 추가**: 아래 Deviations 참조.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking, Dockerfile 범위 밖 우회] app 컨테이너가 `Unable to access jarfile application.jar`로 기동 실패**
- **Found during:** Task 3, `docker compose up -d --wait` 최초 실행
- **Issue:** `Dockerfile`의 `ENTRYPOINT ["java", "-jar", "application.jar"]`은 산출물 jar 이름이 `application.jar`라고 가정하지만, `build.gradle.kts`가 `bootJar`의 `archiveFileName`을 재정의하지 않아 실제 파일명은 `gold-wrestling-be-0.0.1-SNAPSHOT.jar`다. `docker create` + `docker cp`로 이미지 내부를 직접 확인해 확정.
- **Fix:** `deploy/compose.prod.yml`의 app 서비스에 `entrypoint: ["sh", "-c"]` + `command: ['exec java -jar "$(find /application -maxdepth 1 -name "*.jar" | head -1)"']`를 추가해 실제 jar 파일을 런타임에 탐색해 실행하도록 우회. **`Dockerfile` 자체는 이번 실행의 hard constraint로 수정 금지 범위**라 근본 수정(ENTRYPOINT를 실제 파일명에 맞추거나 `archiveFileName = "application.jar"`로 고정)은 이 플랜에서 하지 않았다 — 후속 커밋 필요.
- **Files modified:** `deploy/compose.prod.yml`
- **Verification:** 우회 적용 후 `up -d --wait`에서 app이 `healthy`로 전환, actuator 200 확인
- **후속 해소(오케스트레이터, 같은 청크):** Dockerfile 빌더 스테이지에 `cp build/libs/*.jar application.jar` 후 extract하도록 고쳐(공식 Boot 4.1 파셜 Dockerfile 형태, D-176) 이미지 안에 `application.jar`가 생기게 했고, compose의 `entrypoint`/`command` 우회는 제거했다. 재빌드한 `gw-be:test`로 3컨테이너를 다시 띄워 `docker inspect .Path/.Args`가 `java [-jar application.jar]`임을 확인했고, health 200 / info 404(Caddy) / info 200(앱 내부) / 80→443 308 / 메모리 limit 576716800·314572800·52428800 / Flyway 최초 적용·재기동 up-to-date가 모두 그대로 재현됐다. `docker stats` 관측: app 364.5MiB / postgres 34.1MiB / caddy 18.7MiB.
- **Committed in:** `889306d`

**2. [Rule 1 - Bug] Caddy가 "server block without any key is global configuration, and if used, it must be first" 에러로 재시작 루프**
- **Found during:** Task 3, `deploy/.env`를 `.env.example` 그대로 복사한 뒤 caddy 컨테이너가 `Restarting` 상태 반복
- **Issue:** Caddyfile의 `{$DOMAIN:localhost}`·`{$ACME_EMAIL}` 콜론 기본값 문법은 환경변수가 "**아예 설정되지 않았을 때**"만 적용된다. `docker compose`의 `env_file: .env`는 `.env.example`의 빈 줄(`DOMAIN=`, `ACME_EMAIL=`)도 "빈 문자열로 설정됨"으로 만들기 때문에 기본값이 적용되지 않고, 빈 사이트 주소 블록이 생겨 파싱이 깨졌다(`email` 지시어는 빈 인자를 거부, 빈 사이트 주소는 두 번째 "키 없는 블록"으로 오인식됨). `docker run -e DOMAIN= ... caddy validate`로 동일 에러를 재현해 근본 원인을 확정.
- **Fix:** `deploy/compose.local.yml`의 caddy 서비스 `environment`에 `DOMAIN: localhost`, `ACME_EMAIL: dev@example.invalid`를 명시적으로 채워 `deploy/.env`를 수정하지 않아도 로컬 검증이 그대로 동작하게 했다. `deploy/Caddyfile` 본문은 무변경.
- **Files modified:** `deploy/compose.local.yml`
- **Verification:** 재적용 후 caddy `healthy`, `https://localhost/actuator/health` 200, `http://localhost/actuator/health` 308 확인
- **Committed in:** `889306d`

---

**Total deviations:** 2 auto-fixed (1 blocking 우회, 1 bug fix)
**Impact on plan:** 둘 다 compose 파일(허용 범위) 안에서 해결했고 `deploy/Caddyfile`·`Dockerfile` 원본은 건드리지 않았다. Dockerfile 버그는 실제 운영 배포에도 그대로 영향을 미치므로 **07-04 또는 후속 커밋에서 Dockerfile 자체를 반드시 고쳐야 한다** — 아래 Next Phase Readiness 참조.

## Issues Encountered

- Docker 로컬 환경에 이미 `gold-wrestling-postgres`라는 이름의 dev용 컨테이너가 상시 실행 중이어서 최초 `up -d` 시도가 이름 충돌로 실패했다 — container_name 접두사 분리로 해결(위 Decisions 참조). 호스트 포트 5432를 dev postgres가 이미 점유하고 있었지만, 운영 compose는 postgres 포트를 노출하지 않으므로 포트 충돌 자체는 없었다.

## 테스트 미작성 파일과 그 이유 (§10.0 면제)

- `deploy/compose.prod.yml`, `deploy/Caddyfile`, `deploy/compose.local.yml` — `docs/conventions.md` §10.0 면제 목록(빌드/오케스트레이션 설정, docker-compose류)과 동일 성격의 인프라 아티팩트다. JUnit으로 검증할 도메인 로직이 없고, 이 플랜의 동작 검증은 Task 3에서 실제 `docker compose up -d --wait`·`curl`·`docker inspect`·Flyway 로그 확인으로 전부 실측했다 — Gradle 테스트 스위트의 대상이 아니라 Docker 데몬/네트워크 상태를 검증하는 항목이기 때문이다.

## 로컬 실기동 관측값 (07-04 운영 문서·07-06 실서버 대조용)

**(1) `docker inspect --format '{{.HostConfig.Memory}}'` 실측 바이트값**

| 컨테이너 | 값(바이트) | 목표 |
|---|---|---|
| app | `576716800` | 550M |
| postgres | `314572800` | 300M |
| caddy | `52428800` | 50M |

셋 다 목표값과 정확히 일치 — `deploy.resources.limits.memory`가 스웜 없는 `docker compose up`에도 그대로 적용됨이 확정됐다(07-RESEARCH.md MEDIUM-HIGH 신뢰도 가정이 TRUE로 검증됨).

**(2) actuator 차단 관측 — 엣지(Caddy) vs 앱 내부 직접 호출**

| 경로 | 엣지(`https://localhost`, Caddy 경유) | 앱 내부(`docker compose exec app curl localhost:8080`) |
|---|---|---|
| `/actuator/health` | `200` | `200` |
| `/actuator/info` | `404` | `200` |

`/actuator/info`가 엣지에서만 차단되고 앱 내부에서는 200이라는 것은, 차단이 앱(`management.endpoints.web.exposure`)이 아니라 Caddy 계층에서 일어난다는 것을 증명한다(D-20). 추가로 `http://localhost/actuator/health` → `308`(HTTP→HTTPS 리다이렉트) 확인.

**(3) 로컬 오버라이드의 `local_certs` 주입 방식과 그 이유**

**주입하지 않았다.** 07-RESEARCH.md는 `local_certs` 전역 옵션 또는 `tls internal`이 로컬 검증에 필요하다고 가정했지만, 실측 결과 Caddy는 사이트 주소가 `localhost`(공인 도메인이 아님)임을 자동 감지해 내부 CA로 자체 서명 인증서를 스스로 발급한다(caddy 로그: `"logger":"tls.obtain","msg":"certificate obtained successfully","identifier":"localhost","issuer":"local"`). 따라서 `deploy/Caddyfile`은 완전히 무변경이고, `deploy/compose.local.yml`은 caddy `environment`에 `DOMAIN=localhost`·`ACME_EMAIL=dev@example.invalid`만 명시(둘 다 `.env`에 빈 값으로 두면 Caddy 파싱이 깨지는 별도 버그가 있어 필수 — 위 Deviations #2 참조)한다. 애초 계획했던 Compose inline `configs.content` + `import` 방식(구현·테스트했으나 두 개의 "키 없는 전역 블록"이 생겨 `"must be first"` 에러가 남을 확인)은 폐기했다.

**(4) §10.0 면제 사유 한 줄**

compose·Caddyfile은 `docs/conventions.md` §10.0의 오케스트레이션/설정 파일 면제 대상이라 JUnit 테스트를 만들지 않았고, 동작 검증은 Task 3의 실제 `docker compose up`/`curl`/`docker inspect`/로그 확인으로 갈음했다.

## User Setup Required

None - 이 플랜은 외부 서비스 설정이 필요 없다. 로컬 검증용 `deploy/.env`는 `.gitignore`(패턴 `.env`, `.env.*`)로 확실히 무시됨을 `git check-ignore -q deploy/.env`로 재확인했고 커밋되지 않았다.

## 이번에 쓴 기술

1. **`depends_on: condition: service_healthy` — healthcheck 기반 기동 순서 게이트**
   - **왜 필요했는가:** app이 postgres보다 먼저 뜨면(또는 postgres가 아직 커넥션을 받을 준비가 안 됐는데 app이 먼저 붙으면) Flyway 마이그레이션이나 Hikari 커넥션 풀 초기화가 실패할 수 있다. 단순히 `depends_on: postgres`(컨테이너 "시작"만 대기)로는 postgres 프로세스가 떠 있어도 아직 쿼리를 못 받는 순간을 놓칠 수 있다.
   - **안 썼으면 뭐가 깨지는가:** 컨테이너 시작 순서와 "쿼리를 받을 준비"가 다르므로, app이 postgres 기동 도중에 접속을 시도해 첫 배포 때마다 랜덤하게 크래시하거나 재시도 루프에 빠질 수 있다 — 이번에 `StartedAt` 타임스탬프 비교(postgres 14:28:44 → app 14:28:49)로 순서가 실제로 지켜짐을 확인했다.

2. **★ `deploy.resources.limits.memory` — Compose Specification의 non-swarm 메모리 제한**
   - **왜 필요했는가:** RAM 1GB짜리 t3.micro 서버에서 앱 하나가 메모리를 과점하면 OS 전체가 OOM killer에 걸려 postgres·caddy까지 함께 죽을 수 있다. 컨테이너마다 상한(550M/300M/50M)을 걸어 한 프로세스의 폭주가 다른 프로세스를 끌고 내려가지 않게 격리한다.
   - **안 썼으면 뭐가 깨지는가:** 이번에 `docker inspect`로 실측한 값(576716800/314572800/52428800 바이트)이 목표와 정확히 일치했다 — 이 관측이 없었다면 "옛 버전 문법(`mem_limit`)과 혼용하면 조용히 무시된다"는 07-RESEARCH.md의 Pitfall이 이 프로젝트에도 실제로 재현되는지 아무도 확인하지 못한 채 운영에 나갔을 것이다.

3. **Caddy 자동 HTTPS의 "공인 도메인 아님 자동 감지" — `{$DOMAIN:localhost}` + 내부 CA**
   - **왜 필요했는가:** 원래 계획(07-RESEARCH.md)은 로컬 검증에 `local_certs` 전역 옵션이나 `tls internal` 같은 별도 로컬 전용 설정이 필요하다고 가정했다. 실제로 붙여 보니 Caddy가 사이트 주소 `localhost`를 스스로 "공인 인터넷 도메인이 아님"으로 판단해 Let's Encrypt 대신 내부 CA로 자체 서명 인증서를 즉시 발급했다.
   - **안 썼으면 뭐가 깨지는가:** 이 사실을 모른 채 `local_certs`를 억지로 주입하려다 실제로 버그(전역 옵션 블록이 두 개가 되는 Caddyfile 파싱 오류)를 만났다 — 정확한 원인을 실측하지 않았다면 "왜 안 되지?"를 반복하며 잘못된 방향(예: Caddyfile 본문 수정)으로 시간을 썼을 것이다. 이번 실측 덕분에 `deploy/Caddyfile`을 완전히 무변경 상태로 유지하면서 로컬 검증을 통과시켰다.

4. **★ Caddyfile 환경변수 콜론 기본값(`{$VAR:default}`)의 "설정 안 됨" vs "빈 문자열로 설정됨" 구분**
   - **왜 필요했는가:** `docker compose`의 `env_file`은 `.env` 파일에 있는 키를 값이 비어 있어도(`DOMAIN=`) 컨테이너 환경변수로 "설정"한다. bash의 `${VAR:-default}`는 "비어 있어도 unset과 동일 취급"하지만, Caddy의 `{$VAR:default}`는 그렇지 않다 — 변수가 존재하기만 하면(빈 문자열이어도) 기본값을 적용하지 않는다.
   - **안 썼으면 뭐가 깨지는가:** `.env.example`을 그대로 복사해 로컬 검증을 시도하면 `DOMAIN=`·`ACME_EMAIL=`이 빈 문자열로 주입돼 Caddy가 매번 `"must be first"` 에러로 재시작 루프에 빠진다 — 이 구분을 몰랐다면 "Caddyfile 문법이 잘못됐다"고 오판하고 이미 검증된 `deploy/Caddyfile` 자체를 잘못 고쳤을 수 있다. 실제 원인은 env 값 자체였다.

5. **일부러 안 쓴 것 — Compose inline `configs.content` + Caddyfile `import`**
   - 처음에는 `local_certs`를 주입하기 위해 Compose Specification의 인라인 `configs` 기능(새 파일을 만들지 않고 compose 안에 짧은 Caddyfile 조각을 담아 `import`로 원본을 불러오는 방식)을 구현했다. 문법적으로는 유효했지만 실제로 `caddy run` 시 "전역 옵션 블록이 두 번 나타나면 안 된다"는 Caddy 자체 제약에 걸려 동작하지 않았고, 애초에 `local_certs`가 불필요하다는 게 밝혀지면서 이 접근 전체를 폐기했다 — 복잡한 우회보다 "왜 필요하다고 생각했는지"부터 실측으로 재확인하는 편이 더 빨랐다.

## Next Phase Readiness

- ~~**Dockerfile 근본 버그가 남아 있다.**~~ **해소(같은 청크, D-176)** — Dockerfile이 추출 전 jar를 `application.jar`로 고정하도록 고쳐졌고 compose 우회는 제거됐다. 07-05가 GHCR에 push할 이미지는 이 수정본으로 빌드해야 한다(`gw-be:test`는 이미 재빌드됨).
- (1) 메모리 실측 3값, (2) actuator 이중 관측, (3) `local_certs` 미주입 사유, (4) §10.0 면제 사유는 모두 위 "로컬 실기동 관측값" 절에 기록 완료 — 07-04 `docs/operations.md`·07-06 실서버 대조가 그대로 인용 가능하다.
- `deploy/` 3개 파일(`compose.prod.yml`, `Caddyfile`, `compose.local.yml`)이 Phase 8 배포 워크플로가 scp로 전달할 파일 세트로 확정됐다(D-18).
- 로컬 `docker-compose.yml`·`src/`·`application.yml`·`SecurityConfig.kt`에 diff 0건으로 D-17·hard constraint 준수 확인됨(`git status --porcelain` 클린).

---
*Phase: 07-container-server-setup*
*Completed: 2026-09-08*

## Self-Check: PASSED

- 생성 파일 4건 전부 존재 확인 (`deploy/compose.prod.yml`, `deploy/Caddyfile`, `deploy/compose.local.yml`, 이 SUMMARY 파일)
- 커밋 4건(`bf3fe68`, `23aa8e5`, `889306d`, `528aeb0`) 전부 `git log`에서 확인
