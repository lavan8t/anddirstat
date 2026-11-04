package com.kd.anddirstat.treemap

import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.ui.screens.generate50kScatterTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestTreemapGeneratorTest {

    @Test
    fun test50kScatterTreeGeneratesExactCountAndStructure() {
        val root = generate50kScatterTree(targetCount = 50_000, seed = 12345L)

        // Count all leaf files in tree
        var fileCount = 0
        fun countFiles(node: CompactNode) {
            val children = node.children
            if (children == null || children.isEmpty()) {
                if (!node.isDirectory) {
                    fileCount++
                }
            } else {
                for (child in children) {
                    countFiles(child)
                }
            }
        }

        countFiles(root)
        assertEquals("Should generate exactly 50,000 leaf files", 50_000, fileCount)
        assertTrue("Total size of tree should be positive", root.size > 0L)
        assertTrue("Should have top level folders", (root.children?.size ?: 0) > 10)
    }
}
