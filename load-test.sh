#!/bin/bash

echo "🧪 Running load tests against the log processing system..."

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
until curl -f http://localhost:8080/apigateway/producer/api/logs/health > /dev/null 2>&1; do
  echo "Waiting for API Gateway..."
  sleep 2
done

echo "✅ Services are ready!"

# Generate test data
echo "📊 Generating test log events..."

# Simple load test using curl
for i in {1..100}; do
  curl -X POST http://localhost:8080/apigateway/producer/api/logs \
    -H "Content-Type: application/json" \
    -d "{
      \"organizationId\": \"org-set3-$((i % 5))\",
      \"level\": \"INFO\",
      \"message\": \"Test log message $i\",
      \"source\": \"load-test\"
    }" &

  echo -e "\n"
  # Limit concurrent requests
  if (( i % 10 == 0 )); then
    wait
    echo -e "\nSent $i requests...\n"
  fi
done

wait
echo "✅ Load test completed! Sent 100 log events."
echo "📊 Check Grafana dashboard at http://localhost:3000 for metrics."
