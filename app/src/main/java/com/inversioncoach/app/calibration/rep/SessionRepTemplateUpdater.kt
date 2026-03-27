package com.inversioncoach.app.calibration.rep

import com.inversioncoach.app.calibration.DrillMovementProfile
import com.inversioncoach.app.calibration.RepTemplateSource

class SessionRepTemplateUpdater(
    private val windowSelector: RepWindowSelector = RepWindowSelector(),
    private val templateBuilder: RepTemplateBuilder = RepTemplateBuilder(),
    private val templateBlender: RepTemplateBlender = RepTemplateBlender(),
) {
    fun updateProfile(
        profile: DrillMovementProfile,
        repWindows: List<List<com.inversioncoach.app.model.PoseFrame>>,
        minimumRepCount: Int = 2,
        minimumFramesPerRep: Int = 10,
        learnedWeight: Float = 0.3f,
        nowMs: Long = System.currentTimeMillis(),
    ): DrillMovementProfile? {
        val cleanReps = windowSelector.selectCleanReps(repWindows, minimumFrames = minimumFramesPerRep)
        if (cleanReps.size < minimumRepCount) return null

        val learned = templateBuilder.build(
            drillType = profile.drillType,
            profileVersion = profile.profileVersion,
            reps = cleanReps,
        ) ?: return null

        val merged = profile.repTemplate?.let { baseline ->
            templateBlender.blend(baseline = baseline, learned = learned, learnedWeight = learnedWeight)
        } ?: learned.copy(source = RepTemplateSource.CLEAN_REP_CAPTURE)

        return profile.copy(
            repTemplate = merged,
            updatedAtMs = nowMs,
        )
    }
}
