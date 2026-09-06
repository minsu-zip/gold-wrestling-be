---
quick_id: 260901-tkv
description: CI 워크플로 추가 및 main 보호 required check 절차 문서화
date: 2026-09-01
status: complete
commit: 9cdd42e
---

# Summary: CI 워크플로 + main 보호 required check 문서화

## 변경 파일

- `.github/workflows/ci.yml` (신규) — PR(dev·main) + push(dev·main)에서 `./gradlew ktlintCheck build`.
  단일 job `build` → required check 이름 `CI / build`. temurin JDK 21 + gradle/actions/setup-gradle@v4 캐시.
  paths-ignore 없음(FE D-08과 동일 이유), concurrency `ci-${{ github.ref }}`, permissions contents: read,
  checkout persist-credentials: false. Testcontainers는 러너 기본 Docker 사용 — 서비스 컨테이너·시크릿 불요.
- `README.md` — "CI · 브랜치 보호 (운영)" 섹션 신설: 워크플로 개요 + main 보호 required status check
  등록 4단계 절차(dev 머지 → PR 1회 실행 → Checks 탭 문자열 복사 → 보호 규칙 등록) + job 이름 변경 경고.
- `docs/decisions.md` — D-152 기록 (단일 job·트리거 설계·기각 대안).

## 검증

- YAML 파싱 통과 (python3 yaml.safe_load). actionlint 미설치로 건너뜀.
- 테스트 미작성: yml·문서 변경만 — conventions.md §10.0 면제 목록 해당.
- CI 자체의 첫 실검증은 dev 푸시/PR 시 GitHub Actions에서 이루어진다.

## 커밋 안 함

CLAUDE.md 커밋 규칙(사용자 명시 요청 시에만)이 GSD 자동 커밋을 덮어쓴다 — 사용자 확인 후 커밋.
