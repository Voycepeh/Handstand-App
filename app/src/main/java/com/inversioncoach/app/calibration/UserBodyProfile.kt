package com.inversioncoach.app.calibration

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

data class CaptureStepSummary(
    val stepType: String,
    val acceptedFrames: Int,
    val qualityScore: Float,
    val stabilityScore: Float,
    val notes: String = "",
)

data class SegmentRatios(
    val shoulderToTorso: Float,
    val hipToShoulder: Float,
    val upperArmToTorso: Float,
    val forearmToUpperArm: Float,
    val thighToTorso: Float,
    val shinToThigh: Float,
    val armToTorso: Float,
    val legToTorso: Float,
)

data class SymmetryMetrics(
    val armSymmetry: Float,
    val legSymmetry: Float,
    val shoulderLevelBaseline: Float,
    val hipLevelBaseline: Float,
)

data class UserBodyProfile(
    val id: String = "bp_${UUID.randomUUID()}",
    val name: String = "Body Profile",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val isActive: Boolean = true,
    val overallQuality: Float = 0f,
    val frontConfidence: Float = 0f,
    val sideConfidence: Float = 0f,
    val overheadConfidence: Float = 0f,
    val captureVersion: Int = 1,
    val segmentRatios: SegmentRatios,
    val symmetryMetrics: SymmetryMetrics,
    val stepSummaries: List<CaptureStepSummary> = emptyList(),
) {
    /**
     * Contract: all segment values are normalized in torso units.
     * `torsoLengthNormalized` intentionally resolves to 1f as the base unit.
     */
    constructor(
        version: Int = 1,
        shoulderWidthNormalized: Float,
        hipWidthNormalized: Float,
        torsoLengthNormalized: Float,
        upperArmLengthNormalized: Float,
        forearmLengthNormalized: Float,
        femurLengthNormalized: Float,
        shinLengthNormalized: Float,
        leftRightConsistency: Float,
    ) : this(
        captureVersion = version,
        segmentRatios = SegmentRatios(
            shoulderToTorso = shoulderWidthNormalized,
            hipToShoulder = hipWidthNormalized / shoulderWidthNormalized.coerceAtLeast(0.0001f),
            upperArmToTorso = upperArmLengthNormalized / torsoLengthNormalized.coerceAtLeast(0.0001f),
            forearmToUpperArm = forearmLengthNormalized / upperArmLengthNormalized.coerceAtLeast(0.0001f),
            thighToTorso = femurLengthNormalized / torsoLengthNormalized.coerceAtLeast(0.0001f),
            shinToThigh = shinLengthNormalized / femurLengthNormalized.coerceAtLeast(0.0001f),
            armToTorso = (upperArmLengthNormalized + forearmLengthNormalized) / torsoLengthNormalized.coerceAtLeast(0.0001f),
            legToTorso = (femurLengthNormalized + shinLengthNormalized) / torsoLengthNormalized.coerceAtLeast(0.0001f),
        ),
        symmetryMetrics = SymmetryMetrics(
            armSymmetry = leftRightConsistency,
            legSymmetry = leftRightConsistency,
            shoulderLevelBaseline = 0f,
            hipLevelBaseline = 0f,
        ),
    )

    // Backward-compatible aliases consumed by existing drill/overlay logic.
    val shoulderWidthNormalized: Float get() = segmentRatios.shoulderToTorso
    val hipWidthNormalized: Float get() = segmentRatios.hipToShoulder * segmentRatios.shoulderToTorso
    val torsoLengthNormalized: Float get() = 1f
    val upperArmLengthNormalized: Float get() = segmentRatios.upperArmToTorso
    val forearmLengthNormalized: Float get() = segmentRatios.forearmToUpperArm * segmentRatios.upperArmToTorso
    val femurLengthNormalized: Float get() = segmentRatios.thighToTorso
    val shinLengthNormalized: Float get() = segmentRatios.shinToThigh * segmentRatios.thighToTorso
    val leftRightConsistency: Float get() = ((symmetryMetrics.armSymmetry + symmetryMetrics.legSymmetry) / 2f).coerceIn(0f, 1f)

    fun encode(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("isActive", isActive)
        put("overallQuality", overallQuality)
        put("frontConfidence", frontConfidence)
        put("sideConfidence", sideConfidence)
        put("overheadConfidence", overheadConfidence)
        put("captureVersion", captureVersion)
        put("segmentRatios", JSONObject().apply {
            put("shoulderToTorso", segmentRatios.shoulderToTorso)
            put("hipToShoulder", segmentRatios.hipToShoulder)
            put("upperArmToTorso", segmentRatios.upperArmToTorso)
            put("forearmToUpperArm", segmentRatios.forearmToUpperArm)
            put("thighToTorso", segmentRatios.thighToTorso)
            put("shinToThigh", segmentRatios.shinToThigh)
            put("armToTorso", segmentRatios.armToTorso)
            put("legToTorso", segmentRatios.legToTorso)
        })
        put("symmetryMetrics", JSONObject().apply {
            put("armSymmetry", symmetryMetrics.armSymmetry)
            put("legSymmetry", symmetryMetrics.legSymmetry)
            put("shoulderLevelBaseline", symmetryMetrics.shoulderLevelBaseline)
            put("hipLevelBaseline", symmetryMetrics.hipLevelBaseline)
        })
        put("stepSummaries", JSONArray().apply {
            stepSummaries.forEach { step ->
                put(JSONObject().apply {
                    put("stepType", step.stepType)
                    put("acceptedFrames", step.acceptedFrames)
                    put("qualityScore", step.qualityScore)
                    put("stabilityScore", step.stabilityScore)
                    put("notes", step.notes)
                })
            }
        })
    }.toString()

    companion object {
        fun normalize(profile: UserBodyProfile?): UserBodyProfile? {
            val source = profile ?: return null
            val ratios = source.segmentRatios
            val symmetry = source.symmetryMetrics
            if (!ratios.isValid()) return null
            val shoulderToTorso = ratios.shoulderToTorso.coerceAtLeast(0.0001f)
            val upperArmToTorso = ratios.upperArmToTorso.coerceAtLeast(0.0001f)
            val thighToTorso = ratios.thighToTorso.coerceAtLeast(0.0001f)
            val forearmToUpperArm = ratios.forearmToUpperArm.coerceAtLeast(0.0001f)
            val shinToThigh = ratios.shinToThigh.coerceAtLeast(0.0001f)
            return source.copy(
                overallQuality = source.overallQuality.coerceIn(0f, 1f),
                frontConfidence = source.frontConfidence.coerceIn(0f, 1f),
                sideConfidence = source.sideConfidence.coerceIn(0f, 1f),
                overheadConfidence = source.overheadConfidence.coerceIn(0f, 1f),
                segmentRatios = SegmentRatios(
                    shoulderToTorso = shoulderToTorso,
                    hipToShoulder = ratios.hipToShoulder.coerceAtLeast(0.0001f),
                    upperArmToTorso = upperArmToTorso,
                    forearmToUpperArm = forearmToUpperArm,
                    thighToTorso = thighToTorso,
                    shinToThigh = shinToThigh,
                    armToTorso = (upperArmToTorso + (forearmToUpperArm * upperArmToTorso)).coerceAtLeast(0.0001f),
                    legToTorso = (thighToTorso + (shinToThigh * thighToTorso)).coerceAtLeast(0.0001f),
                ),
                symmetryMetrics = SymmetryMetrics(
                    armSymmetry = symmetry.armSymmetry.coerceIn(0f, 1f),
                    legSymmetry = symmetry.legSymmetry.coerceIn(0f, 1f),
                    shoulderLevelBaseline = symmetry.shoulderLevelBaseline.sanitizeFinite(),
                    hipLevelBaseline = symmetry.hipLevelBaseline.sanitizeFinite(),
                ),
            )
        }

        fun decode(raw: String?): UserBodyProfile? {
            if (raw.isNullOrBlank()) return null
            if (!raw.trimStart().startsWith("{")) return decodeLegacy(raw)
            return runCatching {
                val obj = JSONObject(raw)
                val segment = obj.optJSONObject("segmentRatios") ?: return@runCatching null
                val symmetry = obj.optJSONObject("symmetryMetrics") ?: return@runCatching null
                val steps = obj.optJSONArray("stepSummaries") ?: JSONArray()
                val hasRequiredRatios = listOf(
                    "shoulderToTorso",
                    "hipToShoulder",
                    "upperArmToTorso",
                    "forearmToUpperArm",
                    "thighToTorso",
                    "shinToThigh",
                    "armToTorso",
                    "legToTorso",
                ).all { segment.has(it) }
                if (!hasRequiredRatios) return@runCatching null
                normalize(UserBodyProfile(
                    id = obj.optString("id").takeIf { it.isNotBlank() } ?: "bp_${UUID.randomUUID()}",
                    name = obj.optString("name", "Body Profile"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    isActive = obj.optBoolean("isActive", true),
                    overallQuality = obj.optDouble("overallQuality", 0.0).toFloat(),
                    frontConfidence = obj.optDouble("frontConfidence", 0.0).toFloat(),
                    sideConfidence = obj.optDouble("sideConfidence", 0.0).toFloat(),
                    overheadConfidence = obj.optDouble("overheadConfidence", 0.0).toFloat(),
                    captureVersion = obj.optInt("captureVersion", 1),
                    segmentRatios = SegmentRatios(
                        shoulderToTorso = segment.getDouble("shoulderToTorso").toFloat(),
                        hipToShoulder = segment.getDouble("hipToShoulder").toFloat(),
                        upperArmToTorso = segment.getDouble("upperArmToTorso").toFloat(),
                        forearmToUpperArm = segment.getDouble("forearmToUpperArm").toFloat(),
                        thighToTorso = segment.getDouble("thighToTorso").toFloat(),
                        shinToThigh = segment.getDouble("shinToThigh").toFloat(),
                        armToTorso = segment.getDouble("armToTorso").toFloat(),
                        legToTorso = segment.getDouble("legToTorso").toFloat(),
                    ),
                    symmetryMetrics = SymmetryMetrics(
                        armSymmetry = symmetry.getDouble("armSymmetry").toFloat(),
                        legSymmetry = symmetry.getDouble("legSymmetry").toFloat(),
                        shoulderLevelBaseline = symmetry.getDouble("shoulderLevelBaseline").toFloat(),
                        hipLevelBaseline = symmetry.getDouble("hipLevelBaseline").toFloat(),
                    ),
                    stepSummaries = buildList {
                        for (i in 0 until steps.length()) {
                            val s = steps.optJSONObject(i) ?: continue
                            add(
                                CaptureStepSummary(
                                    stepType = s.optString("stepType", ""),
                                    acceptedFrames = s.optInt("acceptedFrames", 0),
                                    qualityScore = s.optDouble("qualityScore", 0.0).toFloat(),
                                    stabilityScore = s.optDouble("stabilityScore", 0.0).toFloat(),
                                    notes = s.optString("notes", ""),
                                ),
                            )
                        }
                    },
                ))
            }.getOrNull()
        }

        private fun decodeLegacy(raw: String): UserBodyProfile? {
            val parts = raw.split('|')
            if (parts.size != 9) return null
            return runCatching {
                val shoulder = parts[1].toFloat()
                val hip = parts[2].toFloat()
                val torso = parts[3].toFloat().coerceAtLeast(0.0001f)
                val upper = parts[4].toFloat()
                val forearm = parts[5].toFloat()
                val thigh = parts[6].toFloat()
                val shin = parts[7].toFloat()
                val symmetry = parts[8].toFloat()
                normalize(UserBodyProfile(
                    captureVersion = parts[0].toInt(),
                    segmentRatios = SegmentRatios(
                        shoulderToTorso = shoulder,
                        hipToShoulder = (hip / shoulder.coerceAtLeast(0.0001f)),
                        upperArmToTorso = upper / torso,
                        forearmToUpperArm = forearm / upper.coerceAtLeast(0.0001f),
                        thighToTorso = thigh / torso,
                        shinToThigh = shin / thigh.coerceAtLeast(0.0001f),
                        armToTorso = (upper + forearm) / torso,
                        legToTorso = (thigh + shin) / torso,
                    ),
                    symmetryMetrics = SymmetryMetrics(
                        armSymmetry = symmetry,
                        legSymmetry = symmetry,
                        shoulderLevelBaseline = 0f,
                        hipLevelBaseline = 0f,
                    ),
                ))
            }.getOrNull()
        }

        private fun SegmentRatios.isValid(): Boolean = listOf(
            shoulderToTorso,
            hipToShoulder,
            upperArmToTorso,
            forearmToUpperArm,
            thighToTorso,
            shinToThigh,
            armToTorso,
            legToTorso,
        ).all { it.isFinite() && it > 0f && abs(it) < 100f }

        private fun Float.sanitizeFinite(): Float = if (isFinite()) this else 0f
    }
}
