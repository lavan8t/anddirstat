package com.kd.anddirstat.viewmodel

import com.kd.anddirstat.cleanup.CleanupQueueHelper
import com.kd.anddirstat.cleanup.StagedQueueItem
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.util.StorageVolumeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the shell state of [com.kd.anddirstat.MainActivity]. Pure Kotlin: no Android
 * types and no View geometry (popup rects, touch offsets, gesture physics stay in the View).
 */
data class MainUiState(
    val selectedNode: CompactNode? = null,
    val selectedPath: String? = null,
    val activePopupNode: CompactNode? = null,
    val activePopupPath: String? = null,

    val selectedTreeNodes: Map<CompactNode, String> = emptyMap(),
    val discoverSearchQuery: String = "",
    val isDiscoverSearchActive: Boolean = false,

    val stagedQueue: Map<CompactNode, StagedQueueItem> = emptyMap(),
    val showCleanupReviewSheet: Boolean = false,

    val hasStoragePermission: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val usageAccessDismissed: Boolean = false,

    val showFreeSpace: Boolean = true,
    val showSystemApps: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val showSystemOS: Boolean = false,

    val detectedVolumes: List<StorageVolumeInfo> = emptyList(),
    val selectedVolumeIds: Set<String> = emptySet(),
    val activeVolume: StorageVolumeInfo? = null,
    val scanAppsSelected: Boolean = true,
    val showVolumeSelectionDialog: Boolean = false,

    val isTreeDeleting: Boolean = false,
    val treeDeleteCurrentCount: Int = 0,
    val treeDeleteTotalCount: Int = 0,
    val treeDeleteCurrentFileName: String = "",
    val treeDeleteIsTrash: Boolean = true,

    val explorerTargetPath: String? = null,
    val nodeToDelete: Pair<CompactNode, String>? = null,
    val showTreeDeleteDialog: Boolean = false,

    val blockReason: String? = null
)

