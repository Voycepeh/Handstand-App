package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.DrillType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillMetricsCalculatorTest {

    private val calculator = DrillMetricsCalculator()

    @Test
    fun handstandUsesBandedThresholds() {
        val calibration = DrillProfiles.forDrill(DrillType.CHEST_TO_WALL_HANDSTAND, emptyList(), ThresholdStrictness.STANDARD)
        val metrics = derivedMetrics(
            elbowAngle = 176f,
            shoulderAngle = 178f,
            hipAngle = 174f,
            kneeAngle = 176f,
            bodyLineDeviation = 0.05f,
        )

        val subscores = calculator.computeSubscores(DrillType.CHEST_TO_WALL_HANDSTAND, metrics, calibration)

        assertTrue(subscores["elbow_lock"] == 100)
        assertTrue(subscores["leg_tension"] == 100)
        assertTrue(subscores["hip_line"] == 100)
        assertTrue(subscores["line_quality"] == 100)
    }

    @Test
    fun repDrillsArePhaseAwareAndDoNotExposeUniversalElbowPathMetric() {
        val calibration = DrillProfiles.forDrill(DrillType.PUSH_UP, emptyList(), ThresholdStrictness.STANDARD)
        val metrics = derivedMetrics(
            elbowAngle = 98f,
            pathMetrics = mapOf("depth_norm" to 0.6f, "elbow_flare_proxy" to 0.1f, "path_variance" to 0.1f),
            tempoMetrics = mapOf("descent_sec" to 1.0f, "ascent_sec" to 0.7f),
        )

        val subscores = calculator.computeSubscores(DrillType.PUSH_UP, metrics, calibration)

        assertTrue(subscores.containsKey("descent_quality"))
        assertTrue(subscores.containsKey("bottom_depth_quality"))
        assertTrue(subscores.containsKey("ascent_quality"))
        assertTrue(subscores.containsKey("top_lockout_quality"))
        assertTrue(subscores.containsKey("flare_stability_quality"))
        assertFalse(subscores.containsKey("elbow_path"))
    }

    @Test
    fun strictnessPresetShiftsThresholdBands() {
        val beginner = DrillProfiles.forDrill(DrillType.CHEST_TO_WALL_HANDSTAND, emptyList(), ThresholdStrictness.BEGINNER).thresholds
        val advanced = DrillProfiles.forDrill(DrillType.CHEST_TO_WALL_HANDSTAND, emptyList(), ThresholdStrictness.ADVANCED).thresholds

        assertTrue(beginner.elbowExcellentDeg < advanced.elbowExcellentDeg)
        assertTrue(beginner.stackAcceptableNorm > advanced.stackAcceptableNorm)
    }

    private fun derivedMetrics(
        elbowAngle: Float = 170f,
        shoulderAngle: Float = 170f,
        hipAngle: Float = 170f,
        kneeAngle: Float = 170f,
        bodyLineDeviation: Float = 0.1f,
        tempoMetrics: Map<String, Float> = emptyMap(),
        pathMetrics: Map<String, Float> = emptyMap(),
    ): DerivedMetrics = DerivedMetrics(
        timestampMs = 0L,
        jointAngles = mapOf(
            "elbow_angle" to elbowAngle,
            "shoulder_angle_proxy" to shoulderAngle,
            "hip_angle" to hipAngle,
            "knee_angle" to kneeAngle,
        ),
        segmentVerticalDeviation = emptyMap(),
        stackOffsetsNorm = mapOf("shoulder_stack_offset" to 0f, "hip_stack_offset" to 0f, "ankle_stack_offset" to 0f),
        bodyLineDeviationNorm = bodyLineDeviation,
        kneeExtensionScore = 80,
        bananaProxyScore = 25,
        pelvicControlProxyScore = 80,
        shoulderOpennessScore = 80,
        scapularElevationProxyScore = 80,
        tempoMetrics = tempoMetrics,
        pathMetrics = pathMetrics,
        confidenceLevel = ConfidenceLevel.HIGH,
        confidence = 1f,
    )
}
