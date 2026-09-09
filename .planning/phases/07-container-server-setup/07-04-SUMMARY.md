---
phase: 07-container-server-setup
plan: 04
subsystem: infra
tags: [bash, docker, docs, server-provisioning, operations-runbook]

# Dependency graph
requires:
  - phase: 07-container-server-setup (plan 01)
    provides: "운영 env 키 계약(SWAGGER_ENABLED 등), D-169~D-175 결정 로그"
  - phase: 07-container-server-setup (plan 03)
    provides: "deploy/compose.prod.yml·deploy/Caddyfile(D-176/D-177 반영본), 로컬 실기동 메모리 실측값(app 576716800B/postgres 314572800B/caddy 52428800B)"
provides:
  - "deploy/server-setup.sh — 멱등 서버 초기 세팅 스크립트(Docker+compose·스왑 2GB·타임존·배포 디렉토리, INFRA-06)"
  - "docs/operations.md — 운영 env 키 표(29개 전수)·메모리 예산표(실측 열 비움)·서버 세팅 절차·11단계 수동 배포 runbook(INFRA-05/06/07, D-04)"
  - "README.md의 docs/operations.md·docs/metrics.md 링크"
affects: [07-06, 08-deploy-pipeline, 09-ops-safety]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "블록별 독립 멱등 가드(command -v/swapon --show/grep -q .env/etc/fstab/[ -f ])로 재실행 안전성 확보"
    - "메모리 예산표는 실측 열을 '(미측정 — 07-06 실서버 배포에서 기입)'으로 명시적으로 비워 두고 로컬 참고값은 별도 표기해 혼동 방지"

key-files:
  created:
    - deploy/server-setup.sh
    - docs/operations.md
  modified:
    - README.md

key-decisions:
  - "docs/operations.md의 env 키 표는 .env.example 29개 키 전수를 grep 루프로 대조해 1:1 동기화를 강제한다"
  - "DOMAIN·ACME_EMAIL은 REQUIREMENTS.md INFRA-07이 명시한 KAKAO_REDIRECT_URI/CORS_ALLOWED_ORIGINS와 달리 실도메인이 아니므로 <your-domain>/<your-email> 플레이스홀더로 남긴다"

patterns-established:
  - "서버 세팅 스크립트 종료 요약은 실행 시각처럼 매번 달라지는 값을 배제해, 2회 실행 결과를 그대로 diff 비교할 수 있게 한다"

requirements-completed: [INFRA-05, INFRA-06, INFRA-07]

# Metrics
duration: 약 40분
completed: 2026-09-09
---

# Phase 7 Plan 04: 서버 초기 세팅 스크립트 + 운영 문서(operations.md) Summary

**빈 Ubuntu 24.04 EC2를 멱등하게 배포 가능 상태로 만드는 `deploy/server-setup.sh`와, `.env.example` 29개 키·메모리 예산표(실측 열은 07-06을 위해 비움)·11단계 수동 배포 runbook을 담은 `docs/operations.md`를 신설했다.**

## Performance

- **Duration:** 약 40분
- **Completed:** 2026-09-09T00:45:56Z (09:45 KST)
- **Tasks:** 3/3 완료
- **Files modified:** 3 (신설 2, 수정 1)

## Accomplishments

- `deploy/server-setup.sh` 신설 — Docker Engine+compose 플러그인(공식 apt 저장소, 코드네임 동적 조회) / 스왑 2GB(파일 생성 가드 + fstab 등록 가드 분리) / 타임존 Asia/Seoul / docker 그룹 가입 / `/opt/gold-wrestling/backups` + `.env` 자리(없을 때만 생성) 5블록. `bash -n` 통과, 실행 권한 부여(100755), 시크릿 0건, 서버에서 git 미사용(D-18) 확인.
- `docs/operations.md` 신설 — §1 운영 env 키 표(`.env.example` 29개 키 전수, grep 루프로 1:1 동기화 검증), §2 메모리 예산표(app 550M/postgres 300M/caddy 50M, JVM·postgres 파라미터 근거, 실측 열은 07-06을 위해 비움, 로컬 참고값 별도 표기), §3 서버 세팅 절차(재로그인 필요 이유·서버 디렉토리 구조·scp 전달), §4 11단계 수동 배포 runbook(사전확인→롤백, 단계마다 실패 시 대응).
- `README.md`에 `docs/operations.md`·`docs/metrics.md` 링크 1줄씩 추가 — 운영 절차 본문은 복제하지 않음(`grep -c 'ssh ubuntu@' README.md` = 0).

