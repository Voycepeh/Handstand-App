package com.inversioncoach.app.ui.drillstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inversioncoach.app.drills.DrillCameraView
import com.inversioncoach.app.drills.DrillCueConfigCodec
import com.inversioncoach.app.drills.DrillMovementMode
import com.inversioncoach.app.drills.DrillSourceType
import com.inversioncoach.app.drills.DrillStatus
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
import com.inversioncoach.app.drills.catalog.PhaseBoundaryGuides
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import com.inversioncoach.app.drills.catalog.PhasePoseSourceType
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.drills.catalog.PhasePoseAuthoring
import com.inversioncoach.app.drills.catalog.SkeletonKeyframeTemplate
import com.inversioncoach.app.drills.catalog.SkeletonTemplate
import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.storage.repository.SessionRepository
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

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
    private companion object {
        const val TAG = "DrillStudioHydration"
    }
    private val _uiState = MutableStateFlow<DrillStudioUiState>(DrillStudioUiState.Loading)
    val uiState: StateFlow<DrillStudioUiState> = _uiState.asStateFlow()

    fun initialize(request: DrillStudioInitRequest) {
        viewModelScope.launch {
            _uiState.value = DrillStudioUiState.Loading
            val result = runCatching {
                val catalog = catalogLoader?.invoke() ?: repository.loadCatalog()
                val templateRecord = request.templateId?.let { templateId -> sessionRepository?.getReferenceTemplate(templateId) }
                val persistedDrill = request.drillId?.let { drillId -> sessionRepository?.getDrill(drillId) }
                val seedDrillId = templateRecord?.drillId ?: persistedDrill?.id ?: request.drillId
                val seed = when {
                    request.mode == "create" -> null
                    seedDrillId != null -> resolveSeedById(catalog.drills, seedDrillId)
                    else -> catalog.drills.firstOrNull()
                }
                val persistedSeed = persistedDrill?.toDrillTemplate(seed)
                val draftResult = when {
                    request.mode == "create" -> resolveInitialDraft(
                        mode = request.mode,
                        templateRecord = templateRecord,
                        seed = seed,
                    )
                    templateRecord != null -> resolveInitialDraft(
                        mode = request.mode,
                        templateRecord = templateRecord,
                        seed = seed,
                    )
                    persistedSeed != null -> {
                        EditorDraftResult(
                            draft = DrillStudioDraftRepository.createDraftFromTemplate(
                                template = persistedSeed,
                                sourceSeedId = persistedDrill.id,
                            ),
                            statusMessage = "Loaded persisted drill for editing.",
                        )
                    }
                    else -> resolveInitialDraft(
                        mode = request.mode,
                        templateRecord = templateRecord,
                        seed = seed,
                    )
                }
                val normalized = normalizeDraft(draftResult.draft.template)
                Log.d(
                    TAG,
                    "initialize drillId=${request.drillId} seed=${seed?.id} phases=${normalized.phases.size} " +
                        "phasePoses=${normalized.skeletonTemplate.phasePoses.size} keyframes=${normalized.skeletonTemplate.keyframes.size}",
                )
                DrillStudioUiState.Ready(
                    draft = normalized,
                    sourceSeedId = draftResult.draft.sourceSeedId,
                    editingDrillId = templateRecord?.drillId ?: persistedDrill?.id,
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
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId != phaseId) return@map pose
                    val detected = pose.authoring?.detectedJoints?.get(joint)
                    val nextOffsets = if (detected != null) {
                        (pose.authoring?.manualOffsets.orEmpty() + (joint to JointPoint(point.x - detected.x, point.y - detected.y)))
                    } else {
                        pose.authoring?.manualOffsets.orEmpty()
                    }
                    pose.copy(
                        joints = pose.joints + (joint to point),
                        authoring = pose.authoring?.copy(manualOffsets = nextOffsets),
                    )
                },
            ),
        )
    }

    fun attachReferenceImage(phaseId: String, imageUri: String) = mutateReadyDraft { current ->
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId == phaseId) {
                        pose.copy(
                            authoring = (pose.authoring ?: PhasePoseAuthoring()).copy(
                                sourceType = PhasePoseSourceType.IMAGE,
                                sourceImageUri = imageUri,
                            ),
                        )
                    } else {
                        pose
                    }
                },
            ),
        )
    }

    fun clearReferenceImage(phaseId: String) = mutateReadyDraft { current ->
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId == phaseId) {
                        pose.copy(
                            authoring = (pose.authoring ?: PhasePoseAuthoring()).copy(
                                sourceType = PhasePoseSourceType.MANUAL,
                                sourceImageUri = null,
                                detectedJoints = emptyMap(),
                                jointConfidence = emptyMap(),
                                manualOffsets = emptyMap(),
                                qualityScore = null,
                            ),
                        )
                    } else {
                        pose
                    }
                },
            ),
        )
    }

    fun applyDetectedImagePose(
        phaseId: String,
        normalizedJoints: Map<String, JointPoint>,
        jointConfidence: Map<String, Float>,
        qualityScore: Float,
    ) = mutateReadyDraft { current ->
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId != phaseId) return@map pose
                    pose.copy(
                        joints = DrillStudioPoseUtils.normalizeJointNames(normalizedJoints),
                        authoring = (pose.authoring ?: PhasePoseAuthoring()).copy(
                            sourceType = PhasePoseSourceType.IMAGE,
                            detectedJoints = DrillStudioPoseUtils.normalizeJointNames(normalizedJoints),
                            jointConfidence = jointConfidence,
                            manualOffsets = emptyMap(),
                            qualityScore = qualityScore,
                        ),
                    )
                },
            ),
        )
    }

    fun resetImageDetection(phaseId: String) = mutateReadyDraft { current ->
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId != phaseId) return@map pose
                    val detected = pose.authoring?.detectedJoints.orEmpty()
                    if (detected.isEmpty()) return@map pose
                    pose.copy(
                        joints = detected,
                        authoring = pose.authoring?.copy(manualOffsets = emptyMap()),
                    )
                },
            ),
        )
    }

    fun resetJointCorrection(phaseId: String, joint: String) = mutateReadyDraft { current ->
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId != phaseId) return@map pose
                    val detected = pose.authoring?.detectedJoints?.get(joint) ?: return@map pose
                    pose.copy(
                        joints = pose.joints + (joint to detected),
                        authoring = pose.authoring?.copy(manualOffsets = pose.authoring.manualOffsets - joint),
                    )
                },
            ),
        )
    }

    fun resetAllCorrections(phaseId: String) = resetImageDetection(phaseId)

    fun updatePhaseGuides(phaseId: String, guides: PhaseBoundaryGuides) = mutateReadyDraft { current ->
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId == phaseId) {
                        pose.copy(authoring = (pose.authoring ?: PhasePoseAuthoring()).copy(guides = guides))
                    } else {
                        pose
                    }
                },
            ),
        )
    }

    fun savePhasePose(phaseId: String) = mutateReadyDraft { current ->
        current.copy(
            skeletonTemplate = current.skeletonTemplate.copy(
                phasePoses = current.skeletonTemplate.phasePoses.map { pose ->
                    if (pose.phaseId == phaseId) {
                        pose.copy(joints = DrillStudioPoseUtils.normalizeJointNames(pose.joints))
                    } else {
                        pose
                    }
                },
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
                if (existing == null) repository.createDrill(record) else repository.updateDrill(record)
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
        val explicitPhasePoses = template.skeletonTemplate.phasePoses
        val explicitKeyframes = template.skeletonTemplate.keyframes
        val fallbackSource = when {
            explicitPhasePoses.isNotEmpty() -> "seeded_or_authored_phase_poses"
            explicitKeyframes.isNotEmpty() -> "seeded_or_authored_keyframes"
            else -> "default_upright_fallback"
        }
        val phasePoses = explicitPhasePoses
            .ifEmpty { phasePosesFromKeyframes(normalizedPhases, explicitKeyframes) }
            .let { poses ->
                normalizedPhases.mapIndexed { index, phase ->
                    val existing = poses.firstOrNull { it.phaseId == phase.id }
                    val keyframeDerived = if (existing == null && explicitKeyframes.isNotEmpty()) {
                        phasePoseFromClosestKeyframe(phase, explicitKeyframes)
                    } else {
                        null
                    }
                    (existing ?: PhasePoseTemplate(phase.id, phase.label, defaultJoints())).copy(
                        phaseId = phase.id,
                        name = phase.label,
                        joints = DrillStudioPoseUtils.normalizeJointNames(existing?.joints ?: keyframeDerived?.joints ?: defaultJoints()),
                        transitionDurationMs = (existing?.transitionDurationMs ?: keyframeDerived?.transitionDurationMs)?.coerceAtLeast(100) ?: 700,
                    )
                }
            }
        val normalizedKeyframes = when {
            explicitKeyframes.isNotEmpty() -> explicitKeyframes.map { frame ->
                frame.copy(joints = DrillStudioPoseUtils.normalizeJointNames(frame.joints))
            }
            phasePoses.isNotEmpty() -> phasePosesToKeyframes(phasePoses)
            else -> listOf(SkeletonKeyframeTemplate(0f, defaultJoints()), SkeletonKeyframeTemplate(1f, defaultJoints()))
        }
        Log.d(
            TAG,
            "normalizeDraft id=${template.id} phases=${normalizedPhases.size} phasePoses=${phasePoses.size} " +
                "keyframes=${normalizedKeyframes.size} fallbackSource=$fallbackSource",
        )

        return template.copy(
            cameraView = primaryView,
            supportedViews = supportedViews.distinct(),
            analysisPlane = analysisPlane,
            phases = phases,
            skeletonTemplate = template.skeletonTemplate.copy(
                framesPerSecond = template.skeletonTemplate.framesPerSecond.coerceAtLeast(12),
                phasePoses = phasePoses,
                keyframes = normalizedKeyframes,
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

    private fun phasePoseFromClosestKeyframe(
        phase: DrillPhaseTemplate,
        keyframes: List<SkeletonKeyframeTemplate>,
    ): PhasePoseTemplate? {
        val sorted = keyframes.sortedBy { it.progress }
        if (sorted.isEmpty()) return null
        val midpoint = ((phase.progressWindow?.start ?: 0f) + (phase.progressWindow?.end ?: 1f)) / 2f
        val closest = sorted.minByOrNull { kotlin.math.abs(it.progress - midpoint) } ?: return null
        return PhasePoseTemplate(
            phaseId = phase.id,
            name = phase.label,
            joints = DrillStudioPoseUtils.normalizeJointNames(closest.joints),
            holdDurationMs = null,
            transitionDurationMs = 700,
        )
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

internal fun DrillTemplate.toDrillDefinitionRecord(
    existingId: String,
    existing: DrillDefinitionRecord?,
    ready: Boolean,
): DrillDefinitionRecord {
    val now = System.currentTimeMillis()
    val studioPayload = encodeStudioPayload()
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
        cueConfigJson = DrillCueConfigCodec.merge(
            existingCueConfig = existing?.cueConfigJson,
            comparisonMode = comparisonMode.name,
            studioPayload = studioPayload,
        ),
        sourceType = existing?.sourceType ?: DrillSourceType.USER_CREATED,
        status = if (ready) DrillStatus.READY else existing?.status ?: DrillStatus.DRAFT,
        version = (existing?.version ?: 0) + 1,
        createdAtMs = existing?.createdAtMs ?: now,
        updatedAtMs = now,
    )
}

internal fun DrillDefinitionRecord.toDrillTemplate(seed: DrillTemplate?): DrillTemplate {
    val payload = decodeStudioPayload(cueConfigJson)
    val fallback = seed ?: DrillStudioDraftRepository.createBlankDraft().template
    val phaseNames = phaseSchemaJson.split('|').mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() } }

    val phases = when {
        payload?.phases?.isNotEmpty() == true -> payload.phases
        fallback.phases.isNotEmpty() -> fallback.phases
        else -> phaseNames.mapIndexed { index, name ->
            DrillPhaseTemplate(
                id = normalizePhaseId(name, index),
                label = name,
                order = index + 1,
                progressWindow = PhaseWindow(
                    start = index.toFloat() / phaseNames.size.coerceAtLeast(1),
                    end = ((index + 1).toFloat() / phaseNames.size.coerceAtLeast(1)).coerceAtMost(1f),
                ),
            )
        }
    }

    val resolved = resolveVisiblePhaseData(phases = phases, payload = payload, fallback = fallback)
    Log.d(
        "DrillStudioHydration",
        "toDrillTemplate drill=$id sourceType=$sourceType phaseSource=${resolved.source} payloadPoses=${payload?.phasePoses?.size ?: 0} " +
            "seedPoses=${fallback.skeletonTemplate.phasePoses.size} selectedPhase=${phases.firstOrNull()?.id} " +
            "jointCount=${resolved.phasePoses.firstOrNull()?.joints?.size ?: 0} keyframes=${resolved.keyframes.size}",
    )

    return fallback.copy(
        id = id,
        title = name,
        description = description,
        movementType = if (movementMode == DrillMovementMode.REP) CatalogMovementType.REP else CatalogMovementType.HOLD,
        cameraView = payload?.cameraView ?: cameraView.toCatalogCameraView(),
        supportedViews = payload?.supportedViews ?: listOf(cameraView.toCatalogCameraView()),
        comparisonMode = payload?.comparisonMode ?: cueConfigJson.comparisonModeFromCueConfigTokens(),
        keyJoints = payload?.keyJoints ?: keyJointsJson.split('|').filter { it.isNotBlank() },
        normalizationBasis = payload?.normalizationBasis ?: normalizationBasisJson.toCatalogNormalizationBasis(),
        phases = phases,
        skeletonTemplate = fallback.skeletonTemplate.copy(
            phasePoses = resolved.phasePoses,
            keyframes = resolved.keyframes,
        ),
        calibration = fallback.calibration.copy(
            metricThresholds = payload?.metricThresholds ?: fallback.calibration.metricThresholds,
        ),
    )
}

private data class ResolvedVisiblePhaseData(
    val source: String,
    val phasePoses: List<PhasePoseTemplate>,
    val keyframes: List<SkeletonKeyframeTemplate>,
)

private fun resolveVisiblePhaseData(
    phases: List<DrillPhaseTemplate>,
    payload: PersistedStudioPayload?,
    fallback: DrillTemplate,
): ResolvedVisiblePhaseData {
    val payloadHasData = payload?.phasePoses?.isNotEmpty() == true || payload?.keyframes?.isNotEmpty() == true
    val fallbackHasData = fallback.skeletonTemplate.phasePoses.isNotEmpty() || fallback.skeletonTemplate.keyframes.isNotEmpty()

    val source = when {
        payloadHasData -> "authored_or_persisted_seeded"
        fallbackHasData -> "catalog_seeded"
        else -> "fallback"
    }

    val sourcePoses = when {
        payload?.phasePoses?.isNotEmpty() == true -> payload.phasePoses
        fallback.skeletonTemplate.phasePoses.isNotEmpty() -> fallback.skeletonTemplate.phasePoses
        else -> emptyList()
    }

    val resolvedPhasePoses = phases.mapIndexed { index, phase ->
        val matched = sourcePoses.firstOrNull { matchesPhase(it.phaseId, phase) }
        val derivedFromKeyframe = if (matched == null) {
            val sourceKeyframes = payload?.keyframes?.takeIf { it.isNotEmpty() } ?: fallback.skeletonTemplate.keyframes
            sourceKeyframes.sortedBy { it.progress }.getOrNull(index)
        } else {
            null
        }
        PhasePoseTemplate(
            phaseId = phase.id,
            name = phase.label,
            joints = DrillStudioPoseUtils.normalizeJointNames(
                matched?.joints
                    ?: derivedFromKeyframe?.joints
                    ?: DrillStudioPosePresets.neutralUpright.joints,
            ),
            holdDurationMs = matched?.holdDurationMs,
            transitionDurationMs = matched?.transitionDurationMs ?: 700,
        )
    }

    val resolvedKeyframes = when {
        payload?.keyframes?.isNotEmpty() == true -> payload.keyframes
        fallback.skeletonTemplate.keyframes.isNotEmpty() -> fallback.skeletonTemplate.keyframes
        else -> phasePosesToKeyframesForRecord(resolvedPhasePoses)
    }

    return ResolvedVisiblePhaseData(
        source = source,
        phasePoses = resolvedPhasePoses,
        keyframes = resolvedKeyframes,
    )
}

private fun normalizePhaseId(raw: String, index: Int): String {
    val normalized = raw.lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
    return normalized.ifBlank { "phase_${index + 1}" }
}

private fun matchesPhase(sourcePhaseId: String, phase: DrillPhaseTemplate): Boolean {
    val source = sourcePhaseId.trim().lowercase()
    return source == phase.id.trim().lowercase() || source == normalizePhaseId(phase.label, phase.order - 1)
}

internal data class PersistedStudioPayload(
    val cameraView: CameraView,
    val supportedViews: List<CameraView>,
    val comparisonMode: ComparisonMode,
    val keyJoints: List<String>,
    val normalizationBasis: CatalogNormalizationBasis,
    val phases: List<DrillPhaseTemplate>,
    val phasePoses: List<PhasePoseTemplate>,
    val keyframes: List<SkeletonKeyframeTemplate>,
    val metricThresholds: Map<String, Float>,
)

private fun phasePosesToKeyframesForRecord(phasePoses: List<PhasePoseTemplate>): List<SkeletonKeyframeTemplate> {
    if (phasePoses.isEmpty()) {
        val fallbackJoints = DrillStudioPoseUtils.normalizeJointNames(DrillStudioPosePresets.neutralUpright.joints)
        return listOf(
            SkeletonKeyframeTemplate(0f, fallbackJoints),
            SkeletonKeyframeTemplate(1f, fallbackJoints),
        )
    }
    if (phasePoses.size == 1) {
        val joints = DrillStudioPoseUtils.normalizeJointNames(phasePoses.first().joints)
        return listOf(
            SkeletonKeyframeTemplate(0f, joints),
            SkeletonKeyframeTemplate(1f, joints),
        )
    }
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

internal fun DrillTemplate.encodeStudioPayload(): String {
    val json = JSONObject().apply {
        put("cameraView", cameraView.name)
        put("supportedViews", JSONArray().apply { supportedViews.forEach { put(it.name) } })
        put("comparisonMode", comparisonMode.name)
        put("keyJoints", JSONArray().apply { keyJoints.forEach(::put) })
        put("normalizationBasis", normalizationBasis.name)
        put("phases", JSONArray().apply {
            phases.sortedBy { it.order }.forEach { phase ->
                put(JSONObject().apply {
                    put("id", phase.id)
                    put("label", phase.label)
                    put("order", phase.order)
                    phase.progressWindow?.let { window ->
                        put("windowStart", window.start)
                        put("windowEnd", window.end)
                    }
                })
            }
        })
        put("phasePoses", JSONArray().apply {
            skeletonTemplate.phasePoses.forEach { pose ->
                put(JSONObject().apply {
                    put("phaseId", pose.phaseId)
                    put("name", pose.name)
                    put("holdDurationMs", pose.holdDurationMs)
                    put("transitionDurationMs", pose.transitionDurationMs)
                    put("joints", JSONObject(pose.joints.mapValues { (_, p) -> JSONArray().put(p.x).put(p.y) }))
                    pose.authoring?.let { authoring ->
                        put("authoring", JSONObject().apply {
                            put("sourceType", authoring.sourceType.name)
                            put("sourceImageUri", authoring.sourceImageUri)
                            put("detectedJoints", JSONObject(authoring.detectedJoints.mapValues { (_, p) -> JSONArray().put(p.x).put(p.y) }))
                            put("jointConfidence", JSONObject(authoring.jointConfidence))
                            put("manualOffsets", JSONObject(authoring.manualOffsets.mapValues { (_, p) -> JSONArray().put(p.x).put(p.y) }))
                            put("qualityScore", authoring.qualityScore)
                            put("guides", JSONObject().apply {
                                put("showFrameGuides", authoring.guides.showFrameGuides)
                                put("showFloorLine", authoring.guides.showFloorLine)
                                put("floorLineY", authoring.guides.floorLineY)
                                put("showWallLine", authoring.guides.showWallLine)
                                put("wallLineX", authoring.guides.wallLineX)
                                put("showBarLine", authoring.guides.showBarLine)
                                put("barLineY", authoring.guides.barLineY)
                            })
                        })
                    }
                })
            }
        })
        put("keyframes", JSONArray().apply {
            skeletonTemplate.keyframes.forEach { keyframe ->
                put(JSONObject().apply {
                    put("progress", keyframe.progress)
                    put("joints", JSONObject(keyframe.joints.mapValues { (_, p) -> JSONArray().put(p.x).put(p.y) }))
                })
            }
        })
        put("metricThresholds", JSONObject(calibration.metricThresholds))
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
}

internal fun decodeStudioPayload(cueConfigJson: String): PersistedStudioPayload? = runCatching {
    val encoded = DrillCueConfigCodec.parse(cueConfigJson).studioPayload ?: return null
    if (encoded.isBlank()) return null
    val decoded = String(Base64.getUrlDecoder().decode(encoded))
    val json = JSONObject(decoded)
    val cameraView = CameraView.valueOf(json.getString("cameraView"))
    val supportedViews = json.optJSONArray("supportedViews")?.let { array ->
        List(array.length()) { index -> CameraView.valueOf(array.getString(index)) }
    } ?: listOf(cameraView)
    val comparisonMode = ComparisonMode.valueOf(json.getString("comparisonMode"))
    val keyJoints = json.optJSONArray("keyJoints")?.let { array ->
        List(array.length()) { index -> array.getString(index) }
    } ?: emptyList()
    val normalizationBasis = CatalogNormalizationBasis.valueOf(json.getString("normalizationBasis"))
    val phases = json.optJSONArray("phases")?.let { array ->
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            DrillPhaseTemplate(
                id = item.getString("id"),
                label = item.getString("label"),
                order = item.getInt("order"),
                progressWindow = if (item.has("windowStart") && item.has("windowEnd")) {
                    PhaseWindow(item.getDouble("windowStart").toFloat(), item.getDouble("windowEnd").toFloat())
                } else {
                    PhaseWindow(0f, 1f)
                },
            )
        }
    } ?: emptyList()
    val phasePoses = json.optJSONArray("phasePoses")?.let { array ->
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val joints = item.getJSONObject("joints").keys().asSequence().associateWith { key ->
                val point = item.getJSONObject("joints").getJSONArray(key)
                JointPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
            }
            PhasePoseTemplate(
                phaseId = item.getString("phaseId"),
                name = item.optString("name", item.getString("phaseId")),
                joints = joints,
                holdDurationMs = item.optInt("holdDurationMs").takeIf { it > 0 },
                transitionDurationMs = item.optInt("transitionDurationMs", 700),
                authoring = item.optJSONObject("authoring")?.let { authoring ->
                    val detectedJoints = authoring.optJSONObject("detectedJoints")?.keys()?.asSequence()?.associateWith { key ->
                        val point = authoring.getJSONObject("detectedJoints").getJSONArray(key)
                        JointPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
                    }.orEmpty()
                    val manualOffsets = authoring.optJSONObject("manualOffsets")?.keys()?.asSequence()?.associateWith { key ->
                        val point = authoring.getJSONObject("manualOffsets").getJSONArray(key)
                        JointPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
                    }.orEmpty()
                    val guidesJson = authoring.optJSONObject("guides")
                    val guides = PhaseBoundaryGuides(
                        showFrameGuides = guidesJson?.optBoolean("showFrameGuides", true) ?: true,
                        showFloorLine = guidesJson?.optBoolean("showFloorLine", false) ?: false,
                        floorLineY = guidesJson?.optDouble("floorLineY", 0.9)?.toFloat() ?: 0.9f,
                        showWallLine = guidesJson?.optBoolean("showWallLine", false) ?: false,
                        wallLineX = guidesJson?.optDouble("wallLineX", 0.1)?.toFloat() ?: 0.1f,
                        showBarLine = guidesJson?.optBoolean("showBarLine", false) ?: false,
                        barLineY = guidesJson?.optDouble("barLineY", 0.18)?.toFloat() ?: 0.18f,
                    )
                    PhasePoseAuthoring(
                        sourceType = authoring.optString("sourceType").takeIf { it.isNotBlank() }?.let {
                            runCatching { PhasePoseSourceType.valueOf(it) }.getOrDefault(PhasePoseSourceType.MANUAL)
                        } ?: PhasePoseSourceType.MANUAL,
                        sourceImageUri = authoring.optString("sourceImageUri").ifBlank { null },
                        detectedJoints = detectedJoints,
                        jointConfidence = authoring.optJSONObject("jointConfidence")?.keys()?.asSequence()?.associateWith { key ->
                            authoring.getJSONObject("jointConfidence").getDouble(key).toFloat()
                        }.orEmpty(),
                        manualOffsets = manualOffsets,
                        qualityScore = authoring.optDouble("qualityScore").takeIf { !it.isNaN() }?.toFloat(),
                        guides = guides,
                    )
                },
            )
        }
    } ?: emptyList()
    val keyframes = json.optJSONArray("keyframes")?.let { array ->
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val joints = item.getJSONObject("joints").keys().asSequence().associateWith { key ->
                val point = item.getJSONObject("joints").getJSONArray(key)
                JointPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
            }
            SkeletonKeyframeTemplate(progress = item.getDouble("progress").toFloat(), joints = joints)
        }
    } ?: emptyList()
    val metricThresholds = json.optJSONObject("metricThresholds")?.let { thresholds ->
        thresholds.keys().asSequence().associateWith { key -> thresholds.getDouble(key).toFloat() }
    } ?: emptyMap()

    PersistedStudioPayload(
        cameraView = cameraView,
        supportedViews = supportedViews,
        comparisonMode = comparisonMode,
        keyJoints = keyJoints,
        normalizationBasis = normalizationBasis,
        phases = phases,
        phasePoses = phasePoses,
        keyframes = keyframes,
        metricThresholds = metricThresholds,
    )
}.getOrNull()

private fun String.comparisonModeFromCueConfigTokens(): ComparisonMode =
    ComparisonMode.entries.firstOrNull { entry ->
        entry.name == DrillCueConfigCodec.parse(this).comparisonMode
    } ?: ComparisonMode.POSE_TIMELINE

private fun String.toCatalogNormalizationBasis(): CatalogNormalizationBasis =
    CatalogNormalizationBasis.entries.firstOrNull { it.name == this } ?: CatalogNormalizationBasis.HIPS

private fun String.toCatalogCameraView(): CameraView = when (this) {
    DrillCameraView.LEFT -> CameraView.LEFT_PROFILE
    DrillCameraView.RIGHT -> CameraView.RIGHT_PROFILE
    DrillCameraView.FRONT -> CameraView.FRONT
    else -> CameraView.LEFT_PROFILE
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
