# Achievement Tracker

A cross-platform mobile and web application for logging and celebrating life achievements across dozens of dimensions — from countries visited and wildlife observed, to Michelin-starred meals and UNESCO World Heritage Sites.

## Vision

Most people accumulate rich life experiences but have no structured way to record, visualize, or share them. Achievement Tracker provides a personal logbook across six major achievement categories, with timestamps, geolocations, and cloud sync so your records travel with you across every device.

---

## Features

### Guest Mode First
- The mobile app works fully without an account — achievements are stored on-device
- Registration / login is required only to back your data up to the cloud
- Data logged as a guest uploads automatically after the first sign-in

### Authentication
- Email + password registration and login
- Switch between multiple accounts
- Phone auth and Google OAuth (planned)

### Achievement Categories

| Category | What You Track |
|---|---|
| **Geography** | Countries, states/provinces, and cities you have visited |
| **Wildlife** | Animal species and wild plant species you have observed in nature |
| **Culture** | Museums and galleries you have visited |
| **Entertainment** | Movies you have watched |
| **Culinary** | Michelin-starred restaurants you have dined at |
| **Heritage** | UNESCO World Heritage Sites you have visited |

### Record Structure
Every achievement submission includes:
- **Type** — which category and sub-type (e.g., "Wildlife > Animal")
- **Timestamp** — date and optional time of the achievement
- **Location** — GPS coordinates or manually entered place name
- **Content** — the specific achievement detail (e.g., species name, restaurant name, city name)
- **Notes** — optional free-text description or memory
- **Media** — optional photo attachment (cloud-stored)

### Cloud Sync
All records are stored in the cloud and synchronized in real time across iOS, Android, and web. Offline submissions queue locally and sync when connectivity is restored.

---

## Platform Support

| Platform | Status |
|---|---|
| iOS | Supported |
| Android | Supported |
| Web (PWA) | Supported |

---

## Tech Stack

### Mobile (iOS + Android)
- **Kotlin Multiplatform + Compose Multiplatform** — one shared codebase for both platforms (see `docs/adr/0003-mobile-kmp-compose-firestore.md`)
- **SQLDelight** — on-device SQLite storage (guest mode / local source of truth)
- **GitLive firebase-kotlin-sdk** — Firebase Auth + Firestore from shared Kotlin

### Web (separate MVP track)
- **TypeScript / Vite** client with a **Python FastAPI + PostgreSQL** backend, on branch `feat/mvp-auth-geography`

### Backend
- **Firebase Authentication** — handles all auth flows
- **Cloud Firestore** — NoSQL real-time database for achievement records
- **Firebase Storage** — media (photo) uploads
- **Firebase App Check** — protects backend resources from abuse
- **Google App Engine** (optional) — custom API server for advanced queries and third-party integrations

### Infrastructure
- **Google Cloud Platform** — primary cloud provider
- **Firebase Hosting** — web app deployment and CDN
- **GitHub Actions** — CI/CD pipelines for automated testing and deployment
- **GitHub** — source control and pull request management

### Development Tooling
- **Claude Code** — AI-assisted development CLI (Anthropic)
- **GitHub Copilot** — in-editor AI code completion
- **Google Cloud CLI (`gcloud`)** — local cloud resource management
- **Firebase CLI** — emulator suite, hosting deploy, Firestore rules deploy

---

## Repository Structure

```
achievement-tracker/
├── docs/                        # Architecture, plans, ADRs
│   ├── IMPLEMENTATION_PLAN.md
│   ├── ARCHITECTURE.md
│   └── adr/                     # Architecture Decision Records
├── app/                         # Kotlin Multiplatform mobile app (iOS + Android)
│   ├── composeApp/              # Shared Kotlin + Compose UI
│   │   └── src/
│   │       ├── commonMain/      # features/ (auth, achievements, sync), core/, ui/
│   │       ├── androidMain/     # Android entry points + SQLite driver
│   │       ├── iosMain/         # iOS entry points + SQLite driver
│   │       └── commonTest/      # unit tests
│   └── iosApp/                  # Xcode project (XcodeGen) + SwiftUI host
├── backend/                     # Optional App Engine API server
│   ├── src/
│   └── app.yaml
├── firebase/                    # Firestore rules, indexes, functions
│   ├── firestore.rules
│   ├── firestore.indexes.json
│   └── functions/
├── .github/
│   └── workflows/               # CI/CD pipelines
├── CLAUDE.md                    # Claude Code project instructions
└── README.md
```

---

## Getting Started

### Prerequisites
- JDK 17+ and the Android SDK (easiest via [Android Studio](https://developer.android.com/studio))
- Xcode 16+ and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (for iOS)
- [Firebase CLI](https://firebase.google.com/docs/cli) and a Firebase project — **optional**, only needed for cloud sync

See [`docs/MOBILE_SETUP.md`](docs/MOBILE_SETUP.md) for the full mobile guide.

### Setup Steps

See [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) for the full step-by-step setup guide covering:
1. Coding assistant configuration (Claude Code, Copilot)
2. Google Cloud account and project setup
3. Firebase service initialization
4. GitHub and Git CLI configuration
5. Local development environment

### Quick Start

```bash
# Android: build + install the debug APK (guest mode works out of the box)
cd app
./gradlew :composeApp:assembleDebug
adb install composeApp/build/outputs/apk/debug/composeApp-debug.apk

# iOS: generate the Xcode project, then run from Xcode or the CLI
cd app/iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17' build CODE_SIGNING_ALLOWED=NO
```

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feat/your-feature`)
3. Commit your changes following [Conventional Commits](https://www.conventionalcommits.org/)
4. Open a pull request — GitHub Copilot and Claude Code are configured to assist with PR reviews

---

## License

MIT
