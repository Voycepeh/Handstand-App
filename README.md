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

## Procedural 2D skeleton animation engine

The preview system is now a code-driven 2D skeleton renderer using Compose Canvas:
- Source of truth: `SkeletonAnimationSpec` + `SkeletonKeyframe` in `motion/DrillCatalog.kt`
- Shared joints: `BodyJoint` (`head`, `neck`, shoulders, elbows, wrists, ribcage, pelvis, hips, knees, ankles)
- Shared bones: `SkeletonRig.bones`
- Runtime interpolation: `SkeletonAnimationEngine.interpolate(...)`
- Easing support: `LINEAR`, `EASE_IN_OUT`
- Looping target: ~12–18 fps visual smoothness (`fpsHint` defaults to 15)
- Mirroring support for asymmetrical drills (for example reverse lunges)

### Why code-driven animation over stored video files

- Much smaller app footprint than storing multiple video files
- Consistent visual language across drills
- Editable in code and easy to tune keyframes
- Reusable skeleton and naming conventions for future pose-overlay matching
- Supports dynamic highlighting/theming paths later without asset regeneration

## Drill schema

`DrillDefinition` includes:
- `id`, `displayName`, `category`, `level`, `equipment`
- `movementPattern`
- `requiredLandmarks`
- `mainPhases`
- `commonFaults`
- `cues`
- `repMode` (`REP_BASED` or `HOLD_BASED`)
- `previewAnimationId`
- `animationSpec`
- `postureRulePlaceholders`

Rule placeholder philosophy (v1):
- Rep drills: phase thresholds + persistence + symmetry/tempo guards
- Hold drills: line deviation + fault timers + quality score

## Seeded drill catalog

Wave 1 (implemented):
- Wall Push-Up
- Push-Up
- Squat
- Reverse Lunge
- Plank
- Glute Bridge
- Pull-Up
- Dip
- Hanging Knee Raise
- Pike Push-Up
- Hollow Hold
- Wall-Facing Handstand
- L-Sit

Wave 2 (scaffolded with TODO slots):
- Archer Push-Up
- Archer Pull-Up
- (planned next) pistol squat, hanging leg raise, wall walk, freestanding handstand, muscle-up prep, full bridge

Exercise descriptions/cues in-app are normalized summaries derived from public calisthenics technique references and written originally for the app (not copied verbatim).

## How to add a new drill

1. Add/confirm the app drill enum (`model/Models.kt`) for runtime routing.
2. Add drill metadata in `motion/DrillCatalog.kt` using `def(...)`.
3. Add an animation spec (`symmetricSpec`, `lungeSpec`, or a new custom spec).
4. Define phases, faults, cues, movement pattern, and rep mode.
5. Add future rule placeholders for expected analysis strategy.
6. Wire analyzer thresholds in the motion pipeline when posture detection is added for that drill.

## Defining joints and keyframes

- Use normalized coordinates (`0f..1f`) in `NormalizedPoint`
- Include key poses when applicable:
  - neutral
  - start
  - mid eccentric
  - bottom
  - mid concentric
  - top
  - optional hold
- Keep first/last poses compatible for smooth loops

## Mirroring rules

- Enable `mirroredSupported = true` on `SkeletonAnimationSpec`
- Renderer can request mirrored playback
- Engine swaps left/right joint keys and flips x-axis (`x -> 1 - x`)

## Tests

Unit tests cover:
- keyframe interpolation
- mirroring
- loop continuity
- animation loading
- drill schema field integrity

## Build

Use local Gradle installation if wrapper is missing:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