class MainAppViewModel(
    hasStoragePermission: Boolean = false,
    hasUsageAccess: Boolean = false,
    usageAccessDismissed: Boolean = false,
    showFreeSpace: Boolean = true,
    showSystemApps: Boolean = true,
    showHiddenFiles: Boolean = false,
    showSystemOS: Boolean = false
) {
    private val _uiState = MutableStateFlow(
        MainUiState(
            hasStoragePermission = hasStoragePermission,
            hasUsageAccess = hasUsageAccess,
            usageAccessDismissed = usageAccessDismissed,
            showFreeSpace = showFreeSpace,
            showSystemApps = showSystemApps,
            showHiddenFiles = showHiddenFiles,
            showSystemOS = showSystemOS
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onNodeSelected(node: CompactNode?, path: String?) {
        _uiState.update { it.copy(selectedNode = node, selectedPath = path) }
    }

    fun onActivePopupChanged(node: CompactNode?, path: String?) {
        _uiState.update { it.copy(activePopupNode = node, activePopupPath = path) }
    }

    fun dismissPopup() {
        _uiState.update {
            it.copy(
                selectedNode = null,
                selectedPath = null,
                activePopupNode = null,
                activePopupPath = null
            )
        }
    }

    // Both selection modes live on the same screen; acquiring one must release the other.
    fun setTreeSelection(next: Map<CompactNode, String>) {
        _uiState.update {
            val cleared = if (next.isNotEmpty()) {
                it.copy(discoverSearchQuery = "", isDiscoverSearchActive = false)
            } else it
            cleared.copy(selectedTreeNodes = next)
        }
    }

    fun clearTreeSelection() {
        _uiState.update { it.copy(selectedTreeNodes = emptyMap()) }
    }

    fun setDiscoverSelection(query: String, active: Boolean) {
        _uiState.update {
            val cleared = if (query.isNotBlank() || active) it.copy(selectedTreeNodes = emptyMap()) else it
            cleared.copy(discoverSearchQuery = query, isDiscoverSearchActive = active)
        }
    }

    fun clearDiscoverSelection() {
        _uiState.update { it.copy(discoverSearchQuery = "", isDiscoverSearchActive = false) }
    }

    fun onToggleStaged(item: StagedQueueItem) {
        _uiState.update { it.copy(stagedQueue = CleanupQueueHelper.toggleItem(it.stagedQueue, item)) }
    }

    fun onUnstageStaged(node: CompactNode) {
        _uiState.update { it.copy(stagedQueue = it.stagedQueue - node) }
    }

    fun clearStaged() {
        _uiState.update { it.copy(stagedQueue = emptyMap()) }
    }

    fun showCleanupReviewSheet() {
        _uiState.update { it.copy(showCleanupReviewSheet = true) }
    }

    fun dismissCleanupReviewSheet() {
        _uiState.update { it.copy(showCleanupReviewSheet = false) }
    }

    fun setPermissions(hasStoragePermission: Boolean? = null, hasUsageAccess: Boolean? = null) {
        _uiState.update {
            it.copy(
                hasStoragePermission = hasStoragePermission ?: it.hasStoragePermission,
                hasUsageAccess = hasUsageAccess ?: it.hasUsageAccess
            )
        }
    }

    fun setUsageAccessDismissed(dismissed: Boolean) {
        _uiState.update { it.copy(usageAccessDismissed = dismissed) }
    }

    fun setFilterFlags(
        showFreeSpace: Boolean? = null,
        showSystemApps: Boolean? = null,
        showHiddenFiles: Boolean? = null,
        showSystemOS: Boolean? = null
    ) {
        _uiState.update {
            it.copy(
                showFreeSpace = showFreeSpace ?: it.showFreeSpace,
                showSystemApps = showSystemApps ?: it.showSystemApps,
                showHiddenFiles = showHiddenFiles ?: it.showHiddenFiles,
                showSystemOS = showSystemOS ?: it.showSystemOS
            )
        }
    }

    fun setVolumes(volumes: List<StorageVolumeInfo>) {
        _uiState.update { it.copy(detectedVolumes = volumes) }
    }

    fun setSelectedVolumeIds(ids: Set<String>) {
        _uiState.update { it.copy(selectedVolumeIds = ids) }
    }

    fun setActiveVolume(volume: StorageVolumeInfo?) {
        _uiState.update { it.copy(activeVolume = volume) }
    }

    fun setScanAppsSelected(selected: Boolean) {
        _uiState.update { it.copy(scanAppsSelected = selected) }
    }

    fun onToggleVolume(volumeId: String) {
        _uiState.update {
            val next = if (it.selectedVolumeIds.contains(volumeId)) {
                if (it.selectedVolumeIds.size > 1 || it.scanAppsSelected) {
                    it.selectedVolumeIds - volumeId
                } else {
                    it.selectedVolumeIds
                }
            } else {
                it.selectedVolumeIds + volumeId
            }
            it.copy(selectedVolumeIds = next)
        }
    }

    fun showVolumeSelectionDialog() {
        _uiState.update { it.copy(showVolumeSelectionDialog = true) }
    }

    fun dismissVolumeSelectionDialog() {
        _uiState.update { it.copy(showVolumeSelectionDialog = false) }
    }

    fun setTreeDeleting(deleting: Boolean) {
        _uiState.update { it.copy(isTreeDeleting = deleting) }
    }

    fun setDeleteProgress(current: Int, total: Int, fileName: String, isTrash: Boolean) {
        _uiState.update {
            it.copy(
                treeDeleteCurrentCount = current,
                treeDeleteTotalCount = total,
                treeDeleteCurrentFileName = fileName,
                treeDeleteIsTrash = isTrash
            )
        }
    }

    fun showTreeDeleteDialog() {
        _uiState.update { it.copy(showTreeDeleteDialog = true) }
    }

    fun dismissTreeDeleteDialog() {
        _uiState.update { it.copy(showTreeDeleteDialog = false) }
    }

    fun requestNodeDelete(node: CompactNode, path: String) {
        _uiState.update { it.copy(nodeToDelete = node to path) }
    }

    fun dismissNodeDelete() {
        _uiState.update { it.copy(nodeToDelete = null) }
    }

    fun setExplorerTargetPath(path: String) {
        _uiState.update { it.copy(explorerTargetPath = path) }
    }

    fun setBlockReason(reason: String?) {
        _uiState.update { it.copy(blockReason = reason) }
    }
}
