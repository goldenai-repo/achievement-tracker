from __future__ import annotations

import argparse
import json
from pathlib import Path

from app.catalog import parse_admin1, parse_country_info, upsert_catalog_records
from app.db import SessionLocal, init_db


def main() -> None:
    parser = argparse.ArgumentParser(description="Import GeoNames countries and admin1 records")
    parser.add_argument("--input-dir", type=Path, default=Path("../data/raw/geonames"))
    parser.add_argument("--source-version", required=True)
    parser.add_argument("--report", type=Path, default=Path("../data/reports/geonames-import.json"))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    country_path = args.input_dir / "countryInfo.txt"
    admin1_path = args.input_dir / "admin1CodesASCII.txt"
    countries = parse_country_info(country_path, args.source_version)
    country_codes = {record.code for record in countries}
    admin1, rejected = parse_admin1(admin1_path, args.source_version, country_codes)
    records = countries + admin1

    seen_ids: set[str] = set()
    duplicate_ids: list[str] = []
    for record in records:
        if record.id in seen_ids:
            duplicate_ids.append(record.id)
        seen_ids.add(record.id)
    rejected.extend(f"duplicate id: {record_id}" for record_id in duplicate_ids)

    if args.strict and rejected:
        raise SystemExit(f"strict import rejected {len(rejected)} rows")

    inserted = updated = 0
    if not args.dry_run:
        init_db()
        with SessionLocal() as session:
            inserted, updated = upsert_catalog_records(session, records)

    report = {
        "source": "geonames",
        "source_version": args.source_version,
        "dry_run": args.dry_run,
        "country_count": len(countries),
        "admin1_count": len(admin1),
        "record_count": len(records),
        "inserted": inserted,
        "updated": updated,
        "rejected_count": len(rejected),
        "rejected_examples": rejected[:100],
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
