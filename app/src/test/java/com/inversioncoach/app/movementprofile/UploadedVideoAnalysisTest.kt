package com.inversioncoach.app.movementprofile

import android.net.Uri
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
        assertEquals(2, result.droppedFrames)
    }

    @Test
    fun compatibilityAdapterPreservesDrillIdentity() {
        val adapter = ExistingDrillToProfileAdapter()
        val profile = adapter.fromDrill(DrillType.FREESTYLE)
        assertEquals(DrillType.FREESTYLE, profile.drillType)
        assertTrue(profile.displayName.contains("Freestyle"))
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
