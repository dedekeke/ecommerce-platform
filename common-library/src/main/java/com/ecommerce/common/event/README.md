

# Event-Driven Architecture Documentation

This package provides a complete event-driven architecture for the e-commerce platform using Apache Kafka.

## Overview

The event infrastructure enables asynchronous, decoupled communication between microservices through domain events. This architecture provides:

- **Loose coupling**: Services don't need direct dependencies
- **Scalability**: Events can be processed asynchronously at scale
- **Resilience**: Retry logic and DLQ for failed messages
- **Audit trail**: All events are persisted in Kafka
- **Real-time processing**: Services react to events as they occur

## Architecture

```
┌──────────────┐     publish    ┌───────────────┐     consume    ┌──────────────┐
│   Service A  │ ──────────────> │  Kafka Topic  │ ────────────> │  Service B   │
│  (Producer)  │                 │  (order-events)│                │  (Consumer)  │
└──────────────┘                 └───────────────┘                └──────────────┘
                                         │
                                         │ (on failure after retries)
                                         ▼
                                 ┌───────────────┐
                                 │   DLQ Topic   │
                                 │ (order-events │
                                 │     -dlq)     │
                                 └───────────────┘
```

## Components

### 1. Event Schemas (`event/`)

Base class and concrete event implementations:

- **BaseEvent**: Parent class for all events with common metadata
- **OrderCreatedEvent**: Published when a new order is created
- **OrderStatusUpdatedEvent**: Published when order status changes
- **PaymentCompletedEvent**: Published when payment succeeds
- **PaymentFailedEvent**: Published when payment fails
- **InventoryReservedEvent**: Published when inventory is reserved
- **InventoryReleasedEvent**: Published when reservation is released
- **ProductCreatedEvent**: Published when a product is created
- **ProductUpdatedEvent**: Published when a product is updated
- **UserCreatedEvent**: Published when a user is created

### 2. Event Publisher (`event/publisher/`)

**EventPublisher** - Central publisher for all events:
- Automatic correlation ID propagation
- Distributed tracing integration
- Error handling and logging
- Async and sync publishing options

### 3. Event Consumer (`event/consumer/`)

**BaseEventConsumer** - Base class for all consumers:
- Automatic deserialization
- Distributed tracing
- MDC context setup
- Manual acknowledgment support

### 4. Kafka Configuration (`event/config/`)

**KafkaEventConfig** - Complete Kafka setup:
- Topic definitions with partitioning
- Producer/Consumer factory configuration
- Error handling with exponential backoff
- Dead Letter Queue configuration

### 5. Dead Letter Queue (`event/dlq/`)

**DeadLetterQueuePublisher** - DLQ handling:
- Automatic DLQ publishing after retry exhaustion
- Error metadata capture
- Stack trace preservation

### 6. Metrics (`event/metrics/`)

**KafkaMetricsConfig** - Prometheus metrics:
- Producer/Consumer performance metrics
- Message throughput tracking
- Error rate monitoring

### 7. Utilities (`event/util/`)

**EventSerializationUtil** - Serialization helpers:
- Type-safe serialization/deserialization
- Event validation
- Event conversion

## Kafka Topics

| Topic | Partitions | Use Case | Retention |
|-------|-----------|----------|-----------|
| `order-events` | 3 | Order lifecycle events | 7 days |
| `payment-events` | 3 | Payment transactions | 7 days |
| `inventory-events` | 3 | Stock reservations/releases | 7 days |
| `product-events` | 3 | Product catalog changes | 7 days |
| `user-events` | 2 | User management | 7 days |
| `*-dlq` | 1 | Failed messages | 30 days |

## Usage

### Publishing Events

#### Option 1: Using EventPublisher (Recommended)

```java
@Service
public class OrderService {

    private final EventPublisher eventPublisher;

    public Order createOrder(CreateOrderRequest request) {
        // Create order
        Order order = // ... create order logic

        // Build event
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getUserId())
                .items(mapToEventItems(order.getItems()))
                .total(order.getTotal())
                .status(order.getStatus().name())
                .build();

        // Initialize metadata
        event.initializeMetadata("ORDER_CREATED", "order-service");

        // Publish event (async)
        eventPublisher.publishEvent(
            KafkaEventConfig.TOPIC_ORDER_EVENTS,
            event
        );

        return order;
    }
}
```

#### Option 2: Synchronous Publishing

```java
// When you need to ensure event is published before proceeding
try {
    SendResult<String, String> result = eventPublisher.publishEventSync(
        KafkaEventConfig.TOPIC_ORDER_EVENTS,
        event
    );
    log.info("Event published at offset: {}", result.getRecordMetadata().offset());
} catch (Exception e) {
    // Handle publishing failure
    log.error("Failed to publish event", e);
}
```

### Consuming Events

#### Step 1: Create Event Consumer

