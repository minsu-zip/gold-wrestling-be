---
phase: 07-container-server-setup
plan: 02
subsystem: infra
tags: [docker, buildx, spring-boot-4, multi-arch, layered-jar, dockerfile]

# Dependency graph
requires:
  - phase: 07-container-server-setup (plan 01)
    provides: "운영 배포가 참조할 env 키 계약(SWAGGER_ENABLED 등), D-169~D-175 결정 로그"
provides:
  - "레포 루트 Dockerfile — Boot 4.1 tools jarmode 레이어 4종 추출 멀티스테이지, 비루트 실행"
  - ".dockerignore — 시크릿·빌드 산출물·계획 문서 빌드 컨텍스트 제외"
  - "docs/metrics.md — fat jar 대비 레이어드 이미지 절감 수치, 멀티 아키텍처 빌더 1회 컴파일 실측(A1 TRUE)"
  - "로컬 gw-be:test 이미지(멀티스테이지 검증 완료) — 07-03 compose 오버라이드가 재사용"
  - "gw-builder(docker-container 드라이버) buildx 빌더 — 07-05 GHCR push가 재사용"
affects: [07-03, 07-05, 08-deploy-pipeline]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "멀티스테이지 Dockerfile: FROM --platform=$BUILDPLATFORM 빌더 고정 + eclipse-temurin:21-jre-noble 런타임 + 비루트 USER app"
    - "Boot tools jarmode 레이어 4종(dependencies/spring-boot-loader/snapshot-dependencies/application) 순서 COPY"
    - "buildx --output=type=cacheonly 로컬 검증 (멀티플랫폼은 --load와 동시 사용 불가)"

key-files:
  created:
    - Dockerfile
    - .dockerignore
    - docs/metrics.md
  modified: []

key-decisions:
  - "가정 A1(빌더가 amd64+arm64 두 플랫폼에서 실제로 1회만 컴파일한다) 실측으로 TRUE 확인 — 최초 빌드·--no-cache-filter=builder 강제 재캐시 두 방식 모두 gradlew bootJar 실행 1회"
  - "레이어드 이미지의 이득은 전체 크기(동일, 563MB)가 아니라 코드 변경 시 재전송 바이트(72.2MB → 606kB, 약 119배)에서만 발생함을 실측으로 확정"

patterns-established:
  - "레이어드 vs fat jar 비교는 베이스·비루트·ENTRYPOINT를 동일하게 맞춘 임시 Dockerfile로 변수를 통제해 측정하고, 측정 후 임시 산출물을 삭제한다"

requirements-completed: [INFRA-01, INFRA-02]

# Metrics
duration: 약 45분
completed: 2026-09-08
---

# Phase 7 Plan 02: 컨테이너 이미지 빌드 아티팩트 Summary

**Boot 4.1 tools jarmode 레이어 4종 분리 멀티스테이지 Dockerfile을 신설해 비루트 실행·`.env` 제외를 로컬 빌드로 실증하고, buildx로 amd64+arm64 두 플랫폼을 빌드해 빌더가 실제로 1회만 컴파일한다는 가정(A1)을 두 가지 방식으로 검증했으며, fat jar와의 재배포 전송량 차이(72.2MB → 606kB)를 `docs/metrics.md`에 숫자로 남겼다.**

## Performance

- **Duration:** 약 45분
- **Completed:** 2026-09-08T14:12Z (23:12 KST)
- **Tasks:** 3/3 완료
- **Files modified:** 3 (전부 신설)

## Accomplishments

- 레포 루트 `Dockerfile` 신설 — `eclipse-temurin:21-jdk-noble` 빌더(`--platform=$BUILDPLATFORM`) → `java -Djarmode=tools ... extract --layers` → `eclipse-temurin:21-jre-noble` 런타임에 `dependencies`/`spring-boot-loader`/`snapshot-dependencies`/`application` 4개 레이어를 순서대로 COPY. 비루트 `app` 사용자로 `USER app` 전환.
- `.dockerignore` 신설 — `.env`·키 파일·`build/`·`.git/`·`.planning/`·`docs/` 등 제외. 이미지 안에 `.env`가 존재하지 않음을 `docker run --entrypoint sh ... ls -a /application`로 실증.
- `docker build -t gw-be:test .` 단일 플랫폼 빌드 성공, 비루트(`uid=999`) 실행 확인, `lib` 레이어 추출 확인.
  - **정정(오케스트레이터, 07-03 실기동 후):** 이 시점의 이미지에는 `application.jar`가 없었다 — extract를 `build/libs/*.jar`에 바로 돌려 application 레이어 파일명이 `gold-wrestling-be-0.0.1-SNAPSHOT.jar`로 남았고, 위 acceptance criteria의 "`application.jar`와 `lib` 또는 `BOOT-INF`" 검사는 `lib`만으로 통과해 버그를 놓쳤다. Dockerfile에 `cp build/libs/*.jar application.jar` 단계를 넣어 공식 형태로 고쳤고(D-176), 재빌드 후 `ls /application`에 `application.jar`·`lib`가 있음을 확인했다. 레이어 크기·절감 수치(`docs/metrics.md`)는 파일 이름만 바뀐 것이라 변하지 않는다.
