# Consumer Not Receiving Messages - Fixed! 🎉

## **What Was Wrong**

Your consumer wasn't receiving messages because:

1. ❌ **Manual Commit Not Implemented**: Configuration was set to manual acknowledgment (`enable-auto-commit: false`), but the consumer wasn't acknowledging messages
2. ❌ **No Kafka Consumer Configuration Bean**: Missing explicit Kafka consumer configuration
3. ❌ **No Message Tracking**: Couldn't track topic, partition, and offset information

## **What Was Fixed**

### **1. Created KafkaConsumerConfig** (NEW)
**File:** `log-consumer/src/main/java/com/nikhil/logprocessor/consumer/config/KafkaConsumerConfig.java`

Features:
- ✅ Explicit consumer factory configuration
- ✅ Enable Kafka listener annotation processing with `@EnableKafka`
- ✅ Configure `ConcurrentKafkaListenerContainerFactory` with manual acknowledge mode
- ✅ Set concurrency to 3 for parallel message processing
- ✅ Proper session timeout and max poll records

### **2. Enhanced LogEventConsumer**
**File:** `log-consumer/src/main/java/com/nikhil/logprocessor/consumer/service/LogEventConsumer.java`

Changes:
- ✅ Added `Acknowledgment` parameter for manual commit
- ✅ Added Kafka headers (`@Header`) to track topic, partition, offset
- ✅ Properly acknowledge messages AFTER successful processing
- ✅ Don't acknowledge on error (allows retry)
- ✅ Enhanced logging with partition/offset info
- ✅ Reference to `kafkaListenerContainerFactory` bean

### **3. Updated application.yml**
Added `poll-timeout: 3000` for better timeout handling

---

## **How It Works Now**

```
Producer sends message to Kafka
         ↓
[Kafka Topic: log-events]
         ↓
Consumer receives message with headers:
  - Topic: log-events
  - Partition: 0 (or N)
  - Offset: 123 (or sequence number)
         ↓
LogEventConsumer.consumeLogEvent() processes:
  1. Parse JSON to LogEvent object
  2. Set processedAt timestamp
  3. Save to PostgreSQL
  4. Acknowledge message (manual commit)
         ↓
Kafka marks message as consumed
```

---

## **Testing the Fix**

### **Step 1: Rebuild the Project**
```bash
cd distributed-log-processor

# Clean and rebuild consumer
mvn clean package -DskipTests -pl log-consumer
```

### **Step 2: Start Services in Order**

**Terminal 1: Start Kafka & Dependencies**
```bash
docker compose up -d
# Verify: docker compose ps
```

**Terminal 2: Start Producer**
```bash
cd log-producer
mvn spring-boot:run
```

Wait for: `Started LogProducerApplication`

**Terminal 3: Start Consumer** (This is the important one now!)
```bash
cd log-consumer
mvn spring-boot:run
```

Look for these log lines:
```
2026-05-13 04:30:00.123 INFO ... - Started LogConsumerApplication
2026-05-13 04:30:00.456 INFO ... - Log Consumer started successfully
```

**Terminal 4: Start Gateway**
```bash
cd api-gateway
mvn spring-boot:run
```

### **Step 3: Send a Test Message**

```bash
curl -X POST http://localhost:8080/api/logs \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "test-org",
    "level": "INFO",
    "message": "Testing consumer fix",
    "source": "test-service"
  }'
```

**Expected Response (200 OK):**
```json
{
  "status": "success",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Log event queued for processing"
}
```

### **Step 4: Check Consumer Logs**

You should see in **Terminal 3** (Consumer):

```
04:31:23.123 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] DEBUG - Received message from topic: log-events, partition: 0, offset: 42
04:31:23.456 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  - Successfully processed log event: 550e8400-e29b-41d4-a716-446655440000 (Topic: log-events, Partition: 0, Offset: 42)
```

### **Step 5: Verify in Database (Optional)**

