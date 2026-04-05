# Diagram: Architecture Subsystems

```mermaid
flowchart LR
    UI[UI Screens + Route Nav]
    LIVE[LiveCoachingViewModel]
    UPLOAD[UploadVideoViewModel]
    UCOORD[ActiveUploadCoordinator]
    UWORK[UploadVideoProcessingWorker]
    WMAN[WorkManager]
    STUDIO[DrillStudioViewModel]
    DRILLPKG[drillpackage/* contract + mapping]
    RUNTIME[drills/runtime/*]
    DRILLS[drills/* domain]
    MOVE[movementprofile/* domain]
    POSEML[On-device Pose ML + landmarks]
    ANALYSIS[motion + biomechanics + drill scoring]
    RECORD[recording/* export pipeline]
    MEDIA[SessionMediaResolver]
    REPO[SessionRepository]
    DB[(Room DB)]
    BLOB[(SessionBlobStorage)]

    UI --> LIVE
    UI --> UPLOAD
    UPLOAD --> UCOORD
    UCOORD --> WMAN --> UWORK
    UI --> STUDIO

    LIVE --> POSEML --> ANALYSIS
    LIVE --> RECORD

    UWORK --> MOVE
    UWORK --> POSEML --> ANALYSIS
    UWORK --> RECORD

    STUDIO --> DRILLS
    STUDIO --> DRILLPKG
    DRILLPKG --> REPO
    REPO --> RUNTIME

    DRILLS --> REPO
    MOVE --> REPO
    LIVE --> REPO
    UPLOAD --> REPO
    UCOORD --> REPO
    UWORK --> REPO

    RECORD --> MEDIA --> REPO

    REPO --> DB
    REPO --> BLOB
```
