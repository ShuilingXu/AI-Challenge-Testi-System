#!/usr/bin/env bash
set -euo pipefail

# This script is for a single-host local/LAN verification environment only. It
# intentionally does not configure TLS or a public reverse proxy. Use the
# GitHub Actions deployment and OpenResty/systemd setup documented in README
# for Internet-facing production deployments.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
LOG_DIR="$ROOT_DIR/logs"
ENV_FILE="$ROOT_DIR/.env"

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
APP_BIND_ADDRESS="${APP_BIND_ADDRESS:-127.0.0.1}"
APP_ALLOWED_CIDR="${APP_ALLOWED_CIDR:-}"
APP_FIREWALL_STATUS=unmanaged
TURN_HOST="${TURN_HOST:-}"
TURN_EXTERNAL_IP="${TURN_EXTERNAL_IP:-}"
TURN_PRIVATE_IP="${TURN_PRIVATE_IP:-}"
INTERVIEW_TURN_SHARED_SECRET="${INTERVIEW_TURN_SHARED_SECRET:-}"
TURN_REALM="${TURN_REALM:-}"
TURN_MIN_PORT="${TURN_MIN_PORT:-}"
TURN_MAX_PORT="${TURN_MAX_PORT:-}"
REDIS_HOST="${REDIS_HOST:-}"
REDIS_PORT="${REDIS_PORT:-}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
REDIS_SSL_ENABLED="${REDIS_SSL_ENABLED:-}"
INTERVIEW_VIDEO_FFMPEG_PATH="${INTERVIEW_VIDEO_FFMPEG_PATH:-}"
INTERVIEW_VIDEO_VIDEO_CODEC="${INTERVIEW_VIDEO_VIDEO_CODEC:-}"
INTERVIEW_VIDEO_AUDIO_CODEC="${INTERVIEW_VIDEO_AUDIO_CODEC:-}"
RESUME_OCR_ENABLED="${RESUME_OCR_ENABLED:-}"
RESUME_OCR_TESSERACT_PATH="${RESUME_OCR_TESSERACT_PATH:-}"
RESUME_OCR_LANGUAGE="${RESUME_OCR_LANGUAGE:-}"
RESUME_OCR_DPI="${RESUME_OCR_DPI:-}"
RESUME_OCR_MAX_PAGES="${RESUME_OCR_MAX_PAGES:-}"

install_package() {
  local package_name="$1"
  if ! dpkg -s "$package_name" >/dev/null 2>&1; then
    sudo apt-get install -y "$package_name"
  fi
}

ensure_dependencies() {
  if ! command -v sudo >/dev/null 2>&1; then
    echo "sudo is required to install missing dependencies."
    exit 1
  fi

  sudo apt-get update
  install_package openjdk-17-jdk
  install_package maven
  install_package coturn
  install_package curl
  install_package ffmpeg
  install_package openssl
  install_package postgresql-client
  install_package python3-venv
  install_package redis-tools
  install_package tesseract-ocr
  install_package tesseract-ocr-eng
  install_package tesseract-ocr-chi-sim

  local node_major=0
  if command -v node >/dev/null 2>&1; then
    node_major="$(node -v | sed 's/^v//' | cut -d. -f1)"
  fi

  if [ "$node_major" -lt 18 ]; then
    sudo apt-get install -y ca-certificates curl gnupg
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt-get install -y nodejs
  fi

}

ensure_resume_ocr() {
  local tesseract_bin="${RESUME_OCR_TESSERACT_PATH:-tesseract}"

  if ! command -v "$tesseract_bin" >/dev/null 2>&1; then
    echo "tesseract was not found. Install tesseract-ocr or set RESUME_OCR_TESSERACT_PATH."
    exit 1
  fi

  if [ -z "$RESUME_OCR_LANGUAGE" ]; then
    if "$tesseract_bin" --list-langs 2>/dev/null | grep -Fxq "chi_sim"; then
      RESUME_OCR_LANGUAGE="chi_sim+eng"
    else
      RESUME_OCR_LANGUAGE="eng"
      echo "Warning: Tesseract language chi_sim was not found. Resume OCR will use eng only."
    fi
  fi

  RESUME_OCR_ENABLED="${RESUME_OCR_ENABLED:-true}"
  RESUME_OCR_DPI="${RESUME_OCR_DPI:-200}"
  RESUME_OCR_MAX_PAGES="${RESUME_OCR_MAX_PAGES:-5}"

  set_env_value RESUME_OCR_ENABLED "$RESUME_OCR_ENABLED"
  set_env_value RESUME_OCR_TESSERACT_PATH "$tesseract_bin"
  set_env_value RESUME_OCR_LANGUAGE "$RESUME_OCR_LANGUAGE"
  set_env_value RESUME_OCR_DPI "$RESUME_OCR_DPI"
  set_env_value RESUME_OCR_MAX_PAGES "$RESUME_OCR_MAX_PAGES"
}

