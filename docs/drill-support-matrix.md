# Drill Support Matrix

Legend:
- **Reachable** = user can select it from current Start Drill UI and run through biomechanics scoring path.
- **Analyzer routing** = drill is supported by current config-driven analyzer construction (`DrillConfigs` + `ConfiguredDrillAnalyzer`).

| DrillType | In enum | Has DrillModeConfig | Analyzer routing | Shown in StartDrillScreen | Reachable | Recommended action |
|---|---|---|---|---|---|---|
| WALL_PUSH_UP | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| INCLINE_OR_KNEE_PUSH_UP | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| BODYWEIGHT_SQUAT | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| REVERSE_LUNGE | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| FOREARM_PLANK | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| GLUTE_BRIDGE | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| STANDARD_PUSH_UP | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| PULL_UP_OR_ASSISTED_PULL_UP | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| PARALLEL_BAR_DIP | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| HANGING_KNEE_RAISE | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| PIKE_PUSH_UP | ✅ | ✅ | ✅ | ✅ | ✅ | Keep |
| HOLLOW_BODY_HOLD | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| WALL_FACING_HANDSTAND_HOLD | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| L_SIT_HOLD | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| BURPEE | ✅ | ❌ | ❌ | ❌ | ❌ | Future / quarantine |
| STANDING_POSTURE_HOLD | ✅ | ❌ | ❌ | ❌ | ❌ | Remove from active path (done) |
| PUSH_UP | ✅ | ✅ | ✅ | ✅ | ✅ | Keep |
| SIT_UP | ✅ | ❌ | ❌ | ❌ | ❌ | Remove from active path (done) |
| CHEST_TO_WALL_HANDSTAND | ✅ | ✅ | ✅ | ✅ | ✅ | Keep |
| BACK_TO_WALL_HANDSTAND | ✅ | ❌ | ❌ | ❌ | ❌ | Merge into config-driven wall parameter when reintroduced |
| ELEVATED_PIKE_PUSH_UP | ✅ | ✅ | ✅ | ✅ | ✅ | Keep |
| NEGATIVE_WALL_HANDSTAND_PUSH_UP | ✅ | ✅ | ✅ | ✅ | ✅ | Keep |
| FREESTANDING_HANDSTAND_FUTURE | ✅ | ✅ | ✅ | ✅ | ✅ | Keep as **experimental placeholder** |

## Notes

- Active drills intentionally preserved:
  - `FREESTANDING_HANDSTAND_FUTURE`
  - `CHEST_TO_WALL_HANDSTAND`
  - `PIKE_PUSH_UP`
  - `ELEVATED_PIKE_PUSH_UP`
  - `PUSH_UP`
  - `NEGATIVE_WALL_HANDSTAND_PUSH_UP`
- Unsupported drills are now explicit unsupported paths instead of silently falling back to standing-posture logic.
