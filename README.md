# CaliVision

Calisthenics motion analysis

CaliVision is a drill-centric calisthenics and handstand training app focused on connected workflows for live coaching, upload analysis, drill authoring, review, and calibration-aware feedback.

## What the app is

CaliVision is organized around drills, not isolated recording tools. Each workflow is meant to carry drill context through capture, analysis, review, and iteration:

- Start practice from **Home / Drill Hub**.
- Author and refine drills in **Manage Drills** and **Drill Studio**.
- Run **Live Session** coaching with countdown gating and real-time overlays.
- Use **Upload / Reference Training** for offline analysis and reference-template work.
- Review outcomes in **Results / Session History**.
- Apply shared **Calibration / Profiles** context across analysis flows.

## Current user journeys

1. **Drill Hub → Start Live Session**
   Select a drill, resolve the effective session view, complete countdown, coach live, finalize, then review results/replay.
2. **Manage Drills → Drill Studio**
   Create or edit drills with a single, clear save path and reliable persisted reload behavior.
3. **Upload / Reference Training**
   Analyze imported attempts, optionally create or update drill-linked references, and compare future attempts.
4. **Results / Session History**
   Review scored sessions, replay media, and revisit drill-linked context from prior practice.
5. **Calibration / Profiles**
   Manage active profile and calibration data that influences both live and imported analysis.

See full flow detail in [docs/features/current-user-flows.md](docs/features/current-user-flows.md).

## Tech stack and how it plays together

- **Kotlin** powers the core app logic and workflow orchestration.
- **Jetpack Compose** renders the drill-centric screens and keeps UI state aligned with each coaching step.
- **Room + local storage** persist drills, sessions, profile/calibration state, references, and analysis outputs for reliable review and continuity.
- **ML Kit pose detection** extracts body landmarks from live camera sessions and uploaded video.
- **Recording/media pipelines** manage raw capture, annotated replay/export, and fallback playback behavior.
- **Calibration and movement-analysis modules** interpret landmarks in drill context for drill-specific scoring, comparison, and feedback.

In practice, the user starts from a drill, Compose screens collect workflow state, camera or upload flows produce media frames, pose detection extracts landmarks, analysis/calibration layers interpret movement for the active drill, persistence stores sessions/results/profiles, and replay/export surfaces outcomes back to the user.

## Machine learning and analysis scope

Under the hood, CaliVision already uses on-device machine-learning-powered pose detection to extract landmarks from live camera sessions and uploaded videos. Those landmarks flow through our own motion-analysis, biomechanics, calibration, and drill-scoring layers to produce structured feedback, replay overlays, and comparison results. Current seeded drill baselines (including v1 JSON/template inputs) are based on our own analysis of target movement patterns. The reference-template and movement-profile workflow is being structured to support a more adaptive learning path over time as more drill data is captured, without claiming a fully self-learning end-to-end model today.

## Core concepts

- **Drill-centric context**: workflows should start from and return to drill context.
- **Deterministic session lifecycle**: countdown gating, start, live analysis loop, finalize, persistence.
- **Annotated-first replay with fallback**: prefer verified annotated replay; fall back to raw capture when needed.
- **Cross-workflow calibration**: active profile informs analysis in live sessions and upload analysis.
- **Practical simplification**: one clear save path and fewer redundant actions.

## Repo structure / documentation map

- `app/src/main/java/com/inversioncoach/app/ui` - screens, navigation, and workflow-state coordination.
- `app/src/main/java/com/inversioncoach/app/drills` - drill definitions and drill-authoring support.
- `app/src/main/java/com/inversioncoach/app/calibration` - calibration sessions and profile handling.
- `app/src/main/java/com/inversioncoach/app/movementprofile` - upload/reference extraction and comparison data.
- `app/src/main/java/com/inversioncoach/app/recording` - recording, export, and replay preparation.
- `app/src/main/java/com/inversioncoach/app/storage` - Room repositories and media/blob persistence.

Docs entry points:

- Architecture: [ARCHITECTURE.md](ARCHITECTURE.md)
- Architecture details: [`docs/architecture/`](docs/architecture)
- Feature docs: [`docs/features/`](docs/features)
- Diagrams: [`docs/diagrams/`](docs/diagrams)
- Decisions/ADRs: [`docs/decisions/`](docs/decisions)

## Running locally

Prerequisites:

- JDK 17
- Android SDK (`compileSdk 34`, `targetSdk 34`, `minSdk 28`)
- Gradle 8.14.x on PATH (wrapper is not checked in)

Quick start:

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
gradle testDebugUnitTest
gradle :app:assembleDebug
```

## Contribution expectations

- Target PRs to `main`.
- If workflow/UI behavior changes, update matching docs and diagrams in the same PR.
- Prefer simple, deterministic UX over branching/overlapping controls.
- Keep terminology aligned with current flows: Drill Hub, Manage Drills, Drill Studio, Upload / Reference Training, Results / Session History, Calibration / Profiles.

See [CONTRIBUTING.md](CONTRIBUTING.md) for full guidance.
