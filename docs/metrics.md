# 운영 실측 지표 (metrics.md)

> 이 문서는 이미지 빌드·배포 아티팩트 관련 **실측 수치**의 정본이다. Phase 7이 최초 생성했고
> Phase 10(VERIFY-02, k6 부하테스트)이 이어서 항목을 추가한다. 값 자체의 "왜 이 선택인가"는
> `docs/decisions.md`를 본다 — 이 문서는 숫자와 그 숫자가 의미하는 것만 다룬다.
> 서버 실측(메모리 RSS·`docker pull` 실전 소요 시간)은 이 문서가 아니라 `docs/operations.md`의
> 메모리 예산표가 담당한다(Phase 7 07-06, 수동 배포 시 작성 예정).

## 1. 이미지 빌드 전략 절감 수치 (07-CONTEXT D-14)

**측정 일자:** 2026-09-08
**측정 환경:** 로컬 Mac (Apple Silicon, `linux/arm64` 네이티브), Docker Desktop 28.5.1, buildx v0.29.1-desktop.1, BuildKit v0.25.1(단일 플랫폼 로컬 빌드는 `default`/`desktop-linux` 빌더 사용)
**측정 방법:** 동일 소스(`build.gradle.kts`·`src/`)에서 두 Dockerfile을 각각 빌드해 비교했다.
- **레이어드**: 레포 루트 `Dockerfile` (07-CONTEXT D-13, Boot `tools` jarmode `extract --layers`로 dependencies/spring-boot-loader/snapshot-dependencies/application 4개 레이어 분리)
- **fat jar**: 비교 전용 임시 Dockerfile(레포에 커밋되지 않음, 스캐폴드 삭제 완료) — 베이스 이미지·비루트 사용자·`ENTRYPOINT`는 레이어드 쪽과 동일하게 맞추고 `COPY --from=builder .../*.jar application.jar` 한 줄로 끝나는 단일 레이어 런타임만 다르게 구성해 변수를 통제했다.

| 항목 | fat jar 단일 레이어 | 레이어 추출 | 차이 |
|---|---|---|---|
| 전체 이미지 크기 (`docker image ls`) | 563MB | 563MB | 0 (동일 — 아래 해설 참조) |
| 애플리케이션 콘텐츠 레이어 수 | 1개 (`application.jar` 72.2MB 단일 레이어) | 4개 (`dependencies` 71.6MB + `spring-boot-loader` 4.1kB + `snapshot-dependencies` 4.1kB + `application` 606kB) | 1개 → 4개 |
| 소스 1줄(주석) 변경 후 **재빌드 시 다시 만들어지는 애플리케이션 레이어 총합** | 72.2MB (`application.jar` 레이어 전체 재생성) | 606kB (`application` 레이어만 재생성, 나머지 3개는 buildx `CACHED`) | 약 **119배** 감소 (72.2MB → 606kB) |

**이 수치가 의미하는 것:** 두 이미지의 **전체 크기는 같다** — `tools extract`는 fat jar를 압축 해제해 디렉토리 4개로 재배치할 뿐 바이트를 더하거나 빼지 않기 때문이다(레이어드 쪽 4개 레이어 합계 71.6MB+4.1kB+4.1kB+606kB ≈ 72.2MB로 fat jar의 72.2MB 단일 레이어와 사실상 동일). **차이는 "코드만 바뀐 재배포에서 무엇을 다시 전송해야 하는가"에서만 발생한다.** 이 프로젝트는 의존성(BOM이 관리하는 Spring/Kotlin/JDBC 라이브러리 전체)이 71.6MB를 차지하는데, fat jar는 코드 한 줄만 바꿔도 이 71.6MB를 포함한 단일 레이어 전체(72.2MB)를 다시 빌드해 서버가 다시 pull해야 한다. 레이어드는 자주 바뀌지 않는 의존성 레이어를 그대로 캐시 히트시키고 실제로 바뀐 애플리케이션 코드가 담긴 606kB짜리 `application` 레이어만 다시 전송한다. RAM 1GB·업로드 대역폭이 넉넉하지 않은 t3.micro 서버에 배포 때마다 72MB를 반복 전송하는 것과 606kB를 전송하는 것은 배포 소요 시간·네트워크 비용 모두에서 체감 차이가 크다.

