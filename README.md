# Inversion Coach / Handstand App

Inversion Coach is an Android app for live inversion coaching, pose-aware recording, imported-video analysis, and replay.

It is built to help users practice handstand and inversion drills with:
- multi-user profile switching
- live coaching
- pose overlays
- uploaded video analysis
- reference-template creation from uploaded videos
- comparison scoring against saved references
- session history
- annotated replay and export
- calibration-aware feedback

## Why this project exists

Practicing inversions is hard to judge in real time. This app helps users review posture, stacking, movement quality, and drill performance through camera-based analysis and replay.

The goal is not just to record video, but to turn practice into structured feedback.

## What the app can do

The app currently supports two primary flows:

1. **Live coaching**
   - camera-based session
   - countdown before start
   - drill mode or freestyle mode
   - realtime overlays and cues
   - post-session replay and export

2. **Imported video analysis**
   - analyze an existing video offline
   - generate pose-aware metrics and replay context
   - optionally save the upload as a drill reference template
   - optionally compare the upload against a selected stored reference template
   - optionally export annotated output

Both flows persist session data locally and resolve replay from the best available source, preferring annotated output when valid and falling back to raw video when needed.

The app also supports a shared-device **multi-user profile workflow**:
- create, rename, switch, and archive user profiles
- keep one active user profile at a time
- maintain body-profile calibration history per user
- stamp each saved session with user/body profile attribution metadata

In practice, this means teams/coaches can share one device while keeping per-athlete calibration history, session ownership, and comparison context separated.

## Who this repo is for

This repo is useful for:
- users testing the Android app
- contributors improving coaching, overlays, export, or analysis
- AI-assisted builders who need a quick mental model of the system
- anyone trying to understand how the app works before changing code

## How to understand this repo

Start from the user journey, not the package list.

1. A user selects (or creates) an active user profile
2. The app resolves that profile’s latest calibration/body profile (or default model)
3. The user chooses a drill or freestyle mode
4. The app captures camera frames or imports a video
5. Pose frames are analyzed into alignment and movement signals
6. (Optional) The user saves an uploaded attempt as a reference template for that drill
7. (Optional) The user compares a current attempt against a previously saved reference template
8. Overlays, cues, and results are generated
9. Raw and annotated session outputs are saved for replay and history with profile attribution

## Core concepts

### Calibration

Calibration stores body-specific values so analysis can become more consistent across users and drills.

### Multi-user profile system

One active user profile is selected from a list of local profiles. Calibration and session attribution are scoped to that active profile so multiple people can use the same device without mixing body-profile history.

### Drills

Drills define movement expectations, tracking mode, and scoring logic.

### Live coaching

Live coaching handles countdown, recording, overlays, cues, and post-session finalization.

### Imported video analysis

Imported video analysis applies the same general analysis idea to previously recorded footage.

### Reference training and comparison

Uploaded attempts can become drill-scoped reference templates. Later attempts can be scored against those templates (or built-in templates) to highlight similarity, phase-level performance, and top movement differences.

### Replay and export

Sessions can be reviewed using annotated output when available, with raw video fallback when needed.

## Architecture at a glance

The app can be understood in five layers:

1. **UI layer**
   - Compose screens and interaction flow

2. **Orchestration layer**
   - session lifecycle
   - countdowns
   - async processing
   - export coordination

3. **Analysis layer**
   - pose processing
   - smoothing
   - readiness
   - drill and movement evaluation

4. **Overlay and feedback layer**
   - skeleton rendering
   - ideal line rendering
   - cue generation
   - replay formatting

5. **Persistence layer**
   - session metadata
   - blob storage
   - replay source resolution

## Repo guide

### Best place to start

- `README.md`
- `ARCHITECTURE.md`

### Product and system docs

- [Architecture guide](ARCHITECTURE.md)
- [System overview](docs/architecture/system-overview.md)
- [App modules](docs/architecture/app-modules.md)
- [Session lifecycle](docs/architecture/session-lifecycle.md)
- [Video pipeline](docs/architecture/video-pipeline.md)
- [Replay and fallback](docs/architecture/replay-and-fallback.md)
- [Calibration and scoring](docs/architecture/calibration-and-scoring.md)
- [Overlay rendering](docs/architecture/overlay-rendering.md)

### Feature guides

- [Calibration](docs/features/calibration.md)
- [Live coaching](docs/features/live-coaching.md)
- [Session history](docs/features/session-history.md)
- [Video import](docs/features/video-import.md)

### Diagrams

- [UI flow](docs/diagrams/ui-flow.md)
- [Class diagram](docs/diagrams/class-diagram.md)
- [Live session sequence](docs/diagrams/sequence-live-session.md)
- [Import analysis sequence](docs/diagrams/sequence-import-analysis.md)
- [Export finalization sequence](docs/diagrams/sequence-export-finalization.md)

### Architecture decisions

- [ADR-001 annotated export strategy](docs/decisions/adr-001-annotated-export-strategy.md)
- [ADR-002 replay fallback strategy](docs/decisions/adr-002-replay-fallback-strategy.md)
- [ADR-003 session duration source of truth](docs/decisions/adr-003-session-duration-source-of-truth.md)

## Current capabilities

- multi-user profile management (create, switch, rename, archive)
- active-user-scoped calibration/body profile versioning
- per-session user/body profile attribution for history and replay integrity
- live coaching sessions with countdown and drill/freestyle modes
- drill-specific reference-template creation from uploaded videos
- comparison scoring for current attempts against stored reference templates
- pose smoothing, correction, and issue detection
- overlay timeline capture for replay/export
- raw capture persistence and media verification
- annotated export pipeline with progress and failure state
- replay source resolution with fallback rules
- imported video analysis with stage-based UI
- session history and results review
- calibration/body profile support for analysis tuning

## Known limitations

- annotated export can take longer than raw replay readiness on lower-end devices
- playback correctness depends on metadata normalization and validation
- some fallback and export edge cases are still guarded by defensive diagnostics

## Running the project

### Prerequisites

- JDK 17
- Android SDK (`compileSdk 34`, `targetSdk 34`, `minSdk 28`)
- Gradle 8.14.x

### Quick start

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
java -version
gradle testDebugUnitTest
gradle :app:assembleDebug
```

## Testing

```bash
gradle testDebugUnitTest
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
