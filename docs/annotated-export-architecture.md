# Annotated Video Export Architecture (Audited)

## End-to-end path in current code

1. **Raw persistence**: `SessionRecorder` finalizes raw capture, and `SessionRepository.saveRawVideoBlob(...)` persists the canonical raw URI used for replay/export.
2. **Overlay persistence**: live pose samples are collected by `OverlayTimelineRecorder`, serialized with `OverlayTimelineJson`, and stored as `OverlayTimeline`.
3. **Export start**: `AnnotatedExportPipeline.export(...)` validates inputs, marks processing state, then delegates to `AnnotatedVideoComposer.compose(...)`.
4. **Decode/render**: `AnnotatedVideoCompositor.export(...)` uses `MediaExtractor + MediaCodec` decode (surface output), resolves overlays by decoder timestamp (`OverlayTimelineResolver.overlayAt(...)`), composites in GL, and feeds encoder surface.
5. **Encode/mux**: H.264 output from `MediaCodec` encoder is muxed to MP4 with `MediaMuxer`.
6. **Output verification**: exported metadata is re-read and validated (duration/size/dimensions), then pipeline-level verification persists success/failure state.
7. **Replay source selection**: results/replay preference resolves annotated first when readable, otherwise falls back to raw (`resolvePreferredReplayUri(...)`).

## Root causes fixed in this PR

### Duration truncation

- Export success previously only required "playable output", not "output duration near raw duration".
- This allowed early-truncated outputs to pass verification.
- Fix: export now verifies output duration against authoritative raw duration (with tolerance) before reporting success.

### 90-degree rotation and overlay misalignment

- Rotation metadata could be missing from extractor format and not recovered from retriever metadata fallback.
- Output dimension planning did not normalize for 90/270 rotations.
- Overlay points were not guaranteed to share the exact same normalization transform as video.
- Fix: source metadata + canonical export transform are now explicit; video and overlay both use the same normalized orientation mapping.

## Diagnostics now logged per export

- raw source URI
- raw duration / width / height / rotation
- overlay frame count + first/last timestamps + span
- selected output width / height
- export start/end timestamps
- verified output duration / width / height / rotation
- verification pass/failure detail
