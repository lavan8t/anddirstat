package com.kd.anddirstat.treemap

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animate
import androidx.compose.ui.util.lerp
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
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
    onZoomChanged: ((Boolean) -> Unit)? = null,
    onZoomFocusChanged: ((Float) -> Unit)? = null,
    isLandscape: Boolean = false,
    onNodeSelected: (CompactNode, String, Offset?) -> Unit,
    onDismissPopup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bottomSpaceDp = if (isLandscape) 0.dp else 64.dp
    val bottomSpacePx = remember(density, isLandscape) {
        with(density) { bottomSpaceDp.toPx() }
    }

    val displayMetrics = remember { context.resources.displayMetrics }
    var canvasSize by remember { mutableStateOf(IntSize(displayMetrics.widthPixels, displayMetrics.heightPixels)) }
    val shadingMode = remember { FileUtils.getTreemapShadingMode(context) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val currentScaleState = rememberUpdatedState(scale)
    val currentOffsetState = rememberUpdatedState(offset)

    val cacheKey = "${System.identityHashCode(rootNode)}_${canvasSize.width}_${canvasSize.height}_${isDark}_$isLandscape"
    val precalculatedTiles = remember { mutableStateOf<List<TreemapTile>>(emptyList()) }
    val quadtree = remember { mutableStateOf<TreemapQuadtree?>(null) }

    LaunchedEffect(cacheKey, rootNode, rootPath, isDark) {
        val usableH = (canvasSize.height.toFloat() - bottomSpacePx).coerceAtLeast(100f)
        if (canvasSize.width > 0 && usableH > 0) {
            val tiles = withContext(Dispatchers.Default) {
                computeTreemapTiles(rootNode, rootPath, canvasSize.width.toFloat(), usableH, isDark)
            }
            precalculatedTiles.value = tiles
            quadtree.value = if (tiles.isEmpty()) null else {
                val qt = TreemapQuadtree(0f, 0f, canvasSize.width.toFloat(), usableH)
                qt.insertAll(tiles)
                qt
            }
        } else {
            precalculatedTiles.value = emptyList()
            quadtree.value = null
        }
    }

    val bottomTiles = remember(precalculatedTiles.value, bottomSpacePx, canvasSize.height) {
        if (bottomSpacePx <= 0f) emptyList()
        else {
            val usableH = (canvasSize.height.toFloat() - bottomSpacePx).coerceAtLeast(100f)
            precalculatedTiles.value.filter { it.top + it.height >= usableH - 16f }
        }
    }

    val nodeToTileMap = remember(precalculatedTiles.value) {
        precalculatedTiles.value.associateBy { it.node }
    }

    LaunchedEffect(resetKey) {
        if (resetKey > 0) {
            val startScale = scale
            val startOffset = offset
            if (startScale != 1f || startOffset != Offset.Zero) {
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) { progress, _ ->
                    scale = lerp(startScale, 1f, progress)
                    offset = Offset(
                        lerp(startOffset.x, 0f, progress),
                        lerp(startOffset.y, 0f, progress)
                    )
                    onScaleChanged?.invoke(scale)
                }
            }
            scale = 1f
            offset = Offset.Zero
            onScaleChanged?.invoke(1f)
            onZoomChanged?.invoke(false)
            onZoomFocusChanged?.invoke(0.5f)
        }
    }

    val selectedTile = remember(selectedNode, nodeToTileMap) {
        if (selectedNode == null) null else nodeToTileMap[selectedNode]
    }

    val staticBitmap by produceState<ImageBitmap?>(
        initialValue = TreemapBitmapCache.get("${cacheKey}_$pureBlack"),
        key1 = cacheKey,
        key2 = pureBlack,
        key3 = precalculatedTiles.value
    ) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && precalculatedTiles.value.isNotEmpty()) {
            val bmpKey = "${cacheKey}_$pureBlack"
            val cachedBmp = TreemapBitmapCache.get(bmpKey)
            if (cachedBmp != null) {
                value = cachedBmp
            } else {
                val usableH = (canvasSize.height.toFloat() - bottomSpacePx).toInt().coerceAtLeast(100)
                val bmp = withContext(Dispatchers.Default) {
                    renderTreemapToBitmap(
                        tiles = precalculatedTiles.value,
                        width = canvasSize.width,
                        height = usableH,
                        isDark = isDark,
                        pureBlack = pureBlack,
                        context = context
                    )
                }
                TreemapBitmapCache.put(bmpKey, bmp)
                value = bmp
            }
        } else {
            value = null
        }
    }

    var lastSelectedTile by remember { mutableStateOf<TreemapTile?>(null) }
    if (selectedTile != null) {
        lastSelectedTile = selectedTile
    }

    val hasSelection = selectedNode != null && selectedTile != null

    val selectionScale by animateFloatAsState(
        targetValue = if (hasSelection) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "selectionScale"
    )

    val targetStrokeWidth = if (selectedTile != null) {
        val minDim = minOf(selectedTile.width * scale, selectedTile.height * scale)
        if (minDim < 8f) maxOf(1f, minDim * 0.25f) else if (minDim < 20f) 2.0f else 3.0f
    } else 0f

    val selectionBorderWidth by animateFloatAsState(
        targetValue = targetStrokeWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "selectionBorderWidth"
    )

    val canvasBg = if (pureBlack && isDark) Color.Black else if (isDark) Color(0xFF08090E) else Color(0xFFF1F3F9)

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
                .pointerInput(precalculatedTiles.value, quadtree.value) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val downPos = down.position

                        fun getTileAt(touchOffset: Offset): TreemapTile? {
                            val curScale = currentScaleState.value
                            val curOff = currentOffsetState.value
                            val touchX = (touchOffset.x - curOff.x) / curScale
                            val touchY = (touchOffset.y - curOff.y) / curScale
                            val qt = quadtree.value
                            return if (qt != null) {
                                qt.queryVisible(touchX, touchY, touchX, touchY).lastOrNull()
                            } else {
                                precalculatedTiles.value.findLast {
                                    touchX >= it.left && touchX <= it.left + it.width &&
                                    touchY >= it.top && touchY <= it.top + it.height
                                }
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
                                onDismissPopup()
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
                                val wasZoomed = oldScale > 1.02f
                                val isZoomedNow = newScale > 1.02f
                                if (wasZoomed != isZoomedNow) {
                                    onZoomChanged?.invoke(isZoomedNow)
                                }

                                if (newScale <= 1.001f) {
                                    offset = Offset.Zero
                                } else {
                                    val focal = currCentroid - offset
                                    val newOffset = currCentroid - focal * (newScale / oldScale) + panDelta

                                    val viewW = canvasSize.width.toFloat()
                                    val viewH = (canvasSize.height.toFloat() - bottomSpacePx).coerceAtLeast(100f)
                                    val maxOffsetX = 0f
                                    val minOffsetX = viewW - viewW * newScale
                                    val maxOffsetY = 0f
                                    val minOffsetY = viewH - viewH * newScale

                                    offset = Offset(
                                        newOffset.x.coerceIn(minOffsetX, maxOffsetX),
                                        newOffset.y.coerceIn(minOffsetY, maxOffsetY)
                                    )

                                    if (viewW > 0f && newScale > 1.02f) {
                                        val focusXRatio = ((-offset.x + viewW / 2f) / (viewW * newScale)).coerceIn(0f, 1f)
                                        onZoomFocusChanged?.invoke(focusXRatio)
                                    } else {
                                        onZoomFocusChanged?.invoke(0.5f)
                                    }
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
                                val elapsed = System.currentTimeMillis() - downTime
                                tooltipActive = false
                                glidingTile = null
                                glidingTouchPos = null

                                if (!hadMultiTouch && !hasMovedOutOfInitialTile && elapsed < longPressActivationTimeout) {
                                    if (initialTile != null) {
                                        triggerCrispHaptic(context)
                                        onNodeSelected(initialTile.node, initialTile.path, downPos)
                                    } else {
                                        onDismissPopup()
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

                                    if (elapsedSinceDown >= longPressActivationTimeout && !hasMovedOutOfInitialTile && curTile == initialTile && initialTile != null) {
                                        tooltipActive = true
                                        glidingTile = initialTile
                                        triggerCrispHaptic(context)
                                        onNodeSelected(initialTile.node, initialTile.path, change.position)
                                    }
                                } else {
                                    // Glide mode: immediately select and open popup for hovered tile
                                    if (curTile != null && curTile != glidingTile) {
                                        triggerCrispHaptic(context)
                                        glidingTile = curTile
                                        onNodeSelected(curTile.node, curTile.path, change.position)
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            val usableDrawH = (size.height - bottomSpacePx).coerceAtLeast(100f)
            clipRect(0f, 0f, size.width, usableDrawH) {
                val currentScale = scale
                val currentOffset = offset
                val isAmoled = pureBlack && isDark
                val currentStaticBitmap = staticBitmap
                val isFrozenStatic = currentScale <= 1.001f && currentOffset == Offset.Zero && currentStaticBitmap != null

                if (isFrozenStatic) {
                    drawImage(currentStaticBitmap)

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
                    // Direct full GPU hardware vector rendering on zoom and pan with Quadtree spatial culling
                    val viewW = size.width
                    val viewH = size.height
                    val qt = quadtree.value
                    val drawTiles = if (qt != null && (currentScale > 1.05f || currentOffset != Offset.Zero)) {
                        val vLeft = (-currentOffset.x / currentScale).coerceAtLeast(0f)
                        val vTop = (-currentOffset.y / currentScale).coerceAtLeast(0f)
                        val vRight = ((viewW - currentOffset.x) / currentScale).coerceAtMost(canvasSize.width.toFloat())
                        val vBottom = ((viewH - currentOffset.y) / currentScale).coerceAtMost(canvasSize.height.toFloat())
                        val minTileArea = (0.5f / (currentScale * currentScale)).coerceAtLeast(0.0001f)
                        qt.queryVisible(vLeft, vTop, vRight, vBottom, minTileArea)
                    } else {
                        precalculatedTiles.value
                    }

                    val numTiles = drawTiles.size
                    if (numTiles > 2500 && currentStaticBitmap != null && currentScale < 4.0f) {
                        drawContext.canvas.save()
                        drawContext.transform.translate(currentOffset.x, currentOffset.y)
                        drawContext.transform.scale(currentScale, currentScale, pivot = Offset.Zero)
                        drawImage(currentStaticBitmap)
                        drawContext.canvas.restore()

                        for (node in selectedNodes) {
                            val tile = nodeToTileMap[node] ?: continue
                            val sLeft = tile.left * currentScale + currentOffset.x
                            val sTop = tile.top * currentScale + currentOffset.y
                            val sW = tile.width * currentScale
                            val sH = tile.height * currentScale
                            if (sLeft + sW < 0f || sLeft > viewW || sTop + sH < 0f || sTop > viewH) continue

                            val drawW = maxOf(1f, sW)
                            val drawH = maxOf(1f, sH)
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
                        for (i in 0 until numTiles) {
                        val tile = drawTiles[i]

                        val sLeft = tile.left * currentScale + currentOffset.x
                        val sTop = tile.top * currentScale + currentOffset.y
                        val sW = tile.width * currentScale
                        val sH = tile.height * currentScale

                        // Viewport frustum culling and sub-pixel micro-culling
                        if (sLeft + sW < 0f || sLeft > viewW || sTop + sH < 0f || sTop > viewH || sW < 0.5f || sH < 0.5f) {
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
                        } else if (shadingMode == FileUtils.TreemapShadingMode.SOLID || drawW < 14f || drawH < 14f) {
                            drawRect(
                                color = tileBaseColor,
                                topLeft = Offset(sLeft, sTop),
                                size = Size(drawW, drawH)
                            )
                        } else {
                            if (shadingMode == FileUtils.TreemapShadingMode.CUSHION) {
                                drawContext.canvas.save()
                                drawContext.transform.translate(sLeft, sTop)
                                drawContext.transform.scale(drawW, drawH, pivot = Offset.Zero)
                                drawRect(
                                    brush = tile.cushionBrush,
                                    topLeft = Offset.Zero,
                                    size = Size(1f, 1f)
                                )
                                drawContext.canvas.restore()
                            } else {
                                drawContext.canvas.save()
                                drawContext.transform.translate(sLeft, sTop)
                                drawContext.transform.scale(drawW, drawH, pivot = Offset.Zero)
                                drawRect(
                                    brush = tile.linearBrush,
                                    topLeft = Offset.Zero,
                                    size = Size(1f, 1f)
                                )
                                drawContext.canvas.restore()
                            }
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
                            val bmp = AppIconCache.peek(tile.pkgName) ?: AppIconCache.get(context, tile.pkgName)
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
            }

            // Exact adaptive inset selection border matching the precise tile bounds with spring physics
            val sel = selectedTile ?: lastSelectedTile
            if (sel != null && selectionBorderWidth > 0.05f) {
                val curScale = currentScaleState.value
                val curOff = currentOffsetState.value
                val selLeft = sel.left * curScale + curOff.x
                val selTop = sel.top * curScale + curOff.y
                val selW = sel.width * curScale
                val selH = sel.height * curScale

                val centerX = selLeft + selW / 2f
                val centerY = selTop + selH / 2f
                val scaledW = selW * selectionScale
                val scaledH = selH * selectionScale

                val sLeft = centerX - scaledW / 2f
                val sTop = centerY - scaledH / 2f

                val halfStroke = selectionBorderWidth / 2f
                val inLeft = sLeft + halfStroke
                val inTop = sTop + halfStroke
                val inW = maxOf(1f, scaledW - selectionBorderWidth)
                val inH = maxOf(1f, scaledH - selectionBorderWidth)

                drawRect(
                    color = SelectionBorderColor,
                    topLeft = Offset(inLeft, inTop),
                    size = Size(inW, inH),
                    style = Stroke(width = selectionBorderWidth)
                )
            }
        }
    }

        // Bottom nav-pill zone: true progressive Gaussian blur connecting seamlessly with treemap tiles
        if (!isLandscape && bottomSpaceDp > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomSpaceDp)
                    .clipToBounds()
            ) {
                if (bottomTiles.isNotEmpty()) {
                    val drawColumns: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                        val curScale = currentScaleState.value
                        val curOff = currentOffsetState.value
                        for (tile in bottomTiles) {
                            val tLeft = tile.left * curScale + curOff.x
                            val tWidth = maxOf(1f, tile.width * curScale)
                            val dom = tile.pkgName?.let { AppIconCache.getDominantColor(it) }
                            val colors = if (dom == null || dom == tile.baseColor) tile.gradientColors else shadeColors(dom, isDark)
                            if (shadingMode == FileUtils.TreemapShadingMode.CUSHION) {
                                val brush = tile.cushionBrush
                                drawContext.canvas.save()
                                drawContext.transform.translate(tLeft, 0f)
                                drawContext.transform.scale(tWidth, size.height, pivot = Offset.Zero)
                                drawRect(brush = brush, topLeft = Offset.Zero, size = Size(1f, 1f))
                                drawContext.canvas.restore()
                            } else {
                                drawRect(
                                    color = colors[1],
                                    topLeft = Offset(tLeft, 0f),
                                    size = Size(tWidth, size.height)
                                )
                            }
                        }
                    }

                    val blurModifier = remember {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Modifier.blur(BlurRadiusSpec.verticalGradient(0.dp, 36.dp))
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(20.dp)
                        } else {
                            Modifier
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(blurModifier),
                        onDraw = drawColumns
                    )
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
