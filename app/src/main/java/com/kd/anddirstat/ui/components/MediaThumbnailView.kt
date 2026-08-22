package com.kd.anddirstat.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaThumbnailCache {
    private val memoryCache = object : LruCache<String, Bitmap>(20 * 1024) { // 20 MB cache
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    fun get(path: String): Bitmap? = memoryCache.get(path)

    fun put(path: String, bitmap: Bitmap) {
        memoryCache.put(path, bitmap)
    }

    suspend fun loadThumbnail(path: String, isVideo: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val cached = get(path)
        if (cached != null) return@withContext cached

        val actual = FileUtils.resolveActualFile(path) ?: return@withContext null
        if (!actual.exists() || !actual.canRead()) return@withContext null

        try {
            val bmp = if (isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createVideoThumbnail(actual, Size(320, 320), null)
                } else {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(actual.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createImageThumbnail(actual, Size(320, 320), null)
                } else {
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(actual.absolutePath, boundsOptions)
                    val sampleSize = maxOf(1, maxOf(boundsOptions.outWidth / 320, boundsOptions.outHeight / 320))
                    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    BitmapFactory.decodeFile(actual.absolutePath, decodeOptions)
                }
            }
            if (bmp != null) {
                put(path, bmp)
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun MediaThumbnailView(
    node: CompactNode,
    path: String,
    modifier: Modifier = Modifier,
    fallbackTint: Color = MaterialTheme.colorScheme.primary
) {
    val nameLower = remember(node.name) { node.name.lowercase() }
    val isVideo = remember(nameLower) {
        nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") ||
        nameLower.endsWith(".mov") || nameLower.endsWith(".webm") || nameLower.endsWith(".3gp") ||
        nameLower.endsWith(".ts") || nameLower.endsWith(".m4v")
    }
    val isImage = remember(nameLower) {
        nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") ||
        nameLower.endsWith(".webp") || nameLower.endsWith(".heic") || nameLower.endsWith(".gif") ||
        nameLower.endsWith(".bmp") || nameLower.endsWith(".svg")
    }

    var thumbnailBitmap by remember(path) {
        mutableStateOf(MediaThumbnailCache.get(path))
    }

    LaunchedEffect(path, isVideo, isImage) {
        if ((isVideo || isImage) && thumbnailBitmap == null) {
            thumbnailBitmap = MediaThumbnailCache.loadThumbnail(path, isVideo)
        }
    }

    if (thumbnailBitmap != null) {
        Image(
            bitmap = thumbnailBitmap!!.asImageBitmap(),
            contentDescription = node.name,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        // Material theme semantic fallback (no bg fill, clean icon)
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FileUtils.getNodeIcon(node, false),
                contentDescription = null,
                tint = fallbackTint,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
