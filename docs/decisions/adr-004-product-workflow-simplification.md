# ADR-004: Product Workflow Simplification

## Status

Accepted

## Context

As the app evolved beyond a narrow live/import framing, workflow complexity increased in drill authoring and related navigation. Contributors introduced overlapping save paths, redundant actions, and branching UI decisions that were hard for users to reason about.

Observed issues:

- Multiple save variants that implied different outcomes without clear value.
- Redundant actions (for example archive/delete style overlaps) that added UX noise.
- Existing drill edit flows that did not always reload persisted data reliably.
- Expert-only branches that obscured the main path for most users.

## Decision

We intentionally simplify product workflows around a few rules:

1. **Prefer one obvious save path** over multiple confusing save variants.
2. **Avoid redundant actions** (such as archive vs delete) when they do not provide clear user value.
3. **Ensure persisted editing reliability**: opening an existing drill should restore saved drill data deterministically.
4. **Favor workflow clarity over expert-only branching** in primary UI paths.
5. **Keep flows drill-centric** so users return to meaningful practice context.

## Consequences

### Positive

- Faster understanding of the app’s primary workflows.
- Lower maintenance burden for UI/state branching.
- More predictable persistence behavior in Drill Studio and related flows.
- Cleaner docs/diagrams and reduced terminology drift.

### Trade-offs

- Some advanced or legacy options may be removed from first-class UI.
- Contributors must justify any new branching behavior against these simplification rules.

## Contributor actions

When changing workflow behavior:

- Update feature docs and architecture docs in the same PR.
- Update Mermaid diagrams for affected user journeys.
- Use current terminology consistently (Drill Hub, Manage Drills, Drill Studio, Upload / Reference Training, Results / Session History).
