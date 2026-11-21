package com.kd.anddirstat.viewmodel

import com.kd.anddirstat.cleanup.CleanupQueueHelper
import com.kd.anddirstat.cleanup.StagedQueueItem
import com.kd.anddirstat.model.CompactNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainAppViewModelTest {

    private fun node(name: String, size: Long = 0L) = CompactNode(name, false, size)

    @Test
    fun initialStateIsEmpty() {
        val state = MainAppViewModel().uiState.value

        assertNull(state.selectedNode)
        assertNull(state.selectedPath)
        assertNull(state.activePopupNode)
        assertNull(state.activePopupPath)
        assertTrue(state.selectedTreeNodes.isEmpty())
        assertEquals("", state.discoverSearchQuery)
        assertFalse(state.isDiscoverSearchActive)
        assertTrue(state.stagedQueue.isEmpty())
        assertFalse(state.showCleanupReviewSheet)
        assertFalse(state.hasStoragePermission)
        assertFalse(state.hasUsageAccess)
        assertFalse(state.usageAccessDismissed)
        assertTrue(state.showFreeSpace)
        assertTrue(state.showSystemApps)
        assertFalse(state.showHiddenFiles)
        assertFalse(state.showSystemOS)
        assertTrue(state.detectedVolumes.isEmpty())
        assertTrue(state.selectedVolumeIds.isEmpty())
        assertNull(state.activeVolume)
        assertTrue(state.scanAppsSelected)
        assertFalse(state.showVolumeSelectionDialog)
        assertFalse(state.isTreeDeleting)
        assertEquals(0, state.treeDeleteCurrentCount)
        assertEquals(0, state.treeDeleteTotalCount)
        assertEquals("", state.treeDeleteCurrentFileName)
        assertTrue(state.treeDeleteIsTrash)
        assertNull(state.explorerTargetPath)
        assertNull(state.nodeToDelete)
        assertFalse(state.showTreeDeleteDialog)
        assertNull(state.blockReason)
    }

    @Test
    fun constructorAppliesInitialFlags() {
        val state = MainAppViewModel(
            hasStoragePermission = true,
            hasUsageAccess = true,
            usageAccessDismissed = true,
            showFreeSpace = false,
            showSystemApps = false,
            showHiddenFiles = true,
            showSystemOS = true
        ).uiState.value

        assertTrue(state.hasStoragePermission)
        assertTrue(state.hasUsageAccess)
        assertTrue(state.usageAccessDismissed)
        assertFalse(state.showFreeSpace)
        assertFalse(state.showSystemApps)
        assertTrue(state.showHiddenFiles)
        assertTrue(state.showSystemOS)
    }

    @Test
    fun selectingTreeNodeClearsDiscoverSelection() {
        val viewModel = MainAppViewModel()
        viewModel.setDiscoverSelection("photo", true)

        val node = node("photo.jpg")
        viewModel.setTreeSelection(mapOf(node to "/DCIM/photo.jpg"))

        val state = viewModel.uiState.value
        assertEquals(mapOf(node to "/DCIM/photo.jpg"), state.selectedTreeNodes)
        assertEquals("", state.discoverSearchQuery)
        assertFalse(state.isDiscoverSearchActive)
    }

    @Test
    fun selectingDiscoverClearsTreeSelection() {
        val viewModel = MainAppViewModel()
        viewModel.setTreeSelection(mapOf(node("a") to "/a"))

        viewModel.setDiscoverSelection("query", true)

        val state = viewModel.uiState.value
        assertTrue(state.selectedTreeNodes.isEmpty())
        assertEquals("query", state.discoverSearchQuery)
        assertTrue(state.isDiscoverSearchActive)
    }

    @Test
    fun stagingAndUnstagingChangeReclaimableBytes() {
        val viewModel = MainAppViewModel()
        val node = node("cache", 120L)
        val item = StagedQueueItem(node = node, path = "/cache", reclaimableBytes = 120L)

        viewModel.onToggleStaged(item)
        assertEquals(1, viewModel.uiState.value.stagedQueue.size)
        assertEquals(120L, CleanupQueueHelper.calculateTotalReclaimable(viewModel.uiState.value.stagedQueue.values))

        viewModel.onToggleStaged(item)
        assertTrue(viewModel.uiState.value.stagedQueue.isEmpty())
        assertEquals(0L, CleanupQueueHelper.calculateTotalReclaimable(viewModel.uiState.value.stagedQueue.values))
    }

    @Test
    fun filterEventUpdatesFlags() {
        val viewModel = MainAppViewModel()

        viewModel.setFilterFlags(showFreeSpace = false, showHiddenFiles = true)

        val state = viewModel.uiState.value
        assertFalse(state.showFreeSpace)
        assertTrue(state.showSystemApps)
        assertTrue(state.showHiddenFiles)
        assertFalse(state.showSystemOS)
    }

    @Test
    fun clearStagedEmptiesQueue() {
        val viewModel = MainAppViewModel()
        viewModel.onToggleStaged(StagedQueueItem(node("a", 10L), "/a", 10L))
        viewModel.onToggleStaged(StagedQueueItem(node("b", 20L), "/b", 20L))
        assertEquals(2, viewModel.uiState.value.stagedQueue.size)

        viewModel.clearStaged()

        assertTrue(viewModel.uiState.value.stagedQueue.isEmpty())
    }

    @Test
    fun dismissPopupClearsBothNodeAndActivePopup() {
        val viewModel = MainAppViewModel()
        val node = node("file")
        viewModel.onNodeSelected(node, "/file")
        viewModel.onActivePopupChanged(node, "/file")

        viewModel.dismissPopup()

        val state = viewModel.uiState.value
        assertNull(state.selectedNode)
        assertNull(state.selectedPath)
        assertNull(state.activePopupNode)
        assertNull(state.activePopupPath)
    }

    @Test
    fun toggleVolumeKeepsLastSelectionWhenScanAppsDisabled() {
        val viewModel = MainAppViewModel()
        viewModel.setScanAppsSelected(false)
        viewModel.setSelectedVolumeIds(setOf("primary"))

        viewModel.onToggleVolume("primary")
        assertEquals(setOf("primary"), viewModel.uiState.value.selectedVolumeIds)

        viewModel.onToggleVolume("sdcard")
        assertEquals(setOf("primary", "sdcard"), viewModel.uiState.value.selectedVolumeIds)
    }
}
