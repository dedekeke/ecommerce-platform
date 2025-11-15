#!/bin/bash

##############################################################################
# Stop All Services
#
# This script stops all running microservices.
##############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Stopping All Services${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Check if running in tmux
SESSION_NAME="ecommerce-services"
if tmux has-session -t "$SESSION_NAME" 2>/dev/null; then
    echo -e "${YELLOW}Killing tmux session: $SESSION_NAME${NC}"
    tmux kill-session -t "$SESSION_NAME"
    echo -e "${GREEN}✓ Tmux session killed${NC}"
fi

# Stop background processes
if [ -d "logs" ]; then
    for pidfile in logs/*.pid; do
        if [ -f "$pidfile" ]; then
            PID=$(cat "$pidfile")
            SERVICE=$(basename "$pidfile" .pid)

            if ps -p $PID > /dev/null 2>&1; then
                echo -e "${YELLOW}Stopping $SERVICE (PID: $PID)${NC}"
                kill $PID 2>/dev/null || true
                echo -e "${GREEN}✓ Stopped $SERVICE${NC}"
            else
                echo -e "${YELLOW}$SERVICE not running${NC}"
            fi

            rm "$pidfile"
        fi
    done
fi

# Kill any remaining Spring Boot processes
echo ""
echo -e "${YELLOW}Checking for remaining Spring Boot processes...${NC}"
pkill -f "spring-boot:run" || true
pkill -f "maven" || true

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}All services stopped${NC}"
echo -e "${GREEN}============================================${NC}"
