# Achievement Tracker

A cross-platform mobile and web application for logging and celebrating life achievements across dozens of dimensions — from countries visited and wildlife observed, to Michelin-starred meals and UNESCO World Heritage Sites.

## Vision

Most people accumulate rich life experiences but have no structured way to record, visualize, or share them. Achievement Tracker provides a personal logbook across six major achievement categories, with timestamps, geolocations, and cloud sync so your records travel with you across every device.

---

## Features

### Authentication
- Email + password registration and login
- Phone number authentication (optional)
- Switch between multiple accounts
- Google OAuth single sign-on (optional, advanced)

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
| Web | MVP target |
| Android | Planned Kotlin client |
| iOS | Planned after API stabilization |

---

## Tech Stack

### Frontend
- **TypeScript/JavaScript + Vite** — first web demo
- **Firebase Web SDK** — browser authentication
- **Kotlin/Jetpack Compose** — planned Android client after the web/API contract stabilizes

### Backend
- **Python + FastAPI** — API, authorization, validation, and domain logic
- **Firebase Authentication** — registration/login and identity provider
- **Firebase Admin Python SDK** — verifies Firebase ID tokens in FastAPI
- **PostgreSQL** — source of truth for catalog, check-ins, unlocks, and sync
- **SQLAlchemy + Alembic** — persistence and migrations

### Infrastructure
- **Google Cloud Platform** — primary cloud provider
- **Cloud Run** — Python API deployment
- **Cloud SQL** — managed PostgreSQL
- **Firebase Hosting or Cloud Run** — web app deployment
- **GitHub Actions** — CI/CD pipelines for automated testing and deployment
- **GitHub** — source control and pull request management

### Development Tooling
- **Claude Code** — AI-assisted development CLI (Anthropic)
- **GitHub Copilot** — in-editor AI code completion
- **Google Cloud CLI (`gcloud`)** — local cloud resource management
- **Firebase CLI** — Firebase project and Auth tooling when needed

---

## Repository Structure

```
achievement-tracker/
├── docs/                        # Architecture, plans, ADRs
│   ├── IMPLEMENTATION_PLAN.md
│   ├── ARCHITECTURE.md
│   └── adr/                     # Architecture Decision Records
├── web/                         # Vite TypeScript web client
├── backend/                     # FastAPI Python service
│   ├── app/
│   └── tests/
├── data/seed/                   # Versioned country/state catalog seeds
├── infra/                       # Docker and deployment configuration
├── .github/
│   └── workflows/               # CI/CD pipelines
├── CLAUDE.md                    # Claude Code project instructions
└── README.md
```

---

## Getting Started

### Prerequisites
- [Node.js](https://nodejs.org/) >= 20
- Python >= 3.12 and [uv](https://docs.astral.sh/uv/)
- Docker Desktop for local PostgreSQL
- [Google Cloud CLI](https://cloud.google.com/sdk/docs/install) for deployment
- A Firebase project with Email/Password Authentication enabled

### Setup Steps

See [`docs/IMPLEMENTATION_PLAN_WEB_PYTHON.md`](docs/IMPLEMENTATION_PLAN_WEB_PYTHON.md) for the active step-by-step setup guide covering:
1. Coding assistant configuration (Claude Code, Copilot)
2. Google Cloud account and project setup
3. Firebase service initialization
4. GitHub and Git CLI configuration
5. Local development environment

For the current backend startup commands, including local SQLite mode and
Cloud SQL integration mode, see [`docs/LOCAL_BACKEND.md`](docs/LOCAL_BACKEND.md).

### Quick Start (after full setup)

```bash
# Start PostgreSQL locally
docker compose -f infra/docker-compose.yml up -d db

# Run the Python API
cd backend && uv run uvicorn app.main:app --reload

# Run the web client
cd web && npm install && npm run dev
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
