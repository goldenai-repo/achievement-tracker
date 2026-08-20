from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator, Optional

from sqlalchemy.orm import Session

from app.db import CatalogEntity


@dataclass(frozen=True)
class CatalogRecord:
    id: str
    kind: str
    code: str
    name: str
    name_ascii: Optional[str]
    parent_id: Optional[str]
    source_id: str
    source_version: str
    metadata: dict


def _fields(path: Path) -> Iterator[tuple[int, list[str]]]:
    with path.open("r", encoding="utf-8") as handle:
        for row_number, line in enumerate(handle, start=1):
            if not line.strip() or line.startswith("#"):
                continue
            yield row_number, line.rstrip("\n").split("\t")


def parse_country_info(path: Path, source_version: str) -> list[CatalogRecord]:
    records: list[CatalogRecord] = []
    for row_number, fields in _fields(path):
        if len(fields) < 17:
            raise ValueError(f"{path}:{row_number}: expected at least 17 columns")
        code = fields[0].strip().upper()
        name = fields[4].strip()
        source_id = fields[16].strip()
        if len(code) != 2 or not code.isalpha() or not name or not source_id:
            raise ValueError(f"{path}:{row_number}: invalid country row")
        records.append(
            CatalogRecord(
                id=f"country:{code}",
                kind="country",
                code=code,
                name=name,
                name_ascii=name,
                parent_id=None,
                source_id=source_id,
                source_version=source_version,
                metadata={
                    "iso3": fields[1].strip(),
                    "iso_numeric": fields[2].strip(),
                    "continent": fields[8].strip(),
                    "capital": fields[5].strip(),
                },
            )
        )
    return records


def parse_admin1(
    path: Path,
    source_version: str,
    country_codes: set[str],
) -> tuple[list[CatalogRecord], list[str]]:
    records: list[CatalogRecord] = []
    rejected: list[str] = []
    for row_number, fields in _fields(path):
        if len(fields) < 4:
            rejected.append(f"{path}:{row_number}: expected 4 columns")
            continue
        code, name, name_ascii, source_id = (field.strip() for field in fields[:4])
        country_code, separator, subdivision_code = code.partition(".")
        if not separator or not country_code or not subdivision_code or not name or not source_id:
            rejected.append(f"{path}:{row_number}: invalid admin1 code or name")
            continue
        country_code = country_code.upper()
        if country_code not in country_codes:
            rejected.append(f"{path}:{row_number}: missing parent country {country_code}")
            continue
        records.append(
            CatalogRecord(
                id=f"admin1:{country_code}-{subdivision_code}",
                kind="admin1",
                code=f"{country_code}.{subdivision_code}",
                name=name,
                name_ascii=name_ascii or name,
                parent_id=f"country:{country_code}",
                source_id=source_id,
                source_version=source_version,
                metadata={"country_code": country_code, "subdivision_code": subdivision_code},
            )
        )
    return records, rejected


def upsert_catalog_records(session: Session, records: list[CatalogRecord]) -> tuple[int, int]:
    inserted = 0
    updated = 0
    now = datetime.now(timezone.utc)
    for record in records:
        entity = session.get(CatalogEntity, record.id)
        if entity is None:
            entity = CatalogEntity(
                id=record.id,
                created_at=now,
                active=True,
            )
            session.add(entity)
            inserted += 1
        else:
            updated += 1
        entity.kind = record.kind
        entity.code = record.code
        entity.name = record.name
        entity.name_ascii = record.name_ascii
        entity.parent_id = record.parent_id
        entity.source = "geonames"
        entity.source_id = record.source_id
        entity.source_version = record.source_version
        entity.metadata_json = record.metadata
        entity.updated_at = now
    session.commit()
    return inserted, updated
