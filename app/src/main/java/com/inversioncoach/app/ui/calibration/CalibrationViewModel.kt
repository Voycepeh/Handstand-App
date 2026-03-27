package com.inversioncoach.app.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inversioncoach.app.calibration.CalibrationCapture
import com.inversioncoach.app.calibration.CalibrationProfileProvider
import com.inversioncoach.app.calibration.CalibrationReadinessEvaluator
import com.inversioncoach.app.calibration.CalibrationSession
import com.inversioncoach.app.calibration.CalibrationStep
import com.inversioncoach.app.calibration.DrillMovementProfileRepository
import com.inversioncoach.app.calibration.StructuralCalibrationEngine
import com.inversioncoach.app.calibration.hold.HoldTemplateBlender
import com.inversioncoach.app.calibration.hold.HoldTemplateBuilder
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.model.SmoothedPoseFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class CalibrationViewModel(
    private val drillType: DrillType,
    private val calibrationProfileProvider: CalibrationProfileProvider,
    private val drillMovementProfileRepository: DrillMovementProfileRepository,
    private val engine: StructuralCalibrationEngine = StructuralCalibrationEngine(),
    private val holdTemplateBuilder: HoldTemplateBuilder = HoldTemplateBuilder(),
    private val holdTemplateBlender: HoldTemplateBlender = HoldTemplateBlender(),
) : ViewModel() {

    private val steps = listOf(
        CalibrationStep.FRONT_NEUTRAL,
        CalibrationStep.SIDE_NEUTRAL,
        CalibrationStep.ARMS_OVERHEAD,
        CalibrationStep.CONTROLLED_HOLD,
    )

    private val session = CalibrationSession(drillType = drillType)
    private val readinessEvaluator = CalibrationReadinessEvaluator()
    private val _state = MutableStateFlow(CalibrationUiState())
    val state: StateFlow<CalibrationUiState> = _state.asStateFlow()

    private var stepStartedAtMs: Long = 0L
    private val acceptedForStep = mutableListOf<PoseFrame>()
    private var rejectedForStep = 0
    private var previousFrame: PoseFrame? = null

    fun beginCalibration() {
        startStep(0)
    }

    fun onPoseFrame(frame: PoseFrame) {
        val current = _state.value
        if (current.phase != CalibrationPhase.CAPTURING || !current.isCapturing || current.isComplete) return

        val readiness = readinessEvaluator.evaluate(current.currentStep, frame)
        val isStillEnough = isStillEnough(frame)
        val ready = readiness.usable && isStillEnough

        _state.update {
            it.copy(
                latestFrame = frame.toSmoothedPoseFrame(),
                visibleJointCount = readiness.visibleJointCount,
                isReady = ready,
                missingRequiredJoints = readiness.missingRequiredJoints,
                readinessMessage = readinessMessageFor(
                    step = current.currentStep,
                    visibleCount = readiness.visibleJointCount,
                    missingJoints = readiness.missingRequiredJoints,
                    isStillEnough = isStillEnough,
                    isReady = ready,
                ),
            )
        }

        previousFrame = frame
        if (ready) {
            acceptedForStep += frame
            _state.update { it.copy(acceptedFrames = acceptedForStep.size, errorMessage = null) }
            if (acceptedForStep.size >= current.requiredFrames) {
                completeCurrentStep()
            }
        } else {
            rejectedForStep += 1
            _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun completeCurrentStep() {
        val step = _state.value.currentStep
        session.record(
            CalibrationCapture(
                step = step,
                startedAtMs = stepStartedAtMs,
                completedAtMs = System.currentTimeMillis(),
                acceptedFrames = acceptedForStep.toList(),
                rejectedFrameCount = rejectedForStep,
            ),
        )

        val currentIndex = steps.indexOf(step)
        if (currentIndex >= steps.lastIndex) {
            completeCalibration()
            return
        }

        _state.update {
            it.copy(
                stepResultMessage = "Step ${currentIndex + 1} complete.",
                isReady = false,
            )
        }

        viewModelScope.launch {
            delay(700)
            startStep(currentIndex + 1)
        }
    }

    fun completeCalibration() {
        viewModelScope.launch {
            val builtProfile = engine.buildProfile(session)
            if (builtProfile == null) {
                _state.update {
                    it.copy(
                        phase = CalibrationPhase.CAPTURING,
                        isCapturing = true,
                        errorMessage = "Calibration incomplete. Finish all required steps.",
                    )
                }
                return@launch
            }

            val existing = calibrationProfileProvider.resolve(drillType)
            val controlledHoldFrames = session.get(CalibrationStep.CONTROLLED_HOLD)?.acceptedFrames.orEmpty()
            val nextVersion = existing.profileVersion + 1

            val learnedHoldTemplate = holdTemplateBuilder.build(
                drillType = drillType,
                profileVersion = nextVersion,
                bodyProfile = builtProfile,
                frames = controlledHoldFrames,
            )

            val finalHoldTemplate = when {
                existing.holdTemplate != null && learnedHoldTemplate != null ->
                    holdTemplateBlender.blend(existing.holdTemplate, learnedHoldTemplate)

                learnedHoldTemplate != null -> learnedHoldTemplate
                else -> existing.holdTemplate
            }

            val newProfile = existing.copy(
                profileVersion = nextVersion,
                userBodyProfile = builtProfile,
                holdTemplate = finalHoldTemplate,
                updatedAtMs = System.currentTimeMillis(),
            )
            drillMovementProfileRepository.save(newProfile)

            _state.update {
                it.copy(
                    phase = CalibrationPhase.COMPLETED,
                    isCapturing = false,
                    isComplete = true,
                    stepResultMessage = "Calibration saved.",
                    errorMessage = null,
                )
            }
        }
    }

    private fun startStep(index: Int) {
        val step = steps[index]
        val copy = copyFor(step)
        acceptedForStep.clear()
        rejectedForStep = 0
        previousFrame = null
        stepStartedAtMs = System.currentTimeMillis()
        _state.value = _state.value.copy(
            phase = CalibrationPhase.CAPTURING,
            currentStep = step,
            stepIndex = index + 1,
            totalSteps = steps.size,
            title = copy.title,
            instruction = copy.instruction,
            cameraPlacement = copy.cameraPlacement,
            acceptedFrames = 0,
            isReady = false,
            readinessMessage = "Get into position...",
            missingRequiredJoints = emptyList(),
            requiredJointNames = requiredJointsFor(step),
            isCapturing = true,
            isComplete = false,
            stepResultMessage = null,
            errorMessage = null,
        )
    }

    private fun readinessMessageFor(
        step: CalibrationStep,
        visibleCount: Int,
        missingJoints: List<String>,
        isStillEnough: Boolean,
        isReady: Boolean,
    ): String {
        if (visibleCount < 8) return "Move farther back and keep full body in frame."
        if (step == CalibrationStep.SIDE_NEUTRAL && missingJoints.isNotEmpty()) return "Turn sideways to the camera."
        if (step == CalibrationStep.ARMS_OVERHEAD && missingJoints.any { it.contains("wrist") || it.contains("elbow") }) {
            return "Raise arms higher and keep elbows/wrists visible."
        }
        if (!isStillEnough) return "Hold still."
        if (missingJoints.isNotEmpty()) return "Adjust framing to show required joints."
        if (isReady) return "Ready"
        return "Keep steady."
    }

    private fun requiredJointsFor(step: CalibrationStep): List<String> = when (step) {
        CalibrationStep.FRONT_NEUTRAL -> listOf("left_shoulder", "right_shoulder", "left_hip", "right_hip")
        CalibrationStep.SIDE_NEUTRAL -> listOf(
            "left_shoulder",
            "left_hip",
            "left_knee",
            "left_ankle",
            "right_shoulder",
            "right_hip",
            "right_knee",
            "right_ankle",
        )

        CalibrationStep.ARMS_OVERHEAD -> listOf("left_shoulder", "right_shoulder", "left_elbow", "right_elbow", "left_wrist", "right_wrist")
        CalibrationStep.CONTROLLED_HOLD -> listOf("left_wrist", "right_wrist", "left_shoulder", "right_shoulder", "left_hip", "right_hip")
    }

    private fun isStillEnough(frame: PoseFrame): Boolean {
        val previous = previousFrame ?: return true
        val previousNose = previous.joints.firstOrNull { it.name == "nose" }
        val currentNose = frame.joints.firstOrNull { it.name == "nose" }
        if (previousNose == null || currentNose == null) return true
        val movement = abs(previousNose.x - currentNose.x) + abs(previousNose.y - currentNose.y)
        return movement < 0.04f
    }

    private fun PoseFrame.toSmoothedPoseFrame(): SmoothedPoseFrame = SmoothedPoseFrame(
        timestampMs = timestampMs,
        joints = joints,
        confidence = confidence,
        analysisWidth = analysisWidth,
        analysisHeight = analysisHeight,
        analysisRotationDegrees = analysisRotationDegrees,
        mirrored = mirrored,
    )
}
