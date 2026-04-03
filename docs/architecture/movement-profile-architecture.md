# Movement Profile Architecture

This document summarizes how drill-linked movement profiles are represented and used today.

## Core model

The movement-profile domain lives in `app/src/main/java/com/inversioncoach/app/movementprofile`.

Key model types include:

- `MovementProfile`
- `MovementType`
- `PhaseDefinition`
- `AlignmentRule`
- `HoldRule`
- `RepRule`
- `ReadinessRule`
- `CalibrationProfile`
- `MovementTemplateCandidate`

## Core engines and adapters

Current reusable engines:

- `PoseFrameNormalizer`
- `LandmarkVisibilityEvaluator`
- `JointAngleEngine`
- `MotionPhaseDetector`
- `ReadinessEngine`
- `HoldDetector`
- `RepDetector`
- `AlignmentScorer`
- `MovementFeedbackEngine`

Compatibility bridge:

- `ExistingDrillToProfileAdapter` maps drill definitions to movement-profile contracts.

## Upload/reference analysis integration

Upload analysis pipeline classes:

- `UploadedVideoAnalyzer`
- `UploadedVideoAnalysisCoordinator`
- `UploadedAnalysisRepository` (`FileUploadedAnalysisRepository` implementation)
- `MovementTemplateCandidateGenerator`

Outputs include timeline points, derived metrics, and candidate templates used for drill-linked reference workflows.

## Baseline/template behavior today

- Current seeded drill baselines and v1 templates are authored from product-defined movement analysis and rules.
- The current repo does not implement autonomous end-to-end self-learning drill scoring.
- Reference-template and movement-profile structures are intentionally designed so adaptation can become more data-informed over time as more drill data is captured.

## Contributor note

When movement-profile schema, thresholds, or mapping logic change, update:

- this document,
- `docs/features/video-import.md`,
- `docs/features/reference-training.md`,
- and relevant sequence/class diagrams.
