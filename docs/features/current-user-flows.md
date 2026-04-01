# Current User Flows

This document describes the intended top-level product journeys after the recent UI, UX, and workflow simplification changes.

## Drill Hub as the Main Entry Point

The app should feel drill-centric. Users should be able to move from home into drill selection, drill management, live practice, upload analysis, and history without being dropped into unrelated editing flows by accident.

## Main Journeys

### Practice live

1. Open Drill Hub or choose a drill from the home surface.
2. Start a live session for that drill.
3. Countdown completes.
4. Practice with realtime overlays and cues.
5. Stop session.
6. Review results, replay, and saved history.

### Upload and review an attempt

1. Open upload/reference training flow.
2. Select a video.
3. Analyze the clip.
4. Review replay and feedback.
5. Optionally save it as part of drill history or use it as reference input.

### Manage and edit drills

1. Open Manage Drills.
2. Open an existing drill or create a new one.
3. Edit it in Drill Studio.
4. Save through one clear validation-and-save path.
5. Return to Manage Drills.

### Build reference-based training

1. Use a strong session or imported clip.
2. Link it to a drill.
3. Create or update a reference template / baseline.
4. Compare future attempts to that drill-linked reference.

### Calibrate and manage profiles

1. Open calibration/profile flow.
2. Create, edit, or select the active body profile.
3. Use that profile across analysis flows.

## Simplification Rules

- Avoid duplicate actions that lead to the same outcome.
- Keep destructive actions explicit and confirm before deletion.
- Reopening an existing drill should restore persisted values reliably.
- Save actions should be truthful and easy to understand.
- Drill workflows should return users to drill context after completion.
