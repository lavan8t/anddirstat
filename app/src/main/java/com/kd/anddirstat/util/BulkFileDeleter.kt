package com.kd.anddirstat.util

import com.kd.anddirstat.cleanup.DeletionSafetyGuard
import com.kd.anddirstat.cleanup.SafetyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class BulkDeleteResult(
    val deletedCount: Int,
    val freedBytes: Long,
    val blockedPaths: List<String>
)

/**
 * Bulk deletion with a bounded worker pool (4 threads, the point where DiskBoss measured its
 * best SSD scaling). Each file is guarded before deletion; starred and protected paths are
 * skipped. Threads are bounded so a large batch cannot saturate the device.
 */
object BulkFileDeleter {
    private const val WORKERS = 4

    suspend fun delete(
        context: android.content.Context,
        files: List<File>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): BulkDeleteResult = withContext(Dispatchers.IO) {
        val candidates = files.filter { it.exists() }
        val safetyByFile = candidates.associateWith { file ->
            DeletionSafetyGuard.checkSafety(null, file.absolutePath, context)
        }
        val safeFiles = safetyByFile.filterValues { it is SafetyResult.Allowed }.keys.toList()
        val blocked = safetyByFile.filterValues { it is SafetyResult.Blocked }.keys.map { it.absolutePath }

        val permits = Semaphore(WORKERS)
        val done = AtomicInteger(0)
        val freed = AtomicLong(0)

        coroutineScope {
            safeFiles.map { file ->
                async {
                    permits.withPermit {
                        try {
                            val size = file.length()
                            if (FileUtils.deleteOrTrashFile(file, context)) {
                                freed.addAndGet(size)
                                done.incrementAndGet()
                            }
                        } catch (_: Exception) {
                        }
                        onProgress(done.get(), safeFiles.size)
                    }
                }
            }.awaitAll()
        }

        val totalFreed = freed.get()
        if (totalFreed > 0L) StorageTrendManager.recordFreedBytes(context, totalFreed)
        BulkDeleteResult(done.get(), totalFreed, blocked)
    }
}
