package com.inversioncoach.app.ui.results

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.AnnotatedExportStage
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.IssueEvent
import com.inversioncoach.app.model.SessionSource
import com.inversioncoach.app.media.SessionArtifact
import com.inversioncoach.app.media.SessionMediaResolver
import com.inversioncoach.app.media.SessionMediaSourceOpener
import com.inversioncoach.app.media.SessionMediaType
import com.inversioncoach.app.media.SessionVideoSaver
import com.inversioncoach.app.model.sessionMode
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.common.formatPrimaryPerformance
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import com.inversioncoach.app.ui.common.parseSessionMetrics
import com.inversioncoach.app.ui.common.buildSessionSummaryDisplay
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.upload.UploadJobCoordinator
import com.inversioncoach.app.ui.components.DropdownOption
import com.inversioncoach.app.ui.components.ReliableDropdownField
import com.inversioncoach.app.ui.components.SessionMediaActionsCard
import com.inversioncoach.app.ui.live.mediaAssetExists
import com.inversioncoach.app.ui.live.SessionDiagnostics
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ResultsScreen(sessionId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val session by repository.observeSession(sessionId).collectAsState(initial = null)
    val comparison by repository.observeSessionComparison(sessionId).collectAsState(initial = null)
    val drills by repository.getAllDrills().collectAsState(initial = emptyList())
    val templates by repository.observeReferenceTemplates().collectAsState(initial = emptyList())
    val issueTimeline by repository.observeIssueTimeline(sessionId).collectAsState(initial = emptyList())
    val sourceOpener = remember(context) { SessionMediaSourceOpener(context) }
    val mediaResolver = remember(sourceOpener) { SessionMediaResolver(assetExists = sourceOpener::isReadable) }
    val videoSaver = remember(context, sourceOpener) { SessionVideoSaver(context, sourceOpener) }
    val mediaActions = remember(context, sourceOpener, videoSaver) { ResultsMediaActions(context, sourceOpener, videoSaver) }
    val resolvedMedia = remember(session) { session?.let(mediaResolver::resolve) }
    val preferredReplay = resolvedMedia?.preferredReplay
    val replayUri = preferredReplay?.uri
    val replayLabel = when (preferredReplay?.type) {
        SessionMediaType.ANNOTATED -> "Annotated replay"
        SessionMediaType.RAW -> "Raw replay"
        null -> "Replay unavailable"
    }
    val rawUri = (resolvedMedia?.raw as? SessionArtifact.Available)?.uri
    val hasReplay = !replayUri.isNullOrBlank()
    val showRawVideoButton = shouldShowRawVideoButton(replayUri = replayUri, rawUri = rawUri)
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf("") }
    var diagnosticsExpanded by remember { mutableStateOf(false) }
    var persistedDiagnostics by remember { mutableStateOf<String?>(null) }
    var showPromotionDialog by remember { mutableStateOf(false) }
    var promotionError by remember { mutableStateOf<String?>(null) }
    var lastRefreshSignature by remember(sessionId) { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    if (showPromotionDialog && session != null) {
        SaveSessionAsReferenceDialog(
            session = session!!,
            drills = drills,
            initialDrillId = session?.drillId ?: parseInlineMetrics(session?.metricsJson.orEmpty())["drillId"],
            onDismiss = {
                showPromotionDialog = false
                promotionError = null
            },
            onSave = { targetDrillId, referenceName, setBaseline ->
                scope.launch {
                    val saved = repository.promoteSessionToReference(
                        sessionId = sessionId,
                        targetDrillId = targetDrillId,
                        referenceName = referenceName,
                        setAsBaseline = setBaseline,
                    )
                    if (saved == null) {
                        promotionError = "Could not save reference."
                    } else {
                        promotionError = null
                        showPromotionDialog = false
                        val drillName = drills.firstOrNull { it.id == targetDrillId }?.name ?: "selected drill"
                        val message = if (setBaseline) {
                            "Saved as baseline reference for $drillName"
                        } else {
                            "Saved as reference for $drillName"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            errorMessage = promotionError,
        )
    }

    val collapsedIssueTimeline = remember(issueTimeline, session?.startedAtMs) {
        collapseIssueTimeline(issueTimeline, session?.startedAtMs)
    }
    val sessionSummaryDisplay = remember(session) { buildSessionSummaryDisplay(session) }
    val sessionMode = session?.sessionMode()
    val contextTemplateId = session?.let { activeSession ->
        activeSession.referenceTemplateId ?: parseInlineMetrics(activeSession.metricsJson)["referenceTemplateId"]
    }

    val displayDurationMs = session?.let { activeSession ->
        resolveDisplayDurationMs(
            context = context,
            annotatedUri = activeSession.annotatedVideoUri,
            rawUri = activeSession.rawVideoUri,
            sessionDurationMs = (activeSession.completedAtMs - activeSession.startedAtMs).coerceAtLeast(0L),
        )
    } ?: 0L

    LaunchedEffect(sessionId) {
        notes = repository.readSessionNotes(sessionId).orEmpty()
        persistedDiagnostics = repository.readSessionDiagnostics(sessionId)
        repository.reconcileRawPersistState(sessionId)
        repository.reconcileActiveUploadJobs(
            hasActiveWorker = UploadJobCoordinator.isActive(),
            reason = "results_screen_load",
        )
    }


    LaunchedEffect(
        session?.rawPersistStatus,
        session?.annotatedExportStatus,
        session?.annotatedVideoUri,
        replayUri,
    ) {
        val activeSession = session ?: return@LaunchedEffect
        val signature = listOf(
            activeSession.rawPersistStatus,
            activeSession.annotatedExportStatus,
            activeSession.annotatedExportFailureReason.orEmpty(),
            activeSession.annotatedVideoUri.orEmpty(),
            activeSession.bestPlayableUri.orEmpty(),
            replayUri.orEmpty(),
        ).joinToString("|")
        if (lastRefreshSignature == signature) return@LaunchedEffect
        lastRefreshSignature = signature
        SessionDiagnostics.logStructured(
            event = "results_screen_state_refresh",
            sessionId = activeSession.id,
            drillType = activeSession.drillType,
            rawUri = activeSession.rawVideoUri,
            annotatedUri = activeSession.annotatedVideoUri,
            overlayFrameCount = activeSession.overlayFrameCount,
            failureReason = "rawPersistStatus=${activeSession.rawPersistStatus};annotatedExportStatus=${activeSession.annotatedExportStatus};selectedReplaySource=${replayLabel};selectedReplayUri=${replayUri.orEmpty()};terminalStateReached=${activeSession.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_READY || activeSession.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED}",
        )
    }

    ScaffoldedScreen(title = "Results") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Session breakdown", style = MaterialTheme.typography.headlineSmall)
            if (session == null) {
                Text(
                    "Session details are loading or unavailable. Some actions may be disabled.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            session?.let { activeSession ->
                val isProcessing = activeSession.annotatedExportStatus in setOf(
                    AnnotatedExportStatus.VALIDATING_INPUT,
                    AnnotatedExportStatus.PROCESSING,
                    AnnotatedExportStatus.PROCESSING_SLOW,
                ) || activeSession.rawPersistStatus == com.inversioncoach.app.model.RawPersistStatus.PROCESSING
                if (isProcessing || activeSession.annotatedExportStatus in setOf(AnnotatedExportStatus.ANNOTATED_FAILED, AnnotatedExportStatus.SKIPPED)) {
                    val isFailed = activeSession.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED
                    val isSkipped = activeSession.annotatedExportStatus == AnnotatedExportStatus.SKIPPED
                    val rawFallbackAvailable = mediaResolver.resolve(activeSession).raw is SessionArtifact.Available
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                when {
                                    rawFallbackAvailable && (isFailed || isSkipped) -> "Raw replay available"
                                    isSkipped -> "Annotated export skipped"
                                    isFailed -> "Annotated export failed"
                                    else -> "Processing status"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (isProcessing) {
                                val uploadStageText = when (activeSession.annotatedExportStage) {
                                    AnnotatedExportStage.PREPARING,
                                    AnnotatedExportStage.DECODING_SOURCE,
                                    -> "Analyzing uploaded video"

                                    AnnotatedExportStage.LOADING_OVERLAYS,
                                    AnnotatedExportStage.RENDERING,
                                    -> "Rendering annotated video"

                                    AnnotatedExportStage.ENCODING -> "Exporting video"
                                    AnnotatedExportStage.VERIFYING -> "Verifying output"
                                    else -> "Processing uploaded video"
                                }
                                LinearProgressIndicator(
                                    progress = { (activeSession.annotatedExportPercent.coerceIn(0, 100) / 100f).coerceAtLeast(0.05f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(activeSession.uploadPipelineStageLabel ?: uploadStageText)
                                if (activeSession.uploadAnalysisTotalFrames > 0) {
                                    Text(
                                        "Analyzing movement: ${activeSession.uploadAnalysisProcessedFrames.coerceAtMost(activeSession.uploadAnalysisTotalFrames)} / ${activeSession.uploadAnalysisTotalFrames} frames",
                                    )
                                }
                                activeSession.uploadProgressDetail
                                    ?.takeIf { it.isNotBlank() && it != activeSession.uploadPipelineStageLabel }
                                    ?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Text("Progress: ${activeSession.annotatedExportPercent}%")
                                Text("ETA: ${activeSession.annotatedExportEtaSeconds?.let { "${it}s" } ?: "-"}")
                            } else if (isSkipped) {
                                Text("Status: ${humanReadableStatus("SKIPPED")}")
                                if (rawFallbackAvailable) {
                                    Text("Annotated export failed. Raw replay is available.")
                                }
                            } else {
                                Text("Status: ${humanReadableStatus("FAILED")}")
                                if (rawFallbackAvailable) {
                                    Text("Annotated export failed. Raw replay is available.")
                                }
                            }
                            StatusRow("Replay source", if (rawFallbackAvailable && !isProcessing) "Raw" else replayLabel.removeSuffix(" replay"))
                            StatusRow("Raw", humanReadableStatus(activeSession.rawPersistStatus.name))
                            if (activeSession.rawPersistFailureReason in setOf("RAW_REPLAY_INVALID", "RAW_MEDIA_CORRUPT", "SOURCE_VIDEO_UNREADABLE")) {
                                Text(
                                    "Raw replay file was copied but is not decodable (${activeSession.rawPersistFailureReason}).",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            StatusRow("Annotated", humanReadableStatus(activeSession.annotatedExportStatus.name))
                            if (!activeSession.annotatedExportFailureReason.isNullOrBlank()) {
                                StatusRow(
                                    "Failure reason",
                                    humanReadableStatus(activeSession.annotatedExportFailureReason.orEmpty()),
                                    valueColor = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (isProcessing) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
                                        repository.updateAnnotatedExportFailureReason(sessionId, "EXPORT_CANCELLED")
                                    }
                                }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Session ID: $sessionId")
                    Text("Type: ${sessionTypeLabel(session)}")
                    resolveDrillContextLabel(session, drills)?.let { drillContext ->
                        Text("Drill: $drillContext")
                    }
                    if (!contextTemplateId.isNullOrBlank()) {
                        val contextTemplateName = templates.firstOrNull { it.id == contextTemplateId }?.displayName
                        Text("Template context: ${contextTemplateName ?: contextTemplateId}")
                    }
                    session?.referenceTemplateId?.let { linkedTemplateId ->
                        val linkedTemplateName = templates.firstOrNull { it.id == linkedTemplateId }?.displayName
                        Text(
                            "Linked template: ${linkedTemplateName ?: linkedTemplateId}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("Started: ${formatSessionDateTime(session?.startedAtMs ?: 0L)}")
                    Text("Duration: ${formatSessionDuration(displayDurationMs)}")
                    session?.let {
                        Text(
                            "Profile: ${it.userProfileId ?: "unknown"} • Body v${it.bodyProfileVersion ?: 0}" +
                                if (it.usedDefaultBodyModel) " (default model)" else "",
                        )
                    }
                    session?.let { Text(formatPrimaryPerformance(it)) }
                    session?.let {
                        val metrics = parseSessionMetrics(it.metricsJson)
                        val hasHoldMetrics =
                            metrics.alignedDurationMs != null &&
                                metrics.bestAlignedStreakMs != null &&
                                metrics.sessionTrackedMs != null &&
                                metrics.alignmentRate != null &&
                                metrics.avgStability != null
                        val hasRepMetrics =
                            (metrics.acceptedReps != null || metrics.validReps != null) &&
                                metrics.rawRepAttempts != null &&
                                metrics.rejectedReps != null &&
                                metrics.avgRepScore != null
                        if (metrics.trackingMode == "HOLD_BASED" && hasHoldMetrics) {
                            Text("Alignment %: ${((metrics.alignmentRate ?: 0f) * 100f).toInt()} • Avg alignment: ${metrics.avgAlignment ?: 0}")
                            Text("Avg stability: ${metrics.avgStability ?: 0}")
                        } else if (metrics.trackingMode == "REP_BASED" && hasRepMetrics) {
                            Text("Accepted reps: ${metrics.acceptedReps ?: metrics.validReps ?: 0} • Rejected: ${metrics.rejectedReps ?: 0}")
                            Text("Avg rep score: ${metrics.avgRepScore ?: 0} • Best rep: ${metrics.bestRepScore ?: 0}")
                            if (!metrics.repFailureReason.isNullOrBlank()) Text("Top failure reason: ${metrics.repFailureReason}")
                        } else if (it.sessionSource == SessionSource.UPLOADED_VIDEO) {
                            Text("Upload analysis metrics are not available yet.")
                        }
                    }
                    comparison?.let { comparisonRecord ->
                        val metrics = parseInlineMetrics(session?.metricsJson.orEmpty())
                        val drillId = metrics["drillId"] ?: comparisonRecord.drillId
                        val drillName = drills.firstOrNull { it.id == drillId }?.name ?: drillId
                        Text("Selected drill: $drillName")
                        val resolvedTemplateName = metrics["referenceTemplateName"]
                            ?: templates.firstOrNull { it.id == comparisonRecord.templateId }?.displayName
                            ?: comparisonRecord.templateId
                        Text("Reference template: $resolvedTemplateName")
                        Text("Overall similarity: ${comparisonRecord.overallSimilarityScore}/100")
                        Text(
                            "Phase scores: ${formatInlinePairs(comparisonRecord.phaseScoresJson)}",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Top differences: ${comparisonRecord.differencesJson.split('|').filter { diff -> diff.isNotBlank() }.take(3).joinToString("; ")}",
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("Scoring version: ${comparisonRecord.scoringVersion}")
                    }
                    Text(
                        "Top wins: ${sessionSummaryDisplay.wins}",
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Top issues: ${sessionSummaryDisplay.issues}",
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Top improvement focus: ${sessionSummaryDisplay.improvement}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Issue timeline summary")
                    if (sessionMode == SessionMode.FREESTYLE) {
                        Text("Issue timeline is not tracked for freestyle sessions")
                    } else if (collapsedIssueTimeline.isEmpty()) {
                        Text("No issue events captured for this session")
                    } else {
                        collapsedIssueTimeline.forEach { summary ->
                            Text(summary)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Session notes") },
            )

            resolvedMedia?.let { media ->
                SessionMediaActionsCard(
                    media = media,
                    onPlayRaw = { uri -> mediaActions.openVideo(uri, sessionId) },
                    onSaveRaw = { uri ->
                        mediaActions.saveRaw(
                            sourceUri = uri,
                            sessionName = session?.title.orEmpty(),
                            sessionTimestampMs = session?.completedAtMs ?: session?.startedAtMs ?: 0L,
                        )
                    },
                    onPlayAnnotated = { uri -> mediaActions.openVideo(uri, sessionId) },
                    onSaveAnnotated = { uri ->
                        mediaActions.saveAnnotated(
                            sourceUri = uri,
                            sessionName = session?.title.orEmpty(),
                            sessionTimestampMs = session?.completedAtMs ?: session?.startedAtMs ?: 0L,
                        )
                    },
                )
            }
            if (!hasReplay) {
                val rawFailure = session?.rawPersistFailureReason
                val message = if (rawFailure in setOf("RAW_REPLAY_INVALID", "RAW_MEDIA_CORRUPT", "SOURCE_VIDEO_UNREADABLE")) {
                    "Replay unavailable: raw file exists but cannot be decoded."
                } else {
                    "No replay asset is available for this session."
                }
                Text(message)
            }
            Text(replayAvailabilityBadge(replayLabel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showRawVideoButton) {
                Button(onClick = { mediaActions.openVideo(rawUri) }, enabled = !rawUri.isNullOrBlank(), modifier = Modifier.fillMaxWidth()) { Text("Raw replay") }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val inMemoryReport = SessionDiagnostics.buildReport(session, sessionId)
                    val report = persistedDiagnostics?.takeIf { it.isNotBlank() } ?: inMemoryReport
                    val events = SessionDiagnostics.eventsForSession(sessionId)
                    val canCopyDiagnostics = report.isNotBlank()
                    Text("Developer diagnostics", style = MaterialTheme.typography.titleMedium)
                    Text(SessionDiagnostics.rootCauseSummary(session, events))
                    StatusRow("Replay source", if (replayLabel == "Annotated replay") "Annotated" else if (replayLabel == "Raw replay") "Raw" else "None")
                    StatusRow("Raw", humanReadableStatus(session?.rawPersistStatus?.name))
                    StatusRow("Annotated", humanReadableStatus(session?.annotatedExportStatus?.name))
                    session?.annotatedExportFailureReason?.takeIf { it.isNotBlank() }?.let { reason ->
                        StatusRow("Failure", humanReadableStatus(reason), valueColor = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { diagnosticsExpanded = !diagnosticsExpanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (diagnosticsExpanded) "Hide diagnostics" else "Show diagnostics")
                    }
                    if (canCopyDiagnostics) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(report))
                                Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Copy diagnostics log") }
                    }
                    if (diagnosticsExpanded) {
                        Text(
                            buildDeveloperStateDump(session, replayLabel),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 16,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            report,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 24,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        repository.saveSessionNotes(sessionId, notes)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save note") }
            Button(
                onClick = { showPromotionDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = session != null,
            ) { Text("Use for Drill Reference") }
            Button(
                onClick = {
                    mediaActions.sharePreferred(resolvedMedia)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasReplay,
            ) { Text("Share video (.mp4)") }
            Button(
                onClick = {
                    scope.launch {
                        repository.clearSessionVideos(sessionId)
                        Toast.makeText(context, "Session videos deleted. Session history kept.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasReplay,
            ) {
                Text("Delete videos only (keep session)")
            }
            Button(
                onClick = {
                    scope.launch {
                        repository.deleteSession(sessionId)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete this session and all data")
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun SaveSessionAsReferenceDialog(
    session: com.inversioncoach.app.model.SessionRecord,
    drills: List<com.inversioncoach.app.model.DrillDefinitionRecord>,
    initialDrillId: String?,
    onDismiss: () -> Unit,
    onSave: (targetDrillId: String, referenceName: String?, setBaseline: Boolean) -> Unit,
    errorMessage: String?,
) {
    val drillOptions = remember(drills) {
        drills
            .filter { it.status == DrillStatus.READY }
            .map { DropdownOption(it.id, it.name) }
    }
    var selectedDrillId by remember(initialDrillId, drillOptions) {
        mutableStateOf(initialDrillId?.takeIf { id -> drillOptions.any { it.value == id } } ?: drillOptions.firstOrNull()?.value)
    }
    var referenceName by remember(session.id) { mutableStateOf(session.title.takeIf { it.isNotBlank() }?.let { "$it Reference" }.orEmpty()) }
    var setAsBaseline by remember(session.id) { mutableStateOf(false) }
    val selectedOption = drillOptions.firstOrNull { it.value == selectedDrillId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Session as Drill Reference") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (drillOptions.isEmpty()) {
                    Text("No drills available. Create or import a drill first.")
                } else {
                    ReliableDropdownField(
                        label = "Drill",
                        selected = selectedOption ?: drillOptions.first(),
                        options = drillOptions,
                        onOptionSelected = { selectedDrillId = it.value },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = referenceName,
                    onValueChange = { referenceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reference name") },
                    placeholder = { Text("Reference <date/time>") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = setAsBaseline, onCheckedChange = { setAsBaseline = it })
                    Text("Set as baseline for this drill")
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedDrillId?.let { onSave(it, referenceName.takeIf(String::isNotBlank), setAsBaseline) } },
                enabled = selectedDrillId != null && drillOptions.isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}



internal fun pickDisplayDurationMs(
    annotatedDurationMs: Long?,
    rawDurationMs: Long?,
    sessionDurationMs: Long,
): Long = annotatedDurationMs ?: rawDurationMs ?: sessionDurationMs

private fun resolveDisplayDurationMs(
    context: android.content.Context,
    annotatedUri: String?,
    rawUri: String?,
    sessionDurationMs: Long,
): Long = pickDisplayDurationMs(
    annotatedDurationMs = extractVideoDurationMs(context, annotatedUri),
    rawDurationMs = extractVideoDurationMs(context, rawUri),
    sessionDurationMs = sessionDurationMs,
)

private fun extractVideoDurationMs(
    context: android.content.Context,
    uriString: String?,
): Long? {
    if (uriString.isNullOrBlank()) return null
    return runCatching {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, android.net.Uri.parse(uriString))
            retriever
                .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        } finally {
            retriever.release()
        }
    }.getOrNull()
}

private fun mediaDurationMs(uri: String?): Long {
    val target = uri?.takeIf(::mediaAssetExists) ?: return 0L
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        val path = android.net.Uri.parse(target).path ?: return 0L
        retriever.setDataSource(path)
        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Throwable) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}


private fun sessionTypeLabel(session: com.inversioncoach.app.model.SessionRecord?): String {
    val active = session ?: return "-"
    return when (active.sessionSource) {
        SessionSource.UPLOADED_VIDEO -> "Uploaded video analysis"
        SessionSource.LIVE_COACHING -> active.drillType.displayName
    }
}

private fun resolveDrillContextLabel(
    session: com.inversioncoach.app.model.SessionRecord?,
    drills: List<com.inversioncoach.app.model.DrillDefinitionRecord>,
): String? {
    val active = session ?: return null
    val metrics = parseInlineMetrics(active.metricsJson)
    val drillId = metrics["drillId"]?.takeIf { it.isNotBlank() }
    if (drillId != null) {
        return drills.firstOrNull { it.id == drillId }?.name ?: drillId
    }
    return if (active.sessionSource == SessionSource.LIVE_COACHING) active.drillType.displayName else null
}

private fun formatDurationWithMs(durationMs: Long): String = "${formatSessionDuration(durationMs)} (${durationMs} ms)"

internal fun shouldShowRawVideoButton(replayUri: String?, rawUri: String?): Boolean =
    !rawUri.isNullOrBlank() && replayUri != rawUri

internal fun replayAvailabilityBadge(replayLabel: String): String =
    if (replayLabel == "Annotated replay") "Annotated Replay Ready" else "Raw Only"


internal fun formatElapsedDuration(elapsedMs: Long?): String {
    val safeMs = (elapsedMs ?: 0L).coerceAtLeast(0L)
    val totalSec = safeMs / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return if (min > 0L) "${min}m ${sec}s" else "${sec}s"
}

private fun parseInlineMetrics(raw: String): Map<String, String> =
    raw.split('|')
        .mapNotNull { token ->
            val index = token.indexOf(':')
            if (index <= 0) null else token.substring(0, index) to token.substring(index + 1)
        }
        .toMap()

private fun formatInlinePairs(raw: String): String =
    raw.split('|')
        .mapNotNull { token ->
            val index = token.indexOf(':')
            if (index <= 0) null else "${token.substring(0, index)} ${token.substring(index + 1)}"
        }
        .joinToString(", ")

@Composable
private fun StatusRow(
    label: String,
    value: String?,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$label:")
        Text(value, color = valueColor)
    }
}

private fun humanReadableStatus(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return value
        .replace('_', ' ')
        .lowercase(Locale.US)
        .replaceFirstChar { it.titlecase(Locale.US) }
}

private fun buildDeveloperStateDump(
    session: com.inversioncoach.app.model.SessionRecord?,
    replayLabel: String,
): String = buildString {
    appendLine("replay source selected: ${if (replayLabel == "Annotated replay") "annotated" else if (replayLabel == "Raw replay") "raw" else "none"}")
    appendLine("rawPersistStatus: ${session?.rawPersistStatus}")
    appendLine("rawVideoUri: ${session?.rawVideoUri.orEmpty()}")
    appendLine("annotatedExportStatus: ${session?.annotatedExportStatus}")
    appendLine("annotatedExportFailureReason: ${session?.annotatedExportFailureReason.orEmpty()}")
    appendLine("annotatedVideoUri: ${session?.annotatedVideoUri.orEmpty()}")
    appendLine("overlay frame count: ${session?.overlayFrameCount ?: 0}")
    appendLine("overlayTimelineUri: ${session?.overlayTimelineUri.orEmpty()}")
    appendLine("export started at: ${session?.annotatedExportLastUpdatedAt ?: 0L}")
    appendLine("export completed at: ${session?.annotatedExportedAtMs ?: 0L}")
    appendLine("raw duration: ${formatDurationWithMs(mediaDurationMs(session?.rawVideoUri))}")
    appendLine("annotated duration: ${formatDurationWithMs(mediaDurationMs(session?.annotatedVideoUri))}")
}

private data class CollapsedIssueRange(
    val issue: String,
    val startMs: Long,
    val endMs: Long,
    val peakSeverity: Int,
)

private fun collapseIssueTimeline(issueEvents: List<IssueEvent>, sessionStartMs: Long?): List<String> {
    if (issueEvents.isEmpty()) return emptyList()
    val sorted = issueEvents.sortedBy { it.timestampMs }
    val merged = mutableListOf<CollapsedIssueRange>()
    val maxGapMs = 1200L

    for (event in sorted) {
        val last = merged.lastOrNull()
        val shouldMerge = last != null &&
            last.issue == event.issue &&
            event.timestampMs - last.endMs <= maxGapMs

        if (shouldMerge) {
            merged[merged.lastIndex] = last!!.copy(
                endMs = event.timestampMs,
                peakSeverity = maxOf(last.peakSeverity, event.severity),
            )
        } else {
            merged += CollapsedIssueRange(
                issue = event.issue,
                startMs = event.timestampMs,
                endMs = event.timestampMs,
                peakSeverity = event.severity,
            )
        }
    }

    return merged.map { range ->
        val start = formatElapsed(sessionStartMs, range.startMs)
        val end = formatElapsed(sessionStartMs, range.endMs)
        val durationSec = ((range.endMs - range.startMs).coerceAtLeast(0L)) / 1000.0
        if (start == end) {
            "$start ${range.issue} (peak sev ${range.peakSeverity})"
        } else {
            "$start–$end ${range.issue} (${String.format("%.1f", durationSec)}s, peak sev ${range.peakSeverity})"
        }
    }
}

private fun formatElapsed(startedAtMs: Long?, timestampMs: Long?): String {
    if (startedAtMs == null || timestampMs == null || timestampMs < startedAtMs) return "--:--"
    val elapsedSeconds = ((timestampMs - startedAtMs) / 1000).toInt()
    return "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
}

