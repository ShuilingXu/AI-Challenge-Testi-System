#!/usr/bin/env python3
"""One-time, transactional SQLite to PostgreSQL data migration for Auto HR."""

import argparse
from datetime import datetime, timedelta, timezone
import os
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import sys

try:
    import psycopg
except ImportError as error:
    raise SystemExit("Install psycopg[binary] before running this migration.") from error


TABLES = [
    # Parent rows must be copied before a source-candidate employee reference.
    # PostgreSQL's production FK is intentionally non-deferrable and therefore
    # cannot be postponed with SET CONSTRAINTS ALL DEFERRED.
    "sys_user", "hr_department", "recruitment_job", "recruitment_candidate",
    "hr_employee", "hr_integration_binding", "recruitment_resume_file",
    "hr_salary_history", "hr_performance_month", "hr_overtime_month",
    "hr_social_insurance_month", "hr_special_deduction_month", "hr_payroll_month",
    "user_dashboard_config",
    "interview_batch", "interview_question", "interview_candidate", "interview_submission",
    "sys_audit_log", "interview_knowledge_base", "interview_knowledge_item",
    "interview_job_knowledge_weight", "interview_llm_config", "interview_process_template",
    "interview_process_template_stage", "interview_process", "interview_process_stage",
    "interview_ai_record", "interview_video_session",
]

EXPECTED_TABLE_COUNT = 29
if len(TABLES) != EXPECTED_TABLE_COUNT:
    raise RuntimeError(f"Migration table list must contain {EXPECTED_TABLE_COUNT} tables, got {len(TABLES)}")

SCHEMA_PATH = Path(__file__).resolve().parent.parent / "backend" / "src" / "main" / "resources" / "schema.sql"


def require_complete_table_manifest():
    schema = SCHEMA_PATH.read_text(encoding="utf-8")
    schema_tables = set(re.findall(
        r"^\s*CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+([A-Za-z0-9_]+)\s*\(",
        schema,
        flags=re.IGNORECASE | re.MULTILINE,
    ))
    manifest_tables = set(TABLES)
    if len(TABLES) != len(manifest_tables):
        raise RuntimeError("Migration table manifest contains duplicate table names.")
    if schema_tables != manifest_tables:
        missing = sorted(schema_tables - manifest_tables)
        extra = sorted(manifest_tables - schema_tables)
        raise RuntimeError(
            "Migration table manifest differs from schema.sql; "
            f"missing={missing or 'none'}, extra={extra or 'none'}"
        )


require_complete_table_manifest()

TYPE_COLUMNS = {
    "smallint", "integer", "bigint", "numeric", "decimal", "real", "double precision",
    "boolean", "date", "time without time zone", "time with time zone",
    "timestamp without time zone", "timestamp with time zone",
}

# These nullable relationships can point to rows that have not been inserted yet,
# including rows in the same table. Insert them as NULL and restore them after all
# parent rows exist because production PostgreSQL constraints are non-deferrable.
DEFERRED_REFERENCE_COLUMNS = {
    "hr_department": ("parent_department_id", "manager_employee_id"),
    "hr_employee": ("manager_employee_id",),
}

DEFAULT_BACKUP_DIR = Path(__file__).resolve().parent.parent / "backups" / "postgres-migration"
# Coordinate one-time migrations with the application's startup migration runner.
# PostgreSQL advisory locks are session-scoped and are released automatically when
# the psycopg connection closes, including error paths.
MIGRATION_LOCK_ID = 4_154_857_282_026


def quote(identifier):
    return '"' + identifier.replace('"', '""') + '"'


def target_table_exists(cursor, table):
    cursor.execute(
        "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
        "WHERE table_schema = 'public' AND table_name = %s)",
        (table,),
    )
    return cursor.fetchone()[0]


def require_empty_target(cursor):
    nonempty_tables = []
    for table in TABLES:
        if not target_table_exists(cursor, table):
            continue
        cursor.execute(f"SELECT COUNT(*) FROM {quote(table)}")
        count = cursor.fetchone()[0]
        if count:
            nonempty_tables.append(f"{table} ({count})")
    if nonempty_tables:
        joined = ", ".join(nonempty_tables)
        raise SystemExit(
            "Target PostgreSQL contains data in: " + joined
            + ". Re-run only after verification with --force-overwrite."
        )


