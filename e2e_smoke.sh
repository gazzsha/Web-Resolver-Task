#!/usr/bin/env bash
# e2e_smoke.sh — Full auth-flow + API smoke test for Web-Resolver-Task MVP.
# Usage: ./e2e_smoke.sh
# Requires: curl, jq, a running instance at http://localhost:8080
# Start stack first:
#   docker compose up -d postgres kafka zookeeper
#   ./gradlew :MainApplication:bootRun

set -euo pipefail

# ---------------------------------------------------------------------------
# ANSI colours
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

BASE_URL="http://localhost:8080"

PASS=0
FAIL=0
RESULTS=()

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

pass() {
    local msg="$1"
    PASS=$((PASS + 1))
    RESULTS+=("[PASS] $msg")
    echo -e "  ${GREEN}[PASS]${RESET} $msg"
}

fail() {
    local msg="$1"
    FAIL=$((FAIL + 1))
    RESULTS+=("[FAIL] $msg")
    echo -e "  ${RED}[FAIL]${RESET} $msg"
}

section() {
    echo ""
    echo -e "${CYAN}${BOLD}=== $1 ===${RESET}"
}

# Assert that an HTTP status code matches an expected value.
# Usage: assert_status <expected> <actual> <label>
assert_status() {
    local expected="$1"
    local actual="$2"
    local label="$3"
    if [ "$actual" -eq "$expected" ]; then
        pass "$label (HTTP $actual)"
    else
        fail "$label (expected HTTP $expected, got $actual)"
    fi
}

# ---------------------------------------------------------------------------
# Dependency check
# ---------------------------------------------------------------------------
if ! command -v jq &>/dev/null; then
    echo -e "${RED}ERROR: 'jq' is not installed. Install it (e.g. brew install jq) and retry.${RESET}"
    exit 2
fi

# ---------------------------------------------------------------------------
# Banner
# ---------------------------------------------------------------------------
echo ""
echo -e "${CYAN}${BOLD}##############################################${RESET}"
echo -e "${CYAN}${BOLD}#   Web-Resolver-Task  —  E2E Smoke Test     #${RESET}"
echo -e "${CYAN}${BOLD}##############################################${RESET}"
echo ""
echo -e "  Target : ${BOLD}${BASE_URL}${RESET}"
echo -e "  Date   : $(date '+%Y-%m-%d %H:%M:%S')"

# ---------------------------------------------------------------------------
# Step 1 — Health check
# ---------------------------------------------------------------------------
section "1. Health check"
HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/health" || true)
if [ "$HEALTH_STATUS" = "200" ]; then
    HEALTH_BODY=$(curl -s "${BASE_URL}/actuator/health")
    STATUS_VAL=$(echo "$HEALTH_BODY" | jq -r '.status // empty')
    if [ "$STATUS_VAL" = "UP" ]; then
        pass "Actuator health returns UP"
    else
        fail "Actuator health returned status='${STATUS_VAL}' (expected UP)"
    fi
else
    fail "Actuator health returned HTTP $HEALTH_STATUS (expected 200). Is the app running?"
    echo -e "${YELLOW}  Hint: docker compose up -d postgres kafka zookeeper && ./gradlew :MainApplication:bootRun${RESET}"
    echo ""
    echo -e "${RED}Cannot continue without a running application. Exiting.${RESET}"
    exit 1
fi

# ---------------------------------------------------------------------------
# Step 2 — Register a new user with timestamp-based email
# ---------------------------------------------------------------------------
section "2. Register new user (unique timestamp email)"
NEW_EMAIL="e2e+$(date +%s)@diplom.local"
NEW_PASS="E2ePass99!"

REG_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${NEW_EMAIL}\",\"password\":\"${NEW_PASS}\"}")
REG_STATUS=$(echo "$REG_RESP" | tail -n1)
REG_BODY=$(echo "$REG_RESP" | sed '$d')

assert_status 200 "$REG_STATUS" "POST /auth/register with unique email"

