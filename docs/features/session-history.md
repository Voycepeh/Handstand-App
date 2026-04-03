# Feature: Session History

Session History is the persisted record of drill-linked outcomes from live and upload workflows.

## What history stores

- Session metadata, scores, and issue summaries.
- Media state for raw/annotated assets.
- Resolver-selected replay source.
- Profile/profile attribution metadata for traceability.

## User-facing behavior

- Show prior sessions in drill-relevant context.
- Open replay using resolved best media source.
- Preserve truthful status when annotated export failed but raw replay exists.

## Cross-workflow role

History is not a passive archive; it is part of the training loop:

- review prior attempts,
- build references from strong sessions,
- compare current performance to previous outcomes.
