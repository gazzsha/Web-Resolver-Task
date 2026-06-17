#!/bin/bash

echo "=== Web Resolver - Full Submission Test ==="
echo ""

# Submit solution
echo "1. Submitting Two Sum solution..."
RESPONSE=$(curl -s -X PATCH http://localhost:8080/api/v1/task-resolver/task/start \
  -H "Content-Type: application/json" \
  -u admin:admin \
  -d '{
    "testId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "code": "class Solution { public int[] twoSum(int[] nums, int target) { java.util.Map<Integer, Integer> map = new java.util.HashMap<>(); for (int i = 0; i < nums.length; i++) { int complement = target - nums[i]; if (map.containsKey(complement)) { return new int[] { map.get(complement), i }; } map.put(nums[i], i); } throw new IllegalArgumentException(\"No solution\"); } }",
    "language": "java"
  }')

echo "Submission Response:"
echo "$RESPONSE" | python3 -m json.tool

# Extract task ID
TASK_ID=$(echo "$RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('id', ''))" 2>/dev/null)

if [ -z "$TASK_ID" ]; then
  echo "❌ Failed to get task ID"
  exit 1
fi

echo ""
echo "✓ Task ID: $TASK_ID"
echo ""
echo "2. Waiting for worker to process (10 seconds)..."
sleep 10

echo ""
echo "3. Fetching result..."
RESULT=$(curl -s http://localhost:8080/api/v1/task-results/$TASK_ID \
  -u admin:admin)

if [ -z "$RESULT" ] || [ "$RESULT" = "null" ]; then
  echo "⚠️  No result yet (worker may still be processing)"
  echo ""
  echo "Checking Kafka topics..."
  docker exec web-resolver-kafka kafka-topics --bootstrap-server localhost:9092 --describe 2>&1 | grep -E "task-execution|task-results"
else
  echo "Result:"
  echo "$RESULT" | python3 -m json.tool
fi

echo ""
echo "=== Test Complete ==="
