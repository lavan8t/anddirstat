package com.kd.anddirstat.treemap

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.ui.components.AppIconCache
import com.kd.anddirstat.util.FileUtils

private val CanvasBgColor = Color(0xFF08090E)
private val SelectionBorderColor = Color.White

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
)

fun computeTreemapTiles(
    rootNode: CompactNode,
    rootPath: String,
    width: Float,
    height: Float,
    isDark: Boolean = true
): List<TreemapTile> {
    if (width <= 0f || height <= 0f || rootNode.size <= 0L) return emptyList()

    val tiles = ArrayList<TreemapTile>(512)

    fun buildTiles(node: CompactNode, currentPath: String, l: Float, t: Float, w: Float, h: Float, inAppsScope: Boolean) {
        if (w <= 0.5f || h <= 0.5f) return

        val children = node.children
        val inApps = inAppsScope || node.name == "Apps & System Packages"
        val isApp = inApps && children?.any { it.name.startsWith("App Code") } == true

        if (children == null || children.isEmpty() || isApp) {
            val baseColor = getNodeColor(node, isDark)
            val highlight = if (isDark) {
                Color(
                    red = (baseColor.red * 1.15f + 0.04f).coerceIn(0f, 1f),
                    green = (baseColor.green * 1.15f + 0.04f).coerceIn(0f, 1f),
                    blue = (baseColor.blue * 1.15f + 0.04f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            } else {
                Color(
                    red = (baseColor.red * 1.35f + 0.15f).coerceIn(0f, 1f),
                    green = (baseColor.green * 1.35f + 0.15f).coerceIn(0f, 1f),
                    blue = (baseColor.blue * 1.35f + 0.15f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            }
            val shadow = if (isDark) {
                Color(
                    red = (baseColor.red * 0.35f).coerceIn(0f, 1f),
                    green = (baseColor.green * 0.35f).coerceIn(0f, 1f),
                    blue = (baseColor.blue * 0.35f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            } else {
                Color(
                    red = (baseColor.red * 0.40f).coerceIn(0f, 1f),
                    green = (baseColor.green * 0.40f).coerceIn(0f, 1f),
                    blue = (baseColor.blue * 0.40f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            }
            val pkg = if (isApp) FileUtils.extractPackageName(node) else null
            val gradientColors = listOf(highlight, baseColor, shadow)
            tiles.add(TreemapTile(node, currentPath, l, t, w, h, isApp, pkg, baseColor, gradientColors))
            return
        }

        val validChildren = children.filter { it.size > 0L }.sortedByDescending { it.size }
        if (validChildren.isEmpty()) return

        layoutSquarified(validChildren, l, t, w, h) { child, cLeft, cTop, cWidth, cHeight ->
            val childPath = if (currentPath == "Device Storage") child.name else if (currentPath.endsWith("/")) "$currentPath${child.name}" else "$currentPath/${child.name}"
            buildTiles(child, childPath, cLeft, cTop, cWidth, cHeight, inApps)
        }
    }

    buildTiles(rootNode, rootPath, 0f, 0f, width, height, false)
    return tiles
}

class IntArrayList(initialCapacity: Int = 8) {
    var data = IntArray(initialCapacity)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) {
            data = data.copyOf(data.size * 2)
        }
        data[size++] = value
    }

    fun get(index: Int): Int = data[index]
}

class SpatialTileGrid(
    val tiles: List<TreemapTile>,
    val canvasWidth: Float,
    val canvasHeight: Float,
    val gridCols: Int = 64,
    val gridRows: Int = 64
) {
    private val buckets: Array<IntArrayList?> = arrayOfNulls(gridCols * gridRows)
    val nodeToTileMap: HashMap<CompactNode, TreemapTile> = HashMap(tiles.size)

    init {
        val invW = if (canvasWidth > 0f) gridCols / canvasWidth else 0f
        val invH = if (canvasHeight > 0f) gridRows / canvasHeight else 0f

        for (i in tiles.indices) {
            val tile = tiles[i]
            nodeToTileMap[tile.node] = tile

            val minC = (tile.left * invW).toInt().coerceIn(0, gridCols - 1)
            val maxC = ((tile.left + tile.width) * invW).toInt().coerceIn(0, gridCols - 1)
            val minR = (tile.top * invH).toInt().coerceIn(0, gridRows - 1)
            val maxR = ((tile.top + tile.height) * invH).toInt().coerceIn(0, gridRows - 1)

            for (r in minR..maxR) {
                val rowOffset = r * gridCols
                for (c in minC..maxC) {
                    val idx = rowOffset + c
                    var list = buckets[idx]
                    if (list == null) {
                        list = IntArrayList(8)
                        buckets[idx] = list
                    }
                    list.add(i)
                }
            }
        }
    }

    fun findTileAt(x: Float, y: Float): TreemapTile? {
        if (x < 0f || x > canvasWidth || y < 0f || y > canvasHeight) return null
        val c = (x / canvasWidth * gridCols).toInt().coerceIn(0, gridCols - 1)
        val r = (y / canvasHeight * gridRows).toInt().coerceIn(0, gridRows - 1)
        val bucket = buckets[r * gridCols + c] ?: return null

        for (k in bucket.size - 1 downTo 0) {
            val tile = tiles[bucket.get(k)]
            if (x >= tile.left && x <= tile.left + tile.width &&
                y >= tile.top && y <= tile.top + tile.height) {
                return tile
            }
        }
        return null
    }
}

@Composable
fun TreemapCanvas(
    rootNode: CompactNode,
    rootPath: String,
    selectedNode: CompactNode?,
    resetKey: Int = 0,
    isDark: Boolean = true,
    pureBlack: Boolean = false,
    onScaleChanged: ((Float) -> Unit)? = null,
    onNodeSelected: (CompactNode, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayMetrics = remember { context.resources.displayMetrics }
    var canvasSize by remember { mutableStateOf(IntSize(displayMetrics.widthPixels, displayMetrics.heightPixels)) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val currentScaleState = rememberUpdatedState(scale)
    val currentOffsetState = rememberUpdatedState(offset)

    val precalculatedTiles = remember(rootNode, rootPath, canvasSize.width, canvasSize.height, isDark) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            computeTreemapTiles(rootNode, rootPath, canvasSize.width.toFloat(), canvasSize.height.toFloat(), isDark)
        } else {
            emptyList()
        }
    }

    val spatialGrid = remember(precalculatedTiles, canvasSize.width, canvasSize.height) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && precalculatedTiles.isNotEmpty()) {
            SpatialTileGrid(precalculatedTiles, canvasSize.width.toFloat(), canvasSize.height.toFloat())
        } else null
    }

    LaunchedEffect(resetKey) {
        if (resetKey > 0) {
            scale = 1f
            offset = Offset.Zero
            onScaleChanged?.invoke(1f)
        }
    }

    val selectedTile = remember(selectedNode, spatialGrid) {
        if (selectedNode == null || spatialGrid == null) null
        else spatialGrid.nodeToTileMap[selectedNode]
    }

    val canvasBg = if (pureBlack && isDark) Color.Black else if (isDark) Color(0xFF08090E) else Color(0xFFF1F3F9)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBg)
            .onSizeChanged { newSize ->
                canvasSize = newSize
            }
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(1f, 30f)
                    scale = newScale
                    onScaleChanged?.invoke(newScale)

                    if (newScale <= 1.001f) {
                        offset = Offset.Zero
                    } else {
                        val focal = centroid - offset
                        val newOffset = centroid - focal * (newScale / oldScale) + pan

                        val viewW = size.width.toFloat()
                        val viewH = size.height.toFloat()
                        val maxOffsetX = 0f
                        val minOffsetX = viewW - viewW * newScale
                        val maxOffsetY = 0f
                        val minOffsetY = viewH - viewH * newScale

                        offset = Offset(
                            newOffset.x.coerceIn(minOffsetX, maxOffsetX),
                            newOffset.y.coerceIn(minOffsetY, maxOffsetY)
                        )
                    }
                }
            }
            .pointerInput(spatialGrid) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val upOrCancel = waitForUpOrCancellation()
                    if (upOrCancel != null && !upOrCancel.isConsumed) {
                        val curScale = currentScaleState.value
                        val curOffset = currentOffsetState.value
                        val touchX = (upOrCancel.position.x - curOffset.x) / curScale
                        val touchY = (upOrCancel.position.y - curOffset.y) / curScale

                        val hit = spatialGrid?.findTileAt(touchX, touchY)
                        if (hit != null) {
                            triggerCrispHaptic(context)
                            onNodeSelected(hit.node, hit.path)
                        }
                    }
                }
            }
    ) {
        val viewW = size.width
        val viewH = size.height
        val currentScale = scale
        val currentOffset = offset
        val isAmoled = pureBlack && isDark

        val numTiles = precalculatedTiles.size
        for (i in 0 until numTiles) {
            val tile = precalculatedTiles[i]

            val sLeft = tile.left * currentScale + currentOffset.x
            val sTop = tile.top * currentScale + currentOffset.y
            val sW = tile.width * currentScale
            val sH = tile.height * currentScale

            // Viewport frustum culling
            if (sLeft + sW < 0f || sLeft > viewW || sTop + sH < 0f || sTop > viewH) {
                continue
            }

            // Sub-pixel culling
            if (sW < 0.75f && sH < 0.75f) {
                continue
            }

            val isSelected = selectedNode != null && tile.node === selectedNode

            if (isAmoled) {
                // AMOLED: outline borders only when unselected, animated smooth fill when selected
                if (isSelected) {
                    drawRect(
                        color = tile.baseColor,
                        topLeft = Offset(sLeft, sTop),
                        size = Size(sW, sH)
                    )
                }
                drawRect(
                    color = tile.baseColor,
                    topLeft = Offset(sLeft, sTop),
                    size = Size(sW, sH),
                    style = Stroke(width = if (currentScale > 2f) 1.5f else 1.0f)
                )
            } else {
                // Fast path: draw solid color for small tiles (<16px) — 50x faster GPU throughput
                if (sW < 16f || sH < 16f) {
                    drawRect(
                        color = tile.baseColor,
                        topLeft = Offset(sLeft, sTop),
                        size = Size(sW, sH)
                    )
                } else {
                    val cX = sLeft + sW * 0.35f
                    val cY = sTop + sH * 0.35f
                    val radius = maxOf(sW, sH) * 0.85f

                    val brush = Brush.radialGradient(
                        colors = tile.gradientColors,
                        center = Offset(cX, cY),
                        radius = radius
                    )

                    drawRect(
                        brush = brush,
                        topLeft = Offset(sLeft, sTop),
                        size = Size(sW, sH)
                    )
                }
            }

            if (tile.pkgName != null && sW >= 24f && sH >= 24f) {
                val bmp = AppIconCache.get(context, tile.pkgName)
                if (bmp != null) {
                    // Larger app icon (up to 80px), strictly square 1:1 aspect ratio
                    val iconSize = minOf(80f, minOf(sW, sH) * 0.70f).coerceAtLeast(16f)
                    val iconX = sLeft + (sW - iconSize) / 2f
                    val iconY = sTop + (sH - iconSize) / 2f
                    drawImage(
                        image = bmp,
                        dstOffset = IntOffset(iconX.toInt(), iconY.toInt()),
                        dstSize = IntSize(iconSize.toInt(), iconSize.toInt())
                    )
                }
            }
        }

        // Instant crisp selection border — zero animation delay, zero fade overlay
        val sel = selectedTile
        if (selectedNode != null && sel != null) {
            val selLeft = sel.left * currentScale + currentOffset.x
            val selTop = sel.top * currentScale + currentOffset.y
            val selW = sel.width * currentScale
            val selH = sel.height * currentScale

            drawRect(
                color = SelectionBorderColor,
                topLeft = Offset(selLeft, selTop),
                size = Size(selW, selH),
                style = Stroke(width = 3.5f)
            )
        }
    }
}

