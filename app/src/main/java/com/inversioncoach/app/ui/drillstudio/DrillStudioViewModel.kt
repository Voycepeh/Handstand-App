package com.inversioncoach.app.ui.drillstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inversioncoach.app.drills.catalog.AnalysisPlane
import com.inversioncoach.app.drills.catalog.CalibrationTemplate
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.CatalogNormalizationBasis
import com.inversioncoach.app.drills.catalog.ComparisonMode
import com.inversioncoach.app.drills.catalog.DrillCatalogRepository
import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.drills.catalog.SkeletonKeyframeTemplate
import com.inversioncoach.app.drills.catalog.SkeletonTemplate
import com.inversioncoach.app.drills.DrillCameraView
import com.inversioncoach.app.drills.DrillMovementMode
import com.inversioncoach.app.drills.DrillSourceType
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.storage.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DrillStudioInitRequest(
    val mode: String = "drill",
    val drillId: String? = null,
    val templateId: String? = null,
)

sealed interface DrillStudioUiState {
    data object Loading : DrillStudioUiState
    data class Error(val message: String) : DrillStudioUiState
    data class Ready(
        val draft: DrillTemplate,
        val sourceSeedId: String?,
        val editingDrillId: String? = null,
        val editingTemplateId: String? = null,
        val validationErrors: List<String> = emptyList(),
        val statusMessage: String? = null,
    ) : DrillStudioUiState
}

