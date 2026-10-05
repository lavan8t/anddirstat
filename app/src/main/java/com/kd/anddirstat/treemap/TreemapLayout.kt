package com.kd.anddirstat.treemap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.ui.components.AppIconCache
import com.kd.anddirstat.util.FileUtils

/** Cushion shading colours (highlight, base, shadow); single source for both renderers. */
fun shadeColors(base: Color, isDark: Boolean): List<Color> {
    val hm = if (isDark) 1.65f else 1.75f
    val ha = if (isDark) 0.25f else 0.35f
    val sm = if (isDark) 0.45f else 0.40f
    fun c(m: Float, a: Float) = Color(
        (base.red * m + a).coerceIn(0f, 1f),
        (base.green * m + a).coerceIn(0f, 1f),
        (base.blue * m + a).coerceIn(0f, 1f),
        1f
    )
    return listOf(c(hm, ha), base, c(sm, 0f))
}

class TreemapTile(
    val node: CompactNode,
    val path: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val isApp: Boolean,
    val pkgName: String?,
    val baseColor: Color,
    val gradientColors: List<Color>
) {
    val cushionBrush: Brush by lazy(LazyThreadSafetyMode.NONE) {
        Brush.radialGradient(gradientColors, center = Offset(0.38f, 0.35f), radius = 0.85f)
    }
}

fun computeTreemapTiles(
    rootNode: CompactNode,
    rootPath: String,
    width: Float,
    height: Float,
    isDark: Boolean = true
): List<TreemapTile> {
    if (width <= 0f || height <= 0f || rootNode.size <= 0L) return emptyList()

    val tiles = ArrayList<TreemapTile>(1024)

    fun buildTiles(node: CompactNode, currentPath: String, l: Float, t: Float, w: Float, h: Float, inAppsScope: Boolean) {
        val area = w * h
        // Micro-area Level of Detail (LOD) aggregation:
        // If a directory or node is smaller than 4 square px or < 1px in either dimension,
        // stop recursing into its thousands of sub-pixel children. Treat it as a single aggregated tile.
        if (w < 1.0f || h < 1.0f || area < 4.0f) {
            val baseColor = getNodeColor(node, isDark)
            tiles.add(TreemapTile(node, currentPath, l, t, w, h, false, null, baseColor, shadeColors(baseColor, isDark)))
            return
        }

        val children = node.children
        val inApps = inAppsScope || node.name == "Apps & System Packages"
        val isApp = inApps && children?.any { it.name.startsWith("App Code") } == true

        if (children == null || children.isEmpty() || isApp) {
            val pkg = if (isApp) FileUtils.extractPackageName(node) else null
            val appDominantColor = if (pkg != null) AppIconCache.getDominantColor(pkg) else null
            val baseColor = appDominantColor ?: getNodeColor(node, isDark)
            tiles.add(TreemapTile(node, currentPath, l, t, w, h, isApp, pkg, baseColor, shadeColors(baseColor, isDark)))
            return
        }

        val validChildren = if (children.size <= 32) {
            children.filter { it.size > 0L }
        } else {
            val list = ArrayList<CompactNode>(children.size)
            for (c in children) {
                if (c.size > 0L) list.add(c)
            }
            list
        }
        if (validChildren.isEmpty()) return

        layoutSquarified(validChildren, l, t, w, h) { child, cLeft, cTop, cWidth, cHeight ->
            val childPath = if (currentPath == "Device Storage") child.name else if (currentPath.endsWith("/")) "$currentPath${child.name}" else "$currentPath/${child.name}"
            buildTiles(child, childPath, cLeft, cTop, cWidth, cHeight, inApps)
        }
    }

    buildTiles(rootNode, rootPath, 0f, 0f, width, height, false)
    return tiles
}

