# Architecture Guide

This document is the top-level architecture map for the current CaliVision implementation.

## Product workflow anchors

- **Home / Drill Hub** (`Route.Home`)
- **Drills (usage path)** (`Route.Start` with workspace destination -> `Route.DrillWorkspace`)
- **Manage Drills (create/edit path)** (`Route.ManageDrills`)
- **Drill Studio** (`Route.DrillStudio`)
- **Drill Workspace** (`Route.DrillWorkspace`)
- **Live Session** (`Route.Live`)
- **Upload / Reference Training** (`Route.UploadVideo`, `Route.UploadVideoForDrill`)
- **Results / History** (`Route.Results`, `Route.HistoryOverview`, `Route.SessionHistory`)

## Runtime layers

1. **UI and navigation**: `app/ui/**`
2. **Workflow orchestration**: live/upload/drill studio view models and route args
3. **Domain**: `drills/**`, `movementprofile/**`
4. **Analysis**: `pose/**`, `motion/**`, `biomechanics/**`
5. **Media/replay/export**: `recording/**`, `media/**`, `camera/**`, `overlay/**`
6. **Persistence**: `storage/db/**`, `storage/repository/**`, `SessionBlobStorage`

## Key boundaries

- `SessionRepository` is the persistence boundary for sessions, drills, templates, and media status.
- `SessionMediaResolver` resolves replay source from verified media candidates.
- `AnnotatedExportPipeline` handles annotated replay generation.
- `UploadedVideoAnalyzer` pipeline is executed by `UploadVideoProcessingWorker`; `UploadVideoViewModel` enqueues work and observes repository/DB state.

## Rules for contributors

- Keep drill-centric flow integrity intact.
- Prefer one clear path for drill creation/editing outcomes.
- Do not silently break drill metadata/catalog schema.
- Do not silently break replay/export/upload flows.
- Keep naming aligned with current UX terms.
- Any PR that changes workflows, navigation, architecture, terminology, or media flow must update docs and diagrams in the same PR.

## Architecture docs index

- [`docs/architecture/system-overview.md`](docs/architecture/system-overview.md)
- [`docs/architecture/app-modules.md`](docs/architecture/app-modules.md)
- [`docs/architecture/session-lifecycle.md`](docs/architecture/session-lifecycle.md)
- [`docs/architecture/video-pipeline.md`](docs/architecture/video-pipeline.md)
- [`docs/architecture/replay-and-fallback.md`](docs/architecture/replay-and-fallback.md)
- [`docs/architecture/overlay-rendering.md`](docs/architecture/overlay-rendering.md)
- [`docs/architecture/movement-profile-architecture.md`](docs/architecture/movement-profile-architecture.md)

## Diagram index

- [`docs/diagrams/ui-flow.md`](docs/diagrams/ui-flow.md)
- [`docs/diagrams/architecture-subsystems.md`](docs/diagrams/architecture-subsystems.md)
- [`docs/diagrams/sequence-live-session.md`](docs/diagrams/sequence-live-session.md)
- [`docs/diagrams/sequence-import-analysis.md`](docs/diagrams/sequence-import-analysis.md)
- [`docs/diagrams/sequence-export-finalization.md`](docs/diagrams/sequence-export-finalization.md)
- [`docs/diagrams/class-diagram.md`](docs/diagrams/class-diagram.md)
