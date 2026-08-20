from datetime import datetime, timezone

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.auth import CurrentUser
from app.checkins import CheckInCreate, create_checkin, get_summary, list_checkins
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
