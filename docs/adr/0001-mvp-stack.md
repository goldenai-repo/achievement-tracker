# ADR 0001: Use Flutter and Firebase for the first MVP

- Status: Accepted for MVP
- Date: 2026-07-28

## Context

The first release must support a web E2E demo while keeping a path to Android and iOS. The team also wants a Google Cloud deployment and rapid iteration on authentication, list data, and synchronization.

## Decision

Use Flutter for the shared client and Firebase Authentication plus Cloud Firestore for the first backend. Treat Firebase as the initial Google Cloud backend. Add Python on Cloud Run only when ingestion, AI, or server-side integrations need a custom service.

## Consequences

- One client codebase covers Web, Android, and iOS.
- Auth, security rules, real-time listeners, and local development can be validated early.
- The MVP has less custom infrastructure to operate.
- Flutter/Dart becomes a required production skill; Kotlin is kept as a separate learning track.
- A future custom API can be introduced without changing the domain model if repositories remain the client boundary.
