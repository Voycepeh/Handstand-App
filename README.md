# Inversion Coach (Android)

Inversion Coach is a Kotlin/Jetpack Compose Android app with two primary video features:

1. **Live coaching overlay** (camera + real-time analysis)
2. **Upload-video overlay analysis** (offline analysis of imported clips)

Both flows persist sessions and converge on a shared **Results / Replay / Export fallback** experience.


## Local setup (build-accurate)

### Prerequisites

- **JDK 17** (required).
- Android SDK with:
  - `compileSdk 34`
  - `targetSdk 34`
  - `minSdk 28`
- Android Studio (recommended) or Gradle 8.14.x CLI.

### Quick start

1. Ensure Java 17 is active:
   ```bash
   export JAVA_HOME=/path/to/jdk-17
   export PATH="$JAVA_HOME/bin:$PATH"
   java -version
   ```
2. Run unit tests:
   ```bash
   gradle testDebugUnitTest
   ```
3. Build debug APK:
   ```bash
   gradle :app:assembleDebug
   ```

### Notes

- This repository does **not** include a checked-in `gradlew` wrapper script right now, so commands above use your locally installed `gradle`.
- For release signing, provide `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` Gradle properties.

---

## Current feature overview

### 1) Live coaching overlay flow (real-time)

- Entry points from **Home**:
  - **Start Live Coaching** (freestyle)
  - **Choose Drill** (drill-specific session)
- `LiveCoachingScreen` wires camera lifecycle to `LiveCoachingViewModel`.
- Pose frames are analyzed in real-time and rendered as overlay during the session.
- On stop, session finalization persists:
  - summary + metrics
  - frame metrics / issue timeline
  - raw recording status + URI
  - annotated export status and replay asset selection
- Replay resolution prefers annotated output when truly ready, otherwise raw fallback.

### 2) Upload-video overlay analysis flow (offline)

- Entry point from **Home**: **Upload Video**.
- `UploadVideoScreen` -> `UploadVideoViewModel` -> `DefaultUploadVideoAnalysisRunner`.
- Pipeline:
  1. import raw video blob
  2. run offline pose analysis (`UploadedVideoAnalyzer`)
  3. synthesize overlay timeline
  4. run annotated export pipeline
  5. verify and persist replay-selectable asset
- UI states are stage-based: preparing, analyzing, rendering, verifying, success/failure.
- If annotated export fails, flow still preserves truthful fallback to raw replay.

### 3) Replay / export / session flow

- **Results** screen uses replay selection helpers to choose the best readable asset.
- Export status is reflected from persisted session state (`AnnotatedExportStatus`, raw persist state).
- History and Progress route back to Results for replay and session review.

---

## Real architecture (implementation-aligned)

### App modules used today

- **Navigation/UI**: `ui/navigation/Nav.kt`, screen packages under `ui/`
- **Live session pipeline**:
  - `ui/live/LiveCoachingScreen.kt`
  - `ui/live/LiveCoachingViewModel.kt`
  - `camera/CameraSessionManager.kt`
  - `pose/PoseAnalyzer.kt`, `pose/PoseSmoother.kt`
  - `motion/*`, `biomechanics/*`, `coaching/*`
- **Upload pipeline**:
  - `ui/upload/UploadVideoFlow.kt`
  - `movementprofile/UploadedVideoAnalyzer.kt`
  - `movementprofile/MlKitVideoPoseFrameSource.kt`
  - `recording/AnnotatedExportPipeline.kt`
- **Persistence & media**:
  - `storage/repository/SessionRepository.kt`
  - Room entities/DAOs under `storage/db/*`
  - blob/media helpers (`SessionBlobStorage`, recording helpers)
- **Replay/export state interpretation**:
  - `ui/live/SessionAnalysisSupport.kt`
  - `ui/results/ResultsScreen.kt`

### Boundary separation

- **Live coaching** is driven by camera-time pose callbacks and live state machine in `LiveCoachingViewModel`.
- **Upload analysis** is driven by `UploadVideoViewModel` + `DefaultUploadVideoAnalysisRunner` and does not depend on live camera session state.
- Shared pieces (repository, export pipeline, replay selectors) are reused where they are genuinely cross-flow.

---

## UI/user flowchart (current)

```mermaid
flowchart TD
    H[Home]

    H -->|Start Live Coaching| L[LiveCoachingScreen Freestyle]
    H -->|Choose Drill| S[StartDrillScreen]
    S -->|Start| L2[LiveCoachingScreen Drill]

    H -->|Upload Video| U[UploadVideoScreen]

    L -->|Stop| R[Results]
    L2 -->|Stop| R

    U -->|Choose Video| U2[Upload processing stages]
    U2 -->|Success| R
    U2 -->|Failure| U

    H --> HI[History]
    H --> P[Progress]
    HI -->|Open Session| R
    P -->|Open Session| R

    R -->|Done| H
```

---

## UML diagrams (current code)

### Class diagram (core services/viewmodels/pipelines)

