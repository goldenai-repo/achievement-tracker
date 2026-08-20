"""Import a prepared catalog JSONL artifact into any configured SQL database.

The operation is idempotent by the application-owned catalog entity ID, so the
same command can target local PostgreSQL/SQLite during validation and Cloud
SQL during deployment.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from app.db import CatalogEntity, SessionLocal, init_db


def _load_records(path: Path) -> list[dict[str, Any]]:
    records = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError as error:
            raise ValueError(f"Invalid JSON on line {line_number}: {error}") from error
        if not isinstance(record, dict):
            raise ValueError(f"Record on line {line_number} is not an object")
        records.append(record)
    return records


def _validate(records: list[dict[str, Any]]) -> None:
    ids = [record.get("id") for record in records]
    if any(not value for value in ids):
        raise ValueError("Every catalog record must have an id")
    if len(ids) != len(set(ids)):
        raise ValueError("Catalog artifact contains duplicate ids")
    id_set = set(ids)
    missing_parents = {
        record["parent_id"]
        for record in records
        if record.get("parent_id") and record["parent_id"] not in id_set
    }
    if missing_parents:
        raise ValueError(f"Catalog artifact contains missing parents: {sorted(missing_parents)}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--report", type=Path, default=Path("../data/reports/catalog-artifact-import.json"))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    records = _load_records(args.artifact)
    _validate(records)
    result = {"artifact": str(args.artifact), "records": len(records), "dry_run": args.dry_run}

    if not args.dry_run:
        init_db()
        with SessionLocal() as session:
            for record in records:
                entity = session.get(CatalogEntity, record["id"])
                values = {
                    "kind": record["kind"],
                    "code": record["code"],
                    "name": record["name"],
                    "name_ascii": record.get("name_ascii"),
                    "parent_id": record.get("parent_id"),
                    "source": record["source"],
                    "source_id": record["source_id"],
                    "source_version": record["source_version"],
                    "latitude": record.get("latitude"),
                    "longitude": record.get("longitude"),
                    "metadata_json": record.get("metadata") or {},
                    "active": record.get("active", True),
                }
                if entity is None:
                    from datetime import datetime, timezone

                    now = datetime.now(timezone.utc)
                    entity = CatalogEntity(id=record["id"], created_at=now, updated_at=now, **values)
                    session.add(entity)
                else:
                    for field, value in values.items():
                        setattr(entity, field, value)
                    from datetime import datetime, timezone

                    entity.updated_at = datetime.now(timezone.utc)
            session.commit()
        result["imported"] = len(records)

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