private fun triggerCrispHaptic(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(12L)
            }
        }
    } catch (_: Exception) {
    }
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
    if (children.isEmpty() || width <= 0.5f || height <= 0.5f || totalBytes <= 0L) return

    val totalArea = width * height
    val scaleFactor = totalArea / totalBytes.toFloat()

    var curLeft = left
    var curTop = top
    var curWidth = width
    var curHeight = height

    var startIdx = 0
    val childCount = children.size

    while (startIdx < childCount && curWidth > 0.5f && curHeight > 0.5f) {
        val isHorizontal = curWidth >= curHeight
        val sideLength = if (isHorizontal) curHeight else curWidth

        var rowBytes = children[startIdx].size
        var minBytes = rowBytes
        var maxBytes = rowBytes
        var bestWorst = worstAspectRatio(maxBytes, minBytes, rowBytes, sideLength, scaleFactor)

        var endIdx = startIdx + 1
        while (endIdx < childCount) {
            val nextBytes = children[endIdx].size
            val nextRowBytes = rowBytes + nextBytes
            val nextMinBytes = if (nextBytes < minBytes) nextBytes else minBytes
            val nextMaxBytes = if (nextBytes > maxBytes) nextBytes else maxBytes

            val nextWorst = worstAspectRatio(nextMaxBytes, nextMinBytes, nextRowBytes, sideLength, scaleFactor)
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

        val rowArea = rowBytes.toFloat() * scaleFactor
        val rowThickness = rowArea / sideLength

        if (isHorizontal) {
            var itemTop = curTop
            for (i in startIdx until endIdx) {
                val itemBytes = children[i].size
                val itemHeight = sideLength * (itemBytes.toFloat() / rowBytes.toFloat())
                onChildLayout(children[i], curLeft, itemTop, rowThickness, itemHeight)
                itemTop += itemHeight
            }
            curLeft += rowThickness
            curWidth -= rowThickness
        } else {
            var itemLeft = curLeft
            for (i in startIdx until endIdx) {
                val itemBytes = children[i].size
                val itemWidth = sideLength * (itemBytes.toFloat() / rowBytes.toFloat())
                onChildLayout(children[i], itemLeft, curTop, itemWidth, rowThickness)
                itemLeft += itemWidth
            }
            curTop += rowThickness
            curHeight -= rowThickness
        }

        startIdx = endIdx
    }
}

