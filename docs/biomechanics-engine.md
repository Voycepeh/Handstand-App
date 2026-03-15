# Biomechanics Engine

## Current analysis flow

1. `LiveCoachingViewModel.onPoseFrame` smooths the frame, runs motion analysis, and applies `FrameValidityGate`.
2. For supported drills, `DrillConfigs.byTypeOrNull` provides a `DrillModeConfig` that now includes a `DrillCalibrationProfile`.
3. `AlignmentMetricsEngine` uses a cached `ConfiguredDrillAnalyzer` (single generic analyzer class) per drill.
4. `BaseDrillAnalyzer.analyzeFrame` pipeline:
   - `PoseSmoother` low-pass smoothing (per-drill alpha from calibration)
   - `PoseNormalization` (dominant side selection, midpoints, torso-length normalization)
   - `CommonMetricsCalculator` (angles, offsets, path/tempo proxies)
   - `IssueClassifier` (drill-specific issue logic with profile thresholds + activation frames)
   - `DrillMetricsCalculator` (subscores)
   - `ScoreEngine` (weighted overall score)
   - `CueEngine` (cooldown + persisted issue-based coaching cue)
5. `BaseDrillAnalyzer.finalizeSession` builds session score, issue timeline, recommendation, and summary narrative.

## Refactor highlights

- Removed redundant thin analyzer subclasses and replaced them with `ConfiguredDrillAnalyzer`.
- Removed silent fallback analyzer routing; unsupported drill usage now produces explicit unsupported-drill errors at config lookup.
- Calibration is now first-class in `DrillCalibrationProfile`:
  - threshold profile
  - score weights
  - smoothing alpha
  - wall reference
  - frame acceptance rules
  - issue activation frame overrides

## Calibration guide

Calibration is centralized in:
- `DrillProfiles.forDrill(...)` (threshold defaults per supported drill)
- `DrillModeConfig.calibration` (effective profile consumed by analyzers)

To tune a drill:
1. Edit thresholds in `DrillProfiles.thresholdFor`.
2. Edit score weights in `DrillConfigs` metric weights.
3. Optionally tune `smoothingAlpha`, `wallReferenceX`, and `issueActivationFrames` in `DrillCalibrationProfile` creation.

## Debug visibility

Per-frame debug now includes `metricDebug` entries (`MetricDebugEvaluation`) that capture:
- metric key
- raw metric value
- threshold band classification
- resulting subscore
- optionally associated triggered issue

This supplements existing debug fields (angles, offsets, issue classifications, cue trace, score).
