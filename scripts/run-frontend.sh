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

echo "=== Building and previewing React MFEs ==="

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
echo "Waiting 3 seconds for React MFE preview servers to be ready..."
sleep 3

# ---------------------------------------------------------------------------
# Angular MFEs — native-federation outputs static files (remoteEntry.json +
# chunks).  We build them with `ng build` and then serve the dist directory
# with `npx serve` (a zero-config static server).
#
# IMPORTANT: Angular MFEs MUST be running BEFORE the shell-app starts so that
# the native-federation runtime can fetch their remoteEntry.json files and
# register the import maps during shell initialisation.
# ---------------------------------------------------------------------------

build_and_serve_angular() {
  local name="$1"
  local port="$2"
  local dir="$FRONTEND/$name"
  local dist_dir="$dir/dist/$name/browser"

  echo "[$name] Building Angular MFE..."
  npm --prefix "$dir" run build 2>&1 | sed "s/^/[$name] /"

  if [ ! -d "$dist_dir" ]; then
    # Fallback: some Angular versions output directly to dist/<name>
    dist_dir="$dir/dist/$name"
  fi

  echo "[$name] Starting static server on port $port..."
  npx --yes serve "$dist_dir" -p "$port" --cors 2>&1 | sed "s/^/[$name] /" &
  PIDS+=($!)
}

build_and_serve_angular "user-dashboard-mfe" 5004
build_and_serve_angular "admin-dashboard-mfe" 5005

echo ""
echo "Waiting 5 seconds for Angular MFE static servers to be ready..."
sleep 5

echo ""
echo "=== Starting shell-app dev server ==="
npm --prefix "$FRONTEND/shell-app" run dev 2>&1 | sed "s/^/[shell] /" &
PIDS+=($!)

echo ""
echo "All services started:"
echo "  shell-app            http://localhost:5173"
echo "  product-catalog      http://localhost:5001  (webpack/vite-plugin-federation)"
echo "  cart                 http://localhost:5002  (webpack/vite-plugin-federation)"
echo "  checkout             http://localhost:5003  (webpack/vite-plugin-federation)"
echo "  user-dashboard-mfe   http://localhost:5004  (Angular native-federation)"
echo "  admin-dashboard-mfe  http://localhost:5005  (Angular native-federation)"
echo ""
echo "Dependency order: Angular MFEs (5004, 5005) must be up before shell"
echo "so import maps register correctly on startup."
echo ""
echo "Press Ctrl+C to stop all services."

wait
