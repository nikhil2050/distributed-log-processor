# Consumer Not Receiving Messages - Debugging Checklist

## **Quick Diagnosis**

### **Symptom: Producer says "success" but Consumer doesn't log anything**

Run these checks in order:

---

## **Phase 1: Verify Setup (30 seconds)**

- [ ] **Kafka running?**
  ```bash
  docker compose ps
  # Should show: kafka, postgres, zookeeper all UP
  ```

- [ ] **Consumer running?**
  ```bash
  lsof -i :8082
  # Should show something listening on port 8082
  ```

- [ ] **Producer running?**
  ```bash
  lsof -i :8081
  # Should show something listening on port 8081
  ```

---

## **Phase 2: Check Kafka Topic (1 minute)**

- [ ] **Topic exists?**
  ```bash
  docker exec kafka kafka-topics.sh --list --bootstrap-server kafka:9092
  # Should include: log-events
  ```

- [ ] **Messages in topic?**
  ```bash
  docker exec kafka kafka-console-consumer.sh \
    --bootstrap-server kafka:9092 \
    --topic log-events \
    --from-beginning \
    --max-messages 5 \
    --timeout-ms 1000
  # Should show JSON messages like: {"id":"...","level":"INFO",...}
  ```

---

## **Phase 3: Check Consumer Configuration (2 minutes)**

- [ ] **KafkaConsumerConfig exists?**
  ```bash
  ls log-consumer/src/main/java/com/nikhil/logprocessor/consumer/config/KafkaConsumerConfig.java
  # File should exist
  ```

- [ ] **LogEventConsumer has Acknowledgment?**
  ```bash
  grep -n "Acknowledgment" log-consumer/src/main/java/com/nikhil/logprocessor/consumer/service/LogEventConsumer.java
  # Should show imports and parameter
  ```

- [ ] **LogEventConsumer calls acknowledge()?**
  ```bash
  grep -n "acknowledgment.acknowledge()" log-consumer/src/main/java/com/nikhil/logprocessor/consumer/service/LogEventConsumer.java
  # Should show at least one matching line
  ```

- [ ] **@KafkaListener has containerFactory?**
  ```bash
  grep -A 3 "@KafkaListener" log-consumer/src/main/java/com/nikhil/logprocessor/consumer/service/LogEventConsumer.java
  # Should show: containerFactory = "kafkaListenerContainerFactory"
  ```

---

## **Phase 4: Check Consumer Logs (5 minutes)**

While consumer is running, look for these log messages:

### **✅ Good Signs (Should see these)**
```
Started LogConsumerApplication
DEBUG ... Received message from topic: log-events
INFO ... Successfully processed log event:
```

### **❌ Bad Signs (Should NOT see these)**
```
Failed to process message
NullPointerException
KafkaListenerEndpointRegistry
No bean named 'kafkaListenerContainerFactory'
```

---

## **Phase 5: Check Consumer Group Status (3 minutes)**

- [ ] **Consumer group exists?**
  ```bash
  docker exec kafka kafka-consumer-groups.sh \
    --bootstrap-server kafka:9092 \
    --list
  # Should include: log-consumer-group
  ```

- [ ] **Consumer group lag = 0?**
  ```bash
  docker exec kafka kafka-consumer-groups.sh \
    --bootstrap-server kafka:9092 \
    --group log-consumer-group \
    --describe
  # LAG column should be 0 (all messages consumed)
  ```

---

## **Phase 6: Check Database (2 minutes)**

- [ ] **Database running?**
  ```bash
  docker exec postgres psql -U loguser -d logprocessor -c "SELECT 1"
  # Should return: 1
  ```

- [ ] **Table exists?**
  ```bash
  docker exec postgres psql -U loguser -d logprocessor -c "\dt log_events"
  # Should show table exists
  ```

- [ ] **Data in table?**
  ```bash
  docker exec postgres psql -U loguser -d logprocessor -c "SELECT COUNT(*) FROM log_events"
  # Should show number > 0
  ```

---

