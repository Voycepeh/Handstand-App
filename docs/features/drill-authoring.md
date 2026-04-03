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
- Drill Studio motion preview and drill catalog/start cards share the same portrait skeleton preview renderer path and styling contract.
- Authoring-specific affordances (joint drag hit testing and selected-joint highlight) are layered on top of that shared renderer.

## Simplification direction

Drill authoring should prioritize clarity over branching:

- Avoid multiple save variants that imply ambiguous persistence outcomes.
- Avoid duplicate actions with equivalent results.
- Keep destructive actions explicit and confirmation-backed.
- Return users to Manage Drills with clear post-save state.

For repo-level rationale, see [ADR-004](../decisions/adr-004-product-workflow-simplification.md).
