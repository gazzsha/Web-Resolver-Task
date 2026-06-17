#!/bin/bash

echo "=== Web Resolver - Debug Test ==="
echo ""

# 1. Check if services are running
echo "1. Checking services..."
curl -s http://localhost:8080/actuator/health > /dev/null 2>&1 && echo "✓ Main Application: Running" || echo "✗ Main Application: NOT running"
curl -s http://localhost:8081/actuator/health > /dev/null 2>&1 && echo "✓ Worker Service: Running" || echo "✗ Worker Service: NOT running"
docker exec web-resolver-kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1 && echo "✓ Kafka: Running" || echo "✗ Kafka: NOT running"
docker exec web-resolver-db psql -U postgres -c "SELECT 1" > /dev/null 2>&1 && echo "✓ PostgreSQL: Running" || echo "✗ PostgreSQL: NOT running"
echo ""

# 2. Submit solution
echo "2. Submitting solution..."
RESPONSE=$(curl -s -X PATCH http://localhost:8080/api/v1/task-resolver/task/start \
  -H "Content-Type: application/json" \
  -u admin:admin \
  -d '{
    "testId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "code": "class Solution { public int[] twoSum(int[] nums, int target) { return new int[]{0,1}; } }",
    "language": "java"
  }')

echo "Response: $RESPONSE"
TASK_ID=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('id', ''))" 2>/dev/null)
echo "Task ID: $TASK_ID"
echo ""

# 3. Check Kafka messages
echo "3. Checking Kafka topic offsets..."
docker exec web-resolver-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group worker-group 2>&1 | grep -E "GROUP|worker-group"
echo ""

# 4. Wait and check results
echo "4. Waiting for processing (15 seconds)..."
sleep 15
echo ""

# 5. Check database
echo "5. Checking database for results..."
docker exec web-resolver-db psql -U postgres -d web_resolver -c "SELECT id, task_id, status, passed_tests, total_tests FROM task_results ORDER BY created_at DESC LIMIT 3" 2>&1
echo ""

# 6. Try to get result via API
echo "6. Getting result via API..."
if [ -n "$TASK_ID" ]; then
  RESULT=$(curl -s http://localhost:8080/api/v1/task-results/$TASK_ID -u admin:admin)
  if [ "$RESULT" = "null" ] || [ -z "$RESULT" ]; then
    echo "No result found (404)"
  else
    echo "Result: $RESULT" | python3 -m json.tool
  fi
fi
echo ""

echo "=== Test Complete ==="
