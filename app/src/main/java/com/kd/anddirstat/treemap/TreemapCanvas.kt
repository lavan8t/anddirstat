package com.kd.anddirstat.treemap

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.LruCache
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Brush
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
    externalScale: Float? = null,
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

    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topSpaceDp = if (isLandscape) 48.dp else statusBarTopDp + 48.dp
    val topSpacePx = remember(density, topSpaceDp) {
        with(density) { topSpaceDp.toPx() }
    }

    val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpaceDp = if (isLandscape) 0.dp else navBarBottomDp + 64.dp
    val bottomSpacePx = remember(density, bottomSpaceDp) {
        with(density) { bottomSpaceDp.toPx() }
    }

    val displayMetrics = remember { context.resources.displayMetrics }
    var canvasSize by remember { mutableStateOf(IntSize(displayMetrics.widthPixels, displayMetrics.heightPixels)) }
    val shadingMode = remember { FileUtils.getTreemapShadingMode(context) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(externalScale) {
        if (externalScale != null) {
            val newScale = externalScale.coerceIn(1f, 10f)
            scale = newScale
            val viewW = canvasSize.width.toFloat()
            val usableH = (canvasSize.height.toFloat() - topSpacePx - bottomSpacePx).coerceAtLeast(100f)
            val centerY = topSpacePx + usableH / 2f
            offset = Offset(
                (viewW / 2f) * (1f - newScale),
                centerY * (1f - newScale)
            )
            onScaleChanged?.invoke(newScale)
            onZoomChanged?.invoke(newScale > 1.02f)
        }
    }

    val currentScaleState = rememberUpdatedState(scale)
    val currentOffsetState = rememberUpdatedState(offset)

    val cacheKey = "${System.identityHashCode(rootNode)}_${canvasSize.width}_${canvasSize.height}_${isDark}_${isLandscape}_${topSpacePx.toInt()}_${bottomSpacePx.toInt()}"
    val precalculatedTiles = remember { mutableStateOf<List<TreemapTile>>(emptyList()) }
    val quadtree = remember { mutableStateOf<TreemapQuadtree?>(null) }

    LaunchedEffect(cacheKey, rootNode, rootPath, isDark, topSpacePx, bottomSpacePx) {
        val usableW = canvasSize.width.toFloat().coerceAtLeast(100f)
        val usableH = (canvasSize.height.toFloat() - topSpacePx - bottomSpacePx).coerceAtLeast(100f)
        if (usableW > 0 && usableH > 0) {
            val tiles = withContext(Dispatchers.Default) {
                computeTreemapTiles(rootNode, rootPath, usableW, usableH, isDark, topSpacePx)
            }
            precalculatedTiles.value = tiles
            quadtree.value = if (tiles.isEmpty()) null else {
                val qt = TreemapQuadtree(0f, topSpacePx, usableW, topSpacePx + usableH)
                qt.insertAll(tiles)
                qt
            }
        } else {
            precalculatedTiles.value = emptyList()
            quadtree.value = null
        }
    }

    val topTiles = remember(precalculatedTiles.value, topSpacePx) {
        if (topSpacePx <= 0f) emptyList()
        else {
            precalculatedTiles.value.filter { it.top <= topSpacePx + 16f }
        }
    }

    val bottomTiles = remember(precalculatedTiles.value, bottomSpacePx, canvasSize.height, topSpacePx) {
        if (bottomSpacePx <= 0f) emptyList()
        else {
            val usableH = (canvasSize.height.toFloat() - topSpacePx - bottomSpacePx).coerceAtLeast(100f)
            precalculatedTiles.value.filter { it.top + it.height >= topSpacePx + usableH - 16f }
        }
    }

    val nodeToTileMap = remember(precalculatedTiles.value) {
        precalculatedTiles.value.associateBy { it.node }
    }

    val selectedTile = remember(selectedNode, nodeToTileMap) {
        if (selectedNode == null) null else nodeToTileMap[selectedNode]
    }

    val glRenderer = remember { TreemapGLRenderer(context) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

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
                    val s = lerp(startScale, 1f, progress)
                    val off = Offset(
                        lerp(startOffset.x, 0f, progress),
                        lerp(startOffset.y, 0f, progress)
                    )
                    scale = s
                    offset = off
                    glRenderer.setScaleAndOffset(s, off)
                    glSurfaceViewRef?.requestRender()
                    onScaleChanged?.invoke(s)
                }
            }
            scale = 1f
            offset = Offset.Zero
            glRenderer.setScaleAndOffset(1f, Offset.Zero)
            glSurfaceViewRef?.requestRender()
            onScaleChanged?.invoke(1f)
            onZoomChanged?.invoke(false)
            onZoomFocusChanged?.invoke(0.5f)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> glSurfaceViewRef?.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> glSurfaceViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceViewRef?.onPause()
        }
    }

    val appTiles = remember(precalculatedTiles.value) {
        precalculatedTiles.value.filter { it.pkgName != null }
    }

    val canvasBg = if (pureBlack && isDark) Color.Black else if (isDark) Color(0xFF08090E) else Color(0xFFF1F3F9)

    LaunchedEffect(canvasBg, shadingMode) {
        glRenderer.setClearColor(canvasBg.red, canvasBg.green, canvasBg.blue, canvasBg.alpha)
        val modeInt = when (shadingMode) {
            FileUtils.TreemapShadingMode.SOLID -> 1
            FileUtils.TreemapShadingMode.LINEAR -> 2
            else -> if (pureBlack && isDark) 3 else 0
        }
        glRenderer.setShadingMode(modeInt)
        glSurfaceViewRef?.requestRender()
    }

    LaunchedEffect(precalculatedTiles.value) {
        if (precalculatedTiles.value.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                glRenderer.updateTiles(precalculatedTiles.value)
            }
            glSurfaceViewRef?.requestRender()
        }
    }

    LaunchedEffect(scale, offset) {
        glRenderer.setScaleAndOffset(scale, offset)
        glSurfaceViewRef?.requestRender()
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

    var glidingTile by remember { mutableStateOf<TreemapTile?>(null) }
    var glidingTouchPos by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    holder.setFormat(PixelFormat.TRANSLUCENT)
                    setZOrderMediaOverlay(true)
                    setRenderer(glRenderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    glSurfaceViewRef = this
                }
            },
            update = { glView ->
                glRenderer.setScaleAndOffset(scale, offset)
                glView.requestRender()
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { newSize ->
                    if (newSize.width > 0 && newSize.height > 0 && (newSize.width != canvasSize.width || newSize.height != canvasSize.height)) {
                        canvasSize = newSize
                    }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
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
                                    val viewH = (canvasSize.height.toFloat() - topSpacePx - bottomSpacePx).coerceAtLeast(100f)
                                    val maxOffsetX = 0f
                                    val minOffsetX = viewW - viewW * newScale
                                    val maxOffsetY = topSpacePx * (1f - newScale)
                                    val minOffsetY = (topSpacePx + viewH) - (topSpacePx + viewH) * newScale

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

                                glRenderer.setScaleAndOffset(scale, offset)
                                glSurfaceViewRef?.requestRender()

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
            val sel = selectedTile ?: lastSelectedTile
            val hasSelectionBorder = (sel != null && selectionBorderWidth > 0.05f)
            val hasMarkedNodes = selectedNodes.isNotEmpty()
            val hasAppIcons = appTiles.isNotEmpty()

            if (hasSelectionBorder || hasMarkedNodes || hasAppIcons) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val curScale = currentScaleState.value
                    val curOff = currentOffsetState.value

                    if (hasAppIcons) {
                        for (tile in appTiles) {
                            val sLeft = tile.left * curScale + curOff.x
                            val sTop = tile.top * curScale + curOff.y
                            val sW = tile.width * curScale
                            val sH = tile.height * curScale
                            if (sLeft + sW < 0f || sLeft > size.width || sTop + sH < 0f || sTop > size.height) continue
                            if (sW >= 24f && sH >= 24f) {
                                val bmp = AppIconCache.get(context, tile.pkgName!!)
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

                    if (hasMarkedNodes) {
                        for (node in selectedNodes) {
                            val tile = nodeToTileMap[node] ?: continue
                            val sLeft = tile.left * curScale + curOff.x
                            val sTop = tile.top * curScale + curOff.y
                            val sW = tile.width * curScale
                            val sH = tile.height * curScale
                            if (sLeft + sW < 0f || sLeft > size.width || sTop + sH < 0f || sTop > size.height) continue

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
                    }

                    if (sel != null && selectionBorderWidth > 0.05f) {
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
        }

        // Top ambient zone: progressive Gaussian blur extending treemap colors behind transparent top bar
        if (topSpaceDp > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topSpaceDp)
                    .clipToBounds()
            ) {
                if (topTiles.isNotEmpty()) {
                    val drawTopColumns: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                        val curScale = currentScaleState.value
                        val curOff = currentOffsetState.value
                        for (tile in topTiles) {
                            val tLeft = tile.left * curScale + curOff.x
                            val tWidth = maxOf(1f, tile.width * curScale)
                            if (tLeft + tWidth < 0f || tLeft > size.width) continue
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

                    val topBlurModifier = remember {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Modifier.blur(BlurRadiusSpec.verticalGradient(36.dp, 0.dp))
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(20.dp)
                        } else {
                            Modifier
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(topBlurModifier),
                        onDraw = drawTopColumns
                    )

                    // Subtle top scrim gradient for crisp status bar legibility
                    val topScrimHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topScrimHeight)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        (if (pureBlack && isDark) Color.Black else if (isDark) Color(0xFF08090E) else Color(0xFFF1F3F9)).copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }

        // Bottom ambient zone: progressive Gaussian blur extending treemap colors under nav-pill
        if (!isLandscape && bottomSpaceDp > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomSpaceDp)
                    .clipToBounds()
            ) {
                if (bottomTiles.isNotEmpty()) {
                    val drawBottomColumns: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                        val curScale = currentScaleState.value
                        val curOff = currentOffsetState.value
                        for (tile in bottomTiles) {
                            val tLeft = tile.left * curScale + curOff.x
                            val tWidth = maxOf(1f, tile.width * curScale)
                            if (tLeft + tWidth < 0f || tLeft > size.width) continue
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

                    val bottomBlurModifier = remember {
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
                            .then(bottomBlurModifier),
                        onDraw = drawBottomColumns
                    )

                    // Subtle bottom scrim gradient for gesture bar legibility
                    val bottomScrimHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(bottomScrimHeight)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        (if (pureBlack && isDark) Color.Black else if (isDark) Color(0xFF08090E) else Color(0xFFF1F3F9)).copy(alpha = 0.40f)
                                    )
                                )
                            )
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
