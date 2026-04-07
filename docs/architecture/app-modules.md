# App Modules

This file maps current package boundaries to responsibilities.

## UI and navigation

- `ui/navigation`: route definitions (`Route`) and nav host wiring.
- `ui/home`, `ui/startdrill`: drill hub and launch decisions.
- `ui/drills`, `ui/drillstudio`, `ui/reference`: drill management/workspace/authoring.
- `ui/live`: live coaching screen + view model.
- `ui/upload`: upload/reference analysis flow.
- `ui/results`, `ui/history`, `ui/historyoverview`: results plus detailed and overview history experiences.
- `ui/settings`: settings and developer tuning surfaces.


## Portable package boundary (Studio ↔ Android contract)

- `drillpackage/model`: portable contract models and boundary constants.
- `drillpackage/io`: package JSON/file encoding/decoding helpers.
- `drillpackage/validation`: package validation and import safety report types.
- `drillpackage/mapping`: portable↔catalog/runtime mapping + canonical joint semantics.
- `drillpackage/importing`: explicit parse→validate→map import pipeline result seam.

## Domain and analysis

- `drills/**`: drill definitions/catalog/studio models.
- `movementprofile/**`: upload analysis, template candidates, movement-profile engines.
- `pose/**`, `motion/**`, `biomechanics/**`: frame extraction, movement analysis, scoring.

## Media and replay

- `camera/**`: camera and recorder integration.
- `recording/**`: overlay timeline capture, annotated export, telemetry.
- `media/**`: replay selection (`SessionMediaResolver`).
- `overlay/**`: overlay geometry/drawing support.

## Persistence

- `storage/db/**`: Room entities/DAO/migrations.
- `storage/repository/**`: repository boundaries for sessions/settings/media metadata.
- `storage/SessionBlobStorage.kt`: raw/annotated media persistence.
- `storage/ServiceLocator.kt`: app-wide wiring.

## Contributor rule

When package boundaries or responsibilities change, update architecture docs and diagrams in the same PR.
