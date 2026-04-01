# System Overview

Inversion Coach is now best understood as a drill-centric coaching system with shared live, imported-video, drill-authoring, reference-training, and calibration workflows.

## Runtime Subsystems

- **UI/navigation**: Compose screens route users through home, drill hub, manage drills, drill studio, live coaching, upload analysis, calibration, history, and results.
- **Live orchestration**: `LiveCoachingViewModel` coordinates countdown, frame ingestion, session stop/finalization, replay preparation, and export kickoff.
- **Upload orchestration**: upload flow view models/coordinators handle imported asset processing, persistence, and optional reference/template outcomes.
- **Drill workflow layer**: drill selection, drill editing, drill metadata, and drill-linked comparison/reference flows provide the main context for practice.
- **Analysis engines**: pose processing, biomechanics, readiness/fault detection, movement profiling, and summary generation.
- **Recording/export**: timeline capture, normalization, annotated composition, media verification, compression, and cleanup.
- **Persistence**: Room and blob storage through `SessionRepository`, `SessionBlobStorage`, and drill/template persistence paths.

## Cross-Flow Invariants

1. Drill context should remain stable across live practice, upload analysis, comparison, and history review.
2. Session metadata is persisted even when annotated export fails.
3. Replay is selected from validated assets, preferring annotated output when ready.
4. Raw capture remains a fallback when annotated output is unavailable or invalid.
5. Calibration/profile context should be available to both live and imported analysis paths.
6. Export and finalization events remain diagnostics-heavy because these are async, failure-prone boundaries.