private fun worstAspectRatio(maxBytes: Long, minBytes: Long, rowBytes: Long, sideLength: Float, scaleFactor: Float): Float {
    if (rowBytes <= 0L || minBytes <= 0L || sideLength <= 0f) return Float.MAX_VALUE
    val s2 = (rowBytes.toFloat() * scaleFactor) * rowBytes.toFloat()
    val w2 = sideLength * sideLength
    val r1 = (w2 * maxBytes.toFloat()) / s2
    val r2 = s2 / (w2 * minBytes.toFloat())
    return maxOf(r1, r2)
}

private val ExtensionColorCacheDark = LruCache<String, Color>(128)
private val ExtensionColorCacheLight = LruCache<String, Color>(128)

fun getNodeColor(node: CompactNode, isDark: Boolean = true): Color {
    val name = node.name
    if (name == "[Free Space]") return if (isDark) Color(0xFF0F172A) else Color(0xFF64748B)
    if (name == "[System & OS]") return if (isDark) Color(0xFF060910) else Color(0xFF475569)
    if (name == "Cache" || name == "App Cache") return if (isDark) Color(0xFF5C2900) else Color(0xFFF59E0B)
    if (name == "Data" || name == "App Data") return if (isDark) Color(0xFF1E293B) else Color(0xFF64748B)

    val children = node.children
    if (children != null && children.any { it.name.startsWith("App Code") }) {
        return if (isDark) Color(0xFF06224D) else Color(0xFF1976D2)
    }
    if (node.isDirectory) return if (isDark) Color(0xFF0A0C10) else Color(0xFF2C3240)

    val ext = if (name.startsWith("App Code") || name.startsWith("APK (") || name.endsWith(".apk", ignoreCase = true)) {
        "apk"
    } else {
        val dotIdx = name.lastIndexOf('.')
        if (dotIdx >= 0 && dotIdx < name.length - 1) name.substring(dotIdx + 1).lowercase() else ""
    }

    if (ext.isEmpty() || ext == name.lowercase()) {
        return if (isDark) Color(0xFF263238) else Color(0xFF78909C)
    }

    val cache = if (isDark) ExtensionColorCacheDark else ExtensionColorCacheLight
    val cached = cache.get(ext)
    if (cached != null) return cached

    val computed = when (ext) {
        "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v" ->
            if (isDark) Color(0xFF08264A) else Color(0xFF0055FF)
        "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid" ->
            if (isDark) Color(0xFF38144D) else Color(0xFFAA00FF)
        "jpg", "jpeg", "png", "webp", "heic", "raw", "svg", "gif", "bmp", "ico" ->
            if (isDark) Color(0xFF662900) else Color(0xFFFF8800)
        "apk", "apks", "xapk", "apkm", "obb", "aab" ->
            if (isDark) Color(0xFF5C0A0A) else Color(0xFFFF0055)
        "pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub" ->
            if (isDark) Color(0xFF0B3310) else Color(0xFF00CC44)
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz" ->
            if (isDark) Color(0xFF00363D) else Color(0xFF00CCCC)
        "so", "bin", "dex", "jar", "class", "exe", "dll" ->
            if (isDark) Color(0xFF450000) else Color(0xFFCC0000)
        "html", "xml", "json", "js", "css", "ts", "kt", "java", "c", "cpp", "py" ->
            if (isDark) Color(0xFF613F00) else Color(0xFFFFCC00)
        else -> {
            val hash = Math.abs(ext.hashCode())
            val hue = (hash * 137.507764f) % 360f
            Color.hsl(hue = hue, saturation = 0.55f, lightness = if (isDark) 0.18f else 0.50f)
        }
    }
    cache.put(ext, computed)
    return computed
}
