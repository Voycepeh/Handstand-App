# Diagram: Architecture Subsystems

```mermaid
flowchart LR
    UI[UI Screens + Route Nav]
    LIVE[LiveCoachingViewModel]
    UPLOAD[UploadVideoViewModel]
    STUDIO[DrillStudioViewModel]
    DRILLS[drills/* domain]
    MOVE[movementprofile/* domain]
    CAL[calibration/* domain]
    POSEML[On-device Pose ML + landmarks]
    ANALYSIS[motion + biomechanics + drill scoring]
    RECORD[recording/* export pipeline]
    MEDIA[SessionMediaResolver]
    REPO[SessionRepository]
    DB[(Room DB)]
    BLOB[(SessionBlobStorage)]

    UI --> LIVE
    UI --> UPLOAD
    UI --> STUDIO

    LIVE --> POSEML --> ANALYSIS
    LIVE --> CAL
    LIVE --> RECORD

    UPLOAD --> MOVE
    UPLOAD --> POSEML --> ANALYSIS
    UPLOAD --> RECORD
    UPLOAD --> CAL

    STUDIO --> DRILLS

    DRILLS --> REPO
    MOVE --> REPO
    CAL --> REPO
    LIVE --> REPO
    UPLOAD --> REPO

    RECORD --> MEDIA --> REPO

    REPO --> DB
    REPO --> BLOB
```
