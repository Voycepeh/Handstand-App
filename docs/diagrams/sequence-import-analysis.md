# Sequence: Imported Video Analysis

```mermaid
sequenceDiagram
  actor User
  participant UI as Upload UI
  participant COORD as Upload Analysis Coordinator
  participant UPM as UserProfileManager
  participant SRC as MlKitVideoPoseFrameSource
  participant SCORE as Scoring/Analysis Engines
  participant EXP as AnnotatedExportPipeline
  participant REPO as SessionRepository

  User->>UI: Select video
  UI->>COORD: resolve active profile context
  COORD->>UPM: getOrCreateActiveProfile()
  UPM-->>COORD: active user + latest body profile/default
  UI->>COORD: analyze(uri)
  COORD->>SRC: open frame source
  loop frame iteration
    SRC-->>COORD: pose frame
    COORD->>SCORE: analyze frame
  end
  COORD->>EXP: render annotated export
  EXP-->>COORD: success/failure
  COORD->>REPO: persist session + media state (userProfileId/bodyProfileVersion)
  COORD-->>UI: success/failure UI state
```
