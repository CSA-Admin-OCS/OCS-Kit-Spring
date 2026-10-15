#!/usr/bin/env python3
"""Add ROLE_STUDENT to every user in the local SQLite database.

Usage:
    python3 scripts/add_student_role_all_users.py
    python3 scripts/add_student_role_all_users.py --apply
    python3 scripts/add_student_role_all_users.py --db /path/to/sqlite.db --apply
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DB = PROJECT_ROOT / "volumes" / "sqlite.db"
STUDENT_ROLE_NAME = "ROLE_STUDENT"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Add ROLE_STUDENT to all users in person_roles."
    )
    parser.add_argument(
        "--db",
        default=str(DEFAULT_DB),
        help=f"Path to SQLite DB (default: {DEFAULT_DB})",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Apply updates. Without this flag, runs in dry-run mode.",
    )
    return parser.parse_args()


def ensure_required_tables(cur: sqlite3.Cursor) -> None:
    required = {"person", "person_role", "person_roles"}
    cur.execute("SELECT name FROM sqlite_master WHERE type='table'")
    existing = {row[0] for row in cur.fetchall()}
    missing = sorted(required - existing)
    if missing:
        raise RuntimeError(f"Missing required table(s): {', '.join(missing)}")


def get_or_create_student_role(cur: sqlite3.Cursor, apply: bool) -> int:
    cur.execute("SELECT id FROM person_role WHERE name = ?", (STUDENT_ROLE_NAME,))
    row = cur.fetchone()
    if row:
        return int(row[0])

    if not apply:
        raise RuntimeError(
            f"{STUDENT_ROLE_NAME} does not exist in person_role. "
            "Re-run with --apply to create it."
        )

    cur.execute("INSERT INTO person_role(name) VALUES (?)", (STUDENT_ROLE_NAME,))
    return int(cur.lastrowid)


def count_users_missing_role(cur: sqlite3.Cursor, student_role_id: int) -> int:
    cur.execute(
        """
        SELECT COUNT(*)
        FROM person p
        WHERE NOT EXISTS (
            SELECT 1
            FROM person_roles pr
            WHERE pr.person_id = p.id
              AND pr.roles_id = ?
        )
        """,
        (student_role_id,),
    )
    return int(cur.fetchone()[0])


def apply_role_to_all_users(cur: sqlite3.Cursor, student_role_id: int) -> int:
    cur.execute(
        """
        INSERT INTO person_roles(person_id, roles_id)
        SELECT p.id, ?
        FROM person p
        WHERE NOT EXISTS (
            SELECT 1
            FROM person_roles pr
            WHERE pr.person_id = p.id
              AND pr.roles_id = ?
        )
        """,
        (student_role_id, student_role_id),
    )
    return cur.rowcount if cur.rowcount is not None else 0


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

        cur.execute("SELECT COUNT(*) FROM person")
        total_users = int(cur.fetchone()[0])

        student_role_id = get_or_create_student_role(cur, apply=args.apply)
        missing_before = count_users_missing_role(cur, student_role_id)

        print(f"Database: {db_path}")
        print(f"Total users: {total_users}")
        print(f"Student role id: {student_role_id}")
        print(f"Users missing {STUDENT_ROLE_NAME}: {missing_before}")

        if not args.apply:
            print("Dry run only. Re-run with --apply to write changes.")
            return 0

        inserted = apply_role_to_all_users(cur, student_role_id)
        conn.commit()

        missing_after = count_users_missing_role(cur, student_role_id)
        print(f"Rows inserted into person_roles: {inserted}")
        print(f"Users still missing {STUDENT_ROLE_NAME}: {missing_after}")
        return 0

    except Exception as exc:
        conn.rollback()
        print(f"Error: {exc}")
        return 1
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())