ACCESS_TOKEN=$(echo "$REG_BODY" | jq -r '.accessToken // empty')
REFRESH_TOKEN=$(echo "$REG_BODY" | jq -r '.refreshToken // empty')

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
    fail "accessToken missing in register response"
    echo "  Response: $REG_BODY"
else
    pass "Register response contains non-empty accessToken"
fi

if [ -z "$REFRESH_TOKEN" ] || [ "$REFRESH_TOKEN" = "null" ]; then
    fail "refreshToken missing in register response"
else
    pass "Register response contains non-empty refreshToken"
fi

# ---------------------------------------------------------------------------
# Step 3 — GET /api/v1/tasks with the new user's token (must return 200 + array)
# ---------------------------------------------------------------------------
section "3. GET /api/v1/tasks with valid Bearer token"
TASKS_RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/tasks" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}")
TASKS_STATUS=$(echo "$TASKS_RESP" | tail -n1)
TASKS_BODY=$(echo "$TASKS_RESP" | sed '$d')

assert_status 200 "$TASKS_STATUS" "GET /api/v1/tasks with valid token"

TASK_COUNT=$(echo "$TASKS_BODY" | jq 'if type == "array" then length else 0 end')
if [ "$TASK_COUNT" -gt 0 ]; then
    pass "Tasks endpoint returned non-empty array ($TASK_COUNT tasks)"
else
    fail "Tasks endpoint returned empty array or non-array — seed tasks may not be loaded"
fi

FIRST_TASK_ID=$(echo "$TASKS_BODY" | jq -r '.[0].testId // empty')

# ---------------------------------------------------------------------------
# Step 4 — Login with seed student1
# ---------------------------------------------------------------------------
section "4. Login seed student (student1@diplom.local)"
LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"student1@diplom.local","password":"Student123!"}')
LOGIN_STATUS=$(echo "$LOGIN_RESP" | tail -n1)
LOGIN_BODY=$(echo "$LOGIN_RESP" | sed '$d')

assert_status 200 "$LOGIN_STATUS" "POST /auth/login seed student1"

STUDENT_TOKEN=$(echo "$LOGIN_BODY" | jq -r '.accessToken // empty')
STUDENT_REFRESH=$(echo "$LOGIN_BODY" | jq -r '.refreshToken // empty')

if [ -z "$STUDENT_TOKEN" ] || [ "$STUDENT_TOKEN" = "null" ]; then
    fail "accessToken missing in student1 login response"
else
    pass "student1 login returned valid accessToken"
fi

# ---------------------------------------------------------------------------
# Step 5 — GET /api/v1/tasks/{id} with student token
# ---------------------------------------------------------------------------
section "5. GET /api/v1/tasks/{id} (first task) with student token"
if [ -z "$FIRST_TASK_ID" ] || [ "$FIRST_TASK_ID" = "null" ]; then
    fail "Cannot run task-by-id test: no task ID available from step 3"
else
    TASK_RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/tasks/${FIRST_TASK_ID}" \
        -H "Authorization: Bearer ${STUDENT_TOKEN}")
    TASK_STATUS=$(echo "$TASK_RESP" | tail -n1)
    TASK_BODY=$(echo "$TASK_RESP" | sed '$d')

    assert_status 200 "$TASK_STATUS" "GET /api/v1/tasks/${FIRST_TASK_ID}"
    TASK_TITLE=$(echo "$TASK_BODY" | jq -r '.title // empty')
    if [ -n "$TASK_TITLE" ] && [ "$TASK_TITLE" != "null" ]; then
        pass "Task detail contains title: '$TASK_TITLE'"
    else
        fail "Task detail response missing 'title' field"
    fi
fi

# ---------------------------------------------------------------------------
# Step 6 — POST /auth/refresh with student1's refresh token
# ---------------------------------------------------------------------------
section "6. POST /auth/refresh — obtain new token pair"
if [ -z "$STUDENT_REFRESH" ] || [ "$STUDENT_REFRESH" = "null" ]; then
    fail "Cannot run refresh test: no refresh token available from step 4"
