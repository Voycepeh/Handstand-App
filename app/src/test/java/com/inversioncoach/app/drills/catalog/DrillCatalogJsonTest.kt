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
}
