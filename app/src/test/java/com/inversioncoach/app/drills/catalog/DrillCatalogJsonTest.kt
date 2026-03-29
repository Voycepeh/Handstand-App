package com.inversioncoach.app.drills.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillCatalogJsonTest {
    @Test
    fun decode_readsViewAndPhaseWindowFields() {
        val json = """
            {
              "schemaVersion": 1,
              "catalogId": "test",
              "drills": [
                {
                  "id": "d1",
                  "title": "Drill 1",
                  "family": "handstand",
                  "movementType": "hold",
                  "tags": ["a"],
                  "cameraView": "left_profile",
                  "supportedViews": ["left_profile", "right_profile"],
                  "analysisPlane": "sagittal",
                  "comparisonMode": "pose_timeline",
                  "phases": [
                    { "id": "phase_1", "label": "Phase 1", "order": 0, "progressWindow": [0.0, 0.6] }
                  ],
                  "skeletonTemplate": {
                    "id": "s1",
                    "loop": true,
                    "mirroredSupported": true,
                    "framesPerSecond": 15,
                    "keyframes": [
                      { "progress": 0.0, "joints": { "head": [0.5, 0.2] } },
                      { "progress": 1.0, "joints": { "head": [0.5, 0.25] } }
                    ]
                  },
                  "calibration": {
                    "metricThresholds": { "x": 1.0 },
                    "phaseWindows": { "phase_1": [0.0, 0.6] }
                  }
                }
              ]
            }
        """.trimIndent()

        val catalog = DrillCatalogJson.decode(json)
        val drill = catalog.drills.first()
        assertEquals(CameraView.LEFT_PROFILE, drill.cameraView)
        assertEquals(ComparisonMode.POSE_TIMELINE, drill.comparisonMode)
        assertEquals(0.6f, drill.phases.first().progressWindow?.end)
        assertTrue(drill.skeletonTemplate.mirroredSupported)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_rejectsNonPositiveFps() {
        val json = """
            {
              "schemaVersion": 1,
              "catalogId": "bad",
              "drills": [
                {
                  "id": "d1",
                  "title": "Drill 1",
                  "family": "handstand",
                  "movementType": "hold",
                  "tags": ["a"],
                  "cameraView": "left_profile",
                  "supportedViews": ["left_profile"],
                  "analysisPlane": "sagittal",
                  "comparisonMode": "pose_timeline",
                  "phases": [
                    { "id": "phase_1", "label": "Phase 1", "order": 0 }
                  ],
                  "skeletonTemplate": {
                    "id": "s1",
                    "loop": true,
                    "framesPerSecond": 0,
                    "keyframes": [
                      { "progress": 0.0, "joints": { "head": [0.5, 0.2] } },
                      { "progress": 1.0, "joints": { "head": [0.5, 0.25] } }
                    ]
                  },
                  "calibration": {
                    "metricThresholds": { "x": 1.0 },
                    "phaseWindows": { "phase_1": [0.0, 0.6] }
                  }
                }
              ]
            }
        """.trimIndent()

        DrillCatalogJson.decode(json)
    }

    @Test
    fun encode_generatesKeyframesFromPhasePoses_forCompatibility() {
        val catalog = DrillCatalog(
            schemaVersion = 1,
            catalogId = "test",
            drills = listOf(
                DrillTemplate(
                    id = "d1",
                    title = "Drill 1",
                    family = "handstand",
                    movementType = CatalogMovementType.HOLD,
                    tags = listOf("a"),
                    cameraView = CameraView.LEFT_PROFILE,
                    supportedViews = listOf(CameraView.LEFT_PROFILE),
                    analysisPlane = AnalysisPlane.SAGITTAL,
                    comparisonMode = ComparisonMode.POSE_TIMELINE,
                    phases = listOf(DrillPhaseTemplate("phase_1", "Start", 1), DrillPhaseTemplate("phase_2", "End", 2)),
                    skeletonTemplate = SkeletonTemplate(
                        id = "s1",
                        loop = true,
                        framesPerSecond = 24,
                        phasePoses = listOf(
                            PhasePoseTemplate("phase_1", "Start", mapOf("head" to JointPoint(0.5f, 0.2f))),
                            PhasePoseTemplate("phase_2", "End", mapOf("head" to JointPoint(0.5f, 0.3f))),
                        ),
                        keyframes = emptyList(),
                    ),
                    calibration = CalibrationTemplate(metricThresholds = emptyMap(), phaseWindows = emptyMap()),
                ),
            ),
        )

        val encoded = DrillCatalogJson.encode(catalog)
        val decoded = DrillCatalogJson.decode(encoded)

        assertTrue(encoded.contains("\"keyframes\""))
        assertTrue(decoded.drills.first().skeletonTemplate.keyframes.size >= 2)
        assertTrue(decoded.drills.first().skeletonTemplate.keyframes.first().joints.containsKey("head"))
    }

    @Test
    fun decode_normalizesJointAliases_inPhasePosesAndKeyframes() {
        val json = """
            {
              "schemaVersion": 1,
              "catalogId": "test",
              "drills": [
                {
                  "id": "d1",
                  "title": "Drill 1",
                  "family": "handstand",
                  "movementType": "hold",
                  "tags": ["a"],
                  "cameraView": "left_profile",
                  "supportedViews": ["left_profile"],
                  "analysisPlane": "sagittal",
                  "comparisonMode": "pose_timeline",
                  "phases": [{ "id": "phase_1", "label": "Start", "order": 1 }],
                  "skeletonTemplate": {
                    "id": "s1",
                    "loop": false,
                    "framesPerSecond": 24,
                    "phasePoses": [{
                      "phaseId": "phase_1",
                      "name": "Start",
                      "joints": { "nose": [0.5, 0.2], "left_shoulder": [0.4, 0.3] }
                    }],
                    "keyframes": [{
                      "progress": 0.0,
                      "joints": { "nose": [0.5, 0.2], "left_shoulder": [0.4, 0.3] }
                    }]
                  },
                  "calibration": { "metricThresholds": {}, "phaseWindows": {} }
                }
              ]
            }
        """.trimIndent()
        val decoded = DrillCatalogJson.decode(json)
        val poseJoints = decoded.drills.first().skeletonTemplate.phasePoses.first().joints
        val keyframeJoints = decoded.drills.first().skeletonTemplate.keyframes.first().joints
        assertTrue(poseJoints.containsKey("head"))
        assertTrue(poseJoints.containsKey("shoulder_left"))
        assertTrue(keyframeJoints.containsKey("head"))
    }
}
