# Diagram: Core Class Relationships

```mermaid
classDiagram
    class LiveCoachingViewModel
    class UploadVideoViewModel
    class DrillStudioViewModel
    class OverlayFrameRenderer
    class OverlaySkeletonPreview
    class SeededSkeletonPreview
    class UploadedVideoAnalyzer
    class UploadedVideoAnalysisCoordinator
    class RuntimeBodyProfileResolver
    class AnnotatedExportPipeline
    class SessionMediaResolver
    class SessionRepository
    class SessionBlobStorage

    LiveCoachingViewModel --> AnnotatedExportPipeline
    LiveCoachingViewModel --> SessionMediaResolver
    LiveCoachingViewModel --> SessionRepository
    LiveCoachingViewModel --> RuntimeBodyProfileResolver

    UploadVideoViewModel --> UploadedVideoAnalyzer
    UploadVideoViewModel --> UploadedVideoAnalysisCoordinator
    UploadVideoViewModel --> AnnotatedExportPipeline
    UploadVideoViewModel --> SessionMediaResolver
    UploadVideoViewModel --> SessionRepository
    UploadVideoViewModel --> RuntimeBodyProfileResolver

    DrillStudioViewModel --> SessionRepository
    OverlaySkeletonPreview --> OverlayFrameRenderer
    SeededSkeletonPreview --> OverlaySkeletonPreview
    UploadedVideoAnalysisCoordinator --> SessionRepository
    SessionRepository --> SessionBlobStorage
```
