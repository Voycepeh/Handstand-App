# Feature: Current User Flows

This document captures the current end-to-end app workflows and route surfaces.

## Route-level flow map

- `home` -> Home / Drill Hub
- `start` -> Start Drill selector
- `live/...` -> Live Session
- `results/{sessionId}` -> Results
- `history` + `session-history?...` -> Results / Session History surfaces
- `manage-drills` -> Manage Drills
- `drill-studio?...` -> Drill Studio
- `drill-workspace/{drillId}` -> Drill Workspace
- `upload-video?...` -> Upload / Reference Training
- `profile` -> Profiles

## 1) Home / Drill Hub

Home is the primary entry point. Users can start sessions, open drill workflows, open uploads, inspect history, and access profile/settings.

### First-launch recording preferences onboarding

- On true first launch, Home shows a compact welcome dialog.
- The dialog offers two paths:
  - **Use recommended settings**: applies centralized recording/export defaults and completes onboarding.
  - **Open recording settings**: routes directly to the existing Settings screen and completes onboarding.
- The old duplicate first-launch settings form (export quality/countdown/storage controls inside onboarding) is removed.
- Recording/export preference editing now lives only in **Settings**.

## 2) Manage Drills -> Drill Studio

1. Open `manage-drills`.
2. Create or open a drill.
3. Navigate to `drill-studio`.
4. Save and return to Manage Drills.

## 3) Drill Workspace

For drill-specific context:

- Start a live session for the drill.
- Upload an attempt scoped to the drill.
- Open drill comparison history.
- Open drill editing in Drill Studio.

## 4) Live Session

1. Start from Home or Drill Workspace.
2. Pass countdown gate.
3. Run active coaching loop.
4. Stop and finalize media.
5. Resolve replay source.
6. Navigate to Results.

## 5) Upload / Reference Training

1. Select a video and drill context (optional or drill-scoped).
2. Run sampled-frame analysis.
3. Optionally create/update drill-linked reference artifacts.
4. Export/verify replay candidates.
5. Resolve replay source and open Results.

## 6) Results / Session History

- Results: immediate per-session outcome surface.
- Session History: ongoing list/compare/reopen surface.
- Replay uses resolver output (annotated preferred, raw fallback).

## 7) Profiles

- Set/maintain active profile.
- Profile affects analysis interpretation in live and upload workflows.
- Missing profile falls back to defaults.

## Maintenance rule

When any route name, screen name, or navigation behavior changes, update this file and matching diagrams in the same PR.
