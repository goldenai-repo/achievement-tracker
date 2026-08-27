import json
from pathlib import Path
from typing import Annotated, Optional

from fastapi import Depends, FastAPI, HTTPException, Query, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, RedirectResponse
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from app.auth import CurrentUser, get_current_user
from app.boundaries import geojson_bounds
from app.checkins import (
    CheckInCreate,
    CheckInUpdate,
    create_checkin,
    delete_checkin,
    get_summary,
    list_achievements,
    list_checkins,
    serialize_checkin,
    update_checkin,
)
from app.config import get_settings
from app.db import CatalogEntity, get_db, init_db, upsert_user

settings = get_settings()
app = FastAPI(title="Achievement Tracker API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[settings.web_origin],
    allow_credentials=True,
    allow_methods=["DELETE", "GET", "PATCH", "POST", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
)


@app.on_event("startup")
def startup() -> None:
    init_db()


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/v1/boundaries/{asset_name}", name="get_boundary")
def get_boundary(asset_name: str) -> FileResponse:
    """Serve public, non-user-specific GeoJSON boundary assets.

    MapLibre's remote GeoJSON source cannot attach the app's bearer token, so
    boundary files are deliberately separated from authenticated catalog and
    check-in data. The asset directory is configured outside the repository in
    production and should contain only reviewed boundary files.
    """
    if Path(asset_name).name != asset_name or not asset_name.endswith(".geojson"):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Boundary not found")
    path = Path(settings.boundary_data_dir) / asset_name
    if not path.is_file():
        if settings.boundary_base_url:
            return RedirectResponse(
                url=f"{settings.boundary_base_url.rstrip('/')}/{asset_name}",
                status_code=status.HTTP_307_TEMPORARY_REDIRECT,
            )
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Boundary not found")
    return FileResponse(path, media_type="application/geo+json")


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
    request: Request,
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
            "latitude": entity.latitude,
            "longitude": entity.longitude,
            "boundaryGeoJsonUrl": entity.metadata_json.get("boundary_geojson_url")
            or _local_boundary_url(request, entity),
            "bounds": _local_boundary_bounds(entity),
        }
        for entity in entities
    ]


def _local_boundary_url(request: Request, entity: CatalogEntity) -> Optional[str]:
    asset_name = _boundary_asset_name(entity)
    if asset_name is None:
        return None

    if settings.boundary_base_url:
        return f"{settings.boundary_base_url.rstrip('/')}/{asset_name}"

    if not (Path(settings.boundary_data_dir) / asset_name).is_file():
        return None
    return str(request.url_for("get_boundary", asset_name=asset_name))


def _boundary_asset_name(entity: CatalogEntity) -> Optional[str]:
    if entity.kind == "country":
        return f"{entity.code.lower()}-admin1.geojson"
    if entity.kind == "admin1" and entity.parent_id:
        country_code = entity.parent_id.removeprefix("country:")
        if country_code:
            return f"{country_code.lower()}-admin1.geojson"
    return None


def _local_boundary_bounds(entity: CatalogEntity) -> Optional[dict[str, float]]:
    # Cloud deployments persist bounds in catalog metadata so the API does
    # not need the local boundary directory. Keep the local GeoJSON fallback
    # for development and for older catalog rows.
    persisted_bounds = (entity.metadata_json or {}).get("bounds")
    if isinstance(persisted_bounds, dict):
        try:
            return {
                key: float(persisted_bounds[key])
                for key in ("north", "south", "east", "west")
            }
        except (KeyError, TypeError, ValueError):
            pass

    asset_name = _boundary_asset_name(entity)
    if asset_name is None:
        return None
    path = Path(settings.boundary_data_dir) / asset_name
    if not path.is_file():
        return None
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
        return geojson_bounds(
            document,
            catalog_id=entity.id if entity.kind == "admin1" else None,
        )
    except (OSError, ValueError, TypeError):
        return None


@app.get("/v1/checkins")
def get_checkins(
    current_user: Annotated[CurrentUser, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
    limit: int = Query(default=50, ge=1, le=100),
) -> list[dict]:
    return list_checkins(db, current_user.uid, limit)


@app.get("/v1/achievements")
def get_achievements(
    current_user: Annotated[CurrentUser, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
    limit: int = Query(default=50, ge=1, le=100),
) -> list[dict]:
    return list_achievements(db, current_user.uid, limit)


@app.post("/v1/checkins", status_code=status.HTTP_201_CREATED)
def post_checkin(
    payload: CheckInCreate,
    current_user: Annotated[CurrentUser, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> dict:
    checkin = create_checkin(db, current_user, payload)
    entity = db.get(CatalogEntity, checkin.entity_id)
    return serialize_checkin(checkin, entity)


@app.delete("/v1/checkins/{checkin_id}", status_code=status.HTTP_204_NO_CONTENT)
def remove_checkin(
    checkin_id: str,
    current_user: Annotated[CurrentUser, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> None:
    delete_checkin(db, current_user, checkin_id)


@app.patch("/v1/checkins/{checkin_id}")
def patch_checkin(
    checkin_id: str,
    payload: CheckInUpdate,
    current_user: Annotated[CurrentUser, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> dict:
    checkin = update_checkin(db, current_user, checkin_id, payload)
    entity = db.get(CatalogEntity, checkin.entity_id)
    return serialize_checkin(checkin, entity)


@app.get("/v1/summary")
def get_user_summary(
    current_user: Annotated[CurrentUser, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> dict:
    return get_summary(db, current_user.uid)
