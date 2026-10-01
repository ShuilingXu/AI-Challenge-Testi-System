"""Run with python3 scripts/test-release-sqlite.py; no deployment or server needed."""

import importlib.util
from contextlib import closing
from pathlib import Path
import runpy
import sqlite3
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

SCRIPT_DIR = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("prepare_sqlite", SCRIPT_DIR / "prepare-release-sqlite.py")
prepare_sqlite = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare_sqlite)


class ReleaseSqliteTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "config").mkdir()
        (self.root / "data").mkdir()
        self.env = self.root / "config" / ".env"
        self.env.write_text("DB_TYPE=sqlite\nJWT_SECRET=keep\n", encoding="utf-8")

    def test_fresh_install_and_reinstall_use_writable_database(self):
        prepare_sqlite.prepare(self.root)
        first = self.env.read_text(encoding="utf-8")
        self.assertIn("SQLITE_FALLBACK_URL=jdbc:sqlite:" + str(self.root / "data" / "school_exam.db"), first)
        prepare_sqlite.prepare(self.root)
        self.assertEqual(first, self.env.read_text(encoding="utf-8"))

    def test_legacy_database_backup_includes_uncheckpointed_wal(self):
        source = sqlite3.connect(self.root / "school_exam.db")
        self.addCleanup(source.close)
        source.execute("PRAGMA journal_mode=WAL")
        source.execute("PRAGMA wal_autocheckpoint=0")
        source.execute("CREATE TABLE sample (value TEXT)")
        source.execute("INSERT INTO sample VALUES ('preserved')")
        source.commit()
        self.env.write_text("DB_TYPE=sqlite\nDB_URL=jdbc\\:sqlite\\:school_exam.db\n", encoding="utf-8")
        prepare_sqlite.prepare(self.root)
        with closing(sqlite3.connect(self.root / "data" / "school_exam.db")) as target:
            self.assertEqual([("preserved",)], target.execute("SELECT * FROM sample").fetchall())
        self.assertTrue((self.root / "school_exam.db").exists())
        self.assertIn("DB_URL=jdbc:sqlite:" + str(self.root / "data" / "school_exam.db"), self.env.read_text())

    def test_conflicting_databases_fail_without_replacing_either(self):
        for directory in (self.root, self.root / "data"):
            with closing(sqlite3.connect(directory / "school_exam.db")) as database:
                database.execute("CREATE TABLE sample (value TEXT)")
        before = self.env.read_bytes()
        with self.assertRaisesRegex(RuntimeError, "Both legacy"):
            prepare_sqlite.prepare(self.root)
        self.assertEqual(before, self.env.read_bytes())

    def test_custom_database_urls_are_preserved(self):
        content = "DB_TYPE=sqlite\nDB_URL=jdbc:sqlite:/custom/main.db\nSQLITE_FALLBACK_URL=jdbc:sqlite:/custom/fallback.db\n"
        self.env.write_text(content, encoding="utf-8")
        prepare_sqlite.prepare(self.root)
        self.assertEqual(content, self.env.read_text(encoding="utf-8"))

    def test_postgres_import_manifest_covers_the_current_schema(self):
        # Loading the manifest must not need a real PostgreSQL client/server.
        with patch.dict(sys.modules, {"psycopg": types.ModuleType("psycopg")}):
            migration = runpy.run_path(str(SCRIPT_DIR / "migrate-sqlite-to-postgres.py"))
        self.assertEqual(26, migration["EXPECTED_TABLE_COUNT"])
        self.assertIn("school_class_teacher", migration["TABLES"])
        self.assertIn("school_exam_recording", migration["TABLES"])


if __name__ == "__main__":
    unittest.main()
