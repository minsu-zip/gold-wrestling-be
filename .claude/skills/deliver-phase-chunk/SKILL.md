---
name: deliver-phase-chunk
description: "phase 실행을 리뷰 가능한 청크로 끊어 브랜치·커밋·PR까지 자동으로 내보내는 절차. /gsd-execute-phase 로 phase를 실행하거나 '이 phase 어떻게 나눌까'를 판단할 때 항상 이 절차를 따른다."
---

# phase 청크 단위 납품 (D-084)

phase 하나를 PR 하나로 내지 않는다. **리뷰 가능한 크기의 청크**로 끊어
청크마다 브랜치 → 커밋 → PR → dev 머지를 완주한 뒤 다음 청크로 간다.

## 왜 (한 번만 읽으면 되는 근거)

| PR | 규모 | 결과 |
|---|---|---|
| #2 Phase 2 (15 플랜) | +17,857 | 리뷰봇·사람 모두 실질 리뷰 불가 |
| #5 Phase 3 (11 플랜) | +11,682 | 위와 동일 |
| #7 quick task | +1,368 | 인라인 리뷰 1건이 실제로 잡힘 |

리뷰가 실제로 작동한 유일한 크기는 1~2천 줄이었다. **"봤다고 치고 머지"를 구조적으로 막는 것**이 이 절차의 목적이다.

## 0. 시작 전 (phase당 1회)

```bash
git symbolic-ref --short refs/remotes/origin/HEAD   # → origin/dev 여야 한다
```

`origin/main`이 나오면 **멈추고** `git remote set-head origin dev`로 교정한다.
GSD `execute-phase`는 브랜치를 `origin/HEAD`에서 따는데, 이 레포의 GitHub 기본 브랜치는
`main`이고 main은 dev보다 수백 커밋 뒤처져 있다 — 교정하지 않으면 Phase 2·3 코드가 없는
브랜치에서 작업이 시작된다.

`.planning/config.json`의 `git.branching_strategy`는 `"none"`이다 (D-084).
GSD가 phase당 브랜치를 하나 만들면 청크 분할이 불가능하므로, **브랜치는 이 절차가 소유한다.**

## 1. 청크 경계 정하기

`/gsd-plan-phase` 결과를 **본 뒤에** 정한다. 로드맵 텍스트만 보고 미리 확정하지 않는다.
이때 플랜 목록뿐 아니라 **wave 배치를 반드시 함께 확인한다** — 아래 제약 때문이다.

### 제약: 청크는 wave 경계에서만 끊을 수 있다

GSD는 "청크"를 모른다. 끊을 수 있는 유일한 장치는 `/gsd-execute-phase {N} --wave {M}` 이고,
이건 wave 하나만 실행한 뒤 멈춘다(phase-level 검증·완료 처리는 건너뛴다).
**플랜 하나만 따로 실행하는 명령은 없다.** 따라서:

- 청크 = wave 1개 **또는 연속된 여러 wave**
- wave 중간을 가르는 청크는 **불가능**
- wave 하나가 이미 너무 크면(플랜 8개·1만 줄급) 청크로 쪼갤 방법이 없다 →
  **plan 단계로 돌아가 플랜 의존 관계를 다시 잡는다.** 이 판단은 execute를 시작하기 전에 해야 한다

`--wave`는 1부터 순서대로만 의미가 있다. 앞 wave를 건너뛰고 뒤 wave를 먼저 돌리지 않는다
(뒤 wave는 앞 wave 산출물을 전제한다).

**목표 크기: PR당 3,000~7,000줄.** 넘으면 쪼개고, 1,000줄 미만이면 앞뒤와 합친다.

### 반드시 한 청크에 묶어야 하는 것

- **불변식과 그 방어 코드** — 예: "예약 생성"과 "정원 초과 방지(동시성)"를 나누면,
  *초과 예약이 가능한 코드*가 dev에 남는다. 배포되지 않더라도 다음 청크가 그 전제를 밟는다
