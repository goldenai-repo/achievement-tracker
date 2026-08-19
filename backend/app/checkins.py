from __future__ import annotations

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from fastapi import HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.auth import CurrentUser
from app.db import CatalogEntity, UserCheckin, UserUnlock, upsert_user


class CheckInCreate(BaseModel):
    entity_id: str = Field(min_length=1, max_length=160)
    visited_at: datetime
    note: Optional[str] = Field(default=None, max_length=2000)
    latitude: Optional[float] = Field(default=None, ge=-90, le=90)
    longitude: Optional[float] = Field(default=None, ge=-180, le=180)


class CheckInUpdate(BaseModel):
    visited_at: datetime
    note: Optional[str] = Field(default=None, max_length=2000)


def _utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def create_checkin(db: Session, current_user: CurrentUser, payload: CheckInCreate) -> UserCheckin:
    entity = db.get(CatalogEntity, payload.entity_id)
    if entity is None or not entity.active:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Catalog entity not found",
        )

    # Countries with first-level administrative data must be checked in at
    # the admin-1 level. A country marker can still summarize visited regions
    # and open the form with the country preselected.
    if entity.kind == "country":
        has_admin1 = db.scalar(
            select(func.count())
            .select_from(CatalogEntity)
            .where(
                CatalogEntity.kind == "admin1",
                CatalogEntity.parent_id == entity.id,
                CatalogEntity.active.is_(True),
            )
        )
        if has_admin1:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="Select a first-level administrative region for this country",
            )

    now = datetime.now(timezone.utc)
    visited_at = _utc(payload.visited_at)
    upsert_user(
        db,
        firebase_uid=current_user.uid,
        email=current_user.email,
        display_name=current_user.display_name,
    )
    checkin = UserCheckin(
        id=uuid4().hex,
        user_id=current_user.uid,
        entity_id=payload.entity_id,
        visited_at=visited_at,
        note=payload.note,
        latitude=payload.latitude,
        longitude=payload.longitude,
        created_at=now,
        updated_at=now,
    )
    unlock = db.get(UserUnlock, (current_user.uid, payload.entity_id))
    if unlock is None:
        unlock = UserUnlock(
            user_id=current_user.uid,
            entity_id=payload.entity_id,
            first_checked_in_at=visited_at,
            last_checked_in_at=visited_at,
            visit_count=1,
            updated_at=now,
        )
        db.add(unlock)
    else:
        first_checked_in_at = _utc(unlock.first_checked_in_at)
        last_checked_in_at = _utc(unlock.last_checked_in_at)
        unlock.first_checked_in_at = min(first_checked_in_at, visited_at)
        unlock.last_checked_in_at = max(last_checked_in_at, visited_at)
        unlock.visit_count += 1
        unlock.updated_at = now
    db.add(checkin)
    db.commit()
    db.refresh(checkin)
    return checkin


def delete_checkin(db: Session, current_user: CurrentUser, checkin_id: str) -> None:
    """Soft-delete one owned visit and keep its place unlock aggregate accurate."""
    checkin = db.scalar(
        select(UserCheckin).where(
            UserCheckin.id == checkin_id,
            UserCheckin.user_id == current_user.uid,
            UserCheckin.deleted_at.is_(None),
        )
    )
    if checkin is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Check-in not found")

    now = datetime.now(timezone.utc)
    checkin.deleted_at = now
    checkin.updated_at = now

    unlock = db.get(UserUnlock, (current_user.uid, checkin.entity_id))
    remaining = list(
        db.scalars(
            select(UserCheckin).where(
                UserCheckin.user_id == current_user.uid,
                UserCheckin.entity_id == checkin.entity_id,
                UserCheckin.deleted_at.is_(None),
                UserCheckin.id != checkin.id,
            )
        )
    )
    if unlock is not None:
        if not remaining:
            db.delete(unlock)
        else:
            unlock.visit_count = len(remaining)
            unlock.first_checked_in_at = min(_utc(item.visited_at) for item in remaining)
            unlock.last_checked_in_at = max(_utc(item.visited_at) for item in remaining)
            unlock.updated_at = now

    db.commit()


def update_checkin(
    db: Session,
    current_user: CurrentUser,
    checkin_id: str,
    payload: CheckInUpdate,
) -> UserCheckin:
    checkin = db.scalar(
        select(UserCheckin).where(
            UserCheckin.id == checkin_id,
            UserCheckin.user_id == current_user.uid,
            UserCheckin.deleted_at.is_(None),
        )
    )
    if checkin is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Check-in not found")

    now = datetime.now(timezone.utc)
    checkin.visited_at = _utc(payload.visited_at)
    checkin.note = payload.note
    checkin.updated_at = now

    # Date edits can change the first/last visit shown for the place, so keep
    # the unlock aggregate consistent with the edited check-in.
    unlock = db.get(UserUnlock, (current_user.uid, checkin.entity_id))
    if unlock is not None:
        visits = list(
            db.scalars(
                select(UserCheckin).where(
                    UserCheckin.user_id == current_user.uid,
                    UserCheckin.entity_id == checkin.entity_id,
                    UserCheckin.deleted_at.is_(None),
                )
            )
        )
        unlock.visit_count = len(visits)
        unlock.first_checked_in_at = min(_utc(item.visited_at) for item in visits)
        unlock.last_checked_in_at = max(_utc(item.visited_at) for item in visits)
        unlock.updated_at = now

    db.commit()
    db.refresh(checkin)
    return checkin


