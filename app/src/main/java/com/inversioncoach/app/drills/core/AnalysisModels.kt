package com.inversioncoach.app.drills.core

data class DrillFault(
    val code: String,
    val severity: Int,
)

data class DrillCueCandidate(
    val id: String,
    val text: String,
    val severity: Int,
)

data class FrameAnalysis(
    val timestampMs: Long,
    val overallScore: Int,
    val faults: List<DrillFault>,
    val cueCandidate: DrillCueCandidate?,
)

data class SessionDrillResult(
    val bestFrameTimestampMs: Long?,
    val worstFrameTimestampMs: Long?,
    val consistencyScore: Int,
)
