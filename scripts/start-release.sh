#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$APP_DIR"
mkdir -p uploads logs

exec java ${JAVA_OPTS:-} -jar "$APP_DIR/backend/auto-hr.jar" \
  --spring.profiles.active="${SPRING_PROFILES_ACTIVE:-prod}" \
  --server.address="${SERVER_ADDRESS:-127.0.0.1}" \
  --server.port="${SERVER_PORT:-8081}"
