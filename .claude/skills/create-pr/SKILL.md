---
name: create-pr
description: "feature 브랜치 작업을 dev로 보내는 PR을 만들 때의 절차. PR 본문에 변경 요약·품질 게이트 결과와 함께 백엔드 학습 노트(기법·아키텍처 설명 + 정확한 비유)를 반드시 포함한다. PR 생성 요청이면 항상 이 절차를 따른다."
allowed-tools:
  - Read
  - Write
  - Bash
  - Grep
  - Glob
---

# PR 생성 절차 (학습 노트 포함)

이 프로젝트는 소유자의 백엔드 학습을 겸한다 (CLAUDE.md 학습 모드).
PR은 코드 리뷰 단위이자 **복습 단위**다 — 나중에 PR 목록만 훑어도
"그때 무슨 기법을 왜 썼는지"가 복원되도록 본문을 작성한다.

## 0. 시작 전 확인

- [ ] 현재 브랜치가 feature 브랜치인지 확인 (`git branch --show-current`). dev/main에서 직접 PR 금지
- [ ] 워킹트리가 깨끗한지 확인 (`git status --short`). 미커밋 변경이 있으면 사용자에게 먼저 알린다
- [ ] `./gradlew build`가 그린인지 확인 (마지막 실행 기록이 없으면 다시 돌린다)
- [ ] gh 활성 계정이 `minsu-zip`인지 확인 (`gh auth status`). 아니면 `gh auth switch --user minsu-zip`
- [ ] API 변경이 있었다면 `docs/api/openapi.yaml`이 재생성·커밋되었는지 확인 (CLAUDE.md 규칙 4)

## 1. 재료 수집

PR 본문은 추측으로 쓰지 않는다. 브랜치의 실제 산출물에서 뽑는다:

```bash
git log --oneline dev..HEAD          # 커밋 흐름 (태스크 단위 원자 커밋 = 작업 로그)
git diff --stat dev..HEAD            # 변경 규모
git diff dev..HEAD -- docs/api/openapi.yaml | head -50        # API 계약 변경 여부
git diff --name-only dev..HEAD -- src/main/resources/db/migration/   # 새 마이그레이션
git diff dev..HEAD -- .env.example                            # 새 환경변수 키
```

- `.planning/phases/*/[NN]-*-SUMMARY.md` — 각 플랜의 "이번에 쓴 기술" 섹션 (학습 노트의 1차 재료)
- `.planning/phases/*/[NN]-REVIEW.md` / `[NN]-REVIEW-FIX.md` — 리뷰 결과·수정 내역
- `.planning/phases/*/[NN]-SECURITY.md` — 보안 감사 결과
- `.planning/phases/*/[NN]-VERIFICATION.md` — 검증 점수
- `.planning/ROADMAP.md` — 이 브랜치가 커버하는 phase·요구사항 ID
- `docs/decisions.md` — 이 브랜치에서 추가·변경된 D-XXX 결정

## 2. PR 본문 구성

섹션 순서는 고정. **[조건부]** 표시 섹션은 해당 사항이 있을 때만 넣고, 없으면 섹션 자체를 뺀다
(단, "API 계약 변경"은 백엔드 레포 특성상 **변경 없음도 명시**한다 — FE가 PR만 보고 판단할 수 있게).

```markdown
## 개요
{무엇을 만들었나 — 요구사항 ID(FOUND-01 등)와 함께 표로}

## 품질 게이트 결과
{테스트 수·빌드, 검증 점수, 보안 감사, 코드 리뷰 결과 — 실측 숫자만}

## API 계약 변경 (openapi.yaml)
{추가/변경/삭제된 엔드포인트 목록. 변경 없으면 "diff 0줄 — FE 타입 재생성 불필요" 한 줄}

## DB 마이그레이션 [조건부]
{새 Flyway 버전 목록 + 각 한 줄 요약. 파괴적 변경(DROP·데이터 이동)이 있으면 굵게 경고.
 로컬 DB에 이미 적용된 경우 그 사실도 명시 — 마이그레이션은 머지 후 수정 불가, 새 버전으로만 정정}

## 환경변수 변경 [조건부]
{.env.example에 추가된 키 이름 목록(값 금지) + 배포 전 서버 환경변수 세팅 필요 여부}

## 주요 커밋 흐름
{feat/fix/docs 단위로 압축. 원자 커밋이므로 "커밋 순서 = 작업 순서"임을 활용}

## 이번에 쓴 백엔드 기술 (학습 노트)
{아래 §3 형식}

## 리뷰 가이드
{아래 §4 형식}

## GSD 추적성
{phase 번호·플랜 ID·요구사항 ID, 검증 산출물 경로(01-VERIFICATION.md 등).
 마지막에 한 줄: "`.planning/` 하위 diff는 GSD 실행 기록이므로 코드 리뷰 대상이 아닙니다"}

## 머지 후
{다음 단계 안내 (다음 GSD 커맨드 등). 결정 기록이 추가됐으면 D-XXX 목록 한 줄씩}
```

