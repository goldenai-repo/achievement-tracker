# Local FastAPI backend

This project supports two local backend modes:

| Mode | Database | Cloud SQL Proxy | Database password |
|---|---|---:|---:|
| Local development | SQLite from `backend/.env` | No | No |
| Cloud integration testing | Google Cloud SQL PostgreSQL | Yes | Yes, for `achievement_app` |

The Android emulator calls the local API at `http://10.0.2.2:8000`. The API
server, not the Android app, connects to the database.

## Prerequisites

Use Python 3.12 or newer. From the repository root:

```bash
cd backend
source .venv/bin/activate
python --version
```

If the virtual environment does not exist, create it and install the backend:

```bash
/opt/homebrew/bin/python3.12 -m venv .venv
source .venv/bin/activate
python -m pip install -e .
```

The local `.env` file is intentionally ignored by Git. Copy one of the
example files if you need to create it, then set the Firebase credential path
to a file that exists only on your machine.

## Local SQLite mode

Use this mode for UI work that does not require Cloud SQL. It does not need a
Cloud SQL password or a running proxy.

```bash
cd backend
source .venv/bin/activate
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Check that the API is running from another terminal:

```bash
curl http://127.0.0.1:8000/healthz
```

Expected response:

```json
{"status":"ok"}
```

## Cloud SQL integration mode

This mode runs FastAPI locally while using the migrated Cloud SQL database.
Keep the Cloud SQL Auth Proxy running in a separate terminal.

### 1. Authenticate the proxy once per machine

```bash
gcloud auth application-default login
gcloud auth application-default set-quota-project milestonetracker-506101
```

### 2. Start Cloud SQL Auth Proxy

```bash
cloud-sql-proxy \
  --port 5433 \
  milestonetracker-506101:us-east1:achievement-tracker-db
```

Keep this terminal open. Wait for `ready for new connections`.

### 3. Set the database connection for the current shell

The password is the PostgreSQL password for `achievement_app`. It is not the
Firebase password, Google account password, or `postgres` administrator
password. Never commit it or put it in a file tracked by Git.

```bash
cd backend
source .venv/bin/activate

printf 'Cloud SQL password: '
read -r -s CLOUD_SQL_DB_PASSWORD
printf '\n'
export CLOUD_SQL_DB_PASSWORD

export CLOUD_SQL_DATABASE_URL="$(python -c 'import os; from sqlalchemy.engine import URL; print(URL.create("postgresql+psycopg", username="achievement_app", password=os.environ["CLOUD_SQL_DB_PASSWORD"], host="127.0.0.1", port=5433, database="achievement_tracker").render_as_string(hide_password=False))')"
```

Validate the URL without printing its password:

```bash
python -c 'import os; from sqlalchemy.engine import make_url; print(make_url(os.environ["CLOUD_SQL_DATABASE_URL"]).render_as_string(hide_password=True))'
```

It should look like:

```text
postgresql+psycopg://achievement_app:***@127.0.0.1:5433/achievement_tracker
```

### 4. Start FastAPI with Cloud SQL

```bash
DATABASE_URL="$CLOUD_SQL_DATABASE_URL" \
BOUNDARY_DATA_DIR=../data/normalized/geoboundaries/boundaries \
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Wait for:

```text
Application startup complete.
```

Then verify the API:

```bash
curl --max-time 5 http://127.0.0.1:8000/healthz
```

The current local boundary endpoint still reads GeoJSON from
`BOUNDARY_DATA_DIR`. The GeoJSON files have also been uploaded to Cloud
Storage, but Cloud Run integration with that bucket is a separate deployment
step.

## Android emulator

Configure the Android debug build to use:

```text
http://10.0.2.2:8000
```

The emulator cannot use the host machine's `127.0.0.1`. Keep both FastAPI and
the Cloud SQL Proxy running when testing Cloud SQL integration locally.

## Current cloud data state

The current migration has completed:

- 3,430 catalog entities in Cloud SQL;
- 25 historical check-ins in Cloud SQL;
- GeoJSON boundary assets in Cloud Storage.

FastAPI is still a local process during this workflow. After deploying it to
Cloud Run, the local proxy and local FastAPI process will no longer be needed
for normal app use.
