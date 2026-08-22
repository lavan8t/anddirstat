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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
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

object TreemapBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(8)
    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bitmap: ImageBitmap) { cache.put(key, bitmap) }
    fun clear() { cache.evictAll() }
}

private fun renderTreemapToBitmap(
    tiles: List<TreemapTile>,
    width: Int,
    height: Int,
    isDark: Boolean,
    pureBlack: Boolean,
    context: Context
): ImageBitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val isAmoled = pureBlack && isDark
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    val bgColor = if (isAmoled) android.graphics.Color.BLACK else if (isDark) 0xFF08090E.toInt() else 0xFFF1F3F9.toInt()
    canvas.drawColor(bgColor)

    for (tile in tiles) {
        val sLeft = tile.left
        val sTop = tile.top
        val sW = maxOf(1f, tile.width)
        val sH = maxOf(1f, tile.height)

        if (isAmoled) {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = tile.baseColor.toArgb()
            paint.shader = null
            canvas.drawRect(sLeft, sTop, sLeft + sW, sTop + sH, paint)
        } else {
            // For small tiles (<8px), draw solid color with 0 shader allocations for 100x speedup
            if (sW < 8f || sH < 8f) {
                paint.style = android.graphics.Paint.Style.FILL
                paint.shader = null
                paint.color = tile.baseColor.toArgb()
                canvas.drawRect(sLeft, sTop, sLeft + sW, sTop + sH, paint)
            } else {
                val c1 = tile.gradientColors[0].toArgb()
                val c2 = tile.gradientColors[1].toArgb()
                val c3 = tile.gradientColors[2].toArgb()
                val shader = android.graphics.LinearGradient(
                    sLeft, sTop, sLeft + sW, sTop + sH,
                    intArrayOf(c1, c2, c3),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )
                paint.style = android.graphics.Paint.Style.FILL
                paint.shader = shader
                canvas.drawRect(sLeft, sTop, sLeft + sW, sTop + sH, paint)
            }
        }

        if (tile.pkgName != null && sW >= 24f && sH >= 24f) {
            val appIcon = AppIconCache.get(context, tile.pkgName)
            if (appIcon != null) {
                val iconSize = minOf(80f, minOf(sW, sH) * 0.70f).coerceAtLeast(16f)
                val iconX = sLeft + (sW - iconSize) / 2f
                val iconY = sTop + (sH - iconSize) / 2f
                val androidBmp = appIcon.asAndroidBitmap()
                val srcRect = android.graphics.Rect(0, 0, androidBmp.width, androidBmp.height)
                val dstRect = android.graphics.RectF(iconX, iconY, iconX + iconSize, iconY + iconSize)
                paint.shader = null
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawBitmap(androidBmp, srcRect, dstRect, paint)
            }
        }
    }

    return bitmap.asImageBitmap()
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
)

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
            val highlight = if (isDark) {
                Color(
                    red = (baseColor.red * 1.25f + 0.08f).coerceIn(0f, 1f),
                    green = (baseColor.green * 1.25f + 0.08f).coerceIn(0f, 1f),
                    blue = (baseColor.blue * 1.25f + 0.08f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            } else {
                Color(
                    red = (baseColor.red * 1.30f + 0.12f).coerceIn(0f, 1f),
                    green = (baseColor.green * 1.30f + 0.12f).coerceIn(0f, 1f),
                    blue = (baseColor.blue * 1.30f + 0.12f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            }
            val shadow = if (isDark) {
                Color(
                    red = (baseColor.red * 0.50f).coerceIn(0f, 1f),
                    green = (baseColor.green * 0.50f).coerceIn(0f, 1f),
                    blue = (baseColor.blue * 0.50f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            } else {
                Color(
                    red = (baseColor.red * 0.45f).coerceIn(0f, 1f),
                    green = (baseColor.green * 0.45f).coerceIn(0f, 1f),
                    blue = (baseColor.blue * 0.45f).coerceIn(0f, 1f),
                    alpha = 1f
                )
            }
            val gradientColors = listOf(highlight, baseColor, shadow)
            tiles.add(TreemapTile(node, currentPath, l, t, maxOf(1f, w), maxOf(1f, h), false, null, baseColor, gradientColors))
            return
        }

        val children = node.children
        val inApps = inAppsScope || node.name == "Apps & System Packages"
        val isApp = inApps && children?.any { it.name.startsWith("App Code") } == true

        if (children == null || children.isEmpty() || isApp) {
            val baseColor = getNodeColor(node, isDark)
            val highlight = if (isDark) {
                Color(
                    red = (baseColor.red * 1.30f + 0.10f).coerceIn(0f, 1f),
                    green = (baseColor.green * 1.30f + 0.10f).coerceIn(0f, 1f),
                    blue = (baseColor.blue * 1.30f + 0.10f).coerceIn(0f, 1f),
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
                    red = (baseColor.red * 0.45f).coerceIn(0f, 1f),
                    green = (baseColor.green * 0.45f).coerceIn(0f, 1f),
                    blue = (baseColor.blue * 0.45f).coerceIn(0f, 1f),
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

@Composable
fun TreemapCanvas(
    rootNode: CompactNode,
    rootPath: String,
    selectedNode: CompactNode?,
    selectedNodes: Set<CompactNode> = emptySet(),
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

    val cacheKey = "${System.identityHashCode(rootNode)}_${canvasSize.width}_${canvasSize.height}_$isDark"
    val precalculatedTiles = remember(cacheKey) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            computeTreemapTiles(rootNode, rootPath, canvasSize.width.toFloat(), canvasSize.height.toFloat(), isDark)
        } else {
            emptyList()
        }
    }

    val nodeToTileMap = remember(precalculatedTiles) {
        precalculatedTiles.associateBy { it.node }
    }

    LaunchedEffect(resetKey) {
        if (resetKey > 0) {
            scale = 1f
            offset = Offset.Zero
            onScaleChanged?.invoke(1f)
        }
    }

    val selectedTile = remember(selectedNode, nodeToTileMap) {
        if (selectedNode == null) null else nodeToTileMap[selectedNode]
    }

    val staticBitmap = remember(cacheKey, pureBlack, precalculatedTiles) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && precalculatedTiles.isNotEmpty()) {
            val bmpKey = "${cacheKey}_$pureBlack"
            val cachedBmp = TreemapBitmapCache.get(bmpKey)
            if (cachedBmp != null) {
                cachedBmp
            } else {
                val bmp = renderTreemapToBitmap(
                    tiles = precalculatedTiles,
                    width = canvasSize.width,
                    height = canvasSize.height,
                    isDark = isDark,
                    pureBlack = pureBlack,
                    context = context
                )
                TreemapBitmapCache.put(bmpKey, bmp)
                bmp
            }
        } else {
            null
        }
    }

    val canvasBg = if (pureBlack && isDark) Color.Black else if (isDark) Color(0xFF08090E) else Color(0xFFF1F3F9)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { clip = true }
            .background(canvasBg)
            .onSizeChanged { newSize ->
                if (newSize.width > 0 && newSize.height > 0 && (newSize.width != canvasSize.width || newSize.height != canvasSize.height)) {
                    canvasSize = newSize
                }
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
            .pointerInput(precalculatedTiles) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val upOrCancel = waitForUpOrCancellation()
                    if (upOrCancel != null && !upOrCancel.isConsumed) {
                        val curScale = currentScaleState.value
                        val curOffset = currentOffsetState.value
                        val touchX = (upOrCancel.position.x - curOffset.x) / curScale
                        val touchY = (upOrCancel.position.y - curOffset.y) / curScale

                        val hit = precalculatedTiles.findLast {
                            touchX >= it.left && touchX <= it.left + it.width &&
                            touchY >= it.top && touchY <= it.top + it.height
                        }
                        if (hit != null) {
                            triggerCrispHaptic(context)
                            onNodeSelected(hit.node, hit.path)
                        }
                    }
                }
            }
    ) {
        val currentScale = scale
        val currentOffset = offset
        val isAmoled = pureBlack && isDark
        val isFrozenStatic = currentScale <= 1.001f && currentOffset == Offset.Zero && staticBitmap != null

        if (isFrozenStatic) {
            // 0.00ms Blit: Draw cached ImageBitmap directly into GPU in one single operation
            drawImage(staticBitmap)

            // Draw marked selection highlights over cached snapshot
            for (node in selectedNodes) {
                val tile = nodeToTileMap[node] ?: continue
                val sLeft = tile.left
                val sTop = tile.top
                val drawW = maxOf(1f, tile.width)
                val drawH = maxOf(1f, tile.height)

                drawRect(
                    color = Color(0x6600E676),
                    topLeft = Offset(sLeft, sTop),
                    size = Size(drawW, drawH)
                )
                drawRect(
                    color = Color(0xFF00E676),
                    topLeft = Offset(sLeft, sTop),
                    size = Size(drawW, drawH),
                    style = Stroke(width = 2.5f)
                )
            }
        } else {
            val viewW = size.width
            val viewH = size.height
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

                val drawW = maxOf(1f, sW)
                val drawH = maxOf(1f, sH)

                if (isAmoled) {
                    drawRect(
                        color = tile.baseColor,
                        topLeft = Offset(sLeft, sTop),
                        size = Size(drawW, drawH),
                        style = Stroke(width = if (currentScale > 1.5f) 2.5f else 1.5f)
                    )
                } else if (drawW < 8f || drawH < 8f) {
                    drawRect(
                        color = tile.baseColor,
                        topLeft = Offset(sLeft, sTop),
                        size = Size(drawW, drawH)
                    )
                } else {
                    val brush = Brush.linearGradient(
                        colors = tile.gradientColors,
                        start = Offset(sLeft, sTop),
                        end = Offset(sLeft + drawW, sTop + drawH)
                    )
                    drawRect(
                        brush = brush,
                        topLeft = Offset(sLeft, sTop),
                        size = Size(drawW, drawH)
                    )
                }

                val isMarked = selectedNodes.contains(tile.node)
                if (isMarked) {
                    drawRect(
                        color = Color(0x6600E676),
                        topLeft = Offset(sLeft, sTop),
                        size = Size(drawW, drawH)
                    )
                    drawRect(
                        color = Color(0xFF00E676),
                        topLeft = Offset(sLeft, sTop),
                        size = Size(drawW, drawH),
                        style = Stroke(width = if (currentScale > 1.5f) 3.5f else 2.5f)
                    )
                }

                if (tile.pkgName != null && sW >= 24f && sH >= 24f) {
                    val bmp = AppIconCache.get(context, tile.pkgName)
                    if (bmp != null) {
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
    if (name == "[Free Space]") return if (isDark) Color(0xFF1E293B) else Color(0xFF64748B)
    if (name == "[System & OS]") return if (isDark) Color(0xFF141923) else Color(0xFF475569)
    if (name == "[Recycle Bin]" || name.startsWith(".trashed")) return if (isDark) Color(0xFF701830) else Color(0xFFE11D48)
    if (name == "Cache" || name == "App Cache") return if (isDark) Color(0xFF8A3E00) else Color(0xFFF59E0B)
    if (name == "Data" || name == "App Data") return if (isDark) Color(0xFF2E3F59) else Color(0xFF64748B)

    val children = node.children
    if (children != null && children.any { it.name.startsWith("App Code") }) {
        return if (isDark) Color(0xFF123D78) else Color(0xFF1976D2)
    }
    if (node.isDirectory) return if (isDark) Color(0xFF151922) else Color(0xFF2C3240)

    val ext = if (name.startsWith("App Code") || name.startsWith("APK (") || name.endsWith(".apk", ignoreCase = true)) {
        "apk"
    } else {
        val dotIdx = name.lastIndexOf('.')
        if (dotIdx >= 0 && dotIdx < name.length - 1) name.substring(dotIdx + 1).lowercase() else ""
    }

    if (ext.isEmpty() || ext == name.lowercase()) {
        return if (isDark) Color(0xFF37474F) else Color(0xFF78909C)
    }

    val cache = if (isDark) ExtensionColorCacheDark else ExtensionColorCacheLight
    val cached = cache.get(ext)
    if (cached != null) return cached

    val computed = when (ext) {
        "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v" ->
            if (isDark) Color(0xFF154378) else Color(0xFF0055FF)
        "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid" ->
            if (isDark) Color(0xFF5E227F) else Color(0xFFAA00FF)
        "jpg", "jpeg", "png", "webp", "heic", "raw", "svg", "gif", "bmp", "ico" ->
            if (isDark) Color(0xFF8F3E00) else Color(0xFFFF8800)
        "apk", "apks", "xapk", "apkm", "obb", "aab" ->
            if (isDark) Color(0xFF801524) else Color(0xFFFF0055)
        "pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub" ->
            if (isDark) Color(0xFF165C22) else Color(0xFF00CC44)
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz" ->
            if (isDark) Color(0xFF005E6B) else Color(0xFF00CCCC)
        "so", "bin", "dex", "jar", "class", "exe", "dll" ->
            if (isDark) Color(0xFF6B1212) else Color(0xFFCC0000)
        "html", "xml", "json", "js", "css", "ts", "kt", "java", "c", "cpp", "py" ->
            if (isDark) Color(0xFF8F6200) else Color(0xFFFFCC00)
        else -> {
            val hash = Math.abs(ext.hashCode())
            val hue = (hash * 137.507764f) % 360f
            Color.hsl(hue = hue, saturation = 0.65f, lightness = if (isDark) 0.28f else 0.50f)
        }
    }
    cache.put(ext, computed)
    return computed
}
