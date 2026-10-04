package com.kd.anddirstat.treemap

import android.app.ActivityManager
import android.content.ComponentCallbacks2
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.CompositingStrategy
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

private const val BOTTOM_BLUR_RADIUS_DP = 30f

/**
 * Bottom fade blur, tiered by platform capability.
 *
 * API 33+ gets a true progressive blur: radius ramps 0 -> [BOTTOM_BLUR_RADIUS_DP] top to
 * bottom, so the extrusion stays sharp where it meets the treemap and softens as it descends
 * behind the nav pill. API 31-32 only support a uniform radius, so it gets a flat blur.
 * Below 31 RenderEffect is unavailable and the modifier is a silent no-op, so we skip it
 * entirely rather than stacking an unblurred duplicate over the sharp copy.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun progressiveBottomBlur(): Modifier = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        Modifier.blur(
            BlurRadiusSpec.verticalGradient(
                startRadius = 0.dp,
                endRadius = BOTTOM_BLUR_RADIUS_DP.dp
            )
        )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        Modifier.blur(BOTTOM_BLUR_RADIUS_DP.dp)
    else -> Modifier
}
private const val ICON_PREFETCH_BATCH = 8

object TreemapBitmapCache {
    private const val BYTES_PER_PIXEL = 4
    private const val HEAP_FRACTION_DIVISOR = 8
    private const val MIN_FULLSCREEN_ENTRIES = 2
    private const val FALLBACK_MEMORY_CLASS_MB = 128
    private const val PRECONFIGURE_BUDGET_BYTES = 16 * 1024 * 1024

    // Sized in real pixel bytes, not entries: one full-screen ARGB_8888 snapshot is ~10 MB,
    // so an entry count of 8 was really an ~83 MB unbounded heap hold.
    @Volatile
    private var budgetBytes = PRECONFIGURE_BUDGET_BYTES

    private val cache = object : LruCache<String, ImageBitmap>(PRECONFIGURE_BUDGET_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width.toLong() * value.height.toLong() * BYTES_PER_PIXEL)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
    }

    // An eighth of the app heap, floored at two full-screen entries so rotation or a theme
    // flip hits the cache instead of re-rendering the snapshot.
    fun configure(context: Context) {
        val metrics = context.resources.displayMetrics
        val fullScreenBytes = metrics.widthPixels.toLong() * metrics.heightPixels.toLong() * BYTES_PER_PIXEL
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val heapBytes = ((activityManager?.memoryClass ?: FALLBACK_MEMORY_CLASS_MB).toLong()) * 1024L * 1024L
        budgetBytes = maxOf(heapBytes / HEAP_FRACTION_DIVISOR, fullScreenBytes * MIN_FULLSCREEN_ENTRIES)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        restoreBudget()
    }

    fun get(key: String): ImageBitmap? {
        restoreBudget()
        return cache.get(key)
    }

    fun put(key: String, bitmap: ImageBitmap) {
        restoreBudget()
        cache.put(key, bitmap)
    }

    fun clear() { cache.evictAll() }

    // Levels are ComponentCallbacks2.TRIM_MEMORY_*; must be forwarded from the Activity because
    // this object outlives Activity destruction and configuration changes. Eviction is done by
    // shrinking the budget, which the next get/put restores once pressure has passed.
    fun trim(level: Int) {
        val current = cache.maxSize()
        val reduced = when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> 0
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> current / 2
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> current / 2
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> current / 4
            else -> return
        }
        if (reduced < current) cache.resize(reduced)
    }

    private fun restoreBudget() {
        if (cache.maxSize() != budgetBytes) cache.resize(budgetBytes)
    }
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
    val shadingMode = FileUtils.getTreemapShadingMode(context)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    val bgColor = if (isAmoled) android.graphics.Color.BLACK else if (isDark) 0xFF08090E.toInt() else 0xFFF1F3F9.toInt()
    canvas.drawColor(bgColor)

    val linearStops = floatArrayOf(0f, 0.45f, 1.0f)
    val cushionStops = floatArrayOf(0f, 0.50f, 1.0f)
    val shaderMatrix = android.graphics.Matrix()

    for (tile in tiles) {
        val sLeft = tile.left
        val sTop = tile.top
        val sW = maxOf(1f, tile.width)
        val sH = maxOf(1f, tile.height)

        val appIcon = if (tile.pkgName != null && sW >= 24f && sH >= 24f) {
            AppIconCache.peek(tile.pkgName)
        } else null

        val tileBaseColor = tile.baseColor

        if (isAmoled) {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = tileBaseColor.toArgb()
            paint.shader = null
            canvas.drawRect(sLeft, sTop, sLeft + sW, sTop + sH, paint)
        } else if (shadingMode == FileUtils.TreemapShadingMode.SOLID || sW < 3f || sH < 3f) {
            paint.style = android.graphics.Paint.Style.FILL
            paint.shader = null
            paint.color = tileBaseColor.toArgb()
            canvas.drawRect(sLeft, sTop, sLeft + sW, sTop + sH, paint)
        } else {
            val cHighlight = tile.gradientColors[0].toArgb()
            val cBase = tile.gradientColors[1].toArgb()
            val cShadow = tile.gradientColors[2].toArgb()

            val shader = if (shadingMode == FileUtils.TreemapShadingMode.LINEAR) {
                android.graphics.LinearGradient(
                    sLeft, sTop, sLeft + sW, sTop + sH,
                    intArrayOf(cHighlight, cBase, cShadow),
                    linearStops,
                    android.graphics.Shader.TileMode.CLAMP
                )
            } else {
                // Exact Cushion Treemap (CTM) Shading model (van Wijk & van de Wetering):
                // Directional light vector L = [1, 2, 10] -> offset center (38% X, 35% Y)
                // Gradient stretches and squeezes with the exact tile aspect ratio:
                val radShader = android.graphics.RadialGradient(
                    0.38f, 0.35f, 0.85f,
                    intArrayOf(cHighlight, cBase, cShadow),
                    cushionStops,
                    android.graphics.Shader.TileMode.CLAMP
                )
                radShader.setLocalMatrix(shaderMatrix.apply {
                    setScale(sW, sH)
                    postTranslate(sLeft, sTop)
                })
                radShader
            }
            paint.style = android.graphics.Paint.Style.FILL
            paint.shader = shader
            canvas.drawRect(sLeft, sTop, sLeft + sW, sTop + sH, paint)
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
) {
    // Both shaders live in the tile's own 0..1 space; the draw pass maps that unit rect onto
    // the tile with a canvas transform, so one brush per tile is valid at any zoom or pan.
    val cushionBrush: Brush = Brush.radialGradient(
        colors = gradientColors,
        center = Offset(0.38f, 0.35f),
        radius = 0.85f
    )
    val linearBrush: Brush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset.Zero,
        end = Offset(1f, 1f)
    )
}

// Canonical treemap shading: the highlight/shadow pair the renderers have always drawn, so
// every layout branch and both render paths agree and cannot drift apart again.
private fun tileGradientColors(baseColor: Color, isDark: Boolean): List<Color> {
    val highlightScale = if (isDark) 1.65f else 1.75f
    val highlightBias = if (isDark) 0.25f else 0.35f
    val shadowScale = if (isDark) 0.45f else 0.40f
    return listOf(
        Color(
            red = (baseColor.red * highlightScale + highlightBias).coerceIn(0f, 1f),
            green = (baseColor.green * highlightScale + highlightBias).coerceIn(0f, 1f),
            blue = (baseColor.blue * highlightScale + highlightBias).coerceIn(0f, 1f),
            alpha = 1f
        ),
        baseColor,
        Color(
            red = (baseColor.red * shadowScale).coerceIn(0f, 1f),
            green = (baseColor.green * shadowScale).coerceIn(0f, 1f),
            blue = (baseColor.blue * shadowScale).coerceIn(0f, 1f),
            alpha = 1f
        )
    )
}

// Uniform grid hit test. Each cell keeps tile indices in original layout order, and a query
// walks its cell backwards, so the winner is the last tile containing the point - identical to
// the findLast this replaces, which relied on later tiles being painted over earlier ones.
private class TileHitIndex(
    private val tiles: List<TreemapTile>,
    private val cellSize: Float = 64f
) {
    private val cols: Int
    private val rows: Int
    private val cellStarts: IntArray
    private val cellItems: IntArray

    init {
        var maxRight = 0f
        var maxBottom = 0f
        for (tile in tiles) {
            val right = tile.left + tile.width
            val bottom = tile.top + tile.height
            if (right > maxRight) maxRight = right
            if (bottom > maxBottom) maxBottom = bottom
        }
        cols = (maxRight / cellSize).toInt() + 1
        rows = (maxBottom / cellSize).toInt() + 1

        val cellCount = cols * rows
        val bounds = IntArray(tiles.size * 4)
        val counts = IntArray(cellCount)
        for (i in tiles.indices) {
            val tile = tiles[i]
            val c0 = clampCol(tile.left)
            val c1 = clampCol(tile.left + tile.width)
            val r0 = clampRow(tile.top)
            val r1 = clampRow(tile.top + tile.height)
            val o = i * 4
            bounds[o] = r0
            bounds[o + 1] = r1
            bounds[o + 2] = c0
            bounds[o + 3] = c1
            for (r in r0..r1) {
                for (c in c0..c1) counts[r * cols + c]++
            }
        }
        cellStarts = IntArray(cellCount + 1)
        for (cell in 0 until cellCount) cellStarts[cell + 1] = cellStarts[cell] + counts[cell]
        cellItems = IntArray(cellStarts[cellCount])
        val cursor = cellStarts.copyOf()
        for (i in tiles.indices) {
            val o = i * 4
            for (r in bounds[o]..bounds[o + 1]) {
                for (c in bounds[o + 2]..bounds[o + 3]) cellItems[cursor[r * cols + c]++] = i
            }
        }
    }

    private fun clampCol(x: Float): Int = (x / cellSize).toInt().coerceIn(0, cols - 1)
    private fun clampRow(y: Float): Int = (y / cellSize).toInt().coerceIn(0, rows - 1)

    fun tileAt(x: Float, y: Float): TreemapTile? {
        val cell = clampRow(y) * cols + clampCol(x)
        for (k in cellStarts[cell + 1] - 1 downTo cellStarts[cell]) {
            val tile = tiles[cellItems[k]]
            if (x >= tile.left && x <= tile.left + tile.width &&
                y >= tile.top && y <= tile.top + tile.height
            ) {
                return tile
            }
        }
        return null
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
            val gradientColors = tileGradientColors(baseColor, isDark)
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
            val gradientColors = tileGradientColors(baseColor, isDark)
            tiles.add(TreemapTile(node, currentPath, l, t, w, h, isApp, pkg, baseColor, gradientColors))
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
    remember(context) { TreemapBitmapCache.configure(context) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val currentScaleState = rememberUpdatedState(scale)
    val currentOffsetState = rememberUpdatedState(offset)

    val cacheKey = "${System.identityHashCode(rootNode)}_${canvasSize.width}_${canvasSize.height}_${isDark}_$isLandscape"
    var iconWarmTick by remember { mutableStateOf(0) }
    val precalculatedTiles = remember(cacheKey, bottomSpacePx, iconWarmTick) {
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

    val tileHitIndex = remember(precalculatedTiles) { TileHitIndex(precalculatedTiles) }

    // Marked state is resolved once per selection change, not once per tile per frame.
    val markedTileFlags = remember(precalculatedTiles, selectedNodes) {
        val flags = BooleanArray(precalculatedTiles.size)
        val markedNodes = HashSet(selectedNodes)
        for (i in precalculatedTiles.indices) {
            if (markedNodes.contains(precalculatedTiles[i].node)) flags[i] = true
        }
        flags
    }

    // The scanner warms app icons off the main thread; this only covers tiles that still have
    // none. Drawing never loads, so an icon can only appear on a later frame, never mid-draw.
    LaunchedEffect(precalculatedTiles, iconWarmTick) {
        val missingIcons = LinkedHashSet<String>()
        for (tile in precalculatedTiles) {
            val pkg = tile.pkgName
            if (pkg != null && AppIconCache.peek(pkg) == null) missingIcons.add(pkg)
        }
        if (missingIcons.isEmpty()) return@LaunchedEffect
        val pending = missingIcons.toList()
        var loaded = 0
        for (start in pending.indices step ICON_PREFETCH_BATCH) {
            val batch = pending.subList(start, minOf(start + ICON_PREFETCH_BATCH, pending.size))
            val results = coroutineScope { batch.map { async { AppIconCache.load(context, it) } }.awaitAll() }
            loaded += results.count { it != null }
        }
        if (loaded > 0) iconWarmTick++
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
                    val hitIndex = tileHitIndex
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val downPos = down.position

                        fun getTileAt(touchOffset: Offset): TreemapTile? {
                            val curScale = currentScaleState.value
                            val curOff = currentOffsetState.value
                            val touchX = (touchOffset.x - curOff.x) / curScale
                            val touchY = (touchOffset.y - curOff.y) / curScale
                            return hitIndex.tileAt(touchX, touchY)
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
                val useCushion = shadingMode == FileUtils.TreemapShadingMode.CUSHION

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

                        val tileBaseColor = tile.baseColor

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
                            drawContext.canvas.save()
                            drawContext.transform.translate(sLeft, sTop)
                            drawContext.transform.scale(drawW, drawH, pivot = Offset.Zero)
                            drawRect(
                                brush = if (useCushion) tile.cushionBrush else tile.linearBrush,
                                topLeft = Offset.Zero,
                                size = Size(1f, 1f)
                            )
                            drawContext.canvas.restore()
                        }

                        if (markedTileFlags[i]) {
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
                            val bmp = AppIconCache.peek(tile.pkgName)
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

        // Bottom nav-pill zone: sharp tile extrusion -> progressively blurred copy -> fade to bg.
        // The blurred copy is filled with the tiles' cached gradient brushes rather than flat
        // base colours, because a blur over flat colour blocks is a visual no-op.
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
                                color = tile.baseColor,
                                topLeft = Offset(tile.left * curScale + curOff.x, 0f),
                                size = Size(maxOf(1f, tile.width * curScale), size.height)
                            )
                        }
                    }
                    // Gradient-filled variant: gives the blur real detail to work with.
                    val drawGradientColumns: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                        val curScale = currentScaleState.value
                        val curOff = currentOffsetState.value
                        for (tile in bottomTiles) {
                            val w = maxOf(1f, tile.width * curScale)
                            drawRect(
                                brush = tile.cushionBrush,
                                topLeft = Offset(tile.left * curScale + curOff.x, 0f),
                                size = Size(w, size.height)
                            )
                        }
                    }
                    Canvas(Modifier.fillMaxSize(), onDraw = drawColumns)
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .then(progressiveBottomBlur()),
                        onDraw = drawGradientColumns
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
        // Videos - Blues family
        "mp4" -> if (isDark) Color(0xFF1565C0) else Color(0xFF1976D2)
        "mkv" -> if (isDark) Color(0xFF0D47A1) else Color(0xFF1565C0)
        "avi" -> if (isDark) Color(0xFF1976D2) else Color(0xFF2196F3)
        "mov" -> if (isDark) Color(0xFF0277BD) else Color(0xFF039BE5)
        "webm" -> if (isDark) Color(0xFF283593) else Color(0xFF3F51B5)
        "flv" -> if (isDark) Color(0xFF303F9F) else Color(0xFF5C6BC0)
        "3gp" -> if (isDark) Color(0xFF1A237E) else Color(0xFF3949AB)
        "ts" -> if (isDark) Color(0xFF01579B) else Color(0xFF0288D1)
        "wmv" -> if (isDark) Color(0xFF006064) else Color(0xFF00838F)
        "m4v" -> if (isDark) Color(0xFF311B92) else Color(0xFF4527A0)
        "mpg", "mpeg", "vob" -> if (isDark) Color(0xFF1E88E5) else Color(0xFF42A5F5)

        // Audio - Purples & Violets family
        "mp3" -> if (isDark) Color(0xFF6A1B9A) else Color(0xFF8E24AA)
        "flac" -> if (isDark) Color(0xFF4A148C) else Color(0xFF6A1B9A)
        "wav" -> if (isDark) Color(0xFF7B1FA2) else Color(0xFF9C27B0)
        "m4a" -> if (isDark) Color(0xFF8E24AA) else Color(0xFFAB47BC)
        "ogg" -> if (isDark) Color(0xFF512DA8) else Color(0xFF673AB7)
        "aac" -> if (isDark) Color(0xFF5E35B1) else Color(0xFF7E57C2)
        "opus" -> if (isDark) Color(0xFF4527A0) else Color(0xFF5E35B1)
        "wma" -> if (isDark) Color(0xFF7E57C2) else Color(0xFF9575CD)
        "mid", "midi" -> if (isDark) Color(0xFFBA68C8) else Color(0xFFCE93D8)
        "alac", "amr" -> if (isDark) Color(0xFF6200EA) else Color(0xFF7C4DFF)

        // Images - Oranges & Ambers family
        "jpg", "jpeg" -> if (isDark) Color(0xFFE65100) else Color(0xFFF57C00)
        "png" -> if (isDark) Color(0xFFD84315) else Color(0xFFFF5722)
        "webp" -> if (isDark) Color(0xFFF57C00) else Color(0xFFFF9800)
        "heic", "heif" -> if (isDark) Color(0xFFBF360C) else Color(0xFFE64A19)
        "gif" -> if (isDark) Color(0xFFFF6F00) else Color(0xFFFFA000)
        "svg" -> if (isDark) Color(0xFFE64A19) else Color(0xFFFF7043)
        "raw", "dng", "cr2", "nef" -> if (isDark) Color(0xFFEF6C00) else Color(0xFFFB8C00)
        "bmp", "ico" -> if (isDark) Color(0xFFFF8F00) else Color(0xFFFFB300)

        // Documents - Greens & Emeralds family
        "pdf" -> if (isDark) Color(0xFF1B5E20) else Color(0xFF2E7D32)
        "doc", "docx" -> if (isDark) Color(0xFF2E7D32) else Color(0xFF388E3C)
        "txt", "log", "rtf" -> if (isDark) Color(0xFF388E3C) else Color(0xFF43A047)
        "xlsx", "xls" -> if (isDark) Color(0xFF004D40) else Color(0xFF00695C)
        "pptx", "ppt" -> if (isDark) Color(0xFF00695C) else Color(0xFF00796B)
        "csv" -> if (isDark) Color(0xFF33691E) else Color(0xFF558B2F)
        "epub", "mobi" -> if (isDark) Color(0xFF558B2F) else Color(0xFF689F38)

        // Archives - Teals & Cyans family
        "zip" -> if (isDark) Color(0xFF006064) else Color(0xFF00838F)
        "rar" -> if (isDark) Color(0xFF00838F) else Color(0xFF0097A7)
        "7z" -> if (isDark) Color(0xFF004D40) else Color(0xFF00695C)
        "tar" -> if (isDark) Color(0xFF00796B) else Color(0xFF00897B)
        "gz", "tgz" -> if (isDark) Color(0xFF0097A7) else Color(0xFF00ACC1)
        "bz2", "xz" -> if (isDark) Color(0xFF00838F) else Color(0xFF00BCD4)
        "iso", "img", "dmg" -> if (isDark) Color(0xFF004D40) else Color(0xFF00796B)

        // Code & Data - Yellows, Golds & Warm Oranges
        "kt", "kts" -> if (isDark) Color(0xFFF57F17) else Color(0xFFFBC02D)
        "java" -> if (isDark) Color(0xFFF9A825) else Color(0xFFFDD835)
        "py" -> if (isDark) Color(0xFFE65100) else Color(0xFFEF6C00)
        "js", "mjs", "cjs" -> if (isDark) Color(0xFFFF8F00) else Color(0xFFFFA000)
        "ts", "tsx" -> if (isDark) Color(0xFF0277BD) else Color(0xFF039BE5)
        "html", "htm" -> if (isDark) Color(0xFFE64A19) else Color(0xFFFF5722)
        "css", "scss" -> if (isDark) Color(0xFF0288D1) else Color(0xFF29B6F6)
        "json" -> if (isDark) Color(0xFFFFB300) else Color(0xFFFFCA28)
        "xml" -> if (isDark) Color(0xFFFF8F00) else Color(0xFFFFB300)
        "yaml", "yml" -> if (isDark) Color(0xFFF57F17) else Color(0xFFFFEE58)
        "c", "cpp", "h", "hpp" -> if (isDark) Color(0xFF0D47A1) else Color(0xFF1976D2)
        "md" -> if (isDark) Color(0xFF6D4C41) else Color(0xFF8D6E63)

        // Packages - Crimsons & Roses family
        "apk" -> if (isDark) Color(0xFFB71C1C) else Color(0xFFD32F2F)
        "apks", "xapk", "apkm", "aab" -> if (isDark) Color(0xFF880E4F) else Color(0xFFC2185B)
        "obb" -> if (isDark) Color(0xFFAD1457) else Color(0xFFD81B60)

        // Binaries / Runtimes - Deep Browns & Maroons
        "so" -> if (isDark) Color(0xFF4A148C) else Color(0xFF7B1FA2)
        "dex" -> if (isDark) Color(0xFF3E2723) else Color(0xFF5D4037)
        "bin", "exe", "dll" -> if (isDark) Color(0xFF4E342E) else Color(0xFF6D4C41)
        "jar", "class" -> if (isDark) Color(0xFF5D4037) else Color(0xFF795548)

        else -> {
            val hash = abs(ext.hashCode())
            val hue = (hash * 137.50777f) % 360f
            Color.hsl(hue = hue, saturation = 0.65f, lightness = if (isDark) 0.28f else 0.50f)
        }
    }
    cache.put(ext, computed)
    return computed
}
