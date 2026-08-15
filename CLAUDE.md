# Achievement Tracker — Claude Code Instructions

## Project Overview

JavaScript/TypeScript web client and Python/FastAPI backend for a cross-platform achievement tracker. Firebase Authentication supplies identity; PostgreSQL is the business database. Kotlin Android/iOS clients are planned after the web/API MVP.

## Tech Stack

- **Frontend:** TypeScript/JavaScript, Vite, Firebase Web SDK
- **Backend:** Python, FastAPI, Pydantic, SQLAlchemy, Alembic
- **Identity:** Firebase Auth and Firebase Admin Python SDK
- **Database:** PostgreSQL locally and Cloud SQL in production
- **Cloud:** Google Cloud Platform, Firebase
- **CI/CD:** GitHub Actions
- **Languages:** TypeScript/JavaScript (web), Python (API), Kotlin later (mobile)

## Repository Layout

```
web/          Vite web client
backend/      FastAPI API and tests
data/seed/    Versioned catalog seed files
infra/        Local Docker and cloud deployment configuration
docs/         Architecture docs and implementation plan
.github/      GitHub Actions workflows
```

## Development Commands

```bash
# Web
npm install
npm run dev
npm run test

# Python
uv sync
uv run uvicorn app.main:app --reload
uv run pytest
uv run ruff check .

# Firebase
firebase login               # authenticate Firebase CLI when needed

# Google Cloud
gcloud auth login
gcloud config set project <PROJECT_ID>
gcloud run deploy            # deploy FastAPI service
```

## Code Conventions

- Feature-first structure under `web/src/` and layered structure under `backend/app/`
- All business writes go through FastAPI services/repositories; the browser never accesses PostgreSQL
- Firebase UID is the external user identifier in every user-owned API query
- Timestamps: always store as UTC, display in local time
- Error handling: use sealed `Result<T>` types rather than throwing exceptions from repositories

## PostgreSQL Data Model

```
catalog_entities(id, kind, code, name, parent_id, source, source_id, source_version)
users(firebase_uid, email, display_name, created_at, updated_at)
user_checkins(id, user_id, entity_id, visited_at, note, latitude, longitude, created_at, updated_at)
user_unlocks(user_id, entity_id, first_checked_in_at, last_checked_in_at, visit_count, updated_at)
```

## Achievement Type Registry

| type key | description |
|---|---|
| `geography.country` | Country visited |
| `geography.state` | State or province visited |
| `geography.city` | City visited |
| `wildlife.animal` | Animal species observed |
| `wildlife.plant` | Wild plant species observed |
| `culture.museum` | Museum or gallery visited |
| `entertainment.movie` | Movie watched |
| `culinary.michelin` | Michelin-starred restaurant visited |
| `heritage.unesco` | UNESCO World Heritage Site visited |

## Security Rules Principles

- FastAPI verifies Firebase ID tokens before every user-owned operation
- User queries are always scoped by the verified Firebase UID
- Catalog writes are restricted to the import/admin job
- Validate entity IDs, timestamps, coordinates, and note lengths at the API boundary

## Testing Strategy

- Unit tests for API services and domain logic
- API integration tests use a disposable PostgreSQL database/container
- Browser E2E tests cover registration, login, create check-in, list, and user isolation

## Environment Variables / Secrets

Never commit Firebase service-account JSON, private keys, or any `.env` file. Use Firebase web config environment variables and CI/Cloud Run secrets for deployment.

## PR Workflow

- Branch naming: `feat/`, `fix/`, `chore/`, `docs/`
- All PRs require passing CI (lint + tests)
- Squash merge to main
