package com.kd.anddirstat.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileMaintenanceEngine {

    data class BatchResult(
        val successCount: Int,
        val skippedStarredCount: Int
    )

    suspend fun batchDelete(
        context: Context,
        files: List<File>,
        isPermanent: Boolean = false,
        actionName: String = if (isPermanent) "Permanently deleted" else "Moved to Recycle Bin",
        onProgress: (current: Int, total: Int, fileName: String) -> Unit = { _, _, _ -> }
    ): BatchResult = withContext(Dispatchers.IO) {
        val (starred, unstarred) = files.partition { FavoritesManager.isStarred(context, it.absolutePath) }
        if (unstarred.isEmpty() && starred.isNotEmpty()) {
            AppNotifier.notify("Cannot delete starred items. Unstar them first.")
            return@withContext BatchResult(0, starred.size)
        }

        var deletedCount = 0
        val total = unstarred.size
        unstarred.forEachIndexed { index, file ->
            onProgress(index + 1, total, file.name)
            try {
                val ok = if (isPermanent) {
                    file.deleteRecursively()
                } else {
                    FileUtils.deleteOrTrashFile(file, context)
                }
                if (ok) deletedCount++
            } catch (_: Exception) {}
        }

        val baseMsg = "$actionName $deletedCount items"
        val msg = if (starred.isNotEmpty()) "$baseMsg (Skipped ${starred.size} starred)" else baseMsg
        AppNotifier.notify(msg)

        BatchResult(deletedCount, starred.size)
    }
}
