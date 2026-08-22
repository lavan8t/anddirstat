package com.kd.anddirstat.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

object AppIconCache {
    private val memoryCache = LruCache<String, ImageBitmap>(100)

    fun get(context: Context, packageName: String?): ImageBitmap? {
        if (packageName == null) return null
        val cached = memoryCache.get(packageName)
        if (cached != null) return cached

        val bitmap = try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bmp = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
        if (bitmap != null) {
            memoryCache.put(packageName, bitmap)
        }
        return bitmap
    }
}

@Composable
fun AppIconView(
    packageName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackVector: ImageVector = Icons.Outlined.Apps,
    fallbackTint: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val imageBitmap = remember(packageName) {
        AppIconCache.get(context, packageName)
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
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
