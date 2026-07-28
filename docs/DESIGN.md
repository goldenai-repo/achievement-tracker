# Achievement Tracker — MVP Design

## 1. Goal

The first MVP validates the core loop:

1. A user registers and signs in.
2. The user selects a canonical country or US state.
3. The user records a check-in.
4. The check-in appears in a personal list and updates unique achievement counts.
5. The same account sees the record after refresh and from another client.

The first client is a JavaScript/TypeScript web application. Android/iOS clients will be Kotlin-based later. The web demo is intentionally independent of the mobile client so the team can validate the product and API without installing Flutter or committing to a cross-platform UI framework.

## 2. Scope and non-goals

### In scope

- Email/password registration, login, logout, and protected routes.
- Country and US-state catalog entries.
- Manual check-in creation with date, optional note, and optional coordinates.
- Personal check-in list and unique unlocked counts.
- FastAPI authorization and local PostgreSQL-based API tests.
- Real-time updates for the same signed-in user.

### Out of scope for the first MVP

- AI extraction or autonomous data ingestion.
- Social sharing, ranking, friend graphs, and public profiles.
- Photos, push notifications, and advanced offline conflict resolution.
- Attractions, heritage, wildlife, and complex achievement rules.
- Production deployment and billing setup.

## 3. Technical decisions

- **Web client:** TypeScript/JavaScript with Vite. Keep the UI small and use the browser's `fetch` API for the Python backend.
- **Mobile clients:** Kotlin/Jetpack Compose for Android first; Kotlin Multiplatform can share domain and networking code with iOS later.
- **Identity:** Firebase Authentication with email/password in the web client. The Python backend verifies Firebase ID tokens using the Firebase Admin Python SDK.
- **API:** Python/FastAPI with Pydantic and SQLAlchemy.
- **Database:** PostgreSQL locally and Cloud SQL for production. PostgreSQL is the source of truth for catalog, check-ins, unlocks, and sync cursors.
- **Cloud:** Cloud Run for the API, Cloud SQL for relational data, Cloud Storage for media later. Firebase remains the identity provider, not the business database.

## 4. Domain model

The system separates canonical entities from user activity. A canonical entity is not duplicated when several users check in to it.

```text
catalog_entities/{entityId}
  kind: country | state
  code: ISO-like stable code, e.g. country:US or state:US-CA
  name: display name
  parentCode: nullable, e.g. country:US for state:US-CA
  source: iso-3166-1 | census-ansi | geonames
  sourceVersion: string
  createdAt: timestamp
  updatedAt: timestamp

users/{firebaseUid}
  displayName
  email
  createdAt
  updatedAt

user_checkins/{checkinId}
  userId: Firebase UID
  entityId: catalog entity ID
  dimension: geography
  visitedAt: timestamp
  note: nullable string
  latitude: nullable decimal
  longitude: nullable decimal
  createdAt: timestamp
  updatedAt: timestamp

user_unlocks/{userId}:{entityId}
  userId: Firebase UID
  entityId: catalog entity ID
  firstCheckedInAt: timestamp
  visitCount: integer
  updatedAt: timestamp
```

`user_checkins` preserve individual visits. `user_unlocks` represent unique achievements and are derived from check-ins. The API updates both in one PostgreSQL transaction. The database, rather than the browser, owns counts and uniqueness constraints.

## 5. Stable identifiers and deduplication

Display names are never used as identifiers. The MVP uses:

- `country:US`, `country:CN`
- `state:US-CA`, `state:US-NY`

Changing a display language must not create another achievement. A future import pipeline must preserve `source` and `sourceId` and require an explicit merge when two source records refer to the same entity.

## 6. Sync and conflict policy

- The first web demo uses normal API reads after writes.
- The API exposes an incremental sync cursor based on `updatedAt` and a monotonic change sequence.
- WebSocket or Server-Sent Events can be added after the basic sync contract is stable.
- All timestamps are stored in UTC and displayed in local time.
- Writes go through FastAPI service/repository layers, never directly from the browser to PostgreSQL.
- The first MVP uses last-write-wins for edits, using `updatedAt`.
- Deletes are soft deletes if delete support is added before the MVP review; otherwise deletion is deferred.

## 7. Security

- Unauthenticated users cannot read or write user data.
- A user can only access `users/{theirUid}/...`.
- Catalog reads are available through the API; catalog writes are restricted to the import/admin job.
- Client-provided counts are never trusted for ranking or statistics.
- Firebase configuration files and secrets are not committed.

## 8. Data source strategy

The first seed data is versioned, reviewable JSON rather than AI-generated data:

- Countries: ISO 3166-1 alpha-2 codes, stored with a stable internal ID such as `country:US`.
- US states: ANSI/FIPS and USPS codes from the US Census reference list, with the product policy explicitly stating whether DC and territories are included.
- Global first-level subdivisions: add GeoNames `admin1CodesASCII` only after licensing, attribution, and normalization are reviewed.

Later catalog sources should be imported into a staging collection, validated, attributed, and then promoted to the production catalog. Planned sources are UNESCO for heritage, GBIF for biodiversity, and an explicitly licensed geographic/POI source for attractions.

## 9. First-week acceptance tests

- Register a new account and reach the home screen.
- Log in again and see the same account's records.
- Create a country and state check-in.
- See the two records in the list and counts of one country and one state.
- Create another check-in for the same entity and verify the unique count stays unchanged while visit count increases.
- Open two clients for the same account and observe the list update.
- Verify a second account cannot read the first account's check-ins through the API.
- Run the web client and FastAPI service locally with PostgreSQL or a documented SQLite test profile.

## 10. Delivery sequence

1. Commit this design and the ADRs.
2. Initialize the Vite web client and FastAPI service.
3. Add Firebase Auth in the browser and ID-token verification in Python.
4. Add PostgreSQL migrations, catalog seed data, API repository, and check-in list.
5. Add API and browser E2E coverage.
6. Add the Android Kotlin shell only after the web/API contract is usable.
7. Push each coherent milestone to `feat/mvp-auth-geography` and open a PR for review.