class DrillStudioViewModel(
    private val repository: DrillCatalogRepository,
    private val sessionRepository: SessionRepository? = null,
    private val catalogLoader: (() -> com.inversioncoach.app.drills.catalog.DrillCatalog)? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DrillStudioUiState>(DrillStudioUiState.Loading)
    val uiState: StateFlow<DrillStudioUiState> = _uiState.asStateFlow()

    fun initialize(request: DrillStudioInitRequest) {
        viewModelScope.launch {
            _uiState.value = DrillStudioUiState.Loading
            val result = runCatching {
                val catalog = catalogLoader?.invoke() ?: repository.loadCatalog()
                val templateRecord = request.templateId?.let { templateId -> sessionRepository?.getReferenceTemplate(templateId) }
                val seedDrillId = templateRecord?.drillId ?: request.drillId
                val seed = when {
                    request.mode == "create" -> null
                    seedDrillId != null -> resolveSeedById(catalog.drills, seedDrillId)
                    else -> catalog.drills.firstOrNull()
                }
                val draftResult = resolveInitialDraft(
                    mode = request.mode,
                    templateRecord = templateRecord,
                    seed = seed,
                )
                val normalized = normalizeDraft(draftResult.draft.template)
                DrillStudioUiState.Ready(
                    draft = normalized,
                    sourceSeedId = draftResult.draft.sourceSeedId,
                    editingDrillId = templateRecord?.drillId ?: request.drillId ?: seed?.id,
                    editingTemplateId = templateRecord?.id,
                    statusMessage = draftResult.statusMessage,
                )
            }

            _uiState.value = result.getOrElse { throwable ->
                DrillStudioUiState.Error(
                    throwable.message ?: "Failed to load Drill Studio editor data.",
                )
            }
        }
    }

    fun updateDraft(transform: (DrillTemplate) -> DrillTemplate) {
        val current = _uiState.value as? DrillStudioUiState.Ready ?: return
        val updated = normalizeDraft(transform(current.draft))
        DrillStudioDraftRepository.saveDraft(
            template = updated,
            sourceSeedId = current.sourceSeedId,
        )
        _uiState.value = current.copy(draft = updated, validationErrors = emptyList(), statusMessage = null)
    }

    fun addPhase() = mutateReadyDraft { current ->
        val (nextPhases, nextPoses) = DrillStudioPhaseEditor.addPhase(
            phases = current.phases,
            poses = current.skeletonTemplate.phasePoses,
            defaultJoints = defaultJoints(),
        )
        current.copy(
            phases = nextPhases,
            skeletonTemplate = current.skeletonTemplate.copy(phasePoses = nextPoses),
        )
    }

    fun duplicatePhase(phaseId: String) = mutateReadyDraft { current ->
        val (nextPhases, nextPoses) = DrillStudioPhaseEditor.duplicatePhase(
            phases = current.phases,
            poses = current.skeletonTemplate.phasePoses,
            phaseId = phaseId,
        )
        current.copy(
            phases = nextPhases,
            skeletonTemplate = current.skeletonTemplate.copy(phasePoses = nextPoses),
        )
    }

    fun deletePhase(phaseId: String) = mutateReadyDraft { current ->
        val (nextPhases, nextPoses) = DrillStudioPhaseEditor.deletePhase(
            phases = current.phases,
            poses = current.skeletonTemplate.phasePoses,
            phaseId = phaseId,
        )
        current.copy(
            phases = nextPhases,
            skeletonTemplate = current.skeletonTemplate.copy(phasePoses = nextPoses),
        )
    }

    fun renamePhase(phaseId: String, label: String) = mutateReadyDraft { current ->
        current.copy(
            phases = current.phases.map { if (it.id == phaseId) it.copy(label = label) else it },
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId == phaseId) pose.copy(name = label) else pose
                },
            ),
        )
    }

    fun updatePhasePoseJoint(phaseId: String, joint: String, point: JointPoint) = mutateReadyDraft { current ->
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = DrillStudioPhaseEditor.updatePoseJoint(current.skeletonTemplate.phasePoses, phaseId, joint, point),
            ),
        )
    }

    fun copyPreviousPose(phaseId: String) = mutateReadyDraft { current ->
        val index = current.skeletonTemplate.phasePoses.indexOfFirst { it.phaseId == phaseId }
        if (index <= 0) return@mutateReadyDraft current
        val previous = current.skeletonTemplate.phasePoses[index - 1]
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.mapIndexed { poseIdx, pose ->
                    if (poseIdx == index) pose.copy(joints = previous.joints) else pose
                },
            ),
        )
    }

    fun mirrorPose(phaseId: String) = mutateReadyDraft { current ->
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId == phaseId) pose.copy(
                        joints = DrillStudioPoseUtils.mirrorWithSemanticSwap(pose.joints),
                    ) else pose
                },
            ),
        )
    }

    fun resetPose(phaseId: String) = mutateReadyDraft { current ->
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId == phaseId) pose.copy(joints = defaultJoints()) else pose
                },
            ),
        )
    }

    fun applyPosePreset(phaseId: String, presetId: String) = mutateReadyDraft { current ->
        val preset = DrillStudioPosePresets.all.firstOrNull { it.id == presetId } ?: return@mutateReadyDraft current
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId == phaseId) pose.copy(joints = DrillStudioPoseUtils.normalizeJointNames(preset.joints)) else pose
                },
            ),
        )
    }

    fun updatePhaseDurations(phaseId: String, holdDurationMs: Int?, transitionDurationMs: Int) = mutateReadyDraft { current ->
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId == phaseId) {
                        pose.copy(
                            holdDurationMs = holdDurationMs?.coerceAtLeast(0),
                            transitionDurationMs = transitionDurationMs.coerceAtLeast(100),
                        )
                    } else {
                        pose
                    }
                },
            ),
        )
    }

    fun movePhase(phaseId: String, direction: Int) = mutateReadyDraft { current ->
        val phases = current.phases.sortedBy { it.order }.toMutableList()
        val index = phases.indexOfFirst { it.id == phaseId }
        if (index == -1) return@mutateReadyDraft current
        val target = (index + direction).coerceIn(0, phases.lastIndex)
        if (target == index) return@mutateReadyDraft current
        val moving = phases.removeAt(index)
        phases.add(target, moving)
        current.copy(phases = phases.mapIndexed { idx, phase -> phase.copy(order = idx + 1) })
    }

    fun save(onComplete: (Boolean) -> Unit = {}) {
        val current = _uiState.value as? DrillStudioUiState.Ready ?: return
        val repository = sessionRepository
        if (repository == null) {
            _uiState.value = current.copy(statusMessage = "Save unavailable.")
            onComplete(false)
            return
        }
        val errors = validateReady(current.draft)
        if (errors.isNotEmpty()) {
            _uiState.value = current.copy(validationErrors = errors, statusMessage = null)
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val persisted = runCatching {
                val drillId = current.editingDrillId ?: "user_drill_${System.currentTimeMillis()}"
                val existing = repository.getDrill(drillId)
                val record = current.draft.toDrillDefinitionRecord(existingId = drillId, existing = existing, ready = true)
                repository.updateDrill(record)
                record
            }.getOrNull()
            val refreshed = (_uiState.value as? DrillStudioUiState.Ready) ?: current
            _uiState.value = refreshed.copy(
                editingDrillId = persisted?.id ?: refreshed.editingDrillId,
                validationErrors = emptyList(),
                statusMessage = if (persisted != null) "Saved" else "Save failed.",
            )
            onComplete(persisted != null)
        }
    }

    fun saveDraft() = save()

    fun saveAndMarkReady() = save()

    fun saveTemplate(setAsBaseline: Boolean) {
        val current = _uiState.value as? DrillStudioUiState.Ready ?: return
        val templateId = current.editingTemplateId
        val repository = sessionRepository
        if (templateId == null || repository == null) {
            _uiState.value = current.copy(statusMessage = "This draft is not linked to an existing template.")
            return
        }
        viewModelScope.launch {
            val saved = repository.updateTemplateFromDraft(
                templateId = templateId,
                draft = current.draft,
                setAsBaseline = if (setAsBaseline) true else null,
            )
            val message = if (saved == null) "Template save failed." else "Template saved"
            val refreshed = (_uiState.value as? DrillStudioUiState.Ready) ?: current
            _uiState.value = refreshed.copy(statusMessage = message)
        }
    }

    fun saveAsNewTemplate(setAsBaseline: Boolean) {
        val current = _uiState.value as? DrillStudioUiState.Ready ?: return
        val drillId = current.editingDrillId
        val repository = sessionRepository
        if (drillId.isNullOrBlank() || repository == null) {
            _uiState.value = current.copy(statusMessage = "Unable to determine drill for new template save.")
            return
        }
        viewModelScope.launch {
            val created = repository.createTemplateFromDraft(
                drillId = drillId,
                draft = current.draft,
                displayName = current.draft.title.ifBlank { "Studio Template" },
                basedOnTemplateId = current.editingTemplateId,
                setAsBaseline = setAsBaseline,
            )
            val refreshed = (_uiState.value as? DrillStudioUiState.Ready) ?: current
            _uiState.value = refreshed.copy(
                editingTemplateId = created.id,
                statusMessage = "Saved as new template",
            )
        }
    }

    private fun mutateReadyDraft(transform: (DrillTemplate) -> DrillTemplate) {
        updateDraft(transform)
    }


    private fun resolveSeedById(drills: List<DrillTemplate>, rawDrillId: String): DrillTemplate? {
        val normalized = rawDrillId.trim()
        val candidates = listOf(
            normalized,
            normalized.lowercase(),
            normalized.lowercase().replace('_', '-'),
            normalized.lowercase().replace('_', ' '),
        )
        return drills.firstOrNull { drill ->
            val id = drill.id.lowercase()
            val title = drill.title.lowercase()
            candidates.any { candidate ->
                candidate == id || candidate == title || candidate == id.replace('-', '_')
            }
        }
    }

    private fun normalizeDraft(template: DrillTemplate): DrillTemplate {
        val supportedViews = template.supportedViews.ifEmpty { listOf(CameraView.LEFT_PROFILE) }
        val primaryView = if (template.cameraView in supportedViews) {
            template.cameraView
        } else {
            supportedViews.first()
        }
        val analysisPlane = analysisPlaneForPrimaryView(primaryView)
        val phases = template.phases
            .ifEmpty {
                listOf(
                    DrillPhaseTemplate(
                        id = "phase_1",
                        label = "Phase 1",
                        order = 1,
                        progressWindow = PhaseWindow(0f, 1f),
                    ),
                )
            }
            .sortedBy { it.order }
            .mapIndexed { index, phase ->
                val window = phase.progressWindow ?: PhaseWindow(
                    start = index.toFloat() / phasesSafeCount(template.phases),
                    end = ((index + 1).toFloat() / phasesSafeCount(template.phases)).coerceAtMost(1f),
                )
                phase.copy(order = index + 1, progressWindow = clampWindow(window))
            }

        val normalizedPhases = phases
        val phasePoses = template.skeletonTemplate.phasePoses
            .ifEmpty { phasePosesFromKeyframes(normalizedPhases, template.skeletonTemplate.keyframes) }
            .let { poses ->
                normalizedPhases.mapIndexed { index, phase ->
                    val existing = poses.firstOrNull { it.phaseId == phase.id } ?: poses.getOrNull(index)
                    (existing ?: PhasePoseTemplate(phase.id, phase.label, defaultJoints())).copy(
                        phaseId = phase.id,
                        name = phase.label,
                        joints = DrillStudioPoseUtils.normalizeJointNames(existing?.joints ?: defaultJoints()),
                        transitionDurationMs = existing?.transitionDurationMs?.coerceAtLeast(100) ?: 700,
                    )
                }
            }

        return template.copy(
            cameraView = primaryView,
            supportedViews = supportedViews.distinct(),
            analysisPlane = analysisPlane,
            phases = phases,
            skeletonTemplate = template.skeletonTemplate.copy(
                framesPerSecond = template.skeletonTemplate.framesPerSecond.coerceAtLeast(12),
                phasePoses = phasePoses,
                keyframes = phasePosesToKeyframes(phasePoses),
            ),
            calibration = template.calibration.copy(
                phaseWindows = template.calibration.phaseWindows.ifEmpty {
                    phases.associate { it.id to (it.progressWindow ?: PhaseWindow(0f, 1f)) }
                },
            ),
        )
    }

    private fun phasesSafeCount(phases: List<DrillPhaseTemplate>): Int = phases.size.coerceAtLeast(1)

    private fun clampWindow(window: PhaseWindow): PhaseWindow =
        PhaseWindow(
            start = window.start.coerceIn(0f, 1f),
            end = window.end.coerceIn(window.start.coerceIn(0f, 1f), 1f),
        )

    private fun defaultJoints(): Map<String, JointPoint> =
        DrillStudioPoseUtils.normalizeJointNames(DrillStudioPosePresets.neutralUpright.joints)

    private fun phasePosesFromKeyframes(
        phases: List<DrillPhaseTemplate>,
        keyframes: List<SkeletonKeyframeTemplate>,
    ): List<PhasePoseTemplate> {
        val sorted = keyframes.sortedBy { it.progress }
        if (phases.isEmpty()) return emptyList()
        return phases.mapIndexed { index, phase ->
            val source = sorted.getOrNull(index)
                ?: sorted.lastOrNull()
                ?: SkeletonKeyframeTemplate(progress = 0f, joints = defaultJoints())
            PhasePoseTemplate(
                phaseId = phase.id,
                name = phase.label,
                joints = DrillStudioPoseUtils.normalizeJointNames(source.joints),
                holdDurationMs = null,
                transitionDurationMs = 700,
            )
        }
    }

    private fun phasePosesToKeyframes(phasePoses: List<PhasePoseTemplate>): List<SkeletonKeyframeTemplate> {
        if (phasePoses.isEmpty()) return listOf(
            SkeletonKeyframeTemplate(0f, defaultJoints()),
            SkeletonKeyframeTemplate(1f, defaultJoints()),
        )
        if (phasePoses.size == 1) return listOf(
            SkeletonKeyframeTemplate(0f, phasePoses.first().joints),
            SkeletonKeyframeTemplate(1f, phasePoses.first().joints),
        )
        val totalDurationMs = phasePoses.sumOf { (it.holdDurationMs ?: 0) + it.transitionDurationMs }.coerceAtLeast(1)
        var elapsedMs = 0
        val keyframes = phasePoses.map { pose ->
            val progress = elapsedMs.toFloat() / totalDurationMs.toFloat()
            elapsedMs += (pose.holdDurationMs ?: 0) + pose.transitionDurationMs
            SkeletonKeyframeTemplate(
                progress = progress.coerceIn(0f, 1f),
                joints = DrillStudioPoseUtils.normalizeJointNames(pose.joints),
            )
        }.toMutableList()
        keyframes += SkeletonKeyframeTemplate(1f, DrillStudioPoseUtils.normalizeJointNames(phasePoses.last().joints))
        return keyframes
    }

    private fun validateReady(draft: DrillTemplate): List<String> {
        val errors = mutableListOf<String>()
        if (draft.title.trim().isEmpty()) errors += "Name is required."
        if (draft.phases.isEmpty()) errors += "At least one phase is required."
        if (draft.phases.any { it.label.isBlank() }) errors += "Phase names cannot be blank."
        if (draft.phases.map { it.order }.distinct().size != draft.phases.size) errors += "Phase ordering must be unique."
        val phasePoseIds = draft.skeletonTemplate.phasePoses.map { it.phaseId }.toSet()
        if (!draft.phases.all { phase -> phase.id in phasePoseIds }) errors += "Each phase must have a pose."
        if (draft.keyJoints.isEmpty()) errors += "Select at least one key joint."
        if (draft.normalizationBasis !in CatalogNormalizationBasis.entries) errors += "Normalization basis is invalid."
        if (draft.supportedViews.isEmpty() || draft.cameraView !in draft.supportedViews) {
            errors += "Camera view must be included in supported views."
        }
        return errors
    }
}

