# Feature: Current User Flows

This document captures the current end-to-end app workflows and route surfaces.

## Route-level flow map

- `home` -> Home / Drill Hub
- `start` -> Start Drill selector
- `live/...` -> Live Session
- `results/{sessionId}` -> Results
- `history` -> History Overview (top-level home history landing page)
- `session-history?...` -> Session History (drill-aware detailed history / compare surface)
- `manage-drills` -> Manage Drills
- `drill-studio?...` -> Drill Studio
- `drill-workspace/{drillId}` -> Drill Workspace
- `upload-video?...` -> Upload / Reference Training

## 1) Home / Drill Hub

Home is the primary entry point. It exposes both drill paths explicitly:

- **Drills**: browse drills for usage and open Drill Workspace.
- **Manage Drills**: create/import/edit/export/delete drill packages via Drill Studio.

Home also keeps **Start Live Coaching**, **Upload Video**, **History**, and **Settings** as top-level actions.

### First-launch recording preferences onboarding

- On true first launch, Home shows a compact welcome dialog.
- The dialog offers two paths:
  - **Use recommended settings**: applies centralized recording/export defaults and completes onboarding.
  - **Open recording settings**: routes directly to the existing Settings screen and completes onboarding.
- The old duplicate first-launch settings form (export quality/countdown/storage controls inside onboarding) is removed.
- Recording/export preference editing now lives only in **Settings**.

## 2) Drills -> Drill Workspace

1. Open `start?destination=workspace` from Home's **Drills** action.
2. Browse training drills.
3. Select a drill to open `drill-workspace/{drillId}`.
4. Use drill-scoped operational actions (live coaching, upload attempt, sessions, compare).

## 3) Manage Drills -> Drill Studio

1. Open `manage-drills`.
2. Use authoring/admin actions: New Drill, Import Drill Package, Open, Export, Delete.
3. **Open** navigates to `drill-studio`.
4. Save and return to Manage Drills.

## 4) Drill Workspace

For drill-specific context:

- Start a live session for the drill.
- Upload an attempt scoped to the drill.
- Open drill comparison history.
- Open drill editing in Drill Studio when needed.

## 5) Live Session

1. Start from Home or Drill Workspace.
2. Pass countdown gate.
3. Run active coaching loop.
4. Stop and finalize media.
5. Resolve replay source.
6. Navigate to Results.

## 6) Upload / Reference Training

1. Select a video and drill context (optional or drill-scoped).
2. Run sampled-frame analysis.
3. Optionally create/update drill-linked reference artifacts.
4. Export/verify replay candidates.
5. Resolve replay source and open Results.

## 7) Results / History split

- **History Overview (`history`)**: top-level landing page opened from Home's History action.
- **Session History (`session-history?...`)**: deeper drill-aware list/compare/reopen surface.
- **Results**: immediate per-session outcome surface.
- Replay uses resolver output (annotated preferred, raw fallback).

## Maintenance rule

When any route name, screen name, or navigation behavior changes, update this file and matching diagrams in the same PR.
