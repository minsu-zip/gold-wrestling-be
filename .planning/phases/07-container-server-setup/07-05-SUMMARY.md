---
phase: 07-container-server-setup
plan: 05
subsystem: infra
tags: [ghcr, buildx, multi-arch, oci-image-index, package-visibility, registry-auth]

# Dependency graph
requires:
  - phase: 07-container-server-setup (plan 02)
    provides: "레포 루트 Dockerfile(D-176 application.jar 고정 반영), gw-builder buildx 빌더"
  - phase: 07-container-server-setup (plan 03)
    provides: "deploy/compose.prod.yml의 APP_IMAGE 기본값(ghcr.io/minsu-zip/gold-wrestling-be:latest)"
provides:
  - "GHCR에 게시된 amd64+arm64 OCI 이미지 인덱스 1개 — ghcr.io/minsu-zip/gold-wrestling-be:latest 및 :50d279c (다이제스트 sha256:c83a16f2…)"
  - "패키지 public 전환 + 무인증 manifest inspect·pull 실증 (서버 docker login 불필요, D-175 실현)"
  - "docs/metrics.md §3 — 게시 태그·다이제스트·플랫폼별 압축 크기·push 소요·시크릿 부재 재확인·무인증 pull 확인"
affects: [07-06, 08-deploy-pipeline]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "buildx --push 멀티태그(latest + git short SHA)로 같은 인덱스를 두 태그가 가리키게 해 롤백 대상을 지목 가능하게 한다"
    - "public 전환 직전에 이미지 파일시스템·history ENV·런타임 env 3중으로 시크릿 부재를 실증한다"
    - "무인증 검증은 docker logout → config.json auths 엔트리 부재 확인 → manifest inspect → 로컬 삭제 후 pull 순서"

key-files:
  created: []
  modified:
    - docs/metrics.md

key-decisions:
  - "SHA 태그는 현재 브랜치 HEAD(50d279c)를 썼다 — dev 머지 후 SHA가 달라지므로 07-06 롤백 예시는 이 태그를 그대로 쓰되, Phase 8부터는 워크플로가 머지 커밋 SHA로 태깅한다"
  - "buildx 기본 provenance attestation(unknown/unknown 매니페스트 2개)은 제거하지 않았다 — 실행 이미지가 아니고 pull에 영향이 없으며 소스·env 값을 담지 않음을 확인해 문서에 설명만 남겼다"

patterns-established:
  - "PAT는 대화형 docker login(비밀번호 프롬프트)으로 입력해 셸 히스토리·파일·로그 어디에도 남기지 않는다. 플랜의 export+공백 방식은 zsh HIST_IGNORE_SPACE 기본 꺼짐이라 권하지 않는다"

requirements-completed: [INFRA-02]

# Metrics
duration: 약 25분 (사람 개입 대기 제외)
completed: 2026-09-09
---

# Phase 7 Plan 05: GHCR 멀티 아키텍처 이미지 게시 Summary

**7a Dockerfile로 amd64+arm64 OCI 이미지 인덱스 1개를 GHCR에 실제로 push하고(`latest`·`50d279c` 두 태그, 43초), 패키지를 public으로 전환한 뒤 `docker logout` 상태에서 `manifest inspect`·`pull`이 성공함을 실증해 서버에 장기 PAT를 두지 않아도 된다는 D-175를 현실로 만들었다. 레포 가시성은 PRIVATE 그대로다.**

## Performance

- **Duration:** 약 25분 (GHCR 로그인·public 전환 사람 대기 제외)
- **Completed:** 2026-09-09T01:10Z push, 이후 public 전환 확인
- **Tasks:** 3/3 완료 (Task 1·3은 human-action 체크포인트)
- **Files modified:** 1 (`docs/metrics.md`)

## Accomplishments

