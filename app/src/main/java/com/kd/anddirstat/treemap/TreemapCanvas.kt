package com.kd.anddirstat.treemap

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.ui.components.AppIconCache
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.FileUtils
import kotlin.math.abs

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

        val appIcon = if (tile.pkgName != null && sW >= 24f && sH >= 24f) {
            AppIconCache.get(context, tile.pkgName)
        } else if (tile.pkgName != null) {
            AppIconCache.get(context, tile.pkgName)
            null
        } else null

        val tileBaseColor = if (tile.pkgName != null) {
            AppIconCache.getDominantColor(tile.pkgName) ?: tile.baseColor
        } else {
            tile.baseColor
        }

        if (isAmoled) {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = tileBaseColor.toArgb()
            paint.shader = null
            canvas.drawRect(sLeft, sTop, sLeft + sW, sTop + sH, paint)
        } else {
            if (sW < 8f || sH < 8f) {
                paint.style = android.graphics.Paint.Style.FILL
                paint.shader = null
                paint.color = tileBaseColor.toArgb()
                canvas.drawRect(sLeft, sTop, sLeft + sW, sTop + sH, paint)
            } else {
                val cHighlight = if (isDark) {
                    Color(
                        red = (tileBaseColor.red * 1.30f + 0.10f).coerceIn(0f, 1f),
                        green = (tileBaseColor.green * 1.30f + 0.10f).coerceIn(0f, 1f),
                        blue = (tileBaseColor.blue * 1.30f + 0.10f).coerceIn(0f, 1f),
                        alpha = 1f
                    ).toArgb()
                } else {
                    Color(
                        red = (tileBaseColor.red * 1.35f + 0.15f).coerceIn(0f, 1f),
                        green = (tileBaseColor.green * 1.35f + 0.15f).coerceIn(0f, 1f),
                        blue = (tileBaseColor.blue * 1.35f + 0.15f).coerceIn(0f, 1f),
                        alpha = 1f
                    ).toArgb()
                }
                val cBase = tileBaseColor.toArgb()
                val cShadow = if (isDark) {
                    Color(
                        red = (tileBaseColor.red * 0.45f).coerceIn(0f, 1f),
                        green = (tileBaseColor.green * 0.45f).coerceIn(0f, 1f),
                        blue = (tileBaseColor.blue * 0.45f).coerceIn(0f, 1f),
                        alpha = 1f
                    ).toArgb()
                } else {
                    Color(
                        red = (tileBaseColor.red * 0.40f).coerceIn(0f, 1f),
                        green = (tileBaseColor.green * 0.40f).coerceIn(0f, 1f),
                        blue = (tileBaseColor.blue * 0.40f).coerceIn(0f, 1f),
                        alpha = 1f
                    ).toArgb()
                }

                // WinDirStat / QDirStat Cushion Treemap Lighting effect:
                // Radial gradient centered top-left (35% X, 30% Y) with simulated directional light
                val centerX = sLeft + sW * 0.35f
                val centerY = sTop + sH * 0.30f
                val radius = maxOf(sW, sH) * 1.05f
                val shader = android.graphics.RadialGradient(
                    centerX, centerY, radius,
                    intArrayOf(cHighlight, cBase, cShadow),
                    floatArrayOf(0f, 0.50f, 1.0f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                paint.style = android.graphics.Paint.Style.FILL
                paint.shader = shader
                canvas.drawRect(sLeft, sTop, sLeft + sW, sTop + sH, paint)
            }
        }

        if (appIcon != null && sW >= 24f && sH >= 24f) {
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
            tiles.add(TreemapTile(node, currentPath, l, t, w, h, false, null, baseColor, gradientColors))
            return
        }

        val children = node.children
        val inApps = inAppsScope || node.name == "Apps & System Packages"
        val isApp = inApps && children?.any { it.name.startsWith("App Code") } == true

        if (children == null || children.isEmpty() || isApp) {
            val pkg = if (isApp) FileUtils.extractPackageName(node) else null
            val appDominantColor = if (pkg != null) AppIconCache.getDominantColor(pkg) else null
            val baseColor = appDominantColor ?: getNodeColor(node, isDark)
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
    val density = LocalDensity.current

    var glidingTile by remember { mutableStateOf<TreemapTile?>(null) }
    var glidingTouchPos by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = true }
                .background(canvasBg)
                .onSizeChanged { newSize ->
                    if (newSize.width > 0 && newSize.height > 0 && (newSize.width != canvasSize.width || newSize.height != canvasSize.height)) {
                        canvasSize = newSize
                    }
                }
                .pointerInput(precalculatedTiles) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val downPos = down.position

                        fun getTileAt(touchOffset: Offset): TreemapTile? {
                            val curScale = currentScaleState.value
                            val curOff = currentOffsetState.value
                            val touchX = (touchOffset.x - curOff.x) / curScale
                            val touchY = (touchOffset.y - curOff.y) / curScale
                            return precalculatedTiles.findLast {
                                touchX >= it.left && touchX <= it.left + it.width &&
                                touchY >= it.top && touchY <= it.top + it.height
                            }
                        }

                        val initialTile = getTileAt(downPos)
                        var tooltipActive = false
                        var holdStartTime = 0L
                        var hadMultiTouch = false
                        var hasMovedOutOfInitialTile = false
                        var bottomSheetOpened = false
                        val longPressActivationTimeout = 220L
                        val continuousHoldTimeout = 800L

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }
                            val totalPointers = event.changes.size
                            val pressedPointers = pressedChanges.size

                            if (pressedPointers >= 2) {
                                // Strictly Two-Finger Zoom and Pan — One-finger panning is disabled
                                hadMultiTouch = true
                                tooltipActive = false
                                if (glidingTile != null) {
                                    glidingTile = null
                                    glidingTouchPos = null
                                }

                                val p0 = pressedChanges[0]
                                val p1 = pressedChanges[1]

                                val currCentroid = (p0.position + p1.position) / 2f
                                val prevCentroid = (p0.previousPosition + p1.previousPosition) / 2f
                                val panDelta = currCentroid - prevCentroid

                                val currDist = (p0.position - p1.position).getDistance()
                                val prevDist = (p0.previousPosition - p1.previousPosition).getDistance()
                                val zoomRatio = if (prevDist > 1f) currDist / prevDist else 1f

                                val oldScale = scale
                                val newScale = (oldScale * zoomRatio).coerceIn(1f, 30f)
                                scale = newScale
                                onScaleChanged?.invoke(newScale)

                                if (newScale <= 1.001f) {
                                    offset = Offset.Zero
                                } else {
                                    val focal = currCentroid - offset
                                    val newOffset = currCentroid - focal * (newScale / oldScale) + panDelta

                                    val viewW = canvasSize.width.toFloat()
                                    val viewH = canvasSize.height.toFloat()
                                    val maxOffsetX = 0f
                                    val minOffsetX = viewW - viewW * newScale
                                    val maxOffsetY = 0f
                                    val minOffsetY = viewH - viewH * newScale

                                    offset = Offset(
                                        newOffset.x.coerceIn(minOffsetX, maxOffsetX),
                                        newOffset.y.coerceIn(minOffsetY, maxOffsetY)
                                    )
                                }

                                event.changes.forEach { it.consume() }
                            } else if (totalPointers > 1) {
                                hadMultiTouch = true
                                tooltipActive = false
                                if (glidingTile != null) {
                                    glidingTile = null
                                    glidingTouchPos = null
                                }
                            }

                            if (pressedPointers == 0) {
                                // All fingers released (UP)
                                val elapsed = System.currentTimeMillis() - downTime
                                tooltipActive = false
                                glidingTile = null
                                glidingTouchPos = null

                                if (!bottomSheetOpened && !hadMultiTouch && !hasMovedOutOfInitialTile && elapsed < longPressActivationTimeout) {
                                    // Deliberate quick tap without dragging
                                    if (initialTile != null) {
                                        triggerCrispHaptic(context)
                                        onNodeSelected(initialTile.node, initialTile.path)
                                    }
                                }
                                break
                            } else if (pressedPointers == 1 && !hadMultiTouch && totalPointers == 1) {
                                val change = pressedChanges.first()
                                val now = System.currentTimeMillis()
                                val elapsedSinceDown = now - downTime
                                val curTile = getTileAt(change.position)

                                if (!tooltipActive) {
                                    if (curTile != initialTile) {
                                        hasMovedOutOfInitialTile = true
                                    }

                                    // Must hold on initial tile for 0.5s without moving out
                                    if (elapsedSinceDown >= longPressActivationTimeout && !hasMovedOutOfInitialTile && curTile == initialTile && initialTile != null) {
                                        tooltipActive = true
                                        holdStartTime = now
                                        glidingTile = initialTile
                                        glidingTouchPos = change.position
                                        triggerCrispHaptic(context)
                                    }
                                } else {
                                    // Tooltip is active: glide inspection mode
                                    if (curTile != null) {
                                        if (curTile != glidingTile) {
                                            triggerCrispHaptic(context)
                                            glidingTile = curTile
                                            holdStartTime = now // Reset 3s hold timer for the new hovered tile
                                        }
                                        glidingTouchPos = change.position

                                        // Only after tooltip appears and is continuously held on this tile for 3.0s: open sheet
                                        if (now - holdStartTime >= continuousHoldTimeout && !bottomSheetOpened) {
                                            bottomSheetOpened = true
                                            triggerCrispHaptic(context)
                                            tooltipActive = false
                                            glidingTile = null
                                            glidingTouchPos = null
                                            onNodeSelected(curTile.node, curTile.path)
                                        }
                                    } else {
                                        glidingTile = null
                                        glidingTouchPos = change.position
                                    }
                                }
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
                    val minDim = minOf(drawW, drawH)
                    val strokeW = if (minDim < 8f) maxOf(1f, minDim * 0.25f) else if (minDim < 20f) 2.0f else 2.5f
                    val halfStroke = strokeW / 2f

                    drawRect(
                        color = Color(0x6600E676),
                        topLeft = Offset(sLeft, sTop),
                        size = Size(drawW, drawH)
                    )
                    drawRect(
                        color = Color(0xFF00E676),
                        topLeft = Offset(sLeft + halfStroke, sTop + halfStroke),
                        size = Size(maxOf(1f, drawW - strokeW), maxOf(1f, drawH - strokeW)),
                        style = Stroke(width = strokeW)
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

                    val tileBaseColor = if (tile.pkgName != null) {
                        AppIconCache.getDominantColor(tile.pkgName) ?: tile.baseColor
                    } else {
                        tile.baseColor
                    }

                    if (isAmoled) {
                        drawRect(
                            color = tileBaseColor,
                            topLeft = Offset(sLeft, sTop),
                            size = Size(drawW, drawH),
                            style = Stroke(width = if (currentScale > 1.5f) 2.5f else 1.5f)
                        )
                    } else if (drawW < 8f || drawH < 8f) {
                        drawRect(
                            color = tileBaseColor,
                            topLeft = Offset(sLeft, sTop),
                            size = Size(drawW, drawH)
                        )
                    } else {
                        val cHighlight = if (isDark) {
                            Color(
                                red = (tileBaseColor.red * 1.30f + 0.10f).coerceIn(0f, 1f),
                                green = (tileBaseColor.green * 1.30f + 0.10f).coerceIn(0f, 1f),
                                blue = (tileBaseColor.blue * 1.30f + 0.10f).coerceIn(0f, 1f),
                                alpha = 1f
                            )
                        } else {
                            Color(
                                red = (tileBaseColor.red * 1.35f + 0.15f).coerceIn(0f, 1f),
                                green = (tileBaseColor.green * 1.35f + 0.15f).coerceIn(0f, 1f),
                                blue = (tileBaseColor.blue * 1.35f + 0.15f).coerceIn(0f, 1f),
                                alpha = 1f
                            )
                        }
                        val cShadow = if (isDark) {
                            Color(
                                red = (tileBaseColor.red * 0.45f).coerceIn(0f, 1f),
                                green = (tileBaseColor.green * 0.45f).coerceIn(0f, 1f),
                                blue = (tileBaseColor.blue * 0.45f).coerceIn(0f, 1f),
                                alpha = 1f
                            )
                        } else {
                            Color(
                                red = (tileBaseColor.red * 0.40f).coerceIn(0f, 1f),
                                green = (tileBaseColor.green * 0.40f).coerceIn(0f, 1f),
                                blue = (tileBaseColor.blue * 0.40f).coerceIn(0f, 1f),
                                alpha = 1f
                            )
                        }
                        val brush = Brush.radialGradient(
                            colors = listOf(cHighlight, tileBaseColor, cShadow),
                            center = Offset(sLeft + drawW * 0.35f, sTop + drawH * 0.30f),
                            radius = maxOf(drawW, drawH) * 1.05f
                        )
                        drawRect(
                            brush = brush,
                            topLeft = Offset(sLeft, sTop),
                            size = Size(drawW, drawH)
                        )
                    }

                    val isMarked = selectedNodes.contains(tile.node)
                    if (isMarked) {
                        val minDim = minOf(drawW, drawH)
                        val strokeW = if (minDim < 8f) maxOf(1f, minDim * 0.25f) else if (minDim < 20f) 2.0f else 2.5f
                        val halfStroke = strokeW / 2f

                        drawRect(
                            color = Color(0x6600E676),
                            topLeft = Offset(sLeft, sTop),
                            size = Size(drawW, drawH)
                        )
                        drawRect(
                            color = Color(0xFF00E676),
                            topLeft = Offset(sLeft + halfStroke, sTop + halfStroke),
                            size = Size(maxOf(1f, drawW - strokeW), maxOf(1f, drawH - strokeW)),
                            style = Stroke(width = strokeW)
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

            // Exact adaptive inset selection border matching the precise tile bounds
            val sel = selectedTile
            if (selectedNode != null && sel != null) {
                val selLeft = sel.left * currentScale + currentOffset.x
                val selTop = sel.top * currentScale + currentOffset.y
                val selW = sel.width * currentScale
                val selH = sel.height * currentScale

                val minDim = minOf(selW, selH)
                val strokeWidth = if (minDim < 8f) {
                    maxOf(1f, minDim * 0.25f)
                } else if (minDim < 20f) {
                    2.0f
                } else {
                    3.0f
                }

                val halfStroke = strokeWidth / 2f
                val inLeft = selLeft + halfStroke
                val inTop = selTop + halfStroke
                val inW = maxOf(1f, selW - strokeWidth)
                val inH = maxOf(1f, selH - strokeWidth)

                drawRect(
                    color = SelectionBorderColor,
                    topLeft = Offset(inLeft, inTop),
                    size = Size(inW, inH),
                    style = Stroke(width = strokeWidth)
                )
            }
        }

        // Floating Glide Inspection Tooltip Overlay (Bigger preview on top, name below with middle ellipsis, size below name)
        val targetPos = glidingTouchPos
        val currentGliding = glidingTile
        if (targetPos != null && currentGliding != null) {
            val touchXPx = targetPos.x
            val touchYPx = targetPos.y
            val cardWidthPx = with(density) { 180.dp.toPx() }
            val cardHeightPx = with(density) { 140.dp.toPx() }
            val paddingPx = with(density) { 16.dp.toPx() }

            val clampedX = (touchXPx - cardWidthPx / 2f).coerceIn(paddingPx, (canvasSize.width - cardWidthPx - paddingPx).coerceAtLeast(paddingPx))
            val clampedY = if (touchYPx > cardHeightPx + paddingPx + 36f) {
                touchYPx - cardHeightPx - 36f
            } else {
                touchYPx + 44f
            }

            val animatedOffset by animateIntOffsetAsState(
                targetValue = IntOffset(clampedX.toInt(), clampedY.toInt()),
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
                label = "glideTooltipOffset"
            )

            AnimatedVisibility(
                visible = glidingTile != null,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessHigh)) +
                        scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh)) +
                       scaleOut(targetScale = 0.88f, animationSpec = spring(stiffness = Spring.StiffnessHigh)),
                modifier = Modifier.offset { animatedOffset }
            ) {
                val tile = currentGliding
                val isMedia = remember(tile.path) {
                    val ext = tile.path.substringAfterLast('.', "").lowercase()
                    ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "dng", "raw", "bmp", "mp4", "mkv", "avi", "mov", "webm", "3gp", "ts", "m4v", "flv", "wmv", "apk")
                }
                val textShadow = remember {
                    Shadow(
                        color = Color.Black.copy(alpha = 0.95f),
                        offset = Offset(0f, 2f),
                        blurRadius = 8f
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (pureBlack && isDark) Color(0xEE000000) else Color(0xEE1E222D),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.widthIn(min = 140.dp, max = 200.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 1. Bigger Preview on Top
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (tile.isApp && tile.pkgName != null) {
                                AppIconView(packageName = tile.pkgName, contentDescription = null, modifier = Modifier.size(52.dp))
                            } else if (isMedia) {
                                MediaThumbnailView(node = tile.node, path = tile.path, modifier = Modifier.fillMaxSize())
                            } else {
                                val iconName = if (tile.node.isDirectory) "folder" else "description"
                                MaterialSymbol(iconName, active = true, size = 36.dp, tint = tile.baseColor)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. File Name with Middle Ellipsis below preview
                        Text(
                            text = FileUtils.middleEllipsis(FileUtils.cleanDisplayName(tile.node.name), 22),
                            style = MaterialTheme.typography.titleSmall.copy(shadow = textShadow),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // 3. File Size below Name
                        Text(
                            text = FileUtils.formatFileSize(tile.node.size, context),
                            style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
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

private val ExtensionColorCacheDark = LruCache<String, Color>(128)
private val ExtensionColorCacheLight = LruCache<String, Color>(128)

fun getNodeColor(node: CompactNode, isDark: Boolean = true): Color {
    val name = node.name
    if (name == "[Free Space]") return if (isDark) Color(0xFF1E293B) else Color(0xFF64748B)
    if (name == "[System & OS]") return if (isDark) Color(0xFF141923) else Color(0xFF475569)
    if (name == "[Temporary System Files]" || name == "Temporary System Files") return if (isDark) Color(0xFF5A3000) else Color(0xFFF59E0B)
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
            val hash = abs(ext.hashCode())
            val hue = (hash * 137.50777f) % 360f
            Color.hsl(hue = hue, saturation = 0.65f, lightness = if (isDark) 0.28f else 0.50f)
        }
    }
    cache.put(ext, computed)
    return computed
}
