# Deploy the FastAPI backend to Cloud Run

This is the staging deployment path for the Achievement Tracker API.

## Architecture

```text
Android/Web
    -> HTTPS Cloud Run service
    -> FastAPI verifies Firebase ID tokens
    -> Cloud SQL PostgreSQL through a Unix socket
    -> Cloud Storage serves reviewed boundary GeoJSON
```

Cloud Run does not use the local `127.0.0.1:5433` Cloud SQL Auth Proxy URL.
It attaches the Cloud SQL instance and exposes the socket at:

```text
/cloudsql/milestonetracker-506101:us-east1:achievement-tracker-db
```

## One-time project setup

The following APIs must be enabled:

```bash
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  sqladmin.googleapis.com
```

The Cloud Run service account is:

```text
achievement-tracker-api@milestonetracker-506101.iam.gserviceaccount.com
```

It needs:

- `roles/cloudsql.client` on the project;
- `roles/secretmanager.secretAccessor` on `cloud-sql-db-password`;
- `roles/storage.objectViewer` on the boundaries bucket.

## Deploy a staging revision

Run from the repository root after replacing `<firebase-project-id>` and the
web origin with real values:

```bash
gcloud run deploy achievement-tracker-api \
  --source ./backend \
  --project milestonetracker-506101 \
  --region us-east1 \
  --service-account achievement-tracker-api@milestonetracker-506101.iam.gserviceaccount.com \
  --add-cloudsql-instances milestonetracker-506101:us-east1:achievement-tracker-db \
  --set-env-vars DB_USER=achievement_app,DB_NAME=achievement_tracker,INSTANCE_UNIX_SOCKET=/cloudsql/milestonetracker-506101:us-east1:achievement-tracker-db,BOUNDARY_BASE_URL=https://storage.googleapis.com/achievement-tracker-boundaries-milestonetracker/boundaries,FIREBASE_PROJECT_ID=<firebase-project-id>,WEB_ORIGIN=<deployed-web-origin> \
  --set-secrets DB_PASSWORD=cloud-sql-db-password:1 \
  --allow-unauthenticated
```

Do not set `FIREBASE_CREDENTIALS_PATH` in Cloud Run. Firebase Admin uses the
Cloud Run service identity when no certificate path is configured.

## Verify

```bash
SERVICE_URL="$(gcloud run services describe achievement-tracker-api \
  --project milestonetracker-506101 \
  --region us-east1 \
  --format='value(status.url)')"

curl --fail "$SERVICE_URL/healthz"
```

An unauthenticated request to `/v1/catalog` returning `401` is expected. The
Android/Web client must send a Firebase ID token in the `Authorization: Bearer`
header.

After the health check passes, update the Android and Web API base URL to the
HTTPS Cloud Run URL. Local SQLite and local Proxy workflows remain available
for development.
