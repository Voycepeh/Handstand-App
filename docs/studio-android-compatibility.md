# Studio ↔ Android Compatibility Plan

## Goal

Prepare CaliVision so future web Studio can be the primary drill authoring source, while Android stays focused on drill runtime and import consumption.

## Current incremental refactor

This change introduces a compatibility seam without removing current Android capabilities:

1. Versioned portable contract (`drillpackage/*`).
2. Validators and JSON/file IO stubs.
3. Mappers between Android catalog/runtime records and portable models.
4. Runtime drill model isolation (`drills/runtime/*`) used by upload runtime flow.

## What remains unchanged intentionally

- Existing seeded drill catalog asset source (`drill_catalog_v1.json`) still loads.
- Existing startup seeding and reconciliation behavior still runs.
- Existing Drill Studio screens and Android editing flows remain available.
- No network sync/protocol was added yet.

## Future web Studio interoperability boundary

Expected path:

1. Web Studio exports `DrillPackage` JSON.
2. Android imports package JSON and validates via `DrillPackageValidator`.
3. Android maps portable drills into local runtime records/catalog-compatible structures.
4. Runtime/live coaching/upload consume `RuntimeDrillDefinition`-style contracts rather than editor-specific assumptions.
5. Legacy Android `LEFT`/`RIGHT` camera values are normalized to portable `SIDE`; portable `SIDE` is never re-expanded to artificial left/right during package mapping.

## Compatibility notes

- `SchemaVersion` is explicit and required in package manifest.
- Portable pose joints are canonicalized to avoid source-specific naming drift, and portable camera perspective is neutralized (no LEFT/RIGHT laterality in portable view).
- Portable model keeps extension key/value metadata for additive compatibility.
- Conversion preserves authored portable fields by storing a portable payload bridge in legacy cue-config metadata when legacy tables cannot represent all contract fields directly.