ensure_video_codecs() {
  local ffmpeg_bin="${INTERVIEW_VIDEO_FFMPEG_PATH:-ffmpeg}"

  if ! command -v "$ffmpeg_bin" >/dev/null 2>&1; then
    echo "ffmpeg was not found. Install ffmpeg or set INTERVIEW_VIDEO_FFMPEG_PATH."
    exit 1
  fi

  if [ -z "$INTERVIEW_VIDEO_VIDEO_CODEC" ]; then
    if has_ffmpeg_encoder "$ffmpeg_bin" "libvpx-vp9"; then
      INTERVIEW_VIDEO_VIDEO_CODEC="libvpx-vp9"
    elif has_ffmpeg_encoder "$ffmpeg_bin" "vp9"; then
      INTERVIEW_VIDEO_VIDEO_CODEC="vp9"
    elif has_ffmpeg_encoder "$ffmpeg_bin" "libvpx"; then
      INTERVIEW_VIDEO_VIDEO_CODEC="libvpx"
    fi
  fi

  if [ -z "$INTERVIEW_VIDEO_AUDIO_CODEC" ]; then
    if has_ffmpeg_encoder "$ffmpeg_bin" "libopus"; then
      INTERVIEW_VIDEO_AUDIO_CODEC="libopus"
    elif has_ffmpeg_encoder "$ffmpeg_bin" "opus"; then
      INTERVIEW_VIDEO_AUDIO_CODEC="opus"
    fi
  fi

  if [ -z "$INTERVIEW_VIDEO_VIDEO_CODEC" ]; then
    echo "No VP9/VP8 WebM video encoder was detected in ffmpeg."
    echo "Available video encoders containing vp8/vp9/vpx:"
    list_ffmpeg_encoders "$ffmpeg_bin" | grep -Ei 'vp8|vp9|vpx' || true
    echo "Install a full ffmpeg build or set INTERVIEW_VIDEO_VIDEO_CODEC to one listed above."
    exit 1
  fi

  if [ -z "$INTERVIEW_VIDEO_AUDIO_CODEC" ]; then
    echo "No Opus audio encoder was detected in ffmpeg."
    echo "Available audio encoders containing opus:"
    list_ffmpeg_encoders "$ffmpeg_bin" | grep -Ei 'opus' || true
    echo "Install a full ffmpeg build or set INTERVIEW_VIDEO_AUDIO_CODEC to one listed above."
    exit 1
  fi

  if ! has_ffmpeg_encoder "$ffmpeg_bin" "$INTERVIEW_VIDEO_VIDEO_CODEC"; then
    echo "ffmpeg video encoder $INTERVIEW_VIDEO_VIDEO_CODEC was not found. Install an ffmpeg build with VP9/VP8 WebM support or set INTERVIEW_VIDEO_VIDEO_CODEC."
    exit 1
  fi

  if ! has_ffmpeg_encoder "$ffmpeg_bin" "$INTERVIEW_VIDEO_AUDIO_CODEC"; then
    echo "ffmpeg audio encoder $INTERVIEW_VIDEO_AUDIO_CODEC was not found. Install an ffmpeg build with Opus/WebM support or set INTERVIEW_VIDEO_AUDIO_CODEC."
    exit 1
  fi

  set_env_value INTERVIEW_VIDEO_VIDEO_CODEC "$INTERVIEW_VIDEO_VIDEO_CODEC"
  set_env_value INTERVIEW_VIDEO_AUDIO_CODEC "$INTERVIEW_VIDEO_AUDIO_CODEC"
}

has_ffmpeg_encoder() {
  local ffmpeg_bin="$1"
  local encoder="$2"
  [ -n "$encoder" ] && list_ffmpeg_encoders "$ffmpeg_bin" | grep -Fxq "$encoder"
}

