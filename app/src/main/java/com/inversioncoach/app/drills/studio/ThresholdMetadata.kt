package com.inversioncoach.app.drills.studio

data class ThresholdMetadata(
    val id: String,
    val label: String,
    val unit: String,
    val min: Float,
    val max: Float,
    val step: Float,
)

object DrillStudioThresholdRegistry {
    val all = listOf(
        ThresholdMetadata("stack_line_error_deg", "Stack line error", "deg", 0f, 45f, 0.5f),
        ThresholdMetadata("wrist_shift_ratio", "Wrist shift", "ratio", 0f, 1f, 0.01f),
        ThresholdMetadata("hold_min_seconds", "Minimum hold", "sec", 0f, 60f, 0.5f),
        ThresholdMetadata("depth_ratio", "Depth", "ratio", 0f, 1f, 0.01f),
        ThresholdMetadata("elbow_flare_deg", "Elbow flare", "deg", 0f, 90f, 1f),
        ThresholdMetadata("rep_tempo_lower", "Min tempo", "sec", 0f, 10f, 0.1f),
        ThresholdMetadata("rep_tempo_upper", "Max tempo", "sec", 0f, 20f, 0.1f),
    )

    private val byId = all.associateBy { it.id }

    fun forMetric(id: String): ThresholdMetadata = byId[id]
        ?: ThresholdMetadata(id, id, "custom", 0f, 180f, 0.5f)
}
