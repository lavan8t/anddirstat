package com.kd.anddirstat.treemap

class TreemapQuadtree(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    private val maxDepth: Int = 12,
    private val maxItemsPerNode: Int = 32,
    private val depth: Int = 0
) {
    private val midX: Float = (minX + maxX) / 2f
    private val midY: Float = (minY + maxY) / 2f

    private val items = ArrayList<TreemapTile>(maxItemsPerNode)
    private var nw: TreemapQuadtree? = null
    private var ne: TreemapQuadtree? = null
    private var sw: TreemapQuadtree? = null
    private var se: TreemapQuadtree? = null
    private var isDivided = false

    private var currentQueryId = 0

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

        val count = items.size
        for (i in 0 until count) {
            val item = items[i]
            nw?.insert(item)
            ne?.insert(item)
            sw?.insert(item)
            se?.insert(item)
        }
        items.clear()
    }

    fun queryVisible(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        minArea: Float = 0f
    ): List<TreemapTile> {
        currentQueryId++
        if (currentQueryId == Int.MAX_VALUE) currentQueryId = 1
        val qId = currentQueryId
        val result = ArrayList<TreemapTile>(1024)
        queryInternal(left, top, right, bottom, minArea, qId, result)
        return result
    }

    private fun queryInternal(
        qLeft: Float,
        qTop: Float,
        qRight: Float,
        qBottom: Float,
        minArea: Float,
        qId: Int,
        outResult: MutableList<TreemapTile>
    ) {
        if (qRight < minX || qLeft > maxX || qBottom < minY || qTop > maxY) {
            return
        }

        val count = items.size
        for (i in 0 until count) {
            val item = items[i]
            if (item.lastQueryId == qId) continue

            val tileRight = item.left + item.width
            val tileBottom = item.top + item.height

            if (tileRight >= qLeft && item.left <= qRight && tileBottom >= qTop && item.top <= qBottom) {
                if (item.width * item.height >= minArea) {
                    item.lastQueryId = qId
                    outResult.add(item)
                }
            }
        }

        if (isDivided) {
            nw?.queryInternal(qLeft, qTop, qRight, qBottom, minArea, qId, outResult)
            ne?.queryInternal(qLeft, qTop, qRight, qBottom, minArea, qId, outResult)
            sw?.queryInternal(qLeft, qTop, qRight, qBottom, minArea, qId, outResult)
            se?.queryInternal(qLeft, qTop, qRight, qBottom, minArea, qId, outResult)
        }
    }
}
