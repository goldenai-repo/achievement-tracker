from datetime import datetime, timezone

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.auth import CurrentUser
from app.checkins import (
    CheckInCreate,
    CheckInUpdate,
    create_checkin,
    delete_checkin,
    get_summary,
    list_achievements,
    list_checkins,
    update_checkin,
)
from app.db import Base, CatalogEntity, UserUnlock


@pytest.fixture()
def session(tmp_path):
    engine = create_engine(
        f"sqlite:///{tmp_path / 'checkins.db'}",
        connect_args={"check_same_thread": False},
    )
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine)
    with session_factory() as db:
        db.add(
            CatalogEntity(
                id="country:US",
                kind="country",
                code="US",
                name="United States",
                name_ascii="United States",
                source="test",
                source_id="6252001",
                source_version="test",
                metadata_json={},
                active=True,
                created_at=datetime.now(timezone.utc),
                updated_at=datetime.now(timezone.utc),
            )
        )
        db.commit()
        yield db


def test_checkin_creates_unique_unlock_and_preserves_visit_count(session) -> None:
    user = CurrentUser(uid="user-1", email="user@example.com", display_name=None)
    visited_at = datetime(2024, 1, 1, tzinfo=timezone.utc)
    payload = CheckInCreate(entity_id="country:US", visited_at=visited_at, note="First trip")

    first = create_checkin(session, user, payload)
    second = create_checkin(
        session,
        user,
        CheckInCreate(entity_id="country:US", visited_at=datetime(2025, 1, 1)),
    )

    assert first.entity_id == "country:US"
    assert second.entity_id == "country:US"
    unlock = session.get(UserUnlock, ("user-1", "country:US"))
    assert unlock is not None
    assert unlock.visit_count == 2
    assert get_summary(session, "user-1") == {
        "checkinCount": 2,
        "uniqueUnlockCount": 1,
        "byKind": {"country": 1},
    }
    assert len(list_checkins(session, "user-1", 50)) == 2


def test_checkin_rejects_unknown_catalog_entity(session) -> None:
    from fastapi import HTTPException

    user = CurrentUser(uid="user-1", email=None, display_name=None)
    with pytest.raises(HTTPException) as error:
        create_checkin(
            session,
            user,
            CheckInCreate(entity_id="country:XX", visited_at=datetime.now(timezone.utc)),
        )
    assert error.value.status_code == 404


def test_achievements_group_repeated_checkins_by_entity(session) -> None:
    user = CurrentUser(uid="user-1", email="user@example.com", display_name=None)
    create_checkin(
        session,
        user,
        CheckInCreate(entity_id="country:US", visited_at=datetime(2024, 1, 1), note="First trip"),
    )
    create_checkin(
        session,
        user,
        CheckInCreate(entity_id="country:US", visited_at=datetime(2025, 1, 1), note="Return trip"),
    )

    achievements = list_achievements(session, user.uid, 50)

    assert len(achievements) == 1
    assert achievements[0]["entity"]["id"] == "country:US"
    assert achievements[0]["visitCount"] == 2
    assert [item["note"] for item in achievements[0]["checkins"]] == [
        "Return trip",
        "First trip",
    ]


def test_country_checkin_requires_admin1_when_catalog_has_regions(session) -> None:
    session.add(
        CatalogEntity(
            id="admin1:US-CA",
            kind="admin1",
            code="US-CA",
            name="California",
            name_ascii="California",
            parent_id="country:US",
            source="test",
            source_id="5332921",
            source_version="test",
            metadata_json={},
            active=True,
            created_at=datetime.now(timezone.utc),
            updated_at=datetime.now(timezone.utc),
        )
    )
    session.commit()

    from fastapi import HTTPException

    with pytest.raises(HTTPException) as error:
        create_checkin(
            session,
            CurrentUser(uid="user-1", email=None, display_name=None),
            CheckInCreate(entity_id="country:US", visited_at=datetime.now(timezone.utc)),
        )

    assert error.value.status_code == 422


def test_delete_checkin_recalculates_unlock_and_removes_empty_place(session) -> None:
    user = CurrentUser(uid="user-1", email="user@example.com", display_name=None)
    first = create_checkin(
        session,
        user,
        CheckInCreate(entity_id="country:US", visited_at=datetime(2024, 1, 1), note="First trip"),
    )
    second = create_checkin(
        session,
        user,
        CheckInCreate(entity_id="country:US", visited_at=datetime(2025, 1, 1), note="Return trip"),
    )

    delete_checkin(session, user, second.id)

    assert get_summary(session, user.uid) == {
        "checkinCount": 1,
        "uniqueUnlockCount": 1,
        "byKind": {"country": 1},
    }
    unlock = session.get(UserUnlock, (user.uid, "country:US"))
    assert unlock is not None
    assert unlock.visit_count == 1
    assert [item["id"] for item in list_checkins(session, user.uid, 50)] == [first.id]

    delete_checkin(session, user, first.id)

    assert get_summary(session, user.uid) == {
        "checkinCount": 0,
        "uniqueUnlockCount": 0,
        "byKind": {},
    }
    assert session.get(UserUnlock, (user.uid, "country:US")) is None
    assert list_achievements(session, user.uid, 50) == []


def test_update_checkin_changes_date_note_and_unlock_bounds(session) -> None:
    user = CurrentUser(uid="user-1", email="user@example.com", display_name=None)
    first = create_checkin(
        session,
        user,
        CheckInCreate(entity_id="country:US", visited_at=datetime(2024, 1, 1), note="Old note"),
    )
    create_checkin(
        session,
        user,
        CheckInCreate(entity_id="country:US", visited_at=datetime(2025, 1, 1), note="Other visit"),
    )

    updated = update_checkin(
        session,
        user,
        first.id,
        CheckInUpdate(visited_at=datetime(2026, 2, 3), note="Updated note"),
    )

    assert updated.note == "Updated note"
    assert updated.visited_at == datetime(2026, 2, 3)
    unlock = session.get(UserUnlock, (user.uid, "country:US"))
    assert unlock is not None
    assert unlock.first_checked_in_at == datetime(2025, 1, 1)
    assert unlock.last_checked_in_at == datetime(2026, 2, 3)
