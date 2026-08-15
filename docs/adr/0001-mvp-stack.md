# ADR 0001: Use a JavaScript web client, Python API, and Firebase Auth for the first MVP

- Status: Accepted for MVP
- Date: 2026-07-28

## Context

The first release must support a web E2E demo while keeping a path to Android and iOS. The team does not want to install Flutter. The product also needs a Python backend and a relational model suitable for catalogs, deduplication, ranking, and sync.

## Decision

Use a TypeScript/JavaScript Vite web client, Firebase Authentication for identity, and a Python/FastAPI API backed by PostgreSQL. Deploy the API on Cloud Run and PostgreSQL on Cloud SQL. Use Kotlin/Jetpack Compose for the later Android client, with Kotlin Multiplatform considered for shared mobile logic. Firebase is the identity provider, not the business database.

## Consequences

- The web demo can be developed with the existing JavaScript ecosystem.
- Python owns authorization, validation, transactions, and synchronization.
- PostgreSQL gives strong uniqueness and relational queries for achievement tracking and ranking.
- Firebase Auth avoids implementing password storage and account recovery in the Python service.
- Android/iOS clients must follow the same versioned API contract instead of directly accessing the database.
