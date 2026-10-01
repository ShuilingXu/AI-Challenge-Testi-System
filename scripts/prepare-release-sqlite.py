#!/usr/bin/env python3
"""Relocate the default SQLite database while the application is stopped.

Custom database URLs are preserved. SQLite's backup API also incorporates a
legacy WAL, and the original database is retained for operator recovery.
"""

import os
from contextlib import closing
from pathlib import Path
import re
import sqlite3
import sys
import tempfile


def property_value(value):
    # SystemConfigService writes Java properties escapes, including escaped colons.
    return re.sub(r"\\(.)", lambda match: match.group(1), value).strip()


def prepare(install_dir):
    root = Path(install_dir)
    env_path = root / "config" / ".env"
    lines = env_path.read_text(encoding="utf-8").splitlines()
    config = {}
    for line in lines:
        match = re.match(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)", line)
        if match:
            config[match[1]] = property_value(match[2])
    default_url = "jdbc:sqlite:school_exam.db"
    destination = root / "data" / "school_exam.db"
    relocated_url = "jdbc:sqlite:" + str(destination)
    updates = {}
    if config.get("SQLITE_FALLBACK_URL", "") in ("", default_url):
        updates["SQLITE_FALLBACK_URL"] = relocated_url
    if config.get("DB_TYPE", "").lower() == "sqlite" and config.get("DB_URL", "") == default_url:
        updates["DB_URL"] = relocated_url
    if not updates:
        return
    legacy = root / "school_exam.db"
    if legacy.exists():
        if destination.exists():
            raise RuntimeError("Both legacy and relocated SQLite databases exist; reconcile them before reinstalling")
        temporary = destination.with_suffix(".db.next")
        try:
            with closing(sqlite3.connect(legacy.resolve().as_uri() + "?mode=ro", uri=True)) as source:
                with closing(sqlite3.connect(temporary)) as target:
                    source.backup(target)
            os.chmod(temporary, 0o600)
            os.replace(temporary, destination)
        finally:
            temporary.unlink(missing_ok=True)
    result = []
    pending = dict(updates)
    for line in lines:
        match = re.match(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s*=", line)
        if match and match[1] in updates:
            result.append(match[1] + "=" + updates[match[1]])
            pending.pop(match[1], None)
        else:
            result.append(line)
    result.extend(key + "=" + value for key, value in pending.items())
    descriptor, name = tempfile.mkstemp(dir=env_path.parent, prefix=".sqlite-env-")
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            output.write("\n".join(result) + "\n")
        os.chmod(name, 0o600)
        os.replace(name, env_path)
    finally:
        Path(name).unlink(missing_ok=True)


if __name__ == "__main__":
    prepare(sys.argv[1])
