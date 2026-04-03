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

- Drill catalog cards, Drill Studio motion preview, and Drill Studio pose authoring now reuse the same base skeleton drawing path as live/upload overlays.
- Preview/authoring surfaces route normalized joint maps through `OverlayFrameRenderer` sizing/color/joint styling (including nose/hip emphasis) instead of a separate “green blob” preview painter.
- Pose authoring may layer edit affordances (selected-joint highlight and drag hit testing) on top of the shared renderer, but the underlying skeleton style should stay aligned with live coaching and upload analysis/export overlays.
