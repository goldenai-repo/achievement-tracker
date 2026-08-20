from __future__ import annotations

import re
import unicodedata
from collections.abc import Iterator
from math import fsum
from typing import Any, Iterable

from app.db import CatalogEntity


def _normalized(value: Any) -> str:
    text = unicodedata.normalize("NFKD", str(value or ""))
    text = "".join(char for char in text if not unicodedata.combining(char))
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def _name_keys(value: Any) -> set[str]:
    """Return common English administrative-name variants for matching."""
    normalized = _normalized(value)
    if not normalized:
        return set()
    keys = {normalized}
    suffixes = (
        " province",
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
                        keys.add(shortened)
                        changed = True
    # Names such as "Guangxi Zhuang Autonomous Region" still contain an
    # ethnic qualifier after the province name. The first token is a useful
    # fallback for the GeoNames catalog while preserving the full key first.
    for key in tuple(keys):
        if " " in key:
            keys.add(key.split(" ", 1)[0])
    return keys


def _property(properties: dict[str, Any], *names: str) -> str:
    for name in names:
        value = properties.get(name)
        if value not in (None, ""):
            return str(value).strip()
    return ""


def _positions(value: Any) -> Iterator[tuple[float, float]]:
    """Yield longitude/latitude pairs from any GeoJSON geometry nesting."""
    if not isinstance(value, list):
        return
    if len(value) >= 2 and all(isinstance(item, (int, float)) for item in value[:2]):
        yield float(value[0]), float(value[1])
        return
    for child in value:
        yield from _positions(child)


def geometry_bounds(geometry: dict[str, Any] | None) -> dict[str, float] | None:
    if not geometry:
        return None
    if geometry.get("type") == "GeometryCollection":
        positions = (
            position
            for child in geometry.get("geometries", [])
            for position in _positions((child or {}).get("coordinates", []))
        )
    else:
        positions = _positions(geometry.get("coordinates", []))
    values = list(positions)
    if not values:
        return None
    longitudes, latitudes = zip(*values)
    return {
        "north": max(latitudes),
        "south": min(latitudes),
        "east": max(longitudes),
        "west": min(longitudes),
    }


def geometry_center(geometry: dict[str, Any] | None) -> tuple[float, float] | None:
    """Return a representative point from the largest polygon in a geometry."""
    focus = geometry_focus(geometry)
    if focus is not None:
        return focus["latitude"], focus["longitude"]
    if not geometry:
        return None
    if geometry.get("type") == "GeometryCollection":
        positions = (
            position
            for child in geometry.get("geometries", [])
            for position in _positions((child or {}).get("coordinates", []))
        )
    else:
        positions = _positions(geometry.get("coordinates", []))
    values = list(positions)
    if not values:
        return None
    longitudes, latitudes = zip(*values)
    return fsum(latitudes) / len(latitudes), fsum(longitudes) / len(longitudes)


def _polygon_rings(geometry: dict[str, Any] | None) -> Iterator[list[list[float]]]:
    if not geometry:
        return
    geometry_type = geometry.get("type")
    coordinates = geometry.get("coordinates")
    if geometry_type == "Polygon" and isinstance(coordinates, list) and coordinates:
        yield coordinates[0]
    elif geometry_type == "MultiPolygon" and isinstance(coordinates, list):
        for polygon in coordinates:
            if polygon:
                yield polygon[0]
    elif geometry_type == "GeometryCollection":
        for child in geometry.get("geometries", []):
            yield from _polygon_rings(child)


def _ring_focus(ring: list[list[float]]) -> tuple[float, float, float, dict[str, float]] | None:
    points = [
        (float(point[0]), float(point[1]))
        for point in ring
        if isinstance(point, list)
        and len(point) >= 2
        and isinstance(point[0], (int, float))
        and isinstance(point[1], (int, float))
    ]
    if len(points) < 3:
        return None
    if points[0] != points[-1]:
        points.append(points[0])
    cross_values = [
        points[index][0] * points[index + 1][1]
        - points[index + 1][0] * points[index][1]
        for index in range(len(points) - 1)
    ]
    area_twice = fsum(cross_values)
    if abs(area_twice) < 1e-12:
        longitude = fsum(point[0] for point in points[:-1]) / (len(points) - 1)
        latitude = fsum(point[1] for point in points[:-1]) / (len(points) - 1)
    else:
        longitude = fsum(
            (points[index][0] + points[index + 1][0]) * cross_values[index]
            for index in range(len(points) - 1)
        ) / (3 * area_twice)
        latitude = fsum(
            (points[index][1] + points[index + 1][1]) * cross_values[index]
            for index in range(len(points) - 1)
        ) / (3 * area_twice)
    longitudes, latitudes = zip(*points[:-1])
    bounds = {
        "north": max(latitudes),
        "south": min(latitudes),
        "east": max(longitudes),
        "west": min(longitudes),
    }
    return abs(area_twice), latitude, longitude, bounds


def geometry_focus(geometry: dict[str, Any] | None) -> dict[str, float] | None:
    """Return center and viewport bounds for the largest polygon component.

    Full geometry bounds can be misleading for countries with remote islands
    or antimeridian-spanning territories. The largest polygon is a stable,
    dependency-free approximation of the primary landmass for the default map
    view. The complete geometry is still preserved in the GeoJSON asset.
    """
    candidates = [focus for ring in _polygon_rings(geometry) if (focus := _ring_focus(ring))]
    if not candidates:
        return None
    _, latitude, longitude, bounds = max(candidates, key=lambda item: item[0])
    return {"latitude": latitude, "longitude": longitude, **bounds}


def geojson_focus(document: dict[str, Any]) -> dict[str, float] | None:
    """Return the largest-polygon focus across a GeoJSON FeatureCollection."""
    candidates = []
    for feature in document.get("features", []):
        geometry = feature.get("geometry") or {}
        candidates.extend(
            focus
            for ring in _polygon_rings(geometry)
            if (focus := _ring_focus(ring))
        )
    if not candidates:
        return None
    _, latitude, longitude, bounds = max(candidates, key=lambda item: item[0])
    return {"latitude": latitude, "longitude": longitude, **bounds}


def geojson_bounds(
    document: dict[str, Any],
    *,
    catalog_id: str | None = None,
) -> dict[str, float] | None:
    selected = []
    for feature in document.get("features", []):
        properties = feature.get("properties") or {}
        if catalog_id is None or properties.get("catalog_id") == catalog_id:
            selected.append(feature)
    feature_bounds = [
        bounds
        for feature in selected
        if (bounds := geometry_bounds(feature.get("geometry"))) is not None
    ]
    if not feature_bounds:
        return None
    return {
        "north": max(item["north"] for item in feature_bounds),
        "south": min(item["south"] for item in feature_bounds),
        "east": max(item["east"] for item in feature_bounds),
        "west": min(item["west"] for item in feature_bounds),
    }


def _catalog_id_for_feature(
    properties: dict[str, Any],
    country_code: str,
    by_code: dict[str, CatalogEntity],
    by_name: dict[str, CatalogEntity],
    by_id: dict[str, CatalogEntity],
    overrides: dict[str, str],
) -> tuple[str | None, str | None]:
    country_code = country_code.upper()
    feature_name = _property(properties, "shapeName", "name", "NAME_1", "NAME_1_EN", "NAME")
    for name in _name_keys(feature_name):
        override_id = overrides.get(name)
        if override_id and override_id in by_id:
            return override_id, "manual_override"

    shape_iso = _property(properties, "shapeISO", "shapeIso", "ISO_1", "GID_1")
    if shape_iso:
        normalized_code = shape_iso.replace("-", ".").replace("_", ".").upper()
        if normalized_code.startswith(f"{country_code}."):
            entity = by_code.get(normalized_code)
            if entity is not None:
                return entity.id, "provider_code"

    for name in _name_keys(feature_name):
        entity = by_name.get(name)
        if entity is not None:
            return entity.id, "normalized_name"
    return None, None


def normalize_admin1_geojson(
    document: dict[str, Any],
    *,
    country_code: str,
    catalog_entities: Iterable[CatalogEntity],
    source_version: str,
    overrides: dict[str, str] | None = None,
) -> tuple[dict[str, Any], list[str]]:
    """Normalize a country ADM1 FeatureCollection for MapLibre.

    The geometry is preserved, while every matched feature receives the
    application-owned ``catalog_id`` property. Unmatched features stay in the
    output and are reported so a data update cannot silently hide a region.
    """
    entities = [
        entity
        for entity in catalog_entities
        if entity.kind == "admin1" and entity.parent_id == f"country:{country_code.upper()}"
    ]
    by_code = {entity.code.upper(): entity for entity in entities}
    by_id = {entity.id: entity for entity in entities}
    by_name = {
        key: entity
        for entity in entities
        for value in (entity.name, entity.name_ascii)
        if value
        for key in _name_keys(value)
    }

    unmatched: list[str] = []
    overrides = {_normalized(key): value for key, value in (overrides or {}).items()}
    features: list[dict[str, Any]] = []
    for index, feature in enumerate(document.get("features", [])):
        properties = dict(feature.get("properties") or {})
        catalog_id, match_method = _catalog_id_for_feature(
            properties,
            country_code,
            by_code,
            by_name,
            by_id,
            overrides,
        )
        if catalog_id:
            properties["catalog_id"] = catalog_id
            properties["catalog_match_method"] = match_method
            feature_bounds = geometry_bounds(feature.get("geometry"))
            if feature_bounds is not None:
                properties["catalog_bounds"] = feature_bounds
        else:
            unmatched.append(
                _property(properties, "shapeName", "name", "NAME_1", "NAME")
                or f"feature-{index}"
            )
        features.append({**feature, "properties": properties})

    return (
        {
            "type": "FeatureCollection",
            "name": f"{country_code.upper()} ADM1",
            "source": "geoBoundaries",
            "source_version": source_version,
            "features": features,
        },
        unmatched,
    )
