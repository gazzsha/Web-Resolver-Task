#!/usr/bin/env bash
# Quick load generator for AI / sandbox / worker metrics demo.
# Logs in as a single test user, submits a mix of correct and broken Python
# solutions against the seeded "Two Sum" task. Each submission carries a
# unique header comment (timestamp + index), so the AI-analyzer cache treats
# them as distinct keys and every call reaches GigaChat — populating the
# success/fallback/cache_hit ratios visible in the Grafana AI dashboard.
#
# Usage: bash scripts/load_gen.sh [N]   (default 20 submissions)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${LOADGEN_EMAIL:-loadtest@example.com}"
PASSWORD="${LOADGEN_PASSWORD:-LoadTest123!}"
TEST_ID="a1b2c3d4-e5f6-7890-abcd-ef1234567890"   # Two Sum
COUNT="${1:-20}"

RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")

TOKEN=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || true)
if [ -z "$TOKEN" ]; then
  RESP=$(curl -s -X POST "$BASE_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"loadtest\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"STUDENT\"}")
  TOKEN=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
fi

echo "Token acquired: ${TOKEN:0:20}..."

STAMP=$(date +%s)

# Solution templates (parametrised with a unique header comment to bust cache).
SOLN_OK_TPL='# loadgen %s\nnums=list(map(int,input().split()))\ntarget=int(input())\nfor i in range(len(nums)):\n  for j in range(i+1,len(nums)):\n    if nums[i]+nums[j]==target:\n      print(i,j); break\n  else:\n    continue\n  break\n'

SOLN_WA_TPL='# loadgen %s\nnums=list(map(int,input().split()))\ntarget=int(input())\nprint(0,1)\n'

SOLN_TLE_TPL='# loadgen %s\nimport time\nnums=list(map(int,input().split()))\ntarget=int(input())\nwhile True:\n  time.sleep(1)\n'

SOLN_STYLE_TPL='# loadgen %s\nimport sys\nnums=list(map(int,input().split()))\ntarget=int(input())\nresult=[]\nfor index_i in range(len(nums)):\n  for index_j in range(index_i+1,len(nums)):\n    if nums[index_i]+nums[index_j]==target:\n      result.append(index_i)\n      result.append(index_j)\nprint(*result[:2])\n'

submit_with_tag() {
  local tpl="$1"; local tag="$2"
  local code
  code=$(printf "$tpl" "$tag")
  local body
  body=$(python3 -c "import json,sys; print(json.dumps({'testId':'$TEST_ID','language':'python','code':sys.stdin.read()}))" <<<"$code")
  curl -s -X PATCH "$BASE_URL/api/v1/task-resolver/task/start" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "$body" > /dev/null
}

for i in $(seq 1 "$COUNT"); do
  tag="${STAMP}-${i}"
  case $((i % 4)) in
    0) submit_with_tag "$SOLN_OK_TPL"    "$tag";;
    1) submit_with_tag "$SOLN_WA_TPL"    "$tag";;
    2) submit_with_tag "$SOLN_STYLE_TPL" "$tag";;
    3) submit_with_tag "$SOLN_TLE_TPL"   "$tag";;
  esac
  printf '.'
done
echo
echo "Submitted $COUNT solutions (each with unique cache-bust header)."
