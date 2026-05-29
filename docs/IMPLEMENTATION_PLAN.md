# Achievement Tracker — Implementation Plan

## Overview

This document outlines the phased plan to build and ship Achievement Tracker: a cross-platform app (iOS / Android / Web) backed by Firebase and Google Cloud Platform.

---

## Phase 0 — Developer Environment Setup

### 0.1 AI Coding Assistants

**Claude Code (Anthropic)**
```bash
npm install -g @anthropic-ai/claude-code
claude                         # launch in project root
```
Claude Code reads `CLAUDE.md` for project-specific context and assists with code generation, refactoring, PR reviews, and debugging via the terminal.

**GitHub Copilot**
- Install the GitHub Copilot extension in VS Code or JetBrains IDE
- Sign in with your GitHub account
- Copilot provides inline autocomplete and the Copilot Chat panel for multi-file questions
- Both tools complement each other: Copilot for in-editor flow, Claude Code for larger architectural tasks and CLI operations

### 0.2 Google Cloud Account and Project

1. Create a Google account at [accounts.google.com](https://accounts.google.com) if you don't have one
2. Go to [console.cloud.google.com](https://console.cloud.google.com) and create a new project named `achievement-tracker`
3. Enable billing (required for most GCP services beyond the free tier)
4. Note your **Project ID** — you will use it in every CLI command

**Key Google Cloud services to explore:**
- **Firebase** — mobile/web backend-as-a-service (Auth, Firestore, Storage, Hosting, Functions); this is the primary backend for this app
- **Google Cloud Storage** — raw object storage; Firebase Storage is built on top of this
- **App Engine** — serverless managed runtime for a custom backend API (used optionally in later phases for advanced queries and third-party integrations)
- **Cloud Run** — containerized serverless execution (alternative to App Engine for custom backend)
- **Secret Manager** — securely store API keys and credentials (used in CI/CD)

**Install the Google Cloud CLI:**
```bash
# macOS (Homebrew)
brew install --cask google-cloud-sdk

# Verify
gcloud version

# Authenticate and configure
gcloud auth login
gcloud config set project achievement-tracker   # replace with your actual project ID
gcloud auth application-default login           # sets up local dev credentials
```

### 0.3 Firebase Project Setup

Firebase is a suite of Google Cloud services optimized for mobile/web apps. It runs on GCP but has its own console and CLI.

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click "Add project" and select the GCP project you created above
3. Enable the following services in the Firebase console:
   - **Authentication** — enable Email/Password and Phone providers; optionally add Google sign-in
   - **Firestore Database** — create in production mode (you will lock down rules in Phase 2)
   - **Firebase Storage** — for media uploads
   - **Firebase Hosting** — for the web app

**Install the Firebase CLI and connect your project:**
```bash
npm install -g firebase-tools
firebase login
firebase use --add    # select your project and create an alias
```

**Download platform config files:**
- Android: `google-services.json` → place in `app/android/app/`
- iOS: `GoogleService-Info.plist` → place in `app/ios/Runner/`
- Web: copy the Firebase config object into `app/web/index.html` or a Dart config file
- These files are gitignored — each developer downloads their own copy

### 0.4 GitHub Account and Git CLI Setup

1. Create an account at [github.com](https://github.com) if you don't have one
2. Install Git:
   ```bash
   brew install git       # macOS
   git --version          # verify
   ```
3. Configure your identity:
   ```bash
   git config --global user.name "Your Name"
   git config --global user.email "you@example.com"
   ```
4. Authenticate with GitHub:
   ```bash
   brew install gh        # GitHub CLI
   gh auth login          # follow prompts; choose HTTPS + browser auth
   ```
5. The `gh` CLI enables Claude Code and Copilot to help you manage pull requests, issues, and Actions directly from the terminal:
   ```bash
   gh pr create           # open a PR
   gh pr list             # see open PRs
   gh pr review <number>  # review a PR
   ```

**Enable GitHub Actions for CI:**
- GitHub Actions are configured in `.github/workflows/`
- Actions will run lint, tests, and deploy on every PR and merge to main
- Store GCP service account keys and Firebase tokens as GitHub Actions secrets in the repo settings

---

## Phase 1 — Flutter App Scaffold

**Goal:** Running shell app on all three platforms with navigation and theming.

### Tasks
- [ ] Initialize Flutter project: `flutter create --org com.goldenai app`
- [ ] Add core dependencies to `pubspec.yaml`:
  - `firebase_core`, `firebase_auth`, `cloud_firestore`, `firebase_storage`
  - `flutter_riverpod`, `riverpod_annotation`
  - `go_router`
  - `freezed`, `json_serializable` (code generation)
  - `geolocator`, `geocoding`
- [ ] Set up feature-first folder structure under `lib/features/`
- [ ] Configure `go_router` with routes: `/`, `/login`, `/register`, `/home`, `/achievements/new`, `/achievements/:id`
- [ ] Implement app theme (light + dark mode) using `ThemeData`
- [ ] Verify the app runs on Chrome, iOS Simulator, and Android Emulator

### Deliverable
A blank multi-screen app that compiles and runs on all three targets.

---

## Phase 2 — Authentication

**Goal:** Users can register, log in, and switch between accounts.

### Tasks
- [ ] **Email/Password auth:**
  - Registration screen (email, password, display name)
  - Login screen
  - Password reset flow
- [ ] **Account switching:** support signing out and signing into a different account
- [ ] **Phone auth (optional):** SMS OTP flow via Firebase Phone Auth
- [ ] **Google Sign-In (optional/advanced):**
  - Add `google_sign_in` package
  - Configure OAuth client IDs in GCP console for each platform
  - Handle the sign-in flow and link to Firebase Auth
- [ ] Persist auth state with Riverpod; redirect unauthenticated users to `/login`
- [ ] Write Firestore security rules: `allow read, write: if request.auth != null && request.auth.uid == userId`
- [ ] Deploy security rules: `firebase deploy --only firestore:rules`

### Deliverable
Working login/register/logout with protected routes.

---

## Phase 3 — Achievement Data Model and Firestore Integration

**Goal:** Define the data schema and implement CRUD operations.

### Tasks
- [ ] Define `Achievement` Dart model with `freezed`:
  ```dart
  @freezed
  class Achievement with _$Achievement {
    const factory Achievement({
      required String id,
      required String type,        // e.g. "geography.country"
      String? subtype,
      required DateTime timestamp,
      GeoPoint? location,
      String? locationName,
      required String content,
      String? notes,
      String? mediaUrl,
      required DateTime createdAt,
      required DateTime updatedAt,
    }) = _Achievement;
  }
  ```
- [ ] Implement `AchievementRepository` with Riverpod provider:
  - `create(Achievement)` — add to Firestore
  - `update(Achievement)` — update existing
  - `delete(String id)` — soft delete (set `deletedAt` field)
  - `watchAll()` — real-time stream of user's achievements
  - `watchByType(String type)` — filtered stream
- [ ] Set up Firestore offline persistence (enabled by default in Flutter SDK)
- [ ] Create Firestore composite indexes for `(type, timestamp DESC)` queries

### Deliverable
Achievements can be created and read from Firestore with real-time sync.

---

## Phase 4 — Achievement Submission UI

**Goal:** Users can log achievements from the app.

### Tasks
- [ ] **Achievement type selector:** scrollable grid or segmented list of the six categories
- [ ] **Submission form** with fields:
  - Type picker (category + subtype)
  - Date/time picker (defaults to now)
  - Location field: tap to use current GPS, or type a place name
  - Content field: free text with autocomplete hints (species names, country names, etc.)
  - Notes field (optional)
  - Photo picker (optional, uploads to Firebase Storage)
- [ ] Form validation
- [ ] Success/error feedback (snackbar or modal)
- [ ] Offline queue: if Firestore is unavailable, Firestore SDK queues writes automatically

### Deliverable
Users can submit any achievement type with full metadata.

---

## Phase 5 — Achievement List and Detail Views

**Goal:** Users can browse, search, and view their achievement history.

### Tasks
- [ ] **Home dashboard:** summary counts per category (e.g., "47 countries, 3 Michelin stars")
- [ ] **Category list views:** filtered lists per achievement type, sorted by date
- [ ] **Achievement detail screen:** full record with map pin (if location present) and photo
- [ ] **Search:** full-text search across `content` and `locationName` fields (client-side on loaded data; Firestore does not support native full-text search)
- [ ] **Edit and delete:** swipe-to-delete with confirmation; tap to edit
- [ ] Map view (optional): plot geography achievements on an interactive map using `google_maps_flutter`

### Deliverable
Complete read experience for all achievement data.

---

## Phase 6 — Cloud Sync and Multi-Device Support

**Goal:** Records sync seamlessly across devices and platforms.

### Tasks
- [ ] Verify real-time Firestore sync works across two simultaneous clients
- [ ] Implement conflict resolution strategy (last-write-wins via `updatedAt` timestamp)
- [ ] Test offline → online sync: submit records while airplane mode is on, verify they sync when connection is restored
- [ ] Handle account sign-in on a new device — existing data appears immediately

### Deliverable
Full cloud sync validated across iOS, Android, and web.

---

## Phase 7 — Web Deployment

**Goal:** Ship the web app to Firebase Hosting.

### Tasks
- [ ] Configure `flutter build web --release`
- [ ] Set up GitHub Actions workflow: on push to `main`, build and deploy to Firebase Hosting
- [ ] Configure a custom domain (optional)
- [ ] Enable Firebase App Check for web to prevent unauthorized API access

### Deliverable
Public web app accessible at `your-project.web.app`.

---

## Phase 8 — Mobile App Builds

**Goal:** Produce installable iOS and Android builds.

### Tasks
- [ ] **Android:**
  - Configure signing keystore
  - Build release APK / AAB: `flutter build appbundle`
  - Set up GitHub Actions workflow for Android build
- [ ] **iOS:**
  - Configure Xcode signing with Apple Developer account
  - Build `.ipa`: `flutter build ios --release`
  - Set up GitHub Actions workflow for iOS build (requires macOS runner)
- [ ] TestFlight (iOS) and internal track (Google Play) for beta testing

### Deliverable
Installable beta builds on both mobile platforms.

---

## Phase 9 — Optional Backend: App Engine API

**Goal:** Add a custom server for advanced queries, analytics, and third-party data enrichment.

Use cases:
- Validate submitted species names against a taxonomy API (e.g., GBIF for wildlife)
- Validate Michelin restaurant names against a reference list
- Generate shareable achievement cards (server-side image rendering)
- Admin analytics dashboard

### Tasks
- [ ] Choose runtime: Python (Flask) or Node.js (Express) on App Engine Standard
- [ ] Initialize App Engine: `gcloud app create --region=us-central`
- [ ] Implement API endpoints behind Firebase Auth token verification
- [ ] Deploy: `gcloud app deploy`
- [ ] Secure with Cloud Endpoints or API Gateway (optional)

---

## Phase 10 — Polish and Launch

- [ ] Onboarding flow for new users (explain achievement categories)
- [ ] Push notifications (Firebase Cloud Messaging) for streaks or milestones
- [ ] Achievement statistics and visualizations (bar charts, world map heatmap)
- [ ] Social sharing: generate shareable image cards of achievements
- [ ] App Store and Google Play submission
- [ ] Analytics: Firebase Analytics events for key user actions

---

## Milestone Summary

| Phase | Milestone | Target |
|---|---|---|
| 0 | Dev environment fully configured | Week 1 |
| 1 | Flutter app running on all platforms | Week 1–2 |
| 2 | Auth working end-to-end | Week 2–3 |
| 3–4 | Submit and store achievements | Week 3–4 |
| 5–6 | Browse, search, cloud sync | Week 4–5 |
| 7–8 | Web + mobile builds shipped | Week 6 |
| 9 | Optional backend API | Week 7+ |
| 10 | Launch-ready polish | Week 8+ |

---

## Architecture Decision Records

Key decisions are documented in `docs/adr/`. The first ADR to write:
- `ADR-001`: Flutter over React Native — single codebase for iOS/Android/web with strong Firebase SDK support
- `ADR-002`: Firestore over PostgreSQL — real-time sync, offline-first, no server to manage for MVP
- `ADR-003`: Riverpod over Bloc/Provider — code generation, compile-time safety, simpler async patterns
