package com.kd.anddirstat.treemap

class TreemapQuadtree(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    private val maxDepth: Int = 8,
    private val maxItemsPerNode: Int = 16,
    private val depth: Int = 0
) {
    private val midX: Float = (minX + maxX) / 2f
    private val midY: Float = (minY + maxY) / 2f

    private val items = mutableListOf<TreemapTile>()
    private var nw: TreemapQuadtree? = null
    private var ne: TreemapQuadtree? = null
    private var sw: TreemapQuadtree? = null
    private var se: TreemapQuadtree? = null
    private var isDivided = false

    fun insert(tile: TreemapTile) {
        val tileRight = tile.left + tile.width
        val tileBottom = tile.top + tile.height

        if (tileRight < minX || tile.left > maxX || tileBottom < minY || tile.top > maxY) {
            return
        }

        if (!isDivided) {
            if (items.size < maxItemsPerNode || depth >= maxDepth) {
                items.add(tile)
                return
            }
            subdivide()
        }

        nw?.insert(tile)
        ne?.insert(tile)
        sw?.insert(tile)
        se?.insert(tile)
    }

    fun insertAll(tiles: Collection<TreemapTile>) {
        for (tile in tiles) {
            insert(tile)
        }
    }

    private fun subdivide() {
        val nextDepth = depth + 1
        nw = TreemapQuadtree(minX, minY, midX, midY, maxDepth, maxItemsPerNode, nextDepth)
        ne = TreemapQuadtree(midX, minY, maxX, midY, maxDepth, maxItemsPerNode, nextDepth)
        sw = TreemapQuadtree(minX, midY, midX, maxY, maxDepth, maxItemsPerNode, nextDepth)
        se = TreemapQuadtree(midX, midY, maxX, maxY, maxDepth, maxItemsPerNode, nextDepth)
        isDivided = true

        val existing = ArrayList(items)
        items.clear()
        for (item in existing) {
            nw?.insert(item)
            ne?.insert(item)
            sw?.insert(item)
            se?.insert(item)
        }
    }

    fun queryVisible(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        minArea: Float = 0f
    ): List<TreemapTile> {
        val result = mutableSetOf<TreemapTile>()
        queryInternal(left, top, right, bottom, minArea, result)
        return result.toList()
    }

    private fun queryInternal(
        qLeft: Float,
        qTop: Float,
        qRight: Float,
        qBottom: Float,
        minArea: Float,
        outResult: MutableSet<TreemapTile>
    ) {
        if (qRight < minX || qLeft > maxX || qBottom < minY || qTop > maxY) {
            return
        }

        for (item in items) {
            val tileRight = item.left + item.width
            val tileBottom = item.top + item.height

            if (tileRight >= qLeft && item.left <= qRight && tileBottom >= qTop && item.top <= qBottom) {
                if (item.width * item.height >= minArea) {
                    outResult.add(item)
                }
            }
        }

        if (isDivided) {
            nw?.queryInternal(qLeft, qTop, qRight, qBottom, minArea, outResult)
            ne?.queryInternal(qLeft, qTop, qRight, qBottom, minArea, outResult)
            sw?.queryInternal(qLeft, qTop, qRight, qBottom, minArea, outResult)
            se?.queryInternal(qLeft, qTop, qRight, qBottom, minArea, outResult)
        }
    }
}
