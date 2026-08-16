#!/usr/bin/env bash
set -euo pipefail

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
  echo "Run this installer as root: sudo ./install-systemd.sh /path/to/.env" >&2
  exit 1
fi

SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR=/opt/auto-hr
SERVICE_FILE=/etc/systemd/system/auto-hr.service
SERVICE_USER=autohr
SERVICE_GROUP=autohr

install_owned_file() {
  local source_file="$1"
  local destination_file="$2"
  local owner="$3"
  local group="$4"
  local mode="$5"

  if [ "$(readlink -f "$source_file")" = "$(readlink -m "$destination_file")" ]; then
    chown "$owner:$group" "$destination_file"
    chmod "$mode" "$destination_file"
  else
    install -o "$owner" -g "$group" -m "$mode" "$source_file" "$destination_file"
  fi
}

for command_name in curl java systemctl; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is not installed: $command_name" >&2
    exit 1
  fi
done

for required_file in "$SOURCE_DIR/backend/auto-hr.jar" "$SOURCE_DIR/start.sh" \
  "$SOURCE_DIR/auto-hr.service"; do
  if [ ! -s "$required_file" ]; then
    echo "Required release file is missing or empty: $required_file" >&2
    exit 1
  fi
done

ENV_SOURCE="${1:-}"
if [ -n "$ENV_SOURCE" ]; then
  if [ ! -s "$ENV_SOURCE" ]; then
    echo "Environment file is missing or empty: $ENV_SOURCE" >&2
    exit 1
  fi
elif [ -s "$INSTALL_DIR/.env" ]; then
  ENV_SOURCE="$INSTALL_DIR/.env"
elif [ -s "$SOURCE_DIR/.env" ]; then
  ENV_SOURCE="$SOURCE_DIR/.env"
else
  echo "First installation requires a production environment file." >&2
  echo "Usage: sudo ./install-systemd.sh /path/to/production.env" >&2
  exit 1
fi

if ! getent group "$SERVICE_GROUP" >/dev/null 2>&1; then
  groupadd --system "$SERVICE_GROUP"
fi
if ! id -u "$SERVICE_USER" >/dev/null 2>&1; then
  useradd --system --gid "$SERVICE_GROUP" --home-dir "$INSTALL_DIR" \
    --shell /usr/sbin/nologin "$SERVICE_USER"
fi

install -d -o root -g "$SERVICE_GROUP" -m 0750 "$INSTALL_DIR" "$INSTALL_DIR/backend"
install -d -o "$SERVICE_USER" -g "$SERVICE_GROUP" -m 0700 \
  "$INSTALL_DIR/uploads" "$INSTALL_DIR/logs"
install -d -o root -g root -m 0755 "$INSTALL_DIR/frontend"

# Stage the JAR before stopping the current unit so validation failures do not
# interrupt an already-running installation.
install -o root -g "$SERVICE_GROUP" -m 0640 \
  "$SOURCE_DIR/backend/auto-hr.jar" "$INSTALL_DIR/backend/auto-hr.jar.next"

systemctl stop auto-hr 2>/dev/null || true
mv -f "$INSTALL_DIR/backend/auto-hr.jar.next" "$INSTALL_DIR/backend/auto-hr.jar"
install_owned_file "$SOURCE_DIR/start.sh" "$INSTALL_DIR/start.sh" root "$SERVICE_GROUP" 0750

install_owned_file "$ENV_SOURCE" "$INSTALL_DIR/.env" "$SERVICE_USER" "$SERVICE_GROUP" 0600
install_owned_file "$SOURCE_DIR/auto-hr.service" "$INSTALL_DIR/auto-hr.service" root root 0644
install -o root -g root -m 0644 "$SOURCE_DIR/auto-hr.service" "$SERVICE_FILE"
if [ -f "$SOURCE_DIR/.env.example" ]; then
  install_owned_file "$SOURCE_DIR/.env.example" "$INSTALL_DIR/.env.example" root root 0644
fi
if [ "$(readlink -f "$SOURCE_DIR/frontend")" != "$(readlink -m "$INSTALL_DIR/frontend")" ]; then
  cp -a "$SOURCE_DIR/frontend/." "$INSTALL_DIR/frontend/"
fi
chown -R "$SERVICE_USER:$SERVICE_GROUP" "$INSTALL_DIR/uploads" "$INSTALL_DIR/logs"
find "$INSTALL_DIR/uploads" "$INSTALL_DIR/logs" -type d -exec chmod 0700 {} +
find "$INSTALL_DIR/uploads" "$INSTALL_DIR/logs" -type f -exec chmod 0600 {} +

systemctl daemon-reload
systemctl enable auto-hr
systemctl restart auto-hr
for attempt in $(seq 1 30); do
  if systemctl is-active --quiet auto-hr \
    && curl --fail --silent --show-error http://127.0.0.1:8081/api/auth/captcha >/dev/null; then
    echo "Auto HR is running at http://127.0.0.1:8081"
    exit 0
  fi
  sleep 2
done

systemctl status auto-hr --no-pager || true
journalctl -u auto-hr -n 100 --no-pager || true
echo "Auto HR did not become healthy within 60 seconds." >&2
exit 1
