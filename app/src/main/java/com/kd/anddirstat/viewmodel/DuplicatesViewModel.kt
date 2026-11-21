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

class DuplicatesViewModel(
    private val source: DuplicateSource,
    private val deleter: CleanerDeleter,
    private val haptics: CleanerHaptics
) : ViewModel() {

    data class UiState(
        val groups: List<DuplicateGroup> = emptyList(),
        val isScanning: Boolean = true,
        val selection: CleanerSelectionState<DuplicateSort> = CleanerSelectionState(sortOrder = DuplicateSort.LARGEST),
        val expandedKeys: Set<String> = emptySet(),
        val isOperating: Boolean = false,
        val progress: OperationProgress = OperationProgress()
    ) {
        val allSelectableCount: Int get() = groups.sumOf { it.files.size }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Groups flat file entries by (name, size) — pure so it is testable from an in-memory list. */
    fun groupFiles(files: List<DuplicateFileItem>): List<DuplicateGroup> {
        return files.groupBy { it.path.substringAfterLast('/') to it.file.length() }
            .filter { it.value.size >= 2 }
            .map { (key, matching) ->
                val name = key.first
                val size = key.second
                DuplicateGroup(
                    key = "${name}_${size}",
                    fileName = name,
                    fileSize = size,
                    files = matching.map {
                        DuplicateFileItem(file = it.file, path = it.path, lastModified = it.lastModified)
                    }
                )
            }
    }

    fun sortGroups(groups: List<DuplicateGroup>, sort: DuplicateSort): List<DuplicateGroup> = when (sort) {
        DuplicateSort.LARGEST -> groups.sortedByDescending { it.fileSize * (it.files.size - 1) }
        DuplicateSort.MOST_COPIES -> groups.sortedByDescending { it.files.size }
        DuplicateSort.NAME_AZ -> groups.sortedBy { it.fileName.lowercase() }
    }

    fun onScan() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isScanning = true) }
            val files = withContext(Dispatchers.IO) { source.load() }
            val groups = sortGroups(groupFiles(files), _uiState.value.selection.sortOrder)
            _uiState.update {
                it.copy(
                    groups = groups,
                    expandedKeys = groups.map { group -> group.key }.toSet(),
                    selection = autoSelect(it.selection, groups, AutoSelectStrategy.KEEP_NEWEST),
                    isScanning = false
                )
            }
        }
    }

    private fun autoSelect(
        selection: CleanerSelectionState<DuplicateSort>,
        groups: List<DuplicateGroup>,
        strategy: AutoSelectStrategy
    ): CleanerSelectionState<DuplicateSort> {
        val paths = mutableSetOf<String>()
        for (group in groups) {
            when (strategy) {
                AutoSelectStrategy.KEEP_NEWEST -> group.files.sortedByDescending { it.lastModified }.drop(1)
                AutoSelectStrategy.KEEP_OLDEST -> group.files.sortedBy { it.lastModified }.drop(1)
                AutoSelectStrategy.SELECT_ALL -> group.files
                AutoSelectStrategy.DESELECT_ALL -> emptyList()
            }.forEach { paths.add(it.path) }
        }
        return selection.copy(selectedPaths = paths)
    }

    fun onSortChanged(sort: DuplicateSort) {
        _uiState.update {
            it.copy(selection = it.selection.setSort(sort), groups = sortGroups(it.groups, sort))
        }
    }

    fun onToggleExpanded(key: String) {
        _uiState.update {
            val expanded = it.expandedKeys
            it.copy(expandedKeys = if (key in expanded) expanded - key else expanded + key)
        }
    }

    fun onToggleSelected(path: String) {
        _uiState.update { it.copy(selection = it.selection.toggle(path)) }
    }

    fun onAutoSelect(strategy: AutoSelectStrategy) {
        _uiState.update { it.copy(selection = autoSelect(it.selection, it.groups, strategy)) }
    }

    fun onDeleteSelected() {
        val state = _uiState.value
        val paths = state.groups.flatMap { it.files }
            .filter { it.path in state.selection.selectedPaths }
            .map { it.path }
            .toSet()
        if (paths.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isOperating = true) }
            deleter.deleteFiles(paths, isPermanent = false, actionName = "Moved to Recycle Bin") { current, total, name ->
                _uiState.update { it.copy(progress = OperationProgress(current, total, name)) }
            }
            _uiState.update { it.copy(isOperating = false) }
            onScan()
        }
    }
}
