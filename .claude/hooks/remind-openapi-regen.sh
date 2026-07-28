#!/usr/bin/env bash
# 컨트롤러·DTO가 바뀌면 docs/api/openapi.yaml 재생성이 필요하다는 사실을 상기시킨다.
# openapi.yaml 은 FE와의 유일한 API 계약이라 코드와 같은 커밋에 들어가야 한다.
set -uo pipefail

payload=$(cat)
file=$(printf '%s' "$payload" | jq -r '.tool_response.filePath // .tool_input.file_path // empty')

[ -z "$file" ] && exit 0
case "$file" in
  *Controller.kt|*/dto/*.kt) ;;
  *) exit 0 ;;
esac

msg="API 표면이 변경되었습니다 (${file##*/}). 작업을 마치기 전에:
  1) ./gradlew bootRun 으로 기동
  2) curl -s http://localhost:8080/v3/api-docs.yaml -o docs/api/openapi.yaml
  3) git diff docs/api/openapi.yaml 로 의도한 변경만 있는지 확인 후 같은 커밋에 포함
(CLAUDE.md 규칙 4 — FE가 이 파일로 타입을 생성한다)"

jq -nc --arg m "$msg" '{
  hookSpecificOutput: {
    hookEventName: "PostToolUse",
    additionalContext: $m
  }
}'
exit 0