private fun DrillTemplate.toDrillDefinitionRecord(
    existingId: String,
    existing: DrillDefinitionRecord?,
    ready: Boolean,
): DrillDefinitionRecord {
    val now = System.currentTimeMillis()
    return DrillDefinitionRecord(
        id = existingId,
        name = title.trim(),
        description = description.trim(),
        movementMode = if (movementType == CatalogMovementType.REP) DrillMovementMode.REP else DrillMovementMode.HOLD,
        cameraView = when (cameraView) {
            CameraView.LEFT_PROFILE -> DrillCameraView.LEFT
            CameraView.RIGHT_PROFILE -> DrillCameraView.RIGHT
            CameraView.FRONT -> DrillCameraView.FRONT
            CameraView.SIDE -> DrillCameraView.LEFT
        },
        phaseSchemaJson = phases.sortedBy { it.order }.joinToString("|") { phase -> phase.label.ifBlank { phase.id } },
        keyJointsJson = keyJoints.joinToString("|"),
        normalizationBasisJson = normalizationBasis.name,
        cueConfigJson = buildList {
            add("legacyDrillType:FREE_HANDSTAND")
            add("comparisonMode:${comparisonMode.name}")
        }.joinToString("|"),
        sourceType = existing?.sourceType ?: DrillSourceType.USER_CREATED,
        status = if (ready) DrillStatus.READY else existing?.status ?: DrillStatus.DRAFT,
        version = (existing?.version ?: 0) + 1,
        createdAtMs = existing?.createdAtMs ?: now,
        updatedAtMs = now,
    )
}



