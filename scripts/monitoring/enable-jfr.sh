#!/bin/bash

# Enable JDK Flight Recorder for Virtual Thread Monitoring
# Usage: ./enable-jfr.sh <service-name> [duration-in-seconds]

set -e

SERVICE_NAME=${1:-"order-service"}
DURATION=${2:-60}
OUTPUT_DIR="./jfr-recordings"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RECORDING_FILE="${OUTPUT_DIR}/${SERVICE_NAME}_${TIMESTAMP}.jfr"

# Create output directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  JDK Flight Recorder - Virtual Threads    ║${NC}"
echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo ""
echo -e "${YELLOW}Service:${NC} $SERVICE_NAME"
echo -e "${YELLOW}Duration:${NC} ${DURATION}s"
echo -e "${YELLOW}Output:${NC} $RECORDING_FILE"
echo ""

# Find the Java process for the service
PID=$(docker-compose ps -q "$SERVICE_NAME" 2>/dev/null || echo "")

if [ -z "$PID" ]; then
    echo -e "${RED}Error: Service '$SERVICE_NAME' not found or not running${NC}"
    echo "Available services:"
    docker-compose ps --services
    exit 1
fi

# Get the actual Java PID inside the container
JAVA_PID=$(docker exec "$PID" jps | grep -v Jps | awk '{print $1}' | head -1)

if [ -z "$JAVA_PID" ]; then
    echo -e "${RED}Error: Could not find Java process in container${NC}"
    exit 1
fi

echo -e "${GREEN}Found Java process: PID $JAVA_PID${NC}"
echo ""

# Service images do not ship the .jfc settings file; copy it in from the repo.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JFC_FILE="$SCRIPT_DIR/../../config/jfr/virtual-threads-monitoring.jfc"
[ -f "$JFC_FILE" ] || { echo "Error: JFC settings file not found at $JFC_FILE"; exit 1; }
docker cp "$JFC_FILE" "${PID}:/tmp/virtual-threads-monitoring.jfc"

# Start JFR recording
echo -e "${YELLOW}Starting JFR recording...${NC}"
docker exec "$PID" jcmd "$JAVA_PID" JFR.start \
    name=virtual-threads-recording \
    settings=/tmp/virtual-threads-monitoring.jfc \
    duration="${DURATION}s" \
    filename=/tmp/recording.jfr

echo -e "${GREEN}Recording started!${NC}"
echo ""
echo -e "${YELLOW}Waiting ${DURATION} seconds...${NC}"
sleep "$DURATION"

# Copy recording from container
echo -e "${YELLOW}Copying recording file...${NC}"
docker cp "${PID}:/tmp/recording.jfr" "$RECORDING_FILE"

echo -e "${GREEN}Recording saved to: $RECORDING_FILE${NC}"
echo ""

# Analyze pinning events
echo -e "${YELLOW}Analyzing virtual thread pinning events...${NC}"
PINNED_COUNT=$(jfr print --events jdk.VirtualThreadPinned "$RECORDING_FILE" 2>/dev/null | grep -c "jdk.VirtualThreadPinned" || true)

if [ "$PINNED_COUNT" -gt 0 ]; then
    echo -e "${RED}⚠️  Found $PINNED_COUNT thread pinning events!${NC}"
    echo -e "${YELLOW}Details:${NC}"
    jfr print --events jdk.VirtualThreadPinned "$RECORDING_FILE" | head -50
else
    echo -e "${GREEN}✓ No thread pinning detected${NC}"
fi

echo ""
echo -e "${YELLOW}To view full recording:${NC}"
echo "  jfr print $RECORDING_FILE"
echo "  jfr print --events jdk.VirtualThreadPinned $RECORDING_FILE"
echo "  jfr print --events jdk.VirtualThreadStart $RECORDING_FILE"
echo ""
echo -e "${YELLOW}To analyze with JDK Mission Control:${NC}"
echo "  jmc -open $RECORDING_FILE"
echo ""
echo -e "${GREEN}Done!${NC}"
