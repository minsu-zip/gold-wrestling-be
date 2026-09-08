# 운영 실측 지표 (metrics.md)

> 이 문서는 이미지 빌드·배포 아티팩트 관련 **실측 수치**의 정본이다. Phase 7이 최초 생성했고
> Phase 10(VERIFY-02, k6 부하테스트)이 이어서 항목을 추가한다. 값 자체의 "왜 이 선택인가"는
> `docs/decisions.md`를 본다 — 이 문서는 숫자와 그 숫자가 의미하는 것만 다룬다.
> 서버 실측(메모리 RSS·`docker pull` 실전 소요 시간)은 이 문서가 아니라 `docs/operations.md`의
> 메모리 예산표가 담당한다(Phase 7 07-06, 수동 배포 시 작성 예정).

## 1. 이미지 빌드 전략 절감 수치 (D-14)

**측정 일자:** 2026-09-08
**측정 환경:** 로컬 Mac (Apple Silicon, `linux/arm64` 네이티브), Docker Desktop 28.5.1, buildx v0.29.1-desktop.1, BuildKit v0.25.1(단일 플랫폼 로컬 빌드는 `default`/`desktop-linux` 빌더 사용)
**측정 방법:** 동일 소스(`build.gradle.kts`·`src/`)에서 두 Dockerfile을 각각 빌드해 비교했다.
- **레이어드**: 레포 루트 `Dockerfile` (D-13, Boot `tools` jarmode `extract --layers`로 dependencies/spring-boot-loader/snapshot-dependencies/application 4개 레이어 분리)
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

---

*Phase: 07-container-server-setup · Plan: 07-02 · 생성: 2026-09-08*
*이어서 채울 문서: Phase 10(VERIFY-02)이 k6 부하테스트 결과 섹션을 이 문서에 추가한다.*
