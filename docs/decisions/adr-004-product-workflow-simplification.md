# ADR-004: Product Workflow Simplification

## Status

Accepted

## Context

The app accumulated overlapping actions and partially separate workflows for drill editing, saving, reference handling, and navigation. That created avoidable UI complexity and made it harder for users to understand what would actually happen when they pressed a button.

Examples of confusion included duplicate destructive actions, multiple save variants that implied different levels of persistence or template behavior, and drill-opening flows that did not reliably restore prior selections.

## Decision

The product should favor simpler, more deterministic workflows:

1. Prefer one clear save path in drill authoring over multiple overlapping save variants.
2. Keep destructive actions explicit and confirmation-based.
3. Remove or hide options that do not provide distinct user value.
4. Preserve persisted drill data reliably when reopening and editing drills.
5. Keep workflows drill-centric so users return to the context they started from.

## Consequences

### Positive

- Lower UI and workflow complexity.
- Easier mental model for drill creation and editing.
- Fewer hidden branches in navigation and persistence behavior.
- Clearer expectations for future contributors.

### Trade-offs

- Fewer expert-facing shortcuts in the UI.
- Some previously exposed internal concepts may remain supported in code while no longer being first-class UI actions.
