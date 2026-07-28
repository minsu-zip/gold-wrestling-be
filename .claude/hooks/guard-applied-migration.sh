#!/usr/bin/env bash
# 이미 커밋된 Flyway 마이그레이션 파일 수정을 차단한다.
# Flyway가 체크섬을 비교하므로, 적용된 마이그레이션을 고치면 기동 자체가 실패한다.
# 고쳐야 하면 새 버전(V<다음번호>__...)을 추가한다.
set -uo pipefail

payload=$(cat)
file=$(printf '%s' "$payload" | jq -r '.tool_input.file_path // empty')

[ -z "$file" ] && exit 0
case "$file" in
  *db/migration/V*.sql) ;;
  *) exit 0 ;;
esac

# git 명령이 cwd 에 의존하므로 레포 루트로 이동한다.
# 이동에 실패하면 "커밋 여부를 알 수 없음"이므로 통과시키지 않고 사람이 판단하도록 ask 로 넘긴다.
if ! cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || ! git rev-parse --git-dir >/dev/null 2>&1; then
  jq -nc '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "ask",
      permissionDecisionReason: "Flyway 마이그레이션 파일인데 git 저장소를 확인할 수 없어 커밋 여부를 판정하지 못했습니다. 이미 적용된 마이그레이션이면 수정하지 말고 새 버전을 추가하세요."
    }
  }'
  exit 0
fi

# 아직 커밋되지 않은 새 마이그레이션은 자유롭게 수정 가능
if ! git ls-files --error-unmatch "$file" >/dev/null 2>&1; then
  exit 0
fi

reason="이미 커밋된 Flyway 마이그레이션입니다: ${file##*/}
Flyway 체크섬 검증 때문에 수정하면 애플리케이션 기동이 실패합니다.
고쳐야 한다면 이 파일을 수정하는 대신 새 버전(V<다음번호>__설명.sql)을 추가하세요.
(docs/conventions.md §9, .claude/skills/add-migration)"

jq -nc --arg r "$reason" '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "deny",
    permissionDecisionReason: $r
  }
}'
exit 0
