# Achievement Tracker - Current Status

Snapshot of this repository’s source. Claims are limited to what the code contains. iOS has not been built or run in Xcode from this Windows environment.

This tree contains the KMP mobile app (`app/`), Firestore rules (`firebase/firestore.rules`), and docs. There is no `backend/` directory and no `.github/workflows`.

## Architecture

Kotlin Multiplatform + Compose Multiplatform in `app/composeApp`. Shared UI, domain, and data live in `app/composeApp/src/commonMain`. Platform source sets supply entry points, SQLDelight drivers, `expect`/`actual` utilities (`core/Platform.kt`, `core/db/DriverFactory.kt`), and Firebase bootstrap.

- Feature folders: `features/achievements` (data / domain / presentation), `features/auth` (data / presentation), `features/sync` (`SyncEngine.kt`, `SyncCoordinator.kt` — no layered subpackages).
- UI: Compose Material3 + Navigation Compose (`ui/App.kt`). ViewModels: Home/Log/Account use `StateFlow`; form and auth screens use Compose `mutableStateOf`.
- DI: hand-rolled `di/AppGraph.kt`, initialized once per platform.
- Local SQLDelight SQLite is the UI source of truth (`Achievement.sq`). Cloud Firestore is used only in `features/sync`; Firebase Auth is used in `AuthRepository.kt`.
- Cloud is optional. Without per-developer Firebase config, the app stays guest-only (`docs/MOBILE_SETUP.md`, `composeApp/build.gradle.kts`, `iOSApp.swift`).

## Existing Features

**Implemented**

- Screens (`ui/App.kt`): Home, Log, Account tabs; create/edit form; sign-in; register. Tapping a log row opens the form (no separate detail route).
- Nine achievement types (`core/model/AchievementType.kt`), mirrored in `firebase/firestore.rules`.
- Local CRUD: create, update, get, watch (all / by type / recent / counts / pending), soft-delete (`AchievementRepository.kt`).
- Form fields bound in `FormScreen.kt`: category, content, optional place name, date, optional notes.
- Validation: required type and content; email format; register password length ≥ 6 and confirm match (`AchievementValidation.kt`, `AuthViewModel.kt`). Sign-in does not locally validate password length.
- Email/password register, sign-in, sign-out (`AuthRepository.kt`).
- Guest mode when Firebase config is absent. Account always shows guest, signed-in, or “cloud not configured”. Home’s guest backup card shows only when cloud is configured and the user is signed out (`HomeScreen.kt`, `AccountScreen.kt`).
- Sync while signed in: push `pendingSync` rows, then pull the collection with `get()` (not a realtime listener), last-write-wins on `updatedAt`. Triggered on sign-in, after local writes, and by Sync now (`SyncEngine.kt`, `SyncCoordinator.kt`). Guest rows stay pending until the first signed-in sync.
- Account UI: pending count, last sync, errors, Sync now, Sign out.
- Unit tests in `commonTest` for types, validation, and `SyncLogic` (not Firestore I/O or UI).

**Partially implemented**

- Location: `locationName` is editable; `latitude` / `longitude` exist on the model, SQLDelight schema, and Firestore mapping, but the form never captures GPS and neither platform requests location permission.
- Media / subtype: `mediaUrl` and `subtype` are persisted and synced if already set (e.g. pulled from remote); there is no UI to set them.
- Multi-account: sign-out then sign-in works. On a different uid, `handleAccountSwitch` deletes rows with `pendingSync = 0` and keeps unsynced rows. No dedicated switcher UI.
- Date/time: new records default to `nowEpochMillis()` (includes time). The form only has a date picker; choosing a date stores noon UTC. No time-of-day control.
- Log filters: chips exist per type key but labels use `type.category`, so Geography and Wildlife repeat (`ListScreen.kt`).

## Android Status

The Android app is the Compose `composeApp` module (`applicationId` `com.goldenai.achievements`, minSdk 26), not a separate native UI.

- `MainApplication.kt`: `cloudAvailable` from `FirebaseApp.getApps`; `AppGraph.init`.
- `MainActivity.kt`: `setContent { App() }`.
- `DriverFactory.android.kt`: `AndroidSqliteDriver` → `achievements.db`.
- `Platform.android.kt`: UUID, clock, date formatting.
- `AndroidManifest.xml`: `INTERNET` only; no location/camera/storage permissions.
- Google Services plugin applied only if `app/composeApp/google-services.json` exists. That file is gitignored (`app/.gitignore`).

## iOS Status

Shared Compose UI is hosted by a thin Swift shell. The Xcode project files exist under `app/iosApp/iosApp.xcodeproj`; they have not been verified to build from this environment.

- Kotlin `iosMain`: `MainViewController.kt` (`ComposeUIViewController { App() }`), `DriverFactory.ios.kt` (`NativeSqliteDriver`), `Platform.ios.kt`.
- Swift (`app/iosApp/iosApp/`): `iOSApp.swift` calls `FirebaseApp.configure()` only if `GoogleService-Info.plist` is in the bundle; `ContentView.swift` is a `UIViewControllerRepresentable` that only embeds Compose. No other SwiftUI screens or data layer.
- `project.yml`: XcodeGen target, iOS 15.0, FirebaseAuth + FirebaseFirestore SPM, Gradle “Compile Kotlin Framework” phase, bundle id `com.goldenai.achievements`. `iosArm64` / `iosSimulatorArm64` only (`composeApp/build.gradle.kts`).
- `Info.plist`: no location/camera/photo usage strings. `GoogleService-Info.plist` is gitignored.

## Missing / Incomplete Features

Not present in source:

- GPS capture, maps, photo picker, Firebase Storage (no storage dependency; no related permissions).
- Search, dedicated detail screen, autocomplete.
- Phone auth, Google sign-in, password reset.
- Compose UI tests; no Android/iOS instrumented tests.
- Web client, backend API, and GitHub Actions (mentioned in `README.md` / `docs/IMPLEMENTATION_PLAN.md`; not in this tree). `docs/IMPLEMENTATION_PLAN.md` still describes a Flutter app.

Schema-only (columns exist; the form does not write them): `subtype`, `latitude`, `longitude`, `mediaUrl`. Local validation does not enforce the Firestore content max of 500 characters (`firebase/firestore.rules`).

## Next Tasks

1. Decide whether to collect GPS, subtype, and media in the form, or stop carrying unused columns.
2. Fix Log filter chip labels (`type.label` vs `type.category`); add a time control if timestamps should be user-editable beyond date.
3. Align local validation with Firestore rules (content length).
4. Auth extras only if required: password reset, then Google/phone.
5. On a Mac with Xcode: build/run `app/iosApp` (regenerate with XcodeGen if `project.yml` changes). See `docs/MOBILE_SETUP.md`.
