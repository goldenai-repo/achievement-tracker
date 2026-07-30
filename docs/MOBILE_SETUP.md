# Mobile App Setup (Android + iOS)

The mobile app lives in `app/` and is a Kotlin Multiplatform project with a
shared Compose Multiplatform UI. It works **out of the box in guest mode** —
no Firebase project, no account, all data stored on-device in SQLite. Cloud
backup (Firebase Auth + Firestore) lights up only when you add the
per-developer Firebase config files described below.

## Prerequisites

| Tool | Notes |
|---|---|
| JDK 17+ | Android Studio's bundled JBR works: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` |
| Android SDK | Install via Android Studio; `app/local.properties` needs `sdk.dir=...` (created automatically by Android Studio) |
| Xcode 16+ | Full Xcode, not just Command Line Tools. If `xcode-select -p` prints `CommandLineTools`, either run `sudo xcode-select -s /Applications/Xcode.app` or prefix builds with `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` |
| XcodeGen | `brew install xcodegen` — regenerates `iosApp.xcodeproj` from `iosApp/project.yml` |

## Build and run — Android

```bash
cd app
./gradlew :composeApp:assembleDebug          # APK at composeApp/build/outputs/apk/debug/
./gradlew :composeApp:testDebugUnitTest      # unit tests
# install on a running emulator/device:
adb install composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

## Build and run — iOS

```bash
cd app/iosApp
xcodegen generate                            # regenerate the Xcode project (checked-out repos must run this once)
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17' build CODE_SIGNING_ALLOWED=NO
```

Or open `iosApp.xcodeproj` in Xcode and hit Run. The "Compile Kotlin
Framework" build phase invokes Gradle; it looks for `JAVA_HOME`, then
`/usr/libexec/java_home`, then Android Studio's bundled JDK.

Known cosmetic issue: some recent iOS **simulator** runtimes fail to load
AppleColorEmoji into Skia, so the emoji category icons render as boxes in the
simulator. Real devices are unaffected.

## Enabling cloud sync (optional, per developer)

Guest mode needs none of this. To enable account registration and Firestore
backup:

1. In the [Firebase console](https://console.firebase.google.com), open (or
   create) the project and enable **Authentication → Email/Password** and
   **Firestore Database**.
2. Register an Android app with package `com.goldenai.achievements`, download
   `google-services.json`, and place it at `app/composeApp/google-services.json`.
3. Register an iOS app with bundle ID `com.goldenai.achievements`, download
   `GoogleService-Info.plist`, place it at `app/iosApp/iosApp/GoogleService-Info.plist`,
   and re-run `xcodegen generate` so it is bundled.
4. Deploy the security rules: `cd firebase && firebase deploy --only firestore:rules`.
5. Rebuild. The Gradle build applies the Google services plugin only when the
   JSON file exists; the iOS app calls `FirebaseApp.configure()` only when the
   plist is present. Both config files are gitignored — never commit them.

## How guest → cloud upload works

- Every local write sets `pendingSync = 1` in the on-device SQLite database
  (the local DB is always the source of truth for the UI).
- Sync only runs while signed in: pending rows (including guest-era rows) are
  pushed to `users/{uid}/achievements/{id}`, then the remote collection is
  pulled and merged last-write-wins on `updatedAt`.
- Deletes are soft (tombstones with `deleted: true`) so they propagate across
  devices without resurrecting records.
- If a *different* account signs in on the same device, previously synced rows
  are removed first; unsynced local rows are kept and upload to the new
  account.
