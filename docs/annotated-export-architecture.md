# Annotated Video Export Architecture (Audited)

## What the real code stores

The app stores **raw video + timestamped overlay metadata** (not sparse bitmap frames and not a second overlay stream):

1. Raw camera video is persisted by `saveRawVideoBlob(...)` in finalization. 
2. Overlay capture is recorded as `OverlayTimeline` samples (`OverlayTimelineRecorder` -> `OverlayTimelineJson`) where each frame is landmarks + render flags + timestamp.
3. During export, timeline frames are converted to `AnnotatedOverlayFrame` and resolved per output timestamp.

## What export actually does

Export is a redraw-and-reencode pipeline:

1. `AnnotatedExportPipeline.export(...)` marks processing, starts timeout, and calls `AnnotatedVideoComposer.compose(...)`.
2. `AnnotatedVideoComposer` forwards raw video + timeline to `AnnotatedVideoCompositor.export(...)`.
3. `AnnotatedVideoCompositor`:
   - decodes raw video frame-by-frame via `MediaMetadataRetriever.getFrameAtTime(...)`
   - resolves overlay data using `OverlayTimelineResolver.overlayAt(timestampMs)`
   - draws overlays with `OverlayFrameRenderer.drawAndroid(...)`
   - encodes H.264 with `MediaCodec` and muxes MP4 with `MediaMuxer`
   - verifies output readability and duration

So the model is: **raw video + timestamped overlay metadata -> redraw overlay per frame on export -> encode new MP4**.

## Bottleneck found for short clips

The dominant bottleneck was decode/seek behavior inside compositor frame extraction:

- The compositor previously used `OPTION_CLOSEST_SYNC`, which repeatedly seeks to sync frames and can be expensive even on short media.
- Export also had duplicated post-export verification in caller and pipeline paths.

Fixes applied:

- switched decode fetch to `OPTION_CLOSEST` for per-frame extraction
- added stage telemetry (decode/render/encode/verify counters + elapsed times)
- removed redundant caller-side verification pass after pipeline verification

## Export-state/UI mismatch fixes

- Added explicit **pre-export failure** path (`EXPORT_NOT_STARTED`) when raw URI is missing before render work begins.
- When export never starts, progress/ETA are cleared instead of pretending active processing.
- Failure card now shows terminal failure and explicit raw fallback message:
  - “Annotated export failed, raw replay available”.
- Upload flow now clears ETA when export is no longer actively processing.

## Structured telemetry now emitted

Telemetry includes:

- `exportStarted`
- `decoderInitialized`
- `firstFrameDecodedAt`
- `decodedFrameCount`
- `renderedFrameCount`
- `encodedFrameCount`
- `overlayFramesAvailable`
- `overlayFramesConsumed`
- `outputBytesWritten`
- `exportCompleted`
- `failureReason`
- elapsed times by stage (`decode`, `overlayResolve`, `render`, `verify`, `total`)

`AnnotatedExportPipeline` now logs these fields in a structured log line during export progress and terminal outcomes.

## Follow-up performance audit (post-telemetry patch)

### 1) Exact decode mechanism now used

The current compositor still decodes via `MediaMetadataRetriever.getFrameAtTime(...)` inside the export loop, now with `OPTION_CLOSEST` (previously `OPTION_CLOSEST_SYNC`).

### 2) Frame cadence (every frame or sparse timestamps)

It decodes **every output frame index** from `0 until totalFrames`, where `totalFrames = duration * outputFps`; this is not sparse decode.

### 3) Overlay lookup efficiency

Overlay lookup is timestamp-based and reasonably efficient:

- `OverlayTimelineResolver` keeps `lowerIndex` and advances monotonically for forward playback.
- It only falls back to binary search when timestamps move backward.
- It interpolates between neighboring overlay samples.

So overlay lookup is not an O(n) full scan per frame.

### 4) Most likely remaining bottlenecks

For longer clips, the pipeline is still fundamentally CPU/memory heavy because each output frame performs:

1. retriever seek/decode to bitmap (`getFrameAtTime`),
2. optional bitmap scaling/conversion,
3. canvas draw of source bitmap + overlay,
4. surface encode drain/mux.

Telemetry fields (`decodeElapsedMs`, `overlayResolveElapsedMs`, `renderElapsedMs`) support confirming which stage dominates in-device runs, but architecturally the retriever+bitmap path remains the high-cost component.

### 5) Should a later PR replace retriever loop with extractor/decoder surfaces?

Yes — for performance scalability, a later PR should move to a `MediaExtractor + MediaCodec` decoder pipeline with surface-to-surface rendering and encoder input surface compositing. That would avoid per-frame `Bitmap` extraction and reduce CPU churn/GC pressure.

## Conclusion

This PR improves correctness, observability, and state semantics, and it slightly improves decode behavior by changing retriever mode. It **does not fully remove the root long-clip performance risk**, because export still depends on per-frame `MediaMetadataRetriever` bitmap extraction.
