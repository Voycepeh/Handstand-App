# Contributing

## Setup

1. Install JDK 17 and Android SDK 34.
2. Ensure `gradle` is available locally (wrapper is not checked in).
3. Run:

   ```bash
   gradle testDebugUnitTest
   gradle :app:assembleDebug
   ```

See [`TESTING.md`](TESTING.md) for a fuller validation checklist.

## Branch and PR requirements

- Create branches from `main`.
- Target PRs to `main`.
- Keep each PR focused on one primary concern.

## Workflow and terminology expectations

Use current product terminology consistently:

- Home / Drill Hub
- Manage Drills
- Drill Studio
- Live Session
- Upload / Reference Training
- Results / Session History
- Profiles

## Documentation and diagram update rule (required)

If your PR changes UX flow, navigation, architecture boundaries, media pipeline behavior, replay/export logic, upload/reference behavior, or terminology, update related docs and Mermaid diagrams in the same PR.

At minimum review:

- `README.md`
- `ARCHITECTURE.md`
- `docs/architecture/*`
- `docs/features/*`
- `docs/diagrams/*`
- `docs/decisions/*` (if a decision-level behavior changed)

## Product guardrails

- Preserve drill-centric flow integrity.
- Prefer deterministic, non-overlapping actions.
- Keep drill edit/save behavior predictable and reload-safe.
- Do not silently break drill metadata/catalog schema or replay/export/upload/profile workflows.
