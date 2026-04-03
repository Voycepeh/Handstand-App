package com.inversioncoach.app.ui.drills

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inversioncoach.app.drills.studio.DrillCatalogDraftStore
import com.inversioncoach.app.drills.studio.DrillCatalogImportExportManager
import com.inversioncoach.app.drills.studio.DrillStudioDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DrillStudioEditorState(
    val drills: List<DrillCatalogDraftStore.DrillPickerItem> = emptyList(),
    val selectedDrillId: String = "",
    val baseline: DrillStudioDocument? = null,
    val working: DrillStudioDocument? = null,
    val status: String? = null,
) {
    val selectedMeta: DrillCatalogDraftStore.DrillPickerItem?
        get() = drills.firstOrNull { it.id == selectedDrillId }

    val hasUnsavedChanges: Boolean
        get() = baseline != null && working != null && baseline != working
}

class DrillStudioEditorViewModel(
    context: Context,
) : ViewModel() {
    private val store = DrillCatalogDraftStore(context)
    private val importExport = DrillCatalogImportExportManager(context, store)

    private val _state = MutableStateFlow(DrillStudioEditorState())
    val state: StateFlow<DrillStudioEditorState> = _state.asStateFlow()

    fun initialize(initialDrillId: String?) {
        val current = _state.value
        if (current.hasUnsavedChanges) return
        viewModelScope.launch {
            refreshAndSelect(initialDrillId)
        }
    }

    fun selectDrill(drillId: String) {
        val current = _state.value
        if (current.hasUnsavedChanges && current.selectedDrillId != drillId) {
            _state.value = current.copy(status = "Unsaved changes. Save or reset before switching drills.")
            return
        }
        viewModelScope.launch {
            refreshAndSelect(drillId)
        }
    }

    fun updateWorking(updated: DrillStudioDocument) {
        _state.value = _state.value.copy(working = updated)
    }

    fun saveDraft() {
        val draft = _state.value.working ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { store.saveDraft(draft) }
                refreshAndSelect(draft.id, status = "Draft saved")
            }.onFailure { throwable ->
                _state.value = _state.value.copy(status = "Save failed: ${throwable.message}")
            }
        }
    }

    fun resetDraft() {
        val draft = _state.value.working ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { store.resetDraft(draft.id) }
                refreshAndSelect(draft.id, status = "Draft reset")
            }.onFailure { throwable ->
                _state.value = _state.value.copy(status = "Reset failed: ${throwable.message}")
            }
        }
    }

    fun duplicateSelected() {
        val draft = _state.value.working ?: return
        viewModelScope.launch {
            runCatching {
                val duplicate = withContext(Dispatchers.IO) { store.duplicate(draft.id) }
                refreshAndSelect(duplicate.id, status = "Duplicated to ${duplicate.displayName}")
            }.onFailure { throwable ->
                _state.value = _state.value.copy(status = "Duplicate failed: ${throwable.message}")
            }
        }
    }

    fun importDraft(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val imported = withContext(Dispatchers.IO) { importExport.importDraft(uri) }
                refreshAndSelect(imported.id, status = "Imported ${imported.displayName}")
            }.onFailure { throwable ->
                _state.value = _state.value.copy(status = "Import failed: ${throwable.message}")
            }
        }
    }

    fun exportDraft(onComplete: (File?) -> Unit) {
        val draft = _state.value.working ?: run {
            onComplete(null)
            return
        }
        viewModelScope.launch {
            runCatching {
                val exported = withContext(Dispatchers.IO) { importExport.exportDraft(draft) }
                _state.value = _state.value.copy(status = "Exported ${exported.name}")
                onComplete(exported)
            }.getOrElse { throwable ->
                _state.value = _state.value.copy(status = "Export failed: ${throwable.message}")
                onComplete(null)
            }
        }
    }

    private suspend fun refreshAndSelect(requestedId: String?, status: String? = null) {
        val (drills, selectedId, baseline) = withContext(Dispatchers.IO) {
            val drills = store.listDrills()
            val selectedId = requestedId
                ?.takeIf { id -> drills.any { it.id == id } }
                ?: drills.firstOrNull()?.id
                .orEmpty()
            val baseline = selectedId.takeIf { it.isNotBlank() }?.let(store::loadForEditor)
            Triple(drills, selectedId, baseline)
        }
        _state.value = DrillStudioEditorState(
            drills = drills,
            selectedDrillId = selectedId,
            baseline = baseline,
            working = baseline,
            status = status,
        )
    }
}
