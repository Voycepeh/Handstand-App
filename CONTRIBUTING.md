# Contributing

## Setup

1. Install JDK 17 and Android SDK 34.
2. Ensure `gradle` is available locally because the wrapper is not checked in.
3. Run:

   ```bash
   gradle testDebugUnitTest
   gradle :app:assembleDebug
   ```

## Branch and PR Expectations

- Base PRs on `main`.
- Keep changes focused on a single concern.
- Update docs when changing workflow, lifecycle, export, replay, drill authoring, or calibration behavior.
- Add or adjust unit tests for behavior changes in pipelines, state derivation, and persistence behavior.
- Prefer small, reviewable PRs with explicit risk notes.

## Product and UX Guardrails

- Prefer one clear path over multiple overlapping actions.
- Avoid adding controls that duplicate the same outcome with different labels.
- Preserve persisted drill data when reopening or editing existing drills.
- Keep drill workflows drill-centric and deterministic.
- Document any workflow simplification that intentionally removes options.

## Documentation Requirements

When touching any of these systems, update matching docs:

- Session lifecycle: `docs/architecture/session-lifecycle.md`
- Video/export pipeline: `docs/architecture/video-pipeline.md`
- Replay resolution rules: `docs/architecture/replay-and-fallback.md`
- Current user workflows: `docs/features/current-user-flows.md`
- Diagrams for significant flow changes: `docs/diagrams/*`

## Testing Guidance

- Run targeted tests first when package-level coverage exists.
- Run the full unit test suite before merge.
- Include failing or flaky test notes in the PR when environment limitations occur.
