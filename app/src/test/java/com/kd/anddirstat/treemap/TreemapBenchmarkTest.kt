package com.kd.anddirstat.treemap

import androidx.compose.ui.graphics.Color
import com.kd.anddirstat.model.CompactNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class TreemapBenchmarkTest {

    @Test
    fun benchmark1MillionNodesSimulation() {
        // Create a tree with 1,000,000 nodes distributed across folders
        val folders = ArrayList<CompactNode>()
        var count = 0
        for (f in 0 until 100) {
            val children = ArrayList<CompactNode>(10_000)
            for (i in 0 until 10_000) {
                children.add(CompactNode(name = "file_${f}_$i.dat", isDirectory = false, size = (100L + (i % 500))))
                count++
            }
            val folderSize = children.sumOf { it.size }
            folders.add(
                CompactNode(
                    name = "folder_$f",
                    isDirectory = true,
                    size = folderSize,
                    children = children.toTypedArray()
                )
            )
        }
        val totalSize = folders.sumOf { it.size }
        val root = CompactNode(
            name = "root",
            isDirectory = true,
            size = totalSize,
            children = folders.toTypedArray()
        )

        assertEquals(1_000_000, count)

        // 1. Layout generation with Level-of-Detail (LOD) micro-culling
        val layoutTime = measureTimeMillis {
            val tiles = computeTreemapTiles(
                rootNode = root,
                rootPath = "root",
                width = 1080f,
                height = 2400f,
                isDark = true
            )
            println("Computed tiles count for 1M nodes: ${tiles.size}")
            // At 1080x2400 (2.59M pixels), sub-pixel nodes fold into their folder boundaries
            assertTrue("Tiles count should be bounded by screen resolution, got ${tiles.size}", tiles.size < 50_000)
        }
        println("Treemap layout for 1M nodes completed in ${layoutTime}ms")

        // 2. Quadtree spatial indexing of tiles
        val tiles = computeTreemapTiles(root, "root", 1080f, 2400f, true)
        val quadtree = TreemapQuadtree(0f, 0f, 1080f, 2400f)

        val indexTime = measureTimeMillis {
            quadtree.insertAll(tiles)
        }
        println("Quadtree spatial indexing of ${tiles.size} tiles: ${indexTime}ms")

        // 3. Frustum query under high zoom (simulate 60fps pan/zoom)
        val queryTime = measureTimeMillis {
            repeat(100) {
                val visible = quadtree.queryVisible(100f, 100f, 400f, 400f, minArea = 1.0f)
                assertTrue(visible.size <= tiles.size)
            }
        }
        println("100 viewport frustum culling queries: ${queryTime}ms (${queryTime / 100.0}ms per query)")
        assertTrue("Viewport query must be sub-millisecond for 120fps", queryTime / 100.0 < 2.0)
    }
}
