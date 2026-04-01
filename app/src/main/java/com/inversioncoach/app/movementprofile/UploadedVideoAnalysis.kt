package com.inversioncoach.app.movementprofile

import android.net.Uri
import android.util.Log
import com.inversioncoach.app.calibration.DrillMovementProfile
import com.inversioncoach.app.model.PoseFrame
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlin.math.hypot
import kotlin.math.max

private const val UPLOAD_ANALYSIS_TAG = "UploadAnalysis"

data class OverlayTimelinePoint(
    val timestampMs: Long,
    val landmarks: List<Pair<String, Pair<Float, Float>>>,
    val metrics: Map<String, Float>,
    val phaseId: String?,
    val confidence: Float,
) : Serializable

data class UploadedMovementSession(
    val id: String,
    val sourceVideoUri: String,
    val movementProfileId: String?,
    val inferredCameraView: CameraViewConstraint,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val frameCount: Int,
    val droppedFrames: Int,
    val phaseTimeline: List<Pair<Long, String>>,
    val overlayTimeline: List<OverlayTimelinePoint>,
    val derivedMetrics: Map<String, Float>,
    val telemetry: Map<String, Long>,
    val templateCandidate: MovementTemplateCandidate,
) : Serializable

interface UploadedAnalysisRepository {
    fun save(session: UploadedMovementSession)
    fun get(sessionId: String): UploadedMovementSession?
}

