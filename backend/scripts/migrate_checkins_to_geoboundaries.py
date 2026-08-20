"""Migrate user data from the GeoNames catalog IDs to geoBoundaries IDs.

The source and target databases are explicit so the existing local database is
never modified accidentally. Entity IDs are mapped by ISO-3 country metadata
and normalized administrative names; visit dates, notes, users, and unlock
aggregates are preserved.

Example:
    python -m scripts.migrate_checkins_to_geoboundaries \
        --source-database-url sqlite:///../data/achievement_tracker.db \
        --target-database-url sqlite:///../data/achievement_tracker_geoboundaries.db \
        --dry-run
"""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, sessionmaker

from app.boundaries import _normalized
from app.db import Base, CatalogEntity, User, UserCheckin, UserUnlock


def _engine(database_url: str):
    connect_args = {"check_same_thread": False} if database_url.startswith("sqlite") else {}
    return create_engine(database_url, connect_args=connect_args, pool_pre_ping=True)


def _utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _country_iso3(entity: CatalogEntity, by_id: dict[str, CatalogEntity]) -> str | None:
    if entity.kind == "country":
        value = (entity.metadata_json or {}).get("iso3")
        return str(value).upper() if value else (entity.code.upper() if len(entity.code) == 3 else None)
    parent = by_id.get(entity.parent_id or "")
    return _country_iso3(parent, by_id) if parent else None


def _migration_name_keys(value: str | None) -> list[str]:
    """Use exact/suffix-stripped names without broad first-token fallbacks."""
    normalized = _normalized(value)
    if not normalized:
        return []
    keys = [normalized]
    suffixes = (
        " province",
        " prefecture",
        " municipality",
        " special administrative region",
        " autonomous region",
        " autonomous prefecture",
    )
    changed = True
    while changed:
        changed = False
        for key in tuple(keys):
            for suffix in suffixes:
                if key.endswith(suffix):
                    shortened = key[: -len(suffix)].strip()
                    if shortened and shortened not in keys:
                        keys.append(shortened)
                        changed = True
    return keys


def _build_mapping(
    source_entities: dict[str, CatalogEntity],
    target_entities: dict[str, CatalogEntity],
) -> tuple[dict[str, str], list[dict[str, Any]]]:
    target_countries = {
        entity.code.upper(): entity
        for entity in target_entities.values()
        if entity.kind == "country"
    }
    target_regions: dict[tuple[str, str], list[CatalogEntity]] = defaultdict(list)
    for entity in target_entities.values():
        if entity.kind != "admin1" or not entity.parent_id:
            continue
        iso3 = entity.parent_id.removeprefix("country:").upper()
        for value in (entity.name, entity.name_ascii):
            for key in _migration_name_keys(value):
                target_regions[(iso3, key)].append(entity)

    mapping: dict[str, str] = {}
    report: list[dict[str, Any]] = []
    for source_id, source in source_entities.items():
        iso3 = _country_iso3(source, source_entities)
        candidates: list[CatalogEntity] = []
        if source.kind == "country" and iso3 in target_countries:
            candidates = [target_countries[iso3]]
        elif source.kind == "admin1" and iso3:
            matches: dict[str, CatalogEntity] = {}
            for value in (source.name, source.name_ascii):
                for key in _migration_name_keys(value):
                    for candidate in target_regions.get((iso3, key), []):
                        matches[candidate.id] = candidate
            candidates = list(matches.values())

        if len(candidates) == 1:
            mapping[source_id] = candidates[0].id
            report.append({"source_id": source_id, "target_id": candidates[0].id, "status": "matched"})
        elif len(candidates) > 1:
            report.append(
                {
                    "source_id": source_id,
                    "status": "ambiguous",
                    "candidates": [candidate.id for candidate in candidates],
                }
            )
        else:
            report.append({"source_id": source_id, "status": "unmatched", "name": source.name, "iso3": iso3})
    return mapping, report


def _copy_user(target: Session, source_user: User) -> None:
    user = target.get(User, source_user.firebase_uid)
    if user is None:
        target.add(
            User(
                firebase_uid=source_user.firebase_uid,
                email=source_user.email,
                display_name=source_user.display_name,
                created_at=source_user.created_at,
                updated_at=source_user.updated_at,
            )
        )
    else:
        user.email = source_user.email
        user.display_name = source_user.display_name
        user.updated_at = source_user.updated_at


