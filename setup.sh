#!/bin/bash

# Start Infrastructure

echo "🚀 Starting Distributed Log Processing System..."

# Start infrastructure services
echo "📦 Starting infrastructure services..."
docker compose up -d

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 30

# Check if Kafka is ready
echo "🔍 Checking Kafka connection..."
until docker exec dlp-kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; do
  echo "Waiting for Kafka..."
  sleep 5
done

# Create Kafka topics
echo "📝 Creating Kafka topics..."
docker exec dlp-kafka kafka-topics --create --topic log-events --bootstrap-server localhost:9092 --partitions 6 --replication-factor 1 --if-not-exists

# Check if PostgreSQL is ready
echo "🔍 Checking PostgreSQL connection..."
until docker exec dlp-postgres pg_isready -U loguser > /dev/null 2>&1; do
  echo "Waiting for PostgreSQL..."
  sleep 5
done

echo "✅ Infrastructure is ready!"
echo "🔗 Access points:"
echo "  - API Gateway: http://localhost:8080"
echo "  - Log Producer: http://localhost:8081"
echo "  - Log Consumer: http://localhost:8082"
echo "  - Prometheus: http://localhost:9090"
echo "  - Grafana: http://localhost:3000 (admin/admin)"

echo "🚀 Ready to start Spring Boot services!"
echo "Run the following in separate terminals:"
echo "  cd log-producer && mvn spring-boot:run"
echo "  cd log-consumer && mvn spring-boot:run"
echo "  cd api-gateway && mvn spring-boot:run"
