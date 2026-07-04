#!/bin/bash
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
ACCESS_TOKEN="${ACCESS_TOKEN:-${PS_ACCESS_TOKEN:-}}"
DRY_RUN="${DRY_RUN:-false}"

if [ "$#" -eq 0 ]; then
  echo "사용법: $0 <problem-json-file> [problem-json-file ...]" >&2
  echo "" >&2
  echo "환경변수:" >&2
  echo "  API_BASE_URL=http://localhost:8080   # 기본값" >&2
  echo "  ACCESS_TOKEN=<access-token>          # 선택, 있으면 Bearer 토큰으로 전송" >&2
  echo "  DRY_RUN=true                         # 검증만 하고 전송하지 않음" >&2
  exit 2
fi

"$ROOT_DIR/harness/validate-problem-json.sh" "$@"

for file in "$@"; do
  if [ "$DRY_RUN" = "true" ]; then
    echo "[DRY-RUN] ${file} -> ${API_BASE_URL}/api/problems"
    continue
  fi

  response_file="$(mktemp)"
  headers=(
    -H "Content-Type: application/json"
  )
  if [ -n "$ACCESS_TOKEN" ]; then
    headers+=(-H "Authorization: Bearer ${ACCESS_TOKEN}")
  fi

  status_code="$(
    curl -sS \
      -o "$response_file" \
      -w "%{http_code}" \
      -X POST \
      "${headers[@]}" \
      --data-binary "@${file}" \
      "${API_BASE_URL}/api/problems"
  )"

  if [ "$status_code" -lt 200 ] || [ "$status_code" -ge 300 ]; then
    echo "[FAIL] ${file} import 실패: HTTP ${status_code}" >&2
    sed 's/^/  /' "$response_file" >&2 || true
    rm -f "$response_file"
    exit 1
  fi

  problem_id="$(node -e "
const fs = require('fs');
const text = fs.readFileSync(process.argv[1], 'utf8');
try {
  const body = JSON.parse(text);
  process.stdout.write(body.id === undefined ? '' : String(body.id));
} catch (_error) {}
" "$response_file")"

  if [ -n "$problem_id" ]; then
    echo "[OK] ${file} -> problem #${problem_id}"
  else
    echo "[OK] ${file} import 완료"
  fi

  rm -f "$response_file"
done
