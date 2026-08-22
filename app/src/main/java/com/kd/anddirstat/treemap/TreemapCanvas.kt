package com.kd.anddirstat.treemap

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.LruCache
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
    val highlightColor: Color,
    val shadowColor: Color
)

fun computeTreemapTiles(
    rootNode: CompactNode,
    rootPath: String,
    width: Float,
    height: Float
): List<TreemapTile> {
    if (width <= 0f || height <= 0f || rootNode.size <= 0L) return emptyList()

    val tiles = ArrayList<TreemapTile>(512)

    fun buildTiles(node: CompactNode, currentPath: String, l: Float, t: Float, w: Float, h: Float, inAppsScope: Boolean) {
        if (w <= 0.5f || h <= 0.5f) return

        val children = node.children
        val inApps = inAppsScope || node.name == "Apps & System Packages"
        val isApp = inApps && children?.any { it.name.startsWith("App Code") } == true

        if (children == null || children.isEmpty() || isApp) {
            val baseColor = getNodeColor(node)
            val highlight = Color(
                red = (baseColor.red * 1.35f + 0.15f).coerceIn(0f, 1f),
                green = (baseColor.green * 1.35f + 0.15f).coerceIn(0f, 1f),
                blue = (baseColor.blue * 1.35f + 0.15f).coerceIn(0f, 1f),
                alpha = 1f
            )
            val shadow = Color(
                red = (baseColor.red * 0.40f).coerceIn(0f, 1f),
                green = (baseColor.green * 0.40f).coerceIn(0f, 1f),
                blue = (baseColor.blue * 0.40f).coerceIn(0f, 1f),
                alpha = 1f
            )
            val pkg = if (isApp) FileUtils.extractPackageName(node) else null
            tiles.add(TreemapTile(node, currentPath, l, t, w, h, isApp, pkg, baseColor, highlight, shadow))
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

fun findTileAt(tiles: List<TreemapTile>, targetX: Float, targetY: Float): TreemapTile? {
    for (i in tiles.indices) {
        val tile = tiles[i]
        if (targetX >= tile.left && targetX <= tile.left + tile.width &&
            targetY >= tile.top && targetY <= tile.top + tile.height) {
            return tile
        }
    }
    return null
}


@Composable
fun TreemapCanvas(
    rootNode: CompactNode,
    rootPath: String,
    selectedNode: CompactNode?,
    resetKey: Int = 0,
    onScaleChanged: ((Float) -> Unit)? = null,
    onNodeSelected: (CompactNode, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val precalculatedTiles = remember(rootNode, rootPath, canvasSize.width, canvasSize.height) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            computeTreemapTiles(rootNode, rootPath, canvasSize.width.toFloat(), canvasSize.height.toFloat())
        } else {
            emptyList()
        }
    }

    LaunchedEffect(resetKey) {
        if (resetKey > 0) {
            scale = 1f
            offset = Offset.Zero
            onScaleChanged?.invoke(1f)
        }
    }

    val selectedTile = remember(selectedNode, precalculatedTiles) {
        if (selectedNode == null) null
        else precalculatedTiles.firstOrNull { it.node === selectedNode }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBgColor)
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
            .pointerInput(precalculatedTiles, scale, offset) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val upOrCancel = waitForUpOrCancellation()
                    if (upOrCancel != null && !upOrCancel.isConsumed) {
                        val touchX = (upOrCancel.position.x - offset.x) / scale
                        val touchY = (upOrCancel.position.y - offset.y) / scale

                        val hit = findTileAt(precalculatedTiles, touchX, touchY)
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

        val numTiles = precalculatedTiles.size
        for (i in 0 until numTiles) {
            val tile = precalculatedTiles[i]

            val sLeft = tile.left * currentScale + currentOffset.x
            val sTop = tile.top * currentScale + currentOffset.y
            val sW = tile.width * currentScale
            val sH = tile.height * currentScale

            if (sLeft + sW < 0f || sLeft > viewW || sTop + sH < 0f || sTop > viewH) {
                continue
            }

            val cX = sLeft + sW * 0.35f
            val cY = sTop + sH * 0.35f
            val radius = maxOf(sW, sH) * 0.85f

            val brush = Brush.radialGradient(
                colors = listOf(tile.highlightColor, tile.baseColor, tile.shadowColor),
                center = Offset(cX, cY),
                radius = radius
            )

            drawRect(
                brush = brush,
                topLeft = Offset(sLeft, sTop),
                size = Size(sW, sH)
            )

            if (tile.pkgName != null && sW >= 20f && sH >= 20f) {
                val bmp = AppIconCache.get(context, tile.pkgName)
                if (bmp != null) {
                    val iconSize = minOf(48f, minOf(sW, sH) * 0.50f).coerceAtLeast(16f)
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

        // Selected tile crisp white border overlay
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
                style = Stroke(width = 4.5f)
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

private val ExtensionColorCache = LruCache<String, Color>(128)

fun getNodeColor(node: CompactNode): Color {
    val name = node.name
    if (name == "[Free Space]") return Color(0xFF475569)
    if (name == "[System & OS]") return Color(0xFF334155)
    if (name == "Cache" || name == "App Cache") return Color(0xFFF59E0B)
    if (name == "Data" || name == "App Data") return Color(0xFF64748B)

    val children = node.children
    if (children != null && children.any { it.name.startsWith("App Code") }) {
        return Color(0xFF0077CC)
    }
    if (node.isDirectory) return Color(0xFF1E222B)

    val ext = if (name.startsWith("App Code") || name.startsWith("APK (") || name.endsWith(".apk", ignoreCase = true)) {
        "apk"
    } else {
        val dotIdx = name.lastIndexOf('.')
        if (dotIdx >= 0 && dotIdx < name.length - 1) name.substring(dotIdx + 1).lowercase() else ""
    }

    if (ext.isEmpty() || ext == name.lowercase()) {
        return Color(0xFF78909C)
    }

    val cached = ExtensionColorCache.get(ext)
    if (cached != null) return cached

    val computed = when (ext) {
        "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v" -> Color(0xFF0055FF)
        "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid" -> Color(0xFFAA00FF)
        "jpg", "jpeg", "png", "webp", "heic", "raw", "svg", "gif", "bmp", "ico" -> Color(0xFFFF8800)
        "apk", "apks", "xapk", "apkm", "obb", "aab" -> Color(0xFFFF0055)
        "pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub" -> Color(0xFF00CC44)
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz" -> Color(0xFF00CCCC)
        "so", "bin", "dex", "jar", "class", "exe", "dll" -> Color(0xFFCC0000)
        "html", "xml", "json", "js", "css", "ts", "kt", "java", "c", "cpp", "py" -> Color(0xFFFFCC00)
        else -> {
            val hash = Math.abs(ext.hashCode())
            val hue = (hash * 137.507764f) % 360f
            Color.hsl(hue = hue, saturation = 0.75f, lightness = 0.50f)
        }
    }
    ExtensionColorCache.put(ext, computed)
    return computed
}
