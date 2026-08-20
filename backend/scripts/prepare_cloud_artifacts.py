"""Prepare catalog and boundary artifacts for Cloud SQL and Cloud Storage.

This script does not upload anything. It creates deterministic, reviewable
artifacts locally so the same files can be imported into Cloud SQL and copied
to a Cloud Storage bucket during deployment.

Example:
    python -m scripts.prepare_cloud_artifacts \
        --boundary-dir ../data/raw/boundaries \
        --output-dir ../data/cloud-artifacts \
        --source-version geoBoundaries-current-gbOpen
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.boundaries import geojson_bounds
from app.db import CatalogEntity, SessionLocal, init_db


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _entity_record(entity: CatalogEntity) -> dict[str, Any]:
    return {
        "id": entity.id,
        "kind": entity.kind,
        "code": entity.code,
        "name": entity.name,
        "name_ascii": entity.name_ascii,
        "parent_id": entity.parent_id,
        "source": entity.source,
        "source_id": entity.source_id,
        "source_version": entity.source_version,
        "latitude": entity.latitude,
        "longitude": entity.longitude,
        "metadata": dict(entity.metadata_json or {}),
        "active": entity.active,
    }


def _boundary_index(document: dict[str, Any]) -> dict[str, dict[str, Any]]:
    indexed: dict[str, dict[str, Any]] = {}
    for feature in document.get("features", []):
        properties = feature.get("properties") or {}
        catalog_id = properties.get("catalog_id")
        if not catalog_id:
            continue
        bounds = properties.get("catalog_bounds") or geojson_bounds(
            {"type": "FeatureCollection", "features": [feature]}
        )
        indexed[str(catalog_id)] = {
            "bounds": bounds,
            "source_name": properties.get("shapeName") or properties.get("name"),
        }
    return indexed


def _write_catalog(records: list[dict[str, Any]], output_dir: Path) -> dict[str, Any]:
    jsonl_path = output_dir / "catalog_entities.jsonl"
    with jsonl_path.open("w", encoding="utf-8") as stream:
        for record in records:
            stream.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")))
            stream.write("\n")

    csv_path = output_dir / "catalog_entities.csv"
    fields = list(records[0].keys()) if records else [
        "id", "kind", "code", "name", "name_ascii", "parent_id", "source",
        "source_id", "source_version", "latitude", "longitude", "metadata", "active",
    ]
    with csv_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for record in records:
            row = dict(record)
            row["metadata"] = json.dumps(row["metadata"], ensure_ascii=False, separators=(",", ":"))
            writer.writerow(row)

    return {
        "jsonl": {
            "file": jsonl_path.name,
            "records": len(records),
            "bytes": jsonl_path.stat().st_size,
            "sha256": _sha256(jsonl_path),
        },
        "csv": {
            "file": csv_path.name,
            "records": len(records),
            "bytes": csv_path.stat().st_size,
            "sha256": _sha256(csv_path),
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--boundary-dir", type=Path, default=Path("../data/raw/boundaries"))
    parser.add_argument("--output-dir", type=Path, default=Path("../data/cloud-artifacts"))
    parser.add_argument("--source-version", required=True)
    parser.add_argument(
        "--boundary-base-url",
        help="Optional public base URL, e.g. https://storage.googleapis.com/bucket/boundaries",
    )
    args = parser.parse_args()

    init_db()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    boundary_output = args.output_dir / "boundaries"
    boundary_output.mkdir(parents=True, exist_ok=True)

    boundary_by_entity: dict[str, dict[str, Any]] = {}
    boundary_by_country: dict[str, dict[str, Any]] = {}
    boundary_files: list[dict[str, Any]] = []
    for source_path in sorted(args.boundary_dir.glob("*.geojson")):
        document = _read_json(source_path)
        copied_path = boundary_output / source_path.name
        shutil.copy2(source_path, copied_path)
        indexed = _boundary_index(document)
        country_code = source_path.name.removesuffix("-admin1.geojson").upper()
        boundary_by_country[country_code] = {
            "file_name": source_path.name,
            "bounds": geojson_bounds(document),
        }
        boundary_by_entity.update(
            {
                entity_id: {**value, "file_name": source_path.name}
                for entity_id, value in indexed.items()
            }
        )
        unmatched = [
            (feature.get("properties") or {}).get("shapeName") or f"feature-{index}"
            for index, feature in enumerate(document.get("features", []))
            if not (feature.get("properties") or {}).get("catalog_id")
        ]
        boundary_files.append(
            {
                "file": source_path.name,
                "object": f"boundaries/{source_path.name}",
                "features": len(document.get("features", [])),
                "matched_features": len(indexed),
                "unmatched_features": unmatched,
                "bytes": copied_path.stat().st_size,
                "sha256": _sha256(copied_path),
            }
        )

    with SessionLocal() as session:
        entities = session.query(CatalogEntity).order_by(CatalogEntity.id).all()
        records = []
        for entity in entities:
            record = _entity_record(entity)
            boundary = boundary_by_entity.get(entity.id)
            if boundary is None and entity.kind == "country":
                boundary = boundary_by_country.get(entity.code.upper())
            if boundary:
                metadata = dict(record["metadata"])
                metadata["boundary_object"] = f"boundaries/{boundary['file_name']}"
                if boundary.get("bounds"):
                    metadata["bounds"] = boundary["bounds"]
                if args.boundary_base_url:
                    metadata["boundary_geojson_url"] = (
                        f"{args.boundary_base_url.rstrip('/')}/{boundary['file_name']}"
                    )
                record["metadata"] = metadata
            records.append(record)

    catalog_files = _write_catalog(records, args.output_dir)
    manifest = {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "catalog_source": "current local catalog_entities table",
        "source_version": args.source_version,
        "catalog": {
            **catalog_files,
            "counts_by_kind": {
                kind: sum(1 for record in records if record["kind"] == kind)
                for kind in sorted({record["kind"] for record in records})
            },
        },
        "boundaries": {
            "directory": "boundaries",
            "files": boundary_files,
            "total_features": sum(item["features"] for item in boundary_files),
            "total_matched_features": sum(item["matched_features"] for item in boundary_files),
        },
        "attribution": "geoBoundaries gbOpen data, CC-BY 4.0; preserve source attribution in deployment metadata.",
    }
    (args.output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
