# Replay and Fallback

Replay resolution is deterministic and shared across live results, history, and upload-analysis outcomes.

## Candidate order

1. **Verified annotated media** (preferred).
2. **Verified raw media** (fallback).
3. **Explicit no-replay state** when neither candidate is valid.

## Decision inputs

- Annotated export status.
- Selected replay URI (`bestPlayableUri`) vs available artifacts.
- Stale in-flight recovery (`PROCESSING`/`PROCESSING_SLOW`/`VALIDATING_INPUT` with no output and no active export owner is terminalized as `ANNOTATED_FAILED`).
- Raw/annotated persistence outcomes.
- Media verification/readability checks.
- Resolver policy for preferred but unavailable candidates.

## Annotated-first with truthful fallback

The app should not hide failures by pretending annotated export always succeeds. It should also avoid blocking user review when raw media is available. Resolver output should reflect real persisted state.

For uploaded analysis, annotated export success and replay selection are now treated separately:

- Annotated artifact can be **present + verified** (`annotatedVideoUri` populated, `annotatedExportStatus=ANNOTATED_READY`).
- Raw replay can still be **chosen as final playback** (`bestPlayableUri` points to raw) when overlay quality is degraded.
- This degraded-selection path should not rewrite annotated success into `ANNOTATED_FAILED`; instead it stores a density warning while keeping the artifact state truthful.

## Stale export recovery contract

When persisted state is inconsistent (for example `rawPersistStatus=SUCCEEDED`, `annotatedExportStatus=PROCESSING`, blank `annotatedVideoUri`), the repository now recovers to a terminal-safe state with:

- `annotatedExportStatus = ANNOTATED_FAILED`
- `annotatedExportFailureReason = EXPORT_INTERRUPTED_OR_STALE`

Recovery runs during app startup and during hydration of Home, History, Results, and session-open flows so stale sessions cannot keep the app in a non-terminal crash-prone state.

Upload-job stall detection now also checks whether the WorkManager upload work is actively running before marking a job stalled. This avoids false `UPLOAD_JOB_STALLED` writes while processing continues off-screen.

## Contributor guidance

If replay behavior changes, update:

- `docs/architecture/video-pipeline.md`
- `docs/architecture/session-lifecycle.md`
- `docs/features/session-history.md`
- relevant sequence diagrams
