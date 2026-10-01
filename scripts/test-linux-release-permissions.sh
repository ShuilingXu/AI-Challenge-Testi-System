#!/usr/bin/env bash
# Run ONLY in a disposable Linux container; creates /opt/auto-hr and user autohr.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ ! -f /.dockerenv ] || [ -e /opt/auto-hr ]; then
  echo "Requires a fresh disposable container without /opt/auto-hr" >&2
  exit 1
fi
groupadd --system autohr
useradd --system --gid autohr --shell /usr/sbin/nologin autohr
install -d -o root -g autohr -m 0750 /opt/auto-hr /opt/auto-hr/backend
install -d -o autohr -g autohr -m 0700 /opt/auto-hr/data /opt/auto-hr/config /opt/auto-hr/uploads /opt/auto-hr/logs
install -o root -g autohr -m 0640 "$ROOT_DIR/backend/target/auto-hr-backend-1.0.0-SNAPSHOT.jar" /opt/auto-hr/backend/auto-hr.jar
install -o root -g autohr -m 0750 "$ROOT_DIR/scripts/start-release.sh" /opt/auto-hr/start.sh
install -o root -g root -m 0644 "$ROOT_DIR/scripts/auto-hr.service" /etc/systemd/system/auto-hr.service
cat > /opt/auto-hr/config/.env <<'ENV'
DB_TYPE=sqlite
JWT_SECRET=linux-permissions-test-secret-at-least-32-characters
REDIS_HOST=autohr-review-redis-20261001
AUTH_SESSION_COOKIE_SECURE=false
AUTH_CSRF_COOKIE_SECURE=false
ENV
python3 - <<'PY'
import sqlite3
with sqlite3.connect('/opt/auto-hr/school_exam.db') as connection:
    connection.execute('CREATE TABLE permission_fixture(value TEXT)')
    connection.execute("INSERT INTO permission_fixture VALUES('legacy retained')")
PY
python3 "$ROOT_DIR/scripts/prepare-release-sqlite.py" /opt/auto-hr
chown autohr:autohr /opt/auto-hr/config/.env /opt/auto-hr/data/school_exam.db
chmod 0600 /opt/auto-hr/config/.env /opt/auto-hr/data/school_exam.db
runuser -u autohr -- python3 - <<'PY'
import sqlite3
with sqlite3.connect('/opt/auto-hr/data/school_exam.db') as connection:
    assert connection.execute('SELECT value FROM permission_fixture').fetchone()[0] == 'legacy retained'
    assert connection.execute('PRAGMA journal_mode=WAL').fetchone()[0] == 'wal'
    connection.execute("INSERT INTO permission_fixture VALUES('service write succeeds')")
print('PASS: service user writes SQLite WAL in data directory; legacy data retained')
PY
runuser -u autohr -- env AUTOHR_ENV_PATH=/opt/auto-hr/config/.env \
  SITE_SETTINGS_PATH=/opt/auto-hr/config/.site-settings.json \
  SITE_CONTENT_PATH=/opt/auto-hr/config/.site-content.json \
  SERVER_PORT=18082 /opt/auto-hr/start.sh > /opt/auto-hr/logs/permissions-test.log 2>&1 &
RUNNER_PID=$!
cleanup() {
  pkill -u autohr -f 'java.*auto-hr.jar' || true
  wait "$RUNNER_PID" || true
}
trap cleanup EXIT
for attempt in $(seq 1 60); do
  if curl --fail --silent http://127.0.0.1:18082/api/auth/captcha > /tmp/permissions-captcha.json; then
    curl --fail --silent http://127.0.0.1:18082/login > /tmp/permissions-login.html
    grep -q 'id="app"' /tmp/permissions-login.html
    echo 'PASS: production release starts as autohr, serves embedded frontend and Redis-backed CAPTCHA'
    systemd-analyze verify /etc/systemd/system/auto-hr.service
    echo 'PASS: systemd unit verification'
    exit 0
  fi
  if ! kill -0 "$RUNNER_PID" 2>/dev/null; then
    echo 'Service stopped; inspect /opt/auto-hr/logs/permissions-test.log inside the disposable container' >&2
    exit 1
  fi
  sleep 1
done
echo 'Service did not become ready in 60 seconds' >&2
exit 1
