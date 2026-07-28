from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from fastapi import Header, HTTPException, status

from app.config import get_settings

try:
    import firebase_admin
    from firebase_admin import auth as firebase_auth
    from firebase_admin import credentials
except ImportError:  # pragma: no cover - dependencies are installed in the API environment.
    firebase_admin = None
    firebase_auth = None
    credentials = None


@dataclass(frozen=True)
class CurrentUser:
    uid: str
    email: Optional[str]
    display_name: Optional[str]


def parse_bearer_token(authorization: Optional[str]) -> str:
    if not authorization:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing Authorization header",
            headers={"WWW-Authenticate": "Bearer"},
        )

    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token.strip():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authorization header must use Bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return token.strip()


def _verify_with_firebase(token: str) -> dict:
    if firebase_admin is None or firebase_auth is None:
        raise HTTPException(status_code=503, detail="Firebase Admin SDK is not installed")

    settings = get_settings()
    try:
        firebase_admin.get_app()
    except ValueError:
        options = (
            {"projectId": settings.firebase_project_id}
            if settings.firebase_project_id
            else None
        )
        try:
            credential = (
                credentials.Certificate(settings.firebase_credentials_path)
                if settings.firebase_credentials_path and credentials
                else None
            )
            firebase_admin.initialize_app(credential=credential, options=options)
        except Exception as exc:  # pragma: no cover - depends on local Google credentials.
            raise HTTPException(status_code=503, detail="Firebase Admin is not configured") from exc

    try:
        decoded = firebase_auth.verify_id_token(token)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired Firebase ID token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc

    if settings.firebase_project_id and decoded.get("aud") != settings.firebase_project_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token audience",
        )
    return decoded


def get_current_user(authorization: Optional[str] = Header(default=None)) -> CurrentUser:
    decoded = _verify_with_firebase(parse_bearer_token(authorization))
    uid = decoded.get("uid")
    if not uid:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token has no user ID")
    return CurrentUser(
        uid=uid,
        email=decoded.get("email"),
        display_name=decoded.get("name") or decoded.get("display_name"),
    )