- **스키마와 그 스키마를 쓰는 첫 코드** — 마이그레이션만 먼저 머지하면 쓰이지 않는 컬럼이 남고,
  되돌리려면 새 마이그레이션이 필요하다 (커밋된 마이그레이션은 수정 불가)
- **엔드포인트와 그 openapi 재생성** — FE 계약이 코드와 갈라진 상태를 dev에 남기지 않는다

### 나눠도 되는 것

- 조회 API ↔ 변경 API
- 회원용 기능 ↔ 관리자용 기능
- 본체 ↔ 부가 스키마(알림 등)

### 청크 이름

`feature/phase-{N}{a|b|c...}-{slug}` — 예: `feature/phase-04a-schedule`,
`feature/phase-04b-reservation`, `feature/phase-04c-admin-ops`

## 2. 청크 실행

```bash
git switch dev && git pull --ff-only
git switch -c feature/phase-04a-schedule    # 반드시 최신 dev에서
```

그다음 이 청크에 속한 wave를 **하나씩 순서대로** 실행한다:

```bash
/gsd-execute-phase 4 --wave 1     # 청크가 wave 1~2면
/gsd-execute-phase 4 --wave 2     # 이어서 2까지 돌리고 멈춘다
```

`--wave` 없이 `/gsd-execute-phase 4`를 치면 **남은 플랜을 전부 실행해** 청크 분할이 무너진다.
청크 진행 중에는 반드시 `--wave`를 붙인다.

커밋은 GSD executor가 플랜·task 단위로 자동 생성한다(Conventional Commits).
`.planning/` 산출물도 같은 브랜치에 함께 커밋된다.

## 3. 청크 완료 → PR (자동)

**사용자 승인을 기다리지 않는다** — CLAUDE.md 커밋 규칙의 명시적 예외(D-084).
청크의 마지막 플랜이 끝나면 바로:

1. `./gradlew ktlintFormat` → `./gradlew build` 그린 확인
2. API 변경이 있었으면 `./gradlew generateApiDocs` (docker compose Postgres 필요)
3. `create-pr` 스킬 절차를 그대로 따라 PR 생성 (base: **dev**)
   - PR 제목에 청크를 명시: `feat: Phase 4a 시간표·세션 — {핵심 3가지}`
   - 본문 `## 개요`에 **"Phase 4 중 {n}/{총} 청크"**와 남은 청크 목록을 적는다
     — 리뷰어가 "왜 이것만 있지?"를 묻지 않게
4. PR URL을 사용자에게 보고한다

**머지 버튼은 절대 누르지 않는다.** 리뷰봇 결과가 도착하면 Critical/Warning을 확인하고
필요하면 같은 브랜치에 수정 커밋을 올린다.

## 4. 다음 청크

사용자가 머지한 뒤:

```bash
git switch dev && git pull --ff-only
git branch -d feature/phase-04a-schedule
git push origin --delete feature/phase-04a-schedule
```

그다음 1번으로 돌아간다.

**청크는 반드시 하나씩 순차로 진행한다.** 두 청크를 병렬로 열면 Flyway 버전이 충돌한다
(둘 다 V6를 만든다). 커밋된 마이그레이션은 수정할 수 없으므로 이 충돌은 새 버전을 추가해야만
풀리고, 이력이 지저분해진다.

## 5. phase 전체 종료

모든 청크가 dev에 머지되면 `/gsd-verify-work`·`/gsd-audit-uat` 등으로 phase 목표 달성을 확인한다.
**dev → main PR은 마일스톤/배포 시점에만** 낸다 (청크마다 내지 않는다).

## 하지 말 것

- ❌ 청크 경계를 플랜 개수로 기계적으로 자르기 (예: "3플랜씩") — 불변식이 두 PR로 쪼개진다
- ❌ 리뷰봇 결과를 안 보고 다음 청크 시작 — 같은 실수가 다음 청크에 복제된다
- ❌ dev·main에 직접 커밋
- ❌ PR 자동 머지
- ❌ 청크를 병렬로 실행
