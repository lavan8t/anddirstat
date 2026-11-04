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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.ui.components.AppIconCache
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    val density = LocalDensity.current
    val marginDp = 10.dp
    val marginPx = with(density) { marginDp.toPx() }

    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topSpaceDp = if (isLandscape) 48.dp else statusBarTopDp + 52.dp + marginDp
    val topSpacePx = with(density) { topSpaceDp.toPx() }

    val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpaceDp = if (isLandscape) 12.dp else navBarBottomDp + 64.dp + marginDp
    val bottomSpacePx = with(density) { bottomSpaceDp.toPx() }

    val leftSpaceDp = if (isLandscape) 84.dp else marginDp
    val leftSpacePx = with(density) { leftSpaceDp.toPx() }

    val rightSpaceDp = marginDp
    val rightSpacePx = with(density) { rightSpaceDp.toPx() }

    val cacheKey = "${System.identityHashCode(rootNode)}_${canvasSize.width}_${canvasSize.height}_${isDark}_${isLandscape}_${topSpacePx}_${bottomSpacePx}_${leftSpacePx}_${rightSpacePx}"
    val precalculatedTiles = remember { mutableStateOf<List<TreemapTile>>(emptyList()) }
    val quadtree = remember { mutableStateOf<TreemapQuadtree?>(null) }

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

    val nodeToTileMap = remember(precalculatedTiles.value) {
        precalculatedTiles.value.associateBy { it.node }
    }

    val selectedTile = remember(selectedNode, nodeToTileMap) {
        if (selectedNode == null) null else nodeToTileMap[selectedNode]
    }

    val glRenderer = remember { TreemapGLRenderer(context) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

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

    val ambientBlobs = remember(precalculatedTiles.value, isDark, canvasSize, topSpacePx, bottomSpacePx, leftSpacePx, rightSpacePx) {
        val tiles = precalculatedTiles.value
        if (tiles.isEmpty()) return@remember emptyList<AmbientBlob>()

        val colorAreaMap = HashMap<Color, Float>()
        for (tile in tiles) {
            val a = tile.width * tile.height
            colorAreaMap[tile.baseColor] = (colorAreaMap[tile.baseColor] ?: 0f) + a
        }
        val topColors = colorAreaMap.entries
            .sortedByDescending { it.value }
            .map { it.key }

        if (topColors.isEmpty()) return@remember emptyList<AmbientBlob>()

        val usableW = (canvasSize.width.toFloat() - leftSpacePx - rightSpacePx).coerceAtLeast(10f)
        val usableH = (canvasSize.height.toFloat() - topSpacePx - bottomSpacePx).coerceAtLeast(10f)
        val midX = leftSpacePx + usableW / 2f
        val midY = topSpacePx + usableH / 2f

        fun dominantColorInRegion(predicate: (TreemapTile) -> Boolean, fallback: Color): Color {
            val regionMap = HashMap<Color, Float>()
            for (tile in tiles) {
                if (predicate(tile)) {
                    val a = tile.width * tile.height
                    regionMap[tile.baseColor] = (regionMap[tile.baseColor] ?: 0f) + a
                }
            }
            return regionMap.maxByOrNull { it.value }?.key ?: fallback
        }

        val cTopLeft = dominantColorInRegion({ it.left < midX && it.top < midY }, topColors[0])
        val cTopRight = dominantColorInRegion({ it.left >= midX && it.top < midY }, topColors.getOrElse(1 % topColors.size) { topColors[0] })
        val cBottomLeft = dominantColorInRegion({ it.left < midX && it.top >= midY }, topColors.getOrElse(2 % topColors.size) { topColors[0] })
        val cBottomRight = dominantColorInRegion({ it.left >= midX && it.top >= midY }, topColors.getOrElse(3 % topColors.size) { topColors[0] })

        val cTopCenter = dominantColorInRegion({ it.top <= topSpacePx + usableH * 0.35f }, topColors[0])
        val cBottomCenter = dominantColorInRegion({ it.top + it.height >= (topSpacePx + usableH) - usableH * 0.35f }, topColors.getOrElse(1 % topColors.size) { cBottomRight })
        val cLeftCenter = dominantColorInRegion({ it.left <= leftSpacePx + usableW * 0.35f }, topColors.getOrElse(2 % topColors.size) { cBottomLeft })
        val cRightCenter = dominantColorInRegion({ it.left + it.width >= (leftSpacePx + usableW) - usableW * 0.35f }, topColors.getOrElse(3 % topColors.size) { cTopRight })

        listOf(
            // Top halo blobs (around top margin, filter pill, and status bar)
            AmbientBlob(relX = 0.05f, relY = 0.0f, offsetY = (-24).dp, radiusDp = 100.dp, color = cTopLeft),
            AmbientBlob(relX = 0.35f, relY = 0.0f, offsetY = (-32).dp, radiusDp = 110.dp, color = cTopCenter),
            AmbientBlob(relX = 0.65f, relY = 0.0f, offsetY = (-30).dp, radiusDp = 105.dp, color = topColors.getOrElse(4 % topColors.size) { cTopRight }),
            AmbientBlob(relX = 0.95f, relY = 0.0f, offsetY = (-24).dp, radiusDp = 100.dp, color = cTopRight),

            // Bottom halo blobs (around bottom margin and navigation pill)
            AmbientBlob(relX = 0.05f, relY = 1.0f, offsetY = 32.dp, radiusDp = 110.dp, color = cBottomLeft),
            AmbientBlob(relX = 0.35f, relY = 1.0f, offsetY = 40.dp, radiusDp = 120.dp, color = cBottomCenter),
            AmbientBlob(relX = 0.65f, relY = 1.0f, offsetY = 38.dp, radiusDp = 115.dp, color = topColors.getOrElse(5 % topColors.size) { cBottomRight }),
            AmbientBlob(relX = 0.95f, relY = 1.0f, offsetY = 32.dp, radiusDp = 110.dp, color = cBottomRight),

            // Left margin halo blobs
            AmbientBlob(relX = 0.0f, relY = 0.30f, offsetX = (-16).dp, radiusDp = 85.dp, color = cLeftCenter),
            AmbientBlob(relX = 0.0f, relY = 0.70f, offsetX = (-16).dp, radiusDp = 85.dp, color = cBottomLeft),

            // Right margin halo blobs
            AmbientBlob(relX = 1.0f, relY = 0.30f, offsetX = 16.dp, radiusDp = 85.dp, color = cTopRight),
            AmbientBlob(relX = 1.0f, relY = 0.70f, offsetX = 16.dp, radiusDp = 85.dp, color = cRightCenter)
        )
    }

    // Material 3 color tokens: zero hardcoded colors
    val surfaceColor = MaterialTheme.colorScheme.surface
    val canvasBg = if (pureBlack && isDark) Color.Black else surfaceColor

    LaunchedEffect(canvasBg, shadingMode) {
        glRenderer.setClearColor(0f, 0f, 0f, 0f)
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
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
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
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "selectionBorderWidth"
    )

    var glidingTile by remember { mutableStateOf<TreemapTile?>(null) }
    var glidingTouchPos by remember { mutableStateOf<Offset?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val gestureModifier = if (isInteractive) {
        Modifier.pointerInput(precalculatedTiles.value, quadtree.value) {
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
                            if (initialTile != null) {
                                triggerCrispHaptic(context)
                                if (selectedNodes.isNotEmpty() && onToggleSelectNode != null) {
                                    onToggleSelectNode(initialTile.node, initialTile.path)
                                } else {
                                    selectTile(initialTile, downPos)
                                }
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
                                selectTile(initialTile, change.position)
                            }
                        } else {
                            if (curTile != null && curTile != glidingTile) {
                                triggerCrispHaptic(context)
                                glidingTile = curTile
                                selectTile(curTile, change.position)
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

        // Organic ambient blur blobs radiating all around the treemap perimeter, moving seamlessly with the canvas
        if (ambientBlobs.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(36.dp)
            ) {
                val curScale = currentScaleState.value
                val curOff = currentOffsetState.value

                val tmLeft = leftSpacePx * curScale + curOff.x
                val tmTop = topSpacePx * curScale + curOff.y
                val rightBound = canvasSize.width.toFloat() - rightSpacePx
                val bottomBound = canvasSize.height.toFloat() - bottomSpacePx
                val tmRight = rightBound * curScale + curOff.x
                val tmBottom = bottomBound * curScale + curOff.y
                val tmWidth = tmRight - tmLeft
                val tmHeight = tmBottom - tmTop

                val inset = 12.dp.toPx() * curScale
                val clipLeft = tmLeft + inset
                val clipTop = tmTop + inset
                val clipRight = tmRight - inset
                val clipBottom = tmBottom - inset

                if (clipRight > clipLeft && clipBottom > clipTop) {
                    clipRect(
                        left = clipLeft,
                        top = clipTop,
                        right = clipRight,
                        bottom = clipBottom,
                        clipOp = ClipOp.Difference
                    ) {
                        for (blob in ambientBlobs) {
                            val blobX = tmLeft + blob.relX * tmWidth + blob.offsetX.toPx() * curScale
                            val blobY = tmTop + blob.relY * tmHeight + blob.offsetY.toPx() * curScale
                            val radiusPx = blob.radiusDp.toPx() * curScale.coerceIn(0.7f, 1.4f)
                            val center = Offset(blobX, blobY)

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        blob.color.copy(alpha = if (isDark) 0.70f else 0.50f),
                                        blob.color.copy(alpha = if (isDark) 0.35f else 0.22f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = radiusPx
                                ),
                                radius = radiusPx,
                                center = center
                            )
                        }
                    }
                } else {
                    for (blob in ambientBlobs) {
                        val blobX = tmLeft + blob.relX * tmWidth + blob.offsetX.toPx() * curScale
                        val blobY = tmTop + blob.relY * tmHeight + blob.offsetY.toPx() * curScale
                        val radiusPx = blob.radiusDp.toPx() * curScale.coerceIn(0.7f, 1.4f)
                        val center = Offset(blobX, blobY)

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    blob.color.copy(alpha = if (isDark) 0.70f else 0.50f),
                                    blob.color.copy(alpha = if (isDark) 0.35f else 0.22f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = radiusPx
                            ),
                            radius = radiusPx,
                            center = center
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
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
                            color = onSurfaceColor,
                            topLeft = Offset(inLeft, inTop),
                            size = Size(inW, inH),
                            style = Stroke(width = selectionBorderWidth)
                        )
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

private data class AmbientBlob(
    val relX: Float,
    val relY: Float,
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = 0.dp,
    val radiusDp: Dp,
    val color: Color
)
