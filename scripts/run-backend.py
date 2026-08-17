#!/usr/bin/env python3
import os
import shutil
import sys
import time
from pathlib import Path


project_root = Path(__file__).resolve().parent.parent
env_path = project_root / ".env"
target_dir = project_root / "backend" / "target"
jar_files = sorted(path for path in target_dir.glob("*.jar") if not path.name.endswith(".original"))

if len(jar_files) != 1:
    found = ", ".join(path.name for path in jar_files) or "none"
    raise SystemExit(f"Expected exactly one backend JAR in {target_dir}; found: {found}")
jar_path = jar_files[0]

# Run an immutable copy so a later build cannot replace files used by this JVM.
runtime_dir = project_root / "backend" / "runtime"
runtime_dir.mkdir(parents=True, exist_ok=True)
for stale_jar in runtime_dir.glob("*.jar"):
    try:
        stale_jar.unlink()
    except PermissionError:
        # A concurrently running JVM may still hold its immutable copy on Windows.
        pass
runtime_jar = runtime_dir / f"{jar_path.stem}-{os.getpid()}-{time.time_ns()}.jar"
shutil.copy2(jar_path, runtime_jar)

extra_args = sys.argv[1:]
os.chdir(project_root)
os.execvp("java", ["java", "-jar", str(runtime_jar), *extra_args])
