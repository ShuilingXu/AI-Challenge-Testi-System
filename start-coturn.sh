#!/usr/bin/env bash
set -e
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"
docker compose -f docker-compose.coturn.yml up -d
echo "coturn started on port 3478. Configure INTERVIEW_TURN_URLS in .env with your reachable host."
