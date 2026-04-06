# Studio ↔ Android Compatibility Plan

## Goal

Keep CaliVision Android focused on runtime/live coaching while CaliVision-Studio web becomes the primary drill authoring/upload/exchange source.

Studio repo: https://github.com/Voycepeh/CaliVision-Studio

## Product split intent

- **Studio source of truth:** authored drill definitions and browser-based upload workflows.
- **Android runtime client:** package import, live coaching execution, mobile results/history.

## Current incremental compatibility seam

This repo already contains compatibility scaffolding without removing current Android capabilities:

1. Versioned portable contract (`drillpackage/*`).
2. Validators and JSON/file IO helpers.
3. Mappers between Android catalog/runtime records and portable models.
4. Runtime drill model isolation (`drills/runtime/*`) used by runtime workflows.

## What remains unchanged intentionally (transitional)

- Existing seeded drill catalog asset source (`drill_catalog_v1.json`) still loads.
- Existing startup seeding and reconciliation behavior still runs.
- Existing Drill Studio screens and Android editing flows remain available.
- Existing upload/reference flows remain available.
- No network sync/protocol was added yet.

## Interoperability path

1. Studio exports `DrillPackage` JSON.
2. Android imports package JSON and validates via `DrillPackageValidator`.
3. Android maps portable drills into local runtime records/catalog-compatible structures.
4. Live coaching consumes runtime drill definitions.
5. Legacy Android `LEFT`/`RIGHT` camera values are normalized to portable `SIDE`; portable `SIDE` is never re-expanded to artificial left/right during package mapping.

## Compatibility notes

- `SchemaVersion` is explicit and required in package manifest.
- Portable pose joints are canonicalized to avoid source-specific naming drift.
- Portable model keeps extension key/value metadata for additive compatibility.
- Conversion preserves authored portable fields via `portablePayload` bridge metadata when legacy tables cannot directly represent all contract fields.

## Documentation rule for boundary changes

When package contracts, import behavior, or Studio/mobile ownership boundaries change, update docs in this repo and align with Studio documentation.