## 2. 멀티 아키텍처 빌드 실측

**측정 방법:** `gw-builder`(`docker-container` 드라이버, `docker buildx create --name gw-builder --driver docker-container --bootstrap --use`)로 `linux/amd64,linux/arm64` 두 플랫폼을 `--output=type=cacheonly`로 빌드하고 `--progress=plain` 로그에서 `RUN ... ./gradlew bootJar` 실행 라인 수를 셌다.

| 항목 | 값 |
|---|---|
| 대상 플랫폼 | `linux/amd64`, `linux/arm64` |
| 빌더 스테이지(`FROM --platform=$BUILDPLATFORM ...`) 컴파일 실행 횟수 (최초 빌드, 캐시 없음) | **1회** (호스트 네이티브 플랫폼 `linux/arm64`에서만 실행 — `linux/amd64` 런타임 스테이지는 같은 빌더 산출물을 `COPY --from=builder`로 그대로 사용) |
| 빌더 스테이지 컴파일 실행 횟수 (`--no-cache-filter=builder`로 빌더 캐시를 강제 무효화한 재측정) | **1회** (재측정에서도 `gradlew bootJar` 라인이 정확히 1회만 등장 — amd64용 별도 컴파일 없음) |
| 최초 멀티플랫폼 빌드 소요 시간(캐시 없음, `docker buildx build --platform linux/amd64,linux/arm64 --output=type=cacheonly`) | 약 120초 |
| `--no-cache-filter=builder` 강제 재컴파일 빌드 소요 시간 | 약 76초 |
| `docker buildx ls`에 `gw-builder`가 `linux/amd64`·`linux/arm64` 플랫폼을 모두 보유하는지 | 예 (두 플랫폼 모두 표시 확인) |

**가정 A1(07-RESEARCH.md — "빌더가 1회만 컴파일한다"는 커뮤니티 통설) 진위: 실측으로 확인됨(TRUE).** `FROM --platform=$BUILDPLATFORM`으로 빌더 스테이지를 호스트 네이티브 플랫폼에 고정하면, JVM 바이트코드가 아키텍처 독립적이라는 성질 덕에 `linux/amd64`·`linux/arm64` 두 타겟 그래프가 동일한 빌더 산출물을 공유한다 — QEMU 에뮬레이션도, 두 번째 컴파일도 발생하지 않았다. `--no-cache-filter=builder`로 빌더 레이어 캐시를 강제로 지운 재측정에서도 `gradlew bootJar` 실행이 정확히 1회만 로그에 나타나(amd64 전용 빌더 스테이지 실행 흔적 0건), INFRA-02가 요구하는 "네이티브 1회 컴파일"이 이 Dockerfile 구조로 실제 보장됨을 확인했다.

## 3. GHCR 게시 결과 (INFRA-02, 07-CONTEXT D-02/D-03 · D-175)

**게시 일자:** 2026-09-09 (UTC 01:09~01:10)
**게시 경로:** 로컬 Mac(arm64) `gw-builder` → `docker buildx build --platform linux/amd64,linux/arm64 --push` → GHCR. Phase 8이 자동화할 push 경로를 손으로 1회 밟은 것이다.
**push 명령(원문):** `docker buildx build --builder gw-builder --platform linux/amd64,linux/arm64 -t ghcr.io/minsu-zip/gold-wrestling-be:latest -t ghcr.io/minsu-zip/gold-wrestling-be:<git short SHA> --push .`

