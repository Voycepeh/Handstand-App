# Inversion Coach / Handstand App

Inversion Coach is an Android app for handstand and inversion coaching with drill-based practice, live feedback, uploaded video analysis, reference comparison, calibration-aware feedback, and session review.

## What the App Does

The app is built around a few connected user journeys instead of isolated recording screens:

- **Drill-based live practice** with countdown, realtime overlays, cues, and post-session replay.
- **Uploaded video analysis** for offline review, export, and comparison.
- **Drill authoring and editing** through Drill Studio and Manage Drills.
- **Reference training workflows** to turn strong examples into reusable comparison baselines.
- **Calibration and profile management** so analysis can adapt to the active body profile.

Both live and imported flows persist session truth locally and resolve replay from the best verified source, preferring annotated output when valid and falling back to raw capture when needed.

## Current Product Flows

### 1. Practice a drill live
Home / Drill Hub → choose drill → configure session → countdown → live coaching → stop → results / replay / history.

### 2. Review an uploaded attempt
Home / Drill Hub → upload video → analyze clip → review replay and feedback → optionally save as history or compare with reference.

### 3. Manage or author drills
Manage Drills → open drill → edit in Drill Studio → save validated drill definition → return to drill list.

### 4. Build reference-based training
Upload or review a strong session → associate to drill → create or update reference template / baseline → compare future attempts.

### 5. Calibrate and manage profiles
Calibration / Profiles → create or select active body profile → use calibrated analysis across drill and session flows.

## Current Capabilities

- Live coaching sessions with countdown and drill-aware session options.
- Pose smoothing, correction, issue detection, and overlay rendering in-session.
- Overlay timeline capture for annotated replay/export.
- Raw capture persistence and media verification.
- Annotated export pipeline with explicit progress and failure state.
- Replay source resolution with deterministic fallback rules.
- Uploaded video analysis with stage-based processing.
- Drill authoring, editing, and management flows.
- Reference training and baseline-oriented comparison workflows.
- Calibration/body profile support for analysis tuning.
- Session history and results review.

## Repo Structure

- `app/src/main/java/com/inversioncoach/app/ui` - Compose screens, route wiring, and UI state.
- `app/src/main/java/com/inversioncoach/app/drills` - drill definitions, registry, studio support, and drill-related workflow logic.
- `app/src/main/java/com/inversioncoach/app/calibration` - calibration sessions, body profiles, readiness, and profile usage.
- `app/src/main/java/com/inversioncoach/app/movementprofile` - imported video pose sourcing and movement/reference extraction.
- `app/src/main/java/com/inversioncoach/app/recording` - recording, export, normalization, validation, and replay preparation.
- `app/src/main/java/com/inversioncoach/app/storage` - Room entities, DAOs, repositories, and blob storage.
- `docs/architecture` - system and subsystem documentation.
- `docs/diagrams` - Mermaid diagrams for user flows and architecture sequences.
- `docs/features` - feature-facing behavior notes.
- `docs/decisions` - architecture and product decision records.

For deeper technical documentation, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Documentation Map

- [Architecture guide](ARCHITECTURE.md)
- [System overview](docs/architecture/system-overview.md)
- [App modules](docs/architecture/app-modules.md)
- [Session lifecycle](docs/architecture/session-lifecycle.md)
- [Video pipeline](docs/architecture/video-pipeline.md)
- [Replay and fallback](docs/architecture/replay-and-fallback.md)
- [Calibration and scoring](docs/architecture/calibration-and-scoring.md)
- [Current user flows](docs/features/current-user-flows.md)
- [Workflow simplification decision](docs/decisions/adr-004-product-workflow-simplification.md)
- [UI flow diagram](docs/diagrams/ui-flow.md)

## Known Limitations

- Annotated export latency can exceed raw replay readiness on lower-end devices.
- Final playback correctness still depends on metadata normalization and validation.
- Some export and replay edge cases are intentionally guarded by defensive diagnostics.

## Running the Project

### Prerequisites

- JDK 17
- Android SDK (`compileSdk 34`, `targetSdk 34`, `minSdk 28`)
- Gradle 8.14.x available locally (wrapper script is not checked in)

### Quick Start

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
java -version
gradle testDebugUnitTest
gradle :app:assembleDebug
```

## Testing

Primary unit test command:

```bash
gradle testDebugUnitTest
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
