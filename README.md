# Inversion Coach (Android)

Inversion Coach is an Android app for calisthenics and posture coaching.

## Architecture (motion-aware)

```text
CAMERA FRAME
-> pose detection landmarks (ML Kit)
-> temporal smoothing (EMA + confidence weighting)
-> joint angle calculations
-> movement phase detection (FSM)
-> posture fault detection (persistence gated)
-> live cue generation (cooldown + priority)
-> session rep summary
```

Core code paths:
- Camera + ML inference: `pose/PoseAnalyzer.kt`
- Legacy smoothing + biomech scoring path: `pose/PoseSmoother.kt`, `biomechanics/*`
- New motion analysis pipeline: `motion/*`
- Live UI integration: `ui/live/*`
- Drill selection + preview animation: `ui/startdrill/*`, `ui/components/DrillPreviewAnimation.kt`

## Motion pipeline modules

New reusable modules under `app/src/main/java/com/inversioncoach/app/motion`:
- `PoseFrame`: timestamp + landmarks + per-landmark confidence
- `SmoothedPoseFrame`: filtered landmarks + per-joint velocity
- `AngleFrame`: named angles, trunk lean, pelvic tilt proxy, line deviation
- `MovementState`: phase, progress, confidence, start time, rep count
- `FaultEvent`: code, severity, message, side, start/end
- `TemporalPoseSmoother`: EMA smoothing with confidence weighting and missing-joint fallback
- `AngleEngine`: 2D angle calculations (extensible for future 3D)
- `MovementPhaseDetector`: FSM with hysteresis via dwell time
- `FaultDetectionEngine`: persistence-gated fault rules
- `FeedbackEngine`: one-cue-at-a-time, cooldown-based cueing
- `MotionAnalysisPipeline`: end-to-end orchestrator

## Drill metadata system

`DrillCatalog.kt` defines drill metadata using Kotlin data classes (JSON-like shape):
- id, displayName, category, level, equipment, movementPattern
- primaryJoints, trackedAngles, requiredLandmarks
- phaseModel, postureRules, cueLibrary
- thumbnail/animation refs
- repCountingEnabled, holdModeEnabled
- checkpoints and keyframe animation source

The catalog includes **15 drills** (including beginner drills).

## Drill preview animation system

- Previews are procedural 2D dummy animations.
- Source of truth: keyframes (`DrillPreviewKeyframe`) in `DrillCatalog.kt`.
- Runtime interpolation and rendering on Compose `Canvas` in `DrillPreviewAnimation.kt`.
- No external copyrighted videos are used.

## Drill selection UX

Start Drill screen now includes:
- looping preview animation per drill
- level tag + movement pattern tag
- tracked checkpoints summary
- details button that opens a drill detail screen with posture checklist

## Debug / tuning tools

- Live debug overlay now includes current phase, active fault, and rep count.
- Settings includes navigation to **Developer threshold tuning** screen.
- Developer screen supports live threshold tuning for key posture/fault limits.

## How to add a new drill

1. Add drill enum (`model/Models.kt`) if needed.
2. Add biomechanics config (`biomechanics/DrillModeConfig.kt`) and scoring weights.
3. Add metadata in `motion/DrillCatalog.kt`:
   - tags, checkpoints, required landmarks, keyframes.
4. Add/adjust phase thresholds (`MovementPhaseDetector`) and fault rules (`FaultDetectionEngine`).
5. If required, add dedicated analyzer mapping in `AlignmentMetricsEngine`.
6. Validate in live debug overlay and tune in Developer threshold screen.

## Threshold tuning guidance

- Start conservative for beginner drills (wider tolerance).
- Increase persistence frames first to reduce false positives.
- Then tighten angle/line thresholds incrementally.
- Use real session recordings and side-view consistency before hardening limits.

## 2D pose estimation limitations

- Perspective distortion can bias angle estimates.
- Occlusion and poor lighting reduce landmark confidence.
- Fast movement introduces motion blur and reduced stability.
- Pelvic tilt/rib-flare proxies are approximations from 2D landmarks.

## Tests

Unit tests were added for:
- angle calculation sanity (`AngleEngineTest`)
- movement phase FSM rep counting (`MovementPhaseDetectorTest`)

## Build

Use local Gradle installation if wrapper is missing:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
