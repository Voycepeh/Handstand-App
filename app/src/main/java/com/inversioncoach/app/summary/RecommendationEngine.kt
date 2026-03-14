package com.inversioncoach.app.summary

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.Recommendation

class RecommendationEngine {
    fun recommend(drill: DrillType, limitingFactor: String): Recommendation {
        val key = limitingFactor.lowercase()
        return when {
            "shoulder" in key -> Recommendation(
                "Chest-to-wall shoulder line holds",
                "Build shoulder openness and active elevation.",
                DrillType.CHEST_TO_WALL_HANDSTAND,
            )

            "banana" in key || "rib" in key -> Recommendation(
                "Hollow body + chest-to-wall line drill",
                "Improve rib/pelvis control and reduce arching.",
                DrillType.CHEST_TO_WALL_HANDSTAND,
            )

            "hip" in key && (drill == DrillType.PIKE_PUSH_UP || drill == DrillType.ELEVATED_PIKE_PUSH_UP) -> Recommendation(
                "Higher pike setup reps",
                "Increase vertical loading and keep hips stacked.",
                DrillType.ELEVATED_PIKE_PUSH_UP,
            )

            "descent" in key || "control" in key -> Recommendation(
                "Slow eccentric wall negatives",
                "Improve tempo and path consistency under load.",
                DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP,
            )

            else -> Recommendation(
                "Repeat same drill with cue focus",
                "Consolidate gains by isolating one cue next set.",
                drill,
            )
        }
    }
}