def require_target_schema(cursor):
    missing = [table for table in TABLES if not target_table_exists(cursor, table)]
    if missing:
        raise SystemExit(
            "Target PostgreSQL is missing required schema tables: " + ", ".join(missing)
            + ". Run the application schema migration before importing data."
        )


def target_column_types(cursor, table):
    cursor.execute(
        "SELECT column_name, data_type FROM information_schema.columns "
        "WHERE table_schema = 'public' AND table_name = %s",
        (table,),
    )
    return dict(cursor.fetchall())


def normalize_value(value, target_type):
    if value == "" and target_type in TYPE_COLUMNS:
        return None
    return value


def reset_sequences(cursor):
    for table in TABLES:
        if not target_table_exists(cursor, table):
            continue
        cursor.execute(
            "SELECT column_name FROM information_schema.columns "
            "WHERE table_schema = 'public' AND table_name = %s "
            "AND (column_default LIKE 'nextval%%' OR is_identity = 'YES')",
            (table,),
        )
        for (column,) in cursor.fetchall():
            cursor.execute("SELECT pg_get_serial_sequence(%s, %s)", (table, column))
            sequence = cursor.fetchone()[0]
            if sequence is None:
                continue
            cursor.execute(f"SELECT MAX({quote(column)}) FROM {quote(table)}")
            maximum = cursor.fetchone()[0]
            cursor.execute("SELECT setval(%s, %s, %s)", (sequence, maximum or 1, maximum is not None))


def backup_postgres(postgres_dsn, backup_dir, retention_days):
    pg_dump = shutil.which("pg_dump")
    if pg_dump is None:
        raise SystemExit("pg_dump is required before --force-overwrite can modify PostgreSQL.")
    destination_dir = Path(backup_dir).resolve()
    destination_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc)
    destination = destination_dir / f"autohr-before-overwrite-{timestamp:%Y%m%dT%H%M%SZ}.dump"
    try:
        subprocess.run(
            [pg_dump, "--format=custom", "--file", str(destination), postgres_dsn],
            check=True,
        )
    except subprocess.CalledProcessError as error:
        destination.unlink(missing_ok=True)
        raise SystemExit("PostgreSQL backup failed; overwrite migration was not started.") from error
    prune_expired_backups(destination_dir, retention_days, timestamp)
    print(f"PostgreSQL backup created: {destination}")
    return destination


def prune_expired_backups(backup_dir, retention_days, now):
    cutoff = now - timedelta(days=retention_days)
    for backup in backup_dir.glob("autohr-before-overwrite-*.dump"):
        modified_at = datetime.fromtimestamp(backup.stat().st_mtime, timezone.utc)
        if modified_at < cutoff:
            backup.unlink()
            print(f"Expired PostgreSQL backup removed: {backup}")


