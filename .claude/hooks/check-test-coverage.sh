#!/usr/bin/env bash
# 턴이 끝날 때, 프로덕션 코드가 바뀌었는데 테스트가 하나도 함께 바뀌지 않았으면 알려 준다.
# 차단하지 않는다(알림만) — 판단은 CLAUDE.md 규칙 10과 conventions.md §10.0 표가 기준이다.
#
# 검사 범위: 아직 push 되지 않은 커밋 + 작업 트리의 변경(추적/미추적 모두)
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

changed() {
  # 미push 커밋
  local upstream
  upstream=$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>/dev/null || true)
  if [ -n "$upstream" ]; then
    git diff --name-only "$upstream"...HEAD 2>/dev/null || true
  fi
  # 작업 트리 (스테이지 + 미스테이지 + 미추적). 리네임은 화살표 뒤 경로만 취한다.
  # --untracked-files=all: 새 디렉터리를 "dir/" 한 줄로 접지 않고 파일 단위로 나열한다
  git status --porcelain --untracked-files=all 2>/dev/null | sed 's/^...//; s/^.* -> //' || true
}

files=$(changed | sort -u | grep -v '^$' || true)
[ -z "$files" ] && exit 0

# 테스트가 필요한 프로덕션 코드 (conventions.md §10.0 면제 항목 제외)
prod=$(printf '%s\n' "$files" \
  | grep -E '^src/main/kotlin/.*\.kt$' \
  | grep -vE '/config/|Application\.kt$|/dto/' || true)

[ -z "$prod" ] && exit 0

tests=$(printf '%s\n' "$files" | grep -E '^src/test/' || true)
[ -n "$tests" ] && exit 0

list=$(printf '%s\n' "$prod" | sed 's|^|  - |' | head -10)

msg="테스트 없이 프로덕션 코드만 변경되었습니다:
$list

CLAUDE.md 규칙 10 — conventions.md §10.0 표에서 이 변경에 필요한 테스트를 확인하세요.
면제 대상(config·필드만 있는 DTO 등)이라면 완료 보고에 그 이유를 한 줄로 밝히면 됩니다."

jq -nc --arg m "$msg" '{systemMessage: $m}'
exit 0