```java
@Component
public class OrderCreatedEventConsumer extends BaseEventConsumer<OrderCreatedEvent> {

    private final InventoryService inventoryService;
    private final NotificationService notificationService;

    public OrderCreatedEventConsumer(
            ObjectMapper objectMapper,
            Tracer tracer,
            InventoryService inventoryService,
            NotificationService notificationService) {
        super(objectMapper, tracer, OrderCreatedEvent.class);
        this.inventoryService = inventoryService;
        this.notificationService = notificationService;
    }

    @Override
    protected void handleEvent(OrderCreatedEvent event) throws Exception {
        log.info("Processing order created event: orderId={}", event.getOrderId());

        // Reserve inventory
        inventoryService.reserveForOrder(event.getOrderId(), event.getItems());

        // Send notification
        notificationService.sendOrderConfirmation(event.getCustomerId(), event);

        log.info("Order created event processed successfully: orderId={}", event.getOrderId());
    }
}
```

#### Step 2: Create Kafka Listener

```java
@Component
public class OrderEventListener {

    private final OrderCreatedEventConsumer orderCreatedEventConsumer;

    @KafkaListener(
        topics = KafkaEventConfig.TOPIC_ORDER_EVENTS,
        groupId = "${spring.application.name}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void listenOrderEvents(String message, Acknowledgment acknowledgment) {
        // Delegate to consumer
        orderCreatedEventConsumer.processEvent(message, acknowledgment);
    }
}
```

### Event Schema Design

All events should include:

```java
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MyCustomEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    // Business-specific fields
    private String entityId;
    private String status;
    // ... more fields

    @Override
    public String getPartitionKey() {
        // Use entity ID for consistent partitioning
        return entityId;
    }
}
```

**Important**: Always override `getPartitionKey()` to ensure related events go to the same partition.

## Partitioning Strategy

Events with the same partition key are guaranteed to be processed in order:

- **Order events**: Partitioned by `orderId`
- **Payment events**: Partitioned by `orderId`
- **Inventory events**: Partitioned by `orderId`
- **Product events**: Partitioned by `productId`
- **User events**: Partitioned by `auth0Id`

This ensures:
- All events for the same order are processed in sequence
- No race conditions for order state updates
- Consistent event ordering

## Error Handling & Retry

### Automatic Retry

The system automatically retries failed message processing with exponential backoff:

1. **Initial delay**: 1 second
2. **Backoff multiplier**: 2x
3. **Max retry time**: 30 seconds
4. **Retry pattern**: 1s, 2s, 4s, 8s, 16s

### Non-Retryable Exceptions

These exceptions skip retry and go directly to DLQ:
- `IllegalArgumentException` - Invalid event data
- `JsonProcessingException` - Malformed JSON

### Dead Letter Queue (DLQ)

After retry exhaustion, messages are sent to DLQ with:
- Original message content
- Error details and stack trace
- Original topic and offset
- Timestamp of failure
- All original headers

**Monitoring DLQ**: Set up alerts for messages in DLQ topics.

## Configuration

### Application Properties

```yaml
# Kafka Configuration
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
    producer:
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
    consumer:
      group-id: ${spring.application.name}
      auto-offset-reset: earliest
      enable-auto-commit: false

# Enable metrics
management:
  metrics:
    export:
      prometheus:
        enabled: true
```

### Topic Creation

Topics are automatically created on application startup with configured partitions and retention.

For manual creation:

```bash
kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --partitions 3 \
  --replication-factor 1
```

## Monitoring

### Prometheus Metrics

The following metrics are exposed:

**Producer Metrics:**
- `kafka_producer_record_send_total` - Total records sent
- `kafka_producer_record_error_total` - Total send errors
- `kafka_producer_record_retry_total` - Total retries
- `kafka_producer_request_latency_avg` - Average request latency

**Consumer Metrics:**
- `kafka_consumer_fetch_manager_records_consumed_total` - Records consumed
- `kafka_consumer_fetch_manager_records_lag` - Consumer lag
- `kafka_consumer_coordinator_commit_latency_avg` - Commit latency

**Custom Metrics:**
- `event_published_total` - Events published by type
- `event_consumed_total` - Events consumed by type
- `event_processing_duration` - Event processing time
- `event_dlq_total` - Events sent to DLQ

### Grafana Dashboards

Create dashboards to monitor:
1. **Message throughput** (messages/sec by topic)
2. **Consumer lag** (messages behind)
3. **Error rates** (failed vs successful)
4. **Processing latency** (p50, p95, p99)
5. **DLQ message count** (should be near zero)

## Best Practices

### 1. Event Design

✅ **DO:**
- Keep events immutable
- Include complete data (avoid lookups in consumers)
- Use semantic versioning for schema evolution
- Add metadata (timestamp, correlation ID, etc.)
- Make events self-descriptive

❌ **DON'T:**
- Include sensitive data (passwords, tokens)
- Make events too large (>1MB)
- Use mutable objects
- Forget to set partition keys

### 2. Publishing

✅ **DO:**
- Use async publishing for better performance
- Handle publishing failures gracefully
- Include correlation IDs for tracing
- Log event publishing for audit

