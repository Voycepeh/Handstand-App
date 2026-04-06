# Feature: Live Coaching

Live Coaching is the drill-centric real-time practice workflow.

## User-facing flow

1. Choose a drill from Drill Hub.
2. Resolve effective drill/session view.
3. Complete countdown gating.
4. Run live coaching with overlays and cues.
5. Stop session.
6. Finalize, export, resolve replay, and navigate to results/history.

## Key behavior

- Countdown/start gating prevents premature start state.
- Overlay/cue loop runs during active session and records timeline inputs.
- Finalization persists raw media before export completion.
- Replay selection is annotated-first with raw fallback.
- Live coaching defaults to back camera + 1x-equivalent zoom and restores last live camera/zoom/view preferences.
- In-session controls support camera switch (front/back), zoom presets (0.5x/1x/2x when supported), and view preset locking (Front/Side/Freestyle).
- Front camera preview can stay mirrored while overlay coordinates and left/right analysis semantics stay consistent through mirrored pose projection metadata.
- Front and Side presets lock render/feedback assumptions to avoid freestyle orientation oscillation; Freestyle keeps adaptive view classification.
- Lightweight framing diagnostics debounce setup warnings for: subject too small, body out of frame, unstable tracking, and suggested camera-height correction.

## Cross-feature integration

- Produces drill-linked outcomes for Results / Session History.
- Supports downstream reference/comparison usage.
