package com.kd.anddirstat.cleanup

import com.kd.anddirstat.model.CompactNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupModelsTest {

    @Test
    fun testCalculateTotalReclaimable() {
        val node1 = CompactNode("file1.mp4", false, size = 1000L)
        val node2 = CompactNode("file2.jpg", false, size = 2500L)
        val items = listOf(
            StagedQueueItem(node1, "/path/file1.mp4", 1000L),
            StagedQueueItem(node2, "/path/file2.jpg", 2500L)
        )
        val total = CleanupQueueHelper.calculateTotalReclaimable(items)
        assertEquals(3500L, total)
    }

    @Test
    fun testToggleQueueItem() {
        val node = CompactNode("file1.mp4", false, size = 1000L)
        val item = StagedQueueItem(node, "/path/file1.mp4", 1000L)
        val initialQueue = emptyMap<CompactNode, StagedQueueItem>()

        val added = CleanupQueueHelper.toggleItem(initialQueue, item)
        assertTrue(added.containsKey(node))
        assertEquals(1, added.size)

        val removed = CleanupQueueHelper.toggleItem(added, item)
        assertFalse(removed.containsKey(node))
        assertEquals(0, removed.size)
    }

    @Test
    fun testStagedQueueItemDefaultValues() {
        val node = CompactNode("file.txt", false, size = 500L)
        val item = StagedQueueItem(node, "/path/file.txt", 500L)
        assertFalse(item.isApp)
        assertEquals(null, item.pkgName)
    }
}
