package com.inversioncoach.app.upload

import android.net.Uri
import com.inversioncoach.app.ui.upload.UploadFlowResult
import com.inversioncoach.app.ui.upload.UploadProgress
import com.inversioncoach.app.ui.upload.UploadTrackingMode
import com.inversioncoach.app.ui.upload.UploadVideoAnalysisRunner

class UploadAnalysisOrchestrator(
    private val runner: UploadVideoAnalysisRunner,
) {
    suspend fun execute(
        uri: Uri,
        ownerToken: String,
        trackingMode: UploadTrackingMode,
        selectedDrillId: String?,
        selectedReferenceTemplateId: String?,
        isReferenceUpload: Boolean,
        createDrillFromReferenceUpload: Boolean,
        pendingDrillName: String?,
        onSessionCreated: (Long) -> Unit,
        onProgress: (UploadProgress) -> Unit,
        onLog: (String) -> Unit,
    ): UploadFlowResult {
        return runner.run(
            uri = uri,
            ownerToken = ownerToken,
            trackingMode = trackingMode,
            selectedDrillId = selectedDrillId,
            selectedReferenceTemplateId = selectedReferenceTemplateId,
            isReferenceUpload = isReferenceUpload,
            createDrillFromReferenceUpload = createDrillFromReferenceUpload,
            pendingDrillName = pendingDrillName,
            onSessionCreated = onSessionCreated,
            onProgress = onProgress,
            onLog = onLog,
        )
    }
}
