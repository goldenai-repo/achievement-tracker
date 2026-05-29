# Achievement Tracker — Claude Code Instructions

## Project Overview

Cross-platform Flutter app (iOS / Android / Web) backed by Firebase / Google Cloud Platform. Users log life achievements across six categories: geography, wildlife, culture, entertainment, culinary, and UNESCO heritage sites.

## Tech Stack

- **Frontend:** Flutter (Dart), Riverpod, go_router
- **Backend:** Firebase Auth, Cloud Firestore, Firebase Storage, optional App Engine
- **Cloud:** Google Cloud Platform, Firebase
- **CI/CD:** GitHub Actions
- **Languages:** Dart (app), TypeScript (Firebase Functions), Python or Node.js (App Engine if used)

## Repository Layout

```
app/          Flutter app — all client code lives here
backend/      Optional App Engine API (advanced queries, integrations)
firebase/     Firestore security rules, indexes, Cloud Functions
docs/         Architecture docs and implementation plan
.github/      GitHub Actions workflows
```

## Development Commands

```bash
# Flutter
flutter pub get              # install dependencies
flutter run -d chrome        # run on web
flutter run                  # run on connected device/simulator
flutter test                 # run unit + widget tests
flutter analyze              # static analysis

# Firebase
firebase emulators:start     # start local emulator suite (Auth, Firestore, Storage)
firebase deploy --only firestore:rules   # deploy security rules
firebase deploy --only hosting           # deploy web app

# Google Cloud
gcloud auth login            # authenticate CLI
gcloud config set project <PROJECT_ID>
gcloud app deploy            # deploy App Engine backend
```

## Code Conventions

- Feature-first folder structure under `app/lib/features/`
- Each feature has `data/`, `domain/`, and `presentation/` layers
- Use Riverpod providers for all state; no raw `setState` in feature code
- Firestore document IDs: always use user UID as path prefix (`users/{uid}/achievements/{docId}`)
- All Firestore writes must go through the repository layer, never directly from UI widgets
- Timestamps: always store as UTC, display in local time
- Error handling: use sealed `Result<T>` types rather than throwing exceptions from repositories

## Firestore Data Model

```
users/{uid}
  achievements/{achievementId}
    type: string          # "geography.country" | "wildlife.animal" | ...
    subtype: string       # optional further classification
    timestamp: timestamp  # UTC
    location: geopoint    # nullable
    locationName: string  # human-readable place
    content: string       # e.g. species name, restaurant name
    notes: string         # optional
    mediaUrl: string      # optional Firebase Storage URL
    createdAt: timestamp
    updatedAt: timestamp
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

- Users can only read and write their own documents (`request.auth.uid == userId`)
- No unauthenticated reads
- Validate all fields on write (type must be in the allowed enum, timestamp must be present)

## Testing Strategy

- Unit tests for all repository and domain logic
- Widget tests for key screens (login, achievement submission form, list views)
- Integration tests using Firebase emulator suite
- No mocking of Firestore — use the local emulator instead

## Environment Variables / Secrets

Never commit `google-services.json`, `GoogleService-Info.plist`, or any `.env` file. These are gitignored. Use Firebase environment config and CI secrets for deployment.

## PR Workflow

- Branch naming: `feat/`, `fix/`, `chore/`, `docs/`
- All PRs require passing CI (lint + tests)
- Squash merge to main
