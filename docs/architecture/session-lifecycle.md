# Session Lifecycle

This document captures the live coaching lifecycle and the async transitions that follow a drill-based practice session.

## Happy Path

1. User enters live coaching from drill context.
2. Session options resolve a concrete effective view before capture begins.
3. Countdown completes and the session becomes active.
4. Recording starts and pose frames stream into analysis, cues, and overlay collectors.
5. User stops session.
6. Recorder finalize callback provides raw URI.
7. Raw artifact is persisted and verified.
8. Overlay timeline is frozen and resolved against session truth.
9. Annotated export runs asynchronously.
10. Replay source resolver chooses the best playable URI, preferring annotated replay and falling back to raw.
11. Session is finalized and appears in results and history under the originating drill context.

## Important State Boundaries

- **Entry state**: drill selection, session option resolution, permission/camera readiness.
- **Countdown state**: warm-up without prematurely treating the session as started.
- **Recording state**: live frame processing, overlay/cue updates, and timeline capture.
- **Finalization state**: stop pressed → finalize callback acceptance → raw persistence → export launch.
- **Replay state**: readiness and validation determine the selected replay asset.

## Failure and Fallback

- Empty or duplicate finalize callbacks are ignored and diagnosed.
- Raw persist failures are captured in status and diagnostics.
- Annotated export failures preserve session truth and the fallback replay path.
- Verification failures force replay selection away from invalid assets.
- Drill context should still remain recoverable even when one downstream media step fails.