internal fun resolveInitialDraft(
    mode: String,
    templateRecord: com.inversioncoach.app.model.ReferenceTemplateRecord?,
    seed: DrillTemplate?,
    mapper: (com.inversioncoach.app.model.ReferenceTemplateRecord, DrillTemplate?) -> TemplateDraftMappingResult = ReferenceTemplateDraftMapper::toDraft,
): EditorDraftResult = when {
    mode == "create" -> EditorDraftResult(DrillStudioDraftRepository.createBlankDraft())
    templateRecord != null -> {
        runCatching {
            val mapped = mapper(templateRecord, seed)
            val editable = DrillStudioDraftRepository.createDraftFromTemplate(
                template = mapped.draft,
                sourceSeedId = seed?.id,
            )
            EditorDraftResult(
                draft = editable,
                statusMessage = buildTemplateStatusMessage(templateRecord.displayName, mapped.warnings),
            )
        }.getOrElse {
            val fallback = when {
                seed != null -> DrillStudioDraftRepository.resolveEditableDraft(seed)
                else -> DrillStudioDraftRepository.createBlankDraft()
            }
            EditorDraftResult(
                draft = fallback,
                statusMessage = "Template parsing failed for ${templateRecord.displayName}; loaded drill-seeded draft instead.",
            )
        }
    }
    seed != null -> EditorDraftResult(DrillStudioDraftRepository.resolveEditableDraft(seed))
    else -> EditorDraftResult(DrillStudioDraftRepository.createBlankDraft())
}

