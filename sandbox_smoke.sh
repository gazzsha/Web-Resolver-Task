#!/usr/bin/env bash
# sandbox_smoke.sh — отдельный тест для проверки реальной работы Docker-sandbox.
# Прогоняет 6 сценариев: 3 правильных решения (Python, Java, Kotlin) и 3
# намеренно неверных (WA, RTE, отсутствие stdin). Печатает passed/total
# и verdict из ответа /api/v1/task-results/{id}.
#
# Usage: ./sandbox_smoke.sh
# Requires: curl, jq, jwt-аутентифицированный seed-юзер student1@diplom.local
# Backend должен быть запущен на http://localhost:8080.

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'
BASE=http://localhost:8080
PASS=0; FAIL=0

if ! command -v jq &>/dev/null; then
  echo -e "${RED}jq не установлен. brew install jq${RESET}"; exit 2
fi

echo -e "${CYAN}${BOLD}#### Sandbox smoke test ####${RESET}"
echo "  Дата: $(date '+%Y-%m-%d %H:%M:%S')"
echo

# ── login ──────────────────────────────────────────────────────────
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"email":"student1@diplom.local","password":"Student123!"}' \
  "$BASE/auth/login" | jq -r '.accessToken')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo -e "${RED}Не удалось залогиниться. Backend поднят?${RESET}"; exit 1
fi
echo -e "${GREEN}OK${RESET} логин student1"
echo

# ── helpers ────────────────────────────────────────────────────────
submit_and_wait() {
  local task_id="$1" lang="$2" code="$3"
  local payload sub status
  payload=$(jq -n --arg t "$task_id" --arg l "$lang" --arg c "$code" \
    '{testId: $t, language: $l, code: $c}')
  sub=$(curl -s -X PATCH -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "$payload" "$BASE/api/v1/task-resolver/task/start" | jq -r '.id')
  [ -z "$sub" ] || [ "$sub" = "null" ] && { echo "submit-failed"; return 1; }

  local deadline=$(( $(date +%s) + 90 ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    status=$(curl -s -H "Authorization: Bearer $TOKEN" \
      "$BASE/api/v1/task-results/$sub" 2>/dev/null | jq -r '.status // empty')
    if [ -n "$status" ] && [ "$status" != "null" ]; then
      curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/task-results/$sub" \
        | jq -r '"\(.status) \(.passedTests)/\(.totalTests) verdict=\([.testResults[].verdict] | unique | join(","))"'
      return 0
    fi
    sleep 2
  done
  echo "timeout"; return 1
}

assert() {
  local label="$1" expected="$2" got="$3"
  if echo "$got" | grep -q "$expected"; then
    PASS=$((PASS+1)); echo -e "  ${GREEN}[PASS]${RESET} $label  →  $got"
  else
    FAIL=$((FAIL+1)); echo -e "  ${RED}[FAIL]${RESET} $label  expected '$expected', got '$got'"
  fi
}

REVERSE=d4e5f6a7-b8c9-0123-def0-234567890123
ADD2=f6a7b8c9-d0e1-2345-f012-456789012345
FIZZ=c3d4e5f6-a7b8-9012-cdef-123456789012

# ── 1. Python: правильное решение Reverse String ────────────────────
echo -e "${CYAN}${BOLD}1. Python — Reverse String (правильно)${RESET}"
got=$(submit_and_wait "$REVERSE" "python" "print(input()[::-1])")
assert "Python: 3/3 OK" "SUCCESS 3/3 verdict=OK" "$got"
echo

# ── 2. Java: правильное Add Two Numbers ─────────────────────────────
echo -e "${CYAN}${BOLD}2. Java — Add Two Numbers (правильно)${RESET}"
JAVA_ADD='import java.util.Scanner;
public class Solution {
  public static void main(String[] a) {
    Scanner s = new Scanner(System.in);
    long x = s.nextLong(), y = s.nextLong();
    System.out.println(x + y);
  }
}'
got=$(submit_and_wait "$ADD2" "java" "$JAVA_ADD")
assert "Java: 4/4 OK" "SUCCESS 4/4 verdict=OK" "$got"
echo

# ── 3. Python FizzBuzz (правильно) ─────────────────────────────────
echo -e "${CYAN}${BOLD}3. Python — FizzBuzz (правильно)${RESET}"
PY_FB='n = int(input())
for i in range(1, n+1):
  if i % 15 == 0: print("FizzBuzz")
  elif i % 3 == 0: print("Fizz")
  elif i % 5 == 0: print("Buzz")
  else: print(i)'
got=$(submit_and_wait "$FIZZ" "python" "$PY_FB")
assert "Python FizzBuzz: 2/2 OK" "SUCCESS 2/2 verdict=OK" "$got"
echo

# ── 4. Wrong Answer ────────────────────────────────────────────────
echo -e "${CYAN}${BOLD}4. Python — Reverse String (печатает оригинал, ожидаем WA на 2 теста, OK на палиндром 'a')${RESET}"
got=$(submit_and_wait "$REVERSE" "python" "print(input())")
assert "WRONG_ANSWER detected" "PARTIAL_SUCCESS 1/3 verdict=OK,WRONG_ANSWER" "$got"
echo

# ── 5. Runtime error ───────────────────────────────────────────────
echo -e "${CYAN}${BOLD}5. Python — деление на ноль (runtime error)${RESET}"
got=$(submit_and_wait "$REVERSE" "python" "print(1/0)")
assert "RUNTIME_ERROR detected" "FAILED 0/3 verdict=RUNTIME_ERROR" "$got"
echo

# ── 6. Java — попытка RCE ─────────────────────────────────────────
echo -e "${CYAN}${BOLD}6. Java — Runtime.exec(\"rm -rf /\") (sandbox должен задушить)${RESET}"
JAVA_RCE='public class Solution {
  public static void main(String[] a) throws Exception {
    Runtime.getRuntime().exec(new String[]{"rm","-rf","/"}).waitFor();
    System.out.println("danger");
  }
}'
got=$(submit_and_wait "$REVERSE" "java" "$JAVA_RCE")
# RUNTIME_ERROR or WRONG_ANSWER (если "danger" пройдёт — это ОК, hostfs read-only)
if echo "$got" | grep -qE "FAILED|ERROR"; then
  PASS=$((PASS+1)); echo -e "  ${GREEN}[PASS]${RESET} RCE НЕ прошёл  →  $got"
else
  FAIL=$((FAIL+1)); echo -e "  ${RED}[FAIL]${RESET} RCE прошёл (изоляция!): $got"
fi
echo

# ── summary ────────────────────────────────────────────────────────
echo -e "${CYAN}${BOLD}#### Summary ####${RESET}"
echo -e "  ${GREEN}Passed: $PASS${RESET}   ${RED}Failed: $FAIL${RESET}"
echo
[ "$FAIL" -eq 0 ] && { echo -e "${GREEN}${BOLD}Sandbox работает корректно.${RESET}"; exit 0; } \
                  || { echo -e "${RED}${BOLD}$FAIL сценариев упали.${RESET}"; exit 1; }