| 항목 | 값 |
|---|---|
| 이미지 이름 | `ghcr.io/minsu-zip/gold-wrestling-be` (`deploy/compose.prod.yml`의 `APP_IMAGE` 기본값과 문자열 일치 확인) |
| 태그 | `latest`, `50d279c` (두 태그가 **같은** 매니페스트 리스트를 가리킨다) |
| `latest`가 가리키는 커밋 | `50d279c` (브랜치 `feature/phase-07b-server-ops`, 7a 머지 직후 + 07-04 산출물 포함) |
| 매니페스트 리스트(OCI image index) 다이제스트 | `sha256:c83a16f27ddc4781fc633268d9a540222313a0c332c50a5858bb22bb4f5fc35c` |
| `linux/amd64` 매니페스트 다이제스트 | `sha256:d3aba5da03987e0b0123927c0a198147399b9e6cd8de70909e383048dfeac29d` |
| `linux/arm64` 매니페스트 다이제스트 | `sha256:9fb0b584f9f032dc6cd6669f9b2b4e338fc7eb55bfffcf16024462553e5f9305` |
| 플랫폼별 압축 전송 크기(레이어 합, `docker manifest inspect` 기준) | amd64 **약 164.5MB** / arm64 **약 162.8MB** (11 레이어) |
| 로컬 전개 크기(`docker image ls`, arm64) | 563MB (§1의 레이어드 이미지와 동일 — push로 바이트가 늘지 않는다) |
| 코드 변경 시 재전송 레이어(application) | 606kB (§1 실측과 동일한 레이어 구조가 GHCR에도 그대로 올라갔음을 `docker history`로 확인) |
| push 소요 시간 | **43초** (07-02 로컬 빌드의 BuildKit 캐시가 살아 있어 컴파일 없이 레이어 업로드만 발생. 캐시 없는 최초 빌드는 §2의 약 120초가 기준) |
| 빌더 컴파일 실행 횟수 | 1회 (`--progress=plain` 로그에서 `gradlew bootJar` 라인 1건 — §2 결과 재현) |

**매니페스트 리스트 구성:** 인덱스 하나 아래에 `linux/amd64`·`linux/arm64` 두 이미지 매니페스트가 들어 있다(INFRA-02의 "매니페스트 1개"). 그 외에 `unknown/unknown` 플랫폼으로 표시되는 항목 2개가 더 보이는데, 이는 buildx가 기본으로 붙이는 **provenance 증명(attestation) 매니페스트**이지 실행 가능한 이미지가 아니다 — `docker pull`은 이를 무시하고 호스트 아키텍처에 맞는 이미지 매니페스트만 받는다. 이 증명에는 빌드 시각·플랫폼·베이스 이미지 다이제스트 같은 빌드 출처 정보만 들어 있고 소스코드나 env 값은 없다.

**public 전환 직전 시크릿 부재 재확인(D-175의 전제, T-07-05):**

| 확인 항목 | 결과 |
|---|---|
| `docker run --rm --entrypoint sh <image> -c 'ls -a /application'` | `application.jar`, `lib`만 존재 — `.env` 0건 |
| `find / -xdev -name ".env" -o -name ".env.*" -o -name "*.pem" -o -name "id_rsa*"` (이미지 전체) | 시스템 CA 번들(`/usr/lib/ssl/cert.pem`) 외 0건 |
| `docker history --no-trunc`의 `ENV` 지시어 | `JAVA_VERSION`·`LANG`·`PATH`·`JAVA_HOME` 4건(모두 베이스 이미지 것) — 시크릿 값 0건 |
| 컨테이너 런타임 `env` 출력 | 위 4건 + `HOME=/home/app`·`HOSTNAME`뿐 |
| 실행 사용자 `id -u` | `999` (비루트 `app`, 07-02와 동일) |

**public 전환·무인증 pull 확인:** 07-05 Task 3에서 기록한다(아래에 이어 붙인다).

---

*Phase: 07-container-server-setup · Plan: 07-02 생성(2026-09-08) · Plan: 07-05 §3 추가(2026-09-09)*
*이어서 채울 문서: Phase 10(VERIFY-02)이 k6 부하테스트 결과 섹션을 이 문서에 추가한다.*
