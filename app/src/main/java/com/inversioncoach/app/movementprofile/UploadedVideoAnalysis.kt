package com.inversioncoach.app.movementprofile

import android.net.Uri
import android.util.Log
import com.inversioncoach.app.calibration.DrillMovementProfile
import com.inversioncoach.app.model.PoseFrame
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.roundToLong

private const val UPLOAD_ANALYSIS_TAG = "UploadAnalysis"
private const val EDGE_FRAME_SKIP_WINDOW = 2

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
            val totalStart = System.currentTimeMillis()
            val decodeStart = totalStart
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
            )
            val decodeDuration = System.currentTimeMillis() - decodeStart

            val analysisStart = System.currentTimeMillis()
            val calibrationProfileVersion = drillMovementProfile?.profileVersion
            val phaseDetector = phaseDetectorFactory(profile)
            val timeline = mutableListOf<OverlayTimelinePoint>()
            val phaseTimeline = mutableListOf<Pair<Long, String>>()
            var dropped = 0
            var edgeFramesSkipped = 0
            var acceptedFrames = 0
            var timestampCorrections = 0
            var lastAcceptedTimestampMs = -1L
            var poseDetectionMs = 0L
            var postProcessMsTotal = 0L
            progressObserver?.onProgress(
                AnalysisProgressEvent(
                    stage = "analysis_started",
                    processedFrames = 0,
                    estimatedTotalFrames = estimatedTotalFrames.takeIf { it > 0 },
                    droppedFrames = 0,
                    detail = "Starting post-processing on uploaded frames",
                )
            )
            var processedFrames = 0
            val pendingFrames = ArrayDeque<Pair<Int, PoseFrame>>()
            fun processFrame(index: Int, frame: PoseFrame, forceEdgeSkip: Boolean) {
                processedFrames = max(processedFrames, index + 1)
                val frameStart = System.nanoTime()
                if (frame.inferenceTimeMs > 0) {
                    poseDetectionMs += frame.inferenceTimeMs
                }
                val resolvedTotalFrames = estimatedTotalFrames.takeIf { it > 0 } ?: processedFrames
                if (index % 2 == 0) {
                    Log.i(
                        UPLOAD_ANALYSIS_TAG,
                        "analysis_sample frameIndex=$index totalHint=$resolvedTotalFrames timestampMs=${frame.timestampMs} dropped=$dropped",
                    )
                }
                val nearStartEdge = index < EDGE_FRAME_SKIP_WINDOW
                val nearEdge = forceEdgeSkip || nearStartEdge
                if (frame.confidence <= 0f || frame.joints.isEmpty()) {
                    if (nearEdge) {
                        edgeFramesSkipped += 1
                        progressObserver?.onProgress(
                            AnalysisProgressEvent(
                                stage = "analysis_edge_frame_skipped",
                                processedFrames = index + 1,
                                estimatedTotalFrames = resolvedTotalFrames,
                                droppedFrames = dropped,
                                timestampMs = frame.timestampMs,
                                detail = "Skipped low-confidence edge frame",
                            ),
                        )
                        return
                    }
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
                    return
                }
                val sanitizedTimestampMs = if (frame.timestampMs <= lastAcceptedTimestampMs) {
                    timestampCorrections += 1
                    lastAcceptedTimestampMs + 1L
                } else {
                    frame.timestampMs
                }
                lastAcceptedTimestampMs = sanitizedTimestampMs
                val normalized = poseFrameNormalizer.normalize(frame)
                val angleFrame = angleEngine.compute(normalized)
                val postStart = System.nanoTime()
                val alignment = alignmentScorer.score(normalized, profile.alignmentRules)
                val phase = phaseDetector.update(angleFrame, alignment >= 0.65f)
                val postProcessMs = ((System.nanoTime() - postStart) / 1_000_000.0).roundToLong()
                postProcessMsTotal += postProcessMs
                phaseTimeline += sanitizedTimestampMs to phase.name
                timeline += OverlayTimelinePoint(
                    timestampMs = sanitizedTimestampMs,
                    // Overlay rendering must stay in canonical source-frame normalized space.
                    // The normalized pose frame is only for movement metrics/phase scoring.
                    landmarks = frame.joints.map { it.name to (it.x to it.y) },
                    metrics = mapOf("alignment_score" to alignment, "trunk_lean" to angleFrame.trunkLeanDeg),
                    phaseId = phase.name,
                    confidence = frame.confidence,
                )
                acceptedFrames += 1
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
            sourceFrames.withIndex().forEach { (index, frame) ->
                pendingFrames.addLast(index to frame)
                if (pendingFrames.size > EDGE_FRAME_SKIP_WINDOW) {
                    val (safeIndex, safeFrame) = pendingFrames.removeFirst()
                    processFrame(safeIndex, safeFrame, forceEdgeSkip = false)
                }
            }
            while (pendingFrames.isNotEmpty()) {
                val (edgeIndex, edgeFrame) = pendingFrames.removeFirst()
                processFrame(edgeIndex, edgeFrame, forceEdgeSkip = true)
            }
            val resolvedTotalFrames = estimatedTotalFrames.takeIf { it > 0 } ?: processedFrames
            val analysisDuration = System.currentTimeMillis() - analysisStart
            val postProcessDuration = postProcessMsTotal
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
                "timing_diagnostics decode_ms=$decodeDuration pose_detection_ms=$poseDetectionMs postprocess_ms=$postProcessDuration total_ms=${System.currentTimeMillis() - totalStart} " +
                    "frames=$processedFrames accepted=$acceptedFrames dropped=$dropped edgeSkipped=$edgeFramesSkipped timestampCorrections=$timestampCorrections " +
                    "view=$view phases=${phaseTimeline.size} candidate=${template.status} calibrationVersion=${calibrationProfileVersion ?: -1}",
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
                    "decode_ms" to decodeDuration,
                    "pose_detection_ms" to poseDetectionMs,
                    "postprocess_ms" to postProcessDuration,
                    "analysis_time_ms" to analysisDuration,
                    "total_frames_processed" to processedFrames.toLong(),
                    "frames_accepted" to acceptedFrames.toLong(),
                    "frames_dropped" to dropped.toLong(),
                    "edge_frames_skipped" to edgeFramesSkipped.toLong(),
                    "timestamp_corrections" to timestampCorrections.toLong(),
                    "candidate_phase_count" to phaseTimeline.map { it.second }.distinct().size.toLong(),
                    "calibration_profile_version" to (calibrationProfileVersion?.toLong() ?: -1L),
                    "total_ms" to (System.currentTimeMillis() - totalStart),
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
