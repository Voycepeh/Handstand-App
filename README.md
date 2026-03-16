# Inversion Coach (Android)

Inversion Coach is a Kotlin/Jetpack Compose Android app for handstand-oriented live coaching. It runs on-device pose detection, derives movement/quality signals, provides timed coaching cues, and stores sessions for history/progress review.

## High-level: what this app is and how people use it

### What this app is about

Inversion Coach helps athletes improve inversion technique (handstands and handstand push-up variations) with **real-time feedback** from on-device pose tracking. The app focuses on practical coaching: detect movement quality issues early, surface simple actionable cues, and summarize how the session went.

### How users should use it

1. Pick a drill (or freestyle mode) from Home.
2. Position camera so full body is visible.
3. Run the set while the app tracks pose and gives live cues.
4. Stop the session and review scores/issues in Results.
5. Use History/Progress to track changes over time.

### Main solution flow (high level)

```text
Select drill -> Start live session -> Camera + pose tracking ->
Motion/quality analysis -> Coaching cue output -> Session persistence ->
Results + History + Progress review
```

## Project overview

### What the app does today

- Runs real-time camera + pose analysis for inversion drills.
- Supports freestyle and drill-guided flows (holds and rep-based patterns).
- Surfaces live guidance (visual overlays, cue text, optional voice cues).
- Records and stores session outputs (session metrics, optional videos, issue timelines).
- Provides history, results, and progress screens backed by local persistence.

### Main user flow

1. User opens **Home** and starts a drill or freestyle session.
2. **Live Coaching** initializes camera + analyzer pipeline.
3. Pose frames are smoothed and passed through motion/biomechanics scoring.
4. Cue engine emits actionable coaching prompts.
5. User stops session; app persists summary + media and opens **Results**.
6. Session can later be reviewed via **History** and **Progress**.

### UI navigation flow

```mermaid
flowchart TD
    H[Home]
    S[Start Drill]
    D[Drill Detail]
    L[Live Coaching]
    R[Results]
    HI[History]
    P[Progress]
    SE[Settings]

    H -->|Choose Drill| S
    H -->|Review Sessions| HI
    H -->|Progress| P
    H -->|Settings| SE

    S -->|Details| D
    S -->|Start| L
    S -->|Back| H

    D -->|Back| S

    L -->|Stop session| R
    R -->|Done| H

    HI -->|Select session| R
    HI -->|Back| H

    P -->|Back| H
    SE -->|Back| H
```

## Current architecture

### Runtime layers

- **App entry + navigation**
  - `MainActivity` hosts Compose app navigation via `AppNavHost`.
- **UI layer (Compose)**
  - Screens under `ui/` drive user flows (`home`, `startdrill`, `live`, `results`, `history`, `progress`, `settings`).
- **Capture + pose layer**
  - `CameraSessionManager` binds CameraX preview/video/analyzer.
  - `PoseAnalyzer` converts camera frames to normalized `PoseFrame`.
- **Analysis/domain layer**
  - `MotionAnalysisPipeline` + `AlignmentMetricsEngine` produce motion phases, quality metrics, and drill scoring.
  - `CueEngine` generates coaching cue timing/text.
  - `SummaryGenerator` composes result summaries/recommendations.
- **Data layer**
  - `SessionRepository` coordinates Room DAOs + blob storage (`SessionBlobStorage`).
  - `InversionCoachDatabase` stores sessions/settings/frame metrics.

### Data movement (high level)

`CameraX frame -> PoseAnalyzer -> LiveCoachingViewModel -> Motion/Biomechanics/Cue engines -> UI state + repository persistence -> Results/History/Progress screens`

## Architecture

### Class diagram (UML / Mermaid)