- `gw-builder`(`docker-container` 드라이버) buildx 빌더 신규 생성 후 `linux/amd64,linux/arm64` 두 플랫폼을 `--output=type=cacheonly`로 빌드 성공. `--progress=plain` 로그에서 `gradlew bootJar` 실행 라인을 최초 빌드·`--no-cache-filter=builder` 강제 재캐시 두 방식 모두에서 **정확히 1회**로 확인 — 07-RESEARCH.md 가정 A1(빌더 1회 컴파일)이 TRUE로 검증됨.
- 비교 전용 임시 fat jar Dockerfile(스캐치패드, 커밋 대상 아님)을 빌드해 레이어드 이미지와 변수를 통제해 비교. 전체 이미지 크기는 동일(563MB — `tools extract`는 재배치일 뿐 바이트 증감 없음)하지만, 소스 1줄(주석) 변경 후 재빌드 시 다시 전송해야 하는 애플리케이션 레이어가 fat jar 72.2MB 대 레이어드 606kB로 약 119배 차이남을 실측.
- `docs/metrics.md` 신설(D-14) — 위 두 실측(이미지 크기·재전송 바이트, 멀티 아키텍처 빌더 컴파일 횟수)을 표+해설로 기록.
- 측정용 임시 소스 변경(`HealthController.kt` 주석 1줄)은 `git checkout --`로 되돌렸고, 임시 이미지(`gw-be:fatjar`, `gw-be:fatjar-changed`, `gw-be:test-changed`)와 스캐치패드의 임시 Dockerfile은 모두 삭제·정리 완료. `gw-be:test`는 07-03이 로컬 오버라이드 이미지로 재사용하도록 남겨 두었다.

## Task Commits

Each task was committed atomically:

1. **Task 1: Dockerfile·.dockerignore 신설 + 단일 플랫폼 빌드 검증** - `1d75445` (feat)
2. **Task 2: 멀티 아키텍처 빌드 + 빌더 1회 컴파일 실측** - 검증 전용 태스크, Dockerfile diff 없음(계획대로) — 별도 커밋 없음, 실측값은 Task 3 커밋에 포함된 `docs/metrics.md`에 기록
3. **Task 3: fat jar 대비 레이어드 이미지 절감 수치 측정 → docs/metrics.md 신설** - `b3df893` (docs)

_TDD 아님 — 이 플랜은 인프라 아티팩트(Dockerfile·설정·문서) 신설 성격이라 conventions §10.0 면제 대상. 아래 "테스트 미작성" 절 참조._

## Files Created/Modified

- `Dockerfile` - Boot 4.1 tools jarmode 레이어 4종 추출 멀티스테이지 빌드, 빌더 `eclipse-temurin:21-jdk-noble`, 런타임 `eclipse-temurin:21-jre-noble`, 비루트 `USER app`
- `.dockerignore` - 시크릿(`.env` 등)·빌드 산출물·VCS/IDE/계획 문서를 빌드 컨텍스트에서 제외
- `docs/metrics.md` - fat jar 대비 레이어드 이미지 절감 수치(§1) + 멀티 아키텍처 빌더 컴파일 실측(§2)

## Decisions Made

- **가정 A1 검증 결과 TRUE**: 07-RESEARCH.md가 "커뮤니티 통설이며 이번 phase가 실측해야 한다"고 명시한 가정 — `FROM --platform=$BUILDPLATFORM`으로 빌더를 호스트 네이티브 플랫폼(이 환경은 `linux/arm64`, Apple Silicon Mac)에 고정하면 JVM 바이트코드가 아키텍처 독립적이라는 성질 덕에 `linux/amd64` 런타임 스테이지가 같은 빌더 산출물을 그대로 `COPY --from=builder`로 재사용한다. 최초 빌드(캐시 없음)와 `--no-cache-filter=builder`로 빌더 캐시를 강제 무효화한 재측정 모두에서 `gradlew bootJar` 실행이 로그에 정확히 1회만 등장 — INFRA-02의 "네이티브 1회 컴파일"이 반증되지 않았다(사용자 보고 불필요, 계획대로 진행).
- 레이어드 이미지의 실질 이득이 어디서 발생하는지 정량 확정: 전체 이미지 바이트 수는 fat jar와 동일하고(`tools extract`는 압축 해제 재배치일 뿐), 이득은 **재배포 시 다시 전송해야 하는 바이트**에서만 발생한다(72.2MB → 606kB). 이 수치를 `docs/metrics.md`에 남겨 향후 "왜 레이어드를 쓰는가"를 다시 논쟁할 필요가 없게 했다.

