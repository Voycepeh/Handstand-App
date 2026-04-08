# Diagram: UI Workflow Overview

```mermaid
flowchart TD
    HOME[Home / Live Coaching Hub]
    WELCOME[First-launch Welcome Dialog]
    START[Choose Drill]
    WORKSPACE[Drill Runtime Detail\nSession/history focus]
    LIVE[Live Coaching / Live Session]
    OWNERSHIP[Attempt Ownership + Stale Recovery Guard]
    SHORT[Session Too Short]
    RESULTS[Results]
    OVERVIEW[History Overview]
    HISTORY[Session History]

    MANAGE[Drill Packages & Migration\nLegacy tooling]
    STUDIO[Drill Studio\nLegacy authoring]

    UPLOAD[Upload / Reference Training\nTransitional]
    SETTINGS[Settings]

    HOME --> WELCOME
    WELCOME -->|Use recommended settings| HOME
    WELCOME -->|Open recording settings| SETTINGS

    HOME --> START --> LIVE
    START --> WORKSPACE
    WORKSPACE --> LIVE
    LIVE --> OWNERSHIP --> RESULTS
    LIVE --> SHORT --> HOME

    SETTINGS --> MANAGE --> STUDIO --> MANAGE
    STUDIO -->|Export/Import Drill Package| MANAGE
    WORKSPACE --> UPLOAD
    WORKSPACE --> HISTORY

    HOME --> OVERVIEW
    OVERVIEW --> RESULTS
    OVERVIEW --> HISTORY
    HISTORY --> RESULTS

    HOME --> SETTINGS --> HOME
```
