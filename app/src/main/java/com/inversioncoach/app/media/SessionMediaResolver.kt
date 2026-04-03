package com.inversioncoach.app.media

import android.util.Log
import com.inversioncoach.app.model.AnnotatedExportStage
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionRecord

class SessionMediaResolver(
    private val assetExists: (String?) -> Boolean,
) {
    private companion object {
        const val TAG = "ExportStateGuard"
    }

    fun resolve(session: SessionRecord): ResolvedSessionMedia {
        val rawMarkedInvalid = SessionMediaOwnership.isRawReplayBlocked(session)
        val rawUri = SessionMediaOwnership.rawCandidates(session).firstOrNull(assetExists)
        val annotatedUri = SessionMediaOwnership.annotatedCandidates(session).firstOrNull(assetExists)

        val raw = when {
            rawMarkedInvalid -> SessionArtifact.Unavailable(SessionArtifactError.RAW_INVALID)
            rawUri != null -> SessionArtifact.Available(rawUri)
            session.rawPersistStatus == RawPersistStatus.PROCESSING -> SessionArtifact.Processing("Raw video is still being prepared")
            else -> SessionArtifact.Unavailable(SessionArtifactError.MISSING_FILE)
        }

        val annotated = when {
            annotatedUri != null -> SessionArtifact.Available(annotatedUri)
            session.annotatedExportStatus in setOf(
                AnnotatedExportStatus.VALIDATING_INPUT,
                AnnotatedExportStatus.PROCESSING,
                AnnotatedExportStatus.PROCESSING_SLOW,
            ) -> SessionArtifact.Processing(processingLabel(session))
            session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED -> SessionArtifact.Unavailable(SessionArtifactError.EXPORT_FAILED)
            else -> SessionArtifact.Unavailable(SessionArtifactError.NO_ANNOTATED_OUTPUT)
        }

        val preferredReplay = when {
            annotated is SessionArtifact.Available -> PreferredReplay(annotated.uri, SessionMediaType.ANNOTATED)
            raw is SessionArtifact.Available -> PreferredReplay(raw.uri, SessionMediaType.RAW)
            else -> null
        }
        Log.d(
            TAG,
            "replay_source_resolution sessionId=${session.id} selected=${preferredReplay?.type ?: "NONE"} annotatedStatus=${session.annotatedExportStatus} rawStatus=${session.rawPersistStatus}",
        )

        return ResolvedSessionMedia(
            raw = raw,
            annotated = annotated,
            preferredReplay = preferredReplay,
        )
    }

    private fun processingLabel(session: SessionRecord): String {
        val base = when (session.annotatedExportStage) {
            AnnotatedExportStage.QUEUED -> "Queued"
            AnnotatedExportStage.PREPARING -> "Preparing"
            AnnotatedExportStage.LOADING_OVERLAYS -> "Building overlays"
            AnnotatedExportStage.DECODING_SOURCE -> "Analyzing frames"
            AnnotatedExportStage.RENDERING -> "Rendering"
            AnnotatedExportStage.ENCODING -> "Encoding"
            AnnotatedExportStage.VERIFYING -> "Verifying"
            AnnotatedExportStage.COMPLETED -> "Completed"
            AnnotatedExportStage.FAILED -> "Failed"
        }
        return "$base ${session.annotatedExportPercent.coerceIn(0, 100)}%"
    }

}

data class ResolvedSessionMedia(
    val raw: SessionArtifact,
    val annotated: SessionArtifact,
    val preferredReplay: PreferredReplay?,
) {
    fun canonicalActionSource(): PreferredReplay? = preferredReplay
}

data class PreferredReplay(
    val uri: String,
    val type: SessionMediaType,
)

enum class SessionMediaType { RAW, ANNOTATED }

sealed interface SessionArtifact {
    data class Available(val uri: String) : SessionArtifact
    data class Processing(val message: String) : SessionArtifact
    data class Unavailable(val error: SessionArtifactError) : SessionArtifact
}

enum class SessionArtifactError {
    EXPORT_FAILED,
    MISSING_FILE,
    NO_ANNOTATED_OUTPUT,
    RAW_INVALID,
}