internal data class EditorDraftResult(
    val draft: EditableDraft,
    val statusMessage: String? = null,
)

private fun buildTemplateStatusMessage(displayName: String, warnings: List<String>): String =
    if (warnings.isEmpty()) {
        "Loaded editable template draft: $displayName"
    } else {
        "Loaded editable template draft: $displayName (${warnings.joinToString(" ")})"
    }

internal fun analysisPlaneForPrimaryView(primaryView: CameraView): AnalysisPlane = when (primaryView) {
    CameraView.FRONT -> AnalysisPlane.FRONTAL
    CameraView.SIDE,
    CameraView.LEFT_PROFILE,
    CameraView.RIGHT_PROFILE,
    -> AnalysisPlane.SAGITTAL
}

internal data class EditableDraft(
    val template: DrillTemplate,
    val sourceSeedId: String?,
)

private object DrillStudioDraftRepository {
    private val drafts = linkedMapOf<String, EditableDraft>()

    fun resolveEditableDraft(seed: DrillTemplate): EditableDraft {
        val existing = drafts.values.firstOrNull { it.sourceSeedId == seed.id }
        if (existing != null) return existing

        if (seed.id.startsWith("draft_")) {
            return drafts[seed.id] ?: EditableDraft(seed, null).also { drafts[seed.id] = it }
        }

        val draftId = "draft_${seed.id}_${System.currentTimeMillis()}"
        val draft = seed.copy(id = draftId, title = "${seed.title} (Draft)")
        return EditableDraft(draft, seed.id).also { drafts[draftId] = it }
    }


