package com.kd.anddirstat.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

object AppIconCache {
    private const val CACHE_SIZE = 300
    private val memoryCache = LruCache<String, ImageBitmap>(CACHE_SIZE)
    private val colorCache = LruCache<String, Color>(CACHE_SIZE)
    private val failedPackages = Collections.synchronizedSet(HashSet<String>())

    fun peek(packageName: String?): ImageBitmap? {
        if (packageName == null) return null
        return memoryCache.get(packageName)
    }

    fun getDominantColor(packageName: String?): Color? {
        if (packageName == null) return null
        return colorCache.get(packageName)
    }

    private fun extractDominantColor(bmp: Bitmap): Color {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in 0 until pixels.size step 2) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a > 100) {
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val maxC = maxOf(r, maxOf(g, b))
                val minC = minOf(r, minOf(g, b))
                val satWeight = if (maxC > 20 && maxC - minC > 15) 3 else 1
                rSum += r * satWeight
                gSum += g * satWeight
                bSum += b * satWeight
                count += satWeight
            }
        }
        return if (count > 0) {
            Color(
                red = (rSum / count).toInt(),
                green = (gSum / count).toInt(),
                blue = (bSum / count).toInt()
            )
        } else {
            Color(0xFF3B82F6)
        }
    }

    suspend fun load(context: Context, packageName: String?): ImageBitmap? {
        if (packageName == null) return null
        val cached = memoryCache.get(packageName)
        if (cached != null) return cached
        if (failedPackages.contains(packageName)) return null

        return withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, 48, 48)
                drawable.draw(canvas)
                val dominantColor = extractDominantColor(bmp)
                colorCache.put(packageName, dominantColor)
                val imageBitmap = bmp.asImageBitmap()
                memoryCache.put(packageName, imageBitmap)
                imageBitmap
            } catch (_: Exception) {
                failedPackages.add(packageName)
                null
            }
        }
    }

    fun get(context: Context, packageName: String?): ImageBitmap? {
        if (packageName == null) return null
        val cached = memoryCache.get(packageName)
        if (cached != null) return cached
        if (failedPackages.contains(packageName)) return null

        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, 48, 48)
            drawable.draw(canvas)
            val dominantColor = extractDominantColor(bmp)
            colorCache.put(packageName, dominantColor)
            val imageBitmap = bmp.asImageBitmap()
            memoryCache.put(packageName, imageBitmap)
            imageBitmap
        } catch (_: Exception) {
            failedPackages.add(packageName)
            null
        }
    }
}

@Composable
fun AppIconView(
    packageName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackSymbol: String = "apps",
    fallbackTint: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    var imageBitmap by remember(packageName) {
        mutableStateOf(AppIconCache.peek(packageName))
    }

    LaunchedEffect(packageName) {
        if (imageBitmap == null && packageName != null) {
            imageBitmap = AppIconCache.load(context, packageName)
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = contentDescription,
            modifier = modifier
        )
    } else {
        MaterialSymbol(
            name = fallbackSymbol,
            active = true,
            size = 28.dp,
            tint = fallbackTint,
            modifier = modifier
        )
    }
}
