"""Integration check against an EMPTY, application-initialized disposable DB.

POSTGRES_TEST_DSN must point to an isolated test database. This script refuses
nonempty targets and never truncates them. Requires psycopg (version 3).
"""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import sqlite3
import tempfile

import psycopg

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("migration", ROOT / "scripts/migrate-sqlite-to-postgres.py")
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)


def run():
    dsn = os.environ["POSTGRES_TEST_DSN"]
    with psycopg.connect(dsn) as target, target.cursor() as cursor:
        migration.require_target_schema(cursor)
        migration.require_empty_target(cursor)
    with tempfile.TemporaryDirectory() as directory:
        source_path = Path(directory) / "fixture.db"
        with sqlite3.connect(source_path) as source:
            source.executescript((ROOT / "backend/src/main/resources/schema.sql").read_text(encoding="utf-8"))
            source.executescript("""
                INSERT INTO sys_user(id,username,password,role_code,display_name) VALUES(101,'migration_teacher','unused','LECTURER','测试教师');
                INSERT INTO school_class(id,major_name,class_name,class_code) VALUES(102,'CS','迁移班级','MIGRATION');
                INSERT INTO school_class_teacher(class_id,user_id) VALUES(102,999);
                INSERT INTO school_assessment_config(id,job_code,job_title,department_name,requirements,responsibilities,publish_date)
                    VALUES(103,'MIGRATION','迁移考试','CS','','','2026-10-01');
                INSERT INTO school_exam_candidate(id,job_id,full_name,mobile_phone,major) VALUES(104,103,'Test','unused','CS');
                INSERT INTO school_exam_process(id,recruitment_candidate_id,job_id,current_stage,stage_status,overall_status,process_status_view)
                    VALUES(105,104,103,'AI','PASSED','COMPLETED','考试已完成');
                INSERT INTO school_exam_recording(process_id,segment_no,file_name) VALUES(105,0,'recording.webm');
            """)
        with contextlib.redirect_stdout(io.StringIO()):
            migration.migrate(source_path, dsn, dry_run=True)
            try:
                migration.migrate(source_path, dsn)
            except psycopg.errors.ForeignKeyViolation:
                pass
            else:
                raise AssertionError("Invalid teacher reference was accepted")
        with psycopg.connect(dsn) as target, target.cursor() as cursor:
            migration.require_empty_target(cursor)
        print("PASS: invalid foreign key rolls back every target write")
        with sqlite3.connect(source_path) as source:
            source.execute("UPDATE school_class_teacher SET user_id=101")
        with contextlib.redirect_stdout(io.StringIO()):
            migration.migrate(source_path, dsn)
        with psycopg.connect(dsn) as target, target.cursor() as cursor:
            cursor.execute("SELECT class_id,user_id FROM school_class_teacher")
            assert cursor.fetchall() == [(102, 101)]
            cursor.execute("SELECT process_id,segment_no,file_name FROM school_exam_recording")
            assert cursor.fetchall() == [(105, 0, "recording.webm")]
            cursor.execute("SELECT class_name FROM school_class WHERE id=102")
            assert cursor.fetchone()[0] == "迁移班级"
            cursor.execute("INSERT INTO sys_user(username,password,role_code) VALUES('next_user','unused','STUDENT') RETURNING id")
            assert cursor.fetchone()[0] > 101
            target.rollback()
        print("PASS: all 26 tables covered, teacher links and recording metadata retained, Unicode and sequences preserved")
        with contextlib.redirect_stdout(io.StringIO()):
            try:
                migration.migrate(source_path, dsn)
            except SystemExit as error:
                assert "contains data" in str(error)
            else:
                raise AssertionError("Nonempty target was overwritten")
        print("PASS: repeat import refuses nonempty database")


if __name__ == "__main__":
    run()
