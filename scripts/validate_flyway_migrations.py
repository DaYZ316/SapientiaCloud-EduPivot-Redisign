#!/usr/bin/env python3
"""Validate Flyway migration naming and ownership rules.

This script is read-only. It checks repository migration files and reports
errors for rules that would make future Flyway maintenance unsafe.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MIGRATION_RELATIVE = Path("src/main/resources/db/migration")

SERVICES = {
    "sc-auth": "auth_",
    "sc-course": "edu_",
    "sc-notification": "ntf_",
    "sc-storage": "storage_",
    "sc-ai": "ai_",
}

LEGACY_CROSS_SERVICE_TABLES = {
    ("sc-auth", "V2026060902__create_student_teacher_tables.sql"): {
        "edu_student",
        "edu_teacher",
    },
    ("sc-auth", "V2026060903__create_notification_tables.sql"): {
        "ntf_notification",
        "ntf_read_status",
    },
    ("sc-auth", "V2026061201__add_updated_at_trigger_and_timestamptz.sql"): {
        "edu_student",
        "edu_teacher",
    },
}

FILENAME_PATTERN = re.compile(r"^V(?P<version>\d{10})__(?P<description>[a-z0-9]+(?:_[a-z0-9]+)*)\.sql$")
TABLE_PATTERN = re.compile(
    r"\b(?:"
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?|"
    r"ALTER\s+TABLE\s+(?:ONLY\s+)?|"
    r"DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?|"
    r"COMMENT\s+ON\s+TABLE\s+"
    r")(?P<table>[a-zA-Z_][\w.]*)",
    re.IGNORECASE,
)
DROP_PATTERN = re.compile(r"^\s*DROP\s+(?:TABLE|COLUMN|INDEX|TRIGGER|CONSTRAINT)\b", re.IGNORECASE | re.MULTILINE)
DML_PATTERN = re.compile(r"^\s*(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM)\b", re.IGNORECASE | re.MULTILINE)
SELECT_PATTERN = re.compile(r"^\s*SELECT\b", re.IGNORECASE | re.MULTILINE)
CONCURRENT_INDEX_PATTERN = re.compile(r"\bCREATE\s+INDEX\s+CONCURRENTLY\b", re.IGNORECASE)


@dataclass(frozen=True)
class Migration:
    service: str
    path: Path
    version: str
    description: str
    content: str


def migration_dirs() -> list[tuple[str, Path]]:
    return [
        (service, ROOT / service / MIGRATION_RELATIVE)
        for service in sorted(SERVICES)
    ]


def read_migrations() -> tuple[list[Migration], list[str]]:
    migrations: list[Migration] = []
    errors: list[str] = []

    for service, directory in migration_dirs():
        if not directory.exists():
            errors.append(f"{service}: missing migration directory {directory.relative_to(ROOT)}")
            continue
        for path in sorted(directory.glob("*.sql")):
            match = FILENAME_PATTERN.match(path.name)
            if not match:
                errors.append(
                    f"{service}: invalid migration filename {path.relative_to(ROOT)} "
                    "(expected VyyyyMMddNN__snake_case.sql)"
                )
                continue
            migrations.append(
                Migration(
                    service=service,
                    path=path,
                    version=match.group("version"),
                    description=match.group("description"),
                    content=path.read_text(encoding="utf-8"),
                )
            )

    return migrations, errors


def extract_tables(sql: str) -> set[str]:
    tables: set[str] = set()
    for match in TABLE_PATTERN.finditer(sql):
        table = match.group("table").strip('"')
        if "." in table:
            table = table.rsplit(".", 1)[1].strip('"')
        tables.add(table)
    return tables


def validate_versions(migrations: list[Migration]) -> list[str]:
    errors: list[str] = []
    by_service: dict[str, dict[str, list[Migration]]] = {}
    for migration in migrations:
        by_service.setdefault(migration.service, {}).setdefault(migration.version, []).append(migration)

    for service, by_version in sorted(by_service.items()):
        for version, same_version in sorted(by_version.items()):
            if len(same_version) > 1:
                files = ", ".join(m.path.name for m in same_version)
                errors.append(f"{service}: duplicate Flyway version {version}: {files}")
    return errors


def validate_table_ownership(migrations: list[Migration]) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []

    for migration in migrations:
        expected_prefix = SERVICES[migration.service]
        legacy_allowed = LEGACY_CROSS_SERVICE_TABLES.get((migration.service, migration.path.name), set())
        tables = extract_tables(migration.content)

        for table in sorted(tables):
            if table.startswith(expected_prefix):
                continue
            if table in legacy_allowed:
                warnings.append(
                    f"{migration.service}: legacy cross-service table {table} in "
                    f"{migration.path.relative_to(ROOT)}"
                )
                continue
            errors.append(
                f"{migration.service}: table {table} in {migration.path.relative_to(ROOT)} "
                f"does not match required prefix {expected_prefix}"
            )

    return errors, warnings


def collect_sql_warnings(migrations: list[Migration]) -> list[str]:
    warnings: list[str] = []
    checks = [
        ("contains data DML", DML_PATTERN),
        ("contains top-level SELECT", SELECT_PATTERN),
        ("contains CREATE INDEX CONCURRENTLY", CONCURRENT_INDEX_PATTERN),
        ("contains DROP operation", DROP_PATTERN),
    ]
    for migration in migrations:
        for label, pattern in checks:
            if pattern.search(migration.content):
                warnings.append(f"{migration.service}: {label}: {migration.path.relative_to(ROOT)}")
    return warnings


def main() -> int:
    migrations, errors = read_migrations()
    errors.extend(validate_versions(migrations))
    ownership_errors, warnings = validate_table_ownership(migrations)
    errors.extend(ownership_errors)
    warnings.extend(collect_sql_warnings(migrations))

    for warning in warnings:
        print(f"WARN: {warning}")
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)

    print(f"Checked {len(migrations)} Flyway migrations across {len(SERVICES)} services.")
    if errors:
        print(f"Flyway migration validation failed with {len(errors)} error(s).", file=sys.stderr)
        return 1
    print("Flyway migration validation passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
