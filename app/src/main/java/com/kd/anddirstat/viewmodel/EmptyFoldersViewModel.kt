package com.kd.anddirstat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EmptyFoldersViewModel(
    private val source: EmptyFolderSource,
    private val deleter: CleanerDeleter
) : ViewModel() {

    data class UiState(
        val folders: List<File> = emptyList(),
        val isScanning: Boolean = true,
        val selection: CleanerSelectionState<Unit> = CleanerSelectionState(sortOrder = Unit),
        val isOperating: Boolean = false,
        val progress: OperationProgress = OperationProgress()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onScan() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isScanning = true) }
            val found = withContext(Dispatchers.IO) { source.load() }
            _uiState.update {
                it.copy(
                    folders = found,
                    selection = it.selection.copy(selectedPaths = found.map { folder -> folder.absolutePath }.toSet()),
                    isScanning = false
                )
            }
        }
    }

    fun onToggleSelected(path: String) {
        _uiState.update { it.copy(selection = it.selection.toggle(path)) }
    }

    fun onSelectAllToggle() {
        _uiState.update {
            val allPaths = it.folders.map { folder -> folder.absolutePath }
            it.copy(selection = if (it.selection.selectedPaths.size == it.folders.size) it.selection.clear() else it.selection.selectAll(allPaths))
        }
    }

    fun onDeleteSelected() {
        val state = _uiState.value
        val paths = state.folders.filter { it.absolutePath in state.selection.selectedPaths }
            .sortedByDescending { it.absolutePath.length }
            .map { it.absolutePath }
            .toSet()
        if (paths.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isOperating = true) }
            deleter.deleteFiles(paths, isPermanent = true, actionName = "Deleted") { current, total, name ->
                _uiState.update { it.copy(progress = OperationProgress(current, total, name)) }
            }
            _uiState.update { it.copy(isOperating = false) }
            onScan()
        }
    }
}
