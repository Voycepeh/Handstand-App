package com.inversioncoach.app.ui.live

import com.inversioncoach.app.recording.OverlayTimeline
import com.inversioncoach.app.storage.SessionBlobStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
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
    fun retrieverFailsExtractorSucceeds() = runBlocking {
        val resolved = resolveRawDurationWithRetries(
            rawUri = "file:///raw.mp4",
            fileExists = true,
            fileSizeBytes = 2048L,
            sessionElapsedMs = 5_000L,
            overlayMaxTimestampMs = 6_000L,
            maxRetries = 0,
            readMetadataDuration = { 0L },
            readExtractorDuration = { 1_777L },
        )

        assertEquals(1_777L, resolved.durationMs)
        assertEquals("media_extractor", resolved.source)
    }

    @Test
    fun validationPassesWhenRawExistsAndFallbackDurationAvailable() {
        val timeline = OverlayTimeline(startedAtMs = 100L, sampleIntervalMs = 80L, frames = emptyList())
        val validation = validateExportSnapshotInputs(
            rawUri = "file:///raw.mp4",
            rawDurationMs = 2_000L,
            rawDurationSource = "session_elapsed",
            overlayTimeline = timeline,
            overlayFrameCount = 10,
            overlayCaptureFrozen = true,
            hasReadableRaw = true,
            toleranceMs = 600L,
        )

        assertNull(validation)
    }

    @Test
    fun validationFailsWhenAllDurationSourcesFail() {
        val timeline = OverlayTimeline(startedAtMs = 100L, sampleIntervalMs = 80L, frames = emptyList())
        val validation = validateExportSnapshotInputs(
            rawUri = "file:///raw.mp4",
            rawDurationMs = 0L,
            rawDurationSource = "none",
            overlayTimeline = timeline,
            overlayFrameCount = 5,
            overlayCaptureFrozen = true,
            hasReadableRaw = true,
            toleranceMs = 600L,
        )

        assertEquals("EXPORT_INPUT_VALIDATION_FAILED_RAW_DURATION_ALL_SOURCES", validation)
    }

    @Test
    fun validationFailsWhenRawMissingAndNoDurationFallback() {
        val timeline = OverlayTimeline(startedAtMs = 100L, sampleIntervalMs = 80L, frames = emptyList())
        val validation = validateExportSnapshotInputs(
            rawUri = "file:///missing.mp4",
            rawDurationMs = 0L,
            rawDurationSource = "none",
            overlayTimeline = timeline,
            overlayFrameCount = 0,
            overlayCaptureFrozen = true,
            hasReadableRaw = false,
            toleranceMs = 600L,
        )

        assertEquals("EXPORT_INPUT_VALIDATION_FAILED_RAW_MISSING_AND_DURATION_UNAVAILABLE", validation)
    }

    @Test
    fun duplicateFinalizeCallbacksAreIgnoredWithoutRestarting() {
        val first = evaluateFinalizeCallbackAcceptance(existingAcceptedUri = null, incomingUri = "file:///first.mp4")
        val duplicate = evaluateFinalizeCallbackAcceptance(existingAcceptedUri = first.acceptedUri, incomingUri = "file:///first.mp4")

        assertEquals(FinalizeCallbackAction.ACCEPTED_FIRST, first.action)
        assertEquals(FinalizeCallbackAction.IGNORED_DUPLICATE, duplicate.action)
        assertEquals("file:///first.mp4", duplicate.acceptedUri)
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
    fun upgradeInFlightDecisionUsesExplicitIgnoreRule() {
        assertFalse(
            resolveRecorderFinalizeFlowOutcome(
                FinalizeCallbackAction.UPGRADED_TO_PERSISTED,
                isFinalizationInFlight = true,
            ) == RecorderFinalizeFlowOutcome.START_FINALIZATION,
        )
    }
}
