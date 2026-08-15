"""Download and normalize one country's geoBoundaries ADM1 file.

Example:
    python -m scripts.download_boundaries --country-code CN --iso3 CHN \
        --output ../data/raw/boundaries

The generated GeoJSON is intentionally kept outside the Android APK. During
local development, place it in ``data/raw/boundaries`` and FastAPI will expose
it automatically. In production, upload it to the configured boundary asset
host or Cloud Storage and put its URL in the country catalog metadata as
``boundary_geojson_url``.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.request import Request, urlopen

from app.boundaries import normalize_admin1_geojson
from app.db import CatalogEntity, SessionLocal

API_TEMPLATE = "https://www.geoboundaries.org/api/current/gbOpen/{iso3}/ADM1/"


def _get_json(url: str) -> dict:
    request = Request(url, headers={"User-Agent": "achievement-tracker-boundary-pipeline/1.0"})
    with urlopen(request, timeout=60) as response:  # noqa: S310 - URL is provider-controlled
        return json.load(response)


def _download(url: str) -> dict:
    request = Request(url, headers={"User-Agent": "achievement-tracker-boundary-pipeline/1.0"})
    with urlopen(request, timeout=180) as response:  # noqa: S310 - URL comes from provider metadata
        return json.load(response)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--country-code", required=True, help="ISO-3166 alpha-2 code, e.g. CN")
    parser.add_argument("--iso3", required=True, help="ISO-3166 alpha-3 code, e.g. CHN")
    parser.add_argument(
        "--input",
        type=Path,
        help="Reuse an existing GeoJSON file and skip the provider download",
    )
    parser.add_argument("--output", type=Path, default=Path("data/raw/boundaries"))
    parser.add_argument(
        "--overrides",
        type=Path,
        default=Path("data/boundary_overrides.json"),
        help="JSON file containing source-name to catalog-id overrides",
    )
    parser.add_argument("--source-version", default="geoBoundaries-current-gbOpen")
    args = parser.parse_args()

    if args.input:
        source_document = json.loads(args.input.read_text(encoding="utf-8"))
    else:
        metadata = _get_json(API_TEMPLATE.format(iso3=args.iso3.upper()))
        geojson_url = metadata.get("gjDownloadURL") or metadata.get("simplifiedGeometryGeoJSON")
        if not geojson_url:
            raise SystemExit("geoBoundaries response did not contain a GeoJSON download URL")
        source_document = _download(geojson_url)

    overrides = {}
    if args.overrides.is_file():
        configured = json.loads(args.overrides.read_text(encoding="utf-8"))
        overrides = configured.get(args.country_code.upper(), {})

    with SessionLocal() as session:
        entities = session.query(CatalogEntity).all()
        normalized, unmatched = normalize_admin1_geojson(
            source_document,
            country_code=args.country_code,
            catalog_entities=entities,
            source_version=args.source_version,
            overrides=overrides,
        )

    args.output.mkdir(parents=True, exist_ok=True)
    output_path = args.output / f"{args.country_code.lower()}-admin1.geojson"
    output_path.write_text(
        json.dumps(normalized, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(json.dumps({"output": str(output_path), "unmatched": unmatched}, ensure_ascii=False))


if __name__ == "__main__":
    main()