def serialize_checkin(checkin: UserCheckin, entity: Optional[CatalogEntity]) -> dict:
    # SQLite can return timezone-aware columns as naive datetimes. Normalize
    # every API timestamp to an explicit UTC ISO-8601 value for mobile clients.
    visited_at = _utc(checkin.visited_at).isoformat().replace("+00:00", "Z")
    created_at = _utc(checkin.created_at).isoformat().replace("+00:00", "Z")
    return {
        "id": checkin.id,
        "entityId": checkin.entity_id,
        "entity": (
            {
                "id": entity.id,
                "kind": entity.kind,
                "code": entity.code,
                "name": entity.name,
                "parentId": entity.parent_id,
                "latitude": entity.latitude,
                "longitude": entity.longitude,
            }
            if entity
            else None
        ),
        "visitedAt": visited_at,
        "note": checkin.note,
        "latitude": checkin.latitude,
        "longitude": checkin.longitude,
        "createdAt": created_at,
    }


def list_checkins(db: Session, user_id: str, limit: int) -> list[dict]:
    checkins = list(
        db.scalars(
            select(UserCheckin)
            .where(UserCheckin.user_id == user_id, UserCheckin.deleted_at.is_(None))
            .order_by(UserCheckin.visited_at.desc(), UserCheckin.created_at.desc())
            .limit(limit)
        )
    )
    entity_ids = {checkin.entity_id for checkin in checkins}
    entities = {
        entity.id: entity
        for entity in db.scalars(select(CatalogEntity).where(CatalogEntity.id.in_(entity_ids)))
    }
    return [serialize_checkin(checkin, entities.get(checkin.entity_id)) for checkin in checkins]


def serialize_achievement(
    entity: CatalogEntity,
    unlock: UserUnlock,
    checkins: list[UserCheckin],
) -> dict:
    """Serialize one place with its repeated visit events grouped underneath."""
    return {
        "entityId": entity.id,
        "entity": {
            "id": entity.id,
            "kind": entity.kind,
            "code": entity.code,
            "name": entity.name,
            "parentId": entity.parent_id,
            "latitude": entity.latitude,
            "longitude": entity.longitude,
        },
        "visitCount": unlock.visit_count,
        "firstVisitedAt": _utc(unlock.first_checked_in_at).isoformat().replace("+00:00", "Z"),
        "lastVisitedAt": _utc(unlock.last_checked_in_at).isoformat().replace("+00:00", "Z"),
        "checkins": [serialize_checkin(checkin, entity) for checkin in checkins],
    }


def list_achievements(db: Session, user_id: str, limit: int) -> list[dict]:
    """Return user places grouped by entity, with individual visits nested."""
    unlocks = list(
        db.scalars(
            select(UserUnlock)
            .where(UserUnlock.user_id == user_id)
            .order_by(UserUnlock.last_checked_in_at.desc())
            .limit(limit)
        )
    )
    if not unlocks:
        return []

    entity_ids = [unlock.entity_id for unlock in unlocks]
    entities = {
        entity.id: entity
        for entity in db.scalars(select(CatalogEntity).where(CatalogEntity.id.in_(entity_ids)))
    }
    checkins = list(
        db.scalars(
            select(UserCheckin)
            .where(
                UserCheckin.user_id == user_id,
                UserCheckin.deleted_at.is_(None),
                UserCheckin.entity_id.in_(entity_ids),
            )
            .order_by(UserCheckin.visited_at.desc(), UserCheckin.created_at.desc())
        )
    )
    by_entity: dict[str, list[UserCheckin]] = {}
    for checkin in checkins:
        by_entity.setdefault(checkin.entity_id, []).append(checkin)

    return [
        serialize_achievement(entity, unlock, by_entity.get(unlock.entity_id, []))
        for unlock in unlocks
        if (entity := entities.get(unlock.entity_id)) is not None
    ]


def get_summary(db: Session, user_id: str) -> dict:
    checkin_count = db.scalar(
        select(func.count(UserCheckin.id)).where(
            UserCheckin.user_id == user_id,
            UserCheckin.deleted_at.is_(None),
        )
    )
    unique_count = db.scalar(
        select(func.count()).select_from(UserUnlock).where(UserUnlock.user_id == user_id)
    )
    by_kind = db.execute(
        select(CatalogEntity.kind, func.count(UserUnlock.entity_id))
        .join(
            UserUnlock,
            (UserUnlock.entity_id == CatalogEntity.id) & (UserUnlock.user_id == user_id),
        )
        .group_by(CatalogEntity.kind)
    )
    return {
        "checkinCount": checkin_count or 0,
        "uniqueUnlockCount": unique_count or 0,
        "byKind": {kind: count for kind, count in by_kind},
    }