## Task Commits

Each task was committed atomically:

1. **Task 1: deploy/server-setup.sh 신설 (멱등 서버 초기 세팅)** - `9c2fb4d` (feat)
2. **Task 2: docs/operations.md 신설 — 운영 env 키 표·메모리 예산표·서버 세팅 절차** - `ff17fab` (docs)
3. **Task 3: docs/operations.md 수동 배포 runbook 섹션 + README 링크** - `846eeee` (docs)

_TDD 아님 — 이 플랜의 산출물(bash 스크립트·마크다운 문서)은 conventions §10.0 면제 대상. 아래 "테스트 미작성" 절 참조._

## Files Created/Modified

- `deploy/server-setup.sh` - 멱등 서버 초기 세팅 스크립트(75줄), 5블록·5개 멱등 가드
- `docs/operations.md` - 운영 env 키 표·메모리 예산표·서버 세팅·수동 배포 runbook(197줄, `## ` 헤더 4개)
- `README.md` - 운영 문서·메트릭 문서 링크 2줄 추가

## Decisions Made

- **DOMAIN·ACME_EMAIL은 실값을 쓰지 않는다:** hard constraint가 REQUIREMENTS.md INFRA-07이 명시한 두 키(`KAKAO_REDIRECT_URI`=`https://app.goldwrestling.com/login/callback`, `CORS_ALLOWED_ORIGINS`=`https://app.goldwrestling.com`)만 실도메인 인용을 허용하므로, PROJECT.md에 이미 나온 `api.goldwrestling.com`이 있었지만 `DOMAIN`·`ACME_EMAIL`은 `<your-domain>`·`<your-email>` 플레이스홀더로 통일했다.
- **메모리 예산표 실측 열은 문자 그대로 비운다:** hard constraint에 따라 07-03의 로컬 실측값(576716800B 등)을 실측 RSS 칸에 채우지 않고 "로컬 참고값(07-03 실측)"이라는 별도 문구로 비고 열에만 넣었다 — 07-06 실서버 배포가 채울 칸과 혼동되지 않게 하기 위함이다.
- **07-CONTEXT 로컬 D-번호(D-06/D-07/D-11 등)는 그대로 인용하지 않는다:** MEMORY.md 지침(decisions.md는 BE·FE 공유 로그, D-번호를 플랜에 하드코딩하지 않는다)에 따라, `docs/operations.md`에는 실제 `docs/decisions.md`에 기록된 전역 번호(D-170·D-171·D-173·D-177 등)만 인용했다. 컨테이너별 메모리 limit(550M/300M/50M) 자체처럼 전역 D-번호로 별도 기록되지 않은 값은 D-번호 없이 `deploy/compose.prod.yml` 실제 값을 그대로 옮기는 방식으로 서술했다.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] server-setup.sh의 자기 지시적 grep 오탐 — "git clone/init/pull 없음" 주석이 acceptance 검사에 스스로 걸림**
- **Found during:** Task 1 acceptance criteria 검증(`grep -cE '(^|[^a-z])git (clone|init|pull)' deploy/server-setup.sh`가 0이 아닌 1을 반환)
- **Issue:** "서버에서 git을 쓰지 않는다"는 설명 주석에 `git clone/init/pull`이라는 명령어 이름을 문자 그대로 인용해, D-18 준수를 검증하는 grep이 실제 사용이 아니라 이 주석 자체를 매치했다. 07-02 SUMMARY의 CDS 플래그 자기지시적 매치와 같은 유형의 실수.
- **Fix:** 주석 문구를 "서버에서 버전관리 명령을 쓰지 않는다"로 바꿔 grep 패턴과 겹치는 리터럴을 제거했다. 실제 동작(git 미사용)은 변경 없음.
- **Files modified:** `deploy/server-setup.sh`
- **Verification:** 재검증 후 grep 결과 0 확인
- **Committed in:** `9c2fb4d` (Task 1 커밋에 이미 반영, 별도 커밋 없음)

