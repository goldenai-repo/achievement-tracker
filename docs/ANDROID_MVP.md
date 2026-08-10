# Android MVP

The Android client lives in `app/` and uses Kotlin, Compose, SQLDelight, and
Firebase Authentication. It does not write achievement data directly to
Firestore. Authenticated requests use a Firebase ID token and call the same
FastAPI endpoints as the web client.

## Local setup

1. Install JDK 17 and an Android SDK through Android Studio.
2. Put the Firebase Android config at:
   `app/composeApp/google-services.json`.
3. Enable Email/Password authentication in the Firebase project.
4. Start the API from the repository root:

   ```bash
   cd backend
   uv run uvicorn app.main:app --reload --port 8000
   ```

5. Build for an Android emulator. The default debug API URL is
   `http://10.0.2.2:8000`:

   ```bash
   cd app
   JAVA_HOME=/path/to/jdk-17 ./gradlew :composeApp:assembleDebug
   ```

For a physical device or a deployed API, pass a different URL:

```bash
./gradlew -PAPI_BASE_URL=https://your-api.example.com :composeApp:assembleDebug
```

## Current flow

- Sign in or register with Firebase Auth.
- Search countries through `GET /v1/catalog?kind=country`.
- Search first-level regions through `GET /v1/catalog?kind=admin1&parent_id=...`.
- Submit a visit through `POST /v1/checkins`.
- Load history and summary through `GET /v1/checkins` and `GET /v1/summary`.
- Cache successful responses in the local SQLDelight database.

Guest records are kept locally as pending rows. The first implementation
uploads them one at a time after login; a batch sync endpoint will be added
before production offline sync.
