# Android release checklist

The release build uses the current HTTPS Cloud Run service by default. The
debug build keeps the local emulator address (`http://10.0.2.2:8000`) through
`API_BASE_URL` in `app/gradle.properties`.

## Before uploading

1. Deploy the current `backend` to Cloud Run and confirm these endpoints on the
   deployed URL: `/healthz`, `/v1/me`, `/v1/catalog`, and
   `/v1/rankings/geography`.
2. Confirm Cloud Run can reach Cloud SQL, Secret Manager, Cloud Storage, and
   Firebase Admin. Do not put database passwords or service-account keys in the
   repository or APK.
3. Confirm `app/composeApp/google-services.json` belongs to the production
   Firebase project and is available locally. It is intentionally not a secret
   substitute for server-side configuration.
4. Create and safely store a Play App Signing/upload keystore. The keystore
   must not be committed.
5. Verify the application ID (`com.goldenai.achievements`), version code, and
   release API URL before the first upload.

## Build a signed bundle

Open the `app` directory and use Android Studio's **Build > Generate Signed
Bundle / APK**. Choose **Android App Bundle** and the `release` variant.

For CI or a local scripted build, add a signing configuration that reads the
keystore path and passwords from environment variables, then run:

```bash
export ANDROID_KEYSTORE_PATH=/secure/path/play-upload.jks
export ANDROID_KEYSTORE_PASSWORD='…'
export ANDROID_KEY_ALIAS='…'
export ANDROID_KEY_PASSWORD='…'

JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :composeApp:bundleRelease
```

If those four variables are absent, Gradle intentionally produces an unsigned
bundle instead of failing or using a development key.

The output is under
`app/composeApp/build/outputs/bundle/release/composeApp-release.aab`.

## Account deletion

The app now re-authenticates the user, calls `DELETE /v1/me`, removes the
Firebase account and server-side application data, then clears the device
cache. Google Play's account-deletion declaration still requires a publicly
reachable web deletion request path and a privacy policy URL; those are
release-console/product tasks, not APK signing settings.