def _rebuild_unlocks(target: Session) -> None:
    target.query(UserUnlock).delete(synchronize_session=False)
    grouped: dict[tuple[str, str], list[UserCheckin]] = defaultdict(list)
    for checkin in target.scalars(select(UserCheckin).where(UserCheckin.deleted_at.is_(None))):
        grouped[(checkin.user_id, checkin.entity_id)].append(checkin)
    now = datetime.now(timezone.utc)
    for (user_id, entity_id), visits in grouped.items():
        timestamps = [_utc(visit.visited_at) for visit in visits]
        target.add(
            UserUnlock(
                user_id=user_id,
                entity_id=entity_id,
                first_checked_in_at=min(timestamps),
                last_checked_in_at=max(timestamps),
                visit_count=len(visits),
                updated_at=now,
            )
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-database-url", required=True)
    parser.add_argument("--target-database-url", required=True)
    parser.add_argument("--report", type=Path, default=Path("../data/reports/geoboundaries-migration.json"))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--allow-unmapped", action="store_true")
    args = parser.parse_args()
    if args.source_database_url == args.target_database_url:
        raise SystemExit("Source and target database URLs must be different")

    source_engine = _engine(args.source_database_url)
    target_engine = _engine(args.target_database_url)
    Base.metadata.create_all(target_engine)
    SourceSession = sessionmaker(bind=source_engine, autoflush=False, autocommit=False)
    TargetSession = sessionmaker(bind=target_engine, autoflush=False, autocommit=False)

    with SourceSession() as source, TargetSession() as target:
        source_entities = {entity.id: entity for entity in source.scalars(select(CatalogEntity))}
        target_entities = {entity.id: entity for entity in target.scalars(select(CatalogEntity))}
        mapping, entity_report = _build_mapping(source_entities, target_entities)
        source_checkins = list(source.scalars(select(UserCheckin).order_by(UserCheckin.created_at)))
        checkin_entity_ids = {checkin.entity_id for checkin in source_checkins}
        unmapped_checkins = [
            checkin.id for checkin in source_checkins if checkin.entity_id not in mapping
        ]
        blocked = [
            item
            for item in entity_report
            if item["source_id"] in checkin_entity_ids
            and item["status"] in {"unmatched", "ambiguous"}
        ]
        if (unmapped_checkins or blocked) and not args.allow_unmapped:
            result = {
                "source_database_url": args.source_database_url,
                "target_database_url": args.target_database_url,
                "dry_run": args.dry_run,
                "source_catalog_entities": len(source_entities),
                "target_catalog_entities": len(target_entities),
                "mapped_entities": len(mapping),
                "unmatched_or_ambiguous_entities": len(blocked),
                "unmapped_catalog_entities_total": sum(
                    item["status"] in {"unmatched", "ambiguous"} for item in entity_report
                ),
                "source_checkins": len(source_checkins),
                "migrated_checkins": 0,
                "unmapped_checkins": unmapped_checkins,
                "unmapped_checkin_entities": blocked,
                "entity_mapping": entity_report,
            }
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            raise SystemExit(
                f"Migration stopped: {len(blocked)} used catalog mappings and "
                f"{len(unmapped_checkins)} check-ins are unmapped. "
                f"See {args.report}."
            )

        migrated_count = 0
        if not args.dry_run:
            for user in source.scalars(select(User)):
                _copy_user(target, user)
            for source_checkin in source_checkins:
                target_id = mapping.get(source_checkin.entity_id)
                if target_id is None:
                    continue
                checkin = target.get(UserCheckin, source_checkin.id)
                values = {
                    "user_id": source_checkin.user_id,
                    "entity_id": target_id,
                    "visited_at": source_checkin.visited_at,
                    "note": source_checkin.note,
                    "latitude": source_checkin.latitude,
                    "longitude": source_checkin.longitude,
                    "created_at": source_checkin.created_at,
                    "updated_at": source_checkin.updated_at,
                    "deleted_at": source_checkin.deleted_at,
                }
                if checkin is None:
                    target.add(UserCheckin(id=source_checkin.id, **values))
                else:
                    for field, value in values.items():
                        setattr(checkin, field, value)
                migrated_count += 1
            target.flush()
            _rebuild_unlocks(target)
            target.commit()

    result = {
        "source_database_url": args.source_database_url,
        "target_database_url": args.target_database_url,
        "dry_run": args.dry_run,
        "source_catalog_entities": len(source_entities),
        "target_catalog_entities": len(target_entities),
        "mapped_entities": len(mapping),
        "unmatched_or_ambiguous_entities": len(blocked),
        "unmapped_catalog_entities_total": sum(
            item["status"] in {"unmatched", "ambiguous"} for item in entity_report
        ),
        "source_checkins": len(source_checkins),
        "migrated_checkins": migrated_count,
        "unmapped_checkins": unmapped_checkins,
        "entity_mapping": entity_report,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "entity_mapping"}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
