from __future__ import annotations

from typing import Any

from sqlalchemy import case, desc, func, select
from sqlalchemy.orm import Session

from app.db import CatalogEntity, User, UserCheckin


def _display_name(value: str | None) -> str:
    """Return a useful, non-sensitive public label for a leaderboard row."""
    name = (value or "").strip()
    return name or "Explorer"


def _serialize_entry(row: Any, current_user_id: str) -> dict[str, Any]:
    return {
        "rank": int(row["rank"]),
        "displayName": _display_name(row["display_name"]),
        "countryCount": int(row["country_count"]),
        "admin1Count": int(row["admin1_count"]),
        "isCurrentUser": row["user_id"] == current_user_id,
    }


def get_geography_ranking(
    db: Session,
    current_user_id: str,
    limit: int = 50,
) -> dict[str, Any]:
    """Build the first geography leaderboard from active cloud check-ins.

    The leaderboard intentionally has no separate table yet. A user's score is
    derived from unique active check-ins:

    * primary metric: distinct first-level administrative regions;
    * secondary metric: distinct countries represented by those regions or by
      an explicit country check-in;
    * ties share the same rank; no arbitrary third metric is used.

    Country check-ins are retained for users whose country has no required
    admin1 selection. For admin1 check-ins, the parent country is counted.
    """
    country_key = case(
        (CatalogEntity.kind == "country", CatalogEntity.id),
        (CatalogEntity.kind == "admin1", CatalogEntity.parent_id),
        else_=None,
    )
    admin1_key = case(
        (CatalogEntity.kind == "admin1", CatalogEntity.id),
        else_=None,
    )

    counts = (
        select(
            UserCheckin.user_id.label("user_id"),
            func.count(func.distinct(country_key)).label("country_count"),
            func.count(func.distinct(admin1_key)).label("admin1_count"),
        )
        .join(CatalogEntity, CatalogEntity.id == UserCheckin.entity_id)
        .where(
            UserCheckin.deleted_at.is_(None),
            CatalogEntity.active.is_(True),
            CatalogEntity.kind.in_(("country", "admin1")),
        )
        .group_by(UserCheckin.user_id)
        .subquery("geography_counts")
    )

    ranked = (
        select(
            counts.c.user_id,
            counts.c.country_count,
            counts.c.admin1_count,
            func.rank()
            .over(
                order_by=(
                    desc(counts.c.admin1_count),
                    desc(counts.c.country_count),
                )
            )
            .label("rank"),
        )
        .subquery("geography_ranked")
    )

    columns = (
        ranked.c.user_id,
        ranked.c.rank,
        ranked.c.country_count,
        ranked.c.admin1_count,
        User.display_name,
    )
    rows = db.execute(
        select(*columns)
        .join(User, User.firebase_uid == ranked.c.user_id)
        .order_by(
            ranked.c.rank.asc(),
            ranked.c.user_id.asc(),
        )
        .limit(limit)
    ).mappings().all()

    current_row = db.execute(
        select(*columns)
        .join(User, User.firebase_uid == ranked.c.user_id)
        .where(ranked.c.user_id == current_user_id)
    ).mappings().first()

    return {
        "category": "geography",
        "primaryMetric": "uniqueAdmin1",
        "secondaryMetric": "uniqueCountries",
        "entries": [_serialize_entry(row, current_user_id) for row in rows],
        "me": (
            _serialize_entry(current_row, current_user_id)
            if current_row is not None
            else None
        ),
    }