- **Task 1 (사람):** 소유자가 write:packages PAT로 `docker login ghcr.io` 수행. 실행자는 `~/.docker/config.json`의 `auths`에 `ghcr.io` 엔트리 존재 여부만 확인했고 토큰 값은 어떤 경로로도 다루지 않았다.
- **Task 2:** `docker buildx build --builder gw-builder --platform linux/amd64,linux/arm64 -t …:latest -t …:50d279c --push .` 성공(43초, 07-02 BuildKit 캐시 적중으로 컴파일 없이 업로드만). `imagetools inspect`로 인덱스 `sha256:c83a16f27ddc4781fc633268d9a540222313a0c332c50a5858bb22bb4f5fc35c` 아래 `linux/amd64`(`sha256:d3aba5da…`)·`linux/arm64`(`sha256:9fb0b584…`) 두 매니페스트 확인 — INFRA-02 "매니페스트 1개" 레지스트리 실증. 이미지 이름이 `deploy/compose.prod.yml`의 `APP_IMAGE` 기본값과 문자열 일치. 플랫폼별 압축 전송 크기 amd64 약 164.5MB / arm64 약 162.8MB.
- **public 전환 직전 시크릿 부재 3중 재확인:** `/application`에 `application.jar`·`lib`만(`.env` 0건), 이미지 전체 `find`에서 `.env`/`.pem`/`id_rsa` 0건, `docker history` ENV는 베이스 이미지의 JAVA_HOME·PATH 등 4건뿐, 런타임 `env`도 동일, `id -u`=999.
- **Task 3 (사람 + 실증):** 소유자가 GitHub UI에서 패키지만 Public으로 전환. 실행자가 `docker logout ghcr.io` → auths 엔트리 부재 확인 → 무인증 `manifest inspect` 성공 → 로컬 이미지 삭제 후 무인증 `pull` 성공(받은 다이제스트가 인덱스와 일치) → 익명 pull 토큰 발급 확인 → `gh repo view --json visibility`가 `PRIVATE`. 전부 `docs/metrics.md` §3에 표로 기록.

## Task Commits

1. **Task 1: GHCR 로그인** — 사람 작업, 커밋 없음
2. **Task 2: 멀티 아키텍처 push + 매니페스트 검증 + metrics.md §3** - `6ba800b` (docs)
3. **Task 3: public 전환 + 무인증 pull 실증 기록** - `81a2d36` (docs)

_TDD 아님 — 레지스트리 조작 + 문서 플랜. 프로덕션 코드 변경 0건._

## Files Created/Modified

- `docs/metrics.md` - §3 "GHCR 게시 결과" 신설: 태그·다이제스트·플랫폼별 크기·push 소요·attestation 설명·시크릿 부재 재확인표·무인증 pull 확인표

## Decisions Made

- SHA 태그를 feature 브랜치 HEAD(`50d279c`)로 붙였다. 07-06은 이 태그를 롤백 예시로 쓸 수 있고, dev 머지 후 SHA가 달라지는 문제는 Phase 8(DEPLOY-02)이 워크플로에서 머지 커밋 기준으로 태깅하면서 해소된다.
- buildx 기본 provenance attestation 매니페스트 2개(`unknown/unknown`)는 그대로 두었다. pull에 영향이 없고 빌드 출처 메타데이터만 담는다. 끄려면 `--provenance=false`인데, 후속 SBOM·서명 도입 여지를 남기는 쪽이 낫다고 판단했다.

## Deviations from Plan

**1. [Rule 2 - 절차 정정] PAT 입력 방식을 export+공백 방식에서 대화형 `docker login` 프롬프트로 바꿔 안내**
- 플랜의 `echo $CR_PAT | docker login --password-stdin` + "export 앞 공백" 방식은 zsh `HIST_IGNORE_SPACE` 옵션이 켜져 있어야만 히스토리에 안 남는데 macOS zsh 기본값은 꺼져 있다. `docker login ghcr.io -u minsu-zip`로 비밀번호 프롬프트에 직접 입력하면 명령줄 자체에 토큰이 등장하지 않아 더 안전하다. 소유자는 이 방식으로 로그인했다.

**2. [Rule 3 - 실행 형태] 체크포인트가 플랜 시작·끝에 있어 executor 서브에이전트 대신 오케스트레이터가 인라인 실행**
- Task 1이 첫 태스크라 서브에이전트를 띄워도 즉시 체크포인트를 반환할 뿐이었고, 사람 개입 2회 사이의 Task 2는 push 명령 1개 + 검증이라 오케스트레이터가 직접 실행하고 문서화했다. 커밋·SUMMARY·상태 갱신 규약은 동일하게 지켰다.

