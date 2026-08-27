from __future__ import annotations

from functools import lru_cache
from typing import Optional

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str = "sqlite:///../data/achievement_tracker.db"
    # Cloud Run uses these fields to build a Cloud SQL Unix socket URL. The
    # existing database_url remains available for local SQLite and Proxy use.
    db_user: Optional[str] = None
    db_password: Optional[str] = None
    db_name: Optional[str] = None
    instance_unix_socket: Optional[str] = None
    boundary_data_dir: str = "../data/raw/boundaries"
    boundary_base_url: Optional[str] = None
    firebase_project_id: Optional[str] = None
    firebase_credentials_path: Optional[str] = None
    web_origin: str = "http://localhost:5173"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()
