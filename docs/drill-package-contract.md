# Drill Package Contract (Android Runtime Consumer)

## Purpose

This document defines the versioned portable drill package contract used for Studio ↔ Android interoperability.

- Android role: **runtime/import consumer**.
- Studio role: **authoring/export source**.
- Studio repo: https://github.com/Voycepeh/CaliVision-Studio

## Contract namespace

Portable contract types live under:

- `app/src/main/java/com/inversioncoach/app/drillpackage/model`
- `app/src/main/java/com/inversioncoach/app/drillpackage/mapping`
- `app/src/main/java/com/inversioncoach/app/drillpackage/validation`
- `app/src/main/java/com/inversioncoach/app/drillpackage/io`

## Camera perspective rule

Portable camera views are perspective-only and neutral:

- `FRONT`
- `SIDE`
- `BACK`

Left/right laterality remains an Android-internal compatibility concern and is not encoded in portable view enums.

## Core models

- `SchemaVersion` (major/minor)
- `DrillManifest`
- `DrillPackage`
- `PortableDrill`
- `PortablePhase`
- `PortablePose`
- `PortableAssetRef`

## Pose contract guarantees

`PortablePose` enforces portability-oriented rules:

- canonical joint names (snake_case)
- normalized 2D coordinates (`x`,`y` in `[0,1]`)
- explicit `viewType` (`FRONT`,`SIDE`,`BACK`)
- optional `visibility` / `confidence`
- order-independent representation via `Map<String, PortableJoint2D>`

## Validation

`DrillPackageValidator` validates:

- required manifest/drill fields
- schema version presence (`major > 0`)
- unique phase ordering per drill
- normalized coordinates and confidence/visibility ranges
- basic asset ref validity (`id`, `type`, `uri`)

## Mapping boundaries

- Catalog authoring model <-> portable contract:
  - `DrillCatalogPortableMapper`
- Existing runtime drill record <-> portable contract:
  - `DrillRecordPortableMapper` (normalizes legacy `LEFT`/`RIGHT` to portable `SIDE`)
- Runtime-only shape from persisted records:
  - `RuntimeDrillMapper`

## IO helpers

- `DrillPackageJsonCodec` provides JSON encode/decode.
- `DrillPackageFileIO` provides local file import/export helpers.
- Remote sync is intentionally out-of-scope for this phase.

## Legacy persistence bridge

Current legacy drill records cannot represent every authored portable field directly. `DrillRecordPortableMapper` preserves non-legacy fields by embedding a `portablePayload` token in cue-config metadata so package round-trips remain explicit and non-lossy.
