from __future__ import annotations

from typing import Optional

import pytest

from app.auth import parse_bearer_token


def test_parse_bearer_token_accepts_case_insensitive_scheme() -> None:
    assert parse_bearer_token("bearer abc123") == "abc123"


@pytest.mark.parametrize("header", [None, "", "Basic abc", "Bearer"])
def test_parse_bearer_token_rejects_invalid_headers(header: Optional[str]) -> None:
    with pytest.raises(Exception):
        parse_bearer_token(header)
