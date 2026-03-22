package com.inversioncoach.app.ui.live

import com.inversioncoach.app.recording.OverlayTimeline
import com.inversioncoach.app.recording.OverlayTimelineFrame
import com.inversioncoach.app.recording.OverlayDrillMetadata
import com.inversioncoach.app.storage.SessionBlobStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportDurationResolutionTest {

    @Test
    fun metadataFirstZeroThenRetrySucceedsBeforeFallbacks() = runBlocking {
        var metadataCalls = 0
        val sourceOrder = mutableListOf<String>()
        val resolved = resolveRawDurationWithRetries(
            rawUri = "file:///raw.mp4",
            fileExists = true,
            fileSizeBytes = 1024L,
            sessionElapsedMs = 9_000L,
            overlayMaxTimestampMs = 8_000L,
            maxRetries = 2,
            readMetadataDuration = {
                metadataCalls += 1
                if (metadataCalls == 1) 0L else 2_345L
            },
            readExtractorDuration = { 0L },
            onAttempt = { sourceOrder += it.source },
            onRetry = { _, _ -> },
        )

        assertEquals(2_345L, resolved.durationMs)
        assertEquals("metadata_retriever", resolved.source)
        assertEquals(listOf("metadata_retriever", "media_extractor", "metadata_retriever"), sourceOrder)
    }

    @Test
    fun mediaRetriesHappenBeforeSessionFallback() = runBlocking {
        val sourceOrder = mutableListOf<String>()
        val resolved = resolveRawDurationWithRetries(
            rawUri = "file:///raw.mp4",
            fileExists = true,
            fileSizeBytes = 2048L,
            sessionElapsedMs = 3_210L,
            overlayMaxTimestampMs = 4_321L,
            maxRetries = 1,
            readMetadataDuration = { 0L },
            readExtractorDuration = { 0L },
            onAttempt = { sourceOrder += it.source },
            onRetry = { _, _ -> },
        )

        assertEquals(3_210L, resolved.durationMs)
        assertEquals("session_elapsed", resolved.source)
        assertEquals(
            listOf(
                "metadata_retriever",
                "media_extractor",
                "metadata_retriever",
                "media_extractor",
                "session_elapsed",
            ),
            sourceOrder,
        )
    }

    @Test
    fun overlayTimestampUsedOnlyAfterMediaAndSessionFail() = runBlocking {
        val sourceOrder = mutableListOf<String>()
        val resolved = resolveRawDurationWithRetries(
            rawUri = "file:///raw.mp4",
            fileExists = true,
            fileSizeBytes = 2048L,
            sessionElapsedMs = 0L,
            overlayMaxTimestampMs = 9_999L,
            maxRetries = 1,
            readMetadataDuration = { 0L },
            readExtractorDuration = { 0L },
            onAttempt = { sourceOrder += it.source },
            onRetry = { _, _ -> },
        )

        assertEquals(9_999L, resolved.durationMs)
        assertEquals("overlay_timeline", resolved.source)
        assertEquals(
            listOf(
                "metadata_retriever",
                "media_extractor",
                "metadata_retriever",
                "media_extractor",
                "session_elapsed",
                "overlay_timeline",
            ),
            sourceOrder,
        )
    }

    @Test
    fun clampHelperTruncatesFramesBeyondChosenDuration() {
        val timeline = timelineOf(100L, 900L, 1500L)

        val clamped = clampOverlayTimelineToDuration(timeline, maxDurationMs = 1000L)

        assertEquals(listOf(100L, 900L), clamped.frames.map { it.timestampMs })
    }

    @Test
    fun mismatchBeyondToleranceGetsClampedAndPreflightDoesNotFail() {
        val snapshot = ExportSnapshot(
            sessionId = 1L,
            stopTimestampMs = 1000L,
            rawUri = "file:///raw.mp4",
            rawDurationMs = 1000L,
            rawDurationSource = "metadata_retriever",
            overlayTimeline = timelineOf(100L, 800L, 1300L),
            overlayTimelineUri = null,
            overlayFrameCount = 3,
        )

        val preflight = prepareExportSnapshotInputs(
            snapshot = snapshot,
            overlayCaptureFrozen = true,
            hasReadableRaw = true,
            toleranceMs = 50L,
            liveOverlayFrameCountAtFreeze = 4,
        )

        assertNull(preflight.fatalReason)
        assertTrue(preflight.clampApplied)
        assertTrue(preflight.durationMismatchMs > 0L)
        assertEquals(listOf(100L, 800L), preflight.snapshot.overlayTimeline.frames.map { it.timestampMs })
        assertEquals(2, preflight.frozenOverlayFrameCount)
        assertEquals(2, preflight.overlayFramesIgnoredAfterFreeze)
    }

    @Test
    fun readableRawWithUnresolvedMediaDurationProceedsWithOverlayFallbackDuration() {
        val snapshot = ExportSnapshot(
            sessionId = 2L,
            stopTimestampMs = 1000L,
            rawUri = "file:///raw.mp4",
            rawDurationMs = 0L,
            rawDurationSource = "none",
            overlayTimeline = timelineOf(120L, 360L, 700L),
            overlayTimelineUri = null,
            overlayFrameCount = 3,
        )

        val preflight = prepareExportSnapshotInputs(
            snapshot = snapshot,
            overlayCaptureFrozen = true,
            hasReadableRaw = true,
            toleranceMs = 600L,
            liveOverlayFrameCountAtFreeze = 3,
        )

        assertNull(preflight.fatalReason)
        assertEquals(700L, preflight.snapshot.rawDurationMs)
        assertEquals("overlay_timeline_fallback", preflight.snapshot.rawDurationSource)
    }

    @Test
    fun readableRawWithoutAnyDurationBasisFailsWithoutSyntheticDuration() {
        val snapshot = ExportSnapshot(
            sessionId = 22L,
            stopTimestampMs = 1000L,
            rawUri = "file:///raw.mp4",
            rawDurationMs = 0L,
            rawDurationSource = "none",
            overlayTimeline = timelineOf(),
            overlayTimelineUri = null,
            overlayFrameCount = 0,
        )

        val preflight = prepareExportSnapshotInputs(
            snapshot = snapshot,
            overlayCaptureFrozen = true,
            hasReadableRaw = true,
            toleranceMs = 600L,
            liveOverlayFrameCountAtFreeze = 0,
        )

        assertEquals("EXPORT_INPUT_VALIDATION_FAILED_DURATION_UNAVAILABLE_READABLE_RAW", preflight.fatalReason)
        assertEquals(0L, preflight.snapshot.rawDurationMs)
    }

    @Test
    fun validationDistinguishesFatalVsRecoverablePreflight() {
        val recoverable = prepareExportSnapshotInputs(
            snapshot = ExportSnapshot(
                sessionId = 3L,
                stopTimestampMs = 1000L,
                rawUri = "file:///raw.mp4",
                rawDurationMs = 0L,
                rawDurationSource = "none",
                overlayTimeline = timelineOf(500L),
                overlayTimelineUri = null,
                overlayFrameCount = 1,
            ),
            overlayCaptureFrozen = true,
            hasReadableRaw = true,
            toleranceMs = 600L,
            liveOverlayFrameCountAtFreeze = 1,
        )
        val fatal = prepareExportSnapshotInputs(
            snapshot = ExportSnapshot(
                sessionId = 4L,
                stopTimestampMs = 1000L,
                rawUri = "file:///missing.mp4",
                rawDurationMs = 0L,
                rawDurationSource = "none",
                overlayTimeline = timelineOf(),
                overlayTimelineUri = null,
                overlayFrameCount = 0,
            ),
            overlayCaptureFrozen = true,
            hasReadableRaw = false,
            toleranceMs = 600L,
            liveOverlayFrameCountAtFreeze = 0,
        )

        assertNull(recoverable.fatalReason)
        assertEquals("EXPORT_INPUT_VALIDATION_FAILED_RAW_MISSING_AND_DURATION_UNAVAILABLE", fatal.fatalReason)
    }



    @Test
    fun deepCopyOverlayTimelineForExportSurvivesLiveBufferMutation() {
        val original = timelineOf(100L, 200L)
        val copy = deepCopyOverlayTimelineForExport(original)

        val mutated = original.copy(frames = emptyList())

        assertEquals(0, mutated.frames.size)
        assertEquals(2, copy.frames.size)
        assertEquals(listOf(100L, 200L), copy.frames.map { it.timestampMs })
    }

    @Test
    fun normalizeFrozenOverlayTimelineConvertsEpochTimestampsToSessionRelative() {
        val sessionStart = 1_700_000_000_000L
        val timeline = OverlayTimeline(
            startedAtMs = sessionStart,
            sampleIntervalMs = 80L,
            frames = listOf(
                timelineFrame(relativeTs = 120L, absoluteTs = sessionStart + 120L),
                timelineFrame(relativeTs = 420L, absoluteTs = sessionStart + 420L),
            ),
        )

        val normalized = normalizeFrozenOverlayTimelineToRelative(timeline)

        assertEquals(0L, normalized.startedAtMs)
        assertEquals(listOf(120L, 420L), normalized.frames.map { it.timestampMs })
        assertEquals(listOf(120L, 420L), normalized.frames.map { it.relativeTimestampMs })
    }


    @Test
    fun preflightFailsWhenSnapshotTimelineIsNotRelativeNormalized() {
        val sessionStart = 1_700_000_000_000L
        val snapshot = ExportSnapshot(
            sessionId = 40L,
            stopTimestampMs = sessionStart + 1500L,
            rawUri = "file:///raw.mp4",
            rawDurationMs = 1_500L,
            rawDurationSource = "metadata_retriever",
            overlayTimeline = OverlayTimeline(
                startedAtMs = sessionStart,
                sampleIntervalMs = 80L,
                frames = listOf(
                    timelineFrame(relativeTs = 120L, absoluteTs = sessionStart + 120L),
                    timelineFrame(relativeTs = 450L, absoluteTs = sessionStart + 450L),
                ),
            ),
            overlayTimelineUri = null,
            overlayFrameCount = 2,
        )

        val preflight = prepareExportSnapshotInputs(
            snapshot = snapshot,
            overlayCaptureFrozen = true,
            hasReadableRaw = true,
            toleranceMs = 600L,
            liveOverlayFrameCountAtFreeze = 2,
        )

        assertEquals("EXPORT_INPUT_VALIDATION_FAILED_TIMESTAMPS_NOT_NORMALIZED", preflight.fatalReason)
    }


    @Test
    fun normalizeFrozenOverlayTimelineKeepsAlreadyValidRelativeTimestampsUnchanged() {
        val sessionStart = 1_700_000_000_000L
        val timeline = OverlayTimeline(
            startedAtMs = sessionStart,
            sampleIntervalMs = 80L,
            frames = listOf(
                timelineFrame(relativeTs = 300L, absoluteTs = sessionStart + 305L),
                timelineFrame(relativeTs = 700L, absoluteTs = sessionStart + 710L),
            ),
        )

        val normalized = normalizeFrozenOverlayTimelineToRelative(timeline)

        assertEquals(listOf(300L, 700L), normalized.frames.map { it.relativeTimestampMs })
        assertEquals(listOf(300L, 700L), normalized.frames.map { it.timestampMs })
    }

    @Test
    fun normalizeFrozenOverlayTimelineFallsBackToDerivedRelativeWhenStoredRelativeLooksStale() {
        val sessionStart = 1_700_000_000_000L
        val timeline = OverlayTimeline(
            startedAtMs = sessionStart,
            sampleIntervalMs = 80L,
            frames = listOf(
                timelineFrame(relativeTs = 0L, absoluteTs = sessionStart + 250L),
                timelineFrame(relativeTs = 10L, absoluteTs = sessionStart + 550L),
            ),
        )

        val normalized = normalizeFrozenOverlayTimelineToRelative(timeline)

        assertEquals(listOf(250L, 550L), normalized.frames.map { it.relativeTimestampMs })
        assertEquals(listOf(250L, 550L), normalized.frames.map { it.timestampMs })
    }

    @Test
    fun preflightUsesFrozenSnapshotAndDoesNotZeroValidOverlayWhenLiveBufferGrows() {
        val snapshot = ExportSnapshot(
            sessionId = 31L,
            stopTimestampMs = 1000L,
            rawUri = "file:///raw.mp4",
            rawDurationMs = 1_000L,
            rawDurationSource = "metadata_retriever",
            overlayTimeline = timelineOf(100L, 400L, 900L),
            overlayTimelineUri = null,
            overlayFrameCount = 3,
        )

        val preflight = prepareExportSnapshotInputs(
            snapshot = snapshot,
            overlayCaptureFrozen = true,
            hasReadableRaw = true,
            toleranceMs = 100L,
            liveOverlayFrameCountAtFreeze = 30,
        )

        assertNull(preflight.fatalReason)
        assertEquals(3, preflight.snapshot.overlayTimeline.frames.size)
        assertEquals(3, preflight.frozenOverlayFrameCount)
        assertEquals(27, preflight.overlayFramesIgnoredAfterFreeze)
    }

    @Test
    fun preflightAllowsExportWhenRawPersistsAndFrozenOverlayExists() {
        val snapshot = ExportSnapshot(
            sessionId = 32L,
            stopTimestampMs = 1000L,
            rawUri = "file:///raw.mp4",
            rawDurationMs = 3_166L,
            rawDurationSource = "metadata_retriever",
            overlayTimeline = timelineOf(80L, 220L, 480L),
            overlayTimelineUri = null,
            overlayFrameCount = 3,
        )

        val preflight = prepareExportSnapshotInputs(
            snapshot = snapshot,
            overlayCaptureFrozen = true,
            hasReadableRaw = true,
            toleranceMs = 600L,
            liveOverlayFrameCountAtFreeze = 3,
        )

        assertNull(preflight.fatalReason)
        assertTrue(preflight.snapshot.overlayTimeline.frames.isNotEmpty())
        assertEquals("metadata_retriever", preflight.snapshot.rawDurationSource)
    }

    @Test
    fun callbackCanUpgradeFromCacheUriToPersistedBlobUri() {
        val cacheUri = "file:///cache/CameraX/transient_capture.mp4"
        val persistedUri = "file:///data/user/0/com.inversioncoach.app/files/session_blobs/session_44/${SessionBlobStorage.RAW_MASTER_FILE_NAME}"

        val first = evaluateFinalizeCallbackAcceptance(existingAcceptedUri = null, incomingUri = cacheUri)
        val upgraded = evaluateFinalizeCallbackAcceptance(existingAcceptedUri = first.acceptedUri, incomingUri = persistedUri)

        assertEquals(FinalizeCallbackAction.ACCEPTED_FIRST, first.action)
        assertEquals(FinalizeCallbackAction.UPGRADED_TO_PERSISTED, upgraded.action)
        assertEquals(persistedUri, upgraded.acceptedUri)
    }

    @Test
    fun firstAcceptedNonBlankFinalizeUriStartsFinalization() {
        val acceptance = evaluateFinalizeCallbackAcceptance(existingAcceptedUri = null, incomingUri = "file:///first.mp4")

        assertEquals(FinalizeCallbackAction.ACCEPTED_FIRST, acceptance.action)
        assertEquals(
            RecorderFinalizeFlowOutcome.START_FINALIZATION,
            resolveRecorderFinalizeFlowOutcome(acceptance.action, isFinalizationInFlight = false),
        )
    }

    @Test
    fun duplicateSameUriDoesNotRestartFinalization() {
        val acceptance = evaluateFinalizeCallbackAcceptance(existingAcceptedUri = "file:///first.mp4", incomingUri = "file:///first.mp4")

        assertEquals(FinalizeCallbackAction.IGNORED_DUPLICATE, acceptance.action)
        assertEquals(
            RecorderFinalizeFlowOutcome.IGNORE_CALLBACK,
            resolveRecorderFinalizeFlowOutcome(acceptance.action, isFinalizationInFlight = false),
        )
    }

    @Test
    fun upgradedPersistedUriStartsFinalizationWhenNotInFlight() {
        val acceptance = evaluateFinalizeCallbackAcceptance(
            existingAcceptedUri = "file:///cache/transient_capture.mp4",
            incomingUri = "file:///data/user/0/com.inversioncoach.app/files/session_blobs/session_44/${SessionBlobStorage.RAW_MASTER_FILE_NAME}",
        )

        assertEquals(FinalizeCallbackAction.UPGRADED_TO_PERSISTED, acceptance.action)
        assertEquals(
            RecorderFinalizeFlowOutcome.START_FINALIZATION,
            resolveRecorderFinalizeFlowOutcome(acceptance.action, isFinalizationInFlight = false),
        )
    }

    @Test
    fun upgradedPersistedUriIgnoredWhenFinalizationAlreadyInFlight() {
        val acceptance = evaluateFinalizeCallbackAcceptance(
            existingAcceptedUri = "file:///cache/transient_capture.mp4",
            incomingUri = "file:///data/user/0/com.inversioncoach.app/files/session_blobs/session_44/${SessionBlobStorage.RAW_MASTER_FILE_NAME}",
        )

        assertEquals(FinalizeCallbackAction.UPGRADED_TO_PERSISTED, acceptance.action)
        assertEquals(
            RecorderFinalizeFlowOutcome.IGNORE_UPGRADE_INFLIGHT,
            resolveRecorderFinalizeFlowOutcome(acceptance.action, isFinalizationInFlight = true),
        )
    }

    @Test
    fun blankFinalizeUriIsIgnored() {
        val acceptance = evaluateFinalizeCallbackAcceptance(existingAcceptedUri = null, incomingUri = "")

        assertEquals(FinalizeCallbackAction.IGNORED_BLANK, acceptance.action)
        assertEquals(
            RecorderFinalizeFlowOutcome.IGNORE_CALLBACK,
            resolveRecorderFinalizeFlowOutcome(acceptance.action, isFinalizationInFlight = false),
        )
    }

    @Test
    fun persistedCanonicalUriIsNotReplacedByLaterCacheUri() {
        val persistedUri = "file:///data/user/0/com.inversioncoach.app/files/session_blobs/session_44/${SessionBlobStorage.RAW_MASTER_FILE_NAME}"
        val redundantCacheUri = "file:///cache/CameraX/transient_capture.mp4"

        val acceptance = evaluateFinalizeCallbackAcceptance(
            existingAcceptedUri = persistedUri,
            incomingUri = redundantCacheUri,
        )

        assertEquals(FinalizeCallbackAction.IGNORED_REDUNDANT, acceptance.action)
        assertEquals(persistedUri, acceptance.acceptedUri)
    }

    @Test
    fun upgradeInFlightDecisionUsesExplicitIgnoreRule() {
        assertFalse(
            resolveRecorderFinalizeFlowOutcome(
                FinalizeCallbackAction.UPGRADED_TO_PERSISTED,
                isFinalizationInFlight = true,
            ) == RecorderFinalizeFlowOutcome.START_FINALIZATION,
        )
    }

    @Test
    fun preflightFailsWhenAllNormalizedFramesAreOutOfRangeAfterTrim() {
        val snapshot = ExportSnapshot(
            sessionId = 23L,
            stopTimestampMs = 1000L,
            rawUri = "file:///raw.mp4",
            rawDurationMs = 100L,
            rawDurationSource = "metadata_retriever",
            overlayTimeline = timelineOf(200L, 300L),
            overlayTimelineUri = null,
            overlayFrameCount = 2,
        )

        val preflight = prepareExportSnapshotInputs(
            snapshot = snapshot,
            overlayCaptureFrozen = true,
            hasReadableRaw = true,
            toleranceMs = 0L,
            liveOverlayFrameCountAtFreeze = 2,
        )

        assertEquals("EXPORT_INPUT_VALIDATION_FAILED_ALL_FRAMES_OUT_OF_RANGE_AFTER_NORMALIZATION", preflight.fatalReason)
        assertEquals(2, preflight.snapshot.overlayTimeline.frames.size)
        assertEquals(0, preflight.overlayFramesIgnoredAfterFreeze)
    }

    private fun timelineFrame(relativeTs: Long, absoluteTs: Long): OverlayTimelineFrame {
        return OverlayTimelineFrame(
            sessionId = 1L,
            relativeTimestampMs = relativeTs,
            absoluteVideoPtsUs = relativeTs * 1000L,
            timestampMs = absoluteTs,
            landmarks = emptyList(),
            skeletonLines = emptyList(),
            headPoint = null,
            hipPoint = null,
            idealLine = null,
            alignmentAngles = emptyMap(),
            visibilityFlags = emptyMap(),
            drillMetadata = OverlayDrillMetadata(
                sessionMode = com.inversioncoach.app.model.SessionMode.FREESTYLE,
                drillCameraSide = null,
                showSkeleton = true,
                showIdealLine = true,
                bodyVisible = true,
                mirrorMode = false,
            ),
        )
    }

    private fun timelineOf(vararg timestamps: Long): OverlayTimeline {
        return OverlayTimeline(
            startedAtMs = 0L,
            sampleIntervalMs = 80L,
            frames = timestamps.mapIndexed { index, ts ->
                OverlayTimelineFrame(
                    sessionId = 1L,
                    relativeTimestampMs = ts,
                    absoluteVideoPtsUs = ts * 1000L,
                    timestampMs = ts,
                    landmarks = emptyList(),
                    skeletonLines = emptyList(),
                    headPoint = null,
                    hipPoint = null,
                    idealLine = null,
                    alignmentAngles = emptyMap(),
                    visibilityFlags = emptyMap(),
                    drillMetadata = OverlayDrillMetadata(
                        sessionMode = com.inversioncoach.app.model.SessionMode.FREESTYLE,
                        drillCameraSide = null,
                        showSkeleton = true,
                        showIdealLine = true,
                        bodyVisible = true,
                        mirrorMode = false,
                    ),
                    sourceFrameIndex = index.toLong(),
                )
            },
        )
    }
}
