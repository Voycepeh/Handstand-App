package com.inversioncoach.app.movementprofile

import android.net.Uri
import android.util.Log
import com.inversioncoach.app.calibration.DrillMovementProfile
import com.inversioncoach.app.model.PoseFrame
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlin.math.max
import kotlin.math.roundToLong

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
    fun decode(videoUri: Uri, request: VideoDecodeRequest): Sequence<PoseFrame> = decode(videoUri)
    fun decode(videoUri: Uri, observer: AnalysisProgressObserver): Sequence<PoseFrame> = decode(videoUri)
    fun decode(videoUri: Uri, observer: AnalysisProgressObserver, request: VideoDecodeRequest): Sequence<PoseFrame> =
        decode(videoUri, request)
}

interface UploadSamplingTelemetryProvider {
    fun samplingTelemetry(): Map<String, Long>
}

data class VideoDecodeRequest(
    val movementType: MovementType?,
    val adaptiveConfig: AdaptiveSamplingConfig = AdaptiveSamplingConfig(),
)

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
            val decodeStart = System.currentTimeMillis()
            Log.i(UPLOAD_ANALYSIS_TAG, "analysis_loop_start uri=$videoUri")
            progressObserver?.onProgress(AnalysisProgressEvent(stage = "decode_start", detail = "Sampling uploaded video frames"))
            var estimatedTotalFrames = 0
            val sourceFrames = frameSource.decode(
                videoUri = videoUri,
                observer = AnalysisProgressObserver { progressEvent ->
                    progressEvent.estimatedTotalFrames?.let { estimated ->
                        if (estimated > 0) estimatedTotalFrames = max(estimatedTotalFrames, estimated)
                    }
                    progressObserver?.onProgress(progressEvent)
                },
                request = VideoDecodeRequest(
                    movementType = profile.movementType,
                ),
            ).toList()
            val resolvedTotalFrames = if (estimatedTotalFrames > 0) {
                estimatedTotalFrames
            } else {
                sourceFrames.size
            }
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
                    estimatedTotalFrames = resolvedTotalFrames,
                    droppedFrames = 0,
                    detail = "Starting post-processing on decoded frames",
                )
            )
            var processedFrames = 0
            for ((index, frame) in sourceFrames.withIndex()) {
                processedFrames = index + 1
                val frameStart = System.nanoTime()
                if (index % 2 == 0) {
                    Log.i(
                        UPLOAD_ANALYSIS_TAG,
                        "analysis_sample frameIndex=$index totalHint=$resolvedTotalFrames timestampMs=${frame.timestampMs} dropped=$dropped",
                    )
                }
                if (frame.confidence <= 0f || frame.joints.isEmpty()) {
                    dropped += 1
                    progressObserver?.onProgress(
                        AnalysisProgressEvent(
                            stage = "analysis_frame_dropped",
                            processedFrames = index + 1,
                            estimatedTotalFrames = resolvedTotalFrames,
                            droppedFrames = dropped,
                            timestampMs = frame.timestampMs,
                        ),
                    )
                } else {
                    val postProcessMs: Long
                    val alignment: Float
                    val normalized = poseFrameNormalizer.normalize(frame)
                    val angleFrame = angleEngine.compute(normalized)
                    val postStart = System.nanoTime()
                    alignment = alignmentScorer.score(normalized, profile.alignmentRules)
                    val phase = phaseDetector.update(angleFrame, alignment >= 0.65f)
                    postProcessMs = ((System.nanoTime() - postStart) / 1_000_000.0).roundToLong()
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
                            estimatedTotalFrames = resolvedTotalFrames,
                            droppedFrames = dropped,
                            timestampMs = frame.timestampMs,
                            detail = "postProcessMs=$postProcessMs",
                        ),
                    )
                }
                val elapsedMs = ((System.nanoTime() - frameStart) / 1_000_000.0).roundToLong()
                if (index % 4 == 0) {
                    val processed = index + 1
                    val throughput = (processed * 1000.0) / maxOf(1L, System.currentTimeMillis() - analysisStart).toDouble()
                    val remaining = (resolvedTotalFrames - processed).coerceAtLeast(0)
                    val etaSec = if (throughput <= 0.0) -1 else (remaining / throughput).roundToLong()
                    Log.i(
                        UPLOAD_ANALYSIS_TAG,
                        "analysis_timing frame=$processed/$resolvedTotalFrames frameMs=$elapsedMs throughputFps=${"%.2f".format(throughput)} etaSec=$etaSec",
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
                "decodeMs=$decodeDuration analyzeMs=$analysisDuration total=$processedFrames dropped=$dropped view=$view phases=${phaseTimeline.size} candidate=${template.status} calibrationVersion=${calibrationProfileVersion ?: -1}",
            )
            progressObserver?.onProgress(
                AnalysisProgressEvent(
                    stage = "analysis_complete",
                    processedFrames = processedFrames,
                    estimatedTotalFrames = maxOf(resolvedTotalFrames, processedFrames),
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
                    "total_frames_processed" to processedFrames.toLong(),
                    "frames_dropped" to dropped.toLong(),
                    "candidate_phase_count" to phaseTimeline.map { it.second }.distinct().size.toLong(),
                    "calibration_profile_version" to (calibrationProfileVersion?.toLong() ?: -1L),
                ) + (frameSource as? UploadSamplingTelemetryProvider)?.samplingTelemetry().orEmpty(),
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
