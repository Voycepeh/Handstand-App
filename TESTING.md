# Testing Guide

This repository does not include the Gradle wrapper; use a locally installed Gradle.

## Prerequisites

- JDK 17
- Android SDK 34
- `gradle` on PATH

## Core validation commands

```bash
gradle testDebugUnitTest
gradle :app:assembleDebug
```

## Recommended validation sequence

1. Run unit tests for a quick regression pass.
2. Build `:app:assembleDebug` to verify Android packaging and compile graph.
3. If you changed workflow behavior, manually verify these flows in app:
   - Home / Drill Hub navigation paths
   - Manage Drills -> Drill Studio save/edit behavior
   - Live Session start/countdown/stop/results flow
   - Upload / Reference Training analysis and results handoff
   - Results / Session History replay selection
   - Profiles activation and persistence

## Manual regression checklist

- Route names and entry points still map to the expected screens.
- Drill context is preserved through live/upload/history workflows.
- Replay fallback remains truthful (annotated preferred, raw fallback).
- Upload/reference drill linkage still works (no silent template/drill association regressions).
- Profile/profile context still resolves in both live and upload analysis.

## Documentation requirement

Any PR that changes UX flow, navigation, architecture, media pipeline, or terminology must update relevant markdown docs and diagrams in the same PR.
