#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND="$REPO_ROOT/frontend"

cleanup() {
  echo ""
  echo "Stopping all MFE processes..."
  kill "${PIDS[@]}" 2>/dev/null || true
  wait 2>/dev/null || true
  echo "All processes stopped."
}

PIDS=()
trap cleanup EXIT INT TERM

echo "=== Building and previewing MFEs ==="

build_and_preview() {
  local name="$1"
  local dir="$FRONTEND/$name"
  echo "[$name] Building..."
  npm --prefix "$dir" run build 2>&1 | sed "s/^/[$name] /"
  echo "[$name] Starting preview server..."
  npm --prefix "$dir" run preview 2>&1 | sed "s/^/[$name] /" &
  PIDS+=($!)
}

build_and_preview "product-catalog-mfe"
build_and_preview "cart-mfe"
build_and_preview "checkout-mfe"

echo ""
echo "Waiting 3 seconds for MFE preview servers to be ready..."
sleep 3

echo ""
echo "=== Starting shell-app dev server ==="
npm --prefix "$FRONTEND/shell-app" run dev 2>&1 | sed "s/^/[shell] /" &
PIDS+=($!)

echo ""
echo "All services started:"
echo "  shell-app          http://localhost:5173"
echo "  product-catalog    http://localhost:5001"
echo "  cart               http://localhost:5002"
echo "  checkout           http://localhost:5003"
echo ""
echo "Press Ctrl+C to stop all services."

wait
