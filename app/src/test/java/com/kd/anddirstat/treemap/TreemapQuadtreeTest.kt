package com.kd.anddirstat.treemap

import androidx.compose.ui.graphics.Color
import com.kd.anddirstat.model.CompactNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreemapQuadtreeTest {

    private fun createTile(name: String, left: Float, top: Float, width: Float, height: Float): TreemapTile {
        return TreemapTile(
            node = CompactNode(name = name, isDirectory = false, size = 100L),
            path = name,
            left = left,
            top = top,
            width = width,
            height = height,
            isApp = false,
            pkgName = null,
            baseColor = Color.Blue,
            gradientColors = listOf(Color.Blue, Color.Cyan)
        )
    }

    @Test
    fun queryVisible_emptyTree_returnsEmptyList() {
        val quadtree = TreemapQuadtree(0f, 0f, 1000f, 1000f)
        val visible = quadtree.queryVisible(0f, 0f, 500f, 500f)
        assertTrue(visible.isEmpty())
    }

    @Test
    fun queryVisible_returnsOnlyTilesInsideOrIntersectingViewport() {
        val quadtree = TreemapQuadtree(0f, 0f, 1000f, 1000f)
        val tileIn = createTile("in", 100f, 100f, 100f, 100f) // (100, 100) -> (200, 200)
        val tileOut = createTile("out", 600f, 600f, 100f, 100f) // (600, 600) -> (700, 700)
        val tilePartial = createTile("partial", 450f, 450f, 100f, 100f) // (450, 450) -> (550, 550)

        quadtree.insertAll(listOf(tileIn, tileOut, tilePartial))

        // Viewport covering (0,0) to (500,500)
        val visible = quadtree.queryVisible(0f, 0f, 500f, 500f)

        assertEquals(2, visible.size)
        assertTrue(visible.contains(tileIn))
        assertTrue(visible.contains(tilePartial))
        org.junit.Assert.assertFalse(visible.contains(tileOut))
    }

    @Test
    fun queryVisible_subpixelPruning_skipsTinyTiles() {
        val quadtree = TreemapQuadtree(0f, 0f, 1000f, 1000f)
        val normalTile = createTile("normal", 50f, 50f, 20f, 20f) // area = 400
        val tinyTile = createTile("tiny", 10f, 10f, 0.5f, 0.5f) // area = 0.25

        quadtree.insertAll(listOf(normalTile, tinyTile))

        val visible = quadtree.queryVisible(0f, 0f, 200f, 200f, minArea = 1.0f)
        assertEquals(1, visible.size)
        assertEquals("normal", visible.first().node.name)
    }
}
