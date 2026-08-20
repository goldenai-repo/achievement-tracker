from typing import Annotated, Optional

from fastapi import Depends, FastAPI, HTTPException, Query, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from app.auth import CurrentUser, get_current_user
from app.checkins import (
    CheckInCreate,
    create_checkin,
    get_summary,
    list_checkins,
    serialize_checkin,
)
from app.config import get_settings
from app.db import CatalogEntity, get_db, init_db, upsert_user

settings = get_settings()
app = FastAPI(title="Achievement Tracker API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[settings.web_origin],
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
)


@app.on_event("startup")
def startup() -> None:
    init_db()


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/v1/me")
def get_me(
    current_user: Annotated[CurrentUser, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> dict:
    user = upsert_user(
        db,
        firebase_uid=current_user.uid,
        email=current_user.email,
        display_name=current_user.display_name,
    )
    return {
        "uid": user.firebase_uid,
        "email": user.email,
        "displayName": user.display_name,
        "createdAt": user.created_at.isoformat(),
        "updatedAt": user.updated_at.isoformat(),
    }


@app.get("/v1/catalog")
def get_catalog(
    _: Annotated[CurrentUser, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
    kind: str = Query(default="country"),
    q: Optional[str] = Query(default=None, max_length=100),
    parent_id: Optional[str] = Query(default=None, max_length=160),
    limit: int = Query(default=25, ge=1, le=500),
) -> list[dict]:
    if kind not in {"country", "admin1"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="kind must be country or admin1",
        )
    query = select(CatalogEntity).where(
        CatalogEntity.kind == kind,
        CatalogEntity.active.is_(True),
    )
    if parent_id:
        query = query.where(CatalogEntity.parent_id == parent_id)
    if q and q.strip():
        term = f"%{q.strip().lower()}%"
        query = query.where(
            or_(
                func.lower(CatalogEntity.name).like(term),
                func.lower(CatalogEntity.name_ascii).like(term),
                func.lower(CatalogEntity.code).like(term),
            )
        )
    entities = db.scalars(query.order_by(CatalogEntity.name).limit(limit))
    return [
        {
            "id": entity.id,
            "kind": entity.kind,
            "code": entity.code,
            "name": entity.name,
            "nameAscii": entity.name_ascii,
            "parentId": entity.parent_id,
        }
        for entity in entities
    ]


@app.get("/v1/checkins")
def get_checkins(
    current_user: Annotated[CurrentUser, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
    limit: int = Query(default=50, ge=1, le=100),
) -> list[dict]:
    return list_checkins(db, current_user.uid, limit)


@app.post("/v1/checkins", status_code=status.HTTP_201_CREATED)
def post_checkin(
    payload: CheckInCreate,
    current_user: Annotated[CurrentUser, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> dict:
    checkin = create_checkin(db, current_user, payload)
    entity = db.get(CatalogEntity, checkin.entity_id)
    return serialize_checkin(checkin, entity)


@app.get("/v1/summary")
def get_user_summary(
    current_user: Annotated[CurrentUser, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> dict:
    return get_summary(db, current_user.uid)
