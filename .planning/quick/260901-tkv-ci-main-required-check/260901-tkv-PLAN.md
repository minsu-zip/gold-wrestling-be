---
quick_id: 260901-tkv
description: CI 워크플로 추가 및 main 보호 required check 절차 문서화
date: 2026-09-01
status: planned
---

# Quick Task 260901-tkv: CI 워크플로 + main 보호 required check 문서화

## 배경

배포 마일스톤 전 사전 작업. 레포가 public으로 전환되어 main 브랜치 보호가 다시 강제되므로,
머지 게이트가 될 테스트 CI가 필요하다. FE 레포의 `ci.yml` 트리거 설계(PR+push, paths-ignore 없음)와
정합해야 한다 — required status check로 등록될 워크플로가 경로 필터로 스킵되면
"Expected — waiting for status"에서 머지가 영영 막히기 때문이다.

## 확인된 전제 (2026-09-01 코드베이스 기준)

- `./gradlew build`는 ktlint 플러그인이 `check`에 배선한 `ktlintCheck`를 이미 포함한다
- `generateApiDocs`(로컬 Postgres 필요)는 `build`에 배선되어 있지 않다 — CI에서 앱 기동 불필요
- 테스트는 Testcontainers(`postgres:18.4-alpine`) + gradle `systemProperty`(JWT 더미·배치 킬스위치)로
  자급된다 — `.env` 없이 Docker만 있으면 된다. ubuntu-latest 러너에 Docker 기본 탑재
- FE 체크 이름은 `CI / quality`·`CI / e2e`. BE는 단일 job → `CI / build`
- 리뷰 봇 concurrency group은 `claude-review-*` — FE처럼 `ci-` 접두로 분리

## Tasks

### Task 1: `.github/workflows/ci.yml` 추가
- 트리거: `pull_request`(dev·main) + `push`(dev·main), paths-ignore 없음 (FE와 동일 설계·동일 이유)
- `concurrency: ci-${{ github.ref }}` + cancel-in-progress
- `permissions: contents: read`, checkout `persist-credentials: false` (FE와 동일 최소권한)
- 단일 job `build`: temurin JDK 21 → `gradle/actions/setup-gradle@v4`(캐시) → `./gradlew ktlintCheck build`
- 하단 주석에 required check 등록 절차 포인터 (정본은 README)

### Task 2: README 운영 섹션에 등록 절차 문서화
- "테스트 · 코드 포맷" 뒤에 CI·브랜치 보호 섹션 추가
- FE D-08과 같은 절차: dev 머지 → PR 1회 실행 → Checks 탭 문자열 복사 → main 보호 규칙 등록
- job 이름 변경 시 등록이 깨진다는 경고 포함. 등록 자체는 사용자가 GitHub 설정에서 수행

### Task 3: `docs/decisions.md`에 D-152 기록

## 테스트 방침

yml·문서 변경만 있으므로 conventions.md §10.0 면제 목록(yml/gradle/문서) 해당 — 테스트 없음.
워크플로 문법은 로컬 YAML 파싱 + actionlint(설치돼 있으면)로 검증한다.

## 커밋 방침

CLAUDE.md 커밋 규칙(명시 요청 시에만)이 GSD 자동 커밋보다 우선한다 — 커밋 없이 멈추고 보고한다.
