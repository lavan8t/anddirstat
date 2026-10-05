package com.kd.anddirstat.treemap

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kd.anddirstat.ui.components.AppIconCache
import com.kd.anddirstat.util.FileUtils

object TreemapBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(2)
    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bitmap: ImageBitmap) { cache.put(key, bitmap) }
    fun clear() { cache.evictAll() }
    fun trim(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            clear()
        }
    }
}

private val MarkFill = Color(0x6600E676)
private val MarkStroke = Color(0xFF00E676)

/** Base colour + shading for a tile; icon dominant colour may arrive after layout. */
private fun tileShade(tile: TreemapTile, isDark: Boolean): List<Color> {
    val dom = tile.pkgName?.let { AppIconCache.getDominantColor(it) }
    return if (dom == null || dom == tile.baseColor) tile.gradientColors else shadeColors(dom, isDark)
}

fun renderTreemapToBitmap(
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


    for (tile in tiles) {
        val sLeft = tile.left
        val sTop = tile.top
        val sW = maxOf(1f, tile.width)
        val sH = maxOf(1f, tile.height)

        val appIcon = if (tile.pkgName != null && sW >= 24f && sH >= 24f) {
            AppIconCache.peek(tile.pkgName)
        } else null

        val colors = tileShade(tile, isDark)
        val tileBaseColor = colors[1]

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
            val argb = intArrayOf(colors[0].toArgb(), colors[1].toArgb(), colors[2].toArgb())
            val shader = if (shadingMode == FileUtils.TreemapShadingMode.LINEAR) {
                android.graphics.LinearGradient(
                    sLeft, sTop, sLeft + sW, sTop + sH,
                    argb,
                    floatArrayOf(0f, 0.45f, 1.0f),
                    android.graphics.Shader.TileMode.CLAMP
                )
            } else {
                // Exact Cushion Treemap (CTM) Shading model (van Wijk & van de Wetering):
                // Directional light vector L = [1, 2, 10] -> offset center (38% X, 35% Y)
                // Gradient stretches and squeezes with the exact tile aspect ratio:
                val radShader = android.graphics.RadialGradient(
                    0.38f, 0.35f, 0.85f,
                    argb,
                    floatArrayOf(0f, 0.50f, 1.0f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                val matrix = android.graphics.Matrix()
                matrix.setScale(sW, sH)
                matrix.postTranslate(sLeft, sTop)
                radShader.setLocalMatrix(matrix)
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

/** Live vector draw (zoom/pan) with frustum culling. */
fun DrawScope.drawTilesLive(
    tiles: List<TreemapTile>,
    scale: Float,
    offset: Offset,
    selectedNodes: Set<com.kd.anddirstat.model.CompactNode>,
    shadingMode: FileUtils.TreemapShadingMode,
    isDark: Boolean,
    isAmoled: Boolean,
    context: Context
) {
    val viewW = size.width
    val viewH = size.height
    val amoledStroke = Stroke(width = if (scale > 1.5f) 2.5f else 1.5f)
    for (i in tiles.indices) {
        val tile = tiles[i]
        val sLeft = tile.left * scale + offset.x
        val sTop = tile.top * scale + offset.y
        val sW = tile.width * scale
        val sH = tile.height * scale
        if (sLeft + sW < 0f || sLeft > viewW || sTop + sH < 0f || sTop > viewH) continue

        val drawW = maxOf(1f, sW)
        val drawH = maxOf(1f, sH)
        val colors = tileShade(tile, isDark)
        val base = colors[1]
        val tl = Offset(sLeft, sTop)
        val sz = Size(drawW, drawH)

        if (isAmoled) {
            drawRect(color = base, topLeft = tl, size = sz, style = amoledStroke)
        } else if (shadingMode == FileUtils.TreemapShadingMode.SOLID || drawW < 3f || drawH < 3f) {
            drawRect(color = base, topLeft = tl, size = sz)
        } else if (shadingMode == FileUtils.TreemapShadingMode.CUSHION) {
            val brush = if (colors === tile.gradientColors) tile.cushionBrush
            else Brush.radialGradient(colors, center = Offset(0.38f, 0.35f), radius = 0.85f)
            drawContext.canvas.save()
            drawContext.transform.translate(sLeft, sTop)
            drawContext.transform.scale(drawW, drawH, pivot = Offset.Zero)
            drawRect(brush = brush, topLeft = Offset.Zero, size = Size(1f, 1f))
            drawContext.canvas.restore()
        } else {
            drawRect(
                brush = Brush.linearGradient(colors, start = tl, end = Offset(sLeft + drawW, sTop + drawH)),
                topLeft = tl,
                size = sz
            )
        }

        if (selectedNodes.contains(tile.node)) drawMarked(sLeft, sTop, drawW, drawH)

        if (tile.pkgName != null && sW >= 24f && sH >= 24f) {
            val bmp = AppIconCache.get(context, tile.pkgName)
            if (bmp != null) {
                val iconSize = minOf(80f, minOf(sW, sH) * 0.70f).coerceAtLeast(16f)
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset((sLeft + (sW - iconSize) / 2f).toInt(), (sTop + (sH - iconSize) / 2f).toInt()),
                    dstSize = IntSize(iconSize.toInt(), iconSize.toInt())
                )
            }
        }
    }
}

/** Green mark overlay for a multi-selected tile. */
fun DrawScope.drawMarked(sLeft: Float, sTop: Float, drawW: Float, drawH: Float) {
    val minDim = minOf(drawW, drawH)
    val strokeW = if (minDim < 8f) maxOf(1f, minDim * 0.25f) else if (minDim < 20f) 2.0f else 2.5f
    val half = strokeW / 2f
    drawRect(color = MarkFill, topLeft = Offset(sLeft, sTop), size = Size(drawW, drawH))
    drawRect(
        color = MarkStroke,
        topLeft = Offset(sLeft + half, sTop + half),
        size = Size(maxOf(1f, drawW - strokeW), maxOf(1f, drawH - strokeW)),
        style = Stroke(width = strokeW)
    )
}
