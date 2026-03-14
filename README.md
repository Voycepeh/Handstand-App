# Inversion Coach (Android, Kotlin, Compose)

Inversion Coach is an on-device real-time posture/mechanics coach for inversion drills, optimized around side-view wall-supported handstand and pike progressions.

## MVP highlights
- Single-activity app with Compose + MVVM/UDF architecture
- Side-view-first drill analysis profiles (each drill has its own metric/fault/cue priorities)
- CameraX integration points for preview + analysis + recording
- MediaPipe Pose Landmarker integration scaffolding
- Deterministic rule-based cue engine with issue persistence and cooldowns
- Debug overlay toggle for raw metrics, angles, and confidence
- Room persistence for sessions, settings, per-frame metrics, and issue timeline events
- WorkManager hook for background retention/cleanup tasks
- Local summary + recommendation generation (no cloud dependency)

## Project structure
- `camera/` camera session orchestration
- `pose/` pose landmarker and smoothing
- `overlay/` live skeletal + ideal line overlays
- `biomechanics/` drill profiles, metrics, scoring, fault classification
- `coaching/` cue prioritization + voice coach
- `recording/` MediaStore recording
- `storage/` Room DB + repository + service locator
- `summary/` local summary and recommendation engines
- `history/` background worker and history-related infra
- `ui/` Compose screens, components, navigation
- `model/` shared domain models

## Setup
1. Open in Android Studio (Ladybug+ recommended).
2. Let Gradle sync.
3. Add `pose_landmarker_lite.task` under `app/src/main/assets/`.
4. Run on a Pixel device (Pixel 10 target profile).
5. Grant camera permission.

## Notes on current MVP implementation
- Analysis, UI, and persistence flows are deterministic and local-first.
- Per-frame metrics and issue events are stored for replay/timeline analysis.
- `PoseAnalyzer.analyze()` still requires concrete `ImageProxy -> MPImage` conversion and async detect invocation for full live MediaPipe inference.
- Annotated video export pipeline is scaffolded; raw MediaStore recording path is implemented.

## Next steps
- Complete MediaPipe live stream frame conversion and timestamped `detectAsync` loop.
- Connect stored per-frame metrics into replay timeline and frame thumbnails.
- Persist complete session lifecycle from `LiveCoachingViewModel` to `SessionRecord` on stop.
- Add freestanding handstand as a separate drill mode/profile in a future release.
