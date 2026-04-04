# Video Pipeline

The media pipeline is shared by live-session finalization and upload/reference analysis outcomes.

## Inputs

- Raw media callback URI (live flow) or imported media URI (upload flow).
- Upload ingest format diagnostics (codec, bit depth/HDR flags, rotation metadata, track inventory).
- Overlay timeline frames (live captured or import-synthesized).
- Resolved metadata (orientation, duration, drill context, timestamps).

## Pipeline stages

1. **Source acceptance**: validate callback/import ownership and URI readiness.
2. **Raw persistence**: copy/retain source media and persist status.
3. **Input normalization (upload path)**: inspect source format and attempt canonical ingest normalization (portrait, SDR, 8-bit-compatible, single-video-track contract).
4. **Timeline resolution**: freeze/serialize overlay timeline against session truth.
5. **Normalization**: resolve duration/orientation/render constraints for export timeline.
6. **Annotated render**: produce export output when possible.
7. **Verification**: inspect playable/readable media.
8. **Replay resolution**: choose best verified replay asset and persist.

## Runtime upload ownership (single active session)

- Upload execution is now owned by an app-scope `ActiveUploadCoordinator`, not the Upload screen lifecycle.
- Exactly one uploaded-video processing session may run at once; subsequent starts are blocked with a user-facing message.
- Upload/Home/History/Results can subscribe to the same active-session state (session id, stage, progress, terminal outcome) and safely reattach after navigation.
- Coordinator exposes terminal-session cleanup (`clearTerminalSession`) so stale completed/cancelled snapshots can be cleared without affecting active work.
- Work remains in-app runtime only (no durable WorkManager recovery queue reintroduced).

## Overlay timing alignment and degraded-result gate

- Upload overlay timeline frames are keyed and rendered by presentation timestamps.
- Timeline freeze/export uses the same timestamp basis for sampled frames, accepted overlays, and compositor resolution.
- Upload diagnostics now log timing+density fields: source duration, estimated source frames, decoded/sampled counts, pose success, accepted overlays, density per second, first/last overlay timestamps, export fps/duration, and normalization mode.
- Upload analysis now uses duration-aware sampling FPS (6fps default, 4fps for long videos, 3fps for very long videos) to prevent pathological slow analysis on lengthy uploads while preserving timeline monotonicity.
- Low overlay density/coverage is treated as degraded (`OVERLAY_DENSITY_TOO_LOW`) and routed to raw fallback instead of healthy annotated success.
- Current degraded thresholds:
  - minimum accepted overlays: `12`
  - minimum overlay density: `1.2` overlays/sec
  - minimum overlay coverage span: `35%` of source duration

## Annotated-first with raw fallback

- Annotated output is preferred when export + verification succeed.
- Raw media remains the safety path when annotated output is missing/invalid.
- Upload sessions keep raw archival media even when ingest-normalized working media is used for analysis/export.
- Session truth must not depend on annotated export success.
- Upload stage failures are terminalized with explicit failure codes so later hydration (home/history/results) can render safely without assuming complete artifacts.

## Why this matters

This design keeps coaching workflows resilient across devices/codecs while preserving a practical user outcome: a replayable session whenever capture/import succeeded.
