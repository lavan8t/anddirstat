package com.kd.anddirstat.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Daily snapshot without a full phone scan: device used/total comes from StatFs, and WhatsApp
 * media is summarised by walking only the WhatsApp directories. Scheduled by [scheduleDailySnapshot].
 */
class DailyStorageSnapshotWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val totalBytes: Long
            val usedBytes: Long
            try {
                val path = Environment.getExternalStorageDirectory()
                val stat = StatFs(path.path)
                totalBytes = stat.blockCountLong * stat.blockSizeLong
                val available = stat.availableBlocksLong * stat.blockSizeLong
                usedBytes = (totalBytes - available).coerceAtLeast(0L)
            } catch (_: Exception) {
                return@withContext Result.retry()
            }

            StorageTrendManager.recordSnapshot(applicationContext, usedBytes, totalBytes)

            val whatsappDirs = listOf(
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media"),
                File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media")
            )
            var total = 0L
            var addedBytes = 0L
            var addedFiles = 0
            val startOfToday = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            val stack = ArrayDeque<File>()
            whatsappDirs.forEach { if (it.exists() && it.isDirectory) stack.add(it) }
            var visited = 0
            while (stack.isNotEmpty() && visited < 20000) {
                val file = stack.removeLast()
                visited++
                if (file.isDirectory) {
                    file.listFiles()?.forEach { stack.add(it) }
                } else {
                    val size = file.length()
                    total += size
                    if (file.lastModified() >= startOfToday) {
                        addedBytes += size
                        addedFiles++
                    }
                }
            }
            WhatsappTrendManager.recordSnapshot(applicationContext, total, addedBytes, addedFiles)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
