package com.inversioncoach.app.movementprofile

import com.inversioncoach.app.model.MovementProfileRecord
import kotlin.math.sqrt

class MovementProfileExtractor {
    fun fromAnalysis(
        profileId: String,
        assetId: String,
        drillId: String,
        extractionVersion: Int,
        analysis: UploadedVideoAnalysisResult,
        createdAtMs: Long,
    ): MovementProfileRecord {
        val phaseTimeline = analysis.phaseTimeline.joinToString("|") { "${it.first}:${it.second}" }
        val features = buildMap {
            val alignment = analysis.overlayTimeline.mapNotNull { it.metrics["alignment_score"] }
            val trunkLean = analysis.overlayTimeline.mapNotNull { it.metrics["trunk_lean"] }
            put("alignment_score_mean", alignment.meanOrZero())
            put("trunk_lean_mean", trunkLean.meanOrZero())
            put("alignment_score_jitter", alignment.stdDev())
            put("trunk_lean_jitter", trunkLean.stdDev())
        }
        return MovementProfileRecord(
            id = profileId,
            assetId = assetId,
            drillId = drillId,
            extractionVersion = extractionVersion,
            poseTimelineJson = phaseTimeline,
            normalizedFeatureJson = features.entries.joinToString("|") { "${it.key}:${it.value}" },
            repSegmentsJson = "",
            holdSegmentsJson = "",
            createdAtMs = createdAtMs,
        )
    }

    fun toSnapshot(record: MovementProfileRecord): StoredProfileSnapshot =
        MovementProfileSnapshotCodec.toSnapshot(record)
}

private fun List<Float>.meanOrZero(): Float = if (isEmpty()) 0f else average().toFloat()
private fun List<Float>.stdDev(): Float {
    if (isEmpty()) return 0f
    val mean = average().toFloat()
    val variance = map { (it - mean) * (it - mean) }.average().toFloat()
    return sqrt(variance)
}