## Issues Encountered

- `gh api /user/packages/...`로 패키지 가시성을 API로 읽으려 했으나 현재 gh 토큰에 `read:packages` scope가 없어 403. 대신 익명 토큰 발급(`ghcr.io/token?scope=repository:...:pull`)과 무인증 pull로 public 상태를 실증했다 — 결과적으로 플랜이 요구한 검증(무인증 pull)이 더 직접적이다.

## 테스트 미작성 파일과 그 이유 (§10.0 면제)

- 이 플랜은 프로덕션 코드 변경이 없다(레지스트리 조작 + `docs/metrics.md` 문서). conventions §10.0 면제 대상이라 JUnit 테스트를 만들지 않았다. 검증은 `imagetools inspect`·`docker run`·`docker history`·`docker pull` 실행으로 갈음했다.

## 이번에 쓴 기술

1. **★ OCI 이미지 인덱스(매니페스트 리스트) — 태그 하나가 아키텍처별 이미지 여러 개를 가리키는 구조**
   - **이 코드에서 왜 필요했는가:** 서버 EC2는 amd64, 소유자 Mac은 arm64다. 태그 `latest` 하나를 pull하면 각자 자기 CPU에 맞는 이미지를 받아야 한다. `--platform linux/amd64,linux/arm64 --push`는 이미지 두 개를 따로 올리는 게 아니라, 두 매니페스트를 담은 **인덱스 하나**(`sha256:c83a16f2…`)를 만들고 태그가 그 인덱스를 가리키게 한다. `docker pull`은 인덱스를 읽고 호스트 아키텍처에 맞는 항목만 내려받는다.
   - **안 썼으면 뭐가 깨지는가:** arm64 Mac에서 `--load`로 빌드한 이미지를 그대로 push하면 amd64 서버가 pull 시 `exec format error`로 기동 자체가 실패한다. 반대로 태그를 `latest-amd64`/`latest-arm64`로 나누면 compose의 `APP_IMAGE` 기본값이 호스트마다 달라져 로컬 검증(07-03)과 운영이 같은 compose 파일을 쓸 수 없게 된다.

2. **★ 레지스트리 인증과 패키지 가시성의 분리 — 로컬 push는 PAT, 서버 pull은 무인증**
   - **이 코드에서 왜 필요했는가:** 이미지를 **올리는** 쪽(내 Mac)은 "minsu-zip 계정 소유자"임을 증명해야 하므로 write:packages PAT로 `docker login`이 필요하다. 반면 **받는** 쪽(EC2)은 패키지가 public이면 인증이 필요 없다. 이 둘을 분리하면 서버에는 토큰 파일이 하나도 없어도 되고, 서버가 털려도 GHCR 쓰기 권한은 유출되지 않는다.
   - **안 썼으면 뭐가 깨지는가:** 패키지를 private으로 두면 서버 `.env`나 `~/.docker/config.json`에 PAT를 상주시켜야 한다. 그 토큰은 만료 전까지 이미지를 **덮어쓸 수도** 있어(write 권한), 서버 침해 시 공격자가 악성 이미지를 push하고 다음 배포에서 서버가 그걸 pull하는 공급망 공격 경로가 생긴다. 이번에 `docker logout` 후 pull 성공으로 이 경로가 실제로 닫혔음을 확인했다.

