### Github Link :
https://github.com/sysdr/sdc-java/tree/main/day1/distributed-log-processor

## Infrastructure Setup with Docker 
### Docker Compose Commands

- docker compose up -d
- docker compose ps 
- docker compose down

## Access PostgreSQL:
```bash
docker exec -it dlp-postgres psql -U loguser -d logprocessor
```

```sql
\dt  -- List tables
SELECT * FROM information_schema.tables;
SELECT * FROM log_events;

```

## Access Kafka:
```bash
# Create a topic
docker exec -it dlp-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic test-topic --partitions 1 --replication-factor 1

# List topics
docker exec -it dlp-kafka kafka-topics --bootstrap-server localhost:9092 --list
# Output: test-topic

# Delete test-topic
docker exec -it dlp-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic test-topic

# Check Kafka logs
docker logs dlp-kafka | tail -20
docker logs -f dlp-kafka

# Check Zookeeper logs
docker logs dlp-zookeeper | tail -20
```

### Terminal 1: Create a topic and start Consumer 
```bash
# Create a topic
docker exec -it dlp-kafka kafka-topics --bootstrap-server localhost:9092 --create --topic test-topic --partitions 1 --replication-factor 1

# Producer
#docker exec -it dlp-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic test-topic --from-beginning
docker exec -it dlp-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic test-topic --from-beginning --max-messages 10
```

### Terminal 2: Start Producer
```bash
docker exec -it dlp-kafka kafka-console-producer --broker-list localhost:9092 --topic test-topic
```


## Implement:
1. log-producer
2. log-consumer
3. api-gateway

## Testing Your System
### Terminal 1: Start producer
```bash
cd log-producer
mvn spring-boot:run
```

### Terminal 2: Start consumer
```bash
cd log-consumer
mvn spring-boot:run
```

### Terminal 3: Start gateway
```bash
cd api-gateway
mvn spring-boot:run
```

### Send Test Events
```bash
postman request POST 'http://localhost:8080/api/logs' \
  --header 'Content-Type: application/json' \
  --body '{
    "organizationId": "my-company",
    "level": "INFO",
    "message": "User logged in successfully",
    "source": "auth-service"
  }'
```

You should see:
1. The producer receives the request and publishes to Kafka
2. The consumer picks up the message and saves to PostgreSQL
3. All services log what they’re doing

### Load Testing Your System
Create a script that sends many requests quickly:
```bash
#!/bin/bash
echo "Sending 100 log events..."
for i in {1..100}; do
  curl -X POST http://localhost:8080/api/logs \
    -H "Content-Type: application/json" \
    -d "{
      \"organizationId\": \"org-$((i % 5))\",
      \"level\": \"INFO\",
      \"message\": \"Load test message $i\",
      \"source\": \"load-test\"
    }" &
    
  # Send 10 at a time to avoid overwhelming the system
  if (( i % 10 == 0 )); then
    wait
    echo "Sent $i requests..."
  fi
done

wait
echo "Load test complete!"
```

Run this and watch your system handle concurrent requests. \
You should see messages being processed in order within each organization ID.

## Monitoring Your System
### Check Health Endpoints
```http
# Check if services are healthy
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # Producer  
curl http://localhost:8082/actuator/health  # Consumer
```

### View Metrics
```http
# See detailed metrics
curl http://localhost:8081/actuator/metrics
curl http://localhost:8081/actuator/prometheus
```

## Experiment with Failures
### Test Database Failure Stop PostgreSQL and send requests:
```bash
docker compose stop postgres
# Send some requests - they should be retried
docker compose start postgres
# Previous requests should now be processed
```

### Test Kafka Failure
```bash
docker compose stop kafka
# Requests should queue up and circuit breaker should activate
docker compose start kafka
# Messages should be processed once Kafka is back
```

This demonstrates how distributed systems handle partial failures gracefully.


## Deployment and Production Readiness

### Complete System Deployment
- Package everything for production:
```bash
# Build all services
mvn clean package

# Create Docker images for each service
cd log-producer && docker build -t log-producer .
cd log-consumer && docker build -t log-consumer .
cd api-gateway && docker build -t api-gateway .
```

### Production Monitoring Setup
- Add Prometheus and Grafana to your docker-compose.yml:
```
 prometheus:
    image: prom/prometheus:latest
    ports:
      - “9090:9090”
      
  grafana:
    image: grafana/grafana:latest
    ports:
      - “3000:3000”
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
```
- Access Grafana at: http://localhost:3000
- (admin/admin) to see beautiful dashboards of your system metrics.

