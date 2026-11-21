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

class RecycleBinViewModel(
    private val source: TrashSource,
    private val deleter: CleanerDeleter,
    private val restorer: TrashRestorer,
    private val haptics: CleanerHaptics,
    private val notifier: CleanerNotifier
) : ViewModel() {

    data class UiState(
        val items: List<TrashedItem> = emptyList(),
        val isLoading: Boolean = true,
        val isGridView: Boolean = false,
        val selection: CleanerSelectionState<TrashSort> = CleanerSelectionState(sortOrder = TrashSort.NEWEST),
        val isOperating: Boolean = false,
        val progress: OperationProgress = OperationProgress(),
        val operationIsTrash: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun applySort(list: List<TrashedItem>, sort: TrashSort): List<TrashedItem> = when (sort) {
        TrashSort.NEWEST -> list.sortedByDescending { it.lastModified }
        TrashSort.OLDEST -> list.sortedBy { it.lastModified }
        TrashSort.LARGEST -> list.sortedByDescending { it.size }
        TrashSort.SMALLEST -> list.sortedBy { it.size }
        TrashSort.NAME_AZ -> list.sortedBy { it.cleanName.lowercase() }
    }

    fun onScan() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isLoading = true) }
            val found = withContext(Dispatchers.IO) { source.load() }
            val sorted = applySort(found, _uiState.value.selection.sortOrder)
            _uiState.update {
                it.copy(items = sorted, selection = it.selection.clear(), isLoading = false)
            }
        }
    }

    fun onSortChanged(sort: TrashSort) {
        _uiState.update {
            val sorted = applySort(it.items, sort)
            it.copy(selection = it.selection.setSort(sort), items = sorted)
        }
    }

    fun onToggleGrid() {
        haptics.perform(HapticKind.TEXT_MOVE)
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun onToggleSelected(path: String) {
        _uiState.update { it.copy(selection = it.selection.toggle(path)) }
    }

    fun onSelectAllToggle() {
        _uiState.update {
            val allPaths = it.items.map { item -> item.path }
            it.copy(selection = if (it.selection.selectedPaths.size == it.items.size) it.selection.clear() else it.selection.selectAll(allPaths))
        }
    }

    fun onDeleteSelected() {
        val state = _uiState.value
        val paths = state.items.filter { it.path in state.selection.selectedPaths }.map { it.path }.toSet()
        if (paths.isEmpty()) return

        haptics.perform(HapticKind.LONG_PRESS)
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isOperating = true, operationIsTrash = false) }
            deleter.deleteFiles(paths, isPermanent = true, actionName = "Permanently deleted") { current, total, name ->
                _uiState.update { it.copy(progress = OperationProgress(current, total, name)) }
            }
            _uiState.update { it.copy(isOperating = false) }
            onScan()
        }
    }

    fun onRestoreSelected() {
        val state = _uiState.value
        val targets = state.items.filter { it.path in state.selection.selectedPaths }
            .map { it.path to it.cleanName }
        if (targets.isEmpty()) return

        haptics.perform(HapticKind.TEXT_MOVE)
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update {
                it.copy(
                    isOperating = true,
                    operationIsTrash = true,
                    progress = OperationProgress(total = targets.size)
                )
            }
            val restored = restorer.restore(targets) { current, total, name ->
                _uiState.update { it.copy(progress = OperationProgress(current, total, name)) }
            }
            _uiState.update { it.copy(isOperating = false) }
            notifier.notify("Restored $restored items")
            onScan()
        }
    }
}
