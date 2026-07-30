# ADR 0003: Kotlin Multiplatform + Compose for mobile, guest-first with direct Firestore sync

- Status: Accepted
- Date: 2026-07-30

_Note: ADRs 0001 and 0002 were introduced on the `feat/mvp-auth-geography`
branch (web MVP). This ADR continues the numbering and partially supersedes
ADR 0001 for mobile clients._

## Context

The product needs Android and iOS apps. The original CLAUDE.md plan called
for Flutter; ADR 0001 later rejected Flutter and named Kotlin/Jetpack Compose
as the Android direction with Kotlin Multiplatform under consideration. The
apps must be fully usable without an account (guest mode); registration and
sign-in are required only to back data up to the cloud.

## Decision

1. **One Kotlin Multiplatform codebase** in `app/`, with Compose
   Multiplatform rendering the UI on both Android and iOS. Only platform
   entry points, SQLite drivers, and small utilities are platform-specific.
2. **Guest-first, local-first storage.** An on-device SQLite database
   (SQLDelight) is the source of truth for the UI in all modes. The app is
   fully functional with zero configuration and zero network.
3. **Cloud backup goes directly to Firestore** (`users/{uid}/achievements`),
   authenticated by Firebase Auth email/password, using the GitLive
   firebase-kotlin-sdk from common code. This diverges from ADR 0001's rule
   that mobile clients must use the versioned Python API: for a single-user
   achievement log, Firestore's offline-friendly sync model fits better and
   removes the server from the critical path. The trade-off is accepted
   knowingly — see Consequences.
4. **Sync model:** local writes mark rows `pendingSync`; while signed in, a
   sync engine pushes pending rows and pulls the remote collection with
   last-write-wins conflict resolution on `updatedAt`. Deletes are soft
   tombstones. Guest data uploads automatically on first sign-in.

## Consequences

- Android and iOS ship from one codebase with shared domain, data, and UI.
- The app works fully offline and without any Firebase project configured;
  builds degrade gracefully when `google-services.json` /
  `GoogleService-Info.plist` are absent.
- The web MVP (FastAPI + PostgreSQL, check-ins/unlocks) and mobile (Firestore,
  achievements) currently have **separate data stores and schemas**. If both
  tracks continue, a reconciliation (one backend or a sync bridge) must be
  designed; until then, accounts created on web and mobile do not share data.
- Firestore security rules (in `firebase/`) are the only server-side
  enforcement for mobile data; they validate ownership and the achievement
  type registry.
