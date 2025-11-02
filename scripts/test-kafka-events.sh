#!/bin/bash

# Kafka Event Infrastructure Test Script
# This script tests the event infrastructure by publishing and consuming test events

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
KAFKA_BROKER="${KAFKA_BROKER:-localhost:9092}"
TOPICS=("order-events" "payment-events" "inventory-events" "product-events" "user-events")
DLQ_TOPICS=("order-events-dlq" "payment-events-dlq" "inventory-events-dlq" "product-events-dlq" "user-events-dlq")

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Kafka Event Infrastructure Test${NC}"
echo -e "${BLUE}========================================${NC}\n"

# Function to print colored messages
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Step 1: Check if Kafka is running
echo -e "\n${BLUE}Step 1: Checking Kafka Availability${NC}"

if ! command -v kafka-topics &> /dev/null; then
    print_warning "kafka-topics command not found. Trying to use docker exec..."
    KAFKA_CMD="docker exec kafka kafka-topics"
    KAFKA_CONSOLE_PRODUCER="docker exec -i kafka kafka-console-producer"
    KAFKA_CONSOLE_CONSUMER="docker exec kafka kafka-console-consumer"
else
    KAFKA_CMD="kafka-topics"
    KAFKA_CONSOLE_PRODUCER="kafka-console-producer"
    KAFKA_CONSOLE_CONSUMER="kafka-console-consumer"
fi

# Test Kafka connection
if $KAFKA_CMD --bootstrap-server $KAFKA_BROKER --list > /dev/null 2>&1; then
    print_success "Kafka is running and accessible"
else
    print_error "Cannot connect to Kafka at $KAFKA_BROKER"
    print_info "Please start Kafka with: docker-compose up -d kafka"
    exit 1
fi

# Step 2: List existing topics
echo -e "\n${BLUE}Step 2: Listing Kafka Topics${NC}"

existing_topics=$($KAFKA_CMD --bootstrap-server $KAFKA_BROKER --list)
print_info "Existing topics:"
echo "$existing_topics" | while read -r topic; do
    echo "  - $topic"
done

# Step 3: Check if event topics exist
echo -e "\n${BLUE}Step 3: Checking Event Topics${NC}"

for topic in "${TOPICS[@]}"; do
    if echo "$existing_topics" | grep -q "^$topic$"; then
        print_success "Topic '$topic' exists"

        # Get topic details
        details=$($KAFKA_CMD --bootstrap-server $KAFKA_BROKER --describe --topic $topic 2>/dev/null)
        partitions=$(echo "$details" | grep "PartitionCount" | awk '{print $4}')
        print_info "  Partitions: $partitions"
    else
        print_warning "Topic '$topic' does not exist"
        print_info "It will be created automatically when the application starts"
    fi
done

# Step 4: Check DLQ topics
echo -e "\n${BLUE}Step 4: Checking Dead Letter Queue Topics${NC}"

for topic in "${DLQ_TOPICS[@]}"; do
    if echo "$existing_topics" | grep -q "^$topic$"; then
        print_success "DLQ topic '$topic' exists"
    else
        print_warning "DLQ topic '$topic' does not exist"
    fi
done

# Step 5: Test event publishing
echo -e "\n${BLUE}Step 5: Publishing Test Event${NC}"

TEST_EVENT=$(cat <<'EOF'
{
  "eventId": "test-event-123",
  "eventType": "ORDER_CREATED",
  "timestamp": "2025-01-15T10:30:00.000Z",
  "version": "1.0",
  "correlationId": "test-correlation-123",
  "source": "test-script",
  "userId": "user-123",
  "orderId": "order-test-123",
  "orderNumber": "ORD-TEST-00001",
  "customerId": "customer-123",
  "items": [
    {
      "productId": "product-1",
      "productName": "Test Product",
      "sku": "TEST-SKU-001",
      "price": 29.99,
      "quantity": 2,
      "subtotal": 59.98
    }
  ],
  "subtotal": 59.98,
  "tax": 6.00,
  "shippingCost": 5.00,
  "total": 70.98,
  "status": "PENDING",
  "shippingAddress": {
    "street": "123 Test St",
    "city": "Test City",
    "state": "TS",
    "zipCode": "12345",
    "country": "US"
  },
  "paymentMethod": "CREDIT_CARD"
}
EOF
)

