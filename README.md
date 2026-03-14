# Inversion Coach (Android)

Inversion Coach is an Android app for side-view inversion drill coaching using an on-device pipeline:

**Camera → Pose Detection → Biomechanics Analysis → Coaching Cue → Session Summary**

The app is currently an MVP focused on live coaching plus basic session persistence and review.

## 1) App overview

### What the app does
- Captures live camera frames with CameraX.
- Runs on-device pose detection with ML Kit Pose Detection (stream mode).
- Computes drill-specific metrics and scores.
- Generates rule-based coaching cues (with optional text-to-speech playback).
- Saves session/frame/issue data locally in Room and shows basic history/results screens.

### Supported drill modes (current)
- Chest-to-wall handstand
- Back-to-wall handstand
- Pike push-up
- Elevated pike push-up
- Negative wall handstand push-up

### Current MVP scope
- Side-view coaching flow is implemented for the five drills above.
- Local persistence and simple trend/review UI are implemented.
- Advanced replay/export/sharing workflows are still incomplete (details below).

---

## 2) Current implementation status

### Fully implemented
- Single-activity Compose app with navigation across Home, Start Drill, Live Coaching, Results, History, and Settings.
- Camera permission request + live CameraX preview/analyzer binding.
- ML Kit STREAM_MODE pose inference integration via `PoseAnalyzer`.
- Per-drill biomechanics analyzer selection and scoring.
- Rule-based cue selection with timing/persistence guards.
- Optional voice cue playback using Android TTS.
- Optional local video recording to MediaStore (`Movies/InversionCoach`) while a live session is running.
- Session lifecycle persistence to Room (session record + sampled frame metrics + issue timeline).
- Results/History screens backed by persisted data.

### Partially implemented
- Home screen "last session / average / issue" card is currently static text, not live data.
- Settings screen persists cue frequency, overlay intensity, and debug overlay flag, but several setting actions are placeholders.
- WorkManager cleanup worker exists as a hook but does not currently perform retention deletion logic.
- Results screen computes and displays summary/timeline data, but action buttons for replay/share/note are placeholders.

### Not yet implemented / known gaps
- Freestanding handstand is defined as a future enum value only; it is not available as a selectable drill config.
- Annotated video generation/replay is not implemented (stored as `null`).
- Raw video replay from Results is not wired.
- "Delete all sessions" and "Enable local-only privacy mode" settings buttons have no action handlers.
- No in-app APK update/install flow (standard Android install path only).

---

## 3) Current architecture

### Major modules/components
- `ui/` — Compose screens and navigation.
- `camera/` — CameraX session binding and lifecycle integration.
- `pose/` — ML Kit pose inference and pose smoothing.
- `biomechanics/` — drill configs, analyzers, scoring, and issue classification.
- `coaching/` — cue selection and voice playback.
- `recording/` — local session recording to MediaStore.
- `storage/` — Room DB, DAOs, repository, and service locator.
- `summary/` — deterministic post-session summary/recommendation generation.
- `history/` — retention worker scaffold.

### High-level live coaching flow
1. User selects a drill and options.
2. Live screen requests camera permission and starts camera preview/analysis.
3. `PoseAnalyzer` emits pose frames.
4. `AlignmentMetricsEngine` computes drill-specific scores/metrics.
5. `CueEngine` selects cues; `VoiceCoach` optionally speaks them.
6. Frame metrics/issues are sampled into Room during the session.
7. On stop, session summary fields are generated and persisted.
8. Results screen reads persisted records for review.

---

## 4) Setup / installation

## Install for testers (recommended)

1. Open the project's **GitHub Releases** page.
2. Download the latest APK file (`InversionCoach-<tag>.apk`) to your Android device.
3. Open the APK and allow install from this source if Android asks.
4. Install and open **Inversion Coach**.
5. Grant **Camera** permission when prompted.

## Release process (maintainers)

Push a version tag to automatically build and publish the APK:

```bash
git tag v0.1.0
git push origin v0.1.0
```

This triggers `.github/workflows/android-release.yml`, which builds the debug APK from `:app` and uploads it to a GitHub Release for that tag.

### Local build (optional)

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5) Current usage flow

1. Open app → tap **Start Drill**.
2. Choose one of the currently supported drills.
3. Optionally toggle voice/recording/overlays.
4. Tap **Start 3s Countdown** (starts session immediately in current build).
5. In live view, keep full body visible in side view and follow cue text (and voice if enabled).
6. Tap **Stop** to finalize.
7. Review the generated Results screen (overall score, average sampled score, top issues, timeline).

What to expect live right now:
- Score and cue updates appear in-session.
- Debug metrics/angles are available if debug overlay is enabled in Settings.
- Recording can be toggled; finalized raw video URI is saved to the session record.

---

## 6) Limitations / known issues

- The Start button label says "Start 3s Countdown," but there is no visible countdown flow.
- Home dashboard summary card is placeholder text.
- Several Results and Settings buttons are present but non-functional placeholders.
- Cleanup/retention worker currently does not delete old data.
- Only one runtime permission is requested (Camera); permission handling for other potential future features is not implemented.
- If release minification is enabled, keep-rules for ML Kit must remain in `app/proguard-rules.pro`.

---

## 7) Roadmap / next steps

- Wire replay actions (raw video first, annotated replay later).
- Replace Home placeholder stats with real repository data.
- Implement Settings actions (privacy-mode behavior, delete-all confirmation + execution).
- Add true retention cleanup policy and scheduling.
- Add/testing-harden additional drill modes (including future freestanding support) only after analyzer coverage exists.
- Expand device testing and improve runtime error/reporting around model availability.


## 8) Camera and pose-detection troubleshooting

### Permissions
- Required at runtime: **Camera** permission.
- If denied, Android preview can still appear in some states, but inference will not run. Re-grant in system app settings.

### Recommended camera setup (for side view detection)
- Place the phone on a stable surface/tripod at roughly hip-to-chest height.
- Keep your full body in-frame: head, shoulders, hips, knees, ankles, and feet.
- Prefer bright, even lighting and high contrast between body and background.
- Use a side profile for handstand drills as intended by scoring logic.

### "Pose inference dropped a frame" / no scoring checklist
1. Verify camera is not blocked and only one user is visible.
2. Hold steady for ~1–2 seconds to reduce frame drops while inference catches up.
3. Step back to include full body if UI shows "body not fully visible".
4. Increase lighting if UI shows "low confidence".
5. If UI shows repeated "frame processing failure", restart app and verify device supports ML Kit pose detection.

### Debug vs release notes
- Debug and release both use the same pose pipeline (`InputImage.fromMediaImage(...)` + STREAM_MODE detector).
- `app/proguard-rules.pro` includes ML Kit keep rules to avoid class stripping when minification is turned on in future release builds.
- Debug overlay (Settings → debug overlay) now shows landmarks detected, inference time, dropped frames, confidence, and rejection reason.
