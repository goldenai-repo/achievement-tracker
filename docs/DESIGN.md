# Achievement Tracker — MVP Design

## 1. Goal

The first MVP validates the core loop:

1. A user registers and signs in.
2. The user selects a canonical country or US state.
3. The user records a check-in.
4. The check-in appears in a personal list and updates unique achievement counts.
5. The same account sees the record after refresh and from another client.

The first client is Flutter Web. The same Flutter project must also build for Android and iOS. Android Studio/Kotlin exploration is kept separate from the production app so it does not create a second client architecture.

## 2. Scope and non-goals

### In scope

- Email/password registration, login, logout, and protected routes.
- Country and US-state catalog entries.
- Manual check-in creation with date, optional note, and optional coordinates.
- Personal check-in list and unique unlocked counts.
- Firestore security rules and local emulator-based tests.
- Real-time updates for the same signed-in user.

### Out of scope for the first MVP

- AI extraction or autonomous data ingestion.
- Social sharing, ranking, friend graphs, and public profiles.
- Photos, push notifications, and offline conflict resolution beyond Firestore's basic client queue.
- Attractions, heritage, wildlife, and complex achievement rules.
- Production deployment and billing setup.

## 3. Technical decisions

- **Client:** Flutter, feature-first Dart structure, Riverpod for state, go_router for routing.
- **Identity:** Firebase Authentication with email/password.
- **Database:** Cloud Firestore. Firebase is the initial Google Cloud backend.
- **Future API:** Python/FastAPI on Cloud Run for ingestion, normalization, and AI workflows. No custom API is needed for the first client path.
- **Native learning:** A separate Kotlin Hello World project may be created outside `app/`; production Android code remains the Flutter target.

## 4. Domain model

The system separates canonical entities from user activity. A canonical entity is not duplicated when several users check in to it.

```text
catalog/{entityId}
  kind: country | state
  code: ISO-like stable code, e.g. country:US or state:US-CA
  name: display name
  parentCode: nullable, e.g. country:US for state:US-CA
  source: seed:iso-3166-1 | seed:us-states
  sourceVersion: string
  createdAt: timestamp
  updatedAt: timestamp

users/{uid}
  profile fields

users/{uid}/checkins/{checkinId}
  entityId: catalog entity ID
  dimension: geography
  visitedAt: timestamp
  note: nullable string
  location: nullable GeoPoint
  createdAt: timestamp
  updatedAt: timestamp

users/{uid}/unlocks/{entityId}
  entityId: catalog entity ID
  firstCheckedInAt: timestamp
  visitCount: integer
  updatedAt: timestamp
```

`checkins` preserve individual visits. `unlocks` represent unique achievements and are derived from check-ins. During the first implementation, unlock updates can happen transactionally in the repository; a trusted Cloud Function can replace this later.

## 5. Stable identifiers and deduplication

Display names are never used as identifiers. The MVP uses:

- `country:US`, `country:CN`
- `state:US-CA`, `state:US-NY`

Changing a display language must not create another achievement. A future import pipeline must preserve `source` and `sourceId` and require an explicit merge when two source records refer to the same entity.

## 6. Sync and conflict policy

- Firestore listeners provide live updates while the user is signed in.
- All timestamps are stored in UTC and displayed in local time.
- Writes go through repositories, never directly from widgets.
- The first MVP uses last-write-wins for edits, using `updatedAt`.
- Deletes are soft deletes if delete support is added before the MVP review; otherwise deletion is deferred.

## 7. Security

- Unauthenticated users cannot read or write user data.
- A user can only access `users/{theirUid}/...`.
- Catalog reads are public to authenticated clients and catalog writes are restricted to trusted tooling/admins.
- Client-provided counts are never trusted for ranking or statistics.
- Firebase configuration files and secrets are not committed.

## 8. Data source strategy

The first seed data is versioned, reviewable JSON rather than AI-generated data:

- Countries: ISO 3166-1 alpha-2 codes.
- US states: a curated list using USPS-style two-letter codes, with the product policy explicitly stating whether DC and territories are included.

Later catalog sources should be imported into a staging collection, validated, attributed, and then promoted to the production catalog. Planned sources are UNESCO for heritage, GBIF for biodiversity, and an explicitly licensed geographic/POI source for attractions.

## 9. First-week acceptance tests

- Register a new account and reach the home screen.
- Log in again and see the same account's records.
- Create a country and state check-in.
- See the two records in the list and counts of one country and one state.
- Create another check-in for the same entity and verify the unique count stays unchanged while visit count increases.
- Open two clients for the same account and observe the list update.
- Verify a second account cannot read the first account's check-ins using the Firestore emulator.
- Build and launch the Flutter app on Chrome and Android Emulator when the local SDK is installed.

## 10. Delivery sequence

1. Commit this design and the ADRs.
2. Initialize the Flutter project and routing shell.
3. Add Firebase Auth and protected routes.
4. Add catalog seed data, Firestore rules, repository, and check-in list.
5. Add emulator/E2E coverage.
6. Push each coherent milestone to `feat/mvp-auth-geography` and open a PR for review.
