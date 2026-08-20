"""Download and normalize a geoBoundaries-only country/admin1 catalog.

The provider supplies boundary geometry and metadata, not application catalog
IDs or center points. This script creates the application-owned IDs, derives
center points and bounds, and writes normalized per-country ADM1 GeoJSON files.

Example:
    python -m scripts.download_geoboundaries_catalog \
        --raw-dir ../data/raw/geoboundaries \
        --normalized-dir ../data/normalized/geoboundaries \
        --source-version geoBoundaries-current-gbOpen \
        --max-workers 8

Use ``--no-download`` to rebuild normalized output from already downloaded
provider files.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any
from urllib.request import Request, urlopen

from app.boundaries import _normalized, geojson_bounds, geojson_focus, geometry_center

API_BASE = "https://www.geoboundaries.org/api/current/gbOpen/ALL"
USER_AGENT = "achievement-tracker-geoboundaries-pipeline/1.0"


def _get_json(url: str, timeout: int = 60) -> Any:
    request = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=timeout) as response:  # noqa: S310 - provider URL
        return json.load(response)


def _download_json(url: str, destination: Path) -> None:
    request = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=240) as response:  # noqa: S310 - provider URL
        destination.write_bytes(response.read())


def _slug(value: str) -> str:
    value = value.casefold().encode("ascii", "ignore").decode("ascii")
    value = re.sub(r"[^a-z0-9]+", "-", value).strip("-")
    return value or "region"


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _metadata_by_iso(path: Path) -> dict[str, dict[str, Any]]:
    rows = json.loads(path.read_text(encoding="utf-8"))
    return {str(row["boundaryISO"]).upper(): row for row in rows if row.get("boundaryISO")}


def _download_missing(metadata: dict[str, dict[str, Any]], directory: Path, simplified: bool, workers: int) -> list[str]:
    directory.mkdir(parents=True, exist_ok=True)
    failures: list[str] = []
    tasks = {}
    with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        for iso3, row in sorted(metadata.items()):
            suffix = "_simplified" if simplified else ""
            path = directory / f"{iso3}{suffix}.geojson"
            if path.is_file() and path.stat().st_size > 0:
                continue
            url = row.get("simplifiedGeometryGeoJSON") if simplified else row.get("gjDownloadURL")
            if not url:
                failures.append(f"{iso3}: missing GeoJSON URL")
                continue
            tasks[pool.submit(_download_json, url, path)] = iso3
        for future in as_completed(tasks):
            iso3 = tasks[future]
            try:
                future.result()
            except Exception as error:  # noqa: BLE001 - report all provider failures
                failures.append(f"{iso3}: {error}")
    return sorted(failures)


def _feature_name(properties: dict[str, Any], index: int) -> str:
    for key in ("shapeName", "name", "NAME_1", "NAME_1_EN", "NAME"):
        value = properties.get(key)
        if value not in (None, ""):
            return str(value).strip()
    return f"Region {index + 1}"


def _country_overrides(path: Path, iso3: str) -> dict[str, dict[str, str]]:
    if not path.is_file():
        return {}
    configured = json.loads(path.read_text(encoding="utf-8"))
    return {
        _normalized(source_name): dict(value) if isinstance(value, dict) else {"name": str(value)}
        for source_name, value in configured.get(iso3, {}).items()
    }


def _catalog_id(iso3: str, name: str, shape_id: str, used: set[str]) -> str:
    base = f"admin1:{iso3}-{_slug(name)}"
    candidate = base
    if candidate in used:
        suffix = hashlib.sha1(shape_id.encode("utf-8")).hexdigest()[:8]
        candidate = f"{base}-{suffix}"
    used.add(candidate)
    return candidate


def _entity(
    *,
    entity_id: str,
    kind: str,
    code: str,
    name: str,
    parent_id: str | None,
    source_id: str,
    source_version: str,
    center: tuple[float, float] | None,
    bounds: dict[str, float] | None,
    full_bounds: dict[str, float] | None,
    metadata: dict[str, Any],
) -> dict[str, Any]:
    metadata = dict(metadata)
    if bounds:
        metadata["bounds"] = bounds
    if full_bounds and full_bounds != bounds:
        metadata["full_bounds"] = full_bounds
    return {
        "id": entity_id,
        "kind": kind,
        "code": code,
        "name": name,
        "name_ascii": name,
        "parent_id": parent_id,
        "source": "geoboundaries",
        "source_id": source_id,
        "source_version": source_version,
        "latitude": center[0] if center else None,
        "longitude": center[1] if center else None,
        "metadata": metadata,
        "active": True,
    }


def _normalize_country(
    iso3: str,
    adm0_metadata: dict[str, Any],
    adm0_document: dict[str, Any],
    adm1_metadata: dict[str, Any] | None,
    adm1_document: dict[str, Any] | None,
    output_boundary_dir: Path,
    source_version: str,
    overrides: dict[str, dict[str, str]],
) -> tuple[dict[str, Any], list[dict[str, Any]], dict[str, Any]]:
    country_id = f"country:{iso3}"
    country_full_bounds = geojson_bounds(adm0_document)
    country_focus = geojson_focus(adm0_document)
    country_bounds = (
        {key: country_focus[key] for key in ("north", "south", "east", "west")}
        if country_focus
        else country_full_bounds
    )
    country_center = (
        (country_focus["latitude"], country_focus["longitude"])
        if country_focus
        else None
    )
    boundary_file = f"{iso3.lower()}-admin1.geojson"
    country = _entity(
        entity_id=country_id,
        kind="country",
        code=iso3,
        name=str(adm0_metadata.get("boundaryName") or iso3),
        parent_id=None,
        source_id=str(adm0_metadata.get("boundaryID") or iso3),
        source_version=source_version,
        center=country_center,
        bounds=country_bounds,
        full_bounds=country_full_bounds,
        metadata={
            "iso3": iso3,
            "boundary_id": adm0_metadata.get("boundaryID"),
            "boundary_year": adm0_metadata.get("boundaryYearRepresented"),
            "boundary_build_date": adm0_metadata.get("buildDate"),
            "boundary_license": adm0_metadata.get("boundaryLicense"),
            "boundary_object": f"boundaries/{boundary_file}",
        },
    )

    regions: list[dict[str, Any]] = []
    normalized_features: list[dict[str, Any]] = []
    used_ids: set[str] = set()
    if adm1_document:
        for index, feature in enumerate(adm1_document.get("features", [])):
            properties = dict(feature.get("properties") or {})
            source_name = _feature_name(properties, index)
            override = overrides.get(_normalized(source_name), {})
            name = override.get("name", source_name)
            shape_id = str(properties.get("shapeID") or f"feature-{index + 1}")
            if override.get("id"):
                region_id = override["id"]
                if region_id in used_ids:
                    raise ValueError(f"Duplicate geoBoundaries override ID in {iso3}: {region_id}")
                used_ids.add(region_id)
            else:
                region_id = _catalog_id(iso3, name, shape_id, used_ids)
            region_code = region_id.removeprefix("admin1:")
            full_bounds = geojson_bounds({"type": "FeatureCollection", "features": [feature]})
            focus = geojson_focus({"type": "FeatureCollection", "features": [feature]})
            bounds = (
                {key: focus[key] for key in ("north", "south", "east", "west")}
                if focus
                else full_bounds
            )
            center = (
                (focus["latitude"], focus["longitude"])
                if focus
                else geometry_center(feature.get("geometry"))
            )
            region = _entity(
                entity_id=region_id,
                kind="admin1",
                code=region_code,
                name=name,
                parent_id=country_id,
                source_id=shape_id,
                source_version=source_version,
                center=center,
                bounds=bounds,
                full_bounds=full_bounds,
                metadata={
                    "country_code": iso3,
                    "shape_id": shape_id,
                    "source_name": source_name,
                    "boundary_id": adm1_metadata.get("boundaryID") if adm1_metadata else None,
                    "boundary_year": adm1_metadata.get("boundaryYearRepresented") if adm1_metadata else None,
                },
            )
            regions.append(region)
            properties.update(
                {
                    "catalog_id": region_id,
                    "catalog_match_method": "geoboundaries_source_feature",
                }
            )
            if source_name != name:
                properties["catalog_match_method"] = "manual_override"
                properties["catalog_name"] = name
                properties["catalog_source_name"] = source_name
            if bounds:
                properties["catalog_bounds"] = bounds
            if full_bounds and full_bounds != bounds:
                properties["catalog_full_bounds"] = full_bounds
            if center:
                properties["catalog_center"] = {"latitude": center[0], "longitude": center[1]}
            normalized_features.append({**feature, "properties": properties})

    output_boundary_dir.mkdir(parents=True, exist_ok=True)
    normalized_document = {
        "type": "FeatureCollection",
        "name": f"{iso3} ADM1",
        "source": "geoBoundaries",
        "source_version": source_version,
        "country_id": country_id,
        "features": normalized_features,
    }
    boundary_path = output_boundary_dir / boundary_file
    boundary_path.write_text(
        json.dumps(normalized_document, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    boundary_report = {
        "country": iso3,
        "file": boundary_file,
        "features": len(normalized_features),
        "bytes": boundary_path.stat().st_size,
        "sha256": _sha256(boundary_path),
        "has_admin1": bool(normalized_features),
    }
    return country, regions, boundary_report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw-dir", type=Path, default=Path("../data/raw/geoboundaries"))
    parser.add_argument("--normalized-dir", type=Path, default=Path("../data/normalized/geoboundaries"))
    parser.add_argument("--source-version", default="geoBoundaries-current-gbOpen")
    parser.add_argument("--max-workers", type=int, default=8)
    parser.add_argument("--no-download", action="store_true")
    parser.add_argument("--full-geometry", action="store_true", help="Download full rather than simplified geometry")
    parser.add_argument(
        "--overrides",
        type=Path,
        default=Path("data/geoboundaries_overrides.json"),
        help="Source-name to canonical-name/ID overrides",
    )
    args = parser.parse_args()

    metadata_dir = args.raw_dir / "metadata"
    adm0_dir = args.raw_dir / "provider" / "adm0"
    adm1_dir = args.raw_dir / "provider" / "adm1"
    metadata_dir.mkdir(parents=True, exist_ok=True)
    if not args.no_download:
        adm0_metadata = _get_json(f"{API_BASE}/ADM0/")
        adm1_metadata = _get_json(f"{API_BASE}/ADM1/")
        (metadata_dir / "adm0.json").write_text(json.dumps(adm0_metadata, ensure_ascii=False), encoding="utf-8")
        (metadata_dir / "adm1.json").write_text(json.dumps(adm1_metadata, ensure_ascii=False), encoding="utf-8")
    else:
        adm0_metadata = json.loads((metadata_dir / "adm0.json").read_text(encoding="utf-8"))
        adm1_metadata = json.loads((metadata_dir / "adm1.json").read_text(encoding="utf-8"))

    adm0_by_iso = {str(row["boundaryISO"]).upper(): row for row in adm0_metadata}
    adm1_by_iso = {str(row["boundaryISO"]).upper(): row for row in adm1_metadata}
    countries = sorted(adm0_by_iso)
    if not args.no_download:
        adm0_failures = _download_missing(adm0_by_iso, adm0_dir, not args.full_geometry, args.max_workers)
        adm1_failures = _download_missing(adm1_by_iso, adm1_dir, not args.full_geometry, args.max_workers)
    else:
        adm0_failures = []
        adm1_failures = []

    suffix = "_simplified" if not args.full_geometry else ""
    boundary_dir = args.normalized_dir / "boundaries"
    records: list[dict[str, Any]] = []
    boundary_reports: list[dict[str, Any]] = []
    failures = []
    for iso3 in countries:
        adm0_path = adm0_dir / f"{iso3}{suffix}.geojson"
        if not adm0_path.is_file():
            failures.append(f"{iso3}: ADM0 file missing")
            continue
        adm1_path = adm1_dir / f"{iso3}{suffix}.geojson"
        adm1_document = json.loads(adm1_path.read_text(encoding="utf-8")) if adm1_path.is_file() else None
        country, regions, report = _normalize_country(
            iso3,
            adm0_by_iso[iso3],
            json.loads(adm0_path.read_text(encoding="utf-8")),
            adm1_by_iso.get(iso3),
            adm1_document,
            boundary_dir,
            args.source_version,
            _country_overrides(args.overrides, iso3),
        )
        records.extend([country, *regions])
        boundary_reports.append(report)

    records.sort(key=lambda record: record["id"])
    args.normalized_dir.mkdir(parents=True, exist_ok=True)
    jsonl_path = args.normalized_dir / "catalog_entities.jsonl"
    with jsonl_path.open("w", encoding="utf-8") as stream:
        for record in records:
            stream.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")
    csv_path = args.normalized_dir / "catalog_entities.csv"
    fields = [
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
    manifest = {
        "schema_version": 1,
        "source": "geoboundaries",
        "source_version": args.source_version,
        "countries_in_metadata": len(countries),
        "countries_imported": sum(1 for record in records if record["kind"] == "country"),
        "admin1_imported": sum(1 for record in records if record["kind"] == "admin1"),
        "countries_with_admin1": sum(1 for item in boundary_reports if item["has_admin1"]),
        "countries_without_admin1": [item["country"] for item in boundary_reports if not item["has_admin1"]],
        "download_failures": sorted(adm0_failures + adm1_failures + failures),
        "boundaries": boundary_reports,
        "catalog_sha256": _sha256(jsonl_path),
    }
    (args.normalized_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    if manifest["download_failures"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