```mermaid
classDiagram
    class MainActivity
    class AppNavHost
    class LiveCoachingScreen
    class LiveCoachingViewModel
    class CameraSessionManager
    class PoseAnalyzer
    class PoseSmoother
    class MotionAnalysisPipeline
    class AlignmentMetricsEngine
    class CueEngine
    class SummaryGenerator
    class SessionRepository
    class SessionBlobStorage
    class InversionCoachDatabase

    MainActivity --> AppNavHost : hosts
    AppNavHost --> LiveCoachingScreen : navigates to
    LiveCoachingScreen --> LiveCoachingViewModel : creates/uses
    LiveCoachingScreen --> CameraSessionManager : binds camera
    LiveCoachingScreen --> PoseAnalyzer : analyzer callback

    LiveCoachingViewModel --> PoseSmoother : smooth()
    LiveCoachingViewModel --> MotionAnalysisPipeline : analyze()
    LiveCoachingViewModel --> AlignmentMetricsEngine : score()
    LiveCoachingViewModel --> CueEngine : nextCue()
    LiveCoachingViewModel --> SummaryGenerator : generate()
    LiveCoachingViewModel --> SessionRepository : persist/observe

    SessionRepository --> InversionCoachDatabase : Room DAOs
    SessionRepository --> SessionBlobStorage : videos/notes blobs
```

### Sequence diagram (UML / Mermaid)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Live as LiveCoachingScreen
    participant Cam as CameraSessionManager
    participant Analyzer as PoseAnalyzer
    participant VM as LiveCoachingViewModel
    participant Motion as MotionAnalysisPipeline
    participant Align as AlignmentMetricsEngine
    participant Cue as CueEngine
    participant Repo as SessionRepository

    User->>Live: Start drill session
    Live->>Cam: bind(preview, analyzer)
    Cam-->>Analyzer: camera frames
    Analyzer-->>VM: onPoseFrame(PoseFrame)
    VM->>VM: smooth frame + update validity gates
    VM->>Motion: process pose/motion state
    VM->>Align: score alignment + issues
    VM->>Cue: choose next coaching cue
    Cue-->>VM: CoachingCue?
    VM-->>Live: updated UI state / cue text / overlay data
    User->>Live: Stop session
    Live->>VM: stopSession()
    VM->>Repo: save session, metrics, media URIs
    Repo-->>VM: sessionId
    VM-->>Live: SessionStopResult
