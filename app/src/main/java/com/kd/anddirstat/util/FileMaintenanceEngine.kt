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
                val fLen = file.length()
                val ok = if (isPermanent) {
                    file.deleteRecursively()
                } else {
                    FileUtils.deleteOrTrashFile(file, context)
                }
                if (ok) {
                    deletedCount++
                    StorageTrendManager.recordFreedBytes(context, fLen)
                }
            } catch (_: Exception) {}
        }

        val baseMsg = "$actionName $deletedCount items"
        val msg = if (starred.isNotEmpty()) "$baseMsg (Skipped ${starred.size} starred)" else baseMsg
        AppNotifier.notify(msg)

        BatchResult(deletedCount, starred.size)
    }

    suspend fun deleteTreeNodes(
        context: Context,
        items: List<Pair<com.kd.anddirstat.model.CompactNode, String>>,
        onProgress: (current: Int, total: Int, name: String, isTrash: Boolean) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val (starredItems, unstarredItems) = items.partition { (_, path) -> FavoritesManager.isStarred(context, path) }
        if (unstarredItems.isEmpty()) {
            AppNotifier.notify("Cannot delete starred files. Unstar them manually first.")
            return@withContext 0
        }
        val allAlreadyTrashed = unstarredItems.all { (node, path) ->
            node.name.startsWith(".trashed") || path.contains(".trashed") || path.contains("[Recycle Bin]")
        }
        val isTrash = !allAlreadyTrashed
        var processedCount = 0
        val packagesToUninstall = mutableListOf<String>()

        unstarredItems.forEachIndexed { index, (node, path) ->
            onProgress(index + 1, unstarredItems.size, node.name, isTrash)
            val pkg = FileUtils.extractPackageName(node, path, context)
            if (pkg != null) {
                packagesToUninstall.add(pkg)
            } else {
                try {
                    val f = FileUtils.resolveActualFile(path) ?: FileUtils.resolveActualFile(node.name)
                    if (f != null && f.exists()) {
                        val fLen = f.length()
                        if (FileUtils.deleteOrTrashFile(f, context)) {
                            processedCount++
                            StorageTrendManager.recordFreedBytes(context, fLen)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        if (packagesToUninstall.isNotEmpty()) FileUtils.uninstallApps(context, packagesToUninstall)
        val baseMsg = if (allAlreadyTrashed) "Deleted $processedCount items permanently" else "Moved $processedCount items to Recycle Bin"
        val msg = if (starredItems.isNotEmpty()) "$baseMsg (Skipped ${starredItems.size} starred items)" else baseMsg
        AppNotifier.notify(msg)
        processedCount
    }
}
