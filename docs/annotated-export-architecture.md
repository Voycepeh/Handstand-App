# Annotated Video Export Architecture

## Root cause of previous behavior

The old export path copied `rawVideoUri` directly into the session's `annotated.mp4` blob without rendering overlays onto decoded frames. In practice:

1. CameraX `VideoCapture` recorded the raw camera stream.
2. Pose overlays were drawn only by Compose (`OverlayRenderer`) on top of live preview UI.
3. `AnnotatedExportPipeline` persisted the raw source URI as "annotated" output.

This meant replay/exported files outside app playback contained no stickman, dots, or alignment guides.

## New architecture

The export flow now produces a true composited MP4:

1. **Overlay data model**: `AnnotatedOverlayFrame` captures timestamped landmarks and render flags.
2. **Shared renderer**: `OverlayFrameRenderer` draws skeleton joints/limb lines, head and hip dots, and ideal line for both preview and export.
3. **Export compositor/encoder**: `AnnotatedVideoCompositor` decodes frames from raw recording, redraws overlays per timestamp, and re-encodes to a new MP4.
4. **Persistence**: resulting composited file is saved as `annotated.mp4` via repository/blob storage.
5. **Replay selection**: replay prefers `annotatedVideoUri`, falling back to raw only if annotated asset is missing.

## Timestamp synchronization

- Export maps frame presentation time (ms) to nearest overlay sample.
- If nearest pose sample is too far (`>200ms`), overlay for that frame is skipped safely and logged.
- This avoids crashes and prevents rendering stale/jumping skeleton data.

## Logging

Exporter logs:

- export start (input, dimensions, fps, duration)
- frame render progress (periodic)
- missing pose frame events
- export complete
- export failures

## Debug validation mode

In debug builds, exporter runs a lightweight pixel-difference validation between raw and annotated output frames and logs whether overlays appear baked into the generated MP4.