## **Common Issues & Quick Fixes**

### **Issue: Consumer runs but no logs appear**

**Check:** Is consumer actually started?
```bash
# In consumer terminal, should see:
"Started LogConsumerApplication in X.XXX seconds"
```
**Fix:** Wait 5-10 seconds after starting, then send message

---

### **Issue: "No bean named 'kafkaListenerContainerFactory'"**

**Check:** Does KafkaConsumerConfig exist?
```bash
ls log-consumer/src/main/java/com/nikhil/logprocessor/consumer/config/KafkaConsumerConfig.java
```
**Fix:** Re-run mvn clean package, restart consumer

---

### **Issue: Consumer processes message but database empty**

**Check:** Is database connection working?
```bash
docker exec postgres psql -U loguser -d logprocessor -c "\dt"
```
**Fix:** Verify application.yml has correct datasource URL and credentials

---

### **Issue: "enable-auto-commit: false but no acknowledgment"**

**Check:** Does LogEventConsumer call acknowledge()?
```bash
grep "acknowledgment.acknowledge()" log-consumer/src/main/java/com/nikhil/logprocessor/consumer/service/LogEventConsumer.java
```
**Fix:** Add acknowledgment parameter and call acknowledge() after processing

---

## **Testing Sequence**

### **1. Send Message**
```bash
curl -X POST http://localhost:8080/api/logs \
  -H "Content-Type: application/json" \
  -d '{"organizationId":"test","level":"INFO","message":"test"}'
```

### **2. Watch Consumer Logs**
```
Expected: "Successfully processed log event: ..."
Actual:   (Check what you see)
```

### **3. Check Kafka Topic**
```bash
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic log-events \
  --from-beginning \
  --max-messages 1
```

### **4. Check Consumer Lag**
```bash
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --group log-consumer-group \
  --describe
# LAG should decrease from N to 0
```

### **5. Check Database**
```bash
docker exec postgres psql -U loguser -d logprocessor -c "SELECT COUNT(*) FROM log_events"
# Count should increase
```

---

## **Emergency Restart**

If things are stuck:

```bash
# 1. Stop all services
docker compose down
pkill -f "mvn spring-boot:run"

# 2. Clean consumer offset (use with caution!)
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --group log-consumer-group \
  --reset-offsets \
  --to-earliest \
  --execute \
  --topic log-events

# 3. Rebuild
cd distributed-log-processor
mvn clean package -DskipTests -pl log-consumer

# 4. Restart services
docker compose up -d
cd log-producer && mvn spring-boot:run
# (in another terminal)
cd log-consumer && mvn spring-boot:run
```

---

## **Success Criteria**

✅ **All of these should be true:**

- [ ] Consumer application starts without errors
- [ ] Message payload shows in Kafka topic
- [ ] "Successfully processed log event" appears in consumer logs
- [ ] Consumer group LAG shows 0
- [ ] New row appears in log_events table with correct data
- [ ] processed_at timestamp is populated

---

## **At-a-Glance Status**

Print this and check off as you go:

```
SETUP
  [ ] Kafka running
  [ ] Producer running (8081)
  [ ] Consumer running (8082)

KAFKA
  [ ] Topic "log-events" exists
  [ ] Messages in topic
  [ ] Consumer group exists

CONSUMER CODE
  [ ] KafkaConsumerConfig file exists
  [ ] LogEventConsumer has @Payload
  [ ] LogEventConsumer has Acknowledgment param
  [ ] LogEventConsumer calls acknowledge()
  [ ] @KafkaListener has containerFactory reference

LOGS
  [ ] No "No bean" errors
  [ ] No NullPointerException
  [ ] "Successfully processed" appears

DATABASE
  [ ] PostgreSQL running
  [ ] log_events table exists
  [ ] Data appears in table

FINAL TEST
  [ ] Send message via curl
  [ ] See "Successfully processed" log
  [ ] Data in database
  [ ] Consumer lag = 0
```

---

**If all checkmarks are complete, your consumer is working!** ✅