3. **public 전환 직전 시크릿 부재 3중 확인 — 파일시스템 · 이미지 history ENV · 런타임 env**
   - **이 코드에서 왜 필요했는가:** public 이미지는 누구나 `docker pull` 후 안을 들여다볼 수 있다. 시크릿이 이미지에 들어가는 경로는 세 가지다: (1) `COPY . .`로 `.env` 파일이 딸려 들어감, (2) Dockerfile `ENV`/`ARG`로 값이 레이어 메타데이터에 박힘, (3) 베이스 이미지가 뭔가를 심어 둠. 각각 `ls -a /application`+`find`, `docker history --no-trunc`, `docker run --entrypoint env`로 따로 확인해야 셋 다 막힌 걸 알 수 있다.
   - **안 썼으면 뭐가 깨지는가:** `.dockerignore`에 `.env`가 있다는 사실만 믿고 넘어갔다면, 예컨대 나중에 누가 `ARG JWT_SECRET`을 Dockerfile에 넣는 순간 그 값이 `docker history`에 평문으로 남는데도 아무도 모른 채 public에 올라간다. 이번 검증은 "지금 없다"뿐 아니라 "무엇을 봐야 하는가"를 문서(`metrics.md` §3 표)로 남겨 Phase 8 워크플로가 같은 검사를 자동화할 수 있게 했다.

4. **다이제스트 고정 태그(`:50d279c`) — 움직이는 `latest`와 고정된 SHA 태그를 같은 인덱스에 동시에 붙이기**
   - **이 코드에서 왜 필요했는가:** `latest`는 다음 push 때 다른 이미지를 가리키게 되는 "움직이는" 태그다. 배포 후 문제가 생겨 되돌리려면 "이전 이미지"를 이름으로 부를 수 있어야 하는데, `latest`만 있으면 그게 불가능하다. 커밋 SHA 태그를 같이 붙여 두면 `APP_IMAGE=…:50d279c`로 compose를 띄워 정확히 그 커밋의 이미지로 롤백할 수 있다.
   - **안 썼으면 뭐가 깨지는가:** 07-06 runbook 11단계(롤백)가 "이전 태그가 없어 롤백 불가"로 끝난다. Phase 8이 SHA 태깅을 자동화하기 전에 그 방식이 실제로 동작하는지(두 태그가 같은 다이제스트를 가리키는지)를 이번에 `imagetools inspect`로 확인해 뒀다.

5. **일부러 안 쓴 것 — `--provenance=false`로 attestation 매니페스트 제거**
   - `imagetools inspect`에 `unknown/unknown` 항목 2개가 보여 "플랫폼이 잘못 들어갔나" 싶을 수 있지만, 이는 buildx가 기본으로 붙이는 빌드 출처 증명이다. pull에 영향이 없고 소스·env를 담지 않음을 확인했으므로 끄지 않았다. 나중에 이미지 서명·SBOM(Out of Scope지만 v1.2 후보)을 붙일 때 같은 자리를 쓴다.

## User Setup Required

- **완료됨:** Docker Desktop 실행, write:packages PAT로 `docker login ghcr.io`, GitHub UI에서 패키지 Public 전환 — 셋 다 소유자가 직접 수행했고 PAT 값은 어디에도 기록되지 않았다.
- **권장:** Phase 7이 끝나면 이 PAT를 GitHub에서 폐기한다. Phase 8은 GitHub Actions `GITHUB_TOKEN`으로 push하므로 로컬 PAT는 더 이상 필요 없다.

## Next Phase Readiness

- (1) 게시 인덱스 다이제스트 `sha256:c83a16f27ddc4781fc633268d9a540222313a0c332c50a5858bb22bb4f5fc35c`, `latest` = 커밋 `50d279c`.
- (2) 무인증 pull: `docker logout ghcr.io` 상태에서 `manifest inspect`·`pull` 모두 성공, 레포는 PRIVATE 유지.
- (3) §10.0 면제: 프로덕션 코드 변경 없음(레지스트리 조작 + 문서).
- 07-06(실 EC2 수동 배포)은 이 이미지를 `docker compose pull`로 받으면 된다 — 서버 `docker login` 단계가 runbook에 없는 것이 정상이다.
- 블로커 없음.

---
*Phase: 07-container-server-setup*
*Completed: 2026-09-09*

## Self-Check: PASSED

- `docs/metrics.md` §3 존재, 이 SUMMARY 파일 존재
- 커밋 2건(`6ba800b`, `81a2d36`) `git log`에서 확인
- `docs/metrics.md`·이 파일에 GitHub 토큰 접두 패턴 0건 (grep으로 확인)
