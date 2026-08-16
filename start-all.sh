#!/usr/bin/env bash
set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"

mkdir -p "$LOG_DIR"

echo "Starting AI School Examination backend and frontend ..."

(
  cd "$ROOT_DIR/backend"
  echo "Starting AI School Examination backend on http://localhost:8080 ..."
  mvn spring-boot:run
) > "$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!

(
  cd "$ROOT_DIR/frontend"
  echo "Starting AI School Examination frontend on http://localhost:3000 ..."
  if [ ! -d "node_modules" ]; then
    echo "Installing frontend dependencies ..."
    npm install
  fi
  npm run dev -- --host 0.0.0.0
) > "$LOG_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!

echo "$BACKEND_PID" > "$LOG_DIR/backend.pid"
echo "$FRONTEND_PID" > "$LOG_DIR/frontend.pid"

echo "Backend:  http://localhost:8080"
echo "Frontend: http://localhost:3000"
echo "Logs:     $LOG_DIR"
echo "Stop:     kill $BACKEND_PID $FRONTEND_PID"
