# Repository Cleanup Audit (Safe-First)

This audit groups findings by confidence and cleanup safety.

## Safe to delete

- **None in this PR.**
  - Every candidate file reviewed had either direct code references, Gradle/resource wiring, or plausible operational use (e.g., docs/scripts used outside app runtime).

## Safe to update

1. **`README.md` refresh**
   - Bring project overview, setup steps, and repository structure in line with current code.
   - Add architecture-focused UML diagrams (Mermaid class + sequence) using real component names.
2. **Remove unused `ServiceLocator.summaryGenerator()` wiring**
   - The helper and related imports were unused by app call sites and safe to remove without behavioral impact.

## Needs manual review

1. **Legacy-looking parallel packages (`biomechanics/*` vs `summary/*`, and multiple cue/summary engines)**
   - There are similarly named engines across package boundaries.
   - Current references indicate both pathways are still exercised in different analysis flows, so deletion is not safe without deeper functional validation.
2. **Long-form docs in `docs/`**
   - Some docs may be historical/migration-heavy.
   - They are not part of runtime but can still be useful institutional context; deletion requires maintainer intent.
3. **`scripts/download-latest-apk.sh` ownership and release assumptions**
   - Script defaults to a specific repo owner and expects release asset naming conventions.
   - It appears operationally useful, but maintaining default arguments should be confirmed by maintainers.

## Execution policy for this PR

- Execute only **safe-to-update** items.
- Do **not** delete files with ambiguous ownership or uncertain usage.
- Keep behavior unchanged.
