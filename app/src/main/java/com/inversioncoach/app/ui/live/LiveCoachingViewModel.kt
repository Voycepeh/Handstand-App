package com.inversioncoach.app.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inversioncoach.app.biomechanics.AlignmentMetricsEngine
import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.coaching.CueEngine
import com.inversioncoach.app.model.DrillScore
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.IssueEvent
import com.inversioncoach.app.model.LiveSessionUiState
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.model.SmoothedPoseFrame
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.pose.PoseSmoother
import com.inversioncoach.app.storage.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveCoachingViewModel(
    private val drillType: DrillType,
    private val metricsEngine: AlignmentMetricsEngine,
    private val cueEngine: CueEngine,
    private val repository: SessionRepository,
    private val smoother: PoseSmoother = PoseSmoother(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveSessionUiState(drillType = drillType))
    val uiState: StateFlow<LiveSessionUiState> = _uiState.asStateFlow()

    private val _smoothedFrame = MutableStateFlow<SmoothedPoseFrame?>(null)
    val smoothedFrame: StateFlow<SmoothedPoseFrame?> = _smoothedFrame.asStateFlow()

    private var latestScore: DrillScore = DrillScore(0, emptyMap(), "-", "-")
    private val sessionId = System.currentTimeMillis()

    fun onPoseFrame(frame: PoseFrame, settings: UserSettings) {
        val config = DrillConfigs.byType(drillType)
        val smoothed = smoother.smooth(frame)
        _smoothedFrame.value = smoothed
        if (smoothed.confidence < 0.45f) {
            _uiState.value = _uiState.value.copy(
                confidence = smoothed.confidence,
                warningMessage = "Improve side-view framing, lighting, and full-body visibility.",
                debugMetrics = emptyList(),
                debugAngles = emptyList(),
            )
            return
        }

        val analysis = metricsEngine.analyze(config, smoothed.toPoseFrame())
        latestScore = analysis.score
        val cue = cueEngine.nextCue(
            config = config,
            metrics = analysis.metrics,
            style = settings.cueStyle,
            minSpacingMs = (settings.cueFrequencySeconds * 1000).toLong(),
        )

        _uiState.value = _uiState.value.copy(
            score = analysis.score.overall,
            confidence = smoothed.confidence,
            currentCue = cue?.text ?: _uiState.value.currentCue,
            warningMessage = null,
            showDebugOverlay = settings.debugOverlayEnabled,
            debugMetrics = analysis.metrics,
            debugAngles = analysis.angles,
        )

        viewModelScope.launch {
            repository.saveFrameMetric(
                FrameMetricRecord(
                    sessionId = sessionId,
                    timestampMs = smoothed.timestampMs,
                    confidence = smoothed.confidence,
                    overallScore = analysis.score.overall,
                    limitingFactor = analysis.score.limitingFactor,
                    metricScoresJson = analysis.metrics.joinToString(";") { "${it.key}:${it.score}" },
                    anglesJson = analysis.angles.joinToString(";") { "${it.key}:${"%.1f".format(it.degrees)}" },
                    activeIssue = analysis.fault,
                ),
            )
            if (analysis.fault != null) {
                repository.saveIssueEvent(
                    IssueEvent(
                        sessionId = sessionId,
                        timestampMs = smoothed.timestampMs,
                        issue = analysis.fault,
                        severity = cue?.severity ?: 1,
                    ),
                )
            }
        }
    }

    fun toggleRecording() {
        _uiState.value = _uiState.value.copy(isRecording = !_uiState.value.isRecording)
    }

    fun finalScore(): DrillScore = latestScore

    private fun SmoothedPoseFrame.toPoseFrame(): PoseFrame = PoseFrame(timestampMs, joints, confidence)
}
