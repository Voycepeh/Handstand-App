# Migration notes: motion-aware coaching upgrade

## Summary
This migration adds a parallel motion-analysis layer (`motion/*`) without removing the existing biomechanics analyzers.

## Breaking changes
- `SettingsScreen` signature changed to include `onDeveloperTuning` callback.
- `StartDrillScreen` signature changed to include `onOpenDetail` callback.
- `LiveSessionUiState` now includes:
  - `currentPhase`
  - `activeFault`

## Runtime behavior changes
- Live coaching now computes movement phase and fault events via `MotionAnalysisPipeline`.
- Live overlay text now includes phase/fault/rep counters.
- Drill selection now includes animated previews and drill detail navigation.

## Follow-up recommendations
- Persist developer tuning values in Room preferences (currently in-memory).
- Bind drill-specific phase models from metadata instead of a single default FSM track angle.
- Expand side-specific fault thresholds by drill.