❌ **DON'T:**
- Block on sync publish in request path
- Publish events in transactions (use transactional outbox pattern)
- Publish partial events

### 3. Consuming

✅ **DO:**
- Make consumers idempotent (handle duplicates)
- Process events quickly (avoid long operations)
- Use manual acknowledgment for critical events
- Implement proper error handling

❌ **DON'T:**
- Modify event data
- Assume event order across partitions
- Ignore processing failures
- Do synchronous HTTP calls in consumers (use async)

### 4. Monitoring

✅ **DO:**
- Monitor consumer lag constantly
- Set up DLQ alerts
- Track processing duration
- Monitor partition rebalancing

❌ **DON'T:**
- Ignore DLQ messages
- Let consumer lag grow unbounded
- Skip metric collection

## Troubleshooting

### Consumer Lag Growing

**Symptoms**: Consumer lag increasing over time

**Causes**:
- Consumer processing too slow
- Not enough consumer threads
- Network issues

**Solutions**:
1. Increase consumer concurrency: `factory.setConcurrency(5);`
2. Optimize event processing logic
3. Scale consumer instances horizontally
4. Check for slow database queries

### Messages Going to DLQ

**Symptoms**: Messages appearing in DLQ topics

**Investigation**:
1. Check DLQ message for error details
2. Look at exception stack trace
3. Verify event schema is correct
4. Check for business logic errors

**Resolution**:
1. Fix the underlying issue
2. Replay messages from DLQ
3. Consider schema evolution

### Duplicate Events

**Symptoms**: Same event processed multiple times

**Causes**:
- Consumer crashed before acknowledgment
- Rebalancing during processing
- Network issues causing retries

**Solutions**:
1. Implement idempotent consumers
2. Use deduplication keys (event ID)
3. Check for duplicate processing in business logic

## Testing

### Unit Testing Event Publisher

```java
@Test
void shouldPublishOrderCreatedEvent() throws Exception {
    // Given
    OrderCreatedEvent event = createTestEvent();

    // When
    CompletableFuture<SendResult<String, String>> future =
        eventPublisher.publishEvent(TOPIC_ORDER_EVENTS, event);

    // Then
    SendResult<String, String> result = future.get();
    assertThat(result.getRecordMetadata().topic()).isEqualTo(TOPIC_ORDER_EVENTS);
    assertThat(result.getRecordMetadata().partition()).isGreaterThanOrEqualTo(0);
}
```

### Integration Testing with Testcontainers

```java
@SpringBootTest
@Testcontainers
class EventIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:latest")
    );

    @Test
    void shouldPublishAndConsumeEvent() {
        // Test publishing and consuming
    }
}
```

## Event Catalog

| Event | Topic | Triggers | Consumers |
|-------|-------|----------|-----------|
| OrderCreatedEvent | order-events | Order placement | Inventory, Payment, Notification |
| OrderStatusUpdatedEvent | order-events | Status change | Notification, Analytics |
| PaymentCompletedEvent | payment-events | Payment success | Order, Notification |
| PaymentFailedEvent | payment-events | Payment failure | Order, Inventory |
| InventoryReservedEvent | inventory-events | Stock reservation | Order |
| InventoryReleasedEvent | inventory-events | Reservation release | Product, Analytics |
| ProductCreatedEvent | product-events | Product creation | Search, Cache |
| ProductUpdatedEvent | product-events | Product update | Search, Cache |
| UserCreatedEvent | user-events | User registration | Notification, Analytics |

## Schema Evolution

When evolving event schemas:

1. **Add new optional fields**: Safe, backward compatible
2. **Remove fields**: Dangerous, may break consumers
3. **Change field types**: Not compatible, create new event version
4. **Rename fields**: Not compatible, create new event version

**Recommended approach**: Create versioned events (v1, v2) and support both during transition.

## Performance Tuning

### Producer Optimization

```yaml
spring:
  kafka:
    producer:
      batch-size: 32768  # Increase batch size
      linger-ms: 20      # Wait 20ms to batch messages
      compression-type: snappy
      buffer-memory: 67108864  # 64MB buffer
```

### Consumer Optimization

```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 500  # Process more records per poll
      fetch-min-bytes: 50000  # Fetch larger batches
      fetch-max-wait-ms: 500  # Wait up to 500ms for data
```

## Security

### Authentication (Future Enhancement)

```yaml
spring:
  kafka:
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: PLAIN
      sasl.jaas.config: |
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_USERNAME}"
        password="${KAFKA_PASSWORD}";
```

### Encryption

- Use SSL/TLS for data in transit
- Consider encrypting sensitive event fields
- Implement key rotation policies

## Summary

This event infrastructure provides:
- ✅ Complete event-driven architecture
- ✅ Automatic retry with exponential backoff
- ✅ Dead Letter Queue for failed messages
- ✅ Distributed tracing integration
- ✅ Prometheus metrics
- ✅ Type-safe event handling
- ✅ Partitioning for guaranteed ordering
- ✅ Comprehensive error handling

For questions or issues, refer to the inline documentation or check the logs.
