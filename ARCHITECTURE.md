# Architecture Guide

This document is the top-level map for the current app shape. The app is no longer just a pair of live/import flows. It now centers around drill-driven workflows, authoring, reference-based comparison, calibration, and resilient replay/export behavior.

## Core Layers

1. **UI layer** (`ui/*`)
   Compose screens, navigation routes, workflow entry points, and user interaction state.

2. **Workflow / orchestration layer** (`ui/live`, `ui/upload`, drill workflow view models)
   Coordinates countdown, live session lifecycle, imported-video analysis, drill editing flows, and async pipeline control.

3. **Domain layer** (`drills/*`, `movementprofile/*`, `calibration/*`)
   Owns drill definitions, reference templates, baseline logic, calibration/body profile behavior, and session option resolution.

4. **Analysis layer** (`pose/*`, `motion/*`, `biomechanics/*`)
   Handles pose processing, scoring, phase detection, readiness, and issue classification.

5. **Recording / export layer** (`recording/*`, `camera/*`, `overlay/*`)
   Handles capture, overlay timelines, normalization, composition, verification, and replay preparation.

6. **Data / persistence layer** (`storage/*`)
   Owns Room entities, repositories, blob persistence, and cross-flow session truth.

## Current Product Ownership Boundaries

- **Live session state owner**: `LiveCoachingViewModel`
- **Uploaded analysis owner**: upload/import analysis coordinator classes under `ui/upload`
- **Drill authoring owner**: Drill Studio and drill repository / mapper paths
- **Reference/baseline ownership**: drill-linked movement profile and template persistence flows
- **Calibration/profile ownership**: calibration and active body profile modules
- **Replay source decision owner**: replay/media resolver helpers used by live, results, and share/save flows
- **Persistence boundary**: `SessionRepository` / `SessionBlobStorage`

## Architecture Themes That Matter Now

### Drill-centric workflows
Most user journeys should start from a drill or return cleanly to drill context. Live practice, uploaded attempts, reference comparison, and drill editing should not feel like disconnected systems.

### Simplified authoring UX
Drill Studio should favor one obvious save path, truthful validation, reliable persistence, and minimal destructive actions.

### Calibration-aware analysis
Body profile and calibration data should influence analysis behavior consistently across live and imported flows.

### Replay resilience
Recording success, export success, and replay availability are related but independent. The app preserves session truth even when annotated export fails.

## Most Important Docs

- [System overview](docs/architecture/system-overview.md)
- [App modules and boundaries](docs/architecture/app-modules.md)
- [Session lifecycle](docs/architecture/session-lifecycle.md)
- [Video pipeline](docs/architecture/video-pipeline.md)
- [Overlay rendering](docs/architecture/overlay-rendering.md)
- [Replay and fallback](docs/architecture/replay-and-fallback.md)
- [Calibration and scoring](docs/architecture/calibration-and-scoring.md)
- [Current user flows](docs/features/current-user-flows.md)

## Diagrams

- [UI flow](docs/diagrams/ui-flow.md)
- [Class diagram](docs/diagrams/class-diagram.md)
- [Live session sequence](docs/diagrams/sequence-live-session.md)
- [Import analysis sequence](docs/diagrams/sequence-import-analysis.md)
- [Export finalization sequence](docs/diagrams/sequence-export-finalization.md)

## Decision Records

- [ADR-001 annotated export strategy](docs/decisions/adr-001-annotated-export-strategy.md)
- [ADR-002 replay fallback strategy](docs/decisions/adr-002-replay-fallback-strategy.md)
- [ADR-003 session duration source of truth](docs/decisions/adr-003-session-duration-source-of-truth.md)
- [ADR-004 product workflow simplification](docs/decisions/adr-004-product-workflow-simplification.md)