Connect to PostgreSQL:
```bash
# Via Docker
docker exec -it postgres psql -U loguser -d logprocessor

# Then run:
SELECT * FROM log_events ORDER BY processed_at DESC LIMIT 5;
```

You should see your log event with:
- ✅ Correct `organizationId`, `level`, `message`, `source`
- ✅ Auto-generated `id`
- ✅ Populated `processed_at` timestamp
- ✅ Database `timestamp` preserved

---

## **Debugging Checklist**

- [ ] Consumer application started without errors
- [ ] Kafka broker is running: `docker compose ps` shows `kafka` running
- [ ] Producer is running on port 8081
- [ ] Consumer is running on port 8082
- [ ] Log message appears in consumer terminal
- [ ] Database entry exists

---

## **If Still Not Receiving Messages**

### **1. Check Kafka Connection**
```bash
# Verify Kafka is running
docker exec kafka kafka-topics.sh --list --bootstrap-server kafka:9092

# Should show: log-events (among others)
```

### **2. Check Consumer Group**
```bash
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --list

# Should show: log-consumer-group
```

### **3. Check Topic Messages**
```bash
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic log-events \
  --from-beginning \
  --max-messages 5
```

### **4. Enable Debug Logging**

Add to `log-consumer/src/main/resources/application.yml`:
```yaml
logging:
  level:
    org.springframework.kafka: DEBUG
    org.apache.kafka: DEBUG
    com.nikhil.logprocessor: DEBUG
```

Then restart consumer and check for detailed logs.

---

## **Complete Message Flow Diagram**

```
┌──────────────────┐
│  API Client      │
│  (curl/postman)  │
└────────┬─────────┘
         │ POST /api/logs
         ▼
┌──────────────────────────┐
│   API Gateway (8080)     │
│  Spring Cloud Gateway    │
└────────┬─────────────────┘
         │ Route to :8081
         ▼
┌──────────────────────────┐
│ Log Producer (8081)      │
│ - LogEventController     │
│ - KafkaProducerService   │
└────────┬─────────────────┘
         │ Send JSON to Kafka
         ▼
     ╔════════════════════╗
     ║  KAFKA CLUSTER     ║
     ║  Topic: log-events ║
     ║  Partition: 0      ║
     ║  Offset: 42        ║
     ╚═────────┬──────────╝
              │
         ╭────┴────╮
         │ CONSUMED │ (in-memory queue)
         ╰────┬────╯
         ▼
┌──────────────────────────────────┐
│  Log Consumer (8082)             │
│  LogEventConsumer                │
│  - @KafkaListener                │
│  - Manual Acknowledgment         │
│  - LogEventRepository.save()     │
└────────┬─────────────────────────┘
         │ Save with JPA/Hibernate
         ▼
┌──────────────────────────────┐
│  PostgreSQL Database         │
│  Table: log_events           │
│  - id                        │
│  - organization_id           │
│  - level                     │
│  - message                   │
│  - source                    │
│  - timestamp                 │
│  - processed_at              │
└──────────────────────────────┘
```

---

## **Key Improvements**

| Before | After |
|--------|-------|
| No acknowledgment | ✅ Manual acknowledge on success |
| No consumer config bean | ✅ Explicit KafkaConsumerConfig |
| Couldn't track headers | ✅ Topic, partition, offset logged |
| Errors acknowledged | ✅ Errors not acknowledged (allow retry) |
| No concurrency | ✅ Set to 3 concurrent listeners |
| Limited debugging | ✅ Detailed log output with metadata |

---

## **Files Modified**

1. ✅ `log-consumer/config/KafkaConsumerConfig.java` (NEW)
2. ✅ `log-consumer/service/LogEventConsumer.java` (UPDATED)
3. ✅ `log-consumer/resources/application.yml` (UPDATED)

---

**Try the fixed setup and you should see messages flowing through immediately!** 🚀

