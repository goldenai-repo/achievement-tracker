# ADR 0002: Separate check-ins from unique unlocks

- Status: Accepted for MVP
- Date: 2026-07-28

## Context

The product needs to represent both individual experiences and deduplicated achievements. A user may visit the same state several times, but it should count as one unlocked state while preserving the visit history.

## Decision

Store each visit in `users/{uid}/checkins`. Store the deduplicated achievement projection in `users/{uid}/unlocks`, keyed by the canonical catalog entity ID. Maintain the projection transactionally in the first implementation.

## Consequences

- Visit history and achievement counts do not compete for one record shape.
- Maps and timelines can use check-ins; progress cards and rankings can use unlocks.
- Catalog identifiers must be stable and must not be based on localized display names.
- A trusted server-side projection can replace the client transaction later without changing the public domain model.