list_ffmpeg_encoders() {
  local ffmpeg_bin="$1"
  "$ffmpeg_bin" -hide_banner -encoders 2>/dev/null | awk '$1 ~ /^[VAS]/ { print $2 }'
}

random_secret() {
  if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl is required to generate cryptographically random secrets." >&2
    return 1
  fi
  local secret
  secret="$(openssl rand -hex 32 2>/dev/null || true)"
  if [ -z "$secret" ]; then
    echo "openssl could not generate a random secret; refusing to use a predictable fallback." >&2
    return 1
  fi
  printf '%s\n' "$secret"
}

validate_local_bind_address() {
  case "$APP_BIND_ADDRESS" in
    127.*|::1|localhost)
      if [ -n "$APP_ALLOWED_CIDR" ]; then
        echo "APP_ALLOWED_CIDR is only valid with an RFC1918 APP_BIND_ADDRESS." >&2
        exit 1
      fi
      ;;
    10.*|192.168.*|172.16.*|172.17.*|172.18.*|172.19.*|172.2[0-9].*|172.3[0-1].*)
      if [ -z "$APP_ALLOWED_CIDR" ]; then
        echo "A private APP_BIND_ADDRESS requires APP_ALLOWED_CIDR, for example 192.168.1.0/24." >&2
        exit 1
      fi
      if ! python3 - "$APP_BIND_ADDRESS" "$APP_ALLOWED_CIDR" 2>/dev/null <<'PY'
import ipaddress
import sys

address = ipaddress.ip_address(sys.argv[1])
network = ipaddress.ip_network(sys.argv[2], strict=False)
rfc1918 = (
    ipaddress.ip_network("10.0.0.0/8"),
    ipaddress.ip_network("172.16.0.0/12"),
    ipaddress.ip_network("192.168.0.0/16"),
)
if address.version != 4 or address not in network:
    raise SystemExit(1)
if not any(network.subnet_of(private_network) for private_network in rfc1918):
    raise SystemExit(1)
PY
      then
        echo "APP_ALLOWED_CIDR must be an RFC1918 network containing APP_BIND_ADDRESS." >&2
        exit 1
      fi
      ;;
    *)
      echo "APP_BIND_ADDRESS must be a loopback or RFC1918 LAN address for this local/LAN-only script." >&2
      echo "Use the documented OpenResty + systemd deployment for public Internet access." >&2
      exit 1
      ;;
  esac
}

detect_public_ip() {
  local ip=""
  ip="$(curl -fsS --max-time 3 https://api.ipify.org 2>/dev/null || true)"
  if [ -z "$ip" ]; then
    ip="$(curl -fsS --max-time 3 https://ifconfig.me 2>/dev/null || true)"
  fi
  if [ -z "$ip" ]; then
    ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
  fi
  echo "$ip"
}

detect_private_ip() {
  hostname -I 2>/dev/null | tr ' ' '\n' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' | grep -v '^127\.' | head -n 1 || true
}

