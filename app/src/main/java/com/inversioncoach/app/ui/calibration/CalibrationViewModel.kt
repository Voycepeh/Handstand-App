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
import com.inversioncoach.app.storage.repository.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class CalibrationViewModel(
    private val referenceDrillType: DrillType,
    private val calibrationProfileProvider: CalibrationProfileProvider,
    private val drillMovementProfileRepository: DrillMovementProfileRepository,
    private val repository: SessionRepository,
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

    private val session = CalibrationSession(drillType = referenceDrillType)
    private val readinessEvaluator = CalibrationReadinessEvaluator()
    private val _state = MutableStateFlow(CalibrationUiState())
    val state: StateFlow<CalibrationUiState> = _state.asStateFlow()

    private var stepStartedAtMs: Long = 0L
    private var rejectedForStep = 0
    private var previousFrame: PoseFrame? = null
    private var latestRawFrame: PoseFrame? = null
    private var capturedRawFrame: PoseFrame? = null

    fun beginCalibration() = startStep(0)

    fun onPoseFrame(frame: PoseFrame) {
        val current = _state.value
        if (current.phase != CalibrationPhase.CAPTURING || current.hasCapturedFrame) return

        latestRawFrame = frame
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
                errorMessage = null,
            )
        }

        previousFrame = frame
        if (!ready) rejectedForStep += 1
    }

    fun captureStep() {
        val current = _state.value
        if (current.phase != CalibrationPhase.CAPTURING) return
        if (!current.isReady) {
            _state.update { it.copy(errorMessage = "Not ready yet. Adjust position first.") }
            return
        }
        val frame = latestRawFrame ?: run {
            _state.update { it.copy(errorMessage = "No frame available yet. Please wait for camera preview.") }
            return
        }
        capturedRawFrame = frame
        _state.update {
            it.copy(
                capturedFrame = frame.toSmoothedPoseFrame(),
                hasCapturedFrame = true,
                acceptedFrames = it.requiredFrames,
                stepResultMessage = "Frame captured. Retake or continue.",
                errorMessage = null,
            )
        }
    }

    fun retakeStep() {
        val current = _state.value
        if (current.phase != CalibrationPhase.CAPTURING || !current.hasCapturedFrame) return
        capturedRawFrame = null
        _state.update {
            it.copy(
                capturedFrame = null,
                hasCapturedFrame = false,
                acceptedFrames = 0,
                stepResultMessage = "Retake ready. Hold still and capture again.",
                errorMessage = null,
            )
        }
    }

    fun continueToNextStep() {
        val current = _state.value
        if (current.phase != CalibrationPhase.CAPTURING) return
        if (!current.hasCapturedFrame) {
            _state.update { it.copy(errorMessage = "Capture a frame before continuing.") }
            return
        }
        completeCurrentStep()
    }

    private fun completeCurrentStep() {
        val step = _state.value.currentStep
        val frame = capturedRawFrame ?: return
        session.record(
            CalibrationCapture(
                step = step,
                startedAtMs = stepStartedAtMs,
                completedAtMs = System.currentTimeMillis(),
                acceptedFrames = listOf(frame),
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
                completedSteps = it.completedSteps + step,
                isReady = false,
            )
        }

        viewModelScope.launch {
            delay(350)
            startStep(currentIndex + 1)
        }
    }

    fun completeCalibration() {
        viewModelScope.launch {
            val builtProfile = engine.buildProfile(session)
            if (builtProfile == null) {
                _state.update {
                    it.copy(phase = CalibrationPhase.CAPTURING, errorMessage = "Calibration incomplete. Finish all required steps.")
                }
                return@launch
            }

            val existing = calibrationProfileProvider.resolve(referenceDrillType)
            val controlledHoldFrames = session.get(CalibrationStep.CONTROLLED_HOLD)?.acceptedFrames.orEmpty()
            val nextVersion = existing.profileVersion + 1
            val learnedHoldTemplate = holdTemplateBuilder.build(
                drillType = referenceDrillType,
                profileVersion = nextVersion,
                bodyProfile = builtProfile,
                frames = controlledHoldFrames,
            )
            val finalHoldTemplate = when {
                existing.holdTemplate != null && learnedHoldTemplate != null -> holdTemplateBlender.blend(existing.holdTemplate, learnedHoldTemplate)
                learnedHoldTemplate != null -> learnedHoldTemplate
                else -> existing.holdTemplate
            }

            val updatedAtMs = System.currentTimeMillis()
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
                updatedAtMs = updatedAtMs,
            )
            repository.saveCalibrationForActiveProfile(builtProfile)

            _state.update {
                it.copy(
                    phase = CalibrationPhase.COMPLETED,
                    stepResultMessage = "Calibration saved for active profile.",
                    completedSteps = steps.toSet(),
                    savedProfileSummary = summarizeProfile(builtProfile),
                    savedAtMs = updatedAtMs,
                    errorMessage = null,
                )
            }
        }
    }

    private data class StepCopy(val title: String, val instruction: String, val cameraPlacement: String)

    private fun copyFor(step: CalibrationStep): StepCopy = when (step) {
        CalibrationStep.FRONT_NEUTRAL -> StepCopy("Front Neutral", "Stand facing the camera with arms relaxed.", "Full body visible, camera at hip-to-chest height.")
        CalibrationStep.SIDE_NEUTRAL -> StepCopy("Side Neutral", "Turn sideways while keeping your full body in frame.", "Side profile visible from head to feet.")
        CalibrationStep.ARMS_OVERHEAD -> StepCopy("Arms Overhead", "Raise both arms overhead and keep elbows/wrists visible.", "Keep shoulders, elbows, and wrists fully visible.")
        CalibrationStep.CONTROLLED_HOLD -> StepCopy("Controlled Hold", "Hold a stable handstand entry/hold position.", "Maintain full-body framing while holding steady.")
    }

    private fun startStep(index: Int) {
        val step = steps[index]
        val copy = copyFor(step)
        rejectedForStep = 0
        previousFrame = null
        latestRawFrame = null
        capturedRawFrame = null
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
            requiredFrames = 1,
            isReady = false,
            readinessMessage = "Get into position...",
            missingRequiredJoints = emptyList(),
            requiredJointNames = requiredJointsFor(step),
            latestFrame = null,
            capturedFrame = null,
            hasCapturedFrame = false,
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
        if (step == CalibrationStep.ARMS_OVERHEAD && missingJoints.any { it.contains("wrist") || it.contains("elbow") }) return "Raise arms higher and keep elbows/wrists visible."
        if (!isStillEnough) return "Hold still."
        if (missingJoints.isNotEmpty()) return "Adjust framing to show required joints."
        if (isReady) return "Ready"
        return "Keep steady."
    }

    private fun requiredJointsFor(step: CalibrationStep): List<String> = when (step) {
        CalibrationStep.FRONT_NEUTRAL -> listOf("left_shoulder", "right_shoulder", "left_hip", "right_hip")
        CalibrationStep.SIDE_NEUTRAL -> listOf("left_shoulder", "left_hip", "left_knee", "left_ankle", "right_shoulder", "right_hip", "right_knee", "right_ankle")
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

    private fun summarizeProfile(profile: com.inversioncoach.app.calibration.UserBodyProfile): String {
        val shoulder = (profile.shoulderWidthNormalized * 100).roundToInt()
        val torso = (profile.torsoLengthNormalized * 100).roundToInt()
        val consistency = (profile.leftRightConsistency * 100).roundToInt()
        return "Shoulders $shoulder • Torso $torso • Symmetry $consistency%"
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
