# Agent Documentation Guardrails

## README change policy (strict)

Do **not** rewrite `README.md` casually.

Only modify `README.md` when product flow, repo purpose, platform ownership (Android vs Studio), or architecture direction has materially changed.

When README updates are justified, they should be:

- intentional and minimal
- aligned with the current product narrative
- explicit about Android vs Studio ownership

Small implementation changes should update code comments or feature docs instead of rewriting README.

## Required README coupling rules

If a PR changes user flow or ownership boundaries between Android and Studio:

- update `README.md` in the same PR
- check/update relevant architecture docs in the same PR

When Android-vs-Studio ownership changes, review README and architecture docs together.

## Motivation and history preservation

Do not remove the product motivation/history sections unless they are clearly obsolete **and** replaced with an updated equivalent.

Preserve the human-led / AI-assisted workflow explanation unless specifically asked to remove it.
