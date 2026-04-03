# Feature: Video Import / Upload Analysis

Video Import supports offline review and reference-training preparation from uploaded clips.

## User-facing flow

1. Open Upload / Reference Training.
2. Select source video clip.
3. Run pose/motion/score analysis.
4. Render or prepare replay/export artifacts.
5. Review outcome and optionally save as drill reference.

## Key behavior

- Upload analysis is parallel in intent to live coaching but uses imported timing/media sources.
- Uploaded frame selection now uses adaptive sampling (default on) instead of fixed cadence.
  - Sparse steady-state targets: ~4 FPS for generic/rep uploads and ~3-4 FPS for hold drills.
  - Burst windows: temporarily ~10-12 FPS when motion/pose confidence changes.
  - Guardrails: first segment, last segment, and rolling-window refresh are always sampled.
  - Safety fallback: if adaptive signals are unavailable, the pipeline falls back to legacy fixed cadence.
- Output persists to session/replay/history surfaces.
- Reference-template creation is optional, drill-linked, and comparison-oriented.

## Integration points

- Shares profile runtime context.
- Shares replay resolution policy with live sessions.
- Feeds Results / Session History and comparison workflows.
- Adaptive thresholds are centralized in `AdaptiveSamplingConfig` (`app/src/main/java/com/inversioncoach/app/movementprofile/UploadedVideoAdaptiveSampling.kt`).
