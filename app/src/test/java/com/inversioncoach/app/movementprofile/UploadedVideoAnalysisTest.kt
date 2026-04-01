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
        assertEquals(2, result.droppedFrames)
    }

    @Test
    fun analyzeRejectsImplausiblePoseJumpAfterTrackerLoss() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(0, 0.9f))
                yield(frame(33, 0f))
                yield(frame(66, 0.9f, shoulderX = 0.05f, hipX = 0.08f))
            }
        }

        val result = UploadedVideoAnalyzer(source).analyze(Uri.parse("file:///tmp/jump.mp4"), profile)

        assertEquals(1, result.overlayTimeline.size)
        assertEquals(2, result.droppedFrames)
    }

    @Test
    fun analyzeRequiresStableFramesBeforeOverlayReacquisition() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                yield(frame(0, 0.9f))
                yield(frame(33, 0f))
                yield(frame(66, 0.9f, shoulderX = 0.54f, hipX = 0.54f))
                yield(frame(99, 0.9f, shoulderX = 0.55f, hipX = 0.55f))
            }
        }

        val result = UploadedVideoAnalyzer(source).analyze(Uri.parse("file:///tmp/reacquire.mp4"), profile)

        assertEquals(2, result.overlayTimeline.size)
        assertEquals(2, result.droppedFrames)
        assertEquals(99L, result.overlayTimeline.last().timestampMs)
    }

    @Test
    fun analyzeDoesNotSharePoseGateStateAcrossRuns() {
        val profile = ExistingDrillToProfileAdapter().fromDrill(DrillType.FREESTYLE)
        var call = 0
        val source = object : VideoPoseFrameSource {
            override fun decode(videoUri: Uri): Sequence<PoseFrame> = sequence {
                if (call == 0) {
                    // End first analysis in reacquisition wait state.
                    yield(frame(0, 0.9f))
                    yield(frame(33, 0f))
                    yield(frame(66, 0.9f, shoulderX = 0.54f, hipX = 0.54f))
                } else {
                    // Second analysis should accept first valid frame immediately.
                    yield(frame(0, 0.9f))
                }
                call += 1
            }
        }

        val analyzer = UploadedVideoAnalyzer(source)
        val first = analyzer.analyze(Uri.parse("file:///tmp/a.mp4"), profile)
        val second = analyzer.analyze(Uri.parse("file:///tmp/b.mp4"), profile)

        assertEquals(1, first.overlayTimeline.size)
        assertEquals(2, first.droppedFrames)
        assertEquals(1, second.overlayTimeline.size)
        assertEquals(0, second.droppedFrames)
        assertEquals(0L, second.overlayTimeline.first().timestampMs)
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

    private fun frame(
        ts: Long,
        confidence: Float,
        shoulderX: Float = 0.50f,
        hipX: Float = 0.50f,
    ): PoseFrame = PoseFrame(
        timestampMs = ts,
        confidence = confidence,
        joints = listOf(
            JointPoint("left_shoulder", shoulderX - 0.02f, 0.30f, 0f, 0.9f),
            JointPoint("right_shoulder", shoulderX + 0.02f, 0.30f, 0f, 0.9f),
            JointPoint("left_hip", hipX - 0.01f, 0.60f, 0f, 0.9f),
            JointPoint("right_hip", hipX + 0.01f, 0.60f, 0f, 0.9f),
            JointPoint("left_elbow", shoulderX - 0.05f, 0.38f, 0f, 0.9f),
            JointPoint("right_elbow", shoulderX + 0.05f, 0.38f, 0f, 0.9f),
            JointPoint("left_wrist", shoulderX - 0.08f, 0.48f, 0f, 0.9f),
            JointPoint("right_wrist", shoulderX + 0.08f, 0.48f, 0f, 0.9f),
            JointPoint("left_ankle", hipX - 0.01f, 0.90f, 0f, 0.9f),
            JointPoint("right_ankle", hipX + 0.01f, 0.90f, 0f, 0.9f),
        ),
    )
}
