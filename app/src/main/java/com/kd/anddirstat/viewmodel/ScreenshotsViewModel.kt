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

class ScreenshotsViewModel(
    private val source: ScreenshotSource,
    private val keptStore: KeptScreenshotsStore,
    private val stars: StarRepository,
    private val haptics: CleanerHaptics,
    private val notifier: CleanerNotifier,
    private val deleter: CleanerDeleter,
    private val restorer: TrashRestorer
) : ViewModel() {

    data class UiState(
        val items: List<ScreenshotItem> = emptyList(),
        val isScanning: Boolean = true,
        val totalInitialCount: Int = 0,
        val currentIndex: Int = 0,
        val isGridView: Boolean = false,
        val selection: CleanerSelectionState<ScreenshotSort> = CleanerSelectionState(sortOrder = ScreenshotSort.NEWEST),
        val history: List<SwipeHistoryItem> = emptyList()
    ) {
        val canUndo: Boolean get() = history.isNotEmpty()
        val currentItem: ScreenshotItem? get() = items.getOrNull(currentIndex)
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun sortItems(list: List<ScreenshotItem>, sort: ScreenshotSort): List<ScreenshotItem> = when (sort) {
        ScreenshotSort.NEWEST -> list.sortedByDescending { it.file.lastModified() }
        ScreenshotSort.OLDEST -> list.sortedBy { it.file.lastModified() }
        ScreenshotSort.LARGEST -> list.sortedByDescending { it.size }
    }

    fun onScan() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isScanning = true) }
            val kept = withContext(Dispatchers.IO) { keptStore.load() }
            val found = withContext(Dispatchers.IO) { source.load(kept) }
            val sorted = sortItems(found, _uiState.value.selection.sortOrder)
            _uiState.update {
                it.copy(
                    items = sorted,
                    totalInitialCount = sorted.size,
                    currentIndex = 0,
                    selection = it.selection.clear(),
                    isScanning = false
                )
            }
        }
    }

    fun onSortChanged(sort: ScreenshotSort) {
        _uiState.update {
            it.copy(selection = it.selection.setSort(sort), items = sortItems(it.items, sort), currentIndex = 0)
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

    fun onKeep() {
        val item = _uiState.value.currentItem ?: return
        haptics.perform(HapticKind.KEEP)
        val kept = keptStore.load().toMutableSet()
        kept.add(item.file.absolutePath)
        keptStore.save(kept)

        _uiState.update { state ->
            val next = state.items.toMutableList()
            next.removeAt(state.currentIndex)
            val nextIndex = if (state.currentIndex >= next.size) maxOf(0, next.size - 1) else state.currentIndex
            state.copy(
                items = next,
                currentIndex = nextIndex,
                history = state.history + SwipeHistoryItem(item, wasDeleted = false, originalIndex = state.currentIndex)
            )
        }
    }

    fun onDelete() {
        val state = _uiState.value
        val item = state.currentItem ?: return
        haptics.perform(HapticKind.DELETE)
        if (stars.isStarred(item.path)) {
            notifier.notify("Starred screenshot is protected.")
            return
        }

        _uiState.update { s ->
            val next = s.items.toMutableList()
            next.removeAt(s.currentIndex)
            val nextIndex = if (s.currentIndex >= next.size) maxOf(0, next.size - 1) else s.currentIndex
            s.copy(
                items = next,
                currentIndex = nextIndex,
                history = s.history + SwipeHistoryItem(item, wasDeleted = true, originalIndex = s.currentIndex)
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val outcome = deleter.deleteFile(item.path, isPermanent = false)
            outcome.blockReason?.let { notifier.notify("Deletion blocked: $it") }
        }
    }

    fun onUndo() {
        val state = _uiState.value
        val last = state.history.lastOrNull() ?: return
        if (last.wasDeleted) {
            viewModelScope.launch(Dispatchers.Default) {
                restorer.restore(listOf(last.item.path to last.item.name)) { _, _, _ -> }
            }
        } else {
            val kept = keptStore.load().toMutableSet()
            kept.remove(last.item.file.absolutePath)
            keptStore.save(kept)
        }

        val restored = state.items.toMutableList()
        val insertIndex = minOf(last.originalIndex, restored.size)
        restored.add(insertIndex, last.item)
        _uiState.update {
            it.copy(items = restored, currentIndex = insertIndex, history = state.history.dropLast(1))
        }
        notifier.notify("Undid review for ${last.item.name}")
    }

    fun onBatchDelete() {
        val state = _uiState.value
        val paths = state.items.filter { it.path in state.selection.selectedPaths }.map { it.path }.toSet()
        if (paths.isEmpty()) return
        haptics.perform(HapticKind.LONG_PRESS)
        viewModelScope.launch(Dispatchers.Default) {
            deleter.deleteFiles(paths, isPermanent = false, actionName = "Moved to Recycle Bin") { _, _, _ -> }
            onScan()
        }
    }
}
