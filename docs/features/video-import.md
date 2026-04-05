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
- Imported media now runs through a dedicated ingest-normalization stage before pose analysis/export.
  - Canonical target contract: portrait pixels, SDR signaling, 8-bit-safe pipeline assumptions, single video track for analysis, stable analysis FPS.
  - Source variants requiring codec/range conversion (HEVC/H.265, HDR/10-bit signaling) trigger canonical transcode attempts.
  - Rotation metadata and extra non-video tracks are handled with metadata compensation when possible (cheap path, no forced transcode).
  - If transcode is unavailable/fails, pipeline falls back explicitly with diagnostics (`decisionReason`, `fallbackReason`, `failureStage`).
- Uploaded frame selection now uses adaptive sampling (default on) instead of fixed cadence.
  - Sparse steady-state targets: ~4-6 FPS depending on duration + movement type.
  - Burst windows: temporarily ~10-12 FPS when motion/pose confidence changes.
  - Guardrails: first segment, last segment, and rolling-window refresh are always sampled (never slower than legacy fixed cadence).
  - Safety fallback: if adaptive signals are unavailable, the pipeline falls back to legacy fixed cadence.
- Output persists to session/replay/history surfaces.
- Edge-frame bootstrap tolerates brief start/end occlusion; low-confidence boundary frames are skipped so short occlusions do not invalidate an otherwise usable upload.
- Picker intake is `content://`-safe: read permission is persisted when available and source media is copied into app-owned storage before metadata/decode/analyze.
- Upload processing treats the copied app-owned URI as the canonical in-app analysis source while the process stays alive; provider URI permission is intake-only.
- Failures in intake/decode/analyze/export are contained into terminal failed states with explicit diagnostics/failure reasons so hydration/history/results remain safe for incomplete uploads.
- If the app process dies mid-upload, the session is marked stalled/failed on next hydrate instead of attempting durable background recovery.
- Reference-template creation is optional, drill-linked, and comparison-oriented.

## Integration points

- Shares profile runtime context.
- Shares replay resolution policy with live sessions.
- Feeds Results / Session History and comparison workflows.
- Adaptive thresholds are centralized in `AdaptiveSamplingConfig` (`app/src/main/java/com/inversioncoach/app/movementprofile/UploadedVideoAdaptiveSampling.kt`).
