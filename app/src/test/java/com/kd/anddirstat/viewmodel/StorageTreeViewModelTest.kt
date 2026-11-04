package com.kd.anddirstat.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StorageTreeViewModelTest {

    @Test
    fun testInitialState() {
        val viewModel = StorageTreeViewModel()
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
        val viewModel = StorageTreeViewModel()
        viewModel.cancelScan()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.isBackgroundScanning)
    }
}
