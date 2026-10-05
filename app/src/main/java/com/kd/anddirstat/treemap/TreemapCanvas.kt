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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
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
    val precalculatedTiles = remember(cacheKey, bottomSpacePx) {
        val usableH = (canvasSize.height.toFloat() - bottomSpacePx).coerceAtLeast(100f)
        if (canvasSize.width > 0 && usableH > 0) {
            computeTreemapTiles(rootNode, rootPath, canvasSize.width.toFloat(), usableH, isDark)
        } else {
            emptyList()
        }
    }

    val bottomTiles = remember(precalculatedTiles, bottomSpacePx, canvasSize.height) {
        if (bottomSpacePx <= 0f) emptyList()
        else {
            val usableH = (canvasSize.height.toFloat() - bottomSpacePx).coerceAtLeast(100f)
            precalculatedTiles.filter { it.top + it.height >= usableH - 16f }
        }
    }

    val nodeToTileMap = remember(precalculatedTiles) {
        precalculatedTiles.associateBy { it.node }
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
        key3 = precalculatedTiles
    ) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && precalculatedTiles.isNotEmpty()) {
            val bmpKey = "${cacheKey}_$pureBlack"
            val cachedBmp = TreemapBitmapCache.get(bmpKey)
            if (cachedBmp != null) {
                value = cachedBmp
            } else {
                val usableH = (canvasSize.height.toFloat() - bottomSpacePx).toInt().coerceAtLeast(100)
                val bmp = withContext(Dispatchers.Default) {
                    renderTreemapToBitmap(
                        tiles = precalculatedTiles,
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

    var lastSelectedNode by remember { mutableStateOf<CompactNode?>(null) }
    var bouncePulse by remember { mutableStateOf(1f) }

    LaunchedEffect(selectedNode) {
        if (selectedNode != null && selectedNode != lastSelectedNode) {
            lastSelectedNode = selectedNode
            bouncePulse = 1.05f
            kotlinx.coroutines.delay(16)
            bouncePulse = 1f
        } else if (selectedNode == null) {
            lastSelectedNode = null
            bouncePulse = 1f
        }
    }

    val selectionScale by animateFloatAsState(
        targetValue = if (hasSelection) bouncePulse else 0.92f,
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
                    // Direct full GPU hardware vector rendering on zoom and pan with frustum culling
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
                        } else if (shadingMode == FileUtils.TreemapShadingMode.SOLID || drawW < 3f || drawH < 3f) {
                            drawRect(
                                color = tileBaseColor,
                                topLeft = Offset(sLeft, sTop),
                                size = Size(drawW, drawH)
                            )
                        } else {
                            val cHighlight = if (isDark) {
                                Color(
                                    red = (tileBaseColor.red * 1.65f + 0.25f).coerceIn(0f, 1f),
                                    green = (tileBaseColor.green * 1.65f + 0.25f).coerceIn(0f, 1f),
                                    blue = (tileBaseColor.blue * 1.65f + 0.25f).coerceIn(0f, 1f),
                                    alpha = 1f
                                )
                            } else {
                                Color(
                                    red = (tileBaseColor.red * 1.75f + 0.35f).coerceIn(0f, 1f),
                                    green = (tileBaseColor.green * 1.75f + 0.35f).coerceIn(0f, 1f),
                                    blue = (tileBaseColor.blue * 1.75f + 0.35f).coerceIn(0f, 1f),
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

                            if (shadingMode == FileUtils.TreemapShadingMode.CUSHION) {
                                drawContext.canvas.save()
                                drawContext.transform.translate(sLeft, sTop)
                                drawContext.transform.scale(drawW, drawH, pivot = Offset.Zero)
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(cHighlight, tileBaseColor, cShadow),
                                        center = Offset(0.38f, 0.35f),
                                        radius = 0.85f
                                    ),
                                    topLeft = Offset.Zero,
                                    size = Size(1f, 1f)
                                )
                                drawContext.canvas.restore()
                            } else {
                                val brush = Brush.linearGradient(
                                    colors = listOf(cHighlight, tileBaseColor, cShadow),
                                    start = Offset(sLeft, sTop),
                                    end = Offset(sLeft + drawW, sTop + drawH)
                                )
                                drawRect(
                                    brush = brush,
                                    topLeft = Offset(sLeft, sTop),
                                    size = Size(drawW, drawH)
                                )
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

        // Bottom nav-pill zone: sharp tile extrusion -> progressively blurred copy (alpha-masked) -> fade to bg
        if (!isLandscape && bottomSpaceDp > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomSpaceDp)
            ) {
                if (bottomTiles.isNotEmpty()) {
                    val drawColumns: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                        val curScale = currentScaleState.value
                        val curOff = currentOffsetState.value
                        for (tile in bottomTiles) {
                            drawRect(
                                color = if (tile.pkgName != null) AppIconCache.getDominantColor(tile.pkgName) ?: tile.baseColor else tile.baseColor,
                                topLeft = Offset(tile.left * curScale + curOff.x, 0f),
                                size = Size(maxOf(1f, tile.width * curScale), size.height)
                            )
                        }
                    }
                    Canvas(Modifier.fillMaxSize(), onDraw = drawColumns)
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        0.6f to Color.Black.copy(alpha = 0.6f),
                                        1f to Color.Black
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                            .blur(28.dp),
                        onDraw = drawColumns
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    canvasBg.copy(alpha = 0.15f),
                                    canvasBg.copy(alpha = 0.40f),
                                    canvasBg.copy(alpha = 0.70f),
                                    canvasBg.copy(alpha = 0.92f),
                                    canvasBg
                                )
                            )
                        )
                )
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