---

**Total deviations:** 1 auto-fixed (bug — 검증 스크립트와 설명 주석의 문자열 충돌)
**Impact on plan:** 스크립트 동작·D-18 준수에는 영향 없음. 문서화 문구만 조정.

## Issues Encountered

None.

## 테스트 미작성 파일과 그 이유 (§10.0 면제)

- `deploy/server-setup.sh` — bash 스크립트. `docs/conventions.md` §10.0 면제 목록(docker-compose류 인프라 아티팩트와 동일 성격)이라 JUnit 테스트를 만들지 않았다. 동작 검증은 이 플랜의 `<acceptance_criteria>`에 명시된 `bash -n`·`test -x`·grep 기반 정적 검사로 전부 통과시켰다. **shellcheck는 이 Mac에 설치돼 있지 않아** `command -v shellcheck` 가드가 "shellcheck 미설치 — bash -n 으로 갈음"을 출력했다 — `bash -n`이 유일한 구문 검증이다. 2회 실행 멱등성 실증(실제 서버에서 스크립트를 두 번 돌려 결과가 같은지 확인)은 이 플랜 범위가 아니라 07-06이 한다(플랜 constraints에 명시).
- `docs/operations.md`, `README.md` — 문서, §10.0 면제.
- **키 개수 대조 결과:** `.env.example`의 `^[A-Z_]+=` 키 29개(`DB_HOST`~`APP_IMAGE`) 전부가 `docs/operations.md` §1 표의 29개 행과 1:1로 일치한다 — plan verify 블록의 grep 루프가 `env key sync OK`를 출력하고 exit 0으로 확인했다.

## 이번에 쓴 기술

1. **★ 멱등(idempotent) 셋업 스크립트 — "이미 돼 있으면 건너뛴다" 가드로 재실행을 안전하게**
   - **이 코드에서 왜 필요했는가:** 서버 세팅은 한 번에 끝나지 않는다. 네트워크가 끊겨 중간에 죽거나, 몇 달 뒤 서버를 이전하며 다시 돌린다. `command -v docker`, `swapon --show | grep`, `[ -f .env ] ||` 같은 가드가 있으면 몇 번을 돌려도 결과가 같다. 특히 `.env`는 "없을 때만 빈 파일 생성"이라 운영 시크릿이 사라지지 않는다.
   - **안 썼으면 뭐가 깨지는가:** 2회 실행 시 `fallocate`가 이미 있는 스왑 파일 위에서 실패해 `set -e`로 스크립트가 중단되거나, `touch`가 아닌 `>` 리다이렉트를 썼다면 서버 `.env`가 비워져 앱이 DB 비밀번호 없이 기동을 시도한다.

2. **스왑 생성 가드와 fstab 등록 가드의 분리**
   - **이 코드에서 왜 필요했는가:** "스왑은 지금 켜져 있는데 `/etc/fstab`에는 없다"는 상태가 실재한다(수동으로 `swapon`만 한 경우). 이 상태에서 가드가 하나뿐이면 fstab 등록을 건너뛰고, 재부팅 후 스왑이 사라진다. 1GB 서버에서 스왑 2GB는 메모리 예산(limit 합 900M)의 전제 조건이라 사라지면 OOM 위험이 직결된다.
   - **안 썼으면 뭐가 깨지는가:** 재부팅 한 번에 스왑이 없어진 채 앱이 뜨고, 트래픽이 몰릴 때 OS OOM killer가 postgres를 먼저 죽이는 시나리오가 열린다.

3. **APT 저장소 서명 검증(`signed-by=` keyring) — 공식 절차를 원문 그대로**
   - **이 코드에서 왜 필요했는가:** Ubuntu 기본 저장소의 docker 패키지는 오래됐고 compose v2 플러그인이 없다. Docker 공식 저장소를 추가하되, 그 저장소의 GPG 공개키를 `/etc/apt/keyrings/docker.asc`에 두고 `signed-by=`로 묶어 "이 키로 서명된 패키지만 신뢰"하게 한다. 코드네임은 `/etc/os-release`에서 읽어 Ubuntu 버전을 하드코딩하지 않았다.
   - **안 썼으면 뭐가 깨지는가:** 서명 검증 없이 저장소를 추가하면 DNS/중간자 공격으로 변조된 docker 바이너리가 서버에 설치될 수 있다 — 플랜 위협 모델 T-07-SC(공급망)가 그대로 열린다.

