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

## Annotated-first with raw fallback

- Annotated output is preferred when export + verification succeed.
- Raw media remains the safety path when annotated output is missing/invalid.
- Upload sessions keep raw archival media even when ingest-normalized working media is used for analysis/export.
- Session truth must not depend on annotated export success.
- Upload stage failures are terminalized with explicit failure codes so later hydration (home/history/results) can render safely without assuming complete artifacts.

## Why this matters

This design keeps coaching workflows resilient across devices/codecs while preserving a practical user outcome: a replayable session whenever capture/import succeeded.