```mermaid
classDiagram
    class AppNavHost {
      +AppNavHost()
      +Route.Home
      +Route.Live
      +Route.UploadVideo
      +Route.Results
    }

    class HomeScreen {
      +onStart()
      +onStartFreestyle()
      +onUploadVideo()
    }

    class LiveCoachingScreen {
      +drillType: DrillType
      +options: LiveSessionOptions
      +onStop(result)
    }

    class LiveCoachingViewModel {
      -sessionId: Long?
      -rawVideoUri: String?
      -annotatedVideoUri: String?
      -rawPersistStatus: RawPersistStatus
      -annotatedExportStatus: AnnotatedExportStatus
      +onPoseFrame(frame, settings)
      +onRecordingFinalized(uri)
      +stopSession(callback)
    }

    class UploadVideoScreen {
      +onBack()
      +onOpenResults(sessionId)
    }

    class UploadVideoViewModel {
      -state: UploadVideoUiState
      +onPickStarted()
      +analyze(uri)
      +cancel()
      +onInvalidSelection(message)
    }

    class DefaultUploadVideoAnalysisRunner {
      -preset: ExportPreset
      +run(uri, onProgress): UploadFlowResult
    }

    class AnnotatedExportPipeline {
      +export(sessionId, rawVideoUri, drillType, drillCameraSide, overlayTimeline, preset, onRenderProgress)
    }

    class SessionRepository {
      +saveSession(record)
      +saveRawVideoBlob(sessionId, sourceUri)
      +saveAnnotatedVideoBlob(sessionId, sourceUri)
      +updateAnnotatedExportStatus(sessionId, status)
      +updateMediaPipelineState(sessionId, block)
      +saveOverlayTimeline(sessionId, timelineJson)
      +observeSession(sessionId)
    }

    class ResultsScreen {
      +ResultsScreen(sessionId, onDone)
    }

    AppNavHost --> HomeScreen
    AppNavHost --> LiveCoachingScreen
    AppNavHost --> UploadVideoScreen
    AppNavHost --> ResultsScreen

    LiveCoachingScreen --> LiveCoachingViewModel
    UploadVideoScreen --> UploadVideoViewModel
    UploadVideoViewModel --> DefaultUploadVideoAnalysisRunner

    LiveCoachingViewModel --> SessionRepository
    DefaultUploadVideoAnalysisRunner --> SessionRepository
    DefaultUploadVideoAnalysisRunner --> AnnotatedExportPipeline
    ResultsScreen --> SessionRepository
```

### Sequence diagram: live overlay flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Home as HomeScreen
    participant Live as LiveCoachingScreen
    participant VM as LiveCoachingViewModel
    participant Cam as CameraSessionManager
    participant Analyzer as PoseAnalyzer
    participant Repo as SessionRepository

    User->>Home: Start Live Coaching / Choose Drill
    Home->>Live: navigate(Route.Live)
    Live->>Cam: start camera + analyzer
    Cam-->>Analyzer: frame stream
    Analyzer-->>VM: onPoseFrame(PoseFrame)
    VM-->>Live: updated overlay/cue UI state

    User->>Live: Stop session
    Live->>VM: stopSession()
    VM->>Repo: persist session + frame metrics + export states
    Repo-->>VM: saved session state
    VM-->>Live: SessionStopResult(sessionId)
    Live->>Home: navigate Results(sessionId)
```

### Sequence diagram: upload-video overlay flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant U as UploadVideoScreen
    participant VM as UploadVideoViewModel
    participant Runner as DefaultUploadVideoAnalysisRunner
    participant Analyzer as UploadedVideoAnalyzer
    participant Export as AnnotatedExportPipeline
    participant Repo as SessionRepository

    User->>U: Choose Video
    U->>VM: analyze(uri)
    VM->>Runner: run(uri, onProgress)

    Runner->>Repo: saveSession(UPLOADED_VIDEO)
    Runner->>Repo: saveRawVideoBlob + raw status
    Runner->>Analyzer: analyze(rawUri)
    Analyzer-->>Runner: overlay timeline + metrics
    Runner->>Export: export(overlayTimeline)
    Export-->>Runner: annotated uri or failure reason
    Runner->>Repo: saveOverlayTimeline + updateMediaPipelineState
    Runner-->>VM: UploadFlowResult
    VM-->>U: success/failure UI + Open Results
```

### Sequence diagram: replay/export finalization

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant R as ResultsScreen
    participant Repo as SessionRepository
    participant Replay as SessionAnalysisSupport

    User->>R: Open session results
    R->>Repo: observeSession(sessionId)
    Repo-->>R: SessionRecord
    R->>Replay: selectReplayAsset(session)
    Replay-->>R: Annotated replay / Raw replay / Unavailable
    R-->>User: Replay + export status + share/delete actions
```

---

## Notes on removed/outdated assumptions

- Upload analysis now reports truthful success when annotated export is unavailable and falls back to raw replay.
- Replay selection is status + asset readability based (not hardcoded success messaging).
- Live flow keeps camera-time overlay path as source-of-truth for live coaching, with persisted replay path resolved at finalization.

---

## Repository structure

- `app/src/main/java/com/inversioncoach/app/`
  - `ui/`: Compose screens + navigation + presentation helpers
  - `camera/`: CameraX session manager
  - `pose/`: pose frame extraction and smoothing
  - `motion/`: movement phase/quality engines
  - `biomechanics/`: drill-specific metrics and scoring
  - `movementprofile/`: upload/offline analysis profile + analyzer pieces
  - `recording/`: overlay timeline and annotated export pipeline
  - `storage/`: repository, Room DB/DAOs, blob storage
- `app/src/test/`: JVM unit tests
- `docs/`: design notes and migration docs

## Build/test locally

> This repository currently uses local `gradle` CLI (no `gradlew` wrapper checked in).

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```