else
    REFRESH_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/auth/refresh" \
        -H "Content-Type: application/json" \
        -d "{\"refreshToken\":\"${STUDENT_REFRESH}\"}")
    REFRESH_STATUS=$(echo "$REFRESH_RESP" | tail -n1)
    REFRESH_BODY=$(echo "$REFRESH_RESP" | sed '$d')

    assert_status 200 "$REFRESH_STATUS" "POST /auth/refresh with valid refresh token"

    NEW_ACCESS=$(echo "$REFRESH_BODY" | jq -r '.accessToken // empty')
    NEW_REFRESH=$(echo "$REFRESH_BODY" | jq -r '.refreshToken // empty')
    if [ -n "$NEW_ACCESS" ] && [ "$NEW_ACCESS" != "null" ]; then
        pass "Refresh returned new accessToken"
    else
        fail "Refresh response missing new accessToken"
    fi
    if [ -n "$NEW_REFRESH" ] && [ "$NEW_REFRESH" != "null" ]; then
        pass "Refresh returned new refreshToken"
    else
        fail "Refresh response missing new refreshToken"
    fi
fi

# ---------------------------------------------------------------------------
# Step 7 — Negative cases
# ---------------------------------------------------------------------------
section "7. Negative cases"

# 7a — GET /api/v1/tasks without token → 401
UNAUTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/v1/tasks")
assert_status 401 "$UNAUTH_STATUS" "GET /api/v1/tasks without Authorization header"

# 7b — POST /auth/login with wrong password → 401
WRONG_PW_RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"student1@diplom.local","password":"WrongPassword999!"}')
assert_status 401 "$WRONG_PW_RESP" "POST /auth/login with wrong password"

# 7c — POST /auth/register with already-registered email → 409
DUP_REG_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${NEW_EMAIL}\",\"password\":\"${NEW_PASS}\"}")
assert_status 409 "$DUP_REG_STATUS" "POST /auth/register duplicate email"

# 7d — GET /api/v1/tasks with malformed token → 401
MALFORMED_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/v1/tasks" \
    -H "Authorization: Bearer this.is.not.a.valid.jwt")
assert_status 401 "$MALFORMED_STATUS" "GET /api/v1/tasks with malformed JWT"

# ---------------------------------------------------------------------------
# Step 8 — OPTIONAL full submission flow (Kafka → worker → sandbox → poll)
# Skipped by default. Enable with:  WITH_SUBMISSION=1 ./e2e_smoke.sh
# Requires Docker daemon + eclipse-temurin:21-jdk-alpine pulled (handled by
# SandboxImagePrewarmer at app startup; first run after a clean docker may
# wait ~30-90s while the image is being pulled).
# ---------------------------------------------------------------------------
if [ "${WITH_SUBMISSION:-0}" = "1" ]; then
    section "8. Submit Two Sum solution and poll for verdict"

    if [ -z "${FIRST_TASK_ID:-}" ] || [ "$FIRST_TASK_ID" = "null" ]; then
        fail "Cannot run submission test: no task ID available from step 3"
    else
        JAVA_CODE='import java.util.*;
