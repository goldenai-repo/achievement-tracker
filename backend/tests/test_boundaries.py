from datetime import datetime, timezone

from app.boundaries import geojson_bounds, normalize_admin1_geojson
from app.db import CatalogEntity


def test_normalize_admin1_adds_application_catalog_id() -> None:
    now = datetime.now(timezone.utc)
    entities = [
        CatalogEntity(
            id="admin1:CN-31",
            kind="admin1",
            code="CN.31",
            name="Shanghai",
            name_ascii="Shanghai",
            parent_id="country:CN",
            source="test",
            source_id="1796236",
            source_version="test",
            metadata_json={},
            active=True,
            created_at=now,
            updated_at=now,
        )
    ]
    document = {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "properties": {"shapeISO": "CN-31", "shapeName": "Shanghai"},
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[120.0, 30.0], [121.0, 30.0], [121.0, 31.0], [120.0, 30.0]]],
                },
            }
        ],
    }

    normalized, unmatched = normalize_admin1_geojson(
        document,
        country_code="CN",
        catalog_entities=entities,
        source_version="test",
    )

    assert unmatched == []
    assert normalized["features"][0]["properties"]["catalog_id"] == "admin1:CN-31"
    assert normalized["features"][0]["properties"]["catalog_bounds"] == {
        "north": 31.0,
        "south": 30.0,
        "east": 121.0,
        "west": 120.0,
    }
    assert geojson_bounds(normalized, catalog_id="admin1:CN-31") == {
        "north": 31.0,
        "south": 30.0,
        "east": 121.0,
        "west": 120.0,
    }


def test_normalize_admin1_applies_manual_override_without_replacing_source_name() -> None:
    now = datetime.now(timezone.utc)
    entities = [
        CatalogEntity(
            id="admin1:CN-30",
            kind="admin1",
            code="CN.30",
            name="Guangdong",
            name_ascii="Guangdong",
            parent_id="country:CN",
            source="test",
            source_id="1809935",
            source_version="test",
            metadata_json={},
            active=True,
            created_at=now,
            updated_at=now,
        )
    ]
    document = {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "properties": {"shapeName": "Guangzhou Province"},
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[110.0, 20.0], [117.0, 20.0], [117.0, 25.0], [110.0, 20.0]]],
                },
            }
        ],
    }

    normalized, unmatched = normalize_admin1_geojson(
        document,
        country_code="CN",
        catalog_entities=entities,
        source_version="test",
        overrides={"Guangzhou Province": "admin1:CN-30"},
    )

    properties = normalized["features"][0]["properties"]
    assert unmatched == []
    assert properties["shapeName"] == "Guangzhou Province"
    assert properties["catalog_id"] == "admin1:CN-30"
    assert properties["catalog_match_method"] == "manual_override"
