package com.inversioncoach.app.movementprofile

import android.net.Uri
import com.inversioncoach.app.calibration.DefaultDrillMovementProfiles
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UploadedVideoAnalysisTest {
    @Test
    fun analyzePreservesSourceFrameOverlayCoordinates() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(0, 0.9f))
            }
        }

        val result = UploadedVideoAnalyzer(source).analyze(Uri.parse("file:///tmp/source-space.mp4"), profile)
        val landmarks = result.overlayTimeline.first().landmarks.toMap()

        assertEquals(0.48f, landmarks.getValue("left_shoulder").first, 0.0001f)
        assertEquals(0.30f, landmarks.getValue("left_shoulder").second, 0.0001f)
    }

    @Test
    fun analyzeAndPersistUploadSession() {
        val profile = MovementProfile(
            id = "freestyle-generic",
            displayName = "Freestyle",
            drillType = DrillType.FREESTYLE,
            movementType = MovementType.HOLD,
            allowedViews = setOf(CameraViewConstraint.ANY),
            phaseDefinitions = listOf(PhaseDefinition("hold", "Hold", sequenceIndex = 0)),
            alignmentRules = listOf(AlignmentRule("line", "shoulder_hip_stack", 0f, 0.2f)),
            holdRule = HoldRule("hold", 1000, 200),
            readinessRule = ReadinessRule(0.3f, setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip"), 3, false),
            keyJoints = setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip"),
        )
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(0, 0.9f))
                yield(frame(33, 0.9f))
                yield(frame(66, 0f))
            }
        }

        val root = createTempDir(prefix = "upload-analysis-test")
        val repo = FileUploadedAnalysisRepository(root)
        val coordinator = UploadedVideoAnalysisCoordinator(repo, UploadedVideoAnalyzer(source))

        val session = coordinator.analyzeAndStore(Uri.parse("file:///tmp/sample.mp4"), profile)
        val restored = repo.get(session.id)
        assertNotNull(restored)
        assertEquals(3, session.frameCount)
        assertEquals(CandidateStatus.DRAFT, session.templateCandidate.status)
        assertTrue(session.telemetry["export_overlay_ready"] == 1L)

        root.deleteRecursively()
    }


    @Test
    fun analyzeReturnsEmptyOverlayWhenNoDetections() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(0, 0f))
                yield(frame(33, 0f))
            }
        }

        val result = UploadedVideoAnalyzer(source).analyze(Uri.parse("file:///tmp/empty.mp4"), profile)

        assertTrue(result.overlayTimeline.isEmpty())
        assertEquals(0, result.droppedFrames)
        assertEquals(2L, result.telemetry["edge_frames_skipped"])
    }

    @Test
    fun analyzeSkipsOccludedEdgeFramesWithoutInvalidatingSession() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(0, 0f))
                yield(frame(33, 0f))
                yield(frame(66, 0.92f))
                yield(frame(99, 0.94f))
                yield(frame(132, 0f))
            }
        }

        val result = UploadedVideoAnalyzer(source).analyze(Uri.parse("file:///tmp/occlusion.mp4"), profile)

        assertEquals(2, result.overlayTimeline.size)
        assertEquals(0, result.droppedFrames)
        assertEquals(3L, result.telemetry["edge_frames_skipped"])
    }

    @Test
    fun analyzeCorrectsNonMonotonicTimestamps() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(100, 0.9f))
                yield(frame(100, 0.9f))
                yield(frame(99, 0.9f))
            }
        }

        val result = UploadedVideoAnalyzer(source).analyze(Uri.parse("file:///tmp/timestamps.mp4"), profile)
        val timestamps = result.overlayTimeline.map { it.timestampMs }

        assertTrue(timestamps.zipWithNext().all { (a, b) -> b > a })
        assertEquals(2L, result.telemetry["timestamp_corrections"])
    }

    @Test
    fun compatibilityAdapterPreservesDrillIdentity() {
        val adapter = ExistingDrillToProfileAdapter()
        val profile = adapter.fromDrill(DrillType.FREESTYLE)
        assertEquals(DrillType.FREESTYLE, profile.drillType)
        assertTrue(profile.displayName.contains("Freestyle"))
    }

    @Test
    fun analyzeIncludesCalibrationProfileVersionInTelemetryWhenProvided() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence { yield(frame(0, 0.9f)) }
        }
        val calibrationProfile = DefaultDrillMovementProfiles.forDrill(DrillType.FREESTYLE, nowMs = 555L).copy(profileVersion = 7)

        val result = UploadedVideoAnalyzer(source).analyze(
            videoUri = Uri.parse("file:///tmp/with-calibration.mp4"),
            profile = profile,
            drillMovementProfile = calibrationProfile,
        )

        assertEquals(7L, result.telemetry["calibration_profile_version"])
    }

    @Test
    fun progressObserverReachesCompletion() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(0, 0.9f))
                yield(frame(200, 0.8f))
            }
        }
        val events = mutableListOf<AnalysisProgressEvent>()

        UploadedVideoAnalyzer(source).analyze(
            videoUri = Uri.parse("file:///tmp/progress.mp4"),
            profile = profile,
            progressObserver = AnalysisProgressObserver { events += it },
        )

        val completion = events.last { it.stage == "analysis_complete" }
        assertEquals(2, completion.processedFrames)
        assertEquals(2, completion.estimatedTotalFrames)
    }

    @Test
    fun progressObserverUsesDecodedFrameCountWhenDecodeEstimateMissing() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(0, 0.9f))
                yield(frame(100, 0.8f))
                yield(frame(200, 0.7f))
            }
        }
        val events = mutableListOf<AnalysisProgressEvent>()

        UploadedVideoAnalyzer(source).analyze(
            videoUri = Uri.parse("file:///tmp/decode-count-fallback.mp4"),
            profile = profile,
            progressObserver = AnalysisProgressObserver { events += it },
        )

        val analysisStarted = events.first { it.stage == "analysis_started" }
        assertEquals(3, analysisStarted.estimatedTotalFrames)
    }

    @Test
    fun analyzerMergesSamplingTelemetryFromFrameSource() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource, UploadSamplingTelemetryProvider {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(0, 0.9f))
            }

            override fun samplingTelemetry(): Map<String, Long> = mapOf(
                "adaptive_candidate_frames" to 42L,
                "adaptive_sampled_frames" to 9L,
            )
        }

        val result = UploadedVideoAnalyzer(source).analyze(
            videoUri = Uri.parse("file:///tmp/telemetry.mp4"),
            profile = profile,
        )

        assertEquals(42L, result.telemetry["adaptive_candidate_frames"])
        assertEquals(9L, result.telemetry["adaptive_sampled_frames"])
    }

    @Test
    fun analyzerEmitsStageTimingTelemetry() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(0, 0.9f).copy(inferenceTimeMs = 12L))
                yield(frame(100, 0.9f).copy(inferenceTimeMs = 11L))
            }
        }

        val result = UploadedVideoAnalyzer(source).analyze(
            videoUri = Uri.parse("file:///tmp/timing-telemetry.mp4"),
            profile = profile,
        )

        assertTrue((result.telemetry["decode_ms"] ?: -1L) >= 0L)
        assertEquals(23L, result.telemetry["pose_detection_ms"])
        assertTrue((result.telemetry["postprocess_ms"] ?: -1L) >= 0L)
        assertTrue((result.telemetry["total_ms"] ?: -1L) >= (result.telemetry["decode_ms"] ?: 0L))
    }

    @Test
    fun analyzerTelemetryKeepsAcceptedCountsAlignedWithOverlayTimeline() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(0, 0.9f))
                yield(frame(120, 0.85f))
                yield(frame(240, 0f))
            }
        }

        val result = UploadedVideoAnalyzer(source).analyze(
            videoUri = Uri.parse("file:///tmp/accepted-counts.mp4"),
            profile = profile,
        )

        assertEquals(result.overlayTimeline.size.toLong(), result.telemetry["frames_accepted"])
        assertEquals(3L, result.telemetry["total_frames_processed"])
    }

    private fun frame(ts: Long, confidence: Float): PoseFrame = PoseFrame(
        timestampMs = ts,
        confidence = confidence,
        joints = listOf(
            JointPoint("left_shoulder", 0.48f, 0.30f, 0f, 0.9f),
            JointPoint("right_shoulder", 0.52f, 0.30f, 0f, 0.9f),
            JointPoint("left_hip", 0.49f, 0.60f, 0f, 0.9f),
            JointPoint("right_hip", 0.51f, 0.60f, 0f, 0.9f),
            JointPoint("left_elbow", 0.45f, 0.38f, 0f, 0.9f),
            JointPoint("right_elbow", 0.55f, 0.38f, 0f, 0.9f),
            JointPoint("left_wrist", 0.42f, 0.48f, 0f, 0.9f),
            JointPoint("right_wrist", 0.58f, 0.48f, 0f, 0.9f),
            JointPoint("left_ankle", 0.49f, 0.90f, 0f, 0.9f),
            JointPoint("right_ankle", 0.51f, 0.90f, 0f, 0.9f),
        ),
    )
}
