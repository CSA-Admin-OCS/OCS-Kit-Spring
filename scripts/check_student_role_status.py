#!/usr/bin/env python3
"""Run ROLE_STUDENT validation checks and print results.

Checks:
1) Number of users missing ROLE_STUDENT
2) Total people vs people that have ROLE_STUDENT

Usage:
    python3 scripts/check_student_role_status.py
    python3 scripts/check_student_role_status.py --db volumes/sqlite.db
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DB = PROJECT_ROOT / "volumes" / "sqlite.db"
STUDENT_ROLE = "ROLE_STUDENT"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Check whether every person has ROLE_STUDENT."
    )
    parser.add_argument(
        "--db",
        default=str(DEFAULT_DB),
        help=f"Path to SQLite DB (default: {DEFAULT_DB})",
    )
    return parser.parse_args()


def ensure_required_tables(cur: sqlite3.Cursor) -> None:
    required = {"person", "person_role", "person_roles"}
    cur.execute("SELECT name FROM sqlite_master WHERE type='table'")
    existing = {row[0] for row in cur.fetchall()}
    missing = sorted(required - existing)
    if missing:
        raise RuntimeError(f"Missing required table(s): {', '.join(missing)}")


def main() -> int:
    args = parse_args()
    db_path = Path(args.db).expanduser().resolve()

    if not db_path.exists():
        print(f"Error: database file not found: {db_path}")
        return 1

    conn = sqlite3.connect(str(db_path))
    try:
        cur = conn.cursor()
        ensure_required_tables(cur)

        cur.execute(
            """
            SELECT COUNT(*)
            FROM person p
            WHERE NOT EXISTS (
              SELECT 1
              FROM person_roles pr
              JOIN person_role r ON r.id = pr.roles_id
              WHERE pr.person_id = p.id AND r.name = ?
            )
            """,
            (STUDENT_ROLE,),
        )
        missing_count = int(cur.fetchone()[0])

        cur.execute("SELECT COUNT(*) FROM person")
        people_count = int(cur.fetchone()[0])

        cur.execute(
            """
            SELECT COUNT(DISTINCT pr.person_id)
            FROM person_roles pr
            JOIN person_role r ON r.id = pr.roles_id
            WHERE r.name = ?
            """,
            (STUDENT_ROLE,),
        )
        with_student_count = int(cur.fetchone()[0])

        print(f"Database: {db_path}")
        print(f"missing ROLE_STUDENT: {missing_count}")
        print(f"people: {people_count}")
        print(f"with ROLE_STUDENT: {with_student_count}")

        if missing_count == 0 and people_count == with_student_count:
            print("status: OK")
            return 0

        print("status: MISMATCH")
        return 2

    except Exception as exc:
        print(f"Error: {exc}")
        return 1
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())