set_env_value() {
  local key="$1"
  local value="$2"

  if [[ ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "Invalid environment variable name: $key" >&2
    exit 1
  fi
  if [[ "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
    echo "Environment variable $key cannot contain a line break." >&2
    exit 1
  fi

  ENV_FILE="$ENV_FILE" ENV_KEY="$key" ENV_VALUE="$value" python3 - <<'PY'
import os
from pathlib import Path
import tempfile

path = Path(os.environ["ENV_FILE"])
key = os.environ["ENV_KEY"]
value = os.environ["ENV_VALUE"]
lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
result = []
found = False
for line in lines:
    if line.startswith(f"{key}="):
        if not found:
            result.append(f"{key}={value}")
            found = True
        continue
    result.append(line)
if not found:
    result.append(f"{key}={value}")
payload = "\n".join(result) + "\n"
path.parent.mkdir(parents=True, exist_ok=True)
fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
try:
    with os.fdopen(fd, "w", encoding="utf-8") as output:
        output.write(payload)
        output.flush()
        os.fsync(output.fileno())
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
PY
}

get_env_value() {
  local key="$1"
  grep -m 1 "^${key}=" "$ENV_FILE" | cut -d= -f2- || true
}

prepare_env() {
  if [ ! -f "$ENV_FILE" ]; then
    cp "$ROOT_DIR/.env.example" "$ENV_FILE"
  fi
  chmod 600 "$ENV_FILE"
  chown "$(id -un)" "$ENV_FILE"

  local jwt_secret
  jwt_secret="$(get_env_value JWT_SECRET)"
  if [ -z "${jwt_secret//[[:space:]]/}" ]; then
    set_env_value JWT_SECRET "$(random_secret)"
    jwt_secret="$(get_env_value JWT_SECRET)"
  fi
  if [ -z "${jwt_secret//[[:space:]]/}" ]; then
    echo "JWT_SECRET generation failed; refusing to start with an empty signing key." >&2
    exit 1
  fi

  TURN_HOST="${TURN_HOST:-$(get_env_value TURN_HOST)}"
  TURN_EXTERNAL_IP="${TURN_EXTERNAL_IP:-$(get_env_value TURN_EXTERNAL_IP)}"
  TURN_EXTERNAL_IP="${TURN_EXTERNAL_IP:-$(detect_public_ip)}"
  TURN_HOST="${TURN_HOST:-$TURN_EXTERNAL_IP}"
  TURN_PRIVATE_IP="${TURN_PRIVATE_IP:-$(get_env_value TURN_PRIVATE_IP)}"
  TURN_PRIVATE_IP="${TURN_PRIVATE_IP:-$(detect_private_ip)}"
  INTERVIEW_TURN_SHARED_SECRET="${INTERVIEW_TURN_SHARED_SECRET:-$(get_env_value INTERVIEW_TURN_SHARED_SECRET)}"
  TURN_REALM="${TURN_REALM:-$(get_env_value TURN_REALM)}"
  TURN_REALM="${TURN_REALM:-autohr.local}"
  TURN_MIN_PORT="${TURN_MIN_PORT:-$(get_env_value TURN_MIN_PORT)}"
  TURN_MIN_PORT="${TURN_MIN_PORT:-49160}"
  TURN_MAX_PORT="${TURN_MAX_PORT:-$(get_env_value TURN_MAX_PORT)}"
  TURN_MAX_PORT="${TURN_MAX_PORT:-49200}"
  REDIS_HOST="${REDIS_HOST:-$(get_env_value REDIS_HOST)}"
  REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
  REDIS_PORT="${REDIS_PORT:-$(get_env_value REDIS_PORT)}"
  REDIS_PORT="${REDIS_PORT:-6379}"
  REDIS_PASSWORD="${REDIS_PASSWORD:-$(get_env_value REDIS_PASSWORD)}"
  REDIS_SSL_ENABLED="${REDIS_SSL_ENABLED:-$(get_env_value REDIS_SSL_ENABLED)}"
  REDIS_SSL_ENABLED="${REDIS_SSL_ENABLED:-false}"
  INTERVIEW_VIDEO_FFMPEG_PATH="${INTERVIEW_VIDEO_FFMPEG_PATH:-$(get_env_value INTERVIEW_VIDEO_FFMPEG_PATH)}"
  INTERVIEW_VIDEO_FFMPEG_PATH="${INTERVIEW_VIDEO_FFMPEG_PATH:-ffmpeg}"
  INTERVIEW_VIDEO_VIDEO_CODEC="${INTERVIEW_VIDEO_VIDEO_CODEC:-$(get_env_value INTERVIEW_VIDEO_VIDEO_CODEC)}"
  INTERVIEW_VIDEO_AUDIO_CODEC="${INTERVIEW_VIDEO_AUDIO_CODEC:-$(get_env_value INTERVIEW_VIDEO_AUDIO_CODEC)}"
  RESUME_OCR_ENABLED="${RESUME_OCR_ENABLED:-$(get_env_value RESUME_OCR_ENABLED)}"
  RESUME_OCR_TESSERACT_PATH="${RESUME_OCR_TESSERACT_PATH:-$(get_env_value RESUME_OCR_TESSERACT_PATH)}"
  RESUME_OCR_TESSERACT_PATH="${RESUME_OCR_TESSERACT_PATH:-tesseract}"
  RESUME_OCR_LANGUAGE="${RESUME_OCR_LANGUAGE:-$(get_env_value RESUME_OCR_LANGUAGE)}"
  RESUME_OCR_DPI="${RESUME_OCR_DPI:-$(get_env_value RESUME_OCR_DPI)}"
  RESUME_OCR_MAX_PAGES="${RESUME_OCR_MAX_PAGES:-$(get_env_value RESUME_OCR_MAX_PAGES)}"

  if [ -z "$TURN_HOST" ]; then
    echo "TURN_HOST is empty. Set TURN_HOST to this server's public IP or domain."
    exit 1
  fi

  if [ -z "$INTERVIEW_TURN_SHARED_SECRET" ]; then
    INTERVIEW_TURN_SHARED_SECRET="$(random_secret)"
  fi

  set_env_value INTERVIEW_STUN_URLS "stun:stun.l.google.com:19302,stun:stun.cloudflare.com:3478"
  set_env_value INTERVIEW_TURN_URLS "turn:${TURN_HOST}:3478?transport=udp,turn:${TURN_HOST}:3478?transport=tcp"
  set_env_value INTERVIEW_TURN_SHARED_SECRET "$INTERVIEW_TURN_SHARED_SECRET"
  set_env_value TURN_HOST "$TURN_HOST"
  set_env_value TURN_EXTERNAL_IP "$TURN_EXTERNAL_IP"
  set_env_value TURN_PRIVATE_IP "$TURN_PRIVATE_IP"
  set_env_value TURN_REALM "$TURN_REALM"
  set_env_value TURN_MIN_PORT "$TURN_MIN_PORT"
  set_env_value TURN_MAX_PORT "$TURN_MAX_PORT"
  set_env_value REDIS_HOST "$REDIS_HOST"
  set_env_value REDIS_PORT "$REDIS_PORT"
  set_env_value REDIS_PASSWORD "$REDIS_PASSWORD"
  set_env_value REDIS_SSL_ENABLED "$REDIS_SSL_ENABLED"
  set_env_value INTERVIEW_VIDEO_FFMPEG_PATH "$INTERVIEW_VIDEO_FFMPEG_PATH"
  chmod 600 "$ENV_FILE"
}

ensure_redis() {
  if [ -z "$REDIS_PASSWORD" ]; then
    REDIS_PASSWORD="$(random_secret)"
    set_env_value REDIS_PASSWORD "$REDIS_PASSWORD"
  fi

  if [ "$REDIS_HOST" = "127.0.0.1" ] || [ "$REDIS_HOST" = "localhost" ] || [ "$REDIS_HOST" = "::1" ]; then
    install_package redis-server
    sudo REDIS_PASSWORD="$REDIS_PASSWORD" python3 - <<'PY'
import os
from pathlib import Path

path = Path("/etc/redis/redis.conf")
lines = path.read_text(encoding="utf-8").splitlines()
replacements = {
    "bind": "bind 127.0.0.1 ::1",
    "protected-mode": "protected-mode yes",
    "requirepass": "requirepass " + os.environ["REDIS_PASSWORD"],
}
seen = set()
result = []
for line in lines:
    stripped = line.lstrip("# ").strip()
    key = stripped.split(maxsplit=1)[0] if stripped else ""
    if key in replacements:
        if key not in seen:
            result.append(replacements[key])
            seen.add(key)
        continue
    result.append(line)
for key, replacement in replacements.items():
    if key not in seen:
        result.append(replacement)
path.write_text("\n".join(result) + "\n", encoding="utf-8")
PY
    sudo systemctl enable --now redis-server
  fi

  local redis_response=""
  local -a redis_tls_args=()
  if [ "$REDIS_SSL_ENABLED" = "true" ]; then
    redis_tls_args+=(--tls)
  fi
  if [ -n "$REDIS_PASSWORD" ]; then
    redis_response="$(REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" "${redis_tls_args[@]}" ping 2>/dev/null || true)"
  else
    redis_response="$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" "${redis_tls_args[@]}" ping 2>/dev/null || true)"
  fi
  if [ "$redis_response" != "PONG" ]; then
    echo "Redis is unavailable at ${REDIS_HOST}:${REDIS_PORT}. Check REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, and REDIS_SSL_ENABLED."
    exit 1
  fi
}

ensure_database_prerequisites() {
  local db_type db_url db_username db_password postgres_url
  db_type="$(get_env_value DB_TYPE)"
  db_type="${db_type:-sqlite}"
  if [ "$db_type" = "sqlite" ]; then
    return
  fi
  if [ "$db_type" != "pgsql" ]; then
    echo "deploy-ubuntu.sh supports SQLite or an already provisioned PostgreSQL service; DB_TYPE=$db_type is not supported." >&2
    exit 1
  fi

  db_url="$(get_env_value DB_URL)"
  db_username="$(get_env_value DB_USERNAME)"
  db_password="$(get_env_value DB_PASSWORD)"
  if [ -z "$db_url" ] || [ -z "$db_username" ] || [ -z "$db_password" ]; then
    echo "DB_TYPE=pgsql requires non-empty DB_URL, DB_USERNAME, and DB_PASSWORD in .env." >&2
    echo "Provision PostgreSQL and its database/user first, or use DB_TYPE=sqlite for a local test." >&2
    exit 1
  fi
  if [[ "$db_password" == "change-this-password" || "$db_password" == "your_password" ]]; then
    echo "DB_PASSWORD still contains a placeholder value; refusing to start." >&2
    exit 1
  fi
  postgres_url="${db_url#jdbc:}"
  if ! PGPASSWORD="$db_password" psql "$postgres_url" -U "$db_username" -v ON_ERROR_STOP=1 -qAtc 'SELECT 1' >/dev/null 2>&1; then
    echo "PostgreSQL is unavailable or the configured credentials are invalid: $db_url" >&2
    echo "Provision the service/database/user before running this script, then retry." >&2
    exit 1
  fi
}

configure_coturn() {
  local turn_service="coturn"
  local external_ip_line="external-ip=${TURN_EXTERNAL_IP}"

  if [ -n "$TURN_PRIVATE_IP" ] && [ "$TURN_PRIVATE_IP" != "$TURN_EXTERNAL_IP" ]; then
    external_ip_line="external-ip=${TURN_EXTERNAL_IP}/${TURN_PRIVATE_IP}"
  fi

  echo "Configuring coturn on ${TURN_HOST}:3478 ..."
  sudo tee /etc/turnserver.conf >/dev/null <<EOF
listening-port=3478
listening-ip=0.0.0.0
${external_ip_line}
fingerprint
use-auth-secret
static-auth-secret=${INTERVIEW_TURN_SHARED_SECRET}
realm=${TURN_REALM}
server-name=${TURN_REALM}
no-multicast-peers
no-cli
min-port=${TURN_MIN_PORT}
max-port=${TURN_MAX_PORT}
verbose
log-binding
syslog
EOF
  sudo chown root:root /etc/turnserver.conf
  sudo chmod 600 /etc/turnserver.conf

  if [ -f /etc/default/coturn ]; then
    if grep -q '^TURNSERVER_ENABLED=' /etc/default/coturn; then
      sudo sed -i 's/^TURNSERVER_ENABLED=.*/TURNSERVER_ENABLED=1/' /etc/default/coturn
    else
      echo 'TURNSERVER_ENABLED=1' | sudo tee -a /etc/default/coturn >/dev/null
    fi
  fi

  if ! systemctl list-unit-files coturn.service >/dev/null 2>&1 && systemctl list-unit-files turnserver.service >/dev/null 2>&1; then
    turn_service="turnserver"
  fi

  sudo systemctl enable "$turn_service"
  sudo systemctl restart "$turn_service"
}

configure_firewall() {
  if ! command -v ufw >/dev/null 2>&1; then
    return
  fi

  if sudo ufw status 2>/dev/null | grep -q "Status: active"; then
    if [ -n "$APP_ALLOWED_CIDR" ]; then
      sudo ufw allow from "$APP_ALLOWED_CIDR" to any port "$BACKEND_PORT" proto tcp
      sudo ufw allow from "$APP_ALLOWED_CIDR" to any port "$FRONTEND_PORT" proto tcp
      sudo ufw allow from "$APP_ALLOWED_CIDR" to any port 3478 proto tcp
      sudo ufw allow from "$APP_ALLOWED_CIDR" to any port 3478 proto udp
      sudo ufw allow from "$APP_ALLOWED_CIDR" to any port "${TURN_MIN_PORT}:${TURN_MAX_PORT}" proto udp
      APP_FIREWALL_STATUS=restricted
    else
      APP_FIREWALL_STATUS=loopback
    fi
  fi
}

stop_existing_processes() {
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti tcp:"$BACKEND_PORT" | xargs -r kill || true
    lsof -ti tcp:"$FRONTEND_PORT" | xargs -r kill || true
  fi
}

start_backend() {
  echo "Building backend..."
  (cd "$BACKEND_DIR" && mvn -q -DskipTests package)

  local jar_file
  local -a jar_files=()
  mapfile -t jar_files < <(find "$BACKEND_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' -print)
  if [ "${#jar_files[@]}" -ne 1 ]; then
    echo "Expected exactly one backend jar; found ${#jar_files[@]}." >&2
    printf '%s\n' "${jar_files[@]}" >&2
    exit 1
  fi
  jar_file="${jar_files[0]}"

  echo "Starting backend on http://${APP_BIND_ADDRESS}:$BACKEND_PORT ..."
  SPRING_PROFILES_ACTIVE=prod nohup java -jar "$jar_file" --spring.profiles.active=prod \
    --server.address="$APP_BIND_ADDRESS" --server.port="$BACKEND_PORT" > "$LOG_DIR/backend.log" 2>&1 &
  echo $! > "$LOG_DIR/backend.pid"
}

start_frontend() {
  echo "Installing frontend dependencies..."
  (cd "$FRONTEND_DIR" && npm install)

  echo "Starting frontend on http://${APP_BIND_ADDRESS}:$FRONTEND_PORT ..."
  nohup npm --prefix "$FRONTEND_DIR" run dev -- --host "$APP_BIND_ADDRESS" --port "$FRONTEND_PORT" > "$LOG_DIR/frontend.log" 2>&1 &
  echo $! > "$LOG_DIR/frontend.pid"
}

wait_for_http() {
  local name="$1"
  local url="$2"
  local log_file="$3"
  local pid_file="$4"

  for attempt in $(seq 1 30); do
    if [ -f "$pid_file" ] && ! kill -0 "$(cat "$pid_file")" 2>/dev/null; then
      echo "$name exited before becoming healthy." >&2
      tail -n 80 "$log_file" >&2 || true
      exit 1
    fi
    if curl --fail --silent --show-error "$url" >/dev/null; then
      return
    fi
    sleep 2
  done
  echo "$name did not become healthy within 60 seconds: $url" >&2
  tail -n 80 "$log_file" >&2 || true
  exit 1
}

main() {
  mkdir -p "$LOG_DIR"
  ensure_dependencies
  validate_local_bind_address
  prepare_env
  ensure_database_prerequisites
  ensure_redis
  ensure_video_codecs
  ensure_resume_ocr
  configure_coturn
  configure_firewall
  stop_existing_processes
  start_backend
  wait_for_http "Backend" "http://${APP_BIND_ADDRESS}:${BACKEND_PORT}/api/auth/captcha" "$LOG_DIR/backend.log" "$LOG_DIR/backend.pid"
  start_frontend
  wait_for_http "Frontend" "http://${APP_BIND_ADDRESS}:${FRONTEND_PORT}/" "$LOG_DIR/frontend.log" "$LOG_DIR/frontend.pid"

  echo "AI School Examination System is healthy (local/LAN mode only)."
  echo "Backend:  http://${APP_BIND_ADDRESS}:$BACKEND_PORT"
  echo "Frontend: http://${APP_BIND_ADDRESS}:$FRONTEND_PORT"
  echo "TURN:     turn:$TURN_HOST:3478 udp/tcp"
  echo "TURN map: ${TURN_EXTERNAL_IP}${TURN_PRIVATE_IP:+/$TURN_PRIVATE_IP}"
  echo "Video:    ffmpeg=$INTERVIEW_VIDEO_FFMPEG_PATH; encoders=$INTERVIEW_VIDEO_VIDEO_CODEC,$INTERVIEW_VIDEO_AUDIO_CODEC"
  if [ -n "$APP_ALLOWED_CIDR" ]; then
    if [ "$APP_FIREWALL_STATUS" = "restricted" ]; then
      echo "UFW:      allow $APP_ALLOWED_CIDR to tcp $FRONTEND_PORT,$BACKEND_PORT,3478 and udp 3478,$TURN_MIN_PORT:$TURN_MAX_PORT"
    else
      echo "Firewall: UFW is inactive or unavailable; restrict the same ports to $APP_ALLOWED_CIDR in the active host/network firewall"
    fi
  else
    echo "Firewall: application ports remain loopback-only; no application UFW rules were added"
  fi
  echo "Logs:     $LOG_DIR"
  echo "Stop:     kill \$(cat logs/backend.pid) \$(cat logs/frontend.pid)"
}

main "$@"