    fun createDraftFromTemplate(template: DrillTemplate, sourceSeedId: String?): EditableDraft {
        val draft = template.copy(id = template.id.ifBlank { "draft_template_${System.currentTimeMillis()}" })
        return EditableDraft(draft, sourceSeedId).also { drafts[draft.id] = it }
    }

    fun createBlankDraft(): EditableDraft {
        val id = "draft_new_${System.currentTimeMillis()}"
        val draft = DrillTemplate(
            id = id,
            title = "",
            family = "Custom",
            movementType = CatalogMovementType.HOLD,
            tags = listOf("custom"),
            cameraView = CameraView.LEFT_PROFILE,
            supportedViews = listOf(CameraView.LEFT_PROFILE),
            analysisPlane = AnalysisPlane.SAGITTAL,
            comparisonMode = ComparisonMode.POSE_TIMELINE,
            keyJoints = listOf("shoulder_left", "shoulder_right", "hip_left", "hip_right"),
            normalizationBasis = CatalogNormalizationBasis.HIPS,
            phases = listOf(
                DrillPhaseTemplate(
                    id = "phase_1",
                    label = "Setup",
                    order = 1,
                    progressWindow = PhaseWindow(0f, 1f),
                ),
            ),
            skeletonTemplate = SkeletonTemplate(
                id = "skel_$id",
                loop = true,
                mirroredSupported = false,
                framesPerSecond = 24,
                keyframes = emptyList(),
            ),
            calibration = CalibrationTemplate(
                metricThresholds = emptyMap(),
                phaseWindows = emptyMap(),
            ),
        )
        return EditableDraft(draft, null).also { drafts[id] = it }
    }

    fun saveDraft(template: DrillTemplate, sourceSeedId: String?) {
        drafts[template.id] = EditableDraft(template, sourceSeedId)
    }
}
