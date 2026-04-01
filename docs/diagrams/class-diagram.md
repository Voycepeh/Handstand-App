# Class Diagram (Key Architecture Classes)

```mermaid
classDiagram
    class LiveCoachingViewModel {
      +startSession()
      +stopSession()
      +onRecordingFinalized(uri)
    }
    class DrillStudioViewModel {
      +loadDrill(id)
      +validateAndSave()
    }
    class SessionRepository {
      +saveSession(record)
      +updateAnnotatedExportStatus(sessionId, status)
    }
    class SessionBlobStorage {
      +saveRawVideoBlob()
      +saveAnnotatedVideoBlob()
    }
    class AnnotatedExportPipeline {
      +export(...)
    }
    class SessionMediaResolver {
      +resolveReplay(...)
    }
    class DrillRegistry {
      +getDrill(...)
    }
    class ReferenceTemplateBuilder {
      +buildFromProfile(...)
    }
    class MlKitVideoPoseFrameSource {
      +open(...)
    }
    class MotionAnalysisPipeline {
      +analyze(...)
    }
    class CalibrationEngine {
      +buildProfile(...)
    }

    LiveCoachingViewModel --> DrillRegistry
    LiveCoachingViewModel --> SessionRepository
    LiveCoachingViewModel --> AnnotatedExportPipeline
    LiveCoachingViewModel --> MotionAnalysisPipeline
    LiveCoachingViewModel --> SessionMediaResolver
    DrillStudioViewModel --> DrillRegistry
    DrillStudioViewModel --> SessionRepository
    SessionRepository --> SessionBlobStorage
    AnnotatedExportPipeline --> SessionMediaResolver
    MlKitVideoPoseFrameSource --> MotionAnalysisPipeline
    ReferenceTemplateBuilder --> MotionAnalysisPipeline
    CalibrationEngine --> MotionAnalysisPipeline
```
