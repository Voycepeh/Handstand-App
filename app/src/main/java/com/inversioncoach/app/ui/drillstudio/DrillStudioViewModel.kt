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

        val keyframes = template.skeletonTemplate.keyframes.ifEmpty {
            listOf(
                SkeletonKeyframeTemplate(
                    progress = 0f,
                    joints = defaultJoints(),
                ),
                SkeletonKeyframeTemplate(
                    progress = 1f,
                    joints = defaultJoints(),
                ),
            )
        }

        return template.copy(
            cameraView = primaryView,
            supportedViews = supportedViews.distinct(),
            phases = phases,
            skeletonTemplate = template.skeletonTemplate.copy(
                framesPerSecond = template.skeletonTemplate.framesPerSecond.coerceAtLeast(12),
                keyframes = keyframes,
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

    private fun defaultJoints(): Map<String, JointPoint> = mapOf(
        "nose" to JointPoint(0.5f, 0.2f),
        "left_shoulder" to JointPoint(0.42f, 0.35f),
        "right_shoulder" to JointPoint(0.58f, 0.35f),
        "left_hip" to JointPoint(0.45f, 0.55f),
        "right_hip" to JointPoint(0.55f, 0.55f),
        "left_ankle" to JointPoint(0.45f, 0.85f),
        "right_ankle" to JointPoint(0.55f, 0.85f),
    )
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