4. **docker 그룹 가입은 재로그인 후 적용 — 프로세스의 그룹 목록은 로그인 시점에 결정된다**
   - **이 코드에서 왜 필요했는가:** `usermod -aG docker ubuntu`는 즉시 반영되지 않는다. 리눅스는 사용자의 보조 그룹을 세션 시작 때 읽어 프로세스에 붙이므로, 같은 SSH 세션에서 `docker ps`를 치면 여전히 permission denied가 난다. 스크립트 종료 메시지에 재로그인 안내를 넣은 이유다.
   - **안 썼으면 뭐가 깨지는가:** 07-06 실행자가 "스크립트가 실패했다"고 오판해 `sudo docker`로 우회하거나, 소켓 권한을 `chmod 666`으로 열어 버리는 잘못된 수정으로 흐른다.

5. **운영 문서를 실행 가능한 runbook으로 — 단계마다 "명령 + 기대 결과 + 실패하면"**
   - **이 코드에서 왜 필요했는가:** 실서버 배포(07-06)는 사람이 손으로 한다. "health를 확인한다"가 아니라 `curl -s https://<domain>/actuator/health` → `{"status":"UP"}`처럼 적어야 즉흥 판단이 사라지고, 그 판단이 기록되지 않는 문제(플랜 Purpose)가 막힌다. Phase 8은 이 11단계를 그대로 워크플로로 옮긴다.
   - **안 썼으면 뭐가 깨지는가:** 실서버에서 "인증서 영속"을 확인한다며 `restart caddy` 대신 `down/up -v`를 돌려 볼륨을 지우고 재발급을 유발하는 식의 실수가 생긴다 — 문서에 "무엇이 보이면 성공"이 없으면 검증 자체가 검증되지 않는다.

6. **일부러 안 쓴 것 — 서버에서 `git clone`으로 파일 받기 (07-CONTEXT D-18)**
   - 서버에 git을 두면 레포 접근 토큰(private 레포)도 함께 둬야 하고, 서버 이전 시 "무엇을 복사해야 하는가"가 불명확해진다. 대신 `/opt/gold-wrestling/`에 compose·Caddyfile·`.env`·`backups/` 4종만 두고 scp/`bash -s`로 전달한다 — 서버 이전(Phase 9)이 디렉토리 복사 하나로 끝난다. 스크립트에 버전관리 명령이 0건임을 grep으로 강제했다.

## User Setup Required

None - 이 플랜은 외부 서비스 설정이 필요 없다. `deploy/server-setup.sh`는 아직 실서버에 실행되지 않았다 — 07-06이 실제 EC2에서 처음 실행하고 2회 실행 비교·`docs/operations.md` §2 실측 열 기입까지 수행한다.

## Next Phase Readiness

- `deploy/server-setup.sh`·`docs/operations.md`가 07-06(실서버 수동 배포)이 그대로 따라 실행할 절차·문서로 확정됐다. §4의 11단계는 각 단계 실패 시 대응까지 포함해, 즉흥 판단 없이 수행 가능하다.
- 메모리 예산표(§2)의 실측 열 3칸(app/postgres/caddy)은 의도적으로 비어 있다 — 07-06이 채울 자리다.
- `git status --porcelain`이 클린 상태(이 SUMMARY 커밋 전 기준 `.planning/STATE.md`의 오케스트레이터 사전 수정만 남아 있었고, 최종 메타데이터 커밋에서 함께 처리한다).
- 블로커 없음.

---
*Phase: 07-container-server-setup*
*Completed: 2026-09-09*

## Self-Check: PASSED

- 생성/수정 파일 3건 전부 존재 확인 (`deploy/server-setup.sh`, `docs/operations.md`, `README.md`)
- 커밋 3건(`9c2fb4d`, `ff17fab`, `846eeee`) 전부 `git log`에서 확인
