from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Optional

import pytest
from fastapi import HTTPException

import app.auth as auth_module
from app.auth import (
    CurrentUser,
    delete_firebase_user,
    parse_bearer_token,
    require_recent_authentication,
)


def test_parse_bearer_token_accepts_case_insensitive_scheme() -> None:
    assert parse_bearer_token("bearer abc123") == "abc123"


@pytest.mark.parametrize("header", [None, "", "Basic abc", "Bearer"])
def test_parse_bearer_token_rejects_invalid_headers(header: Optional[str]) -> None:
    with pytest.raises(Exception):
        parse_bearer_token(header)


def test_account_deletion_requires_recent_authentication() -> None:
    recent_user = CurrentUser(
        uid="user-1",
        email="user@example.com",
        display_name=None,
        auth_time=datetime.now(timezone.utc) - timedelta(minutes=4),
    )
    require_recent_authentication(recent_user)

    stale_user = CurrentUser(
        uid="user-1",
        email="user@example.com",
        display_name=None,
        auth_time=datetime.now(timezone.utc) - timedelta(minutes=6),
    )
    with pytest.raises(HTTPException) as error:
        require_recent_authentication(stale_user)
    assert error.value.status_code == 401


def test_account_deletion_returns_502_and_logs_unexpected_firebase_errors(
    monkeypatch, caplog
) -> None:
    class UserNotFoundError(Exception):
        pass

    class FakeFirebaseAuth:
        @staticmethod
        def delete_user(_: str) -> None:
            raise RuntimeError("upstream unavailable")

    FakeFirebaseAuth.UserNotFoundError = UserNotFoundError

    monkeypatch.setattr(auth_module, "firebase_auth", FakeFirebaseAuth)

    with pytest.raises(HTTPException) as error:
        delete_firebase_user("user-1")

    assert error.value.status_code == 502
    assert error.value.detail == "Could not delete the Firebase account"
    assert "Firebase account deletion failed for uid=user-1" in caplog.text


def test_account_deletion_is_idempotent_when_firebase_user_is_missing(monkeypatch) -> None:
    class UserNotFoundError(Exception):
        pass

    class FakeFirebaseAuth:
        @staticmethod
        def delete_user(_: str) -> None:
            raise UserNotFoundError()

    FakeFirebaseAuth.UserNotFoundError = UserNotFoundError

    monkeypatch.setattr(auth_module, "firebase_auth", FakeFirebaseAuth)

    delete_firebase_user("already-deleted")
