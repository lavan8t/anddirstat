package com.kd.anddirstat.viewmodel

import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.util.StorageVolumeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class FakeTreeStore(var tree: CompactNode? = null) : TreeStore {
    var saveCount = 0
        private set

    override suspend fun load(): CompactNode? = tree

    override suspend fun save(tree: CompactNode) {
        saveCount++
        this.tree = tree
    }
}

private class FakeProgress : ScanProgressSink {
    var finished = false
        private set
    val titles = mutableListOf<String>()

    override fun update(title: String, detail: String, progress: Int, max: Int, indeterminate: Boolean) {
        titles.add(title)
    }

    override fun finish(message: String?) {
        finished = true
    }
}

private class FakePermissions(
    private val storage: Boolean = true,
    private val usage: Boolean = false
) : StoragePermissionGate {
    override fun storageAllowed(): Boolean = storage
    override fun usageAllowed(): Boolean = usage
}

private class FakeScanner(private val result: CompactNode) : TreeScannerPort {
    override suspend fun scan(
        selectedVolumes: List<StorageVolumeInfo>,
        includeFreeSpace: Boolean,
        scanApps: Boolean,
        onProgress: (String, String, Int, Int) -> Unit
    ): CompactNode = result
}

private class FakeSnapshots : SnapshotSink {
    var count = 0
        private set

    override fun record(totalBytes: Long, usedBytes: Long) {
        count++
    }
}

private class FakeIcons : IconWarmer {
    var warmed: List<String> = emptyList()
        private set

    override fun warm(packageNames: List<String>) {
        warmed = packageNames
    }
}

class StorageTreeViewModelTest {

    private fun sampleTree(): CompactNode {
        val photo = CompactNode("photo.jpg", false, 60L)
        val dcim = CompactNode("DCIM", true, 60L).apply { children = arrayOf(photo) }
        val free = CompactNode("[Free Space]", false, 40L)
        return CompactNode("root", true, 100L).apply { children = arrayOf(dcim, free) }
    }

    private fun viewModel(
        store: FakeTreeStore = FakeTreeStore(),
        progress: FakeProgress = FakeProgress(),
        scanner: FakeScanner = FakeScanner(sampleTree()),
        snapshots: FakeSnapshots = FakeSnapshots(),
        icons: FakeIcons = FakeIcons()
    ) = Triple(
        StorageTreeViewModel(store, progress, FakePermissions(), scanner, snapshots, icons),
        store,
        progress
    )

    private fun waitFor(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3000L
        while (!predicate() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not met in time", predicate())
    }

    @Test
    fun testInitialState() {
        val (viewModel, _, _) = viewModel()
        val state = viewModel.uiState.value

        assertNull(state.rootNode)
        assertNull(state.rawScannedNode)
        assertEquals(0L, state.deviceTotalBytes)
        assertEquals(0, state.extensionStats.size)
        assertEquals(0, state.topFiles.size)
        assertFalse(state.isLoading)
        assertFalse(state.isBackgroundScanning)
    }

    @Test
    fun testCancelScanResetsFlags() {
        val (viewModel, _, _) = viewModel()
        viewModel.cancelScan()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.isBackgroundScanning)
    }

    @Test
    fun testRestoreFromCacheAppliesFilterSettings() {
        val (viewModel, _, _) = viewModel(store = FakeTreeStore(sampleTree()))
        viewModel.restoreOrScan(emptyList(), false, StorageFilterSettings(showFreeSpace = false))

        waitFor { viewModel.uiState.value.rawScannedNode != null }
        val state = viewModel.uiState.value
        assertNotNull(state.rawScannedNode)
        assertNotNull(state.rootNode)
        assertEquals(60L, state.rootNode!!.size)
        assertEquals(100L, state.deviceTotalBytes)
    }

    @Test
    fun testScanPublishesProgressAndStoresTree() {
        val (viewModel, store, progress) = viewModel()
        val volume = StorageVolumeInfo("primary", "Internal", File("/storage/emulated/0"), true, false, false, 100L, 40L)
        viewModel.performScan(listOf(volume), false, StorageFilterSettings())

        waitFor { viewModel.uiState.value.rawScannedNode != null }
        assertNotNull(viewModel.uiState.value.rawScannedNode)
        assertTrue(progress.finished)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun testFilterEventRecomputesFilteredTree() {
        val (viewModel, _, _) = viewModel(store = FakeTreeStore(sampleTree()))
        viewModel.restoreOrScan(emptyList(), false, StorageFilterSettings())
        waitFor { viewModel.uiState.value.rootNode != null }
        assertEquals(100L, viewModel.uiState.value.rootNode!!.size)

        viewModel.updateFilter(StorageFilterSettings(showFreeSpace = false))
        waitFor { viewModel.uiState.value.rootNode?.size == 60L }
        assertEquals(60L, viewModel.uiState.value.rootNode!!.size)
    }

    @Test
    fun testPruneRemovesDeletedNodesAndSavesTree() {
        val (viewModel, store, _) = viewModel(store = FakeTreeStore(sampleTree()))
        viewModel.restoreOrScan(emptyList(), false, StorageFilterSettings())
        waitFor { viewModel.uiState.value.rawScannedNode != null }

        val photo = viewModel.uiState.value.rawScannedNode!!.children!![0].children!![0]
        viewModel.removeDeletedNodes(setOf(photo), StorageFilterSettings())
        waitFor { store.saveCount == 1 }

        assertTrue(viewModel.uiState.value.rawScannedNode!!.children!![0].children!!.isEmpty())
        assertEquals(1, store.saveCount)
    }
}
