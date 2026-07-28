# Achievement Tracker — Web/Python MVP Plan

This is the active implementation plan. The older Flutter plan in `docs/IMPLEMENTATION_PLAN.md` is retained for history only and must not be used for current work.

## Target architecture

```text
Browser (TypeScript/JavaScript + Vite)
        │ Firebase Auth ID token
        ▼
Python FastAPI on Cloud Run
        │ SQLAlchemy / Alembic
        ▼
PostgreSQL locally → Cloud SQL in production
```

Firebase Authentication owns registration, login, password reset, and provider integrations. The browser obtains an ID token and sends it as `Authorization: Bearer <token>`. FastAPI verifies the token with the Firebase Admin Python SDK and uses the verified Firebase UID as the user identity. The browser never connects directly to PostgreSQL.

## Repository layout

```text
web/                         Vite web client
  src/auth/                  Firebase web auth
  src/api/                   typed API client
  src/features/checkins/     list and create flows
backend/                     FastAPI service
  app/api/                   routes and auth dependencies
  app/models/                SQLAlchemy models
  app/services/              domain operations
  app/db/                    session and migrations
  tests/                     API tests
data/seed/                   versioned catalog seed files
infra/                       Docker and deployment configuration
docs/                        design, ADRs, and API contract
```

## Database design

### Canonical catalog

`catalog_entities` stores things that can be unlocked. It is independent of users.

| Column | Purpose |
|---|---|
| `id` | Stable ID such as `country:US` or `state:US-CA` |
| `kind` | `country`, `state`, later `attraction`, `heritage`, `species` |
| `code` | Source/normative code, e.g. ISO alpha-2 or USPS/FIPS |
| `name` | Default display name |
| `names` | Localized names as JSONB or a separate names table |
| `parent_id` | Parent entity, e.g. US for California |
| `source` / `source_id` | Provenance and source record ID |
| `source_version` | Import version/date |
| `active` | Whether users can currently select it |
| `metadata` | Extensible source-specific fields |

Constraints:

- `id` is immutable.
- `(kind, code)` is unique.
- `(source, source_id)` is unique when a source ID exists.
- A display name is never used as a primary key.

### User activity

`users` maps a Firebase UID to an application profile.

`user_checkins` stores every claimed visit:

```text
id, user_id, entity_id, visited_at,
note, latitude, longitude,
created_at, updated_at, deleted_at
```

`user_unlocks` is a derived unique projection:

```text
user_id, entity_id, first_checked_in_at,
last_checked_in_at, visit_count, updated_at
```

Use a unique constraint on `(user_id, entity_id)` in `user_unlocks`. A new check-in updates this row in the same transaction. This supports both a timeline of visits and a deduplicated count of achievements.

### Future achievement rules

Do not encode complex rules into `user_unlocks`. Later add:

```text
achievement_definitions
user_achievement_progress
```

Examples include “visit 5 states”, “see 3 biomes”, or “visit a UNESCO site in 3 countries”. This keeps geography records reusable by multiple achievement definitions.

## Catalog data sources

For the first week, import small, reviewed seed files into PostgreSQL:

1. Countries: ISO 3166-1 alpha-2 codes. Keep the normalized seed in the repo with source and version metadata.
2. US states: US Census ANSI/FIPS and USPS codes. Decide explicitly whether the product includes DC and US territories.
3. Global first-level subdivisions: later import GeoNames `admin1CodesASCII.txt`, after reviewing attribution and license requirements.

The source file is not the user-facing database. The importer normalizes source rows into `catalog_entities`, reports duplicates and missing parents, and records an import version.

For future dimensions:

- Heritage: UNESCO World Heritage data.
- Biodiversity: GBIF species data.
- Attractions: an explicitly licensed OpenStreetMap/Wikidata-derived dataset.

AI should assist with extraction and entity matching, but it should not be the authority for canonical names, IDs, or unlock decisions.

## First-week execution

### Milestone 1 — API skeleton

- Create `backend/` with FastAPI, Pydantic, SQLAlchemy, Alembic, and pytest.
- Add `/healthz`.
- Add environment-based database configuration.
- Add Firebase Admin token verification dependency.
- Add `/v1/me` to provision/read the local user profile.

### Milestone 2 — Catalog and check-ins

- Add migrations for users, catalog entities, check-ins, and unlocks.
- Add country and US-state seed files.
- Add `GET /v1/catalog?kind=country|state`.
- Add `GET /v1/checkins`.
- Add `POST /v1/checkins` with transactionally updated unlock projection.
- Add `DELETE /v1/checkins/{id}` with a documented recalculation policy.
- Add `GET /v1/summary`.

### Milestone 3 — Web client

- Create Vite TypeScript app.
- Add Firebase web configuration through environment variables.
- Add register, login, logout, and auth state handling.
- Attach Firebase ID token to API requests.
- Add catalog selector, create check-in form, list, and summary cards.
- Add loading/error/empty states.

### Milestone 4 — E2E and sync

- Run backend against a local PostgreSQL container.
- Test Firebase-authenticated API access with a test project/emulator strategy.
- Verify user isolation.
- Add `updated_since` or cursor-based sync endpoint.
- Validate the same account in two browser windows.

## Later milestones

- Week 2: map view, location capture, country/state boundary display.
- Week 3: heritage, species, and attraction catalog import pipelines.
- Week 4: AI note extraction and user-confirmed entity matching.
- Week 5: sharing, profiles, ranking, privacy, abuse prevention.
- Week 6: Kotlin Android client against the stable API; then iOS/Kotlin Multiplatform evaluation.
- Week 7+: Cloud Run, Cloud SQL, Cloud Storage, CI/CD, monitoring, and mobile beta releases.

## Definition of done for the web demo

- A user can register/login through Firebase Auth.
- FastAPI rejects missing or invalid Firebase tokens.
- A signed-in user can create and list country/state check-ins.
- A second user cannot see the first user's records.
- Repeated visits preserve history but do not inflate the unique unlock count.
- Catalog rows include source and version metadata.
- The app runs locally with documented commands and no committed secrets.
