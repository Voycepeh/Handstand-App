package com.inversioncoach.app.ui.drillstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inversioncoach.app.drills.catalog.AnalysisPlane
import com.inversioncoach.app.drills.catalog.CalibrationTemplate
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.ComparisonMode
import com.inversioncoach.app.drills.catalog.DrillCatalogRepository
import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.drills.catalog.SkeletonKeyframeTemplate
import com.inversioncoach.app.drills.catalog.SkeletonTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DrillStudioInitRequest(
    val mode: String = "drill",
    val drillId: String? = null,
)

sealed interface DrillStudioUiState {
    data object Loading : DrillStudioUiState
    data class Error(val message: String) : DrillStudioUiState
    data class Ready(
        val draft: DrillTemplate,
        val sourceSeedId: String?,
    ) : DrillStudioUiState
}

class DrillStudioViewModel(
    private val repository: DrillCatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DrillStudioUiState>(DrillStudioUiState.Loading)
    val uiState: StateFlow<DrillStudioUiState> = _uiState.asStateFlow()

    fun initialize(request: DrillStudioInitRequest) {
        viewModelScope.launch {
            _uiState.value = DrillStudioUiState.Loading
            val result = runCatching {
                val catalog = repository.loadCatalog()
                val seed = when {
                    request.mode == "create" -> null
                    request.drillId != null -> resolveSeedById(catalog.drills, request.drillId)
                    else -> catalog.drills.firstOrNull()
                }
                val draft = when {
                    request.mode == "create" -> DrillStudioDraftRepository.createBlankDraft()
                    seed != null -> DrillStudioDraftRepository.resolveEditableDraft(seed)
                    else -> DrillStudioDraftRepository.createBlankDraft()
                }
                val normalized = normalizeDraft(draft.template)
                DrillStudioUiState.Ready(
                    draft = normalized,
                    sourceSeedId = draft.sourceSeedId,
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
        _uiState.value = current.copy(draft = updated)
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
}

private data class EditableDraft(
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

    fun createBlankDraft(): EditableDraft {
        val id = "draft_new_${System.currentTimeMillis()}"
        val draft = DrillTemplate(
            id = id,
            title = "New Drill",
            family = "Custom",
            movementType = CatalogMovementType.HOLD,
            tags = listOf("custom"),
            cameraView = CameraView.LEFT_PROFILE,
            supportedViews = listOf(CameraView.LEFT_PROFILE),
            analysisPlane = AnalysisPlane.SAGITTAL,
            comparisonMode = ComparisonMode.POSE_TIMELINE,
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
