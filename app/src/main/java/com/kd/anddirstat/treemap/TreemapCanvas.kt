package com.kd.anddirstat.treemap

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.ui.components.AppIconCache
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.provider.Settings

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
    isInteractive: Boolean = true,
    onScaleChanged: ((Float) -> Unit)? = null,
    onZoomChanged: ((Boolean) -> Unit)? = null,
    onZoomFocusChanged: ((Float) -> Unit)? = null,
    isLandscape: Boolean = false,
    onNodeSelected: (CompactNode, String, Offset?, androidx.compose.ui.geometry.Rect?, androidx.compose.ui.graphics.Color?) -> Unit = { _, _, _, _, _ -> },
    onDismissPopup: () -> Unit = {},
    onFocusRectChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onToggleSelectNode: ((CompactNode, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
            val viewH = canvasSize.height.toFloat()
            offset = Offset(
                (viewW / 2f) * (1f - newScale),
                (viewH / 2f) * (1f - newScale)
            )
            onScaleChanged?.invoke(newScale)
            onZoomChanged?.invoke(newScale > 1.02f)
        }
    }

    val currentScaleState = rememberUpdatedState(scale)
    val currentOffsetState = rememberUpdatedState(offset)
    val toggleSelectNode by rememberUpdatedState(onToggleSelectNode)

    val glRenderer = remember { TreemapGLRenderer(context) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
    val reduceMotion = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
    LaunchedEffect(reduceMotion) { glRenderer.setEntranceEnabled(!reduceMotion) }

    val density = LocalDensity.current
    val marginDp = 10.dp
    val marginPx = with(density) { marginDp.toPx() }

    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topSpaceDp = if (isLandscape) 48.dp else statusBarTopDp + 52.dp + marginDp
    val topSpacePx = with(density) { topSpaceDp.toPx() }

    val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpaceDp = if (isLandscape) 12.dp else navBarBottomDp + 64.dp + marginDp
    val bottomSpacePx = with(density) { bottomSpaceDp.toPx() }

    val leftSpaceDp = marginDp
    val leftSpacePx = with(density) { leftSpaceDp.toPx() }

    val rightSpaceDp = marginDp
    val rightSpacePx = with(density) { rightSpaceDp.toPx() }

    val cacheKey = "${System.identityHashCode(rootNode)}_${canvasSize.width}_${canvasSize.height}_${isDark}_${isLandscape}_${topSpacePx}_${bottomSpacePx}_${leftSpacePx}_${rightSpacePx}"
    val precalculatedTiles = remember { mutableStateOf<List<TreemapTile>>(emptyList()) }
    val quadtree = remember { mutableStateOf<TreemapQuadtree?>(null) }
    val tileListId = System.identityHashCode(precalculatedTiles.value)

    LaunchedEffect(cacheKey, rootNode, rootPath, isDark) {
        val usableW = (canvasSize.width.toFloat() - leftSpacePx - rightSpacePx).coerceAtLeast(100f)
        val usableH = (canvasSize.height.toFloat() - topSpacePx - bottomSpacePx).coerceAtLeast(100f)
        if (usableW > 0 && usableH > 0) {
            val tiles = withContext(Dispatchers.Default) {
                computeTreemapTiles(rootNode, rootPath, usableW, usableH, isDark, startY = topSpacePx, startX = leftSpacePx)
            }
            precalculatedTiles.value = tiles
            quadtree.value = if (tiles.isEmpty()) null else {
                val qt = TreemapQuadtree(leftSpacePx, topSpacePx, leftSpacePx + usableW, topSpacePx + usableH)
                qt.insertAll(tiles)
                qt
            }
        } else {
            precalculatedTiles.value = emptyList()
            quadtree.value = null
        }
    }

    val nodeToTileMap = remember(tileListId) {
        precalculatedTiles.value.associateBy { it.node }
    }

    // Glow grows with the falling tiles, then its reach tracks zoom. Strength is constant
    // per element so the border reads the same regardless of tile size.
    var glowProgress by remember { mutableStateOf(if (reduceMotion) 1f else 0f) }
    val glowSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(tileListId) {
        if (precalculatedTiles.value.isEmpty()) return@LaunchedEffect
        if (reduceMotion) {
            glowProgress = 1f
        } else {
            glowProgress = 0f
            animate(0f, 1f, animationSpec = glowSpec) { value, _ ->
                glowProgress = value
            }
        }
    }

    val selectedTile = remember(selectedNode, nodeToTileMap) {
        if (selectedNode == null) null else nodeToTileMap[selectedNode]
    }

    // Material 3 Expressive motion physics: damping ratio 0.82f, stiffness MediumLow
    LaunchedEffect(resetKey) {
        if (resetKey > 0) {
            val startScale = scale
            val startOffset = offset
            if (startScale != 1f || startOffset != Offset.Zero) {
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
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
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    glSurfaceViewRef?.onResume()
                    glSurfaceViewRef?.requestRender()
                }
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

    // Material 3 color tokens: zero hardcoded colors
    val surfaceColor = MaterialTheme.colorScheme.surface
    val canvasBg = if (pureBlack && isDark) Color.Black else surfaceColor

    LaunchedEffect(canvasBg, shadingMode) {
        // Fill the whole GL surface with the background colour. Uncovered pixels at high zoom then
        // show this colour instead of transparent black, so no gap or wallpaper shows through.
        glRenderer.setClearColor(canvasBg.red, canvasBg.green, canvasBg.blue, 1f)
        val modeInt = when (shadingMode) {
            FileUtils.TreemapShadingMode.SOLID -> 1
            FileUtils.TreemapShadingMode.LINEAR -> 2
            else -> if (pureBlack && isDark) 3 else 0
        }
        glRenderer.setShadingMode(modeInt)
        glSurfaceViewRef?.requestRender()
    }

    var entranceActive by remember { mutableStateOf(false) }

    LaunchedEffect(tileListId) {
        if (precalculatedTiles.value.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                glRenderer.updateTiles(precalculatedTiles.value)
            }
            if (!reduceMotion) {
                glRenderer.startEntrance()
                entranceActive = true
            }
            glSurfaceViewRef?.requestRender()
        }
    }

    LaunchedEffect(entranceActive) {
        if (!entranceActive) return@LaunchedEffect
        val deadline = System.currentTimeMillis() + 1400L
        while (System.currentTimeMillis() < deadline) {
            glSurfaceViewRef?.requestRender()
            delay(16L)
        }
        entranceActive = false
        glSurfaceViewRef?.requestRender()
    }

    LaunchedEffect(scale, offset, selectedTile) {
        glRenderer.setScaleAndOffset(scale, offset)
        glSurfaceViewRef?.requestRender()
        selectedTile?.let { tile ->
            val left = tile.left * scale + offset.x
            val top = tile.top * scale + offset.y
            onFocusRectChanged?.invoke(
                androidx.compose.ui.geometry.Rect(left, top, left + tile.width * scale, top + tile.height * scale)
            )
        }
    }

    var glidingTile by remember { mutableStateOf<TreemapTile?>(null) }
    var glidingTouchPos by remember { mutableStateOf<Offset?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary

    val gestureModifier = if (isInteractive) {
        Modifier.pointerInput(tileListId, quadtree.value, selectedNode) {
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
                    val direct = if (qt != null) {
                        qt.queryVisible(touchX, touchY, touchX, touchY).lastOrNull()
                    } else {
                        precalculatedTiles.value.findLast {
                            touchX >= it.left && touchX <= it.left + it.width &&
                            touchY >= it.top && touchY <= it.top + it.height
                        }
                    }
                    if (direct != null) return direct

                    // Margin tap tolerance: snapping touch to nearest perimeter tile when tapping inside margin
                    val marginTol = with(density) { 16.dp.toPx() } / curScale
                    val rightBound = canvasSize.width.toFloat() - rightSpacePx
                    val bottomBound = canvasSize.height.toFloat() - bottomSpacePx
                    val clampedX = touchX.coerceIn(leftSpacePx, rightBound)
                    val clampedY = touchY.coerceIn(topSpacePx, bottomBound)
                    if (kotlin.math.abs(touchX - clampedX) <= marginTol &&
                        kotlin.math.abs(touchY - clampedY) <= marginTol) {
                        return if (qt != null) {
                            qt.queryVisible(clampedX, clampedY, clampedX, clampedY).lastOrNull()
                        } else {
                            precalculatedTiles.value.findLast {
                                clampedX >= it.left && clampedX <= it.left + it.width &&
                                clampedY >= it.top && clampedY <= it.top + it.height
                            }
                        }
                    }
                    return null
                }

                val initialTile = getTileAt(downPos)
                var tooltipActive = false
                var hadMultiTouch = false
                var hasMovedOutOfInitialTile = false
                val longPressActivationTimeout = 220L

                while (true) {
                    val event = awaitPointerEvent()
                    val pressedChanges = event.changes.filter { it.pressed }
                    val totalPointers = event.changes.size
                    val pressedPointers = pressedChanges.size

                    if (pressedPointers >= 2) {
                        // Strictly Two-Finger Zoom and Pan
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
                            val viewH = canvasSize.height.toFloat()
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

                    fun selectTile(tile: TreemapTile, touchPos: Offset?) {
                        val curScale = currentScaleState.value
                        val curOff = currentOffsetState.value
                        val screenLeft = tile.left * curScale + curOff.x
                        val screenTop = tile.top * curScale + curOff.y
                        val screenW = tile.width * curScale
                        val screenH = tile.height * curScale
                        val tileRect = androidx.compose.ui.geometry.Rect(screenLeft, screenTop, screenLeft + screenW, screenTop + screenH)
                        onNodeSelected(tile.node, tile.path, touchPos, tileRect, tile.baseColor)
                    }

                    if (pressedPointers == 0) {
                        val elapsed = System.currentTimeMillis() - downTime
                        tooltipActive = false
                        glidingTile = null
                        glidingTouchPos = null

                        if (!hadMultiTouch && !hasMovedOutOfInitialTile && elapsed < longPressActivationTimeout) {
                            when {
                                selectedNode != null && initialTile?.node != selectedNode -> onDismissPopup()
                                selectedNode != null -> selectTile(initialTile!!, downPos)
                                initialTile == null -> onDismissPopup()
                                else -> {
                                    triggerCrispHaptic(context)
                                    selectTile(initialTile, downPos)
                                }
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
                                if (toggleSelectNode != null) {
                                    onDismissPopup()
                                    toggleSelectNode?.invoke(initialTile.node, initialTile.path)
                                } else {
                                    selectTile(initialTile, change.position)
                                }
                            }
                        } else {
                            if (curTile != null && curTile != glidingTile) {
                                triggerCrispHaptic(context)
                                glidingTile = curTile
                                if (toggleSelectNode != null) toggleSelectNode?.invoke(curTile.node, curTile.path)
                                else selectTile(curTile, change.position)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxSize().background(canvasBg)) {
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

        // Progressive blur bound to the treemap transform: only perimeter tiles contribute,
        // so the margin continues the tile colours without a static image or per-tile overlay.
        if (precalculatedTiles.value.isNotEmpty() && glowProgress > 0f) {
            Canvas(modifier = Modifier.fillMaxSize().blur(18.dp)) {
                val curScale = currentScaleState.value
                val curOff = currentOffsetState.value
                // Glow fills from the treemap border to the device edges; no base surface shows.
                val glowAlpha = 0.85f * glowProgress
                val overlap = 4f * curScale
                val eps = 4f
                val rightBound = canvasSize.width.toFloat() - rightSpacePx
                val bottomBound = canvasSize.height.toFloat() - bottomSpacePx

                for (tile in precalculatedTiles.value) {
                    val isTop = tile.top <= topSpacePx + eps
                    val isBottom = tile.top + tile.height >= bottomBound - eps
                    val isLeft = tile.left <= leftSpacePx + eps
                    val isRight = tile.left + tile.width >= rightBound - eps
                    if (!isTop && !isBottom && !isLeft && !isRight) continue

                    val sLeft = tile.left * curScale + curOff.x
                    val sTop = tile.top * curScale + curOff.y
                    val sW = tile.width * curScale
                    val sH = tile.height * curScale
                    if (sLeft + sW < -overlap || sLeft > size.width + overlap ||
                        sTop + sH < -overlap || sTop > size.height + overlap) continue

                    val color = tile.baseColor.copy(alpha = glowAlpha)
                    // Edge tiles extend their colour to the screen edge; corners cover the gaps.
                    val t = (sTop + overlap).coerceAtLeast(0f)
                    val b = (sTop + sH - overlap).coerceAtMost(size.height)
                    val l = (sLeft + overlap).coerceAtLeast(0f)
                    val r = (sLeft + sW - overlap).coerceAtMost(size.width)

                    if (isTop && t > 0f) {
                        drawRect(color, Offset(sLeft.coerceAtLeast(0f), 0f), Size((sW + overlap).coerceAtLeast(1f), t))
                    }
                    if (isBottom && b < size.height) {
                        drawRect(color, Offset(sLeft.coerceAtLeast(0f), b), Size((sW + overlap).coerceAtLeast(1f), size.height - b))
                    }
                    if (isLeft && l > 0f) {
                        drawRect(color, Offset(0f, sTop.coerceAtLeast(0f)), Size(l, (sH + overlap).coerceAtLeast(1f)))
                    }
                    if (isRight && r < size.width) {
                        drawRect(color, Offset(r, sTop.coerceAtLeast(0f)), Size(size.width - r, (sH + overlap).coerceAtLeast(1f)))
                    }
                    if (isTop && isLeft) drawRect(color, Offset(0f, 0f), Size(l, t))
                    if (isTop && isRight) drawRect(color, Offset(r, 0f), Size(size.width - r, t))
                    if (isBottom && isLeft) drawRect(color, Offset(0f, b), Size(l, size.height - b))
                    if (isBottom && isRight) drawRect(color, Offset(r, b), Size(size.width - r, size.height - b))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            val hasMarkedNodes = selectedNodes.isNotEmpty()

            if (hasMarkedNodes) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val curScale = currentScaleState.value
                    val curOff = currentOffsetState.value

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
                                color = primaryColor.copy(alpha = 0.35f),
                                topLeft = Offset(sLeft, sTop),
                                size = Size(drawW, drawH)
                            )
                            drawRect(
                                color = primaryColor,
                                topLeft = Offset(sLeft + halfStroke, sTop + halfStroke),
                                size = Size(maxOf(1f, drawW - strokeW), maxOf(1f, drawH - strokeW)),
                                style = Stroke(width = strokeW)
                            )
                        }
                    }
                }
            }
        }

        // Status bar subtle scrim for icon legibility
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        if (statusBarHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeight + 12.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                surfaceColor.copy(alpha = 0.40f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Navigation bar subtle scrim for gesture bar legibility
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        if (navBarHeight > 0.dp && !isLandscape) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navBarHeight + 16.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                surfaceColor.copy(alpha = 0.45f)
                            )
                        )
                    )
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

