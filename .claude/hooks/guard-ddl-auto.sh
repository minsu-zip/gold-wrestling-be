#!/usr/bin/env bash
# ddl-auto 를 validate/none 이외의 값으로 바꾸는 것을 차단한다.
# 스키마의 유일한 주체는 Flyway 다 (CLAUDE.md 기술 규칙, docs/conventions.md §9).
set -uo pipefail

payload=$(cat)
content=$(printf '%s' "$payload" | jq -r '.tool_input.new_string // .tool_input.content // empty')

[ -z "$content" ] && exit 0

# ddl-auto 값 추출 (yml / properties 양쪽 형태)
bad=$(printf '%s' "$content" \
  | grep -oiE 'ddl-auto[[:space:]]*[:=][[:space:]]*[a-z-]+' \
  | grep -viE ':[[:space:]]*(validate|none)$' \
  | grep -viE '=[[:space:]]*(validate|none)$' || true)

[ -z "$bad" ] && exit 0

reason="ddl-auto 를 validate/none 이외의 값으로 바꿀 수 없습니다: $(printf '%s' "$bad" | tr '\n' ' ')
스키마 변경의 유일한 주체는 Flyway 마이그레이션입니다 (create/update 는 스키마를 조용히 바꿔 버립니다).
테이블이 필요하면 src/main/resources/db/migration 에 마이그레이션을 추가하세요.
(CLAUDE.md 기술 규칙, docs/conventions.md §9)"

jq -nc --arg r "$reason" '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "deny",
    permissionDecisionReason: $r
  }
}'
exit 0