## Deviations from Plan

None - 계획대로 실행됨. 3개 태스크 모두 acceptance_criteria를 실제 명령 실행으로 통과시켰고, Dockerfile 추측 변형(레이어 추출 명령 등)은 07-RESEARCH.md에 인용된 공식 명령을 그대로 썼다(재확인 불필요, verify-boot4-api 스킬을 새로 돌릴 필요가 없었음).

**Dockerfile 자기 수정 1건 (스타일 정정, 태스크 범위 내):** Task 1 acceptance criteria 검증 중 `grep -ciE 'ArchiveClassesAtExit|SharedArchiveFile' Dockerfile`이 `1`을 반환했다 — CDS를 "넣지 않았다"고 설명하는 한국어 주석 안에 그 두 플래그 이름을 문자 그대로 인용해서 발생한 자기 지시적(self-referential) 매치였다. 실제 CDS 단계는 없었다(D-15 그대로 준수). 주석 문구를 "CDS/AppCDS 관련 클래스 아카이브 생성·훈련 실행 단계"로 바꿔 플래그 이름을 지우고 재검증(0건)했다. Dockerfile 커밋(`1d75445`) 안에 이미 반영돼 있어 별도 커밋을 만들지 않았다.

## Issues Encountered

None.

## 테스트 미작성 파일과 그 이유 (§10.0 면제)

- `Dockerfile` — `docs/conventions.md` §10.0 면제 목록(`build.gradle.kts`, docker-compose, 문서류)과 같은 성격의 빌드 설정 아티팩트다. JUnit으로 검증할 도메인 로직이 없고, 동작 검증은 이 플랜의 `<verify>`에 명시된 실제 `docker build`/`docker run`/grep 실행으로 갈음했다(레포지토리 상태가 아니라 Docker 데몬 상태를 검증하는 항목이라 애초에 Gradle 테스트 스위트의 대상이 아니다).
- `.dockerignore` — 패턴 나열 파일, §10.0 면제. `.env` 제외 여부는 Task 1의 자동 검증(`ls -a /application` 안에 `.env` 부재 확인)으로 실증했다.
- `docs/metrics.md` — 문서, §10.0 면제.

## 이번에 쓴 기술

1. **멀티스테이지 Docker 빌드 — 빌더/런타임 스테이지 분리**
   - **이 코드에서 왜 필요했는가:** `./gradlew bootJar`를 돌리려면 JDK와 소스 전체, Gradle wrapper 캐시가 필요하지만, 실제로 컨테이너를 서버에서 실행할 때는 컴파일된 `jar`와 JRE만 있으면 된다. 하나의 이미지에 둘 다 넣으면 컴파일러·소스코드·Gradle 캐시까지 운영 서버로 배포되는 셈이라 이미지가 불필요하게 커지고, 소스코드(도메인 로직)가 이미지 레이어 안에 그대로 남는다.
   - **안 썼으면 뭐가 깨지는가:** 이미지 크기가 커지는 건 둘째치고, GHCR public 패키지(D-175로 이미 감수하기로 한 리스크)에 컴파일 전 소스가 통째로 남아 있으면 "바이트코드 디컴파일 노출"보다 더 직접적으로 소스 전체가 노출된다.

2. **★ Boot `tools` jarmode 레이어 추출 — `java -Djarmode=tools -jar app.jar extract --layers`**
   - **이 코드에서 왜 필요했는가:** 우리 앱은 의존성(Spring/Kotlin/JDBC 등)이 71.6MB인데 실제 우리가 짠 코드는 606kB뿐이다. `jar` 하나를 통째로 이미지 레이어 하나로 COPY하면, Docker는 "레이어 전체가 바뀌었다"고 보고 코드 한 줄만 바꿔도 71.6MB 전부를 다시 빌드·전송한다. `tools` jarmode는 이 `jar`를 의존성/로더/스냅샷/애플리케이션 코드 4개 디렉토리로 미리 쪼개 각각을 별도 레이어로 COPY하게 해 준다.
   - **안 썼으면 뭐가 깨지는가:** RAM 1GB·네트워크가 넉넉하지 않은 t3.micro 서버에 배포할 때마다 72MB를 새로 pull해야 한다 — 이번 실측으로 606kB 대 72.2MB, 약 119배 차이임을 직접 확인했다(`docs/metrics.md` §1).

