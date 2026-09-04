from datetime import datetime, timezone

from app.db import CatalogEntity, User, UserCheckin
from app.rankings import get_geography_ranking


def _entity(entity_id: str, kind: str, parent_id: str | None = None) -> CatalogEntity:
    now = datetime.now(timezone.utc)
    code = entity_id.split(":", 1)[-1]
    return CatalogEntity(
        id=entity_id,
        kind=kind,
        code=code,
        name=code,
        name_ascii=code,
        parent_id=parent_id,
        source="test",
        source_id=entity_id,
        source_version="test",
        metadata_json={},
        active=True,
        created_at=now,
        updated_at=now,
    )


def _user(db, user_id: str, display_name: str | None) -> None:
    now = datetime.now(timezone.utc)
    db.add(
        User(
            firebase_uid=user_id,
            email=f"{user_id}@example.com",
            display_name=display_name,
            created_at=now,
            updated_at=now,
        )
    )


def _checkin(db, user_id: str, entity_id: str, number: int) -> None:
    now = datetime(2024, 1, number, tzinfo=timezone.utc)
    db.add(
        UserCheckin(
            id=f"checkin-{user_id}-{number}-{entity_id}",
            user_id=user_id,
            entity_id=entity_id,
            visited_at=now,
            created_at=now,
            updated_at=now,
            deleted_at=None,
        )
    )


def test_geography_ranking_orders_unique_regions_then_countries(session) -> None:
    session.add_all(
        [
            _entity("country:US", "country"),
            _entity("country:CN", "country"),
            _entity("admin1:US-CA", "admin1", "country:US"),
            _entity("admin1:US-NY", "admin1", "country:US"),
            _entity("admin1:CN-31", "admin1", "country:CN"),
            _entity("admin1:US-TX", "admin1", "country:US"),
        ]
    )
    _user(session, "user-1", "Alice")
    _user(session, "user-2", None)
    _checkin(session, "user-1", "admin1:US-CA", 1)
    _checkin(session, "user-1", "admin1:US-CA", 2)  # repeated visit, one region
    _checkin(session, "user-1", "admin1:US-NY", 3)
    _checkin(session, "user-1", "admin1:CN-31", 4)
    _checkin(session, "user-2", "admin1:US-TX", 5)
    session.commit()

    result = get_geography_ranking(session, "user-1", limit=50)

    assert result["primaryMetric"] == "uniqueAdmin1"
    assert result["entries"][0] == {
        "rank": 1,
        "displayName": "Alice",
        "countryCount": 2,
        "admin1Count": 3,
        "isCurrentUser": True,
    }
    assert result["entries"][1]["displayName"] == "Explorer"
    assert result["entries"][1]["rank"] == 2
    assert result["me"]["admin1Count"] == 3


def test_geography_ranking_ignores_deleted_checkins_and_keeps_ties(session) -> None:
    session.add_all(
        [
            _entity("country:US", "country"),
            _entity("admin1:US-CA", "admin1", "country:US"),
            _entity("admin1:US-NY", "admin1", "country:US"),
            _entity("admin1:US-TX", "admin1", "country:US"),
            _entity("admin1:US-FL", "admin1", "country:US"),
        ]
    )
    _user(session, "user-1", "Alice")
    _user(session, "user-2", "Bob")
    _checkin(session, "user-1", "admin1:US-CA", 1)
    _checkin(session, "user-1", "admin1:US-NY", 2)
    _checkin(session, "user-1", "admin1:US-TX", 3)
    _checkin(session, "user-2", "admin1:US-CA", 4)
    _checkin(session, "user-2", "admin1:US-FL", 5)
    session.query(UserCheckin).filter_by(id="checkin-user-1-3-admin1:US-TX").one().deleted_at = datetime.now(
        timezone.utc
    )
    session.commit()

    result = get_geography_ranking(session, "user-1", limit=50)

    assert result["entries"][0]["admin1Count"] == 2
    assert result["entries"][0]["rank"] == 1
    assert result["entries"][1]["rank"] == 1
    assert result["me"]["admin1Count"] == 2
