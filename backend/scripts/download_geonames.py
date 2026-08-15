from __future__ import annotations

import argparse
import hashlib
import json
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

BASE_URL = "https://download.geonames.org/export/dump"
FILES = ("countryInfo.txt", "admin1CodesASCII.txt", "readme.txt")
COORDINATE_FILES = ("allCountries.zip",)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_file(url: str, destination: Path, force: bool) -> dict:
    if destination.exists() and not force:
        return {"status": "skipped", "path": str(destination), "sha256": sha256(destination)}

    temporary = destination.with_suffix(destination.suffix + ".part")
    temporary.unlink(missing_ok=True)
    with urllib.request.urlopen(url, timeout=60) as response, temporary.open("wb") as handle:
        while chunk := response.read(1024 * 1024):
            handle.write(chunk)
    temporary.replace(destination)
    return {"status": "downloaded", "path": str(destination), "sha256": sha256(destination)}


def main() -> None:
    parser = argparse.ArgumentParser(description="Download GeoNames catalog source files")
    parser.add_argument("--output-dir", type=Path, default=Path("../data/raw/geonames"))
    parser.add_argument("--source-version", required=True)
    parser.add_argument("--force", action="store_true")
    parser.add_argument(
        "--include-coordinates",
        action="store_true",
        help="Also download GeoNames allCountries.zip for latitude/longitude enrichment",
    )
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    results = []
    filenames = FILES + (COORDINATE_FILES if args.include_coordinates else ())
    for filename in filenames:
        destination = args.output_dir / filename
        result = download_file(f"{BASE_URL}/{filename}", destination, args.force)
        result.update(
            {
                "source": "geonames",
                "source_url": f"{BASE_URL}/{filename}",
                "source_version": args.source_version,
                "downloaded_at": datetime.now(timezone.utc).isoformat(),
                "byte_size": destination.stat().st_size,
            }
        )
        results.append(result)

    metadata_path = args.output_dir / "download-manifest.json"
    metadata_path.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    for result in results:
        print(f"{result['status']}: {result['path']} ({result['byte_size']} bytes)")
    print(f"manifest: {metadata_path}")


if __name__ == "__main__":
    main()
