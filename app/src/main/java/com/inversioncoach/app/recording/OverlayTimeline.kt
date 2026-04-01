package com.inversioncoach.app.recording

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.EffectiveView
import com.inversioncoach.app.overlay.FreestyleViewMode
import com.inversioncoach.app.pose.PoseScaleMode

/**
 * Lightweight timeline payload used to reconstruct overlays for post-session video rendering.
 */
data class OverlayTimelineFrame(
    val sessionId: Long,
    val relativeTimestampMs: Long,
    val absoluteVideoPtsUs: Long? = null,
    val timestampMs: Long,
    val landmarks: List<JointPoint>,
    val skeletonLines: List<Pair<String, String>>,
    val headPoint: JointPoint?,
    val hipPoint: JointPoint?,
    val idealLine: Pair<JointPoint, JointPoint>?,
    val alignmentAngles: Map<String, Float>,
    val visibilityFlags: Map<String, Boolean>,
    val drillMetadata: OverlayDrillMetadata,
    val smoothedLandmarks: List<JointPoint> = landmarks,
    val confidence: Float = 0f,
    val captureWidth: Int? = null,
    val captureHeight: Int? = null,
    val captureRotationDegrees: Int? = null,
    val scaleMode: PoseScaleMode = PoseScaleMode.FIT,
    val unreliableJointNames: Set<String> = emptySet(),
    val sourceFrameIndex: Long? = null,
)

data class OverlayDrillMetadata(
    val sessionMode: SessionMode,
    val drillCameraSide: DrillCameraSide?,
    val effectiveView: EffectiveView = EffectiveView.SIDE,
    val freestyleViewMode: FreestyleViewMode = FreestyleViewMode.UNKNOWN,
    val showSkeleton: Boolean,
    val showIdealLine: Boolean,
    val showCenterOfGravity: Boolean = true,
    val bodyVisible: Boolean,
    val mirrorMode: Boolean,
)

data class OverlayTimeline(
    val version: Int = 1,
    val startedAtMs: Long,
    val sampleIntervalMs: Long,
    val frames: List<OverlayTimelineFrame>,
)

fun AnnotatedOverlayFrame.toTimelineFrame(sessionId: Long, sessionStartedAtMs: Long): OverlayTimelineFrame {
    val byName = smoothedLandmarks.ifEmpty { landmarks }.associateBy { it.name }
    val relativeTimestampMs = (timestampMs - sessionStartedAtMs).coerceAtLeast(0L)
    return OverlayTimelineFrame(
        sessionId = sessionId,
        relativeTimestampMs = relativeTimestampMs,
        absoluteVideoPtsUs = relativeTimestampMs * 1_000L,
        timestampMs = timestampMs,
        landmarks = landmarks,
        smoothedLandmarks = smoothedLandmarks,
        skeletonLines = emptyList(),
        headPoint = byName["HEAD"] ?: byName["NOSE"],
        hipPoint = byName["LEFT_HIP"] ?: byName["RIGHT_HIP"],
        idealLine = null,
        alignmentAngles = emptyMap(),
        visibilityFlags = mapOf("bodyVisible" to bodyVisible),
        confidence = confidence,
        drillMetadata = OverlayDrillMetadata(
            sessionMode = sessionMode,
            drillCameraSide = drillCameraSide,
            effectiveView = effectiveView,
            freestyleViewMode = freestyleViewMode,
            showSkeleton = showSkeleton,
            showIdealLine = showIdealLine,
            showCenterOfGravity = showCenterOfGravity,
            bodyVisible = bodyVisible,
            mirrorMode = mirrorMode,
        ),
        captureWidth = sourceWidth.takeIf { it > 0 },
        captureHeight = sourceHeight.takeIf { it > 0 },
        captureRotationDegrees = sourceRotationDegrees,
        scaleMode = scaleMode,
        unreliableJointNames = unreliableJointNames,
    )
}

fun OverlayTimelineFrame.toAnnotatedOverlayFrame(): AnnotatedOverlayFrame = AnnotatedOverlayFrame(
    timestampMs = relativeTimestampMs,
    landmarks = landmarks,
    smoothedLandmarks = smoothedLandmarks,
    confidence = confidence,
    sessionMode = drillMetadata.sessionMode,
    drillCameraSide = drillMetadata.drillCameraSide,
    effectiveView = drillMetadata.effectiveView,
    freestyleViewMode = drillMetadata.freestyleViewMode,
    bodyVisible = drillMetadata.bodyVisible,
    showSkeleton = drillMetadata.showSkeleton,
    showIdealLine = drillMetadata.showIdealLine,
    showCenterOfGravity = drillMetadata.showCenterOfGravity,
    mirrorMode = drillMetadata.mirrorMode,
    sourceWidth = captureWidth ?: 0,
    sourceHeight = captureHeight ?: 0,
    sourceRotationDegrees = captureRotationDegrees ?: 0,
    scaleMode = scaleMode,
    unreliableJointNames = unreliableJointNames,
)