3. **★ Docker BuildKit `--platform=$BUILDPLATFORM` — 빌더 스테이지를 빌드 호스트 아키텍처에 고정**
   - **이 코드에서 왜 필요했는가:** 최종 이미지는 `amd64`(서버 EC2)와 `arm64`(로컬 Mac, 향후 서버 이전 대비) 두 아키텍처용으로 나와야 한다. 그런데 자바 바이트코드는 CPU 아키텍처와 무관하게 동일하다 — Go처럼 아키텍처별로 따로 컴파일할 필요가 없다. `$BUILDPLATFORM`은 "지금 빌드를 실행 중인 컴퓨터의 아키텍처"를 가리키는 BuildKit 내장 변수라, 빌더 스테이지를 여기에 고정하면 두 번째 타겟 아키텍처는 QEMU 에뮬레이션도, 재컴파일도 없이 같은 빌더 산출물을 그대로 재사용한다.
   - **안 썼으면 뭐가 깨지는가:** 빌더 스테이지까지 `--platform`을 타겟(예: `linux/amd64,linux/arm64`)에 맞춰 두면 BuildKit이 각 아키텍처마다 별도 빌더를 만들어, Apple Silicon Mac에서 `amd64`용 컴파일이 QEMU 에뮬레이션 위에서 다시 한 번 돌아간다 — 이번 실측(120초 vs 76초, 그리고 `gradlew bootJar` 로그 라인 수 1회 확인)이 없었다면 이 차이를 코드 리뷰만으로는 확인할 방법이 없었다.

4. **비루트 컨테이너 실행 사용자 (`groupadd --system` + `useradd --system` + `USER`)**
   - **이 코드에서 왜 필요했는가:** 컨테이너는 기본적으로 `root`로 프로세스를 실행한다. 애플리케이션에 컨테이너 탈출(escape) 취약점이 있으면, `root`로 돌고 있었을 경우 호스트에서도 `root` 권한을 얻을 수 있다. `eclipse-temurin` 공식 이미지는 이 계정을 미리 만들어 주지 않는다(Node 같은 일부 언어 이미지와 달리) — 직접 `groupadd`/`useradd`로 시스템 계정을 만들고 `USER app`으로 전환해야 한다.
   - **안 썼으면 뭐가 깨지는가:** `docker run --entrypoint id gw-be:test -u`가 `0`(root)을 반환했을 것이고, T-07-03(권한 상승 위협)의 완화 조치가 실제로는 적용되지 않은 채 "적용했다"고 잘못 보고하게 됐을 것이다 — 이번엔 `uid=999`로 실제 확인했다.

5. **일부러 안 쓴 것 — CDS/AppCDS(클래스 데이터 공유)**
   - CDS는 JVM 기동 시간을 단축시키는 기법(자주 쓰는 클래스를 미리 아카이브해 클래스 로딩을 건너뜀)이지만, 이번 Dockerfile에는 넣지 않았다. D-15가 v1.2 후보로 명시적으로 미룬 항목이라, 이번 실측이나 구현 대상에서 제외했다 — 넣었다면 "빌더 1회 컴파일" 실측(A1)에 훈련 실행 스텝까지 섞여 결과 해석이 복잡해졌을 것이다.

## User Setup Required

None - Docker Desktop은 이미 실행 중이었다(사전 전제 D-02 충족 확인). `gw-builder` buildx 빌더는 이 플랜이 최초 생성했고 07-05(GHCR push)가 그대로 재사용한다 — 별도 사용자 설정 불필요.

## Next Phase Readiness

- **(1) 빌더 컴파일 실행 횟수 실측값과 가정 A1 진위:** 최초 빌드·강제 재캐시 두 방식 모두 **1회**, A1 **TRUE**로 검증됨 (`docs/metrics.md` §2).
- **(2) 두 이미지 크기 실제 숫자:** `gw-be:test`(레이어드) 563MB, `gw-be:fatjar`(비교용, 삭제됨) 563MB — 전체 크기는 동일. 코드 변경 시 재전송 레이어는 레이어드 606kB 대 fat jar 72.2MB (`docs/metrics.md` §1).
- **(3) §10.0 면제:** 위 "테스트 미작성 파일과 그 이유" 절 참조 — Dockerfile·`.dockerignore`·`docs/metrics.md` 3개 파일 모두 conventions §10.0 면제 대상(빌드 설정·문서)이라 JUnit 테스트를 만들지 않았다.
- `gw-be:test` 로컬 이미지와 `gw-builder` buildx 빌더가 07-03(compose)·07-05(GHCR push)에 그대로 넘어간다. `docker-compose.yml`·`build.gradle.kts`·`src/`에 diff 0건으로 D-17 준수 확인됨.
- 블로커 없음.

---
*Phase: 07-container-server-setup*
*Completed: 2026-09-08*

## Self-Check: PASSED

- 생성 파일 4건 전부 존재 확인 (`Dockerfile`, `.dockerignore`, `docs/metrics.md`, 이 SUMMARY 파일)
- 커밋 3건(`1d75445`, `b3df893`, `ba426ec`) 전부 `git log`에서 확인