마지막 줄에 세션 링크 트레일러를 붙인다 (Claude Code 규칙).

## 3. 학습 노트 작성 규칙

CLAUDE.md 학습 모드 형식을 그대로 따르되, PR용으로 **비유 한 줄**을 추가한다.
항목마다 4요소:

1. **이름** (한 줄 정의). 처음 등장한 개념은 `★` 표시
2. **비유** — *기울임체 한 줄.* 단, **정확성이 우선**이다:
   - 비유가 개념의 핵심 메커니즘을 보존해야 한다 (예: Flyway = "DB 스키마의 git 히스토리" ✓ — 불변 이력에 새 버전만 추가한다는 핵심이 살아 있음)
   - 틀린 직관을 심는 비유면 차라리 생략한다 (CLAUDE.md: "틀린 이해를 유발하는 비유는 쓰지 않는다")
3. **이 브랜치에서 왜 필요했는가** — 우리 도메인의 구체 상황으로
4. **안 썼으면 뭐가 깨지는가** — 실패 시나리오 한 줄

지킬 것:
- **이 브랜치에 실제로 등장한 것만.** 일반 백엔드 강의 요약 금지
- SUMMARY.md의 "이번에 쓴 기술" 섹션들을 취합·중복 제거해 **5~8항목으로 압축** (전부 나열 금지)
- **일부러 쓰지 않은 것** 소단락을 반드시 포함 — 무엇을 왜 안 골랐는지가 고른 것만큼 중요하다
- 아키텍처 수준 변화(패키지 구조, 트랜잭션 경계, 계층 분리 등)가 있었다면 별도 항목으로
- 트랜잭션·락·N+1·멱등성 등 CLAUDE.md "자주 다룰 주제"에 해당하는 코드가 이 브랜치에 있으면 우선 선정

## 4. 리뷰 가이드 작성 규칙 (학습 겸용)

소유자가 코드를 "읽으며 배우는" 동선을 설계한다:

- **읽는 순서** — 의존성 방향대로 3~5단계 (예: `ErrorCode` → `GlobalExceptionHandler` → 테스트).
  각 단계에 "여기서 볼 것" 한 줄
- **가장 배울 게 많은 파일 1~2개** — 왜 그 파일인지 한 줄
- **직접 확인하는 방법** — 로컬에서 눈으로 검증 가능한 명령 2~3개
  (예: `docker-compose up -d && ./gradlew build`, `./gradlew generateApiDocs`,
  `curl`로 에러 응답 모양 확인 등). 읽기보다 실행이 오래 남는다

## 5. PR 생성

본문은 셸 인용 문제를 피하기 위해 반드시 파일로 쓴 뒤 `--body-file`로 전달한다:

```bash
git push -u origin "$(git branch --show-current)"
gh pr create --base dev \
  --title "feat: {한 줄 요약}" \
  --body-file {scratchpad}/pr-body.md
```

- base는 **dev** (dev→main PR은 마일스톤/배포 시점에만 — 브랜치 전략)
- 제목은 Conventional Commits 형식. phase 작업이면 `feat: Phase {N} {슬러그} — {핵심 3가지}` 꼴 권장
- **머지는 하지 않는다.** PR URL을 보고하고 머지 버튼은 사용자가 누른다
- PR을 열면 CI의 Claude 리뷰 봇(`.github/workflows/claude-pr-review.yml`)이 자동으로
  인라인 리뷰를 남긴다 — 완료 보고에 "봇 리뷰가 도착하면 Critical/Warning부터 확인 후
  머지"를 안내한다. 봇이 Critical을 남기면 머지 전에 이 세션에서 수정을 제안한다

## 6. 완료 보고

- PR URL
- 본문에 담은 학습 노트 항목 수와 ★(신규 개념) 수
- API 계약·마이그레이션·환경변수 변경 유무 요약 (조건부 섹션이 들어갔는지)
- 머지 전 사용자가 확인하면 좋을 파일 1~2개 (리뷰 가이드와 동일 지점)
