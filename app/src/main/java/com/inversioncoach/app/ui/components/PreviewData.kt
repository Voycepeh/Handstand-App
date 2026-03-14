package com.inversioncoach.app.ui.components

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionUiState
import com.inversioncoach.app.model.SessionRecord

object PreviewData {
    val sampleLive = LiveSessionUiState(
        drillType = DrillType.CHEST_TO_WALL_HANDSTAND,
        score = 82,
        currentCue = "Push taller through shoulders",
        confidence = 0.91f,
        holdSeconds = 24,
        isRecording = true,
    )

    val sampleSessions = listOf(
        SessionRecord(
            id = 1,
            title = "Wall line holds",
            drillType = DrillType.CHEST_TO_WALL_HANDSTAND,
            startedAtMs = 0,
            completedAtMs = 0,
            overallScore = 79,
            strongestArea = "shoulder_openness",
            limitingFactor = "rib_pelvis_control",
            issues = "ribs flaring",
            wins = "active shoulders",
            metricsJson = "{}",
            annotatedVideoUri = null,
            rawVideoUri = null,
            notesUri = null,
            bestFrameTimestampMs = 20000,
            worstFrameTimestampMs = 47000,
            topImprovementFocus = "rib and pelvis control",
        ),
    )
}
