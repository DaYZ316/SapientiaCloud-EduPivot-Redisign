#!/usr/bin/env python3
"""Audit SQL conventions adapted from Alibaba/P3C database rules.

The project is already in production shape, so this script keeps a small
legacy allowlist for old migrations and fails on newly introduced violations.
It is intentionally read-only.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVICES = ("sc-auth", "sc-course", "sc-notification", "sc-storage", "sc-ai")
MIGRATION_RELATIVE = Path("src/main/resources/db/migration")

LEGACY_FOREIGN_KEY_FILES = {
    "sc-auth/src/main/resources/db/migration/V2026050701__create_auth_user_tables.sql",
    "sc-storage/src/main/resources/db/migration/V2026061001__create_storage_tables.sql",
    "sc-ai/src/main/resources/db/migration/V2026061601__create_ai_tables.sql",
    "sc-ai/src/main/resources/db/migration/V2026062501__create_ai_live_summary_tables.sql",
}

LEGACY_UQ_FILES = {
    "sc-course/src/main/resources/db/migration/V2026061701__add_class_participant_seat_index.sql",
    "sc-course/src/main/resources/db/migration/V2026061801__create_live_practice_tables.sql",
    "sc-ai/src/main/resources/db/migration/V2026062501__create_ai_live_summary_tables.sql",
}

REMEDIATION_UQ_FILES = {
    # Forward-only remediation migrations mention the old names while renaming them.
    "sc-course/src/main/resources/db/migration/V2026062702__standardize_unique_index_names_and_comments.sql",
    "sc-ai/src/main/resources/db/migration/V2026062703__standardize_unique_constraint_names.sql",
}

CREATE_TABLE_PATTERN = re.compile(
    r"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?P<table>[a-zA-Z_][\w.]*)\s*\(",
    re.IGNORECASE,
)
COMMENT_TABLE_PATTERN = re.compile(
    r"\bCOMMENT\s+ON\s+TABLE\s+(?P<table>[a-zA-Z_][\w.]*)\s+IS\b",
    re.IGNORECASE,
)
FOREIGN_KEY_PATTERN = re.compile(
    r"\bFOREIGN\s+KEY\b|\bREFERENCES\s+[a-zA-Z_][\w.]*\b|\bON\s+DELETE\s+CASCADE\b",
    re.IGNORECASE,
)
UQ_NAME_PATTERN = re.compile(r"\buq_[a-zA-Z0-9_]*\b", re.IGNORECASE)
SELECT_STAR_PATTERN = re.compile(r"\bSELECT\s+\*", re.IGNORECASE)
COUNT_COLUMN_PATTERN = re.compile(r"\bCOUNT\s*\(\s*(?!\*)[^)]+\)", re.IGNORECASE)
LARGE_VARCHAR_PATTERN = re.compile(r"\bVARCHAR\s*\(\s*(\d+)\s*\)", re.IGNORECASE)
FLOAT_DOUBLE_PATTERN = re.compile(r"\b(FLOAT|DOUBLE)\b", re.IGNORECASE)


@dataclass(frozen=True)
class Finding:
    path: Path
    message: str
    line: int | None = None

    def format(self) -> str:
        rel = self.path.relative_to(ROOT)
        suffix = f":{self.line}" if self.line is not None else ""
        return f"{rel}{suffix}: {self.message}"


def normalize(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def migration_files() -> list[Path]:
    files: list[Path] = []
    for service in SERVICES:
        directory = ROOT / service / MIGRATION_RELATIVE
        if directory.exists():
            files.extend(sorted(directory.glob("*.sql")))
    return files


def source_sql_files() -> list[Path]:
    files: list[Path] = []
    for service in SERVICES:
        root = ROOT / service
        if root.exists():
            files.extend(root.rglob("*.sql"))
            files.extend(root.rglob("*.xml"))
            files.extend(root.rglob("*.java"))
    common = ROOT / "sc-common"
    if common.exists():
        files.extend(common.rglob("*.sql"))
        files.extend(common.rglob("*.xml"))
        files.extend(common.rglob("*.java"))
    return sorted(set(files))


def line_number(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def unqualified(name: str) -> str:
    return name.strip('"').rsplit(".", 1)[-1].strip('"')


def audit_migrations(files: list[Path]) -> tuple[list[Finding], list[Finding]]:
    errors: list[Finding] = []
    warnings: list[Finding] = []
    created_tables: dict[str, Path] = {}
    table_comments: set[str] = set()

    for path in files:
        rel = normalize(path)
        text = path.read_text(encoding="utf-8", errors="ignore")
        for match in CREATE_TABLE_PATTERN.finditer(text):
            created_tables[unqualified(match.group("table"))] = path
        for match in COMMENT_TABLE_PATTERN.finditer(text):
            table_comments.add(unqualified(match.group("table")))

        for match in FOREIGN_KEY_PATTERN.finditer(text):
            finding = Finding(path, "database foreign keys/cascades are not allowed by the project P3C profile", line_number(text, match.start()))
            if rel in LEGACY_FOREIGN_KEY_FILES:
                warnings.append(finding)
            else:
                errors.append(finding)

        for match in UQ_NAME_PATTERN.finditer(text):
            finding = Finding(path, "unique indexes/constraints should use the uk_ prefix, not uq_", line_number(text, match.start()))
            if rel in REMEDIATION_UQ_FILES:
                continue
            if rel in LEGACY_UQ_FILES:
                warnings.append(finding)
            else:
                errors.append(finding)

        for match in LARGE_VARCHAR_PATTERN.finditer(text):
            if int(match.group(1)) > 5000:
                errors.append(Finding(path, "VARCHAR length exceeds 5000; use TEXT or split wide data", line_number(text, match.start())))

        for match in FLOAT_DOUBLE_PATTERN.finditer(text):
            errors.append(Finding(path, "FLOAT/DOUBLE should be avoided in table DDL; use exact numeric types when precision matters", line_number(text, match.start())))

    for table, path in sorted(created_tables.items()):
        if table not in table_comments:
            errors.append(Finding(path, f"missing COMMENT ON TABLE for {table}"))

    return errors, warnings


def audit_query_text(files: list[Path]) -> list[Finding]:
    errors: list[Finding] = []
    for path in files:
        text = path.read_text(encoding="utf-8", errors="ignore")
        for match in SELECT_STAR_PATTERN.finditer(text):
            errors.append(Finding(path, "avoid SELECT *; specify required columns", line_number(text, match.start())))
        if path.suffix.lower() in {".sql", ".xml"}:
            for match in COUNT_COLUMN_PATTERN.finditer(text):
                errors.append(Finding(path, "use COUNT(*) instead of COUNT(column) or COUNT(constant)", line_number(text, match.start())))
    return errors


def main() -> int:
    migrations = migration_files()
    errors, warnings = audit_migrations(migrations)
    errors.extend(audit_query_text(source_sql_files()))

    for warning in warnings:
        print(f"WARN: {warning.format()}")
    for error in errors:
        print(f"ERROR: {error.format()}", file=sys.stderr)

    print(f"Checked {len(migrations)} migration files and SQL-bearing source files.")
    if errors:
        print(f"P3C SQL audit failed with {len(errors)} error(s).", file=sys.stderr)
        return 1
    print("P3C SQL audit passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