class FileUploadedAnalysisRepository(private val rootDir: File) : UploadedAnalysisRepository {
    init {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    override fun save(session: UploadedMovementSession) {
        ObjectOutputStream(File(rootDir, "${session.id}.bin").outputStream()).use { it.writeObject(session) }
    }

    override fun get(sessionId: String): UploadedMovementSession? {
        val file = File(rootDir, "$sessionId.bin")
        if (!file.exists()) return null
        return ObjectInputStream(file.inputStream()).use { it.readObject() as UploadedMovementSession }
    }
}

interface VideoPoseFrameSource {
    fun decode(videoUri: Uri): Sequence<PoseFrame>
    fun decode(videoUri: Uri, observer: AnalysisProgressObserver): Sequence<PoseFrame> = decode(videoUri)
}

data class AnalysisProgressEvent(
    val stage: String,
    val processedFrames: Int = 0,
    val estimatedTotalFrames: Int? = null,
    val droppedFrames: Int = 0,
    val timestampMs: Long? = null,
    val detail: String? = null,
)

fun interface AnalysisProgressObserver {
    fun onProgress(event: AnalysisProgressEvent)
}

class UploadedVideoAnalyzer(
    private val frameSource: VideoPoseFrameSource,
    private val poseFrameNormalizer: PoseFrameNormalizer = PoseFrameNormalizer(),
    private val angleEngine: JointAngleEngine = JointAngleEngine(),
    private val phaseDetectorFactory: (MovementProfile) -> MotionPhaseDetector = { MotionPhaseDetector(it) },
    private val alignmentScorer: AlignmentScorer = AlignmentScorer(),
) {
    fun analyze(
        videoUri: Uri,
        profile: MovementProfile,
        drillMovementProfile: DrillMovementProfile? = null,
        progressObserver: AnalysisProgressObserver? = null,
    ): UploadedVideoAnalysisResult {
        try {
            val poseQualityGate = UploadedPoseQualityGate()
            val decodeStart = System.currentTimeMillis()
            Log.i(UPLOAD_ANALYSIS_TAG, "analysis_loop_start uri=$videoUri")
            progressObserver?.onProgress(AnalysisProgressEvent(stage = "decode_start", detail = "Sampling uploaded video frames"))
            val sourceFrames = frameSource.decode(
                videoUri = videoUri,
                observer = AnalysisProgressObserver { progressEvent ->
                    progressObserver?.onProgress(progressEvent)
                },
            ).toList()
            val decodeDuration = System.currentTimeMillis() - decodeStart

            val analysisStart = System.currentTimeMillis()
            val calibrationProfileVersion = drillMovementProfile?.profileVersion
            val phaseDetector = phaseDetectorFactory(profile)
            val timeline = mutableListOf<OverlayTimelinePoint>()
            val phaseTimeline = mutableListOf<Pair<Long, String>>()
            var dropped = 0
            progressObserver?.onProgress(
                AnalysisProgressEvent(
                    stage = "analysis_started",
                    processedFrames = 0,
                    estimatedTotalFrames = sourceFrames.size,
                    droppedFrames = 0,
                    detail = "Starting post-processing on decoded frames",
                )
            )
            sourceFrames.forEachIndexed { index, frame ->
                if (index % 2 == 0) {
                    Log.i(
                        UPLOAD_ANALYSIS_TAG,
                        "analysis_sample frameIndex=$index total=${sourceFrames.size} timestampMs=${frame.timestampMs} dropped=$dropped",
                    )
                }
                val gateDecision = poseQualityGate.evaluate(frame)
                if (frame.confidence <= 0f || frame.joints.isEmpty() || !gateDecision.acceptForOverlay) {
                    if (!gateDecision.acceptForOverlay) {
                        Log.w(
                            UPLOAD_ANALYSIS_TAG,
                            "analysis_frame_rejected frameIndex=$index timestampMs=${frame.timestampMs} reason=${gateDecision.reason}",
                        )
                    }
                    dropped += 1
                    progressObserver?.onProgress(
                        AnalysisProgressEvent(
                            stage = "analysis_frame_dropped",
                            processedFrames = index + 1,
                            estimatedTotalFrames = sourceFrames.size,
                            droppedFrames = dropped,
                            timestampMs = frame.timestampMs,
                        ),
                    )
                } else {
                    val normalized = poseFrameNormalizer.normalize(frame)
                    val angleFrame = angleEngine.compute(normalized)
                    val alignment = alignmentScorer.score(normalized, profile.alignmentRules)
                    val phase = phaseDetector.update(angleFrame, alignment >= 0.65f)
                    phaseTimeline += frame.timestampMs to phase.name
                    timeline += OverlayTimelinePoint(
                        timestampMs = frame.timestampMs,
                        // Overlay rendering must stay in canonical source-frame normalized space.
                        // The normalized pose frame is only for movement metrics/phase scoring.
                        landmarks = frame.joints.map { it.name to (it.x to it.y) },
                        metrics = mapOf("alignment_score" to alignment, "trunk_lean" to angleFrame.trunkLeanDeg),
                        phaseId = phase.name,
                        confidence = frame.confidence,
                    )
                    progressObserver?.onProgress(
                        AnalysisProgressEvent(
                            stage = "analysis_frame_processed",
                            processedFrames = index + 1,
                            estimatedTotalFrames = sourceFrames.size,
                            droppedFrames = dropped,
                            timestampMs = frame.timestampMs,
                        ),
                    )
                }
            }
            val analysisDuration = System.currentTimeMillis() - analysisStart
            val view = inferView(profile)
            val template = MovementTemplateCandidateGenerator().generate(
                sessionId = "upload-${System.currentTimeMillis()}",
                movementName = profile.displayName,
                inferredView = view,
                profile = profile,
                overlayTimeline = timeline,
                phaseTimeline = phaseTimeline,
            )
            Log.i(
                UPLOAD_ANALYSIS_TAG,
                "decodeMs=$decodeDuration analyzeMs=$analysisDuration total=${sourceFrames.size} dropped=$dropped view=$view phases=${phaseTimeline.size} candidate=${template.status} calibrationVersion=${calibrationProfileVersion ?: -1}",
            )
            progressObserver?.onProgress(
                AnalysisProgressEvent(
                    stage = "analysis_complete",
                    processedFrames = sourceFrames.size,
                    estimatedTotalFrames = sourceFrames.size,
                    droppedFrames = dropped,
                    detail = "Uploaded analysis complete",
                ),
            )
            return UploadedVideoAnalysisResult(
                inferredView = view,
                phaseTimeline = phaseTimeline,
                overlayTimeline = timeline,
                droppedFrames = dropped,
                telemetry = mapOf(
                    "decode_time_ms" to decodeDuration,
                    "analysis_time_ms" to analysisDuration,
                    "total_frames_processed" to sourceFrames.size.toLong(),
                    "frames_dropped" to dropped.toLong(),
                    "candidate_phase_count" to phaseTimeline.map { it.second }.distinct().size.toLong(),
                    "calibration_profile_version" to (calibrationProfileVersion?.toLong() ?: -1L),
                ),
                candidate = template,
            )
        } catch (e: Exception) {
            Log.e(UPLOAD_ANALYSIS_TAG, "analysis_exception uri=$videoUri message=${e.message}", e)
            progressObserver?.onProgress(AnalysisProgressEvent(stage = "analysis_exception", detail = e.message))
            throw e
        }
    }

    private fun inferView(profile: MovementProfile): CameraViewConstraint = when {
        profile.allowedViews.contains(CameraViewConstraint.SIDE_LEFT) -> CameraViewConstraint.SIDE_LEFT
        profile.allowedViews.contains(CameraViewConstraint.SIDE_RIGHT) -> CameraViewConstraint.SIDE_RIGHT
        else -> CameraViewConstraint.ANY
    }
}

private data class PoseGateDecision(
    val acceptForOverlay: Boolean,
    val reason: String,
)

private data class PoseStats(
    val bodyCenterX: Float,
    val bodyCenterY: Float,
    val torsoSize: Float,
)

private class UploadedPoseQualityGate(
    private val minLandmarkVisibility: Float = 0.55f,
    private val minReliableLandmarks: Int = 6,
    private val maxCenterJumpTorsoUnits: Float = 1.4f,
    private val maxReacquireCenterJumpTorsoUnits: Float = 2.0f,
    private val minTorsoScaleRatio: Float = 0.6f,
    private val maxTorsoScaleRatio: Float = 1.7f,
    private val reacquireStableFrames: Int = 2,
) {
    private var previousAccepted: PoseStats? = null
    private var waitingForReacquisition = false
    private var stableReacquireCount = 0

    fun evaluate(frame: PoseFrame): PoseGateDecision {
        val stats = statsFor(frame) ?: run {
            if (previousAccepted != null) {
                waitingForReacquisition = true
                stableReacquireCount = 0
            }
            return PoseGateDecision(acceptForOverlay = false, reason = "invalid_geometry_or_low_visibility")
        }
        val previous = previousAccepted
        if (previous == null) {
            previousAccepted = stats
            return PoseGateDecision(acceptForOverlay = true, reason = "ok_initial")
        }
        val jumpThreshold = if (waitingForReacquisition) maxReacquireCenterJumpTorsoUnits else maxCenterJumpTorsoUnits
        val centerJump = normalizedCenterJump(previous, stats)
        val scaleRatio = stats.torsoSize / previous.torsoSize
        if (centerJump > jumpThreshold || scaleRatio < minTorsoScaleRatio || scaleRatio > maxTorsoScaleRatio) {
            waitingForReacquisition = true
            stableReacquireCount = 0
            return PoseGateDecision(acceptForOverlay = false, reason = "implausible_jump")
        }
        if (!waitingForReacquisition) {
            previousAccepted = stats
            return PoseGateDecision(acceptForOverlay = true, reason = "ok")
        }

        stableReacquireCount += 1
        return if (stableReacquireCount >= reacquireStableFrames) {
            waitingForReacquisition = false
            stableReacquireCount = 0
            previousAccepted = stats
            PoseGateDecision(acceptForOverlay = true, reason = "reacquired_stable")
        } else {
            PoseGateDecision(acceptForOverlay = false, reason = "waiting_stable_reacquisition")
        }
    }

    private fun statsFor(frame: PoseFrame): PoseStats? {
        val jointsByName = frame.joints.associateBy { it.name }
        val leftShoulder = jointsByName["left_shoulder"] ?: return null
        val rightShoulder = jointsByName["right_shoulder"] ?: return null
        val leftHip = jointsByName["left_hip"] ?: return null
        val rightHip = jointsByName["right_hip"] ?: return null
        if (leftShoulder.visibility < minLandmarkVisibility ||
            rightShoulder.visibility < minLandmarkVisibility ||
            leftHip.visibility < minLandmarkVisibility ||
            rightHip.visibility < minLandmarkVisibility
        ) {
            return null
        }
        val reliableLandmarks = frame.joints.count { it.visibility >= minLandmarkVisibility }
        if (reliableLandmarks < minReliableLandmarks) return null

        val shoulderCenterX = (leftShoulder.x + rightShoulder.x) / 2f
        val shoulderCenterY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipCenterX = (leftHip.x + rightHip.x) / 2f
        val hipCenterY = (leftHip.y + rightHip.y) / 2f
        val torsoWidth = distance(leftShoulder.x, leftShoulder.y, rightShoulder.x, rightShoulder.y)
        val torsoHeight = distance(shoulderCenterX, shoulderCenterY, hipCenterX, hipCenterY)
        if (torsoWidth < 0.02f || torsoHeight < 0.05f) return null
        val torsoAspect = torsoHeight / torsoWidth
        if (torsoAspect < 0.5f || torsoAspect > 8.0f) return null

        return PoseStats(
            bodyCenterX = (shoulderCenterX + hipCenterX) / 2f,
            bodyCenterY = (shoulderCenterY + hipCenterY) / 2f,
            torsoSize = max(torsoHeight, torsoWidth),
        )
    }

    private fun normalizedCenterJump(previous: PoseStats, current: PoseStats): Float {
        val centerDistance = distance(previous.bodyCenterX, previous.bodyCenterY, current.bodyCenterX, current.bodyCenterY)
        return centerDistance / previous.torsoSize.coerceAtLeast(0.001f)
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float = hypot(ax - bx, ay - by)
}

data class UploadedVideoAnalysisResult(
    val inferredView: CameraViewConstraint,
    val phaseTimeline: List<Pair<Long, String>>,
    val overlayTimeline: List<OverlayTimelinePoint>,
    val droppedFrames: Int,
    val telemetry: Map<String, Long>,
    val candidate: MovementTemplateCandidate,
)

class UploadedVideoAnalysisCoordinator(
    private val repository: UploadedAnalysisRepository,
    private val analyzer: UploadedVideoAnalyzer,
) {
    fun analyzeAndStore(videoUri: Uri, profile: MovementProfile): UploadedMovementSession {
        val started = System.currentTimeMillis()
        val result = analyzer.analyze(videoUri, profile)
        val completed = System.currentTimeMillis()
        val session = UploadedMovementSession(
            id = "uploaded-${started}",
            sourceVideoUri = videoUri.toString(),
            movementProfileId = profile.id,
            inferredCameraView = result.inferredView,
            startedAtMs = started,
            completedAtMs = completed,
            frameCount = result.overlayTimeline.size + result.droppedFrames,
            droppedFrames = result.droppedFrames,
            phaseTimeline = result.phaseTimeline,
            overlayTimeline = result.overlayTimeline,
            derivedMetrics = mapOf(
                "avg_alignment_score" to result.overlayTimeline.map { it.metrics["alignment_score"] ?: 0f }.average().toFloat(),
                "duration_ms" to max(0L, completed - started).toFloat(),
            ),
            telemetry = result.telemetry + mapOf("export_overlay_ready" to if (result.overlayTimeline.isNotEmpty()) 1L else 0L),
            templateCandidate = result.candidate,
        )
        repository.save(session)
        return session
    }
}

class MovementTemplateCandidateGenerator {
    fun generate(
        sessionId: String,
        movementName: String?,
        inferredView: CameraViewConstraint,
        profile: MovementProfile,
        overlayTimeline: List<OverlayTimelinePoint>,
        phaseTimeline: List<Pair<Long, String>>,
    ): MovementTemplateCandidate {
        val trunk = overlayTimeline.mapNotNull { it.metrics["trunk_lean"] }
        val alignment = overlayTimeline.mapNotNull { it.metrics["alignment_score"] }
        val confidence = if (alignment.isEmpty()) 0f else alignment.average().toFloat()
        return MovementTemplateCandidate(
            id = "candidate-$sessionId",
            sourceSessionId = sessionId,
            tentativeName = movementName,
            movementTypeGuess = profile.movementType,
            detectedView = inferredView,
            keyJoints = profile.keyJoints,
            candidatePhases = phaseTimeline.map { it.second }.distinct().mapIndexed { idx, p -> PhaseDefinition(p.lowercase(), p, sequenceIndex = idx) },
            candidateRomMetrics = mapOf(
                "trunk_lean_min" to (trunk.minOrNull() ?: 0f),
                "trunk_lean_max" to (trunk.maxOrNull() ?: 0f),
            ),
            thresholdSuggestions = mapOf(
                "alignment_score_min" to (alignment.minOrNull() ?: 0.5f),
                "trunk_lean_target" to ((trunk.minOrNull() ?: 0f) + (trunk.maxOrNull() ?: 0f)) / 2f,
            ),
            confidence = confidence,
            status = CandidateStatus.DRAFT,
        )
    }
}