```


## Media finalization architecture (storage-efficient)

- **Live performance first**
  - CameraX records at high quality for downstream export quality.
  - Live pose analysis runs on a resized analysis stream to protect FPS and overlay responsiveness.
  - Overlay rendering stays lightweight and independent from finalize-time compression.
  - No compression/transcoding is performed while recording is active.

- **Ordered finalization pipeline**
  1. Persist `raw_master` first.
  2. Export `annotated_master` from `raw_master`.
  3. Compress `annotated_master` -> `annotated_final`.
  4. If annotated path fails or times out, compress `raw_master` -> `raw_final` fallback.
  5. Verify output (exists, non-zero size, readable duration) after each stage before promoting status.

- **Successor-based cleanup**
  - A source file is never deleted until its successor has been successfully created and verified.
  - After verified `annotated_final`, delete `annotated_master` and `raw_master` (normal mode).
  - After verified `raw_final` fallback, delete `raw_master` (normal mode).
  - Cleanup failures are persisted but do not block selecting a playable asset.

- **Retention policy**
  - Default: retain only compressed playback assets.
  - Preferred retained asset: `annotated_final`.
  - Fallback retained asset: `raw_final`.
  - Master/intermediate assets are retained only in debug mode.

- **Playback selection**
  - History/results resolve replay in this order: `annotated_final` -> `raw_final` -> legacy/raw fallback.
  - `bestPlayableUri` points to the best verified retained asset.

### Developer note

The app analyzes resized frames live to keep real-time coaching smooth (stable FPS, low latency cues, responsive overlays), but exports from high-quality intermediate recordings to preserve replay fidelity and annotation quality for post-session review.

### Upload annotation pipeline (offline, CPU-parallel)

Uploaded video annotation now uses a staged producer-consumer pipeline optimized for modern phone CPUs while keeping encoded output ordered and deterministic:

1. **Decode stage (sequential):** sample frames in presentation order from the raw uploaded video source-of-truth URI.
2. **Analysis stage (parallel):** run bounded worker-pool pose inference/metric extraction on sampled frames (`workerCount = min(4, cores - 2)`, configurable and clamped).
3. **Overlay synthesis stage:** persist lightweight timeline points keyed by timestamp/frame index.
4. **Render/encode stage (sequential):** render every output frame in order and encode sequentially to MP4.
5. **Verification stage:** block READY until URI/file/size/metadata/playability checks pass.

Additional behavior:
- **Decoupled cadence:** output renders at 24/30 FPS while analysis runs at lower cadence (10–20 FPS), with interpolation/nearest-pose reuse for smooth overlays.
- **Bounded memory:** no full-history annotated frame buffering; bounded channels and short-lived bitmaps only.
- **Export presets:**
  - `FAST` = 720p / 24 FPS output / 10 FPS analysis
  - `BALANCED` = 720p / 30 FPS output / 15 FPS analysis
  - `HIGH` = 1080p / 30 FPS output / 18 FPS analysis
- **Structured telemetry:** decode/analyze/render/encode/verify/total timings plus queue backlog and worker utilization snapshots for offline profiling.
- **Stage-aware progress UI:** Preparing video → Analyzing movement → Rendering annotated video → Verifying output → Completed/Failed with percentage + optional ETA.


## Movement-profile foundation (new in this PR)

The app now includes a **movement-profile domain layer** that decouples analysis contracts from hard-coded drill branches:

- `MovementProfile` + rule primitives (`ReadinessRule`, `AlignmentRule`, `HoldRule`, `RepRule`, `PhaseDefinition`)
- legacy drill compatibility via `ExistingDrillToProfileAdapter` and `LegacyDrillExecutionBridge`
- calibration versioning foundation via `CalibrationProfile`
- upload-first offline analysis pipeline (`UploadedVideoAnalysisCoordinator` / `UploadedVideoAnalyzer` / `UploadedAnalysisRepository`)
- draft `MovementTemplateCandidate` generation from uploaded timeline analysis

This is an incremental foundation: existing drill flows remain in place while freestyle/generic paths can consume profile-driven services.

See `docs/architecture/movement-profile-architecture.md` for details.

## Repository structure

- `app/src/main/java/com/inversioncoach/app/`
  - `ui/`: Compose screens, navigation, and presentation helpers.
  - `camera/`: CameraX session binding/orchestration.
  - `pose/`: ML Kit pose analysis and frame shaping.
  - `motion/`: phase detection, quality/fault evaluation, drill catalog.
  - `biomechanics/`: drill scoring/threshold engines and analyzer support.
  - `coaching/`: cue + voice coaching logic.
  - `storage/`: repository, Room database/DAOs, blob storage.
  - `recording/`: recording and annotated export pipeline.
- `app/src/main/res/`: Android resources (icons, strings, drawables, XML configs).
- `app/src/test/`: JVM unit tests for motion/overlay/logic subsystems.
- `docs/`: supplementary design and migration notes.
- `scripts/`: utility scripts (e.g., release APK download helper).

## Setup and build

### Prerequisites

- Android Studio (current stable) or local Gradle CLI.
- Android SDK 34 installed.
- JDK 17.
- Android device/emulator API 28+ with camera support for live coaching flows.

> Note: this repo currently does **not** include `gradlew`; use a local `gradle` install.

### Build locally (CLI)

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

### Install debug APK to device

```bash
gradle :app:installDebug
```

Then launch **Inversion Coach** and grant camera permission.

### Release build

Release signing is read from Gradle properties (`~/.gradle/gradle.properties` or `-P` flags):

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
- optional: `APP_VERSION_CODE`, `APP_VERSION_NAME`

If release signing props are missing, local release builds fall back to debug keystore signing.

```bash
gradle :app:assembleRelease
```

### Install from GitHub Releases (if using published APKs)

1. Download latest APK asset from Releases.
2. Allow installs from unknown sources for your installer app.
3. Install APK and launch app.
4. Grant camera permission when prompted.
