package com.kd.anddirstat.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

object AppIconCache {
    private const val CACHE_SIZE = 300
    private val memoryCache = LruCache<String, ImageBitmap>(CACHE_SIZE)
    private val failedPackages = Collections.synchronizedSet(HashSet<String>())

    fun peek(packageName: String?): ImageBitmap? {
        if (packageName == null) return null
        return memoryCache.get(packageName)
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
    fallbackVector: ImageVector = Icons.Rounded.Apps,
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
        Icon(
            imageVector = fallbackVector,
            contentDescription = contentDescription,
            tint = fallbackTint,
            modifier = modifier
        )
    }
}
