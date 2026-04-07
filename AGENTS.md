# AGENTS Guide for CaliVision

## Project purpose

CaliVision Android is the **mobile runtime/live-coaching app** for the CaliVision ecosystem.

- Primary role: edge-device coaching runtime with phone camera portability.
- Ecosystem pairing: CaliVision-Studio web is the long-term authoring/upload/exchange counterpart.
- Studio repo: https://github.com/Voycepeh/CaliVision-Studio

## Product ownership split (required context)

- **Android owns (primary):** live coaching runtime, package import/consumption, session replay/history, portable in-session UX.
- **Studio owns (source of truth):** full drill authoring, richer drill management, browser-first upload analysis/exchange.
- Existing Android authoring/upload surfaces may remain during transition, but heavy new investment in Android-first authoring should be treated cautiously and generally avoided unless needed for migration compatibility.

## Toolchain requirements

- JDK 17
- Android SDK 34
- Local `gradle` installation (Gradle wrapper is not checked in)

## Main validation commands

```bash
gradle testDebugUnitTest
gradle :app:assembleDebug
```

## Branch and PR rules

- Branch from `main`.
- Target PRs to `main`.
- Keep PRs focused and scoped to one primary concern.

## Architecture and coding guardrails

- Preserve drill-centric flow integrity.
- Keep terminology aligned with current UX labels and the Studio/mobile split.
- Do not silently break drill metadata/catalog/schema behavior.
- Do not silently break replay/export/upload workflows.
- Avoid duplicate entry points or overlapping controls that create ambiguous outcomes.
- Treat portable drill package compatibility with Studio as a critical contract.

## Documentation and diagrams rule (required)

Any PR changing UX flow, navigation, architecture, media pipeline, terminology, package contracts, import behavior, or upload/live ownership boundaries **must** update relevant markdown docs and Mermaid diagrams in the same PR.

When boundaries affect Studio ↔ Android responsibilities, update documentation in both repos where relevant (or explicitly note follow-up docs required in Studio).

## Repo cleanup/doc-update behavior

For cleanup and doc-focused tasks, proactively refresh `README.md`, architecture docs, feature docs, roadmap docs, and diagrams so they match current and target behavior.

## Owner preference

The repo owner prefers PR-ready execution against `main` (implemented changes + PR), not patch-only suggestions.


## Portable package boundary guidance

When touching package import behavior, prefer the explicit seam in `app/src/main/java/com/inversioncoach/app/drillpackage/importing/DrillPackageImportPipeline.kt` and keep parse/validate/map logic out of UI screens where possible.
