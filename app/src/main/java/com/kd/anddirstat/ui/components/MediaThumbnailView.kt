package com.kd.anddirstat.ui.components

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaThumbnailCache {
    private val memoryCache = object : LruCache<String, Bitmap>(32 * 1024) { // 32 MB cache
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    fun get(path: String): Bitmap? = memoryCache.get(path)

    fun put(path: String, bitmap: Bitmap) {
        memoryCache.put(path, bitmap)
    }

    suspend fun loadThumbnail(context: Context, path: String, altName: String, isVideo: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val cached = get(path)
        if (cached != null) return@withContext cached

        val actual = FileUtils.resolveActualFile(path) ?: FileUtils.resolveActualFile(altName)
        if (actual == null || !actual.exists() || !actual.canRead()) return@withContext null

        try {
            var bmp: Bitmap? = null

            // 1. ContentResolver / MediaStore query
            try {
                val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val proj = arrayOf(MediaStore.MediaColumns._ID)
                context.contentResolver.query(
                    baseUri,
                    proj,
                    "${MediaStore.MediaColumns.DATA}=?",
                    arrayOf(actual.absolutePath),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val itemUri = ContentUris.withAppendedId(baseUri, id)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            bmp = context.contentResolver.loadThumbnail(itemUri, Size(320, 320), null)
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. ThumbnailUtils Android 10+
            if (bmp == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    bmp = if (isVideo) {
                        ThumbnailUtils.createVideoThumbnail(actual, Size(320, 320), null)
                    } else {
                        ThumbnailUtils.createImageThumbnail(actual, Size(320, 320), null)
                    }
                } catch (_: Exception) {}
            }

            // 3. Fallback for Video: MediaMetadataRetriever
            if (bmp == null && isVideo) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(actual.absolutePath)
                    bmp = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                    retriever.release()
                } catch (_: Exception) {}
            }

            // 4. Fallback for Images: BitmapFactory
            if (bmp == null && !isVideo) {
                try {
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(actual.absolutePath, boundsOptions)
                    if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
                        var sampleSize = 1
                        val halfH = boundsOptions.outHeight / 2
                        val halfW = boundsOptions.outWidth / 2
                        while (halfH / sampleSize >= 320 && halfW / sampleSize >= 320) {
                            sampleSize *= 2
                        }
                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        bmp = BitmapFactory.decodeFile(actual.absolutePath, decodeOptions)
                    }
                } catch (_: Exception) {}
            }

            // 5. Fallback: Search DCIM/.thumbnails
            if (bmp == null) {
                try {
                    val dcimThumbDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), ".thumbnails")
                    if (dcimThumbDir.exists() && dcimThumbDir.isDirectory) {
                        val baseName = actual.nameWithoutExtension
                        val matchingThumb = dcimThumbDir.listFiles()?.firstOrNull {
                            it.name.contains(baseName, ignoreCase = true)
                        }
                        if (matchingThumb != null) {
                            bmp = BitmapFactory.decodeFile(matchingThumb.absolutePath)
                        }
                    }
                } catch (_: Exception) {}
            }

            val finalBmp = bmp
            if (finalBmp != null) {
                put(path, finalBmp)
            }
            finalBmp
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
    val context = LocalContext.current
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

    var thumbnailBitmap by remember(path, node.name) {
        mutableStateOf(MediaThumbnailCache.get(path))
    }

    LaunchedEffect(path, node.name, isVideo, isImage) {
        if ((isVideo || isImage) && thumbnailBitmap == null) {
            thumbnailBitmap = MediaThumbnailCache.loadThumbnail(context, path, node.name, isVideo)
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
