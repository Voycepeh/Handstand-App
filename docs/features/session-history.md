# Feature: Session History

- Sessions are persisted with metrics, statuses, and media references.
- Sessions persist body-profile attribution metadata (`userProfileId`, `bodyProfileId`, `bodyProfileVersion`, `usedDefaultBodyModel`) for replay/export/history integrity.
- Legacy `userBodyProfileJson` remains migration fallback only and is not the primary runtime source when a `BodyProfileRecord` is present.
- During migration, legacy settings JSON is promoted once into `BodyProfileRecord` for the active user, then legacy JSON is cleared.
- History screen provides access to prior results/replay.
- Replay source selection remains aligned with persisted media truth.
