# Mobile Direction Roadmap

This roadmap explains how CaliVision Android evolves from a mixed-capability app into a runtime/live-coaching-first mobile client.

Studio counterpart: https://github.com/Voycepeh/CaliVision-Studio

## Direction statement

Android remains the edge-device execution surface. Studio becomes the primary authoring/upload/exchange surface.

## Transition diagram (current → target)

```mermaid
flowchart LR
    CURRENT[Current Android\nLive + Upload + Authoring (mixed)]
    TRANSITION[Transition Phase\nKeep compatibility + guide users]
    TARGET[Target Android\nImport + Live Runtime + Review]

    CURRENT --> TRANSITION --> TARGET
```

## Current state

- Android provides live coaching runtime.
- Android also still contains upload and drill-authoring surfaces.
- Package compatibility work exists and should keep expanding.

## Target state

- Android emphasizes:
  - package import
  - drill browsing/selection
  - live coaching runtime
  - results/history review
- Studio emphasizes:
  - full drill authoring and lifecycle management
  - browser upload analysis
  - ecosystem-level drill exchange

## User migration posture

- Keep existing Android users unblocked during transition.
- Prefer nudging heavy authoring/upload flows to Studio.
- Keep mobile low-friction for in-session coaching.

## Contributor implications

- New docs/code should reinforce runtime-first Android language.
- Major authoring/upload investments on Android should include explicit transition rationale.
- Changes to package contract/import flows must include doc updates.


## Architecture cleanup focus (near-term)

- Keep portable package contract logic centralized and reviewable.
- Keep runtime drill consumption decoupled from mobile authoring internals.
- Keep upload and mobile authoring surfaces explicitly transitional in docs and naming.
- Keep Studio compatibility fixtures/tests healthy for import seams.