def migrate(sqlite_path, postgres_dsn, force_overwrite=False, dry_run=False,
            backup_dir=DEFAULT_BACKUP_DIR, backup_retention_days=5):
    source_path = Path(sqlite_path)
    if not source_path.is_file():
        raise SystemExit(f"SQLite source file does not exist: {source_path}")
    if backup_retention_days < 1:
        raise SystemExit("Backup retention must be at least one day.")
    if force_overwrite and not dry_run:
        backup_postgres(postgres_dsn, backup_dir, backup_retention_days)
    source = sqlite3.connect(f"{source_path.resolve().as_uri()}?mode=ro", uri=True)
    source.row_factory = sqlite3.Row
    try:
        source.execute("BEGIN")
        with psycopg.connect(postgres_dsn) as target:
            try:
                with target.cursor() as cursor:
                    cursor.execute("SELECT pg_advisory_lock(%s)", (MIGRATION_LOCK_ID,))
                    require_target_schema(cursor)
                    if not dry_run and not force_overwrite:
                        require_empty_target(cursor)
                    if not dry_run:
                        cursor.execute("SET CONSTRAINTS ALL DEFERRED")
                        if force_overwrite:
                            target_tables = [table for table in TABLES if target_table_exists(cursor, table)]
                            if target_tables:
                                table_list = ", ".join(quote(table) for table in target_tables)
                                cursor.execute(f"TRUNCATE TABLE {table_list} CASCADE")
                    deferred_updates = []
                    migrated_tables = set()
                    source_row_counts = {}
                    for table in TABLES:
                        columns = [row[1] for row in source.execute(f"PRAGMA table_info({quote(table)})")]
                        if not columns:
                            print(f"{table}: skipped (not present in SQLite source)")
                            continue
                        if not target_table_exists(cursor, table):
                            raise SystemExit(f"Target PostgreSQL table is missing: {table}")
                        migrated_tables.add(table)
                        target_types = target_column_types(cursor, table)
                        missing_columns = [column for column in columns if column not in target_types]
                        if missing_columns:
                            raise SystemExit(f"Target PostgreSQL table {table} is missing columns: {', '.join(missing_columns)}")
                        rows = source.execute(f"SELECT * FROM {quote(table)}").fetchall()
                        source_row_counts[table] = len(rows)
                        print(f"{table}: {len(rows)} rows")
                        if dry_run:
                            continue
                        if rows:
                            column_list = ", ".join(quote(column) for column in columns)
                            placeholders = ", ".join(["%s"] * len(columns))
                            deferred_columns = [
                                column for column in DEFERRED_REFERENCE_COLUMNS.get(table, ())
                                if column in columns
                            ]
                            values = []
                            for row in rows:
                                normalized = {
                                    column: normalize_value(row[column], target_types[column])
                                    for column in columns
                                }
                                for column in deferred_columns:
                                    if normalized[column] is not None:
                                        deferred_updates.append((table, row["id"], column, normalized[column]))
                                        normalized[column] = None
                                values.append(tuple(normalized[column] for column in columns))
                            cursor.executemany(
                                f"INSERT INTO {quote(table)} ({column_list}) VALUES ({placeholders})",
                                values,
                            )
                    if not dry_run:
                        for table, row_id, column, reference_id in deferred_updates:
                            cursor.execute(
                                f"UPDATE {quote(table)} SET {quote(column)} = %s WHERE id = %s",
                                (reference_id, row_id),
                            )
                            if cursor.rowcount != 1:
                                raise RuntimeError(
                                    f"Could not restore {table}.{column} for source row {row_id}"
                                )
                        reset_sequences(cursor)
                        for table, source_count in source_row_counts.items():
                            cursor.execute(f"SELECT COUNT(*) FROM {quote(table)}")
                            target_count = cursor.fetchone()[0]
                            if target_count != source_count:
                                raise RuntimeError(
                                    f"Row-count verification failed for {table}: "
                                    f"SQLite={source_count}, PostgreSQL={target_count}"
                                )
                    print(f"Migration table coverage: {len(migrated_tables)}/{EXPECTED_TABLE_COUNT}")
            except BaseException:
                target.rollback()
                print("PostgreSQL migration failed; all target changes were rolled back.", file=sys.stderr)
                raise
    finally:
        source.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sqlite_path")
    parser.add_argument("--dsn", default=os.environ.get("POSTGRES_DSN"))
    parser.add_argument("--force-overwrite", action="store_true", help="allow replacing a non-empty PostgreSQL target")
    parser.add_argument("--dry-run", action="store_true", help="validate tables and report source row counts without writing")
    parser.add_argument("--backup-dir", default=str(DEFAULT_BACKUP_DIR),
                        help="directory for pre-overwrite PostgreSQL backups")
    parser.add_argument("--backup-retention-days", type=int, default=5,
                        help="days to retain pre-overwrite backups (default: 5)")
    args = parser.parse_args()
    if not args.dsn:
        parser.error("--dsn or POSTGRES_DSN is required")
    migrate(args.sqlite_path, args.dsn, args.force_overwrite, args.dry_run,
            args.backup_dir, args.backup_retention_days)


if __name__ == "__main__":
    main()
