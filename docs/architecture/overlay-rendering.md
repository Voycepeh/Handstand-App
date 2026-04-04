# Overlay Rendering

Overlay responsibilities are intentionally split between live coaching UI and export rendering.

## Live overlay responsibilities

- Draw low-latency visual guidance during active coaching.
- Reflect current pose/cue state for immediate user feedback.
- Prioritize responsiveness and coaching clarity.

## Export overlay responsibilities

- Render timeline-consistent overlays for saved/exported media.
- Apply normalized orientation/duration metadata.
- Produce stable output suitable for replay/history/share.

## Contract between live and export

- Live loop records timeline data needed by export.
- Export should preserve visual intent without requiring live rendering internals.
- Drift, timestamp mismatches, or invalid timeline/media combinations should fail safely and emit diagnostics.

## Shared preview/authoring renderer contract

- Drill Studio motion preview is the source-of-truth surface for drill skeleton visuals.
- Drill catalog cards, Drill Studio motion preview, and Drill Studio pose authoring now reuse the same base skeleton drawing path via `OverlaySkeletonPreview` -> `OverlayFrameRenderer`.
- Preview/authoring surfaces route normalized joint maps through one shared joint alias mapping, connection list, scale-to-canvas fit policy, centering/content-padding policy, and joint/bone style treatment (including nose/hip emphasis).
- Shared policy now lives in `SkeletonRenderContract.SharedPolicy` and includes aspect ratio, content padding, style multiplier, stroke scaling, joint scaling, and canonical bone assumptions.
- `OverlaySkeletonPreview` accepts one policy object (`SkeletonRenderPolicy`) and no longer exposes duplicate style wrappers for the same sizing/styling controls.
- Bounds mapping is explicit per surface: pose authoring uses displayed reference-image bounds, while motion/choose-drill previews use shared content-rect bounds from the same policy helper.
- Pose authoring may layer edit affordances (selected-joint highlight and drag hit testing) on top of the shared renderer, but it should not replace full-skeleton rendering with handle-only markers.
