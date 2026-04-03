# Feature: Reference Training

Reference Training turns strong attempts into drill-linked comparison baselines.

## Core flow

1. Start from Upload / Reference Training (or selected prior result).
2. Analyze the clip/session and compute movement/scoring signals.
3. Associate analyzed result with a target drill.
4. Create or update reference template/baseline.
5. Use the reference when comparing later attempts for the same drill.

## Association rules

- References should be drill-linked so comparisons remain contextually correct.
- Reference creation should not require users to understand internal pipeline details.
- Comparison UI should clearly indicate which reference is active.

## Persistence expectations

- Persist source/media state and derived reference metadata.
- Seeded baseline/reference templates are catalog-derived from `drill_catalog_v1.json`, which is the canonical seeded source.
- Keep reference creation optional; analysis should still be useful without saving a template.
- Maintain compatibility with replay/export resolution and history surfaces.
