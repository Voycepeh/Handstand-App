# Feature: Drill Authoring (Drill Studio)

Drill Studio is the authoring workspace for creating and refining drill definitions used across live and upload workflows.

## What Drill Studio is for

- Create new drill definitions.
- Edit existing drills with predictable persistence.
- Maintain drill metadata needed by coaching, scoring, and reference workflows.

## Persisted editing behavior

When users reopen an existing drill, the editor should:

- Reload persisted drill data reliably.
- Avoid silently resetting prior selections.
- Surface validation errors clearly.
- Save through one obvious path that writes current truth.

## Pose authoring and preview rendering

- Pose Authoring uses the same base skeleton renderer visual language as live coaching and uploaded-video overlay surfaces.
- Drill Studio motion preview is the visual source of truth for authored drill skeleton presentation.
- Drill Studio motion preview, drill catalog/start cards, and pose authoring all use one shared portrait skeleton preview renderer path and styling contract (joint aliases, connection layout, sizing/fit, colors, and joint treatment).
- Authoring-specific affordances (joint drag hit testing and selected-joint highlight) are layered on top of that shared renderer; only the active joint gets special highlight treatment.
- Pose authoring keeps the full readable skeleton visible even when a phase pose map is sparse by combining authored joints with a canonical fallback pose before rendering.

## Simplification direction

Drill authoring should prioritize clarity over branching:

- Avoid multiple save variants that imply ambiguous persistence outcomes.
- Avoid duplicate actions with equivalent results.
- Keep destructive actions explicit and confirmation-backed.
- Return users to Manage Drills with clear post-save state.

For repo-level rationale, see [ADR-004](../decisions/adr-004-product-workflow-simplification.md).

## Phase 1 image-seeded phase authoring

Drill Studio now supports **single-image phase seeding** directly inside phase editing:

- Attach one reference image per phase via **Add reference image** with source chooser:
  - Take photo
  - Choose from device
- Run on-device pose detection on that image.
- View skeleton overlay directly on top of the image canvas.
- Apply constrained per-joint correction offsets (drag and reset flows).
- Save canonical normalized phase joints plus authoring metadata into drill persistence.
- Selected authoring images are copied into app-managed storage so phase editing survives restart/provider URI churn.

### Pose authoring viewport contract

The phase editor now treats pose authoring as a dedicated viewport surface:

- Reference image rendering is clipped to the pose canvas only (no parent-card/background bleed).
- Pose overlay, joints, guides, and hit-testing use the same mapped image bounds inside the viewport.
- Overlay framing is always based on the actively displayed image rect, and authoring now uses a tighter portrait-safe content padding policy so imported images occupy more of the available canvas on tall phones.
- Pose detection normalization uses full image dimensions (not landmark-bounding-box normalization), so the detected skeleton remains anchored to the displayed image geometry.
- Authoring controls are grouped into compact sections (image+detection, edit, advanced editing, save) to reduce vertical fragmentation on narrow screens.
- The viewport keeps a stable portrait aspect ratio whether a reference image is attached or not.
- Empty canvas mode is explicit: when no image is attached, the authoring area shows a deliberate “No reference image loaded” state while keeping manual pose editing available.

### Camera capture behavior

- **Take photo** uses `ActivityResultContracts.TakePicture` with a FileProvider-backed temp capture URI under app cache.
- Capture result handling logs launch/success/failure/cancel paths for easier regression diagnosis.
- Cancel, URI creation failure, permission denial, and launch failures are surfaced as user-visible status text with a direct fallback path to **Choose from device** (no dead button/no silent no-op).

### Boundary and environment guides

The phase authoring canvas includes lightweight optional visual guides:

- frame/corner guides
- floor line
- wall line
- bar/reference line

These are stored as authoring settings and are intended to be extendable in future phases.

> Current phase-1 note: Drill Studio keeps image-space normalization and viewport mapping consistent with the shared overlay framing contract used by preview surfaces, while preserving existing live/upload overlay paths.

### Persistence and compatibility

- Authored phase image metadata is persisted with the drill in DB-backed drill records (via Drill Studio payload in drill cue config).
- Existing seeded drill loading is preserved.
- New authored data coexists with seeded catalog drills.

### Drill package export/import

Manage Drills now supports package sharing for authored drills:

- Export includes drill metadata + cue payload (including phase poses, manual offsets, guide settings, and image-source authoring references).
- Export sanitizes local image references so imported drills do not depend on inaccessible `content://` URIs from another device.
- Import rehydrates the drill record so the drill is immediately editable and previewable.
