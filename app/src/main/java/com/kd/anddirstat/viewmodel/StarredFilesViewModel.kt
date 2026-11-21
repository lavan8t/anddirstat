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

class StarredFilesViewModel(
    private val source: StarredSource,
    private val stars: StarRepository,
    private val haptics: CleanerHaptics,
    private val notifier: CleanerNotifier,
    private val opener: FileOpener
) : ViewModel() {

    data class UiState(
        val items: List<StarredItem> = emptyList(),
        val searchQuery: String = "",
        val isSearchActive: Boolean = false,
        val isGridView: Boolean = false,
        val selection: CleanerSelectionState<Unit> = CleanerSelectionState(sortOrder = Unit)
    ) {
        val selectedPaths: Set<String> get() = selection.selectedPaths
        val filteredItems: List<StarredItem>
            get() = if (searchQuery.isBlank()) items else items.filter {
                it.name.contains(searchQuery, ignoreCase = true) || it.path.contains(searchQuery, ignoreCase = true)
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onRefresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val items = withContext(Dispatchers.IO) { source.load() }
            _uiState.update { it.copy(items = items) }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchActiveChanged(active: Boolean) {
        _uiState.update {
            if (active) it.copy(isSearchActive = true)
            else it.copy(isSearchActive = false, searchQuery = "")
        }
    }

    fun onToggleGrid() {
        haptics.perform(HapticKind.TEXT_MOVE)
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun onToggleSelected(path: String) {
        _uiState.update { it.copy(selection = it.selection.toggle(path)) }
    }

    fun onClearSelection() {
        _uiState.update { it.copy(selection = it.selection.clear()) }
    }

    fun onToggleStar(path: String) {
        haptics.perform(HapticKind.TEXT_MOVE)
        stars.toggle(path)
        onRefresh()
    }

    fun onUnstarSelected() {
        val paths = _uiState.value.selection.selectedPaths
        if (paths.isEmpty()) return
        haptics.perform(HapticKind.LONG_PRESS)
        paths.forEach { stars.toggle(it) }
        _uiState.update { it.copy(selection = it.selection.clear()) }
        onRefresh()
        notifier.notify("Unstarred selected items")
    }

    fun onOpenItem(path: String) {
        opener.open(path)
    }
}