fun layoutSquarified(
    children: List<CompactNode>,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    onChildLayout: (child: CompactNode, cLeft: Float, cTop: Float, cWidth: Float, cHeight: Float) -> Unit
) {
    val totalBytes = children.sumOf { it.size }
    if (children.isEmpty() || width <= 0.05f || height <= 0.05f || totalBytes <= 0L) return

    val totalArea = width.toDouble() * height.toDouble()
    val scaleFactor = totalArea / totalBytes.toDouble()

    var curLeft = left.toDouble()
    var curTop = top.toDouble()
    var curWidth = width.toDouble()
    var curHeight = height.toDouble()

    var startIdx = 0
    val childCount = children.size

    while (startIdx < childCount && curWidth > 0.05 && curHeight > 0.05) {
        val isHorizontal = curWidth >= curHeight
        val sideLength = if (isHorizontal) curHeight else curWidth

        var rowBytes = children[startIdx].size
        var minBytes = rowBytes
        var maxBytes = rowBytes
        var bestWorst = worstAspectRatioD(maxBytes, minBytes, rowBytes, sideLength, scaleFactor)

        var endIdx = startIdx + 1
        while (endIdx < childCount) {
            val nextBytes = children[endIdx].size
            val nextRowBytes = rowBytes + nextBytes
            val nextMinBytes = if (nextBytes < minBytes) nextBytes else minBytes
            val nextMaxBytes = if (nextBytes > maxBytes) nextBytes else maxBytes

            val nextWorst = worstAspectRatioD(nextMaxBytes, nextMinBytes, nextRowBytes, sideLength, scaleFactor)
            if (nextWorst <= bestWorst) {
                rowBytes = nextRowBytes
                minBytes = nextMinBytes
                maxBytes = nextMaxBytes
                bestWorst = nextWorst
                endIdx++
            } else {
                break
            }
        }

        val rowArea = rowBytes.toDouble() * scaleFactor
        val rawThickness = rowArea / sideLength
        val rowThickness = minOf(if (isHorizontal) curWidth else curHeight, rawThickness)

        if (isHorizontal) {
            var itemTop = curTop
            val rowBottom = curTop + sideLength
            for (i in startIdx until endIdx) {
                val itemBytes = children[i].size
                val itemHeight = if (i == endIdx - 1) {
                    (rowBottom - itemTop).coerceAtLeast(0.0)
                } else {
                    sideLength * (itemBytes.toDouble() / rowBytes.toDouble())
                }
                onChildLayout(
                    children[i],
                    curLeft.toFloat(),
                    itemTop.toFloat(),
                    rowThickness.toFloat(),
                    itemHeight.toFloat()
                )
                itemTop += itemHeight
            }
            curLeft += rowThickness
            curWidth = (curWidth - rowThickness).coerceAtLeast(0.0)
        } else {
            var itemLeft = curLeft
            val rowRight = curLeft + sideLength
            for (i in startIdx until endIdx) {
                val itemBytes = children[i].size
                val itemWidth = if (i == endIdx - 1) {
                    (rowRight - itemLeft).coerceAtLeast(0.0)
                } else {
                    sideLength * (itemBytes.toDouble() / rowBytes.toDouble())
                }
                onChildLayout(
                    children[i],
                    itemLeft.toFloat(),
                    curTop.toFloat(),
                    itemWidth.toFloat(),
                    rowThickness.toFloat()
                )
                itemLeft += itemWidth
            }
            curTop += rowThickness
            curHeight = (curHeight - rowThickness).coerceAtLeast(0.0)
        }

        startIdx = endIdx
    }
}

private fun worstAspectRatioD(maxBytes: Long, minBytes: Long, rowBytes: Long, sideLength: Double, scaleFactor: Double): Double {
    if (rowBytes <= 0L || minBytes <= 0L || sideLength <= 0.0) return Double.MAX_VALUE
    val s2 = (rowBytes.toDouble() * scaleFactor) * rowBytes.toDouble()
    val w2 = sideLength * sideLength
    val r1 = (w2 * maxBytes.toDouble()) / s2
    val r2 = s2 / (w2 * minBytes.toDouble())
    return maxOf(r1, r2)
}
