#!/bin/bash

# Analyze JFR Recording for Virtual Thread Issues
# Usage: ./analyze-jfr.sh <recording-file.jfr>

set -e

RECORDING_FILE=${1:-""}

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

if [ -z "$RECORDING_FILE" ] || [ ! -f "$RECORDING_FILE" ]; then
    echo -e "${RED}Error: Recording file not found${NC}"
    echo "Usage: $0 <recording-file.jfr>"
    echo ""
    echo "Available recordings:"
    ls -lh ./jfr-recordings/*.jfr 2>/dev/null || echo "  (none)"
    exit 1
fi

echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  JFR Analysis - Virtual Thread Insights   ║${NC}"
echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo ""
echo -e "${YELLOW}Analyzing:${NC} $RECORDING_FILE"
echo ""

# 1. Virtual Thread Pinning
echo -e "${BLUE}═══ 1. Virtual Thread Pinning Events ═══${NC}"
PINNED_COUNT=$(jfr print --events jdk.VirtualThreadPinned "$RECORDING_FILE" 2>/dev/null | grep -c "jdk.VirtualThreadPinned" || true)
if [ "$PINNED_COUNT" -gt 0 ]; then
    echo -e "${RED}⚠️  Found $PINNED_COUNT pinning events${NC}"
    echo ""
    jfr print --events jdk.VirtualThreadPinned "$RECORDING_FILE" | head -100
else
    echo -e "${GREEN}✓ No thread pinning detected${NC}"
fi
echo ""

# 2. Virtual Thread Lifecycle
echo -e "${BLUE}═══ 2. Virtual Thread Lifecycle ═══${NC}"
STARTED=$(jfr print --events jdk.VirtualThreadStart "$RECORDING_FILE" 2>/dev/null | grep -c "jdk.VirtualThreadStart" || true)
ENDED=$(jfr print --events jdk.VirtualThreadEnd "$RECORDING_FILE" 2>/dev/null | grep -c "jdk.VirtualThreadEnd" || true)
echo -e "Virtual Threads Started: ${GREEN}$STARTED${NC}"
echo -e "Virtual Threads Ended: ${GREEN}$ENDED${NC}"
echo -e "Active (approximate): ${YELLOW}$((STARTED - ENDED))${NC}"
echo ""

# 3. Monitor Contention
echo -e "${BLUE}═══ 3. Monitor Contention (Potential Pinning) ═══${NC}"
MONITOR_ENTER=$(jfr print --events jdk.JavaMonitorEnter "$RECORDING_FILE" 2>/dev/null | grep -c "jdk.JavaMonitorEnter" || true)
if [ "$MONITOR_ENTER" -gt 0 ]; then
    echo -e "${YELLOW}Monitor Enter Events: $MONITOR_ENTER${NC}"
    echo "Top synchronized blocks:"
    jfr print --events jdk.JavaMonitorEnter "$RECORDING_FILE" 2>/dev/null | grep -A 5 "stackTrace" | head -20
else
    echo -e "${GREEN}✓ No monitor contention detected${NC}"
fi
echo ""

# 4. I/O Operations
echo -e "${BLUE}═══ 4. I/O Operations ═══${NC}"
SOCKET_READ=$(jfr print --events jdk.SocketRead "$RECORDING_FILE" 2>/dev/null | grep -c "jdk.SocketRead" || true)
SOCKET_WRITE=$(jfr print --events jdk.SocketWrite "$RECORDING_FILE" 2>/dev/null | grep -c "jdk.SocketWrite" || true)
FILE_READ=$(jfr print --events jdk.FileRead "$RECORDING_FILE" 2>/dev/null | grep -c "jdk.FileRead" || true)
FILE_WRITE=$(jfr print --events jdk.FileWrite "$RECORDING_FILE" 2>/dev/null | grep -c "jdk.FileWrite" || true)

echo -e "Socket Reads: ${GREEN}$SOCKET_READ${NC}"
echo -e "Socket Writes: ${GREEN}$SOCKET_WRITE${NC}"
echo -e "File Reads: ${GREEN}$FILE_READ${NC}"
echo -e "File Writes: ${GREEN}$FILE_WRITE${NC}"
echo ""

# 5. CPU & Memory
echo -e "${BLUE}═══ 5. Resource Usage ═══${NC}"
jfr print --events jdk.CPULoad "$RECORDING_FILE" 2>/dev/null | tail -20
echo ""

# 6. GC Activity
echo -e "${BLUE}═══ 6. Garbage Collection ═══${NC}"
GC_COUNT=$(jfr print --events jdk.GarbageCollection "$RECORDING_FILE" 2>/dev/null | grep -c "jdk.GarbageCollection" || true)
echo -e "GC Events: ${YELLOW}$GC_COUNT${NC}"
if [ "$GC_COUNT" -gt 0 ]; then
    jfr print --events jdk.GarbageCollection "$RECORDING_FILE" 2>/dev/null | tail -10
fi
echo ""

# 7. Exceptions
echo -e "${BLUE}═══ 7. Exceptions & Errors ═══${NC}"
ERROR_COUNT=$(jfr print --events jdk.JavaErrorThrow "$RECORDING_FILE" 2>/dev/null | grep -c "jdk.JavaErrorThrow" || true)
if [ "$ERROR_COUNT" -gt 0 ]; then
    echo -e "${RED}Errors thrown: $ERROR_COUNT${NC}"
    jfr print --events jdk.JavaErrorThrow "$RECORDING_FILE" 2>/dev/null | head -20
else
    echo -e "${GREEN}✓ No errors detected${NC}"
fi
echo ""

# Summary
echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║              SUMMARY                       ║${NC}"
echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
echo ""
if [ "$PINNED_COUNT" -gt 0 ]; then
    echo -e "${RED}⚠️  ACTION REQUIRED: Thread pinning detected!${NC}"
    echo -e "   Review synchronized blocks and native calls"
    echo ""
fi

if [ "$MONITOR_ENTER" -gt 50 ]; then
    echo -e "${YELLOW}⚠️  High monitor contention detected${NC}"
    echo -e "   Consider replacing synchronized with ReentrantLock"
    echo ""
fi

if [ "$PINNED_COUNT" -eq 0 ] && [ "$MONITOR_ENTER" -lt 50 ]; then
    echo -e "${GREEN}✓ Virtual threads configuration looks good!${NC}"
    echo ""
fi

echo -e "${YELLOW}For detailed analysis, open in JDK Mission Control:${NC}"
echo "  jmc -open $RECORDING_FILE"
echo ""
