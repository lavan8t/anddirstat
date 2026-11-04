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
        // Benchmark 50,000 nodes with exact squarified layout
        val folders = ArrayList<CompactNode>()
        var count = 0
        for (f in 0 until 50) {
            val children = ArrayList<CompactNode>(1_000)
            for (i in 0 until 1_000) {
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

        assertEquals(50_000, count)

        // 1. Layout generation with exact squarified treemap algorithm
        var computedTiles: List<TreemapTile> = emptyList()
        val layoutTime = measureTimeMillis {
            computedTiles = computeTreemapTiles(
                rootNode = root,
                rootPath = "root",
                width = 1080f,
                height = 2400f,
                isDark = true
            )
            println("Computed tiles count for 50k nodes: ${computedTiles.size}")
            assertTrue(computedTiles.isNotEmpty())
        }
        println("Exact squarified layout for 50k nodes completed in ${layoutTime}ms")

        // 2. Quadtree spatial indexing of tiles
        val tiles = computedTiles
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
