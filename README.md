# Distributed Log Processing System

### Reference :
[SubStack | SDCourse | Java Day 1: Building Production-Ready Distributed Log Processing Infrastructure](https://sdcourse.substack.com/p/day-1-building-production-ready-distributed?r=64ai7k)

A production-ready distributed log processing system built with Spring Boot, Apache Kafka, Redis, and PostgreSQL. This system demonstrates key distributed system patterns including event-driven architecture, circuit breakers, distributed caching, and comprehensive observability.

![System Architecture Diagram](./DLP_SystemArchitecture.jpg?raw=true "System Architecture Diagram")

## 1.System Architecture 🏗

The system consists of three main services:

| Service          | Port | Desc                                                                    |
|------------------|------|-------------------------------------------------------------------------|
| **API Gateway**  | 8080 | Routes requests, handles rate limiting, and provides unified API access |
| **Log Producer** | 8081 | REST API that accepts log events and publishes them to Kafka            |
| **Log Consumer** | 8082 | Kafka consumer that processes events and stores them in PostgreSQL      |

### Infrastructure Components

| Component         | Port | Desc                                                     |
|-------------------|------|----------------------------------------------------------|
| **Apache Kafka**  | 9092 | Message streaming platform for event-driven architecture |
| **Redis**         | 6379 | Distributed caching and rate limiting                    |
| **PostgreSQL**    | 5432 | Persistent storage for processed log events              |
| **Prometheus**    | 9090 | Metrics collection and monitoring                        |
| **Grafana**       | 3000 | Metrics visualization and dashboards                     |

## 2. Quick Start 🚀

### Prerequisites

- Java 17+
- Maven 3.6+
- Docker and Docker Compose

### 1. Generate the System

If you haven't already, run the generation script:

```bash
chmod +x generate_system_files.sh
./generate_system_files.sh
cd distributed-log-processor
```

### 2. Start Infrastructure

```bash
./setup.sh
```

This will start all infrastructure services (Kafka, Redis, PostgreSQL, Prometheus, Grafana) and create necessary topics.

### 3. Start Application Services

Open three separate terminals and run:

```bash
# Terminal 1: Start Log Producer
cd log-producer
mvn spring-boot:run

# Terminal 2: Start Log Consumer
cd log-consumer
mvn spring-boot:run

# Terminal 3: Start API Gateway
cd api-gateway
mvn spring-boot:run
```

### 4. Verify the System

```bash
# Run integration tests
./integration-tests/system-integration-test.sh

# Run load tests
./load-test.sh
```
NOTE: Check Grafana dashboard at http://localhost:3000 for metrics.

## 3. Monitoring and Observability 📊

### Access Points

- **Grafana Dashboard**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **API Gateway Health**: http://localhost:8080/actuator/health
- **Producer Health**: http://localhost:8081/actuator/health
- **Consumer Health**: http://localhost:8082/actuator/health

### Workflow for Monitoring
**Application** → **Prometheus** (scrapes every 5s) → **Grafana** (visualizes) → You see metrics

### Prometheus
#### Step 1: Check if Services are Being Scraped

1. Go to http://localhost:9090
2. Click "Targets" (top menu)
3. You should see:
   - ✅ prometheus (UP)
   - ✅ api-gateway (UP)
   - ✅ log-producer (UP)
   - ✅ log-consumer (UP)

NOTE: If any show RED (DOWN), your service isn't running or metrics endpoint is wrong.

### Step 2: Connect Grafana to Prometheus
1. Go to http://localhost:3000
2. Click "Add Data Source" (left menu)
3. Select Prometheus
4. Set URL: http://localhost:9090
5. Click "Save & Test"

### Step 3: Create Dashboard
1. Click "Create Dashboard"
2. Click "Add Panel"
3. In Metrics field, paste a query: 
```promql
up{job="log-producer"} == 0 or up{job="log-consumer"} == 0 or up{job="api-gateway"} == 0

# What it does:
# Checks if ANY of these 3 services are DOWN
# Returns 1 if service is DOWN (alert triggers)
# Returns 0 if all services are UP (no alert)

# Breakdown:
# up{job="log-producer"} = Is log-producer UP? (1=yes, 0=no)
# == 0 = IF it's DOWN
# or = OR if any other service is down
# Result: ALERT if ANY service goes down
```
4. Click "Save"

### Prometheus Sample Dashboard Queries
Try these PromQL queries in Prometheus dashboard:

- Service Health (all services UP/DOWN):
```promql
up{job=~"api-gateway|log-producer|log-consumer"}
```
- JVM Memory Usage:
```promql
jvm_memory_used_bytes / jvm_memory_max_bytes * 100
```

### Unverified Queries 🧪 
- Request Rate (requests/sec):
```promql
rate(http_requests_total[1m])
```
- Error Rate (% of failed requests):
```promql
rate(http_requests_errors_total[5m]) / rate(http_requests_total[5m])
```
- Kafka Messages Lag:
```promql
kafka_consumer_records_lag_max
```

```promql
up{job="log-producer"} == 0 or up{job="log-consumer"} == 0 or up{job="api-gateway"} == 0
```


### Key Metrics

- `log_events_received_total`: Total log events received by producer
- `log_events_processed_total`: Total log events processed by consumer
- `log_events_errors_total`: Total processing errors
- `log_event_processing_time`: Time to process individual events

## 4. API Usage 🔧

### Submit Log Event

```bash
curl -X POST http://localhost:8080/api/logs \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "my-org",
    "level": "INFO",
    "message": "Application started successfully",
    "source": "my-service"
  }'
```

### Response

```json
{
  "status": "success",
  "id": "uuid-generated-id",
  "message": "Log event queued for processing"
}
```

## 5. Architecture Patterns 🏛

### 1. Event-Driven Architecture
- **Pattern**: Decoupled services communicate via events
- **Implementation**: Kafka topics with persistent messaging
- **Benefits**: High availability, natural backpressure, replay capability

### 2. Circuit Breaker Pattern
- **Pattern**: Prevents cascade failures in distributed systems
- **Implementation**: Resilience4j circuit breakers
- **Benefits**: System stability, graceful degradation, automatic recovery

### 3. API Gateway Pattern
- **Pattern**: Single entry point for client requests
- **Implementation**: Spring Cloud Gateway with reactive routing
- **Benefits**: Centralized cross-cutting concerns, simplified client logic

### 4. Cache-Aside Pattern
- **Pattern**: Application manages cache alongside database
- **Implementation**: Redis with Spring Cache
- **Benefits**: Improved performance, reduced database load

### 5. Health Check Pattern
- **Pattern**: Multi-level health verification
- **Implementation**: Spring Boot Actuator with custom health indicators
- **Benefits**: Early failure detection, automatic recovery

## 6. Production Considerations 🔥

### 6.1 Performance Characteristics

- **Throughput**: 1,000+ events/second per producer instance
- **Latency**: 99th percentile under 100ms
- **Scalability**: Horizontal scaling via Kafka partitions

### 6.2 Scaling Strategy

1. **Producer Scaling**: Add more producer instances behind load balancer
2. **Consumer Scaling**: Increase consumer instances (max = partition count)
3. **Database Scaling**: Implement read replicas and connection pooling
4. **Cache Scaling**: Redis cluster for high availability

### 6.3 Error Handling

- **Retry Logic**: Exponential backoff with max 3 attempts
- **Dead Letter Queue**: Failed messages routed to error topic
- **Circuit Breakers**: Automatic failure isolation
- **Health Checks**: Proactive failure detection

### 6.4 Security Considerations

- Input validation on all API endpoints
- Rate limiting to prevent abuse
- Health check endpoints exposed for monitoring
- Database credentials externalized via environment variables

## 7. Testing 🧪

### 7.1 Integration Tests

```bash
./integration-tests/system-integration-test.sh
```

Tests include:
- Service health verification
- End-to-end log processing
- Metrics endpoint availability

### 7.2 Load Testing

```bash
./load-test.sh
```

Generates 100 concurrent log events to test system under load.

### 7.3 Manual Testing

```bash
# Submit various log levels
curl -X POST http://localhost:8080/api/logs \
  -H "Content-Type: application/json" \
  -d '{"organizationId": "test", "level": "ERROR", "message": "Test error", "source": "manual-test"}'
```

## 8.️ Development 🛠

### Building

```bash
mvn clean install
```

### Running Tests

```bash
mvn test
```

### Configuration

Key configuration files:
- `application.yml`: Service-specific configuration
- `docker-compose.yml`: Infrastructure setup
- `prometheus.yml`: Metrics collection configuration

## 9. System Lifecycle 🔄

### Startup Sequence 🌅

1. Infrastructure services (Kafka, Redis, PostgreSQL)
2. Log Consumer (to create consumer group)
3. Log Producer (to handle incoming requests)
4. API Gateway (to route external traffic)

### Shutdown Sequence 🌄

1. API Gateway (stop accepting new requests)
2. Log Producer (finish processing current requests)
3. Log Consumer (finish processing current messages)
4. Infrastructure services

## 10. Scaling to Production 📈

This system architecture patterns are used by major tech companies:

- **Netflix**: Event-driven microservices with Kafka
- **Uber**: Real-time data processing with similar patterns
- **Amazon**: Distributed systems with circuit breakers and health checks

### Next Steps for Production

1. **Authentication**: Implement JWT or OAuth2
2. **Authorization**: Role-based access control
3. **SSL/TLS**: Encrypt all network communication
4. **Monitoring**: Enhanced alerting and dashboards
5. **Deployment**: Kubernetes orchestration
6. **Backup**: Automated backup strategies

## 11. Focus areas for enhancement 🔮

- Additional consumer patterns (batch processing)
- Advanced monitoring (distributed tracing)
- Performance optimizations (connection pooling)
- Security enhancements (authentication)

## 12. Learning Resources 📚
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Microservices Patterns](https://microservices.io/patterns/)

---

**Architecture Insight**: This system demonstrates that scalable distributed systems are built on predictable failure rather than perfect reliability. Every component assumes others will fail and provides mechanisms for graceful degradation and automatic recovery.

