#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
RELEASE_DIR="$ROOT_DIR/release"
PACKAGE_NAME="auto-hr-release"
PACKAGE_DIR="$RELEASE_DIR/$PACKAGE_NAME"

if [ -d "$PACKAGE_DIR" ]; then
  rm -rf "$PACKAGE_DIR"
fi
mkdir -p "$PACKAGE_DIR/backend" "$PACKAGE_DIR/frontend" "$PACKAGE_DIR/uploads" "$PACKAGE_DIR/logs"

echo "Building backend tests..."
mvn -q -f "$BACKEND_DIR/pom.xml" test

echo "Building frontend..."
npm --prefix "$FRONTEND_DIR" ci
npm --prefix "$FRONTEND_DIR" run build

echo "Embedding frontend in the executable backend..."
rm -rf "$BACKEND_DIR/target/classes/static"
mkdir -p "$BACKEND_DIR/target/classes/static"
cp -R "$FRONTEND_DIR/dist/." "$BACKEND_DIR/target/classes/static/"
mvn -q -f "$BACKEND_DIR/pom.xml" -DskipTests package

mapfile -t JAR_FILES < <(find "$BACKEND_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' -print)
if [ "${#JAR_FILES[@]}" -ne 1 ]; then
  echo "Expected exactly one executable JAR in $BACKEND_DIR/target; found ${#JAR_FILES[@]}." >&2
  printf '%s\n' "${JAR_FILES[@]}" >&2
  exit 1
fi
JAR_FILE="${JAR_FILES[0]}"
cp "$JAR_FILE" "$PACKAGE_DIR/backend/auto-hr.jar"
cp -R "$FRONTEND_DIR/dist/." "$PACKAGE_DIR/frontend/"
cp "$ROOT_DIR/.env.example" "$PACKAGE_DIR/.env.example"
cp "$ROOT_DIR/scripts/start-release.sh" "$PACKAGE_DIR/start.sh"
cp "$ROOT_DIR/scripts/install-release.sh" "$PACKAGE_DIR/install-systemd.sh"
cp "$ROOT_DIR/scripts/auto-hr.service" "$PACKAGE_DIR/auto-hr.service"
chmod +x "$PACKAGE_DIR/start.sh" "$PACKAGE_DIR/install-systemd.sh"

(
  cd "$RELEASE_DIR"
  rm -f "$PACKAGE_NAME.zip"
  if command -v zip >/dev/null 2>&1; then
    zip -qr "$PACKAGE_NAME.zip" "$PACKAGE_NAME"
  else
    python3 - "$PACKAGE_NAME" "$PACKAGE_NAME.zip" <<'PY'
import pathlib
import sys
import zipfile

source = pathlib.Path(sys.argv[1])
with zipfile.ZipFile(sys.argv[2], "w", zipfile.ZIP_DEFLATED) as archive:
    for path in source.rglob("*"):
        if path.is_file():
            archive.write(path, path.as_posix())
PY
  fi
)

echo "Release package created: $RELEASE_DIR/$PACKAGE_NAME.zip"