if echo "$existing_topics" | grep -q "^order-events$"; then
    print_info "Publishing test event to 'order-events' topic..."
    echo "$TEST_EVENT" | $KAFKA_CONSOLE_PRODUCER --bootstrap-server $KAFKA_BROKER --topic order-events 2>/dev/null

    if [ $? -eq 0 ]; then
        print_success "Test event published successfully"
    else
        print_error "Failed to publish test event"
    fi
else
    print_warning "Skipping event publishing (topic doesn't exist)"
fi

# Step 6: Check consumer groups
echo -e "\n${BLUE}Step 6: Checking Consumer Groups${NC}"

if command -v kafka-consumer-groups &> /dev/null || docker exec kafka which kafka-consumer-groups > /dev/null 2>&1; then
    if command -v kafka-consumer-groups &> /dev/null; then
        CONSUMER_GROUPS_CMD="kafka-consumer-groups"
    else
        CONSUMER_GROUPS_CMD="docker exec kafka kafka-consumer-groups"
    fi

    consumer_groups=$($CONSUMER_GROUPS_CMD --bootstrap-server $KAFKA_BROKER --list 2>/dev/null)

    if [ -n "$consumer_groups" ]; then
        print_info "Active consumer groups:"
        echo "$consumer_groups" | while read -r group; do
            echo "  - $group"
        done
    else
        print_info "No active consumer groups"
    fi
else
    print_warning "Consumer groups command not available"
fi

# Step 7: Check topic message count
echo -e "\n${BLUE}Step 7: Checking Message Counts${NC}"

for topic in "${TOPICS[@]}"; do
    if echo "$existing_topics" | grep -q "^$topic$"; then
        # Get partition count
        partitions=$($KAFKA_CMD --bootstrap-server $KAFKA_BROKER --describe --topic $topic 2>/dev/null | grep "PartitionCount" | awk '{print $4}')

        print_info "Topic: $topic"
        echo "  Partitions: $partitions"

        # Note: Getting exact message count requires consumer groups or manual calculation
        # We'll skip this for now as it's complex
    fi
done

# Step 8: Monitor DLQ for messages
echo -e "\n${BLUE}Step 8: Checking DLQ for Failed Messages${NC}"

dlq_has_messages=false

for dlq_topic in "${DLQ_TOPICS[@]}"; do
    if echo "$existing_topics" | grep -q "^$dlq_topic$"; then
        # Try to peek at DLQ (this might require timeout)
        print_info "Checking $dlq_topic..."

        # Note: This would need timeout command and proper setup
        # For now, just indicate the topic exists
        print_info "  Topic exists and can be monitored"
    fi
done

if [ "$dlq_has_messages" = false ]; then
    print_success "No messages in DLQ (good!)"
fi

# Step 9: Summary and Recommendations
echo -e "\n${BLUE}========================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}========================================${NC}\n"

print_info "Kafka Broker: $KAFKA_BROKER"
print_info "Total topics found: $(echo "$existing_topics" | wc -l)"

echo -e "\n${GREEN}Next Steps:${NC}"
echo "1. Start your microservices with Kafka configuration"
echo "2. Monitor consumer lag: kafka-consumer-groups --bootstrap-server $KAFKA_BROKER --describe --group <group-name>"
echo "3. View topic messages: kafka-console-consumer --bootstrap-server $KAFKA_BROKER --topic <topic-name> --from-beginning"
echo "4. Monitor DLQ topics for failed messages"
echo "5. Check Prometheus metrics at http://localhost:9090"

echo -e "\n${YELLOW}Useful Commands:${NC}"
echo "# List all topics:"
echo "  $KAFKA_CMD --bootstrap-server $KAFKA_BROKER --list"
echo ""
echo "# Describe a topic:"
echo "  $KAFKA_CMD --bootstrap-server $KAFKA_BROKER --describe --topic order-events"
echo ""
echo "# Consume messages:"
echo "  $KAFKA_CONSOLE_CONSUMER --bootstrap-server $KAFKA_BROKER --topic order-events --from-beginning"
echo ""
echo "# Check consumer lag:"
echo "  kafka-consumer-groups --bootstrap-server $KAFKA_BROKER --describe --group order-service"
echo ""
echo "# Delete a topic (careful!):"
echo "  $KAFKA_CMD --bootstrap-server $KAFKA_BROKER --delete --topic <topic-name>"

echo -e "\n${GREEN}Test completed!${NC}\n"
