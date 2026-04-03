# System Overview

CaliVision is a drill-centric coaching system. The product is shaped around practical coaching journeys that share common analysis, media, and persistence infrastructure.

## Workflow surfaces

- **Home / Drill Hub**: primary entry for starting practice and navigating core actions.
- **Manage Drills**: drill catalog management and drill selection context.
- **Drill Studio**: drill authoring/editing with deterministic save behavior.
- **Live Session**: countdown-gated live coaching and scoring loop.
- **Upload / Reference Training**: imported clip analysis, reference creation, and comparison setup.
- **Results / Session History**: persisted session outcomes, replay, and drill-linked review.
- **Calibration / Profiles**: active body profile context applied across coaching workflows.

## Runtime subsystems

- **UI + navigation**: route state and screen coordination.
- **Workflow orchestrators**: live and upload coordinators own lifecycle transitions.
- **Drill/reference domain**: drill definitions, drill-linked references/templates, comparison inputs.
- **Analysis engines**: pose, motion, biomechanics, issue detection, score rollups.
- **Media pipelines**: recording, timeline capture, export, validation, replay selection.
- **Persistence**: session, drill, calibration, and media state in Room + blob storage.

## System invariants

1. Drill context should stay recoverable through live, upload, history, and comparison flows.
2. Countdown and start gating should prevent premature “started” state.
3. Session truth must persist even when annotated export fails.
4. Replay selection should prefer verified annotated output, then fall back to verified raw media.
5. Calibration/profile context should resolve consistently across live and imported analysis.
6. Finalization and export boundaries should remain diagnostics-friendly and failure-tolerant.
