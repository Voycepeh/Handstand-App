# Diagram: UI Workflow Overview

```mermaid
flowchart TD
    HOME[Home / Drill Hub]
    WELCOME[First-launch Welcome Dialog]
    DRILLS[Drills\nBrowse for usage]
    START[Choose / Start Drill]
    WORKSPACE[Drill Workspace\nDrill-level usage hub]
    LIVE[Live Coaching / Live Session]
    OWNERSHIP[Attempt Ownership + Stale Recovery Guard]
    SHORT[Session Too Short]
    RESULTS[Results]
    OVERVIEW[History Overview]
    HISTORY[Session History]

    MANAGE[Manage Drills\nAuthoring/Admin]
    STUDIO[Drill Studio\nCreate/edit drill definitions and templates]

    UPLOAD[Upload / Reference Training]
    SETTINGS[Settings]

    HOME --> WELCOME
    WELCOME -->|Use recommended settings| HOME
    WELCOME -->|Open recording settings| SETTINGS

    HOME --> DRILLS --> START --> WORKSPACE
    WORKSPACE --> LIVE
    LIVE --> OWNERSHIP --> RESULTS
    LIVE --> SHORT --> HOME

    HOME --> MANAGE --> STUDIO --> MANAGE
    STUDIO -->|Export/Import Drill Package| MANAGE
    WORKSPACE --> UPLOAD
    WORKSPACE --> HISTORY

    HOME --> UPLOAD --> OWNERSHIP --> RESULTS
    HOME --> OVERVIEW
    OVERVIEW --> RESULTS
    OVERVIEW --> HISTORY
    HISTORY --> RESULTS

    HOME --> SETTINGS --> HOME
```
