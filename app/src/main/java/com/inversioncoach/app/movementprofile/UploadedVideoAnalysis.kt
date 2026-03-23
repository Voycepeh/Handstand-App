package com.inversioncoach.app.movementprofile

import android.net.Uri
import android.util.Log
import com.inversioncoach.app.model.PoseFrame
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
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
        progressObserver: AnalysisProgressObserver? = null,
    ): UploadedVideoAnalysisResult {
        try {
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
                if (frame.confidence <= 0f || frame.joints.isEmpty()) {
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
                        landmarks = normalized.joints.map { it.name to (it.x to it.y) },
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
            Log.i(UPLOAD_ANALYSIS_TAG, "decodeMs=$decodeDuration analyzeMs=$analysisDuration total=${sourceFrames.size} dropped=$dropped view=$view phases=${phaseTimeline.size} candidate=${template.status}")
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