public class Solution {
  public static void main(String[] args) {
    Scanner sc = new Scanner(System.in);
    String[] parts = sc.nextLine().split(",");
    int target = Integer.parseInt(sc.nextLine());
    int n = parts.length;
    int[] nums = new int[n];
    for (int i = 0; i < n; i++) nums[i] = Integer.parseInt(parts[i]);
    for (int i = 0; i < n; i++)
      for (int j = i+1; j < n; j++)
        if (nums[i] + nums[j] == target) { System.out.println(i + " " + j); return; }
  }
}'

        SUBMIT_PAYLOAD=$(jq -n --arg testId "$FIRST_TASK_ID" --arg code "$JAVA_CODE" \
            '{testId: $testId, language: "java", code: $code}')

        SUBMIT_RESP=$(curl -s -w "\n%{http_code}" -X PATCH "${BASE_URL}/api/v1/task-resolver/task/start" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer ${STUDENT_TOKEN}" \
            -d "$SUBMIT_PAYLOAD")
        SUBMIT_STATUS=$(echo "$SUBMIT_RESP" | tail -n1)
        SUBMIT_BODY=$(echo "$SUBMIT_RESP" | sed '$d')
        assert_status 200 "$SUBMIT_STATUS" "PATCH /api/v1/task-resolver/task/start (Two Sum, java)"

        SUBMISSION_ID=$(echo "$SUBMIT_BODY" | jq -r '.id // empty')
        if [ -z "$SUBMISSION_ID" ] || [ "$SUBMISSION_ID" = "null" ]; then
            fail "Submission response missing id"
            echo "  Response: $SUBMIT_BODY"
        else
            pass "Submission accepted (id=$SUBMISSION_ID)"

            echo -e "${YELLOW}  Polling /api/v1/task-results/${SUBMISSION_ID} (timeout 120s)...${RESET}"
            POLL_TIMEOUT=120
            POLL_DEADLINE=$(( $(date +%s) + POLL_TIMEOUT ))
            POLL_RESULT=""
            POLL_STATUS_FINAL=""
            while [ $(date +%s) -lt $POLL_DEADLINE ]; do
                POLL_RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/task-results/${SUBMISSION_ID}" \
                    -H "Authorization: Bearer ${STUDENT_TOKEN}")
                POLL_STATUS=$(echo "$POLL_RESP" | tail -n1)
                POLL_BODY=$(echo "$POLL_RESP" | sed '$d')
                if [ "$POLL_STATUS" = "200" ]; then
                    POLL_STATUS_FINAL=$(echo "$POLL_BODY" | jq -r '.status // empty')
                    if [ "$POLL_STATUS_FINAL" != "" ] && [ "$POLL_STATUS_FINAL" != "null" ]; then
                        POLL_RESULT="$POLL_BODY"
                        break
                    fi
                fi
                sleep 2
            done

            if [ -z "$POLL_RESULT" ]; then
                fail "Result did not appear within ${POLL_TIMEOUT}s (still 404 — Kafka/worker pipeline stuck?)"
            else
                pass "Result available: status=$POLL_STATUS_FINAL"
                PASSED_TESTS=$(echo "$POLL_RESULT" | jq -r '.passedTests // 0')
                TOTAL_TESTS=$(echo "$POLL_RESULT" | jq -r '.totalTests // 0')
                echo -e "${YELLOW}  passed/total: ${PASSED_TESTS}/${TOTAL_TESTS}${RESET}"
                if [ "$POLL_STATUS_FINAL" = "SUCCESS" ] || [ "$POLL_STATUS_FINAL" = "PARTIAL_SUCCESS" ]; then
                    pass "Submission processed end-to-end (Kafka → worker → sandbox → result)"
                else
                    fail "Submission processed but verdict was '$POLL_STATUS_FINAL' (expected SUCCESS/PARTIAL_SUCCESS)"
                fi
            fi
        fi
    fi
else
    echo ""
    echo -e "${YELLOW}  (Step 8 skipped — set WITH_SUBMISSION=1 to test full Kafka/worker/sandbox pipeline)${RESET}"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo -e "${CYAN}${BOLD}##############################################${RESET}"
echo -e "${CYAN}${BOLD}#                  SUMMARY                   #${RESET}"
echo -e "${CYAN}${BOLD}##############################################${RESET}"
echo ""

for r in "${RESULTS[@]}"; do
    if [[ "$r" == \[PASS\]* ]]; then
        echo -e "  ${GREEN}${r}${RESET}"
    else
        echo -e "  ${RED}${r}${RESET}"
    fi
done

echo ""
echo -e "  ${GREEN}Passed: ${PASS}${RESET}   ${RED}Failed: ${FAIL}${RESET}"
echo ""

if [ "$FAIL" -eq 0 ]; then
    echo -e "${GREEN}${BOLD}All checks passed. MVP auth-flow is healthy.${RESET}"
    echo ""
    exit 0
else
    echo -e "${RED}${BOLD}${FAIL} check(s) failed. Review the output above.${RESET}"
    echo ""
    exit 1
fi
