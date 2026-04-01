# App Modules

## UI Packages

- `ui/home`, `ui/drills`, `ui/startdrill`, `ui/drilldetail`, `ui/live`, `ui/upload`, `ui/results`, `ui/history`, `ui/settings`
- `ui/components`, `ui/common`, and `ui/navigation` provide shared UI primitives and routes.

## Workflow and Flow Control

- `ui/live/LiveCoachingViewModel.kt` owns live session state transitions and finalization sequencing.
- Upload flow classes under `ui/upload` coordinate imported-video analysis lifecycle.
- Drill-management and drill-studio flows under `ui/drills` own drill editing, validation, and drill-linked navigation.

## Drill and Reference Domain

- `drills/*` covers drill definitions, drill registry/catalog behavior, and authoring support.
- `movementprofile/*` covers imported-video pose sourcing, movement extraction, and reference/template compatibility.
- Reference-template and baseline selection logic sits at the boundary between drill workflows, movement profiles, and persistence.

## Calibration and Analysis

- `calibration/*` covers calibration sessions, user body profiles, and readiness/profile application.
- `pose/*` handles frame validation, smoothing, and coordinate mapping.
- `motion/*` handles movement, phase, and quality analysis.
- `biomechanics/*` handles drill-specific scoring and issue classification.

## Media and Recording

- `camera/CameraSessionManager.kt` handles camera and recording session control.
- `recording/*` includes annotated export pipeline, normalization, overlay timeline, replay preparation, and media verification.
- `overlay/*` handles overlay render geometry and frame rendering support.

## Data and Storage

- `storage/repository/SessionRepository.kt` is the main persistence boundary.
- `storage/db/*` provides Room entities, converters, and DAOs.
- `storage/SessionBlobStorage.kt` persists media artifacts.
